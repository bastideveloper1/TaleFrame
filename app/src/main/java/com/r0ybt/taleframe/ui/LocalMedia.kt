package com.r0ybt.taleframe.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.r0ybt.taleframe.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** STOP removes players/decoders entirely; resume begins a fresh local playback. */
@Composable
internal fun foreground(): Boolean {
    val owner = LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return started
}

/** No player key depends on editor geometry, so dragging doesn't rebuild a decoder. */
@Composable
fun LocalMedia(path: String?, options: MediaOptions, modifier: Modifier = Modifier, preview: Boolean = false, fill: Boolean = false, editing: Boolean = false) {
    val active = foreground() && !preview
    var tick by remember(path, options) { mutableLongStateOf(0) }
    LaunchedEffect(path, options, active) {
        tick = 0
        if (active && options.type == "slideshow") {
            while (options.loop || tick < options.frames.lastIndex) { delay((options.seconds.coerceIn(.2f, 3600f) * 1000).toLong()); tick++ }
        }
    }
    val displayed = if (options.type == "slideshow" && options.frames.isNotEmpty()) options.frames[frameIndex(tick, options.frames.size, options.loop)] else path
    key(options.revision) {
    Box(modifier.then(if (options.type == "slideshow") Modifier.semantics {
        contentDescription = "Imagen ${frameIndex(tick, options.frames.size, options.loop) + 1} de ${options.frames.size}"
    } else Modifier), contentAlignment = Alignment.Center) {
        when {
            options.type == "video" && active -> {
                localPoster(path)?.let {Image(it,null,Modifier.fillMaxSize(),contentScale=if(fill) ContentScale.Crop else ContentScale.Fit)}
                VideoMedia(path, options, fill, editing)
            }
            options.type == "gif" && active -> GifMedia(path, options.loop, fill)
            options.type == "video" -> {
                val poster = localPoster(path)
                poster?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (fill) ContentScale.Crop else ContentScale.Fit) }
            }
            else -> {
                val bitmap = localImage(displayed, if (preview) 384 else 1536)
                bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (fill) ContentScale.Crop else ContentScale.Fit) }
                if (displayed != null && bitmap == null) Text("Cargando / recurso no disponible", color = Color.White, modifier = Modifier.background(Color(0x99000000)))
            }
        }
    }
    }
}

@Composable
private fun GifMedia(path: String?, loop: Boolean, fill: Boolean) {
    var error by remember(path) { mutableStateOf<String?>(null) }
    if (Build.VERSION.SDK_INT >= 28) {
        val drawable by produceState<Drawable?>(null, path, loop) {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(requireNotNull(path)))) { decoder, info, _ ->
                        val factor = (1536f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                        decoder.setTargetSize((info.size.width * factor).toInt().coerceAtLeast(1), (info.size.height * factor).toInt().coerceAtLeast(1))
                    }.also { if (it is AnimatedImageDrawable) it.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0 }
                }
            }
            value = decoded.getOrNull()
            error = if (decoded.isFailure) "GIF no disponible. Reemplázalo o elimínalo." else null
        }
        if(drawable==null) localImage(path,384)?.let {Image(it,null,Modifier.fillMaxSize(),contentScale=if(fill) ContentScale.Crop else ContentScale.Fit)}
        val ownedDrawable = drawable
        DisposableEffect(ownedDrawable) { onDispose { (ownedDrawable as? AnimatedImageDrawable)?.stop(); ownedDrawable?.callback = null } }
        AndroidView(factory = { context -> ImageView(context).apply {
            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) { (drawable as? AnimatedImageDrawable)?.start() }
                override fun onViewDetachedFromWindow(view: android.view.View) { (drawable as? AnimatedImageDrawable)?.stop() }
            })
        } }, modifier = Modifier.fillMaxSize(), update = {
            it.scaleType = if (fill) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (it.drawable !== drawable) { it.setImageDrawable(drawable); if (it.isAttachedToWindow) (drawable as? AnimatedImageDrawable)?.start() }
        })
    } else {
        // Android 7/8 fallback. Movie is local and bounded at import, no network dependency.
        val movie by produceState<Movie?>(null, path) { value = withContext(Dispatchers.IO) { try { Movie.decodeFile(path) } catch (_: Exception) { null } } }
        if(movie==null) localImage(path,384)?.let {Image(it,null,Modifier.fillMaxSize(),contentScale=if(fill) ContentScale.Crop else ContentScale.Fit)}
        AndroidView(factory = { context -> LegacyGifView(context) }, modifier = Modifier.fillMaxSize(), update = { it.configure(movie, loop, fill) }, onRelease = { it.running = false })
        if (movie == null) error = "GIF no disponible. Reemplázalo o elimínalo."
    }
    error?.let { Text(it, color = Color.White, modifier = Modifier.background(Color(0x99000000))) }
}
@Suppress("DEPRECATION")
private class LegacyGifView(context: Context) : android.view.View(context) {
    var running = true
    private var movie: Movie? = null
    private var loop = true
    private var fill = false
    private var epoch = android.os.SystemClock.uptimeMillis()
    fun configure(value: Movie?, repeat: Boolean, crop: Boolean) {
        if (movie !== value) { movie = value; epoch = android.os.SystemClock.uptimeMillis() }
        loop = repeat; fill = crop; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        val m = movie ?: return
        val duration = m.duration().coerceAtLeast(1)
        val elapsed = android.os.SystemClock.uptimeMillis() - epoch
        m.setTime(if (loop) (elapsed % duration).toInt() else elapsed.coerceAtMost((duration - 1).toLong()).toInt())
        val sx = width.toFloat() / m.width().coerceAtLeast(1); val sy = height.toFloat() / m.height().coerceAtLeast(1)
        val scale = if (fill) maxOf(sx, sy) else minOf(sx, sy)
        canvas.save(); canvas.translate((width - m.width() * scale) / 2, (height - m.height() * scale) / 2); canvas.scale(scale, scale); m.draw(canvas, 0f, 0f); canvas.restore()
        if (running && (loop || elapsed < duration)) postInvalidateOnAnimation()
    }
}

/** One owner for all MediaPlayer states; callbacks become inert before release. */
internal object MediaDiagnostics {
    @Volatile var allocated = 0; internal set
    @Volatile var created = 0; internal set
}
internal class LocalPlayer(private val onError: (String) -> Unit = {}) {
    private var player: MediaPlayer? = null
    private var prepared = false
    private var ended = false
    fun open(path: String?, options: MediaOptions, surface: Surface? = null, size: (Int, Int) -> Unit = { _, _ -> }) {
        release()
        val p = MediaPlayer(); player = p; MediaDiagnostics.allocated++; MediaDiagnostics.created++
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            p.setDataSource(requireNotNull(path)); p.setSurface(surface)
            p.isLooping = options.loop
            val volume = if (options.muted) 0f else boundedPosition(options.volume)
            p.setVolume(volume, volume)
            p.setOnVideoSizeChangedListener { _, w, h -> if (player === p) size(w, h) }
            p.setOnPreparedListener {
                if (player === p) {
                    prepared = true; size(p.videoWidth, p.videoHeight)
                    if (options.autoplay) p.start() else if (surface != null) p.seekTo(0)
                }
            }
            p.setOnCompletionListener { if (player === p) ended = true }
            p.setOnErrorListener { _, _, _ -> if (player === p) { release(); onError("Formato no compatible o recurso dañado. Reemplázalo o elimínalo.") }; true }
            p.prepareAsync()
        } catch (_: Exception) { release(); onError("No se pudo abrir el recurso. Reemplázalo o elimínalo.") }
    }
    fun toggle() {
        val p = player ?: return
        if (!prepared) return
        try { if (p.isPlaying) p.pause() else { if (ended) { p.seekTo(0); ended = false }; p.start() } }
        catch (_: Exception) { release(); onError("No se pudo reproducir el recurso") }
    }
    fun release() {
        val old = player; player = null; prepared = false; ended = false
        old?.setOnPreparedListener(null); old?.setOnErrorListener(null); old?.setOnCompletionListener(null); old?.setOnVideoSizeChangedListener(null)
        if (old != null) { old.release(); MediaDiagnostics.allocated-- }
    }
}

@Composable
private fun VideoMedia(path: String?, options: MediaOptions, fill: Boolean, editing: Boolean) {
    var error by remember(path) { mutableStateOf<String?>(null) }
    val controller = remember(path, options, fill) { LocalPlayer { error = it } }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(controller, texture) {
        val view = texture
        var surface: Surface? = null
        var videoWidth = 0; var videoHeight = 0
        fun transform() {
            if (view == null || videoWidth <= 0 || videoHeight <= 0 || view.width == 0 || view.height == 0) return
            val scale = if (fill) maxOf(view.width.toFloat() / videoWidth, view.height.toFloat() / videoHeight) else minOf(view.width.toFloat() / videoWidth, view.height.toFloat() / videoHeight)
            view.setTransform(Matrix().apply { setScale(videoWidth * scale / view.width, videoHeight * scale / view.height, view.width / 2f, view.height / 2f) })
        }
        fun start(st: SurfaceTexture) {
            view?.alpha=0f
            surface?.release(); surface = Surface(st)
            controller.open(path, options, surface) { w, h -> videoWidth = w; videoHeight = h; transform() }
        }
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = start(st)
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = transform()
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { controller.release(); surface?.release(); surface = null; return true }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) { view?.alpha=1f }
        }
        view?.surfaceTextureListener = listener
        view?.surfaceTexture?.let(::start)
        onDispose { view?.surfaceTextureListener = null; controller.release(); surface?.release() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { TextureView(it).apply { isOpaque = false; alpha=0f; texture = this } }, modifier = Modifier.fillMaxSize())
        if (!options.autoplay && !editing) TextButton(onClick = { controller.toggle() }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("▶ / Pausa") }
        error?.let { Text(it, color = Color.White, modifier = Modifier.background(Color(0xAA000000))) }
    }
}

@Composable
fun SlideAudio(slide: Slide, preview: Boolean) {
    val active = foreground() && !preview
    var error by remember(slide.audio) { mutableStateOf<String?>(null) }
    DisposableEffect(slide.audio, slide.audioRevision, slide.audioLoop, slide.audioVolume, active) {
        val player = LocalPlayer { error = it }
        if (active && slide.audio != null) player.open(slide.audio, MediaOptions(loop = slide.audioLoop, muted = false, volume = slide.audioVolume))
        onDispose { player.release() }
    }
    error?.let { Text("Audio: $it", color = Color.White, modifier = Modifier.background(Color(0xAA000000))) }
}
