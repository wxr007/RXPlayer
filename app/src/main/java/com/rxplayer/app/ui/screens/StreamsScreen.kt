package com.rxplayer.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.rxplayer.app.media.ExportState
import com.rxplayer.app.media.ThumbnailCache
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.viewmodel.StreamItem
import com.rxplayer.app.viewmodel.StreamViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StreamsScreen(
    onStreamClick: (url: String, streamId: Long, displayName: String) -> Unit,
    viewModel: StreamViewModel = hiltViewModel()
) {
    val streams by viewModel.streams.collectAsState()
    val cachingIds by viewModel.cachingIds.collectAsState()
    val cachingProgress by viewModel.cachingProgress.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<StreamItem?>(null) }
    var renameTarget by remember { mutableStateOf<StreamItem?>(null) }
    var exportTarget by remember { mutableStateOf<StreamItem?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/*")
    ) { uri ->
        if (uri != null && exportTarget != null) {
            viewModel.exportStream(exportTarget!!.id, uri)
        }
        exportTarget = null
    }

    LaunchedEffect(exportState) {
        if (exportState is ExportState.Completed || exportState is ExportState.Error) {
            // auto-dismiss after a delay; dialog handles the display
        }
    }

    if (exportState !is ExportState.Idle) {
        ExportProgressDialog(
            state = exportState,
            onDismiss = { viewModel.resetExportState() }
        )
    }

    if (showAddDialog) {
        AddStreamDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url ->
                val name = url.substringAfterLast("/").substringBeforeLast("?").take(40).ifEmpty { url.take(40) }
                viewModel.addStream(name, url)
                showAddDialog = false
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除流媒体") },
            text = { Text("确定要删除\"${deleteTarget!!.name}\"吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStream(deleteTarget!!.id)
                    deleteTarget = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf(renameTarget!!.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renameStream(renameTarget!!.id, newName.trim())
                            renameTarget = null
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = "流媒体",
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加流媒体")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无流媒体\n请点击右上角 + 添加串流地址",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(streams, key = { it.id }) { stream ->
                    StreamCard(
                        stream = stream,
                        isCaching = stream.id in cachingIds,
                        cacheProgress = cachingProgress[stream.id] ?: 0,
                        onClick = {
                            val playPath = if (stream.cachedPath.isNotEmpty() && stream.cachedPath.toLongOrNull() == null) stream.cachedPath else stream.url
                            onStreamClick(playPath, stream.id, stream.name)
                        },
                        onLongClick = { deleteTarget = stream },
                        onCacheClick = { viewModel.cacheStream(stream.id) },
                        onRenameClick = { renameTarget = stream },
                        onExportClick = {
                            exportTarget = stream
                            exportLauncher.launch("${stream.name}.ts")
                        },
                        onDeleteClick = { deleteTarget = stream }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamCard(
    stream: StreamItem,
    isCaching: Boolean,
    cacheProgress: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCacheClick: () -> Unit,
    onRenameClick: () -> Unit,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(stream.url, stream.coverPath) { mutableStateOf<Bitmap?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }

    if (showProperties) {
        AlertDialog(
            onDismissRequest = { showProperties = false },
            title = { Text("属性") },
            text = {
                Column {
                    Text("名称: ${stream.name}", style = MaterialTheme.typography.bodySmall)
                    Text("分辨率: ${stream.resolution}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("编码: ${stream.codec}", style = MaterialTheme.typography.bodySmall)
                    Text("帧率: ${stream.frameRate}", style = MaterialTheme.typography.bodySmall)
                    if (stream.durationMs > 0L) {
                        val totalSec = stream.durationMs / 1000
                        val h = totalSec / 3600
                        val m = (totalSec % 3600) / 60
                        val s = totalSec % 60
                        val durationStr = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                        Text("时长: $durationStr", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("链接: ${stream.url}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showProperties = false }) {
                    Text("关闭")
                }
            }
        )
    }

    LaunchedEffect(stream.url, stream.coverPath) {
        val path = if (stream.coverPath.isNotEmpty()) stream.coverPath
            else ThumbnailCache(context).getCachedPath(stream.url)
        val file = File(path)
        thumbnail = withContext(Dispatchers.IO) {
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    val isCached = stream.cachedPath.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = stream.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isCaching) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { cacheProgress / 100f },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "$cacheProgress%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (isCached) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.4f),
                        tint = Color(0xFF4CAF50)
                    )
                } else if (isCaching) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { cacheProgress / 100f },
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "$cacheProgress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.4f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多选项",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                showMenu = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出视频") },
                            onClick = {
                                showMenu = false
                                onExportClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("属性") },
                            onClick = {
                                showMenu = false
                                showProperties = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
            Text(
                text = stream.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp)
            )
            Text(
                text = if (isCached) "已缓存" else stream.url,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun AddStreamDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加流媒体") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("串流地址") },
                placeholder = { Text("https://example.com/stream.m3u8") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onAdd(url.trim()) },
                enabled = url.isNotBlank()
            ) {
                Text("添加")
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
private fun ExportProgressDialog(
    state: ExportState,
    onDismiss: () -> Unit
) {
    when (state) {
        is ExportState.Preparing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("导出视频") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("导出视频") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${state.percent}%",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is ExportState.Completed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导出完成") },
                text = { Text("视频文件已成功导出。") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("确定")
                    }
                }
            )
        }
        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导出失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            )
        }
        is ExportState.Idle -> { /* no dialog */ }
    }
}
