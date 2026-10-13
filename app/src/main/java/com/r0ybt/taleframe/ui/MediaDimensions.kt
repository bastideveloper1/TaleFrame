package com.r0ybt.taleframe.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val mediaAspects = ConcurrentHashMap<String, Float>()

/** Logical file dimensions, never the opaque-pixel bounds of a GIF frame. */
@Composable
fun localMediaAspect(path: String?, type: String): Float? {
    val aspect by produceState(path?.let { mediaAspects[it] }, path, type) {
        value = path?.let { file -> withContext(Dispatchers.IO) {
            mediaAspects[file] ?: runCatching {
                if (type == "video") {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(file)
                        val w = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloat() ?: 0f
                        val h = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloat() ?: 0f
                        val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                        if (rotation % 180 != 0) h / w else w / h
                    } finally { metadata.release() }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file, bounds)
                    bounds.outWidth.toFloat() / bounds.outHeight
                }
            }.getOrNull()?.takeIf { it.isFinite() && it > 0f }?.also { mediaAspects[file] = it }
        } }
    }
    return aspect
}
