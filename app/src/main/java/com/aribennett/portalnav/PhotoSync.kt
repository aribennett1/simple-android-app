package com.aribennett.portalnav

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class RemotePhoto(
    val id: String,
    val name: String,
    val mimeType: String,
    val updated: String,
    val url: String
)

object PhotoSync {
    private const val TAG = "PortalNavPhotos"
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    fun request(context: Context, reason: String, toast: Boolean = false) {
        val appContext = context.applicationContext
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "Sync already running; skipped $reason request")
            if (toast) showToast(appContext, "Sync already running")
            return
        }

        Log.i(TAG, "Sync started: $reason")
        if (toast) showToast(appContext, "Sync started")
        executor.execute {
            try {
                val result = syncNow(appContext)
                Prefs.setLastSync(appContext, System.currentTimeMillis(), "ok: $result")
                Log.i(TAG, "Sync success: $result")
                SlideshowActivity.rescanIfVisible()
                if (toast) showToast(appContext, "Sync complete")
            } catch (t: Throwable) {
                Prefs.setLastSync(appContext, System.currentTimeMillis(), "failed: ${t.message}")
                Log.e(TAG, "Sync failed", t)
                if (toast) showToast(appContext, "Sync failed")
            } finally {
                running.set(false)
            }
        }
    }

    private fun syncNow(context: Context): String {
        val manifest = fetchManifest()
        var downloaded = 0
        val tempDir = PhotoStore.tempPhotoDir(context)
        val cleared = PhotoStore.clearTemp(context)
        Log.i(TAG, "Sync staging into ${tempDir.absolutePath}; clearedTemp=$cleared")

        for (item in manifest) {
            val target = PhotoStore.fileFor(tempDir, item)
            download(item.url, target)
            downloaded++
            SlideshowActivity.updateSyncProgressIfVisible(downloaded, manifest.size)
        }

        val copied = PhotoStore.swapTempIntoPhotoDir(context)
        return "items=${manifest.size}, downloaded=$downloaded, swapped=$copied"
    }

    private fun fetchManifest(): List<RemotePhoto> {
        val text = getText(AppConfig.GAS_ENDPOINT)
        if (!text.trimStart().startsWith("[")) {
            error("Manifest endpoint did not return JSON")
        }
        val array = JSONArray(text)
        val photos = ArrayList<RemotePhoto>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val mimeType = obj.optString("mimeType")
            if (!mimeType.startsWith("image/")) continue
            val photo = RemotePhoto(
                id = obj.getString("id"),
                name = obj.getString("name"),
                mimeType = mimeType,
                updated = obj.getString("updated"),
                url = obj.getString("url")
            )
            if (photo.id.isNotBlank() && photo.updated.isNotBlank() && photo.url.isNotBlank()) {
                photos += photo
            }
        }
        Log.i(TAG, "Manifest item count: ${photos.size}")
        return photos
    }

    private fun getText(url: String): String {
        val connection = open(url)
        return connection.inputStream.bufferedReader().use { it.readText() }.also {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        val connection = open(url)
        BufferedInputStream(connection.inputStream).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()
        if (tmp.length() <= 0) {
            tmp.delete()
            error("Downloaded empty file for ${target.name}")
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("Could not move temp file into cache")
        Log.i(TAG, "Downloaded ${target.name}")
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 45_000
        connection.setRequestProperty("User-Agent", "PortalNav/1.0")
        val code = connection.responseCode
        if (code !in 200..299) error("HTTP $code for $url")
        return connection
    }

    private fun showToast(context: Context, text: String) {
        main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
