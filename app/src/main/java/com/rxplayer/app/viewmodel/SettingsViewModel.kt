package com.rxplayer.app.viewmodel

import androidx.lifecycle.ViewModel
import com.rxplayer.app.data.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val themeMode: StateFlow<String> = settingsManager.themeMode
    val autoPlay: StateFlow<Boolean> = settingsManager.autoPlay
    val analysisMode: StateFlow<String> = settingsManager.analysisMode
    val analysisInterval: StateFlow<Int> = settingsManager.analysisInterval
    val seekStep: StateFlow<Int> = settingsManager.seekStep

    fun setThemeMode(mode: String) {
        settingsManager.setThemeMode(mode)
    }

    fun setAutoPlay(enabled: Boolean) {
        settingsManager.setAutoPlay(enabled)
    }

    fun setAnalysisMode(mode: String) {
        settingsManager.setAnalysisMode(mode)
    }

    fun setAnalysisInterval(seconds: Int) {
        settingsManager.setAnalysisInterval(seconds)
    }

    fun setSeekStep(seconds: Int) {
        settingsManager.setSeekStep(seconds)
    }
}