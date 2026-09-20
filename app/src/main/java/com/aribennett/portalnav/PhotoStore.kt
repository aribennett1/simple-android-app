package com.aribennett.portalnav

import android.content.Context
import android.util.Log
import java.io.File

object PhotoStore {
    private const val TAG = "PortalNavPhotos"

    fun photoDir(context: Context): File {
        return (context.getExternalFilesDir(AppConfig.PHOTO_DIR) ?: File(context.filesDir, AppConfig.PHOTO_DIR)).apply { mkdirs() }
    }

    fun tempPhotoDir(context: Context): File {
        return File(photoDir(context).parentFile, "${AppConfig.PHOTO_DIR}.tmp").apply { mkdirs() }
    }

    fun photos(context: Context): List<File> {
        val files = photoDir(context).listFiles()?.filter { file ->
            file.isFile && file.length() > 0 && isImageName(file.name)
        }.orEmpty()
        return files.shuffled()
    }

    fun photoCount(context: Context): Int = photos(context).size

    fun fileFor(context: Context, item: RemotePhoto): File {
        return fileFor(photoDir(context), item)
    }

    fun fileFor(dir: File, item: RemotePhoto): File {
        val ext = extensionFor(item)
        return File(dir, "${safe(item.id)}_${safe(item.updated)}.$ext")
    }

    fun removeStale(context: Context, keep: Set<String>): Int {
        var removed = 0
        photoDir(context).listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep && file.delete()) removed++
        }
        if (removed > 0) Log.i(TAG, "Removed $removed stale cached photos")
        return removed
    }

    fun clear(context: Context): Int {
        val removed = clearDir(photoDir(context))
        if (removed > 0) Log.i(TAG, "Cleared $removed cached photos")
        return removed
    }

    fun clearTemp(context: Context): Int {
        val dir = tempPhotoDir(context)
        val removed = clearDir(dir)
        if (removed > 0) Log.i(TAG, "Cleared $removed temp photos")
        return removed
    }

    fun swapTempIntoPhotoDir(context: Context): Int {
        val target = photoDir(context)
        val temp = tempPhotoDir(context)
        val removed = clearDir(target)
        var copied = 0
        temp.listFiles()?.forEach { source ->
            if (!source.isFile) return@forEach
            val dest = File(target, source.name)
            if (!source.copyTo(dest, overwrite = true).exists()) {
                error("Could not copy ${source.name} into cache")
            }
            copied++
        }
        clearDir(temp)
        Log.i(TAG, "Swapped temp photos into cache: copied=$copied, removed=$removed")
        return copied
    }

    private fun clearDir(dir: File): Int {
        var removed = 0
        dir.mkdirs()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearDir(file)
                if (file.delete()) removed++
            } else if (file.delete()) {
                removed++
            }
        }
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
