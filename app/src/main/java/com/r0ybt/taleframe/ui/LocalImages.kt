package com.r0ybt.taleframe.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object LocalImageCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    fun load(path: String, maxSide: Int): ImageBitmap? {
        val key = "$path:$maxSide"
        synchronized(cache) { cache.get(key) }?.let { return it.asImageBitmap() }
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                    val ratio = maxOf(info.size.width, info.size.height).toFloat() / maxSide
                    if (ratio > 1f) decoder.setTargetSize((info.size.width / ratio).toInt().coerceAtLeast(1), (info.size.height / ratio).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) { null }
        if (bitmap != null) synchronized(cache) { cache.put(key, bitmap) }
        return bitmap?.asImageBitmap()
    }
}

@Composable
fun localImage(path: String?, maxSide: Int): ImageBitmap? {
    val result by produceState<ImageBitmap?>(null, path, maxSide) {
        value = null
        if (path != null) value = withContext(Dispatchers.IO) { LocalImageCache.load(path, maxSide) }
    }
    return result
}
