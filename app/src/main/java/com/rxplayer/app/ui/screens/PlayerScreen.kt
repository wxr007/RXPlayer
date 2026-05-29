package com.rxplayer.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.WindowManager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.ui.components.SceneAnalysisProgress
import com.rxplayer.app.ui.components.SceneGrid
import com.rxplayer.app.ui.components.TimelinePreviewBar
import com.rxplayer.app.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSeekIndicator by remember { mutableStateOf("") }
    var sliderProgress by remember { mutableFloatStateOf(0f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isDraggingTimeline by remember { mutableStateOf(false) }
    val timelineLazyListState = rememberLazyListState()
    var overlayTimerKey by remember { mutableStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var privacyMaskEnabled by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val scenes by viewModel.scenes.collectAsState()
    val analyzingProgress by viewModel.analyzingProgress.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val autoPlay by viewModel.autoPlay.collectAsState()

    val decodedPath = Uri.decode(videoPath)
    val videoName = decodedPath.substringAfterLast("/")

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
            prepare()
        }
    }

    LaunchedEffect(autoPlay) {
        player.playWhenReady = autoPlay
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0)
            if (!isDraggingSlider && totalDuration > 0) {
                sliderProgress = currentPosition.toFloat() / totalDuration
            }
            delay(200)
        }
    }

    LaunchedEffect(showCenterIcon) {
        if (showCenterIcon) {
            delay(800)
            showCenterIcon = false
        }
    }

    LaunchedEffect(showSeekIndicator) {
        if (showSeekIndicator.isNotEmpty()) {
            delay(800)
            showSeekIndicator = ""
        }
    }

    LaunchedEffect(isFullScreen, showOverlay, overlayTimerKey) {
        if (isFullScreen && showOverlay) {
            delay(3000)
            if (!isDraggingSlider && !isDraggingTimeline) {
                showOverlay = false
            }
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            player.setPlaybackSpeed(1f)
            player.release()
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Scaffold(
        containerColor = if (isFullScreen) Color.Black else MaterialTheme.colorScheme.background,
        topBar = {
            if (!isFullScreen) {
                CompactTopAppBar(
                    title = videoName,
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = {
                                Log.d("RXPlayer", "TopBar analyze clicked")
                                viewModel.triggerAnalysis()
                            },
                            enabled = !isAnalyzing
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "视频分析"
                            )
                        }
                        IconButton(
                            onClick = { privacyMaskEnabled = !privacyMaskEnabled }
                        ) {
                            Icon(
                                imageVector = if (privacyMaskEnabled) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                contentDescription = if (privacyMaskEnabled) "关闭隐私遮罩"
                                    else "开启隐私遮罩"
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .background(if (isFullScreen) Color.Black else MaterialTheme.colorScheme.background)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .gestureHandler(
                            player = player,
                            isFullScreen = isFullScreen,
                            showOverlay = showOverlay,
                            onDoubleTap = { xFraction ->
                                if (xFraction < 0.25f) {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                    showSeekIndicator = "-10s"
                                } else if (xFraction > 0.75f) {
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(totalDuration))
                                    showSeekIndicator = "+10s"
                                } else {
                                    player.playWhenReady = !player.playWhenReady
                                    showCenterIcon = true
                                }
                            },
                            onSingleTap = { showOverlay = !showOverlay },
                            onSpeedChange = { speed -> playbackSpeed = speed }
                        )
                )

                if (privacyMaskEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "隐私遮罩",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
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

                if (showSeekIndicator.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = if (showSeekIndicator.startsWith("+")) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Text(
                            text = showSeekIndicator,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
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
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "退出全屏",
                            tint = Color.White
                        )
                    }
                }

                if (isFullScreen && showOverlay) {
                    Slider(
                        value = sliderProgress,
                        onValueChange = { ratio ->
                            isDraggingSlider = true
                            sliderProgress = ratio
                            player.seekTo((ratio * totalDuration).toLong())
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            overlayTimerKey++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 64.dp)
                            .align(Alignment.BottomCenter),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.8f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    )
                }

                if (isFullScreen && showOverlay && scenes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .pointerInput(showOverlay) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val downEvent = awaitPointerEvent()
                                        val anyDown = downEvent.changes.any { it.pressed }
                                        if (anyDown) {
                                            isDraggingTimeline = true
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val stillDown = event.changes.any { it.pressed }
                                                if (!stillDown) {
                                                    isDraggingTimeline = false
                                                    overlayTimerKey++
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        TimelinePreviewBar(
                            scenes = scenes,
                            currentPosition = currentPosition,
                            lazyListState = timelineLazyListState,
                            onSceneClick = { timestampMs ->
                                overlayTimerKey++
                                player.seekTo(timestampMs)
                            }
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
                        value = sliderProgress,
                        onValueChange = { ratio ->
                            isDraggingSlider = true
                            sliderProgress = ratio
                            player.seekTo((ratio * totalDuration).toLong())
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
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

                analyzingProgress?.let { progress ->
                    SceneAnalysisProgress(progress = progress)
                }

                if (scenes.isEmpty() && analyzingProgress == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                Log.d("RXPlayer", "Analyze button clicked")
                                viewModel.triggerAnalysis()
                            },
                            enabled = !isAnalyzing
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "分析场景",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (isAnalyzing) "分析中..." else "分析镜头切换",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(enabled = !isAnalyzing) {
                                    Log.d("RXPlayer", "Analyze text clicked")
                                    viewModel.triggerAnalysis()
                                }
                                .align(Alignment.CenterVertically)
                        )
                    }
                }

                SceneGrid(
                    scenes = scenes,
                    currentPosition = currentPosition,
                    onSceneClick = { timestampMs -> player.seekTo(timestampMs) }
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

private fun Modifier.gestureHandler(
    player: ExoPlayer,
    isFullScreen: Boolean,
    showOverlay: Boolean,
    onDoubleTap: (xFraction: Float) -> Unit,
    onSingleTap: () -> Unit,
    onSpeedChange: (Float) -> Unit
): Modifier = this.then(
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val width = size.width.toFloat()
            val xFraction = down.position.x / width
            val longPressMs = viewConfiguration.longPressTimeoutMillis

            val up = withTimeoutOrNull(longPressMs) {
                waitForUpOrCancellation()
            }

            if (up != null) {
                val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown()
                }
                if (secondDown != null) {
                    val xFraction2 = secondDown.position.x / width
                    waitForUpOrCancellation()
                    onDoubleTap(xFraction2)
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
