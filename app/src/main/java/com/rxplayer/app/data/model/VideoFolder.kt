package com.rxplayer.app.data.model

data class VideoFolder(
    val id: Long = 0,
    val name: String,
    val path: String,
    val videoCount: Int,
    val coverPaths: List<String>,
    val addedAt: Long = System.currentTimeMillis()
)
