package com.hnn.bisnor.data.remote

import android.util.Base64
import com.hnn.bisnor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SupabaseManager {

    // Safely loaded from local BuildConfig without exposing keys in Git
    val PROJECT_URL: String
        get() = BuildConfig.SUPABASE_URL

    val ANON_KEY: String
        get() = BuildConfig.SUPABASE_ANON_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // GZIP Compression for ultra-low database payload storage
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

    suspend fun postCompressedData(table: String, jsonPayload: String): Boolean = withContext(Dispatchers.IO) {
        if (PROJECT_URL.isEmpty() || ANON_KEY.isEmpty()) return@withContext false
        try {
            val url = "$PROJECT_URL/rest/v1/$table"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
