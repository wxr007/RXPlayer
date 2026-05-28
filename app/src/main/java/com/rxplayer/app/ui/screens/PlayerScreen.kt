package com.rxplayer.app.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.ui.components.SceneAnalysisProgress
import com.rxplayer.app.ui.components.TimelinePreviewBar
import com.rxplayer.app.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoPath: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scenes by viewModel.scenes.collectAsState()
    val progress by viewModel.analyzingProgress.collectAsState()
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isFullScreen by remember { mutableStateOf(false) }

    val decodedPath = Uri.decode(videoPath)
    val videoName = decodedPath.substringAfterLast("/")

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            delay(200)
        }
    }

    val toggleFullScreen: () -> Unit = {
        isFullScreen = !isFullScreen
        activity?.let { act ->
            val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
            if (isFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.let { act ->
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                CompactTopAppBar(
                    title = videoName,
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = toggleFullScreen) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "全屏"
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val exoPlayer = player
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!isFullScreen) {
                if (progress != null) {
                    SceneAnalysisProgress(
                        progress = progress!!,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TimelinePreviewBar(
                    scenes = scenes,
                    currentPosition = currentPosition,
                    onSceneClick = { timestampMs ->
                        player.seekTo(timestampMs)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
