package com.r0ybt.taleframe.data

import org.json.JSONObject

/** Styles are snapshots on elements. Library IDs describe origin, never drive live rendering. */
data class VisualStyle(
    val shape: String = "legacy", val font: String = "default", val textScale: Float = 1f,
    val alignment: String = "start", val backgroundOpacity: Float = 1f,
    val borderColor: Int = -1, val borderWidth: Float = 0f, val showName: Boolean = false, val bold: Boolean = false, val italic: Boolean = false, val buttonEffect: String = "none"
)
data class Resource(val id: Long, val projectId: Long, val name: String, val type: String, val category: String, val path: String, val revision: Long = 0)
data class Character(val id: Long, val projectId: Long, val name: String, val description: String = "", val portraitId: Long? = null, val dialogPresetId: Long? = null)
data class Expression(val id: Long, val characterId: Long, val name: String, val resourceId: Long, val order: Int = 0)
data class Preset(
    val id: Long, val projectId: Long, val name: String, val kind: String = "dialog",
    val textColor: Int = -1, val backgroundColor: Int = 0xFF263238.toInt(),
    val style: VisualStyle = VisualStyle(shape = "rounded"), val transition: Transition = Transition()
)
data class ResourceUsage(val elements: Int = 0, val backgrounds: Int = 0, val audio: Int = 0, val expressions: Int = 0, val portraits: Int = 0, val covers: Int = 0, val templates: Int = 0) {
    val total get() = elements + backgrounds + audio + expressions + portraits + covers + templates
    val description get() = "$elements elementos, $backgrounds fondos, $audio audios, $expressions expresiones y $portraits retratos, $covers portadas y $templates plantillas"
}
fun resourceUsage(story: Story, resource: Resource): ResourceUsage = resourceUsageIndex(story,resource.projectId)[resource.id] ?: ResourceUsage()
/** Index paths once: large catalogs don't scan every element for every visible card. */
fun resourceUsageIndex(story: Story, projectId: Long): Map<Long,ResourceUsage> {
    val resources=story.resources.filter {it.projectId==projectId}
    val byPath=resources.groupBy {it.path}.mapValues {it.value.map {r->r.id}}
    val counts=resources.associate {it.id to IntArray(7)}
    val slides=story.slides.filter {it.projectId==projectId};val slideIds=slides.map {it.id}.toSet()
    fun increment(ids:Set<Long>,slot:Int) {ids.forEach {counts[it]?.let {row->row[slot]++}}}
    fun ids(path:String?,extra:List<String>,id:Long?):Set<Long> = buildSet {
        id?.let(::add);path?.let {byPath[it]?.let(::addAll)};extra.forEach {byPath[it]?.let(::addAll)}
    }
    story.elements.filter {it.slideId in slideIds}.forEach {increment(ids(it.image,it.media.frames,it.resourceId),0)}
    slides.forEach {increment(ids(it.image,it.media.frames,it.backgroundResourceId),1);increment(ids(it.audio,emptyList(),it.audioResourceId),2)}
    story.expressions.forEach {increment(setOf(it.resourceId),3)}
    story.characters.filter {it.projectId==projectId}.forEach {it.portraitId?.let {id->increment(setOf(id),4)}}
    story.projects.filter {it.id==projectId}.forEach {it.coverResourceId?.let {id->increment(setOf(id),5)}}
    story.templates.filter {it.projectId==projectId}.forEach {template->
        val all=buildSet {addAll(ids(template.slide.image,template.slide.media.frames,template.slide.backgroundResourceId));addAll(ids(template.slide.audio,emptyList(),template.slide.audioResourceId));template.elements.forEach {addAll(ids(it.image,it.media.frames,it.resourceId))}}
        increment(all,6)
    }
    return counts.mapValues {(_,row)->ResourceUsage(row[0],row[1],row[2],row[3],row[4],row[5],row[6])}
}
internal fun VisualStyle.json(): JSONObject = JSONObject().put("shape", shape).put("font", font)
    .put("scale", boundedValue(textScale, .5f, 2f, 1f)).put("alignment", alignment)
    .put("backgroundOpacity", boundedPosition(backgroundOpacity)).put("borderColor", borderColor)
    .put("borderWidth", boundedValue(borderWidth, 0f, 8f, 0f)).put("showName", showName).put("bold",bold).put("italic",italic).put("buttonEffect",buttonEffect)
internal fun JSONObject.visualStyle(): VisualStyle {
    val s = optJSONObject("style") ?: return VisualStyle()
    return VisualStyle(s.optString("shape", "legacy").takeIf { it in listOf("legacy", "rectangle", "rounded", "oval", "circle", "speech", "thought") } ?: "legacy",
        s.optString("font", "default").takeIf { it in listOf("default", "serif", "mono", "sans") } ?: "default",
        boundedValue(s.optDouble("scale", 1.0).toFloat(), .5f, 2f, 1f),
        s.optString("alignment", "start").takeIf { it in listOf("start", "center", "end") } ?: "start",
        boundedPosition(s.optDouble("backgroundOpacity", 1.0).toFloat()), s.optInt("borderColor", -1),
        boundedValue(s.optDouble("borderWidth", 0.0).toFloat(), 0f, 8f, 0f), s.optBoolean("showName", false), s.optBoolean("bold",false),s.optBoolean("italic",false),s.optString("buttonEffect","none").takeIf {it in listOf("none","glow","pulse")} ?: "none")
}
internal fun Preset.settings(): String = Element(0,0,"text","",textColor=textColor,backgroundColor=backgroundColor,style=style,transition=transition).settings()
internal fun Preset.withSettings(raw: String): Preset = Element(0,0,"text","").withSettings(raw).let { copy(style=it.style,transition=it.transition) }
fun styledElement(slideId: Long, preset: Preset?, speaker: String = "", characterId: Long? = null): Element {
    val button = preset?.kind == "button"
    return Element(0, slideId, if (button) "button" else "text", "",
        y=if (button) .7f else .65f, width=if (button) .28f else .75f, height=if (button) .12f else .22f,
        textColor=preset?.textColor ?: -16777216, backgroundColor=preset?.backgroundColor ?: -1,
        style=preset?.style ?: VisualStyle(shape="rounded"), transition=preset?.transition ?: Transition(),
        speakerName=speaker, characterId=characterId, presetId=preset?.id)
}

fun normalizedCharacterName(name:String):String = name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
