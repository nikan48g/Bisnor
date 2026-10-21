package com.hnn.bisnor.data.repository

import android.content.Context
import com.hnn.bisnor.data.remote.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fire-and-forget upload of local watchlist/playlists after a real change. */
object CloudSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun request(context: Context) {
        val app = context.applicationContext
        val auth = AuthManager(app)
        if (!auth.isLoggedIn) return
        scope.launch { runCatching { auth.syncUp(app) } }
    }
}
