package com.r0ybt.taleframe.data

import android.database.sqlite.SQLiteDatabase

internal const val SQLITE_TEXT_CHUNK = 65536

/** CursorWindow has a row-size limit on older Android versions. Read long text in code-point chunks. */
internal fun sqliteText(db: SQLiteDatabase, table: String, column: String, id: Long, firstChunk: String?): String? {
    if (firstChunk == null || firstChunk.codePointCount(0, firstChunk.length) < SQLITE_TEXT_CHUNK) return firstChunk
    val result = StringBuilder(firstChunk)
    var offset = SQLITE_TEXT_CHUNK.toLong() + 1
    while (true) {
        val chunk = db.rawQuery("SELECT substr($column,?,?) FROM $table WHERE id=?", arrayOf(offset.toString(), SQLITE_TEXT_CHUNK.toString(), id.toString())).use {
            check(it.moveToFirst()); it.getString(0)
        }
        result.append(chunk)
        if (chunk.codePointCount(0, chunk.length) < SQLITE_TEXT_CHUNK) return result.toString()
        offset += SQLITE_TEXT_CHUNK
    }
}
