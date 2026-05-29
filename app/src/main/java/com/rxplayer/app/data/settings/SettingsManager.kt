package com.rxplayer.app.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<String> = _themeMode

    private val _autoPlay = MutableStateFlow(readAutoPlay())
    val autoPlay: StateFlow<Boolean> = _autoPlay

    private val _analysisMode = MutableStateFlow(readAnalysisMode())
    val analysisMode: StateFlow<String> = _analysisMode

    private val _analysisInterval = MutableStateFlow(readAnalysisInterval())
    val analysisInterval: StateFlow<Int> = _analysisInterval

    private val _seekStep = MutableStateFlow(readSeekStep())
    val seekStep: StateFlow<Int> = _seekStep

    private fun readThemeMode(): String = prefs.getString("theme_mode", "system") ?: "system"
    private fun readAutoPlay(): Boolean = prefs.getBoolean("auto_play", true)
    private fun readAnalysisMode(): String = prefs.getString("analysis_mode", "smart") ?: "smart"
    private fun readAnalysisInterval(): Int = prefs.getInt("analysis_interval", 30)
    private fun readSeekStep(): Int = prefs.getInt("seek_step", 10)

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setAutoPlay(enabled: Boolean) {
        prefs.edit().putBoolean("auto_play", enabled).apply()
        _autoPlay.value = enabled
    }

    fun setAnalysisMode(mode: String) {
        prefs.edit().putString("analysis_mode", mode).apply()
        _analysisMode.value = mode
    }

    fun setAnalysisInterval(seconds: Int) {
        val clamped = seconds.coerceIn(5, 60)
        prefs.edit().putInt("analysis_interval", clamped).apply()
        _analysisInterval.value = clamped
    }

    fun setSeekStep(seconds: Int) {
        val clamped = seconds.coerceIn(5, 15)
        prefs.edit().putInt("seek_step", clamped).apply()
        _seekStep.value = clamped
    }
}