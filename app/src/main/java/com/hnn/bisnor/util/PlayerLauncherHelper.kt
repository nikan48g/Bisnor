package com.hnn.bisnor.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import com.hnn.bisnor.R
import com.hnn.bisnor.ui.player.PlayerActivity

data class PlayerAppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val isInstalled: Boolean,
    val iconRes: Int = R.drawable.ic_videoplayer,
    val appIcon: Drawable? = null
)

object PlayerLauncherHelper {

    const val PKG_VLC = "org.videolan.vlc"
    const val PKG_MX_PLAYER = "com.mxtech.videoplayer.ad"
    const val PKG_KM_PLAYER = "com.kmplayer"
    const val PKG_JUST_PLAYER = "com.brouken.player"

    fun getSupportedPlayers(context: Context): List<PlayerAppInfo> {
        val pm = context.packageManager

        fun getDrawable(pkg: String): Drawable? {
            return try {
                pm.getApplicationIcon(pkg)
            } catch (e: Exception) {
                null
            }
        }

        val vlcInstalled = isPackageInstalled(pm, PKG_VLC)
        val mxInstalled = isPackageInstalled(pm, PKG_MX_PLAYER) || isPackageInstalled(pm, "com.mxtech.videoplayer.pro")
        val kmInstalled = isPackageInstalled(pm, PKG_KM_PLAYER)
        val justInstalled = isPackageInstalled(pm, PKG_JUST_PLAYER)

        return listOf(
            PlayerAppInfo(
                id = "internal",
                name = "پلیر بیسنور (داخلی)",
                packageName = "",
                isInstalled = true,
                iconRes = R.drawable.ic_play
            ),
            PlayerAppInfo(
                id = "vlc",
                name = "VLC Player",
                packageName = PKG_VLC,
                isInstalled = vlcInstalled,
                iconRes = R.drawable.ic_vlc,
                appIcon = getDrawable(PKG_VLC)
            ),
            PlayerAppInfo(
                id = "mx",
                name = "MX Player",
                packageName = PKG_MX_PLAYER,
                isInstalled = mxInstalled,
                iconRes = R.drawable.ic_videoplayer,
                appIcon = getDrawable(PKG_MX_PLAYER)
            ),
            PlayerAppInfo(
                id = "kmplayer",
                name = "KMPlayer",
                packageName = PKG_KM_PLAYER,
                isInstalled = kmInstalled,
                iconRes = R.drawable.ic_videoplayer,
                appIcon = getDrawable(PKG_KM_PLAYER)
            ),
            PlayerAppInfo(
                id = "justplayer",
                name = "Just Player",
                packageName = PKG_JUST_PLAYER,
                isInstalled = justInstalled,
                iconRes = R.drawable.ic_videoplayer,
                appIcon = getDrawable(PKG_JUST_PLAYER)
            ),
            PlayerAppInfo(
                id = "system",
                name = "سایر برنامه‌ها (انتخابگر سیستم)",
                packageName = "",
                isInstalled = true,
                iconRes = R.drawable.ic_share
            )
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openInGooglePlay(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun launchPlayer(
        activity: Activity,
        playerChoice: String,
        title: String,
        url: String,
        mediaId: Int,
        mediaCover: String,
        episodeTitle: String,
        episodeIndex: Int,
        startPositionMs: Long
    ) {
        if (url.isEmpty()) return

        when (playerChoice) {
            "vlc" -> launchExternal(activity, PKG_VLC, title, url)
            "mx" -> launchExternal(activity, PKG_MX_PLAYER, title, url)
            "kmplayer" -> launchExternal(activity, PKG_KM_PLAYER, title, url)
            "justplayer" -> launchExternal(activity, PKG_JUST_PLAYER, title, url)
            "system" -> {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                    putExtra("title", title)
                }
                activity.startActivity(Intent.createChooser(intent, "پخش با:"))
            }
            else -> {
                val intent = Intent(activity, PlayerActivity::class.java).apply {
                    putExtra("video_title", title)
                    putExtra("video_url", url)
                    putExtra("media_id", mediaId)
                    putExtra("media_title", title)
                    putExtra("media_cover", mediaCover)
                    putExtra("episode_title", episodeTitle)
                    putExtra("episode_index", episodeIndex)
                    if (startPositionMs > 0L) putExtra("start_position", startPositionMs)
                }
                activity.startActivity(intent)
            }
        }
    }

    private fun launchExternal(activity: Activity, packageName: String, title: String, url: String) {
        if (!isPackageInstalled(activity.packageManager, packageName)) {
            Toast.makeText(activity, "این برنامه نصب نیست. انتقال به گوگل‌پلی...", Toast.LENGTH_SHORT).show()
            openInGooglePlay(activity, packageName)
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(packageName)
                setDataAndType(Uri.parse(url), "video/*")
                putExtra("title", title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "خطا در باز کردن برنامه: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
