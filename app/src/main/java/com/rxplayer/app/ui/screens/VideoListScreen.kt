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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
    onVideoClick: (String) -> Unit,
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
            onDismiss = { showLayoutDialog = false },
            onApply = { columns, crop, newSortBy, ascending, isPortrait ->
                viewModel.setGridColumns(columns)
                viewModel.setDisplayMode(crop)
                viewModel.setSort(newSortBy, ascending)
                viewModel.setThumbnailOrientation(isPortrait)
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
                            contentDescription = "布局设置"
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
                    onClick = { onVideoClick(video.filePath) }
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
    onDismiss: () -> Unit,
    onApply: (columns: Int, crop: Boolean, sortBy: String, ascending: Boolean, portrait: Boolean) -> Unit
) {
    var selectedColumns by remember { mutableStateOf(currentColumns) }
    var selectedCrop by remember { mutableStateOf(currentCropMode) }
    var selectedSortBy by remember { mutableStateOf(currentSortBy) }
    var selectedAscending by remember { mutableStateOf(currentSortAscending) }
    var selectedPortrait by remember { mutableStateOf(currentPortrait) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("布局设置", style = MaterialTheme.typography.titleMedium)
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(selectedColumns, selectedCrop, selectedSortBy, selectedAscending, selectedPortrait)
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

private fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
