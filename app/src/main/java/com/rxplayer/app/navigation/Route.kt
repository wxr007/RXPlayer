package com.rxplayer.app.navigation

import android.util.Base64

sealed class Route(val route: String) {
    object FolderList : Route("folders")
    object VideoList : Route("videos/{folderPath}") {
        fun createRoute(folderPath: String) =
            "videos/${Base64.encodeToString(folderPath.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)}"
        fun decodePath(encoded: String): String =
            String(Base64.decode(encoded, Base64.URL_SAFE))
    }
    object Favorites : Route("favorites")
    object History : Route("history")
    object Settings : Route("settings")
    object Player : Route("player/{videoPath}?autoFullscreen={autoFullscreen}&playbackMode={playbackMode}&folderPath={folderPath}") {
        fun createRoute(videoPath: String, autoFullscreen: Int = 0, playbackMode: Int = 0, folderPath: String = "") =
            "player/${Base64.encodeToString(videoPath.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)}?autoFullscreen=$autoFullscreen&playbackMode=$playbackMode&folderPath=${Base64.encodeToString(folderPath.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)}"
        fun decodePath(encoded: String): String =
            String(Base64.decode(encoded, Base64.URL_SAFE))
        fun decodeFolderPath(encoded: String): String =
            try { String(Base64.decode(encoded, Base64.URL_SAFE)) } catch (_: Exception) { "" }
    }
}
