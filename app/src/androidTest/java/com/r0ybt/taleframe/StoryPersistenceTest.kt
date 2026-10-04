package com.r0ybt.taleframe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.taleframe.data.Element
import com.r0ybt.taleframe.data.StoryRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StoryPersistenceTest {
    @Test fun applicationHasNoInternetPermission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertEquals(0,context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun storySurvivesDatabaseRecreationAndDestinationsAreCleaned() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db="test-${UUID.randomUUID()}.db"
        val source=File(context.cacheDir,"test-background.png")
        Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).also { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        var repository=StoryRepository(context,db)
        var imported: String?=null
        try {
            val project=repository.createProject("Historia prueba")
            val first=repository.createSlide(project,"Lámina 1")
            val second=repository.createSlide(project,"Lámina 2")
            val third=repository.createSlide(project,"Lámina 3")
            imported=repository.importImage(Uri.fromFile(source))
            repository.background(first,0xFF123456.toInt(),imported)
            repository.saveElement(Element(0,first,"text","¿Quieres entrar?",.35f,.2f,0xFFFFFFFF.toInt(),0xFF000000.toInt()))
            repository.saveElement(Element(0,first,"button","Entrar",.6f,.8f,targetId=second))
            repository.saveElement(Element(0,first,"button","Irme",.1f,.8f,targetId=third))
            repository.saveElement(Element(0,second,"button","Volver",targetId=first))
            val before=repository.read()
            repository.close()
            source.delete() // The original provider/file is no longer available.
            repository=StoryRepository(context,db)
            val after=repository.read()
            assertEquals(before,after)
            assertEquals(first,after.slides.first().id)
            assertTrue(File(requireNotNull(imported)).exists())
            assertNotNull(BitmapFactory.decodeFile(imported))
            assertEquals(.35f,after.elements.first().x,0f)
            repository.delete("slides",second)
            assertNull(repository.read().elements.first { it.text=="Entrar" }.targetId)
            assertFalse(repository.read().elements.any { it.slideId==second })
            repository.rename("projects",project,"Renombrado")
            assertEquals("Renombrado",repository.read().projects.single().name)
            repository.delete("projects",project)
            assertTrue(repository.read().slides.isEmpty())
            assertTrue(repository.read().elements.isEmpty())
            assertFalse(File(requireNotNull(imported)).exists())
        } finally { repository.close(); context.deleteDatabase(db); source.delete(); imported?.let { File(it).delete() } }
    }

    @Test fun targetsCannotCrossProjectBoundaries() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db="test-${UUID.randomUUID()}.db"
        val repository=StoryRepository(context,db)
        try {
            val a=repository.createSlide(repository.createProject("A"),"A1")
            val b=repository.createSlide(repository.createProject("B"),"B1")
            try { repository.saveElement(Element(0,a,"button","Invalid",targetId=b)); fail("Cross-project destination accepted") }
            catch(_: IllegalArgumentException) { assertTrue(repository.read().elements.isEmpty()) }
        } finally { repository.close(); context.deleteDatabase(db) }
    }
}
