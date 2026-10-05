package com.r0ybt.taleframe.data

data class Project(val id: Long, val name: String, val coverResourceId: Long? = null, val initialSlideId: Long? = null, val skipDrafts: Boolean = false, val automaticBaseNavigation: Boolean = false)
data class Slide(
    val id: Long, val projectId: Long, val name: String, val color: Int = -1,
    val image: String? = null, val order: Int = 0,
    val backgroundMode: String = "fill", val backgroundScale: Float = 1f,
    val backgroundX: Float = 0f, val backgroundY: Float = 0f,
    val backgroundLocked: Boolean = false,
    val media: MediaOptions = MediaOptions(), val audio: String? = null,
    val audioLoop: Boolean = true, val audioVolume: Float = 1f, val audioRevision: Long = 0,
    val autoEnabled: Boolean = false, val autoSeconds: Float = 5f,
    val autoTargetId: Long? = null, val transition: Transition = Transition(),
    val backgroundResourceId: Long? = null, val audioResourceId: Long? = null, val draft: Boolean = false
)

// Legacy x/y are normalized over available travel, preserving existing compositions.
// freePosition uses stage-relative origins so images can cross 100% size without a singularity.
// A zero width/height retains legacy automatic text sizing until explicitly resized.
data class Element(
    val id: Long, val slideId: Long, val kind: String, val text: String,
    val x: Float = .1f, val y: Float = .1f, val textColor: Int = -16777216,
    val backgroundColor: Int = -1, val targetId: Long? = null,
    val image: String? = null, val width: Float = 0f, val height: Float = 0f,
    val rotation: Float = 0f, val flipped: Boolean = false,
    val opacity: Float = 1f, val locked: Boolean = false, val layer: Int = 0,
    val media: MediaOptions = MediaOptions(), val transition: Transition = Transition(),
    val style: VisualStyle = VisualStyle(), val speakerName: String = "", val sourceName: String = "",
    val resourceId: Long? = null, val characterId: Long? = null, val expressionId: Long? = null, val presetId: Long? = null,
    val freePosition: Boolean = false, val expressionFrames: List<Long> = emptyList(), val panel: PanelOptions? = null, val baseNavigation: String? = null
)
data class Story(val projects: List<Project> = emptyList(), val slides: List<Slide> = emptyList(), val elements: List<Element> = emptyList(),
    val resources: List<Resource> = emptyList(), val characters: List<Character> = emptyList(),
    val expressions: List<Expression> = emptyList(), val presets: List<Preset> = emptyList(), val templates: List<SlideTemplate> = emptyList())
fun boundedPosition(value: Float): Float = boundedValue(value, 0f, 1f, 0f)
fun boundedValue(value: Float, min: Float, max: Float, fallback: Float): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

/** Free framing applies only to standalone images/GIF, preserving panels and characters. */
val Element.supportsFreePlacement: Boolean
    get() = kind == "image" && panel == null && characterId == null && media.type in listOf("image", "gif")
fun finitePosition(value: Float): Float = if (value.isFinite()) value else 0f
fun Element.storedPosition(value: Float): Float = if (supportsFreePlacement) finitePosition(value) else boundedPosition(value)
fun Element.storedSize(value: Float, minimum: Float = 0f, fallback: Float = 0f): Float =
    if (supportsFreePlacement) { if (value.isFinite()) value.coerceAtLeast(minimum) else fallback }
    else boundedValue(value, minimum, 1f, fallback)
