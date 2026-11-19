package com.hnn.bisnor.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Startup probe: is there a network, and which of the two servers answer? */
object ServerHealth {
    data class Result(val network: Boolean, val next: Boolean, val iranflix: Boolean) {
        val anyServer get() = next || iranflix
    }

    // Server 1 = NextMovie, Server 2 = IranFlix
    private val SERVER_1 = listOf("https://w.mihan-cdn.com/api")
    private val SERVER_2 = listOf(
        "https://server-hi-speed-iran.info", "https://hostinnegar.com", "https://windowsdiba.info"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun reachable(url: String): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { true }
    }.getOrDefault(false)

    private fun hasNetwork(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun check(c: Context): Result = withContext(Dispatchers.IO) {
        if (!hasNetwork(c)) return@withContext Result(false, false, false)
        coroutineScope {
            val one = async { SERVER_1.any(::reachable) }
            val two = SERVER_2.map { async { reachable(it) } }
            val r = Result(true, one.await(), two.any { it.await() })
            SourcePreferences.up = buildSet { if (r.next) add(SourcePreferences.NEXT); if (r.iranflix) add(SourcePreferences.IRANFLIX) }
            r
        }
    }
}
