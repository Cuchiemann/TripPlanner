package com.cuchieman.tripplanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cover photos live in the app's own storage as small JPEGs, not as links to the gallery:
 * a link breaks when the photo is moved or deleted, and needs a permission that can expire.
 */
class CoverStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "covers").apply { mkdirs() }

    /** Copies the picked image, scaled down to at most [maxSide] px, and returns its path. */
    suspend fun save(tripId: String, uri: Uri, maxSide: Int = 1600): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ValidationException("No se ha podido leer la foto")

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ValidationException("No se ha podido leer la foto")

        val file = File(dir, "$tripId-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        file.absolutePath
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path != null && path.startsWith(dir.absolutePath)) File(path).delete()
    }
}
