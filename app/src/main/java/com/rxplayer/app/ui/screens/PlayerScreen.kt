package com.rxplayer.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PlayerScreen(
    videoPath: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var showCenterIcon by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

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
            totalDuration = player.duration.coerceAtLeast(0)
            delay(200)
        }
    }

    LaunchedEffect(showCenterIcon) {
        if (showCenterIcon) {
            delay(800)
            showCenterIcon = false
        }
    }

    LaunchedEffect(isFullScreen, showOverlay) {
        if (isFullScreen && showOverlay) {
            delay(3000)
            showOverlay = false
        }
    }

    val toggleFullScreen: () -> Unit = {
        val goingFullScreen = !isFullScreen
        isFullScreen = goingFullScreen
        showOverlay = true
        activity?.let { act ->
            if (goingFullScreen) {
                val videoSize = player.videoSize
                if (videoSize.width > videoSize.height) {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.setPlaybackSpeed(1f)
            player.release()
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
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
                    onBack = onBack
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
                    .then(
                        if (isFullScreen) Modifier.weight(1f)
                        else Modifier.aspectRatio(16f / 9f)
                    )
                    .background(MaterialTheme.colorScheme.background)
                    .gestureHandler(
                        player = player,
                        isFullScreen = isFullScreen,
                        showOverlay = showOverlay,
                        onDoubleTap = {
                            player.playWhenReady = !player.playWhenReady
                            showCenterIcon = true
                        },
                        onSingleTap = { showOverlay = !showOverlay },
                        onSpeedChange = { speed -> playbackSpeed = speed }
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        val exoPlayer = player
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isFullScreen && showOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }

                if (showCenterIcon) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (player.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }
                }

                if (playbackSpeed > 1f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(playbackSpeed),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isFullScreen && showOverlay) {
                    IconButton(
                        onClick = toggleFullScreen,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "退出全屏",
                            tint = Color.White
                        )
                    }
                }
            }

            if (!isFullScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Slider(
                        value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
                        onValueChange = { ratio ->
                            player.seekTo((ratio * totalDuration).toLong())
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Text(
                        text = formatDuration(totalDuration),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    IconButton(onClick = toggleFullScreen) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "全屏"
                        )
                    }
                }
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

private fun Modifier.gestureHandler(
    player: ExoPlayer,
    isFullScreen: Boolean,
    showOverlay: Boolean,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit,
    onSpeedChange: (Float) -> Unit
): Modifier = this.then(
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            val longPressMs = viewConfiguration.longPressTimeoutMillis

            val up = withTimeoutOrNull(longPressMs) {
                waitForUpOrCancellation()
            }

            if (up != null) {
                val secondUp = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown()
                    waitForUpOrCancellation()
                    true
                }
                if (secondUp != null) {
                    onDoubleTap()
                } else {
                    onSingleTap()
                }
            } else {
                player.setPlaybackSpeed(2f)
                onSpeedChange(2f)
                waitForUpOrCancellation()
                player.setPlaybackSpeed(1f)
                onSpeedChange(1f)
            }
        }
    }
)
