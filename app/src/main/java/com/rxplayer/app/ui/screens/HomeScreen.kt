package com.rxplayer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rxplayer.app.data.model.VideoFolder
import com.rxplayer.app.viewmodel.HomeViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFolderClick: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频文件夹") },
                actions = {
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加文件夹")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                    .padding(innerPadding)
            ) {
                items(folders, key = { it.path }) { folder ->
                    FolderCard(
                        folder = folder,
                        onClick = { onFolderClick(folder.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                if (folder.coverPaths.isNotEmpty()) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val rows = folder.coverPaths.chunked(2)
                        for (row in rows) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                for (i in 0 until 2) {
                                    val path = row.getOrNull(i)
                                    if (path != null) {
                                        val model = if (path.startsWith("content://")) {
                                            ImageRequest.Builder(context)
                                                .data(path)
                                                .crossfade(true)
                                                .build()
                                        } else {
                                            ImageRequest.Builder(context)
                                                .data(File(path))
                                                .crossfade(true)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(0.5f),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.4f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp)
            )
            Text(
                text = "${folder.videoCount} 个视频",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
