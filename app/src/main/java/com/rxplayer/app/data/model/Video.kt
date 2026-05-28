package com.rxplayer.app.data.model

data class Video(
    val id: Long = 0,
    val folderPath: String,
    val fileName: String,
    val filePath: String,
    val duration: Long,
    val fileSize: Long,
    val resolution: String,
    val mimeType: String,
    val addedAt: Long
)
