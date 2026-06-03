package com.rxplayer.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

private data class CacheEntry(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val category: String
)

private data class CacheFolder(
    val name: String,
    val path: String,
    val fileCount: Int,
    val totalSize: Long,
    val category: String
)

@Composable
fun CacheBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf<CacheFolder?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空缓存") },
            text = { Text("确定要删除所有缓存文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    clearCache(context.cacheDir)
                    showClearDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (currentDir != null) {
        CacheFolderScreen(
            folder = currentDir!!,
            onBack = { currentDir = null }
        )
    } else {
        CacheRootScreen(
            onClearClick = { showClearDialog = true },
            onFolderClick = { currentDir = it }
        )
    }
}

@Composable
private fun CacheRootScreen(
    onClearClick: () -> Unit,
    onFolderClick: (CacheFolder) -> Unit
) {
    val context = LocalContext.current
    var totalSize by remember { mutableStateOf(0L) }
    var totalCount by remember { mutableStateOf(0) }

    val thumbnailFolder = remember {
        val dir = File(context.cacheDir, "video_thumbnails")
        val files = dir.listFiles() ?: emptyArray()
        CacheFolder(
            name = "video_thumbnails",
            path = dir.absolutePath,
            fileCount = files.size,
            totalSize = files.sumOf { it.length() },
            category = "缩略图缓存"
        )
    }

    val sceneFolders = remember {
        val sceneDir = File(context.cacheDir, "scene_thumbnails")
        if (!sceneDir.exists()) return@remember emptyList()
        sceneDir.listFiles()?.filter { it.isDirectory }?.map { subDir ->
            val files = subDir.listFiles() ?: emptyArray()
            CacheFolder(
                name = subDir.name,
                path = subDir.absolutePath,
                fileCount = files.size,
                totalSize = files.sumOf { it.length() },
                category = "场景缩略图"
            )
        }?.sortedBy { folder: CacheFolder -> folder.name } ?: emptyList()
    }

    val streamFolder = remember {
        val dir = File(context.cacheDir, "stream_cache")
        val files = dir.listFiles() ?: emptyArray()
        CacheFolder(
            name = "stream_cache",
            path = dir.absolutePath,
            fileCount = files.size,
            totalSize = files.sumOf { it.length() },
            category = "视频缓存"
        )
    }

    val exoCacheFolder = remember {
        val dir = File(context.cacheDir, "exoplayer_cache")
        if (!dir.exists()) return@remember null
        val files = dir.listFiles() ?: emptyArray()
        CacheFolder(
            name = "exoplayer_cache",
            path = dir.absolutePath,
            fileCount = files.size,
            totalSize = files.sumOf { it.length() },
            category = "ExoPlayer缓存"
        )
    }

    LaunchedEffect(Unit) {
        totalSize = thumbnailFolder.totalSize + streamFolder.totalSize + sceneFolders.sumOf { it.totalSize } + (exoCacheFolder?.totalSize ?: 0)
        totalCount = thumbnailFolder.fileCount + streamFolder.fileCount + sceneFolders.sumOf { it.fileCount } + (exoCacheFolder?.fileCount ?: 0)
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = "缓存管理",
                onBack = null
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Button(
                    onClick = onClearClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空所有缓存")
                }
            }

            if (thumbnailFolder.fileCount > 0) {
                item {
                    Text(
                        text = "缩略图缓存",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    FolderItem(
                        folder = thumbnailFolder,
                        onClick = { onFolderClick(thumbnailFolder) }
                    )
                }
            }

            if (sceneFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "场景缩略图",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                items(sceneFolders) { folder ->
                    FolderItem(
                        folder = folder,
                        onClick = { onFolderClick(folder) }
                    )
                }
            }

            if (streamFolder.fileCount > 0) {
                item {
                    Text(
                        text = "视频缓存",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                item {
                    FolderItem(
                        folder = streamFolder,
                        onClick = { onFolderClick(streamFolder) }
                    )
                }
            }

            if (exoCacheFolder != null && exoCacheFolder.fileCount > 0) {
                item {
                    Text(
                        text = "ExoPlayer缓存",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                item {
                    FolderItem(
                        folder = exoCacheFolder,
                        onClick = { onFolderClick(exoCacheFolder) }
                    )
                }
            }

            if (totalCount == 0) {
                item {
                    Text(
                        text = "暂无缓存文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (totalCount > 0) {
                item {
                    Text(
                        text = "总计: ${totalCount}个文件, ${formatSize(totalSize)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: CacheFolder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
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
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${folder.fileCount}个文件 · ${formatSize(folder.totalSize)} · ${folder.category}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun CacheFolderScreen(
    folder: CacheFolder,
    onBack: () -> Unit
) {
    var entries by remember(folder.path) {
        mutableStateOf(loadFolderFiles(folder.path))
    }
    var previewIndex by remember { mutableStateOf(-1) }

    val title = when (folder.category) {
        "缩略图缓存" -> "缩略图缓存"
        "场景缩略图" -> folder.name.take(12)
        "视频缓存" -> "视频缓存"
        else -> folder.name
    }

    if (previewIndex >= 0 && previewIndex < entries.size) {
        ImagePreviewPager(
            images = entries.map { it.path },
            initialIndex = previewIndex,
            onDismiss = { previewIndex = -1 }
        )
        return
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = title,
                onBack = onBack
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
                    text = "文件夹为空",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (folder.category == "场景缩略图") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(entries, key = { it.path }) { entry ->
                    SceneThumbnailCell(
                        entry = entry,
                        onClick = {
                            val idx = entries.indexOfFirst { it.path == entry.path }
                            if (idx >= 0) previewIndex = idx
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                items(entries, key = { it.path }) { entry ->
                    CacheItemRow(entry)
                }
            }
        }
    }
}

@Composable
private fun SceneThumbnailCell(
    entry: CacheEntry,
    onClick: () -> Unit
) {
    var bitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.path) {
        bitmap = withContext(Dispatchers.IO) {
            val file = File(entry.path)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(entry.path, opts)
            } else null
        }
    }

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ImagePreviewPager(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { images.size })
    var showInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (showInfo) showInfo = false
                    else onDismiss()
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var bitmap by remember(images[page]) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(images[page]) {
                bitmap = withContext(Dispatchers.IO) {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(images[page], opts)
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    var sample = 1
                    while (maxDim / sample > 2048) sample *= 2
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFile(images[page], decodeOpts)
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        if (showInfo) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CacheItemRow(entry: CacheEntry) {
    val context = LocalContext.current
    var bitmap by remember(entry.path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.path) {
        bitmap = withContext(Dispatchers.IO) {
            val file = File(entry.path)
            if (!file.exists()) return@withContext null
            val ext = file.extension.lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "webp")) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(entry.path, opts)
            } else {
                ThumbnailCache(context).getThumbnail(entry.path, 120)
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
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

private fun loadFolderFiles(dirPath: String): List<CacheEntry> {
    val dir = File(dirPath)
    if (!dir.exists()) return emptyList()
    return dir.listFiles()
        ?.map { file ->
            CacheEntry(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                category = ""
            )
        }
        ?.sortedByDescending { it.lastModified }
        ?: emptyList()
}

private fun clearCache(cacheDir: File) {
    File(cacheDir, "video_thumbnails").let { dir ->
        if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
    }
    File(cacheDir, "stream_cache").let { dir ->
        if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
    }
    File(cacheDir, "scene_thumbnails").let { dir ->
        if (dir.exists()) {
            dir.listFiles()?.forEach { sub ->
                if (sub.isDirectory) sub.listFiles()?.forEach { it.delete() }
                sub.delete()
            }
        }
    }
    File(cacheDir, "exoplayer_cache").let { dir ->
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
