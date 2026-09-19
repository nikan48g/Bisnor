package com.hnn.bisnor.data.remote

import android.content.Context
import android.content.SharedPreferences
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
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class FunnyAvatar(val id: String, val name: String, val drawableRes: Int)

class AuthManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bisnor_auth_v3", Context.MODE_PRIVATE)

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

    val isLoggedIn: Boolean
        get() = currentUsername.isNotEmpty() && SupabaseManager.hasSession(context)

    var lastSyncTime: Long
        get() = prefs.getLong("sync", 0L)
        set(v) { prefs.edit().putLong("sync", v).apply() }

    fun updateUsername(v: String) { currentUsername = v }
    fun updateAvatarUrl(v: String) { userAvatarUrl = v }

    fun logout() {
        SupabaseManager.clearSession(context)
        currentUsername = ""
        userAvatarUrl = ""
        userAvatarId = "avatar_breakingbad"
        prefs.edit().clear().apply()
    }

    suspend fun register(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (!cleanUser.matches(Regex("^[a-z0-9_.-]{3,32}$"))) {
            return@withContext Pair(false, "نام کاربری باید بین ۳ تا ۳۲ حرف انگلیسی، عدد یا زیرخط باشد.")
        }
        if (cleanPass.length < 6) {
            return@withContext Pair(false, "رمز عبور باید حداقل ۶ کاراکتر باشد.")
        }

        val (ok, message) = SupabaseManager.signUp(context, cleanUser, cleanPass, userAvatarId)
        if (ok) {
            currentUsername = cleanUser
            syncUp(context)
            Pair(true, "ثبت‌نام با موفقیت انجام شد! حساب شما فعال گردید.")
        } else {
            Pair(false, message)
        }
    }

    suspend fun login(username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return@withContext Pair(false, "نام کاربری و رمز عبور را وارد کنید.")
        }

        val (ok, message) = SupabaseManager.signIn(context, cleanUser, cleanPass)
        if (!ok) {
            return@withContext Pair(false, message)
        }

        currentUsername = cleanUser
        val profile = SupabaseManager.fetchProfile(context)
        if (profile != null) {
            val avatar = profile.optString("avatar_id", "avatar_breakingbad")
            if (avatar.isNotEmpty()) {
                userAvatarId = avatar
            }
            syncDown(context, profile)
        }

        Pair(true, "ورود با موفقیت انجام شد! حساب شما همگام‌سازی گردید.")
    }

    suspend fun updateAvatar(newAvatarId: String): Boolean = withContext(Dispatchers.IO) {
        userAvatarId = newAvatarId
        if (!isLoggedIn) return@withContext true
        val payload = JSONObject().apply {
            put("avatar_id", newAvatarId)
            put("updated_at", System.currentTimeMillis())
        }
        SupabaseManager.patchProfile(context, payload)
    }

    suspend fun syncUp(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext false

        val favs = FavoritesManager(c).getFavoritesRawJson()
        val pls = PlaylistsManager(c).getPlaylistsRawJson()

        val payload = JSONObject().apply {
            put("favorites_data", SupabaseManager.compressString(favs))
            put("playlists_data", SupabaseManager.compressString(pls))
            put("avatar_id", userAvatarId)
            put("updated_at", System.currentTimeMillis())
        }

        val ok = SupabaseManager.patchProfile(c, payload)
        if (ok) lastSyncTime = System.currentTimeMillis()
        ok
    }

    suspend fun syncDown(c: Context, cachedRecord: JSONObject? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext false

        val record = cachedRecord ?: SupabaseManager.fetchProfile(c) ?: return@withContext false
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
    private const val SESSION_PREFS = "bisnor_session"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val url: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    private fun sessionPrefs(c: Context): SharedPreferences =
        c.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)

    fun hasSession(c: Context?): Boolean {
        if (c == null) return false
        val token = sessionPrefs(c).getString("token", "") ?: ""
        return token.isNotBlank()
    }

    fun getUserId(c: Context): String = sessionPrefs(c).getString("user_id", "") ?: ""

    fun clearSession(c: Context) {
        sessionPrefs(c).edit().clear().apply()
    }

    private fun saveSession(c: Context, jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            val accessToken = obj.optString("access_token", "")
            val userObj = obj.optJSONObject("user")
            val userId = userObj?.optString("id", "") ?: obj.optString("id", "")

            if (accessToken.isNotEmpty() && userId.isNotEmpty()) {
                sessionPrefs(c).edit()
                    .putString("token", accessToken)
                    .putString("user_id", userId)
                    .putString("refresh_token", obj.optString("refresh_token", ""))
                    .apply()
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun usernameToEmail(username: String): String = "${username.trim().lowercase()}@bisnor.internal"

    private fun toBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun fromBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    fun compressString(data: String): String {
        if (data.isBlank() || data == "[]") return ""
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray(StandardCharsets.UTF_8)) }
        return toBase64(bos.toByteArray())
    }

    fun decompressString(compressedBase64: String): String {
        if (compressedBase64.isBlank()) return ""
        return runCatching {
            val bytes = fromBase64(compressedBase64)
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(StandardCharsets.UTF_8).readText()
        }.getOrDefault("")
    }

    private fun executeRequest(
        c: Context,
        path: String,
        method: String = "GET",
        payload: JSONObject? = null,
        useAuthToken: Boolean = false
    ): Pair<Int, String> {
        if (url.isEmpty() || anonKey.isEmpty()) return Pair(500, "")

        val authToken = if (useAuthToken) sessionPrefs(c).getString("token", "") ?: "" else ""
        val bearerToken = if (authToken.isNotBlank()) authToken else anonKey

        val requestBuilder = Request.Builder()
            .url("$url$path")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearerToken")

        val body = payload?.toString()?.toRequestBody(jsonMediaType)
        when (method) {
            "POST" -> requestBuilder.post(body ?: "".toRequestBody(jsonMediaType))
            "PATCH" -> requestBuilder.patch(body ?: "".toRequestBody(jsonMediaType))
            else -> requestBuilder.get()
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { resp ->
                val responseBody = resp.body?.string() ?: ""
                Pair(resp.code, responseBody)
            }
        } catch (e: Exception) {
            Pair(0, e.message ?: "Network error")
        }
    }

    suspend fun signUp(c: Context, username: String, pass: String, avatarId: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val email = usernameToEmail(username)
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                    put("data", JSONObject().apply {
                        put("username", username)
                        put("avatar_id", avatarId)
                    })
                }

                val (code, responseStr) = executeRequest(c, "/auth/v1/signup", "POST", body, useAuthToken = false)
                if (code in 200..299) {
                    saveSession(c, responseStr)

                    // Ensure profile record exists in profiles table
                    val userId = getUserId(c)
                    if (userId.isNotEmpty()) {
                        val profileBody = JSONObject().apply {
                            put("user_id", userId)
                            put("username", username)
                            put("avatar_id", avatarId)
                            put("created_at", System.currentTimeMillis())
                        }
                        executeRequest(c, "/rest/v1/profiles", "POST", profileBody, useAuthToken = true)
                    }
                    Pair(true, "ثبت‌نام با موفقیت انجام شد.")
                } else {
                    val lower = responseStr.lowercase()
                    val errorMsg = when {
                        lower.contains("already registered") || lower.contains("user_already_exists") ->
                            "این نام کاربری قبلاً ثبت شده است."
                        lower.contains("weak_password") || lower.contains("least") ->
                            "رمز عبور ضعیف است. حداقل ۶ کاراکتر وارد کنید."
                        else -> "خطا در ثبت‌نام (${code}). اتصال اینترنت خود را بررسی کنید."
                    }
                    Pair(false, errorMsg)
                }
            } catch (e: Exception) {
                Pair(false, "خطا در اتصال به سرور بیسنور: ${e.message}")
            }
        }

    suspend fun signIn(c: Context, username: String, pass: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            try {
                val email = usernameToEmail(username)
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                }

                val (code, responseStr) = executeRequest(c, "/auth/v1/token?grant_type=password", "POST", body, useAuthToken = false)
                if (code in 200..299) {
                    val saved = saveSession(c, responseStr)
                    if (saved) {
                        Pair(true, "ورود با موفقیت انجام شد.")
                    } else {
                        Pair(false, "خطا در دریافت توکن احراز هویت.")
                    }
                } else {
                    Pair(false, "نام کاربری یا رمز عبور نادرست است.")
                }
            } catch (e: Exception) {
                Pair(false, "خطا در ارتباط با سرور: ${e.message}")
            }
        }

    suspend fun fetchProfile(c: Context): JSONObject? = withContext(Dispatchers.IO) {
        val userId = getUserId(c)
        if (userId.isEmpty() || !hasSession(c)) return@withContext null
        try {
            val (code, responseStr) = executeRequest(c, "/rest/v1/profiles?user_id=eq.$userId&select=*", "GET", useAuthToken = true)
            if (code in 200..299) {
                val arr = JSONArray(responseStr)
                if (arr.length() > 0) arr.getJSONObject(0) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun patchProfile(c: Context, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val userId = getUserId(c)
        if (userId.isEmpty() || !hasSession(c)) return@withContext false
        try {
            val (code, _) = executeRequest(c, "/rest/v1/profiles?user_id=eq.$userId", "PATCH", payload, useAuthToken = true)
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
