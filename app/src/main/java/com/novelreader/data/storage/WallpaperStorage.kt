package com.novelreader.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.novelreader.data.local.preferences.PreferenceAllowlists
import com.novelreader.di.qualifiers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    suspend fun importFromUri(slot: String, uri: Uri): String? = withContext(io) {
        if (slot !in SLOTS) return@withContext null
        val extension = extensionFor(uri) ?: return@withContext null
        val previous = currentRef(slot)
        val destination = fileFor(slot, System.currentTimeMillis(), extension)
        val copied = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    copyWithLimit(input, output, MAX_BYTES)
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
        if (!copied) {
            destination.delete()
            return@withContext null
        }
        if (previous != null && previous != "file:${destination.name}") delete(previous)
        "file:${destination.name}"
    }

    suspend fun saveCropped(
        slot: String,
        uri: Uri,
        crop: WallpaperCrop,
        targetWidth: Int,
        targetHeight: Int
    ): String? = withContext(io) {
        if (slot !in SLOTS) return@withContext null
        if (targetWidth <= 0 || targetHeight <= 0) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        try {
            // inJustDecodeBounds returns null by design: only the Options carry the answer.
            BitmapFactory.decodeStream(boundsStream, null, bounds)
        } finally {
            boundsStream.close()
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@withContext null

        val rect = cropRectFor(
            crop = crop,
            srcWidth = decoded.width.toFloat(),
            srcHeight = decoded.height.toFloat(),
            targetWidth = targetWidth.toFloat(),
            targetHeight = targetHeight.toFloat()
        )
        val x = rect.left.toInt().coerceIn(0, maxOf(0, decoded.width - 1))
        val y = rect.top.toInt().coerceIn(0, maxOf(0, decoded.height - 1))
        val width = rect.width.toInt().coerceAtLeast(1).coerceAtMost(decoded.width - x)
        val height = rect.height.toInt().coerceAtLeast(1).coerceAtMost(decoded.height - y)

        val cropped = try {
            Bitmap.createBitmap(decoded, x, y, width, height)
        } catch (_: Exception) {
            return@withContext null
        }
        val scaled = if (cropped.width == targetWidth && cropped.height == targetHeight) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        }

        val previous = currentRef(slot)
        val destination = fileFor(slot, System.currentTimeMillis(), "jpg")
        val written = try {
            destination.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, output)
            }
        } catch (_: Exception) {
            false
        }
        if (scaled !== cropped) scaled.recycle()
        cropped.recycle()
        decoded.recycle()
        if (!written) {
            destination.delete()
            return@withContext null
        }
        if (previous != null && previous != "file:${destination.name}") delete(previous)
        "file:${destination.name}"
    }

    suspend fun exists(ref: String): Boolean = withContext(io) {
        resolveFile(context.filesDir, ref) != null
    }

    suspend fun delete(ref: String): Boolean = withContext(io) {
        val file = resolveFile(context.filesDir, ref) ?: return@withContext false
        file.delete()
    }

    suspend fun clearSlot(slot: String) {
        if (slot !in SLOTS) return
        wallpapersDir().listFiles()
            ?.filter { it.name.startsWith("${slot}_") }
            ?.forEach { it.delete() }
    }

    private suspend fun currentRef(slot: String): String? = withContext(io) {
        wallpapersDir().listFiles()
            ?.firstOrNull { it.name.startsWith("${slot}_") }
            ?.let { "file:${it.name}" }
    }

    private fun copyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        limit: Long
    ): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > limit) return false
            output.write(buffer, 0, read)
        }
    }

    private fun extensionFor(uri: Uri): String? {
        val fromMime = when (context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> null
        }
        if (fromMime != null) return fromMime
        val name = uri.lastPathSegment?.substringAfterLast('/')?.lowercase() ?: return null
        return when (name.substringAfterLast('.', "")) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            else -> null
        }
    }

    private fun wallpapersDir(): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun fileFor(slot: String, timestamp: Long, extension: String): File =
        File(wallpapersDir(), "${slot}_$timestamp.$extension")

    companion object {
        const val DIR = "wallpapers"
        const val SLOT_HOME = "home"
        const val SLOT_READER = "reader"
        val SLOTS = setOf(SLOT_HOME, SLOT_READER)
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_DECODE_EDGE = 4096
        private const val CROP_QUALITY = 92

        private val FILE_NAME = Regex("^[a-z0-9_]{1,64}\\.(jpg|jpeg|png|webp)$")

        fun isBuiltin(ref: String): Boolean =
            ref.startsWith("builtin:") &&
                ref.removePrefix("builtin:") in PreferenceAllowlists.BUILTIN_WALLPAPERS

        fun builtinId(ref: String): String? =
            if (isBuiltin(ref)) ref.removePrefix("builtin:") else null

        fun fileNameOf(ref: String): String? {
            if (!ref.startsWith("file:")) return null
            val name = ref.removePrefix("file:")
            return name.takeIf { FILE_NAME.matches(it) }
        }

        fun resolveFile(filesDir: File, ref: String): File? {
            val name = fileNameOf(ref) ?: return null
            val root = File(filesDir, DIR).canonicalFile
            val file = File(root, name).canonicalFile
            if (file.parentFile != root) return null
            return file.takeIf { it.isFile }
        }
    }
}
