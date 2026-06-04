package com.rxplayer.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rxplayer.app.media.ThumbnailCache
import com.rxplayer.app.ui.components.CompactTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Entry(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)

@Composable
fun FileBrowserScreen(
    startPath: String,
    onBack: () -> Unit
) {
    var currentPath by remember { mutableStateOf(startPath) }

    val entries = remember(currentPath) {
        val dir = File(currentPath)
        val files = dir.listFiles() ?: emptyArray()
        val dirs = files.filter { it.isDirectory }.sortedBy { it.name }
        val regular = files.filter { it.isFile }.sortedBy { it.name }
        dirs.map { Entry(it.name, it.absolutePath, it.length(), it.lastModified(), true) } +
            regular.map { Entry(it.name, it.absolutePath, it.length(), it.lastModified(), false) }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = File(currentPath).name.ifEmpty { "缓存目录" },
                onBack = {
                    val parent = File(currentPath).parentFile
                    if (parent != null && currentPath.startsWith(startPath)) {
                        currentPath = parent.absolutePath
                    } else {
                        onBack()
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "空文件夹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                items(entries, key = { it.path }) { entry ->
                    if (entry.isDirectory) {
                        FolderRow(
                            entry = entry,
                            onClick = { currentPath = entry.path }
                        )
                    } else {
                        FileRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    entry: Entry,
    onClick: () -> Unit
) {
    var totalSize by remember(entry.path) { mutableStateOf(-1L) }

    LaunchedEffect(entry.path) {
        totalSize = withContext(Dispatchers.IO) {
            File(entry.path).walk().filter { it.isFile }.sumOf { it.length() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (totalSize >= 0) formatSize(totalSize) else "计算中...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "打开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
private val videoExtensions = setOf("mp4", "mkv", "ts", "webm", "avi", "mov", "3gp", "m4v")

@Composable
private fun FileRow(entry: Entry) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    val isImage = ext in imageExtensions
    val isVideo = ext in videoExtensions

    var thumbnail by remember(entry.path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.path) {
        if (isImage) {
            thumbnail = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(entry.path)
                } catch (_: Exception) { null }
            }
        } else if (isVideo) {
            thumbnail = ThumbnailCache(context).getThumbnail(entry.path)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            val icon = when {
                isImage -> Icons.Default.Image
                isVideo -> Icons.Default.Movie
                else -> Icons.Default.Description
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatSize(entry.size)} · ${dateFormat.format(Date(entry.lastModified))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
    HorizontalDivider()
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
