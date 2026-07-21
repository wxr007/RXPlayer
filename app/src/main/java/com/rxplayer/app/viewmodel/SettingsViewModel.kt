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
    val seekStep: StateFlow<Int> = settingsManager.seekStep
    val resolutionDisplay: StateFlow<String> = settingsManager.resolutionDisplay
    val screenshotPath: StateFlow<String> = settingsManager.screenshotPath

    fun setThemeMode(mode: String) {
        settingsManager.setThemeMode(mode)
    }

    fun setAutoPlay(enabled: Boolean) {
        settingsManager.setAutoPlay(enabled)
    }

    fun setSeekStep(seconds: Int) {
        settingsManager.setSeekStep(seconds)
    }

    fun setResolutionDisplay(mode: String) {
        settingsManager.setResolutionDisplay(mode)
    }

    fun setScreenshotPath(path: String) {
        settingsManager.setScreenshotPath(path)
    }
}