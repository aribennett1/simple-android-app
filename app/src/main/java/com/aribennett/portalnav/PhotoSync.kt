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
import kotlin.math.min
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class RemotePhoto(
    val id: String,
    val name: String,
    val mimeType: String,
    val updated: String,
    val url: String
)

object PhotoSync {
    private const val TAG = "PortalNavPhotos"
    private const val PARALLEL_DOWNLOADS = 4
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
        val tempDir = PhotoStore.tempPhotoDir(context)
        val cleared = PhotoStore.clearTemp(context)
        Log.i(TAG, "Sync staging into ${tempDir.absolutePath}; clearedTemp=$cleared")

        val pool = Executors.newFixedThreadPool(PARALLEL_DOWNLOADS)
        val completed = AtomicInteger(0)
        val downloaded = AtomicInteger(0)
        val reused = AtomicInteger(0)
        val failures = ConcurrentLinkedQueue<Throwable>()
        for (item in manifest) {
            pool.submit {
                try {
                    val target = PhotoStore.fileFor(tempDir, item)
                    val existing = PhotoStore.fileFor(context, item)
                    if (existing.exists() && existing.length() > 0) {
                        existing.copyTo(target, overwrite = true)
                        reused.incrementAndGet()
                    } else {
                        download(item.url, target)
                        downloaded.incrementAndGet()
                    }
                    val done = completed.incrementAndGet()
                    SlideshowActivity.updateSyncProgressIfVisible(done, manifest.size)
                } catch (t: Throwable) {
                    failures.add(t)
                }
            }
        }
        pool.shutdown()
        while (!pool.isTerminated) {
            Thread.sleep(250L)
        }

        failures.peek()?.let { throw it }

        val copied = PhotoStore.swapTempIntoPhotoDir(context)
        return "items=${manifest.size}, downloaded=${downloaded.get()}, reused=${reused.get()}, swapped=$copied"
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
        val connection = openWithRetry(url)
        try {
            BufferedInputStream(connection.inputStream).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (tmp.length() <= 0) {
            tmp.delete()
            error("Downloaded empty file for ${target.name}")
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("Could not move temp file into cache")
        Log.i(TAG, "Downloaded ${target.name}")
    }

    private fun openWithRetry(url: String): HttpURLConnection {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return open(url)
            } catch (t: Throwable) {
                last = t
                val delay = min(4_000L, 750L * (attempt + 1))
                Log.w(TAG, "Download open failed; retry ${attempt + 1}/3 in ${delay}ms: ${t.message}")
                Thread.sleep(delay)
            }
        }
        throw last ?: IllegalStateException("Could not open $url")
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
