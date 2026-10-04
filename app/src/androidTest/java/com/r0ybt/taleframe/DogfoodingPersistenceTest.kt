package com.r0ybt.taleframe

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DogfoodingPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:(StoryRepository,String)->Unit) {
        val name="dogfood-${UUID.randomUUID()}.db"
        try {StoryRepository(context,name).use {block(it,name)}} finally {context.deleteDatabase(name);File(context.filesDir,"backgrounds-$name").deleteRecursively()}
    }
    @Test fun optedInDuplicateReorderAndDeleteOnlyChangeBaseNavigationAndKeepGeometry()=fixture {repo,_->
        val p=repo.createProject("P");val a=repo.createSlide(p,"A");val c=repo.createSlide(p,"C")
        val narrative=repo.saveElement(Element(0,a,"button","Rama",targetId=c,transition=Transition("right",650)))
        repo.setAutomaticBaseNavigation(p,true)
        val b=repo.duplicateSlide(a)
        fun base(s:Long,role:String)=repo.read().elements.single {it.slideId==s && it.baseNavigation==role}
        assertEquals(b,base(a,"next").targetId);assertEquals(a,base(b,"previous").targetId);assertEquals(c,base(b,"next").targetId)
        assertFalse(repo.read().elements.any {it.slideId==a && it.baseNavigation=="previous"})
        assertFalse(repo.read().elements.any {it.slideId==c && it.baseNavigation=="next"})
        val moved=base(a,"next").copy(x=.27f,y=.61f,width=.4f,height=.12f,locked=true,rotation=15f)
        repo.saveElement(moved);repo.syncBaseNavigation(p);repo.syncBaseNavigation(p)
        assertEquals(4,repo.read().elements.count {it.baseNavigation!=null})
        repo.moveSlideTo(c,1)
        assertEquals(listOf(a,c,b),repo.read().slides.map {it.id})
        assertEquals(moved.copy(targetId=c),base(a,"next"))
        assertEquals(a,base(c,"previous").targetId);assertEquals(b,base(c,"next").targetId)
        assertEquals(c,repo.read().elements.first {it.id==narrative}.targetId)
        assertEquals(Transition("right",650),repo.read().elements.first {it.id==narrative}.transition)
        repo.delete("slides",c)
        assertEquals(b,base(a,"next").targetId);assertEquals(a,base(b,"previous").targetId)
        assertNull(repo.read().elements.first {it.id==narrative}.targetId)
        repo.delete("slides",b);assertFalse(repo.read().elements.any {it.baseNavigation!=null})
    }
    @Test fun generatedButtonsStayAboveCopiedPanelsAndNewTemplateLayers()=fixture {repo,_->
        val p=repo.createProject("P");val a=repo.templates.createSlide(p,"Página",-1)
        repo.setAutomaticBaseNavigation(p,true)
        val b=repo.duplicateSlide(a)
        val c=repo.templates.createSlide(p,"Otra",-5)
        val story=repo.read()
        for(id in listOf(b,c)) {
            val elements=story.elements.filter {it.slideId==id}
            val highestPanel=elements.filter {it.panel!=null}.maxOf {it.layer}
            assertTrue(elements.filter {it.baseNavigation!=null}.all {it.layer>highestPanel})
        }
    }
    @Test fun defaultOffAndDisablingNeverCreateOrRewriteButtons()=fixture {repo,name->
        val p=repo.createProject("P");val a=repo.createSlide(p,"A");val b=repo.createSlide(p,"B")
        val e=repo.saveElement(Element(0,a,"button","Narrativo",targetId=b))
        repo.duplicateSlide(a);repo.moveSlideTo(b,0)
        assertFalse(repo.read().projects.single().automaticBaseNavigation);assertTrue(repo.read().elements.all {it.baseNavigation==null})
        assertEquals(b,repo.read().elements.first {it.id==e}.targetId)
        repo.setAutomaticBaseNavigation(p,true);repo.setAutomaticBaseNavigation(p,false)
        val before=repo.read().elements.filter {it.baseNavigation!=null}
        repo.moveSlideTo(a,0);repo.duplicateSlide(a)
        assertEquals(before,repo.read().elements.filter {it.id in before.map {b->b.id}})
        StoryRepository(context,name).use {assertEquals(repo.read(),it.read())}
    }
    @Test fun duplicateNamesAreBlockedPerProjectButLegacyDuplicatesCanEditDescription()=fixture {repo,_->
        val p=repo.createProject("P");val other=repo.createProject("Otro")
        val c=repo.library.saveCharacter(Character(0,p,"Guillermo"))
        fun rejected(action:()->Unit) {try {action();fail("Duplicate accepted")} catch(e:IllegalArgumentException) {assertEquals("Ya existe un personaje con este nombre.",e.message)}}
        rejected {repo.library.saveCharacter(Character(0,p," guillermo "))}
        repo.library.saveCharacter(Character(0,other,"GUILLERMO"))
        val d=repo.library.saveCharacter(Character(0,p,"Otro"));rejected {repo.library.saveCharacter(repo.read().characters.first {it.id==d}.copy(name="guillermo"))}
        repo.writableDatabase.execSQL("UPDATE characters SET name='Guillermo' WHERE id=?",arrayOf(d))
        repo.library.saveCharacter(repo.read().characters.first {it.id==c}.copy(description="Conservado"))
        assertEquals(2,repo.read().characters.count {it.projectId==p && it.name=="Guillermo"})
    }
    @Test fun textAndTargetDeltasPreserveQueuedGeometryAndBaseType()=fixture {repo,_->
        val p=repo.createProject("P");val a=repo.createSlide(p,"A");val b=repo.createSlide(p,"B");repo.setAutomaticBaseNavigation(p,true)
        val old=repo.read().elements.single {it.slideId==a}
        repo.moveElement(old.id,.4f,.6f);repo.editElement(old,old.copy(text="Nuevo",transition=Transition("none")))
        val now=repo.read().elements.first {it.id==old.id};assertEquals(.4f,now.x,0f);assertEquals(.6f,now.y,0f);assertEquals("next",now.baseNavigation);assertEquals(b,now.targetId)
        val copied=repo.duplicateElement(now.id);assertNull(repo.read().elements.first {it.id==copied}.baseNavigation)
    }
    @Test fun realV5MigrationPreservesAllEightTablesColumnsIdsAndSequences()=fixture {repo,_->
        val p=repo.createProject("P");val a=repo.createSlide(p,"A");val b=repo.createSlide(p,"B")
        val r=repo.library.addResource(p,"Portada","image","image","/private/legacy.png")
        val c=repo.library.saveCharacter(Character(0,p,"Luna",portraitId=r));repo.library.saveExpression(Expression(0,c,"Feliz",r))
        repo.setCover(p,r);repo.setInitialSlide(p,b);repo.setDraft(a,true);repo.setSkipDrafts(p,true)
        repo.saveElement(Element(0,a,"button","Rama",targetId=b,style=VisualStyle(bold=true,shape="speech"),transition=Transition("left",800)))
        repo.saveSlide(repo.read().slides.first {it.id==a}.copy(autoEnabled=true,autoTargetId=b));repo.templates.saveSlide(a,"Página")
        val expected=repo.read();val legacyName="v5-${UUID.randomUUID()}.db";val dbFile=context.getDatabasePath(legacyName)
        val tables=listOf("projects","slides","elements","resources","presets","characters","expressions","templates")
        fun rows(db:SQLiteDatabase,table:String,cols:List<String>)=db.rawQuery("SELECT ${cols.joinToString(",")} FROM $table ORDER BY id",null).use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}
        val columns=tables.associateWith {table->repo.readableDatabase.rawQuery("PRAGMA table_info($table)",null).use {c->buildList {while(c.moveToNext()) {val name=c.getString(1);if(name !in listOf("base_navigation","automatic_base_navigation")) add(name)}}}}
        try {
            SQLiteDatabase.openOrCreateDatabase(dbFile,null).use {db ->
                tables.forEach {table->
                    val sql=repo.readableDatabase.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?",arrayOf(table)).use {it.moveToFirst();it.getString(0)}
                        .replace(", automatic_base_navigation INTEGER NOT NULL DEFAULT 0","")
                        .replace(", base_navigation TEXT DEFAULT NULL CHECK(base_navigation IS NULL OR (kind='button' AND base_navigation IN ('previous','next')))","")
                    db.execSQL(sql)
                    val cols=columns.getValue(table)
                    rows(repo.readableDatabase,table,cols).forEach {row->db.insertOrThrow(table,null,ContentValues().apply {cols.forEachIndexed {i,col->put(col,row[i])}})}
                }
                db.execSQL("UPDATE sqlite_sequence SET seq=900 WHERE name='elements'");db.version=5
            }
            StoryRepository(context,legacyName).use {migrated->
                assertEquals(6,migrated.readableDatabase.version);assertEquals(expected,migrated.read())
                tables.forEach {table->assertEquals(table,rows(repo.readableDatabase,table,columns.getValue(table)),rows(migrated.readableDatabase,table,columns.getValue(table)))}
                assertFalse(migrated.read().projects.single().automaticBaseNavigation);assertTrue(migrated.read().elements.all {it.baseNavigation==null})
                assertTrue(migrated.saveElement(Element(0,a,"text","Nuevo"))>900)
                migrated.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertEquals(0,it.count)}
            }
        } finally {context.deleteDatabase(legacyName);File(context.filesDir,"backgrounds-$legacyName").deleteRecursively()}
    }
}
