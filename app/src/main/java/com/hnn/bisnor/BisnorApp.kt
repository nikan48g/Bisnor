package com.hnn.bisnor

import android.app.Activity
import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import com.hnn.bisnor.ui.downloads.DownloadsActivity
import com.hnn.bisnor.util.InAppNotificationHelper
import java.lang.ref.WeakReference

class BisnorApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        private var currentActivityRef: WeakReference<Activity>? = null

        val currentActivity: Activity?
            get() = currentActivityRef?.get()
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L || context == null) return

            var title = "فیلم جدید"
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = dm.query(query)
                cursor?.use {
                    if (it.moveToNext()) {
                        val titleIdx = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        if (titleIdx != -1) {
                            val t = it.getString(titleIdx)
                            if (!t.isNullOrBlank()) title = t
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore query exception
            }

            // Show In-App top notification if user is inside the app and NOT in DownloadsActivity
            val activeActivity = currentActivity
            if (activeActivity != null && activeActivity !is DownloadsActivity) {
                InAppNotificationHelper.showDownloadCompletedBanner(activeActivity, title)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        com.hnn.bisnor.data.repository.SegmentedDownloadManager.init(this)

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
