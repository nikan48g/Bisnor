package com.hnn.bisnor.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkClient(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bisnor_settings", Context.MODE_PRIVATE)

    companion object {
        const val PREF_BASE_URL = "pref_base_url"
        const val DEFAULT_BASE_URL = "https://api.iranflix.site/"
    }

    var baseUrl: String
        get() = prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(PREF_BASE_URL, value).apply()
            rebuildRetrofit()
        }

    private var retrofitInstance: Retrofit? = null

    private fun buildOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "BisnorApp/1.0.0 (Android)")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun rebuildRetrofit(): Retrofit {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofitInstance = retrofit
        return retrofit
    }

    val apiService: IranflixApiService
        get() {
            val retrofit = retrofitInstance ?: rebuildRetrofit()
            return retrofit.create(IranflixApiService::class.java)
        }
}
