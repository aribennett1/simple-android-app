package com.aribennett.portalnav

import android.content.Context
import android.util.Log
import java.io.File

object PhotoStore {
    private const val TAG = "PortalNavPhotos"

    fun photoDir(context: Context): File {
        return (context.getExternalFilesDir(AppConfig.PHOTO_DIR) ?: File(context.filesDir, AppConfig.PHOTO_DIR)).apply { mkdirs() }
    }

    fun photos(context: Context): List<File> {
        val files = photoDir(context).listFiles()?.filter { file ->
            file.isFile && file.length() > 0 && isImageName(file.name)
        }.orEmpty()
        return files.shuffled()
    }

    fun photoCount(context: Context): Int = photos(context).size

    fun fileFor(context: Context, item: RemotePhoto): File {
        val ext = extensionFor(item)
        return File(photoDir(context), "${safe(item.id)}_${safe(item.updated)}.$ext")
    }

    fun removeStale(context: Context, keep: Set<String>): Int {
        var removed = 0
        photoDir(context).listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep && file.delete()) removed++
        }
        if (removed > 0) Log.i(TAG, "Removed $removed stale cached photos")
        return removed
    }

    private fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private fun extensionFor(item: RemotePhoto): String {
        val fromName = item.name.substringAfterLast('.', "").lowercase()
        if (fromName in setOf("jpg", "jpeg", "png", "webp")) return fromName
        return when (item.mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun safe(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
