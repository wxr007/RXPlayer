package com.rxplayer.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.media.ThumbnailCache
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.viewmodel.VideoListViewModel

@Composable
fun VideoListScreen(
    folderPath: String,
    onVideoClick: (String, Boolean, Int) -> Unit,
    onBack: () -> Unit,
    viewModel: VideoListViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsState()
    val folderName = if (folderPath.startsWith("content://")) {
        folderPath.substringAfterLast("/").substringAfter("%3A").let { Uri.decode(it) }
    } else {
        folderPath.substringAfterLast("/")
    }

    val cropMode by viewModel.displayMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val portrait by viewModel.thumbnailOrientation.collectAsState()
    val autoFullscreen by viewModel.autoFullscreen.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val resolutionDisplay by viewModel.resolutionDisplay.collectAsState()
    var showLayoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(folderPath) {
        viewModel.loadVideos(folderPath)
    }

    if (showLayoutDialog) {
        LayoutSettingsDialog(
            currentColumns = gridColumns,
            currentCropMode = cropMode,
            currentSortBy = sortBy,
            currentSortAscending = sortAscending,
            currentPortrait = portrait,
            currentAutoFullscreen = autoFullscreen,
            currentPlaybackMode = playbackMode,
            onDismiss = { showLayoutDialog = false },
            onApply = { columns, crop, newSortBy, ascending, isPortrait, isAutoFullscreen, mode ->
                viewModel.setGridColumns(columns)
                viewModel.setDisplayMode(crop)
                viewModel.setSort(newSortBy, ascending)
                viewModel.setThumbnailOrientation(isPortrait)
                viewModel.setAutoFullscreen(isAutoFullscreen)
                viewModel.setPlaybackMode(mode)
                showLayoutDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = folderName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showLayoutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "文件夹设置"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(videos, key = { it.filePath }) { video ->
                VideoGridItem(
                    video = video,
                    cropMode = cropMode,
                    portrait = portrait,
                    resolutionDisplay = resolutionDisplay,
                    onClick = { onVideoClick(video.filePath, autoFullscreen, playbackMode) }
                )
            }
        }
    }
}

@Composable
private fun LayoutSettingsDialog(
    currentColumns: Int,
    currentCropMode: Boolean,
    currentSortBy: String,
    currentSortAscending: Boolean,
    currentPortrait: Boolean,
    currentAutoFullscreen: Boolean,
    currentPlaybackMode: Int,
    onDismiss: () -> Unit,
    onApply: (columns: Int, crop: Boolean, sortBy: String, ascending: Boolean, portrait: Boolean, autoFullscreen: Boolean, playbackMode: Int) -> Unit
) {
    var selectedColumns by remember { mutableStateOf(currentColumns) }
    var selectedCrop by remember { mutableStateOf(currentCropMode) }
    var selectedSortBy by remember { mutableStateOf(currentSortBy) }
    var selectedAscending by remember { mutableStateOf(currentSortAscending) }
    var selectedPortrait by remember { mutableStateOf(currentPortrait) }
    var selectedAutoFullscreen by remember { mutableStateOf(currentAutoFullscreen) }
    var selectedPlaybackMode by remember { mutableStateOf(currentPlaybackMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("文件夹设置", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text("每行显示", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(2, 3, 4).forEach { n ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RadioButton(
                                selected = selectedColumns == n,
                                onClick = { selectedColumns = n }
                            )
                            Text("$n 列", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(
                    "缩略图显示",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = !selectedCrop,
                            onClick = { selectedCrop = false }
                        )
                        Text("留白", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedCrop,
                            onClick = { selectedCrop = true }
                        )
                        Text("剪裁", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    "排序方式",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("name" to "名称", "date" to "日期", "duration" to "时长", "size" to "大小").forEach { (key, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RadioButton(
                                selected = selectedSortBy == key,
                                onClick = {
                                    selectedSortBy = key
                                    selectedAscending = true
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedAscending,
                            onClick = { selectedAscending = true }
                        )
                        Text("升序", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = !selectedAscending,
                            onClick = { selectedAscending = false }
                        )
                        Text("降序", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    "缩略图方向",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = !selectedPortrait,
                            onClick = { selectedPortrait = false }
                        )
                        Text("横屏", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedPortrait,
                            onClick = { selectedPortrait = true }
                        )
                        Text("竖屏", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    "自动全屏",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = !selectedAutoFullscreen,
                            onClick = { selectedAutoFullscreen = false }
                        )
                        Text("关闭", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedAutoFullscreen,
                            onClick = { selectedAutoFullscreen = true }
                        )
                        Text("开启", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    "播放模式",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0 to "单次", 1 to "单曲循环", 2 to "顺序", 3 to "列表循环").forEach { (value, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RadioButton(
                                selected = selectedPlaybackMode == value,
                                onClick = { selectedPlaybackMode = value }
                            )
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(selectedColumns, selectedCrop, selectedSortBy, selectedAscending, selectedPortrait, selectedAutoFullscreen, selectedPlaybackMode)
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun VideoGridItem(
    video: Video,
    cropMode: Boolean,
    portrait: Boolean,
    resolutionDisplay: String = "full",
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(video.filePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.filePath) {
        try {
            val cache = ThumbnailCache(context)
            thumbnail = cache.getThumbnail(video.filePath)
        } catch (_: Exception) {
            thumbnail = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            val thumbAspectRatio = if (portrait) 9f / 16f else 16f / 9f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(thumbAspectRatio)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = video.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (cropMode) ContentScale.Crop else ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.4f).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                if (video.resolution.isNotEmpty()) {
                    val parts = video.resolution.split("x")
                    val displayText = if (resolutionDisplay == "height" && parts.size == 2) {
                        parts[1]
                    } else if (parts.size == 2) {
                        "${parts[0]}×${parts[1]}"
                    } else {
                        video.resolution
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }

                var showMenu by remember { mutableStateOf(false) }
                var showProperties by remember { mutableStateOf(false) }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("属性") },
                            onClick = {
                                showMenu = false
                                showProperties = true
                            }
                        )
                    }
                }

                if (showProperties) {
                    VideoPropertiesDialog(
                        video = video,
                        onDismiss = { showProperties = false }
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = video.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPropertiesDialog(
    video: Video,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("视频属性", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                PropertyRow("文件名", video.fileName)
                PropertyRow("分辨率", video.resolution.ifEmpty { "未知" })
                PropertyRow("时长", formatDuration(video.duration))
                PropertyRow("文件大小", formatFileSize(video.fileSize))
                PropertyRow("类型", video.mimeType)
                PropertyRow("路径", simplifyPath(video.filePath))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000f)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000f)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
        else -> "$bytes B"
    }
}

private fun simplifyPath(filePath: String): String {
    val decoded = Uri.decode(filePath)
    return if (decoded.startsWith("content://")) {
        decoded.substringAfter("primary:")
    } else {
        decoded.removePrefix("/storage/emulated/0/")
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
