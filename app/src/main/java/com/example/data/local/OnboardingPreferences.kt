// InstaGallery v2.0

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

    private const val KEY_MY_PROFILE_PERSON_ID = "my_profile_person_id"
    private const val KEY_MY_PROFILE_BIO = "my_profile_bio"
    private const val KEY_PERSON_BIO_PREFIX = "person_bio_"
    private const val KEY_HIDE_OTHER_FROM_HOME = "hide_other_from_home_feed"
    private const val KEY_DEFAULT_CROP_MODE = "default_crop_mode_4x5"
    private const val KEY_STORY_DURATION_SECONDS = "story_duration_seconds"
    private const val KEY_DEFAULT_INSTACARD_TEMPLATE = "default_instacard_template"
    private const val KEY_DISMISSED_DUPLICATE_PAIRS = "dismissed_duplicate_pair_keys"

    const val DEFAULT_PROFILE_BIO = "Capturing moments on device"

    private val _isScanCompleted = MutableStateFlow(false)
    val isScanCompleted: StateFlow<Boolean> = _isScanCompleted.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(false)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    private val _heartThemeFlow = MutableStateFlow(HeartTheme.CRIMSON_RED)
    val heartThemeFlow: StateFlow<HeartTheme> = _heartThemeFlow.asStateFlow()

    private val _viewedStoryPersonIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val viewedStoryPersonIdsFlow: StateFlow<Set<Long>> = _viewedStoryPersonIdsFlow.asStateFlow()

    private val _myProfilePersonIdFlow = MutableStateFlow(-1L)
    val myProfilePersonIdFlow: StateFlow<Long> = _myProfilePersonIdFlow.asStateFlow()

    private val _customProfileBioFlow = MutableStateFlow(DEFAULT_PROFILE_BIO)
    val customProfileBioFlow: StateFlow<String> = _customProfileBioFlow.asStateFlow()

    private val _personBiosFlow = MutableStateFlow<Map<Long, String>>(emptyMap())
    val personBiosFlow: StateFlow<Map<Long, String>> = _personBiosFlow.asStateFlow()

    private val _hideOtherFromHomeFeedFlow = MutableStateFlow(false)
    val hideOtherFromHomeFeedFlow: StateFlow<Boolean> = _hideOtherFromHomeFeedFlow.asStateFlow()

    private val _defaultCropModeFlow = MutableStateFlow(false)
    val defaultCropModeFlow: StateFlow<Boolean> = _defaultCropModeFlow.asStateFlow()

    private val _storyDurationSecondsFlow = MutableStateFlow(5)
    val storyDurationSecondsFlow: StateFlow<Int> = _storyDurationSecondsFlow.asStateFlow()

    private val _defaultInstaCardTemplateFlow = MutableStateFlow("OLED_DARK")
    val defaultInstaCardTemplateFlow: StateFlow<String> = _defaultInstaCardTemplateFlow.asStateFlow()

    private val _dismissedDuplicatePairsFlow = MutableStateFlow<Set<String>>(emptySet())
    val dismissedDuplicatePairsFlow: StateFlow<Set<String>> = _dismissedDuplicatePairsFlow.asStateFlow()

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

        _myProfilePersonIdFlow.value = prefs.getLong(KEY_MY_PROFILE_PERSON_ID, -1L)
        _customProfileBioFlow.value = prefs.getString(KEY_MY_PROFILE_BIO, DEFAULT_PROFILE_BIO)
            ?: DEFAULT_PROFILE_BIO

        val loadedBios = mutableMapOf<Long, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PERSON_BIO_PREFIX) && value is String) {
                val id = key.removePrefix(KEY_PERSON_BIO_PREFIX).toLongOrNull()
                if (id != null) {
                    loadedBios[id] = value
                }
            }
        }
        _personBiosFlow.value = loadedBios

        _hideOtherFromHomeFeedFlow.value = prefs.getBoolean(KEY_HIDE_OTHER_FROM_HOME, false)
        _defaultCropModeFlow.value = prefs.getBoolean(KEY_DEFAULT_CROP_MODE, false)
        _storyDurationSecondsFlow.value = prefs.getInt(KEY_STORY_DURATION_SECONDS, 5).coerceIn(3, 8)
        _defaultInstaCardTemplateFlow.value = prefs.getString(KEY_DEFAULT_INSTACARD_TEMPLATE, "OLED_DARK")
            ?: "OLED_DARK"
        _dismissedDuplicatePairsFlow.value = prefs.getStringSet(KEY_DISMISSED_DUPLICATE_PAIRS, emptySet())
            ?.toSet()
            ?: emptySet()

        initialized = true
    }

    fun setMyProfilePersonId(personId: Long) {
        _myProfilePersonIdFlow.value = personId
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_MY_PROFILE_PERSON_ID, personId)
                .apply()
        }
    }

    fun setMyProfilePersonId(context: Context, personId: Long) {
        init(context)
        setMyProfilePersonId(personId)
    }

    fun getMyProfilePersonId(): Long = _myProfilePersonIdFlow.value

    fun getMyProfilePersonId(context: Context): Long {
        init(context)
        return _myProfilePersonIdFlow.value
    }

    fun setCustomProfileBio(personId: Long, bio: String) {
        val cleanBio = bio.trim().ifEmpty { DEFAULT_PROFILE_BIO }
        _customProfileBioFlow.value = cleanBio
        if (personId > 0L) {
            _personBiosFlow.value = _personBiosFlow.value + (personId to cleanBio)
        }
        appContext?.let { ctx ->
            val editor = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.putString(KEY_MY_PROFILE_BIO, cleanBio)
            if (personId > 0L) {
                editor.putString("$KEY_PERSON_BIO_PREFIX$personId", cleanBio)
            }
            editor.apply()
        }
    }

    fun setCustomProfileBio(context: Context, personId: Long, bio: String) {
        init(context)
        setCustomProfileBio(personId, bio)
    }

    fun getCustomProfileBio(personId: Long): String {
        return _personBiosFlow.value[personId]
            ?: if (personId == _myProfilePersonIdFlow.value || personId <= 0L) {
                _customProfileBioFlow.value
            } else {
                DEFAULT_PROFILE_BIO
            }
    }

    fun getCustomProfileBio(context: Context, personId: Long): String {
        init(context)
        return getCustomProfileBio(personId)
    }

    fun setHideOtherFromHomeFeed(hide: Boolean) {
        _hideOtherFromHomeFeedFlow.value = hide
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HIDE_OTHER_FROM_HOME, hide)
                .apply()
        }
    }

    fun setHideOtherFromHomeFeed(context: Context, hide: Boolean) {
        init(context)
        setHideOtherFromHomeFeed(hide)
    }

    fun setDefaultCropMode(crop4x5: Boolean) {
        _defaultCropModeFlow.value = crop4x5
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEFAULT_CROP_MODE, crop4x5)
                .apply()
        }
    }

    fun setDefaultCropMode(context: Context, crop4x5: Boolean) {
        init(context)
        setDefaultCropMode(crop4x5)
    }

    fun setStoryDurationSeconds(seconds: Int) {
        val valid = when (seconds) {
            3, 5, 8 -> seconds
            else -> 5
        }
        _storyDurationSecondsFlow.value = valid
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_STORY_DURATION_SECONDS, valid)
                .apply()
        }
    }

    fun setStoryDurationSeconds(context: Context, seconds: Int) {
        init(context)
        setStoryDurationSeconds(seconds)
    }

    fun setDefaultInstaCardTemplate(templateName: String) {
        _defaultInstaCardTemplateFlow.value = templateName
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEFAULT_INSTACARD_TEMPLATE, templateName)
                .apply()
        }
    }

    fun setDefaultInstaCardTemplate(context: Context, templateName: String) {
        init(context)
        setDefaultInstaCardTemplate(templateName)
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

    fun getDismissedDuplicatePairs(context: Context): Set<String> {
        init(context)
        return _dismissedDuplicatePairsFlow.value
    }

    fun addDismissedDuplicatePair(pairKey: String) {
        if (pairKey.isBlank()) return
        val updated = _dismissedDuplicatePairsFlow.value + pairKey
        _dismissedDuplicatePairsFlow.value = updated
        appContext?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_DISMISSED_DUPLICATE_PAIRS, updated)
                .apply()
        }
    }

    fun addDismissedDuplicatePair(context: Context, pairKey: String) {
        init(context)
        addDismissedDuplicatePair(pairKey)
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
