package com.rxplayer.app.data.model

data class VideoFolder(
    val id: Long = 0,
    val name: String,
    val path: String,
    val videoCount: Int,
    val coverPaths: List<String>,
    val addedAt: Long = System.currentTimeMillis(),
    val displayMode: Int = 1,
    val gridColumns: Int = 3,
    val sortBy: String = "date",
    val sortAscending: Int = 0,
    val thumbnailOrientation: Int = 0,
    val autoFullscreen: Int = 0,
    val playbackMode: Int = 0,
    val privacyMask: Boolean = false
)
