package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** A panel is an ordinary image layer with an independent, clipped image frame. */
data class PanelOptions(val mode:String="fill",val scale:Float=1f,val x:Float=0f,val y:Float=0f)
internal fun PanelOptions.json()=JSONObject().put("mode",mode).put("scale",scale).put("x",x).put("y",y)
internal fun JSONObject.panelOptions()=PanelOptions(optString("mode","fill").takeIf {it in listOf("fit","fill","manual")} ?: "fill",boundedValue(optDouble("scale",1.0).toFloat(),.25f,4f,1f),boundedValue(optDouble("x",0.0).toFloat(),-1f,1f,0f),boundedValue(optDouble("y",0.0).toFloat(),-1f,1f,0f))
data class SlideTemplate(val id:Long,val projectId:Long,val name:String,val slide:Slide,val elements:List<Element>,val included:Boolean=false)
fun initialSlide(project:Project,slides:List<Slide>):Slide? {
    val own=slides.filter {it.projectId==project.id}
    return own.find {it.id==project.initialSlideId} ?: own.minByOrNull {it.id}
}
fun playbackStart(project:Project,slides:List<Slide>):Slide? {
    val initial=initialSlide(project,slides)
    return if(project.skipDrafts && initial?.draft==true) slides.filter {it.projectId==project.id && !it.draft}.minByOrNull {it.id} else initial
}
/** Safe destinations: omission never follows an inferred chain. */
fun destinationAllowed(target:Long,slides:List<Slide>,skipDrafts:Boolean)=slides.any {it.id==target && (!skipDrafts || !it.draft)}

object TemplateCodec {
    private fun nullable(o:JSONObject,key:String):Long?=if(o.isNull(key)) null else o.optLong(key).takeIf {it>0}
    fun encode(t:SlideTemplate):String {
        val s=t.slide
        val slide=JSONObject().put("color",s.color).put("image",s.image).put("mode",s.backgroundMode).put("scale",s.backgroundScale).put("x",s.backgroundX).put("y",s.backgroundY).put("locked",s.backgroundLocked).put("settings",s.copy(autoEnabled=false,autoTargetId=null).settings()).put("resource",s.backgroundResourceId).put("audioResource",s.audioResourceId)
        val elements=JSONArray()
        t.elements.forEach {e->elements.put(JSONObject().put("kind",e.kind).put("text",e.text).put("x",e.x).put("y",e.y).put("textColor",e.textColor).put("backgroundColor",e.backgroundColor).put("image",e.image).put("width",e.width).put("height",e.height).put("rotation",e.rotation).put("flipped",e.flipped).put("opacity",e.opacity).put("locked",e.locked).put("layer",e.layer).put("settings",e.settings()).put("resource",e.resourceId).put("character",e.characterId).put("expression",e.expressionId).put("preset",e.presetId))}
        return JSONObject().put("slide",slide).put("elements",elements).toString()
    }
    fun decode(id:Long,project:Long,name:String,raw:String):SlideTemplate {
        val data=JSONObject(raw);val s=data.getJSONObject("slide")
        val slide=Slide(0,project,name,color=s.optInt("color",-1),image=s.optString("image").takeIf {it.isNotBlank()},backgroundMode=s.optString("mode","fill"),backgroundScale=s.optDouble("scale",1.0).toFloat(),backgroundX=s.optDouble("x",0.0).toFloat(),backgroundY=s.optDouble("y",0.0).toFloat(),backgroundLocked=s.optBoolean("locked")).withSettings(s.optString("settings","{}"),null).copy(autoEnabled=false,backgroundResourceId=nullable(s,"resource"),audioResourceId=nullable(s,"audioResource"))
        val array=data.getJSONArray("elements")
        val elements=(0 until array.length()).map {i->val e=array.getJSONObject(i)
            Element(-(i+1L),0,e.getString("kind"),e.optString("text"),e.optDouble("x",.1).toFloat(),e.optDouble("y",.1).toFloat(),e.optInt("textColor",-16777216),e.optInt("backgroundColor",-1),image=e.optString("image").takeIf {it.isNotBlank()},width=e.optDouble("width",0.0).toFloat(),height=e.optDouble("height",0.0).toFloat(),rotation=e.optDouble("rotation",0.0).toFloat(),flipped=e.optBoolean("flipped"),opacity=e.optDouble("opacity",1.0).toFloat(),locked=e.optBoolean("locked"),layer=e.optInt("layer",i)).withSettings(e.optString("settings","{}")).copy(resourceId=nullable(e,"resource"),characterId=nullable(e,"character"),expressionId=nullable(e,"expression"),presetId=nullable(e,"preset"))
        }
        return SlideTemplate(id,project,name,slide,elements)
    }
}
fun includedTemplates():List<SlideTemplate> {
    fun panel(left:Float,top:Float,w:Float,h:Float)=Element(0,0,"image","",x=if(w<1) left/(1-w) else 0f,y=if(h<1) top/(1-h) else 0f,width=w,height=h,backgroundColor=0xFFF4E8EB.toInt(),panel=PanelOptions())
    val layouts=listOf(
        "Lámina completa" to listOf(panel(.03f,.03f,.94f,.94f)),
        "2 verticales" to listOf(panel(.03f,.03f,.455f,.94f),panel(.515f,.03f,.455f,.94f)),
        "2 horizontales" to listOf(panel(.03f,.03f,.94f,.455f),panel(.03f,.515f,.94f,.455f)),
        "3 paneles" to listOf(panel(.03f,.03f,.94f,.455f),panel(.03f,.515f,.455f,.455f),panel(.515f,.515f,.455f,.455f)),
        "4 paneles" to listOf(panel(.03f,.03f,.455f,.455f),panel(.515f,.03f,.455f,.455f),panel(.03f,.515f,.455f,.455f),panel(.515f,.515f,.455f,.455f)),
        "Conversación" to listOf(panel(.03f,.03f,.455f,.62f),panel(.515f,.03f,.455f,.62f),Element(0,0,"text","Escribe tu diálogo…",x=.08f,y=.84f,width=.84f,height=.25f,style=VisualStyle(shape="speech"))))
    return layouts.mapIndexed {i,(name,elements)->SlideTemplate(-(i+1L),0,name,Slide(0,0,name),elements.mapIndexed {n,e->e.copy(id=-(n+1L),layer=n)},true)}
}
class TemplateStore(private val repo:StoryRepository) {
    private val db get()=repo.writableDatabase
    fun fill(story:Story):Story {
        val list=mutableListOf<SlideTemplate>()
        db.rawQuery("SELECT id,project_id,name,body FROM templates ORDER BY name COLLATE NOCASE,id",null).use {c->while(c.moveToNext()) list+=TemplateCodec.decode(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3))}
        return story.copy(templates=list)
    }
    fun saveSlide(slideId:Long,name:String):Long {
        require(name.isNotBlank());val story=repo.read();val slide=story.slides.first {it.id==slideId}
        return insert(SlideTemplate(0,slide.projectId,name,slide,story.elements.filter {it.slideId==slideId}))
    }
    private fun insert(t:SlideTemplate):Long=db.insertOrThrow("templates",null,ContentValues().apply {put("project_id",t.projectId);put("name",t.name.trim());put("body",TemplateCodec.encode(t))})
    fun duplicate(id:Long,projectId:Long):Long {
        val t=find(id,projectId);return insert(t.copy(id=0,projectId=projectId,name="${t.name} (copia)"))
    }
    fun rename(id:Long,name:String) {require(name.isNotBlank());db.update("templates",ContentValues().apply {put("name",name.trim())},"id=?",arrayOf(id.toString()))}
    fun delete(id:Long) {db.delete("templates","id=?",arrayOf(id.toString()));repo.cleanImages()}
    private fun find(id:Long,project:Long)=if(id<0) includedTemplates().first {it.id==id} else repo.read().templates.first {it.id==id && it.projectId==project}
    fun createSlide(projectId:Long,name:String,templateId:Long?):Long {
        require(name.isNotBlank())
        if(templateId==null) return repo.createSlide(projectId,name)
        val t=find(templateId,projectId);val story=repo.read();var id=0L
        fun resource(value:Long?)=value?.takeIf {v->story.resources.any {it.id==v && it.projectId==projectId}}
        db.beginTransaction()
        try {
            id=repo.createSlide(projectId,name,synchronizeNavigation=false)
            val created=repo.read().slides.first {it.id==id}
            repo.saveSlide(t.slide.copy(id=id,projectId=projectId,name=name,order=created.order,draft=false,autoEnabled=false,autoTargetId=null,backgroundResourceId=resource(t.slide.backgroundResourceId),audioResourceId=resource(t.slide.audioResourceId)))
            t.elements.sortedBy {it.layer}.forEach {e->
                val character=e.characterId?.takeIf {v->story.characters.any {it.id==v && it.projectId==projectId}}
                val expression=e.expressionId?.takeIf {v->story.expressions.any {it.id==v && it.characterId==character}}
                repo.saveElement(e.copy(id=0,slideId=id,targetId=null,resourceId=resource(e.resourceId),characterId=character,expressionId=expression,presetId=e.presetId?.takeIf {v->story.presets.any {it.id==v && it.projectId==projectId}},expressionFrames=e.expressionFrames.filter {v->story.expressions.any {it.id==v && it.characterId==character}}))
            }
            repo.syncBaseNavigation(projectId)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return id
    }
    companion object {
        fun createSchema(db:SQLiteDatabase) {
            db.execSQL("ALTER TABLE projects ADD COLUMN cover_resource_id INTEGER REFERENCES resources(id) ON DELETE SET NULL")
            db.execSQL("ALTER TABLE projects ADD COLUMN initial_slide_id INTEGER REFERENCES slides(id) ON DELETE SET NULL")
            db.execSQL("ALTER TABLE projects ADD COLUMN skip_drafts INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE slides ADD COLUMN draft INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,name TEXT NOT NULL,body TEXT NOT NULL)")
            db.execSQL("CREATE INDEX templates_project ON templates(project_id)")
        }
    }
}
