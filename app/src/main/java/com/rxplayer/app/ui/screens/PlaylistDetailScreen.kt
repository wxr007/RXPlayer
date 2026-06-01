package com.rxplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rxplayer.app.data.model.Video
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val videos by viewModel.playlistVideos.collectAsState()
    var removeTarget by remember { mutableStateOf<Video?>(null) }

    LaunchedEffect(playlistId) {
        viewModel.observePlaylistVideos(playlistId)
    }

    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("从播放列表移除") },
            text = { Text("确定要将\"${removeTarget!!.fileName}\"从当前播放列表中移除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeVideoFromPlaylist(playlistId, removeTarget!!.filePath)
                    removeTarget = null
                }) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = playlistName,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "播放列表为空",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
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
                        cropMode = false,
                        portrait = false,
                        onClick = { onVideoClick(video.filePath) },
                        onRemoveFromPlaylistClick = { removeTarget = video }
                    )
                }
            }
        }
    }
}
