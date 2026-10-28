// InstaGallery v1.0

package com.example.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeartTheme(
    val id: String,
    val label: String,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    CRIMSON_RED("CRIMSON_RED", "Crimson Red", Color(0xFFFF3040), Color(0xFFFF0844)),
    SUNSET_GRADIENT("SUNSET_GRADIENT", "Insta Sunset", Color(0xFFDD2A7B), Color(0xFFFEDA77)),
    ELECTRIC_PINK("ELECTRIC_PINK", "Electric Pink", Color(0xFFFF1493), Color(0xFFFF69B4)),
    GOLDEN_AMBER("GOLDEN_AMBER", "Golden Amber", Color(0xFFFFB800), Color(0xFFFF5500)),
    CYBER_BLUE("CYBER_BLUE", "Cyber Blue", Color(0xFF0095F6), Color(0xFF00E5FF)),
    CLASSIC_WHITE("CLASSIC_WHITE", "Classic White", Color(0xFFFFFFFF), Color(0xFFE4E4E7));

    companion object {
        fun fromId(id: String?): HeartTheme = entries.firstOrNull { it.id == id } ?: CRIMSON_RED
    }
}

object OnboardingPreferences {
    private const val PREFS_NAME = "instagallery_onboarding_prefs_v4"
    private const val KEY_SCAN_COMPLETED = "initial_scan_completed"
    private const val KEY_SETUP_COMPLETED = "profile_setup_completed"
    private const val KEY_HEART_THEME_ID = "heart_theme_id"
    private const val KEY_VIEWED_STORY_IDS = "viewed_story_person_ids"

    private val _isScanCompleted = MutableStateFlow(false)
    val isScanCompleted: StateFlow<Boolean> = _isScanCompleted.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(false)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    private val _heartThemeFlow = MutableStateFlow(HeartTheme.CRIMSON_RED)
    val heartThemeFlow: StateFlow<HeartTheme> = _heartThemeFlow.asStateFlow()

    private val _viewedStoryPersonIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val viewedStoryPersonIdsFlow: StateFlow<Set<Long>> = _viewedStoryPersonIdsFlow.asStateFlow()

    private var appContext: Context? = null
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (initialized) return
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isScanCompleted.value = prefs.getBoolean(KEY_SCAN_COMPLETED, false)
        _isSetupCompleted.value = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        _heartThemeFlow.value = HeartTheme.fromId(
            prefs.getString(KEY_HEART_THEME_ID, HeartTheme.CRIMSON_RED.id)
        )
        val savedIds = prefs.getStringSet(KEY_VIEWED_STORY_IDS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        _viewedStoryPersonIdsFlow.value = savedIds
        initialized = true
    }

    fun markStoryViewed(personId: Long) {
        val updated = _viewedStoryPersonIdsFlow.value + personId
        _viewedStoryPersonIdsFlow.value = updated
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_VIEWED_STORY_IDS, updated.map { it.toString() }.toSet())
                .apply()
        }
    }

    fun markStoryViewed(context: Context, personId: Long) {
        init(context)
        markStoryViewed(personId)
    }

    fun setHeartTheme(theme: HeartTheme) {
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HEART_THEME_ID, theme.id)
                .apply()
        }
        _heartThemeFlow.value = theme
    }

    fun setHeartTheme(context: Context, theme: HeartTheme) {
        init(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HEART_THEME_ID, theme.id)
            .apply()
        _heartThemeFlow.value = theme
    }

    fun isInitialScanDone(context: Context): Boolean {
        init(context)
        return _isScanCompleted.value
    }

    fun isProfileSetupDone(context: Context): Boolean {
        init(context)
        return _isSetupCompleted.value
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        init(context)
        return _isScanCompleted.value && _isSetupCompleted.value
    }

    fun markInitialScanCompleted(context: Context, completed: Boolean = true) {
        init(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCAN_COMPLETED, completed)
            .apply()
        _isScanCompleted.value = completed
    }

    fun markProfileSetupCompleted(context: Context, completed: Boolean = true) {
        init(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETED, completed)
            .apply()
        _isSetupCompleted.value = completed
    }

    fun resetOnboardingForRescan(context: Context) {
        init(context)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCAN_COMPLETED, false)
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .apply()
        _isScanCompleted.value = false
        _isSetupCompleted.value = false
    }
}
