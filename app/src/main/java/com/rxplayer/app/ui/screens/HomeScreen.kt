package com.rxplayer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rxplayer.app.data.model.MediaCollection
import com.rxplayer.app.data.model.VideoFolder
import com.rxplayer.app.ui.components.CollectionCard
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onFolderClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val scanningStatus by viewModel.scanningStatus.collectAsState()

    var deleteTarget by remember { mutableStateOf<VideoFolder?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it) }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件夹") },
            text = { Text("确定要删除\"${deleteTarget!!.name}\"吗？不会删除实际文件，只会从列表中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(deleteTarget!!.path)
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

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = "视频文件夹",
                actions = {
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加文件夹")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = scanningStatus != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = scanningStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(top = 2.dp)
                    )
                }
            }
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无视频文件夹\n请点击右上角 + 添加",
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
                ) {
                    items(folders, key = { it.path }) { folder ->
                        CollectionCard(
                            collection = MediaCollection.Folder(folder),
                            progress = scanProgress[folder.path],
                            onClick = { onFolderClick(folder.path) },
                            onLongClick = { deleteTarget = folder }
                        )
                    }
                }
            }
        }
    }
}
