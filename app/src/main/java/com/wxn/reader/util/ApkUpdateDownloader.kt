package com.wxn.reader.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.wxn.base.util.Logger

/** APK 更新下载：系统 DownloadManager 优先，组件异常时浏览器兜底。 */
object ApkUpdateDownloader {

    private const val TAG = "ApkUpdateDownloader"

    /**
     * 发起下载。enqueue 成功 → 系统通知栏呈现进度，完成后用户点通知安装；
     * enqueue 抛异常（"下载"系统组件被停用等）→ 降级 ACTION_VIEW 打开浏览器直链。
     * 全程无需新增权限（无 REQUEST_INSTALL_PACKAGES）。
     */
    fun download(context: Context, url: String, versionName: String) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = url.substringAfterLast('/')
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("StoryVoice $versionName")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
            }
            dm.enqueue(request)
            Logger.d("$TAG: enqueued $fileName")
        } catch (e: Exception) {
            Logger.e("$TAG: enqueue failed, fallback to browser - ${e.message}")
            fallbackToBrowser(context, url)
        }
    }

    private fun fallbackToBrowser(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Logger.e("$TAG: browser fallback failed - ${e.message}")
        }
    }
}
