package com.r0ybt.taleframe.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Static video frames only; scrolling the catalog never allocates a MediaPlayer. */
private object PosterCache {
    private val cache=object : LruCache<String,Bitmap>(8*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    fun load(path:String):ImageBitmap? {
        synchronized(cache) {cache.get(path)}?.let {return it.asImageBitmap()}
        val retriever=MediaMetadataRetriever()
        val bitmap=try {
            retriever.setDataSource(path)
            if(Build.VERSION.SDK_INT>=27) retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,384,512)
            else retriever.getFrameAtTime(0)?.let {full->
                val ratio=(384f/maxOf(full.width,full.height)).coerceAtMost(1f)
                val scaled=Bitmap.createScaledBitmap(full,(full.width*ratio).toInt().coerceAtLeast(1),(full.height*ratio).toInt().coerceAtLeast(1),true)
                if(scaled!==full) full.recycle()
                scaled
            }
        } catch(_:Exception) {null} finally {retriever.release()}
        if(bitmap!=null) synchronized(cache) {cache.put(path,bitmap)}
        return bitmap?.asImageBitmap()
    }
}
@Composable
internal fun localPoster(path:String?):ImageBitmap? {
    val result by produceState<ImageBitmap?>(null,path) {
        value=if(path==null) null else withContext(Dispatchers.IO) {PosterCache.load(path)}
    }
    return result
}
