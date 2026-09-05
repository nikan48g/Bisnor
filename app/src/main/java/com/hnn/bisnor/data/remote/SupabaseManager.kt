package com.hnn.bisnor.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class UserProfile(
    val username: String,
    val isLoggedIn: Boolean = false,
    val lastSyncTime: Long = 0L
)

class AuthManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_auth_v1", Context.MODE_PRIVATE)

    var currentUsername: String
        get() = prefs.getString("auth_username", "") ?: ""
        private set(value) = prefs.edit().putString("auth_username", value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("auth_logged_in", false)
        private set(value) = prefs.edit().putBoolean("auth_logged_in", value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong("auth_last_sync", 0L)
        set(value) = prefs.edit().putLong("auth_last_sync", value).apply()

    fun logout() {
        prefs.edit().clear().apply()
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun register(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanUser.length < 3) return@withContext Pair(false, "نام کاربری باید حداقل ۳ کاراکتر باشد.")
        if (cleanPass.length < 4) return@withContext Pair(false, "رمز عبور باید حداقل ۴ کاراکتر باشد.")

        val hashedPassword = hashPassword(cleanPass)

        // Check if user already exists
        val existing = SupabaseManager.queryUser(cleanUser)
        if (existing != null) {
            return@withContext Pair(false, "این نام کاربری قبلاً ثبت‌نام شده است.")
        }

        val success = SupabaseManager.createUser(cleanUser, hashedPassword)
        if (success) {
            currentUsername = cleanUser
            isLoggedIn = true
            // Initial sync: push current local playlists & favorites
            syncUp(context)
            Pair(true, "ثبت‌نام با موفقیت انجام شد و اطلاعات شما همگام‌سازی گردید.")
        } else {
            Pair(false, "خطا در ثبت‌نام. لطفاً اتصال اینترنت را بررسی کنید.")
        }
    }

    suspend fun login(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Pair(false, "نام کاربری و رمز عبور را وارد کنید.")
        }

        val hashedPassword = hashPassword(cleanPass)
        val record = SupabaseManager.queryUser(cleanUser)
            ?: return@withContext Pair(false, "کاربری با این مشخصات یافت نشد.")

        val serverHash = record.optString("password_hash", "")
        if (serverHash != hashedPassword) {
            return@withContext Pair(false, "رمز عبور وارد شده اشتباه است.")
        }

        currentUsername = cleanUser
        isLoggedIn = true

        // Pull user's synced data from cloud
        syncDown(context, record)
        Pair(true, "خوش آمدید! اطلاعات شما با موفقیت فراخوانی و سینک شد.")
    }

    suspend fun syncUp(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn || currentUsername.isEmpty()) return@withContext false

        val favManager = FavoritesManager(context)
        val plManager = PlaylistsManager(context)

        val favsJson = favManager.getFavoritesRawJson()
        val plsJson = plManager.getPlaylistsRawJson()

        val compressedFavs = SupabaseManager.compressString(favsJson)
        val compressedPls = SupabaseManager.compressString(plsJson)

        val payload = JSONObject().apply {
            put("favorites_data", compressedFavs)
            put("playlists_data", compressedPls)
            put("updated_at", System.currentTimeMillis())
        }

        val success = SupabaseManager.updateUserData(currentUsername, payload.toString())
        if (success) {
            lastSyncTime = System.currentTimeMillis()
        }
        success
    }

    suspend fun syncDown(context: Context, cachedRecord: JSONObject? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn || currentUsername.isEmpty()) return@withContext false

        val record = cachedRecord ?: SupabaseManager.queryUser(currentUsername) ?: return@withContext false

        val compFavs = record.optString("favorites_data", "")
        val compPls = record.optString("playlists_data", "")

        val favManager = FavoritesManager(context)
        val plManager = PlaylistsManager(context)

        if (compFavs.isNotEmpty()) {
            try {
                val decomp = SupabaseManager.decompressString(compFavs)
                if (decomp.isNotEmpty() && decomp != "[]") {
                    favManager.setFavoritesFromRawJson(decomp)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (compPls.isNotEmpty()) {
            try {
                val decomp = SupabaseManager.decompressString(compPls)
                if (decomp.isNotEmpty() && decomp != "[]") {
                    plManager.setPlaylistsFromRawJson(decomp)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        lastSyncTime = System.currentTimeMillis()
        true
    }
}

object SupabaseManager {

    val PROJECT_URL: String
        get() = BuildConfig.SUPABASE_URL

    val ANON_KEY: String
        get() = BuildConfig.SUPABASE_ANON_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun compressString(data: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray(StandardCharsets.UTF_8)) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decompressString(compressedBase64: String): String {
        val bytes = Base64.decode(compressedBase64, Base64.NO_WRAP)
        val bis = ByteArrayInputStream(bytes)
        GZIPInputStream(bis).use {
            return it.bufferedReader(StandardCharsets.UTF_8).readText()
        }
    }

    suspend fun queryUser(username: String): JSONObject? = withContext(Dispatchers.IO) {
        if (PROJECT_URL.isEmpty() || ANON_KEY.isEmpty()) return@withContext null
        try {
            val url = "$PROJECT_URL/rest/v1/users?username=eq.$username&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val array = JSONArray(body)
                    if (array.length() > 0) array.getJSONObject(0) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUser(username: String, passwordHash: String): Boolean = withContext(Dispatchers.IO) {
        if (PROJECT_URL.isEmpty() || ANON_KEY.isEmpty()) return@withContext false
        try {
            val url = "$PROJECT_URL/rest/v1/users"
            val payload = JSONObject().apply {
                put("username", username)
                put("password_hash", passwordHash)
                put("created_at", System.currentTimeMillis())
            }.toString()

            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateUserData(username: String, jsonPayload: String): Boolean = withContext(Dispatchers.IO) {
        if (PROJECT_URL.isEmpty() || ANON_KEY.isEmpty()) return@withContext false
        try {
            val url = "$PROJECT_URL/rest/v1/users?username=eq.$username"
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .patch(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
