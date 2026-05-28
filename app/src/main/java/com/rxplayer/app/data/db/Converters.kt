package com.rxplayer.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCoverPaths(value: List<String>): String {
        return value.joinToString("\n")
    }

    @TypeConverter
    fun toCoverPaths(value: String): List<String> {
        return value.split("\n").filter { it.isNotEmpty() }
    }
}
