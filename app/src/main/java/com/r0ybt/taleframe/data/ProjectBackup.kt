package com.r0ybt.taleframe.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Portable logical format v1. These fields are a format contract, independent of SQLite migrations. */
class ProjectBackup(private val context: Context, private val repo: StoryRepository) {
    companion object {
        const val FORMAT_VERSION = 1
        const val MIME = "application/vnd.taleframe.project"
        private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
        private const val MAX_MEMBER = 500L * 1024 * 1024
        private const val MAX_JSON = 16L * 1024 * 1024
        private val hashName = Regex("resources/[a-f0-9]{64}")
        private val columns = linkedMapOf(
            "projects" to "id,name,cover_resource_id,initial_slide_id,skip_drafts,automatic_base_navigation",
            "resources" to "id,project_id,name,type,category,path,revision",
            "presets" to "id,project_id,name,kind,text_color,background_color,settings",
            "characters" to "id,project_id,name,description,portrait_id,dialog_preset_id",
            "expressions" to "id,character_id,name,resource_id,sort_order",
            "slides" to "id,project_id,name,color,image,sort_order,background_mode,background_scale,background_x,background_y,background_locked,settings,auto_target_id,background_resource_id,audio_resource_id,draft",
            "elements" to "id,slide_id,kind,text,x,y,text_color,background_color,target_id,image,width,height,rotation,flipped,opacity,locked,layer_order,settings,resource_id,character_id,expression_id,preset_id,base_navigation",
            "templates" to "id,project_id,name,body"
        ).mapValues { it.value.split(',') }
        private val references = mapOf(
            "project_id" to "projects", "slide_id" to "slides", "target_id" to "slides", "auto_target_id" to "slides", "initial_slide_id" to "slides",
            "cover_resource_id" to "resources", "background_resource_id" to "resources", "audio_resource_id" to "resources", "resource_id" to "resources", "portrait_id" to "resources",
            "character_id" to "characters", "expression_id" to "expressions", "preset_id" to "presets", "dialog_preset_id" to "presets",
            "resource" to "resources", "audioResource" to "resources", "character" to "characters", "expression" to "expressions", "preset" to "presets"
        )
        private val integers = setOf("id", "revision", "sort_order", "layer_order", "color", "text_color", "background_color", "skip_drafts", "automatic_base_navigation", "background_locked", "draft", "flipped", "locked") + references.keys
        private val reals = setOf("x", "y", "width", "height", "rotation", "opacity", "background_scale", "background_x", "background_y")
        private val nullable = setOf("image", "base_navigation") + references.keys.filter { it !in setOf("project_id", "slide_id", "character_id", "resource_id") }
        fun suggestedFilename(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(120).ifBlank { "Proyecto" } + ".taleframe"
        fun copyName(name: String, existing: List<String>): String {
            var number = 2
            while ("$name ($number)" in existing) number++
            return "$name ($number)"
        }
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    class Prepared internal constructor(val directory: File, internal val data: JSONObject, internal val blobs: Map<String, File>) : Closeable {
        val name: String get() = data.getJSONArray("projects").getJSONObject(0).getString("name")
        override fun close() { directory.deleteRecursively() }
    }
    class InvalidPackage(message: String = "El archivo TaleFrame está incompleto o dañado.", cause: Throwable? = null) : Exception(message, cause)

    fun export(projectId: Long, output: OutputStream, progress: (String) -> Unit = {}) {
        progress("Preparando proyecto…")
        val data = JSONObject()
        val db = repo.readableDatabase
        columns.forEach { (table, fields) ->
            val condition = when (table) {
                "projects" -> "id=?"
                "elements" -> "slide_id IN (SELECT id FROM slides WHERE project_id=?)"
                "expressions" -> "character_id IN (SELECT id FROM characters WHERE project_id=?)"
                else -> "project_id=?"
            }
            val rows = JSONArray()
            db.query(table, fields.toTypedArray(), condition, arrayOf(projectId.toString()), null, null, "id").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    fields.forEachIndexed { i, field -> row.put(field, when {
                        c.isNull(i) -> JSONObject.NULL
                        field in setOf("settings", "body") -> expand(JSONObject(c.getString(i)))
                        c.getType(i) == Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        c.getType(i) == Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        else -> c.getString(i)
                    }) }
                    rows.put(row)
                }
            }
            data.put(table, rows)
        }
        require(data.getJSONArray("projects").length() == 1) { "El proyecto ya no existe" }
        // Templates keep historical snapshots after catalog definitions are removed. Clear only stale origin IDs.
        val ids = idSets(data)
        eachRow(data, "templates") { row -> walk(row.getJSONObject("body")) { objectValue, key ->
            val table = references[key]
            if (table != null && !objectValue.isNull(key) && objectValue.getLong(key) !in ids.getValue(table)) objectValue.put(key, JSONObject.NULL)
            if (key == "expressionFrames") objectValue.put(key, JSONArray(arrayValues(objectValue.getJSONArray(key)).filter { it is Number && it.toLong() in ids.getValue("expressions") }))
        } }
        walk(data) { obj, key -> if (key == "expressionFrames") obj.put(key, JSONArray(arrayValues(obj.getJSONArray(key)).filter { it is Number && it.toLong() in ids.getValue("expressions") })) }
        val files = linkedMapOf<String, File>()
        val paths = mutableMapOf<String, String>()
        transformPaths(data) { path -> paths.getOrPut(path) {
            val file = File(path)
            require(file.isFile && file.canonicalFile.parentFile == repo.mediaDirectory.canonicalFile) { "Falta un recurso del proyecto" }
            val token = "resources/${sha256(file)}"
            files[token] = file
            token
        } }
        require(files.size <= 9998)
        val raw = data.toString().toByteArray(Charsets.UTF_8)
        validateJsonShape(String(raw, Charsets.UTF_8))
        require(raw.size <= MAX_JSON && files.values.sumOf { it.length() } + raw.size <= MAX_TOTAL) { "El proyecto supera el límite de respaldo (2 GB)" }
        val manifest = JSONObject().put("format", "TaleFrame.project").put("formatVersion", FORMAT_VERSION)
            .put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown")
            .put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())).put("projectName", data.getJSONArray("projects").getJSONObject(0).getString("name"))
            .put("projectSha256", digest(raw)).put("resources", JSONArray(files.map { (name, file) -> JSONObject().put("entry", name).put("sha256", name.substringAfter('/')).put("size", file.length()) }))
        progress("Copiando recursos…")
        ZipOutputStream(output).use { zip ->
            // Media is already compressed; level zero also bounds our own archives' compression ratio.
            zip.setLevel(0)
            fun entry(name: String, write: () -> Unit) { zip.putNextEntry(ZipEntry(name)); write(); zip.closeEntry() }
            entry("manifest.json") { zip.write(manifest.toString().toByteArray(Charsets.UTF_8)) }
            entry("project.json") { zip.write(raw) }
            files.entries.forEachIndexed { i, (name, file) ->
                require(file.length() <= MAX_MEMBER)
                progress("Copiando recursos… ${i + 1}/${files.size}")
                entry(name) { file.inputStream().use { it.copyTo(zip, 64 * 1024) } }
            }
        }
    }

    /** No durable file or database writes occur here. All bytes and references are validated in cache. */
    fun prepare(input: InputStream, progress: (String) -> Unit = {}): Prepared {
        val directory = File(context.cacheDir, "backup-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            progress("Validando archivo…")
            val archive = File(directory, "archive.zip")
            input.use { source -> archive.outputStream().use { boundedCopy(source, it, MAX_TOTAL + 16 * 1024 * 1024) } }
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().take(10001).toList()
                require(entries.size in 2..10000)
                val names = entries.map { it.name }
                require(names.toSet().size == names.size && names.containsAll(listOf("manifest.json", "project.json")))
                require(entries.all { !it.isDirectory && (it.name in setOf("manifest.json", "project.json") || hashName.matches(it.name)) })
                require(entries.all { it.size in 0..MAX_MEMBER && (it.size < MAX_JSON || it.compressedSize > 0 && it.size / it.compressedSize.coerceAtLeast(1) <= 1000) })
                require(entries.sumOf { it.size } <= MAX_TOTAL)
                fun json(name: String): Pair<JSONObject, ByteArray> {
                    val bytes = zip.getInputStream(zip.getEntry(name)).use { source -> java.io.ByteArrayOutputStream().also { boundedCopy(source, it, MAX_JSON) }.toByteArray() }
                    val text = String(bytes, Charsets.UTF_8)
                    validateJsonShape(text)
                    val tokens = org.json.JSONTokener(text)
                    val root = tokens.nextValue()
                    require(root is JSONObject && tokens.nextClean() == '\u0000')
                    return root to bytes
                }
                val manifest = json("manifest.json").first
                require(manifest.get("format") == "TaleFrame.project")
                val version = manifest.get("formatVersion")
                require(version is Int || version is Long)
                if ((version as Number).toLong() > FORMAT_VERSION) throw InvalidPackage("Este proyecto fue creado con una versión más reciente de TaleFrame.")
                require(version.toLong() == FORMAT_VERSION.toLong())
                require(manifest.get("appVersion") is String && manifest.get("exportedAt") is String && manifest.get("projectName") is String)
                val (data, raw) = json("project.json")
                require(manifest.getString("projectSha256") == digest(raw))
                val declarations = manifest.getJSONArray("resources")
                require(declarations.length() <= 9998)
                val blobs = linkedMapOf<String, File>()
                for (i in 0 until declarations.length()) {
                    val item = declarations.getJSONObject(i)
                    val name = item.getString("entry")
                    require(hashName.matches(name) && !blobs.containsKey(name))
                    val entry = zip.getEntry(name) ?: error("Missing resource")
                    require(item.get("size") is Number && item.getLong("size") == entry.size && item.getString("sha256") == name.substringAfter('/'))
                    val file = File(directory, name)
                    require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator))
                    check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                    zip.getInputStream(entry).use { source -> file.outputStream().use { boundedCopy(source, it, MAX_MEMBER) } }
                    require(file.length() == entry.size && sha256(file) == item.getString("sha256"))
                    blobs[name] = file
                    progress("Verificando recursos… ${i + 1}/${declarations.length()}")
                }
                require(names.toSet() == blobs.keys + setOf("manifest.json", "project.json"))
                validate(data, blobs)
                require(data.getJSONArray("projects").getJSONObject(0).getString("name") == manifest.getString("projectName"))
                return Prepared(directory, data, blobs)
            }
        } catch (e: Exception) {
            directory.deleteRecursively()
            if (e is InvalidPackage) throw e
            throw InvalidPackage(cause = e)
        }
    }

    /** A durable marker survives OS cache eviction or a killed process during pre-commit copies. */
    fun recoverInterruptedImport() {
        if (repo.mediaDirectory.listFiles()?.any { it.name.startsWith(".backup-pending-") } == true) repo.cleanImages()
    }

    @android.annotation.SuppressLint("UseKtx") // Explicit boundary coordinates SQLite rollback and private-file cleanup.
    fun restore(prepared: Prepared, name: String = prepared.name, progress: (String) -> Unit = {}): Long {
        require(name.isNotBlank())
        val data = JSONObject(prepared.data.toString())
        val createdFiles = mutableListOf<File>()
        val journal = File(repo.mediaDirectory, ".backup-pending-${java.util.UUID.randomUUID()}")
        fun register(file: File) {
            // Write the marker before touching bytes. Recovery uses the complete global reference index,
            // so a commit completed just before a process kill still keeps every restored file.
            if (!journal.exists()) { check(journal.createNewFile()); journal.outputStream().use { it.fd.sync() } }
            createdFiles += file
        }
        val paths = mutableMapOf<String, String>()
        val db = repo.writableDatabase
        try {
            progress("Copiando recursos…")
            prepared.blobs.entries.forEachIndexed { i, (token, source) ->
                val destination = File(repo.mediaDirectory, token.substringAfter('/'))
                if (destination.isFile && sha256(destination) == token.substringAfter('/')) paths[token] = destination.path
                else {
                    // Never replace damaged/shared files owned by existing projects.
                    val target = if (destination.exists()) File(repo.mediaDirectory, java.util.UUID.randomUUID().toString()) else destination
                    register(target)
                    source.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
                    check(sha256(target) == token.substringAfter('/'))
                    paths[token] = target.path
                }
                progress("Copiando recursos… ${i + 1}/${prepared.blobs.size}")
            }
            transformPaths(data) { paths.getValue(it) }
            // Legacy catalogs may contain separate entries with identical bytes. Keep both names/IDs;
            // a private alias satisfies SQLite's per-project (path,type) constraint without losing either entry.
            val catalogPaths = mutableSetOf<Pair<String, String>>()
            eachRow(data, "resources") { row ->
                val path = row.getString("path")
                if (!catalogPaths.add(path to row.getString("type"))) {
                    val alias = File(repo.mediaDirectory, java.util.UUID.randomUUID().toString())
                    register(alias)
                    try { android.system.Os.link(path, alias.path) }
                    catch (_: android.system.ErrnoException) { File(path).inputStream().use { input -> alias.outputStream().use { input.copyTo(it, 64 * 1024) } } }
                    row.put("path", alias.path)
                }
            }
            progress("Importando proyecto…")
            val maps = columns.keys.associateWith { mutableMapOf<Long, Long>() }
            db.beginTransaction()
            try {
                // Insert required ownership first, defer every optional relationship until all IDs exist.
                columns.forEach { (table, _) -> eachRow(data, table) { row ->
                    val values = ContentValues()
                    row.keys().forEach { field -> if (field != "id") {
                        val target = references[field]
                        val value = when {
                            field == "name" && table == "projects" -> name
                            target != null && field in setOf("project_id", "slide_id", "character_id", "resource_id") && table in setOf("resources", "presets", "characters", "expressions", "slides", "elements", "templates") && (field in setOf("project_id", "slide_id") || table == "expressions") -> maps.getValue(target).getValue(row.getLong(field))
                            target != null -> JSONObject.NULL
                            field in setOf("settings", "body") -> collapse(row.getJSONObject(field)).toString()
                            else -> row.get(field)
                        }
                        put(values, field, value)
                    } }
                    maps.getValue(table)[row.getLong("id")] = db.insertOrThrow(table, null, values)
                } }
                columns.forEach { (table, _) -> eachRow(data, table) { row ->
                    remap(row, maps)
                    val values = ContentValues()
                    row.keys().forEach { field -> if (field != "id") put(values, field, if (field in setOf("body", "settings")) collapse(row.getJSONObject(field)).toString() else row.get(field)) }
                    if (table == "projects") values.put("name", name)
                    db.update(table, values, "id=?", arrayOf(maps.getValue(table).getValue(row.getLong("id")).toString()))
                } }
                db.rawQuery("PRAGMA foreign_key_check", null).use { check(!it.moveToFirst()) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            return maps.getValue("projects").values.single()
        } catch (e: Exception) {
            createdFiles.forEach { it.delete() }
            throw e
        } finally { journal.delete(); prepared.close() }
    }

    private fun validate(data: JSONObject, blobs: Map<String, File>) {
        require(data.keys().asSequence().toSet() == columns.keys)
        require(data.getJSONArray("projects").length() == 1)
        val ids = idSets(data)
        require(columns.keys.sumOf { data.getJSONArray(it).length() } <= 100000)
        val mediaTypes = mutableMapOf<String, MutableSet<String>>()
        fun media(token: String, type: String) { require(token in blobs); mediaTypes.getOrPut(token) { mutableSetOf() }.add(type) }
        columns.forEach { (table, fields) -> eachRow(data, table) { row ->
            require(row.keys().asSequence().toSet() == fields.toSet())
            fields.forEach { field ->
                val value = row.get(field)
                val isNullable = field in nullable || table == "elements" && field in setOf("character_id", "resource_id")
                if (value == JSONObject.NULL) require(isNullable) else when {
                    field in setOf("settings", "body") -> require(value is JSONObject)
                    field in integers -> require((value is Int || value is Long) && (field !in references || (value as Number).toLong() in ids.getValue(references.getValue(field))))
                    field in reals -> require(value is Number && value.toDouble().isFinite() && kotlin.math.abs(value.toDouble()) <= 1000000)
                    else -> require(value is String && value.length <= 1000000)
                }
            }
            require(row.getLong("id") > 0)
            fields.filter { it in setOf("skip_drafts", "automatic_base_navigation", "background_locked", "draft", "flipped", "locked") }.forEach { require(row.getLong(it) in 0L..1L) }
            if (row.has("name")) require(row.getString("name").isNotBlank())
            if (table == "resources") { require(row.getString("type") in setOf("image", "gif", "video", "audio")); media(row.getString("path"), row.getString("type")) }
            if (table == "presets") require(row.getString("kind") in setOf("dialog", "narrator", "button", "action"))
            if (table == "elements") { require(row.getString("kind") in setOf("text", "button", "image")); require(row.isNull("base_navigation") || row.getString("kind") == "button" && row.getString("base_navigation") in setOf("previous", "next")) }
            if (table == "slides") require(row.getString("background_mode") in setOf("fill", "fit", "manual"))
            walk(row) { obj, key ->
                references[key]?.let { target -> if (!obj.isNull(key)) { val value = obj.get(key); require(value is Int || value is Long); require((value as Number).toLong() in ids.getValue(target)) } }
                if (key == "media") {
                    val m = obj.getJSONObject(key)
                    if (m.has("type")) require(m.get("type") in setOf("image", "gif", "video", "slideshow"))
                    listOf("loop", "autoplay", "muted").forEach { if (m.has(it)) require(m.get(it) is Boolean) }
                    listOf("volume", "seconds", "revision").forEach { if (m.has(it)) require(m.get(it) is Number && m.getDouble(it).isFinite()) }
                }
                if (key == "transition") {
                    val t = obj.getJSONObject(key)
                    if (t.has("type")) require(t.get("type") in setOf("none", "fade", "left", "right"))
                    if (t.has("ms")) require(t.get("ms") is Int || t.get("ms") is Long)
                }
                if (key in setOf("autoEnabled", "audioLoop")) require(obj.get(key) is Boolean)
                if (key == "expressionFrames") arrayValues(obj.getJSONArray(key)).forEach { require(it is Int || it is Long); require((it as Number).toLong() in ids.getValue("expressions")) }
                if (key == "image" && !obj.isNull(key)) media(obj.getString(key), obj.optJSONObject("settings")?.optJSONObject("media")?.optString("type", "image")?.let { if (it == "slideshow") "image" else it } ?: "image")
                if (key == "audio" && !obj.isNull(key)) media(obj.getString(key), "audio")
                if (key == "frames") arrayValues(obj.getJSONArray(key)).forEach { require(it is String); media(it as String, "image") }
            }
            if (table == "templates") {
                val body = row.getJSONObject("body")
                require(body.get("slide") is JSONObject && body.get("elements") is JSONArray && body.getJSONArray("elements").length() <= 10000)
                arrayValues(body.getJSONArray("elements")).forEach { value ->
                    val element = value as JSONObject
                    require(element.get("kind") in setOf("text", "button", "image"))
                    require(element.get("text") is String && element.get("settings") is JSONObject)
                    listOf("x", "y", "width", "height", "rotation", "opacity", "layer", "textColor", "backgroundColor").forEach { require(element.get(it) is Number && element.getDouble(it).isFinite()) }
                    listOf("flipped", "locked").forEach { require(element.get(it) is Boolean) }
                }
                TemplateCodec.decode(1, 1, row.getString("name"), collapse(body).toString())
            }
        } }
        // Reject constraints before preparing permanent copies, including expressions associated to another character.
        val resourceRows = rows(data, "resources").associateBy { it.getLong("id") }
        val presetRows = rows(data, "presets").associateBy { it.getLong("id") }
        eachRow(data, "projects") { if (!it.isNull("cover_resource_id")) require(resourceRows.getValue(it.getLong("cover_resource_id")).getString("type") == "image") }
        eachRow(data, "characters") { row ->
            if (!row.isNull("portrait_id")) require(resourceRows.getValue(row.getLong("portrait_id")).getString("type") == "image")
            if (!row.isNull("dialog_preset_id")) require(presetRows.getValue(row.getLong("dialog_preset_id")).getString("kind") == "dialog")
        }
        eachRow(data, "expressions") { require(resourceRows.getValue(it.getLong("resource_id")).getString("type") == "image") }
        val expressions = rows(data, "expressions").associateBy { it.getLong("id") }
        eachRow(data, "elements") { row ->
            if (!row.isNull("expression_id")) require(expressions.getValue(row.getLong("expression_id")).getLong("character_id") == row.getLong("character_id"))
            arrayValues(row.getJSONObject("settings").optJSONArray("expressionFrames") ?: JSONArray()).forEach { require(expressions.getValue((it as Number).toLong()).getLong("character_id") == row.getLong("character_id")) }
        }
        require(rows(data, "presets").count { it.getString("kind") == "narrator" } <= 1)
        require(mediaTypes.keys == blobs.keys)
        mediaTypes.forEach { (token, types) -> types.forEach { validateMedia(blobs.getValue(token), it) } }
    }

    private fun validateMedia(file: File, type: String) {
        require(file.length() > 0 && file.length() <= (when (type) { "video" -> 500L; "audio" -> 100L; else -> 40L }) * 1024 * 1024)
        if (type in setOf("image", "gif")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            require(options.outWidth > 0 && options.outHeight > 0)
            if (type == "gif") {
                val header = file.inputStream().use { input -> ByteArray(6).also { require(input.read(it) == 6) }.toString(Charsets.US_ASCII) }
                require(header in setOf("GIF87a", "GIF89a") && options.outWidth <= 4096 && options.outHeight <= 4096 && options.outWidth.toLong() * options.outHeight <= 8000000)
            }
        } else {
            require(type in setOf("audio", "video"))
            val metadata = MediaMetadataRetriever()
            try { metadata.setDataSource(file.path); require(metadata.extractMetadata(if (type == "audio") MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO else MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") } finally { metadata.release() }
        }
    }

    private fun remap(obj: JSONObject, maps: Map<String, Map<Long, Long>>) = walk(obj) { current, key ->
        references[key]?.let { if (!current.isNull(key)) current.put(key, maps.getValue(it).getValue(current.getLong(key))) }
        if (key == "expressionFrames") current.put(key, JSONArray(arrayValues(current.getJSONArray(key)).map { maps.getValue("expressions").getValue((it as Number).toLong()) }))
    }
    private fun transformPaths(obj: JSONObject, transform: (String) -> String) = walk(obj) { current, key ->
        if (key in setOf("path", "image", "audio") && !current.isNull(key)) current.put(key, transform(current.getString(key)))
        if (key == "frames") current.put(key, JSONArray(arrayValues(current.getJSONArray(key)).map { transform(it as String) }))
    }
    private fun walk(obj: JSONObject, depth: Int = 0, action: (JSONObject, String) -> Unit) {
        require(depth <= 32)
        obj.keys().asSequence().toList().forEach { key ->
            action(obj, key)
            when (val value = obj.get(key)) {
                is JSONObject -> walk(value, depth + 1, action)
                is JSONArray -> arrayValues(value).filterIsInstance<JSONObject>().forEach { walk(it, depth + 1, action) }
            }
        }
    }
    private fun expand(obj: JSONObject): JSONObject { walk(obj) { current, key -> if (key == "settings" && current.get(key) is String) current.put(key, JSONObject(current.getString(key))) }; return obj }
    private fun collapse(obj: JSONObject): JSONObject {
        val copy = JSONObject(obj.toString())
        fun visit(current: JSONObject) { current.keys().asSequence().toList().forEach { key ->
            when (val value = current.get(key)) {
                is JSONObject -> { visit(value); if (key == "settings") current.put(key, value.toString()) }
                is JSONArray -> arrayValues(value).filterIsInstance<JSONObject>().forEach(::visit)
            }
        } }
        visit(copy)
        return copy
    }
    private fun idSets(data: JSONObject): Map<String, Set<Long>> = columns.keys.associateWith { table ->
        val rows = rows(data, table)
        rows.map { val id = it.get("id"); require(id is Int || id is Long); (id as Number).toLong().also { value -> require(value > 0) } }.toSet().also { require(it.size == rows.size) }
    }
    private fun rows(data: JSONObject, table: String) = arrayValues(data.getJSONArray(table)).map { it as JSONObject }
    private fun eachRow(data: JSONObject, table: String, action: (JSONObject) -> Unit) = rows(data, table).forEach(action)
    private fun arrayValues(array: JSONArray) = (0 until array.length()).map { array.get(it) }
    private fun put(values: ContentValues, field: String, value: Any) { when (value) {
        JSONObject.NULL -> values.putNull(field)
        is String -> values.put(field, value)
        is Int -> values.put(field, value)
        is Long -> values.put(field, value)
        is Number -> values.put(field, value.toDouble())
        else -> error("Invalid field")
    } }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    // Bound parser allocations before JSONObject constructs arrays/maps from untrusted bytes.
    private fun validateJsonShape(text: String) {
        var depth = 0; var quoted = false; var escaped = false; var containers = 0; var separators = 0
        text.forEach { ch ->
            if (quoted) { if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false }
            else when (ch) {
                '"' -> quoted = true
                '{', '[' -> { depth++; containers++; require(depth <= 32 && containers <= 100000) }
                '}', ']' -> { depth--; require(depth >= 0) }
                ',' -> { separators++; require(separators <= 500000) }
            }
        }
        require(depth == 0 && !quoted)
    }
    private fun boundedCopy(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024); var total = 0L
        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit); output.write(buffer, 0, n) }
    }
}
