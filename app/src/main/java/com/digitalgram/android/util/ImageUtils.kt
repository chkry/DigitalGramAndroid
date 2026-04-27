package com.digitalgram.android.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.media.ExifInterface
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ImageUtils {
    fun loadOrientedBitmap(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val orientation = contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val matrix = Matrix().apply { postScale(sx, sy) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

object ImageUtilsAsync {
    @Volatile private var cached: Pair<String, Bitmap>? = null

    private fun decodeSampled(cr: ContentResolver, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        val (h, w) = opts.outHeight to opts.outWidth
        if (h > reqH || w > reqW) {
            val halfH = h / 2; val halfW = w / 2
            while (halfH / sample >= reqH && halfW / sample >= reqW) sample *= 2
        }
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
    }

    suspend fun getWallpaper(cr: ContentResolver, uri: Uri, reqW: Int, reqH: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        cached?.takeIf { it.first == key }?.second?.let { return@withContext it }
        val bmp = decodeSampled(cr, uri, reqW, reqH) ?: return@withContext null
        cached = key to bmp
        bmp
    }

    fun loadWallpaperAsync(cr: ContentResolver, uri: Uri, target: View, resources: android.content.res.Resources, scope: CoroutineScope) {
        val w = target.width.coerceAtLeast(512)
        val h = target.height.coerceAtLeast(512)
        scope.launch {
            val bmp = getWallpaper(cr, uri, w, h) ?: return@launch
            withContext(Dispatchers.Main) { target.background = BitmapDrawable(resources, bmp) }
        }
    }

    fun clearCache() { cached = null }
}
