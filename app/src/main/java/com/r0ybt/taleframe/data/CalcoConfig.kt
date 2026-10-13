package com.r0ybt.taleframe.data

import android.content.SharedPreferences

enum class CalcoMode { PREVIOUS, MANUAL, NONE }
data class CalcoConfig(val mode: CalcoMode = CalcoMode.NONE, val manualId: Long? = null,
    val opacity: Float = .35f, val configured: Boolean = false, val enabled: Boolean = true)

/** Reference resolution uses album order and never narrative connections. */
fun calcoReference(config: CalcoConfig, current: Slide?, slides: List<Slide>): Slide? {
    if (current == null || !config.enabled) return null
    val album = slides.filter { it.projectId == current.projectId }
        .sortedWith(compareBy<Slide> { it.order }.thenBy { it.id })
    return when (config.mode) {
        CalcoMode.PREVIOUS -> album.indexOfFirst { it.id == current.id }.takeIf { it > 0 }?.let { album[it - 1] }
        CalcoMode.MANUAL -> album.firstOrNull { it.id == config.manualId && it.id != current.id }
        CalcoMode.NONE -> null
    }
}

/** Editor preferences remain local, just like the existing manual calco settings. */
class CalcoPreferences(private val preferences: SharedPreferences) {
    fun read(projectId: Long?, slideId: Long?): CalcoConfig {
        if (projectId == null || slideId == null) return CalcoConfig()
        val prefix = "calco_project_$projectId"
        val oldId = preferences.getLong("calco_slide_$slideId", 0L).takeIf { it > 0 }
        val legacyConfigured = preferences.contains("calco_slide_$slideId") || preferences.contains("calco_opacity_$slideId")
        val storedMode = preferences.getString("${prefix}_mode", null)
        val mode = CalcoMode.entries.firstOrNull { it.name == storedMode }
            ?: if (oldId != null) CalcoMode.MANUAL else CalcoMode.NONE
        return CalcoConfig(mode,
            preferences.getLong("${prefix}_manual", oldId ?: 0L).takeIf { it > 0 },
            boundedValue(preferences.getFloat("${prefix}_opacity", preferences.getFloat("calco_opacity_$slideId", .35f)), 0f, .9f, .35f),
            storedMode != null || legacyConfigured, preferences.getBoolean("${prefix}_enabled", true))
    }

    fun save(projectId: Long, config: CalcoConfig) {
        val prefix = "calco_project_$projectId"
        preferences.edit().apply {
            putString("${prefix}_mode", config.mode.name)
            putBoolean("${prefix}_enabled", config.enabled)
            putFloat("${prefix}_opacity", boundedValue(config.opacity, 0f, .9f, .35f))
            if (config.mode == CalcoMode.MANUAL) putLong("${prefix}_manual", config.manualId ?: 0L)
        }.apply()
    }
}

fun toggleCalco(config: CalcoConfig): CalcoConfig =
    if (config.mode == CalcoMode.NONE) config.copy(mode = CalcoMode.PREVIOUS, enabled = true, configured = true)
    else config.copy(enabled = !config.enabled, configured = true)
