// InstaGallery v1.0

package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OnboardingPreferences {
    private const val PREFS_NAME = "instagallery_onboarding_prefs_v4"
    private const val KEY_SCAN_COMPLETED = "initial_scan_completed"
    private const val KEY_SETUP_COMPLETED = "profile_setup_completed"

    private val _isScanCompleted = MutableStateFlow(false)
    val isScanCompleted: StateFlow<Boolean> = _isScanCompleted.asStateFlow()

    private val _isSetupCompleted = MutableStateFlow(false)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isScanCompleted.value = prefs.getBoolean(KEY_SCAN_COMPLETED, false)
        _isSetupCompleted.value = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        initialized = true
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
