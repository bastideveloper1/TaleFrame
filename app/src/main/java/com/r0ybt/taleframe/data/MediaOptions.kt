package com.r0ybt.taleframe.data

import org.json.JSONArray
import org.json.JSONObject

data class Transition(val type: String = "fade", val millis: Int = 300) {
    val duration: Int get() = if (type == "none") 0 else millis.coerceIn(200, 1000)
}
data class MediaOptions(
    val type: String = "image", val loop: Boolean = true, val autoplay: Boolean = true,
    val muted: Boolean = true, val volume: Float = 1f,
    val frames: List<String> = emptyList(), val seconds: Float = 3f, val revision: Long = 0
)

/** A visit consumes at most one exit. Old timer tokens cannot exit a later visit. */
class ExitGate {
    var token: Long = 0; private set
    private var consumed = false
    fun enter(): Long { token++; consumed = false; return token }
    fun take(visit: Long): Boolean {
        if (visit != token || consumed) return false
        consumed = true; return true
    }
}
fun frameIndex(tick: Long, count: Int, loop: Boolean): Int =
    if (count <= 0) 0 else if (loop) (tick % count).toInt() else tick.coerceAtMost((count - 1).toLong()).toInt()

private fun Transition.json() = JSONObject().put("type", type).put("ms", millis.coerceIn(200, 1000))
private fun JSONObject.transition(): Transition {
    val t = optJSONObject("transition") ?: return Transition()
    return Transition(t.optString("type", "fade").takeIf { it in listOf("none", "fade", "left", "right") } ?: "fade", t.optInt("ms", 300).coerceIn(200, 1000))
}
private fun MediaOptions.json() = JSONObject().put("type", type).put("loop", loop).put("autoplay", autoplay)
    .put("muted", muted).put("volume", boundedPosition(volume)).put("frames", JSONArray(frames))
    .put("seconds", boundedValue(seconds, .2f, 3600f, 3f)).put("revision", revision)
private fun JSONObject.media(): MediaOptions {
    val m = optJSONObject("media") ?: return MediaOptions()
    val frames = m.optJSONArray("frames")
    return MediaOptions(m.optString("type", "image").takeIf { it in listOf("image", "gif", "video", "slideshow") } ?: "image",
        m.optBoolean("loop", true), m.optBoolean("autoplay", true), m.optBoolean("muted", true),
        boundedPosition(m.optDouble("volume", 1.0).toFloat()),
        if (frames == null) emptyList() else (0 until frames.length()).mapNotNull { frames.optString(it).takeIf(String::isNotBlank) },
        boundedValue(m.optDouble("seconds", 3.0).toFloat(), .2f, 3600f, 3f), m.optLong("revision", 0))
}
private fun parse(raw: String) = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
fun Element.settings(): String = JSONObject().put("media", media.json()).put("transition", transition.json()).put("style", style.json()).put("speaker", speakerName).put("source", sourceName).put("expressionFrames", JSONArray(expressionFrames)).toString()
fun Element.withSettings(raw: String): Element = parse(raw).let { copy(media = it.media(), transition = it.transition(), style = it.visualStyle(), speakerName = it.optString("speaker"), sourceName = it.optString("source"),
    expressionFrames = it.optJSONArray("expressionFrames")?.let { frames -> (0 until frames.length()).map { i -> frames.optLong(i) }.filter { id -> id > 0 } } ?: emptyList()) }
fun Slide.settings(): String = JSONObject().put("media", media.json()).put("audio", audio)
    .put("audioRevision", audioRevision).put("audioLoop", audioLoop).put("audioVolume", boundedPosition(audioVolume))
    .put("autoEnabled", autoEnabled).put("autoSeconds", boundedValue(autoSeconds, .2f, 3600f, 5f))
    .put("transition", transition.json()).toString()
fun Slide.withSettings(raw: String, target: Long?): Slide = parse(raw).let { copy(media = it.media(),
    audio = it.optString("audio").takeIf { path -> path.isNotBlank() && path != "null" },
    audioRevision = it.optLong("audioRevision", 0), audioLoop = it.optBoolean("audioLoop", true), audioVolume = boundedPosition(it.optDouble("audioVolume", 1.0).toFloat()),
    autoEnabled = it.optBoolean("autoEnabled", false), autoSeconds = boundedValue(it.optDouble("autoSeconds", 5.0).toFloat(), .2f, 3600f, 5f),
    autoTargetId = target, transition = it.transition()) }
