package com.hnn.bisnor.data.remote

import android.content.Context
import android.util.Base64
import com.hnn.bisnor.BuildConfig
import com.hnn.bisnor.R
import com.hnn.bisnor.data.repository.FavoritesManager
import com.hnn.bisnor.data.repository.PlaylistsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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

data class FunnyAvatar(val id: String, val name: String, val drawableRes: Int)

class AuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("bisnor_auth_v3", Context.MODE_PRIVATE)

    companion object {
        val FUNNY_AVATARS = listOf(
            FunnyAvatar("avatar_breakingbad", "والتر وایت (بریکینگ بد)", R.drawable.avatar_breakingbad),
            FunnyAvatar("avatar_luffy", "لوفی (وان پیس)", R.drawable.avatar_luffy),
            FunnyAvatar("avatar_wednesday", "ونزدی آدامز", R.drawable.avatar_wednesday),
            FunnyAvatar("avatar_nami", "نامی (وان پیس)", R.drawable.avatar_nami),
            FunnyAvatar("avatar_garfield", "گارفیلد", R.drawable.avatar_garfield),
            FunnyAvatar("avatar_bluey", "بلویی", R.drawable.avatar_bluey),
            FunnyAvatar("avatar_bingo", "بینگو", R.drawable.avatar_bingo),
            FunnyAvatar("avatar_carmen", "کارمن سندیگو", R.drawable.avatar_carmen),
            FunnyAvatar("avatar_film", "کلاکت سینما", R.drawable.avatar_film),
            FunnyAvatar("avatar_theater", "ماسک نمایش", R.drawable.avatar_theater),
            FunnyAvatar("avatar_star", "ستاره طلایی", R.drawable.avatar_star)
        )

        fun getAvatarDrawable(id: String): Int {
            return FUNNY_AVATARS.find { it.id == id }?.drawableRes ?: R.drawable.avatar_breakingbad
        }
    }

    var currentUsername: String
        get() = prefs.getString("name", "") ?: ""
        private set(v) { prefs.edit().putString("name", v).apply() }

    var userAvatarId: String
        get() = prefs.getString("avatar", "avatar_breakingbad") ?: "avatar_breakingbad"
        set(v) { prefs.edit().putString("avatar", v).apply() }

    var userAvatarUrl: String
        get() = prefs.getString("url", "") ?: ""
        set(v) { prefs.edit().putString("url", v).apply() }

    var isLoggedIn: Boolean
        get() = currentUsername.isNotEmpty() && prefs.getBoolean("logged_in", false)
        private set(v) { prefs.edit().putBoolean("logged_in", v).apply() }

    var lastSyncTime: Long
        get() = prefs.getLong("sync", 0L)
        set(v) { prefs.edit().putLong("sync", v).apply() }

    fun updateUsername(v: String) { currentUsername = v }
    fun updateAvatarUrl(v: String) { userAvatarUrl = v }

    fun logout() {
        isLoggedIn = false
        currentUsername = ""
        userAvatarUrl = ""
        userAvatarId = "avatar_breakingbad"
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

        if (!cleanUser.matches(Regex("^[a-z0-9_.-]{3,32}$"))) {
            return@withContext Pair(false, "نام کاربری باید بین ۳ تا ۳۲ حرف انگلیسی یا عدد باشد.")
        }
        if (cleanPass.length < 4) {
            return@withContext Pair(false, "رمز عبور باید حداقل ۴ کاراکتر باشد.")
        }

        val existing = SupabaseManager.queryUser(cleanUser)
        if (existing != null) {
            return@withContext Pair(false, "این نام کاربری قبلاً ثبت شده است.")
        }

        val passHash = hashPassword(cleanPass)
        val created = SupabaseManager.createUser(cleanUser, passHash, userAvatarId)
        if (created) {
            currentUsername = cleanUser
            isLoggedIn = true
            syncUp(context)
            Pair(true, "ثبت‌نام با موفقیت انجام شد!")
        } else {
            Pair(false, "خطا در اتصال به سرور بیسنور. اتصال اینترنت خود را بررسی کنید.")
        }
    }

    suspend fun login(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Pair(false, "نام کاربری و رمز عبور را وارد کنید.")
        }

        val record = SupabaseManager.queryUser(cleanUser)
            ?: return@withContext Pair(false, "کاربری با نام «$cleanUser» یافت نشد.")

        val passHash = hashPassword(cleanPass)
        val serverHash = record.optString("password_hash", "")
        if (serverHash != passHash) {
            return@withContext Pair(false, "رمز عبور وارد شده نادرست است.")
        }

        currentUsername = cleanUser
        isLoggedIn = true
        val avatar = record.optString("avatar_id", "avatar_breakingbad")
        if (avatar.isNotEmpty()) {
            userAvatarId = avatar
        }

        syncDown(context, record)
        Pair(true, "ورود با موفقیت انجام شد! حساب شما همگام‌سازی گردید.")
    }

    suspend fun updateAvatar(newAvatarId: String): Boolean = withContext(Dispatchers.IO) {
        userAvatarId = newAvatarId
        if (!isLoggedIn || currentUsername.isEmpty()) return@withContext true
        val payload = JSONObject().apply {
            put("avatar_id", newAvatarId)
            put("updated_at", System.currentTimeMillis())
        }
        SupabaseManager.updateUserData(currentUsername, payload.toString())
    }

    suspend fun syncUp(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn || currentUsername.isEmpty()) return@withContext false

        val favs = FavoritesManager(c).getFavoritesRawJson()
        val pls = PlaylistsManager(c).getPlaylistsRawJson()

        val payload = JSONObject().apply {
            put("favorites_data", SupabaseManager.compressString(favs))
            put("playlists_data", SupabaseManager.compressString(pls))
            put("avatar_id", userAvatarId)
            put("updated_at", System.currentTimeMillis())
        }

        val ok = SupabaseManager.updateUserData(currentUsername, payload.toString())
        if (ok) lastSyncTime = System.currentTimeMillis()
        ok
    }

    suspend fun syncDown(c: Context, cachedRecord: JSONObject? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn || currentUsername.isEmpty()) return@withContext false

        val record = cachedRecord ?: SupabaseManager.queryUser(currentUsername) ?: return@withContext false
        val avatar = record.optString("avatar_id", "")
        if (avatar.isNotEmpty()) userAvatarId = avatar

        val favsCompressed = record.optString("favorites_data", "")
        if (favsCompressed.isNotEmpty()) {
            runCatching {
                val decomp = SupabaseManager.decompressString(favsCompressed)
                if (decomp.isNotBlank() && decomp != "[]") {
                    FavoritesManager(c).setFavoritesFromRawJson(decomp)
                }
            }
        }

        val plsCompressed = record.optString("playlists_data", "")
        if (plsCompressed.isNotEmpty()) {
            runCatching {
                val decomp = SupabaseManager.decompressString(plsCompressed)
                if (decomp.isNotBlank() && decomp != "[]") {
                    PlaylistsManager(c).setPlaylistsFromRawJson(decomp)
                }
            }
        }

        lastSyncTime = System.currentTimeMillis()
        true
    }
}

object SupabaseManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val url: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key: String get() = BuildConfig.SUPABASE_ANON_KEY

    fun compressString(data: String): String {
        if (data.isBlank() || data == "[]") return ""
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray(StandardCharsets.UTF_8)) }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun decompressString(compressedBase64: String): String {
        if (compressedBase64.isBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(compressedBase64, Base64.NO_WRAP)
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(StandardCharsets.UTF_8).readText()
        }.getOrDefault("")
    }

    suspend fun queryUser(username: String): JSONObject? = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) return@withContext null
        try {
            val reqUrl = "$url/rest/v1/users?username=eq.$username&select=*"
            val request = Request.Builder()
                .url(reqUrl)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val arr = JSONArray(body)
                    if (arr.length() > 0) arr.getJSONObject(0) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createUser(username: String, passwordHash: String, avatarId: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isEmpty() || key.isEmpty()) return@withContext false
        try {
            val payload = JSONObject().apply {
                put("username", username)
                put("password_hash", passwordHash)
                put("avatar_id", avatarId)
                put("created_at", System.currentTimeMillis())
            }.toString()

            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$url/rest/v1/users")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
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
        if (url.isEmpty() || key.isEmpty()) return@withContext false
        try {
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$url/rest/v1/users?username=eq.$username")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
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
