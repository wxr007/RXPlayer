package com.rxplayer.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.filled.Info
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rxplayer.app.R
import com.rxplayer.app.media.ThumbnailCache
import com.rxplayer.app.ui.components.CompactTopAppBar
import com.rxplayer.app.ui.components.SceneAnalysisProgress
import com.rxplayer.app.ui.components.SceneGrid
import com.rxplayer.app.ui.components.TimelinePreviewBar
import com.rxplayer.app.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoPath: String,
    autoFullscreen: Boolean = false,
    playbackMode: Int = 0,
    folderPath: String = "",
    playlistId: Long = 0,
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
    var showVideoInfo by remember { mutableStateOf(false) }
    var showAnalysisDialog by remember { mutableStateOf(false) }
    var videoResolution by remember { mutableStateOf("") }
    var videoCodec by remember { mutableStateOf("") }
    var videoFrameRate by remember { mutableStateOf("") }
    var videoDecoderType by remember { mutableStateOf("") }
    var videoDecoderName by remember { mutableStateOf("") }

    val decodedPath = Uri.decode(videoPath)
    val videoName = decodedPath.substringAfterLast("/")

    val player = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory).build()
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && playerError != null) {
                    playerError = null
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val msg = error.localizedMessage ?: error.errorCodeName
                Log.e("RXPlayer", "Playback error: $msg", error)
                playerError = "播放失败: $msg"
            }
        })
    }

    DisposableEffect(player) {
        val analyticsListener = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializationDurationMs: Long
            ) {
                videoDecoderType = getDecoderType(decoderName)
                videoDecoderName = decoderName
            }
        }
        player.addAnalyticsListener(analyticsListener)
        onDispose { player.removeAnalyticsListener(analyticsListener) }
    }

    val folderVideos by viewModel.folderVideos.collectAsState()

    LaunchedEffect(Unit) {
        // Immediately prepare single video so user sees content right away
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoPath)))
        player.repeatMode = when (playbackMode) {
            1 -> ExoPlayer.REPEAT_MODE_ONE
            3 -> ExoPlayer.REPEAT_MODE_ALL
            else -> ExoPlayer.REPEAT_MODE_OFF
        }
        player.prepare()

        // For playlist modes, build full folder playlist by inserting items
        // around the already-playing video. addMediaItems avoids timeline
        // rebuild that causes brief black screen on setMediaItems().
        if (playbackMode >= 2) {
            val list = snapshotFlow { folderVideos }.firstOrNull { it.isNotEmpty() } ?: return@LaunchedEffect
            val mediaItems = list.map { MediaItem.fromUri(Uri.parse(it.filePath)) }
            val startIndex = list.indexOfFirst { it.filePath == videoPath }.coerceAtLeast(0)
            val before = mediaItems.subList(0, startIndex)
            val after = mediaItems.subList(startIndex + 1, mediaItems.size)
            if (before.isNotEmpty()) {
                player.addMediaItems(0, before)
            }
            if (after.isNotEmpty()) {
                player.addMediaItems(player.mediaItemCount, after)
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

    if (showAnalysisDialog) {
        AnalysisSettingsDialog(
            currentMode = viewModel.analysisMode.value,
            currentInterval = viewModel.analysisInterval.collectAsState().value,
            onDismiss = { showAnalysisDialog = false },
            onConfirm = { mode, interval ->
                showAnalysisDialog = false
                viewModel.analyzeWithMode(mode, interval)
            },
            onClearThumbnails = { viewModel.clearAnalysis() }
        )
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val scenes by viewModel.scenes.collectAsState()
    val analyzingProgress by viewModel.analyzingProgress.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val autoPlay by viewModel.autoPlay.collectAsState()
    val seekStep by viewModel.seekStep.collectAsState()

    LaunchedEffect(autoPlay) {
        player.playWhenReady = autoPlay
    }

    LaunchedEffect(autoFullscreen) {
        if (autoFullscreen) {
            toggleFullScreen()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0)
            if (!isDraggingSlider && totalDuration > 0) {
                sliderProgress = currentPosition.toFloat() / totalDuration
            }
            val fmt = player.videoFormat
            if (fmt != null) {
                videoResolution = "${fmt.width}×${fmt.height}"
                val mime = fmt.sampleMimeType ?: ""
                videoCodec = when {
                    mime.contains("avc") || mime.contains("h264") -> "H.264"
                    mime.contains("hevc") || mime.contains("h265") -> "H.265"
                    mime.contains("vp9") -> "VP9"
                    mime.contains("vp8") -> "VP8"
                    mime.contains("av1") -> "AV1"
                    mime.contains("mpeg2") -> "MPEG-2"
                    mime.contains("mpeg4") || mime.contains("mp4v") -> "MPEG-4"
                    mime.isNotEmpty() -> mime.substringAfterLast("/")
                    else -> ""
                }
                videoFrameRate = if (fmt.frameRate > 0f) "${"%.2f".format(fmt.frameRate)}fps" else ""
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
                            onClick = { showAnalysisDialog = true },
                            enabled = !isAnalyzing
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "视频分析"
                            )
                        }
                        IconButton(
                            onClick = {
                                val cache = ThumbnailCache(context)
                                scope.launch {
                                    val ok = cache.saveFrameAt(videoPath, currentPosition)
                                    snackbarHostState.showSnackbar(
                                        if (ok) "已替换封面" else "截图失败"
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera_alt),
                                contentDescription = "截图替换封面"
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
                        IconButton(
                            onClick = { showVideoInfo = !showVideoInfo }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "视频信息"
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
                                val stepMs = seekStep * 1000L
                                if (xFraction < 0.2f) {
                                    player.seekTo((player.currentPosition - stepMs).coerceAtLeast(0))
                                    showSeekIndicator = "-${seekStep}s"
                                } else if (xFraction > 0.8f) {
                                    player.seekTo((player.currentPosition + stepMs).coerceAtMost(totalDuration))
                                    showSeekIndicator = "+${seekStep}s"
                                } else {
                                    player.playWhenReady = !player.playWhenReady
                                    showCenterIcon = true
                                }
                            },
                            onSingleTap = { showOverlay = !showOverlay },
                            onSpeedChange = { speed -> playbackSpeed = speed }
                        )
                )

                if (playerError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = playerError!!,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }

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

                if (showVideoInfo && videoResolution.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = if (playbackSpeed > 1f) 40.dp else 8.dp, start = 8.dp, end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "视频信息",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "分辨率: $videoResolution",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = "编码: $videoCodec",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "帧率: $videoFrameRate",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "解码: $videoDecoderType",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            if (videoDecoderName.isNotEmpty()) {
                                Text(
                                    text = videoDecoderName,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (isFullScreen && showOverlay) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        IconButton(onClick = { showVideoInfo = !showVideoInfo }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "视频信息",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = toggleFullScreen) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "退出全屏",
                                tint = Color.White
                            )
                        }
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
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        thumb = {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                drawCircle(color = Color.White)
                            }
                        },
                        track = { sliderState ->
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            ) {
                                val trackHeight = 10.dp.toPx()
                                val trackCornerRadius = trackHeight / 2
                                val thumbRadius = 10.dp.toPx()
                                val fullTrackWidth = size.width + 2 * thumbRadius
                                val activeWidth = (sliderState.value * fullTrackWidth).coerceIn(0f, fullTrackWidth)

                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.2f),
                                    topLeft = androidx.compose.ui.geometry.Offset(-thumbRadius, 0f),
                                    size = androidx.compose.ui.geometry.Size(width = fullTrackWidth, height = trackHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackCornerRadius)
                                )

                                if (activeWidth > 0) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.8f),
                                        topLeft = androidx.compose.ui.geometry.Offset(-thumbRadius, 0f),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = activeWidth,
                                            height = trackHeight
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackCornerRadius)
                                    )
                                }
                            }
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        thumb = {
                            Canvas(modifier = Modifier.size(28.dp)) {
                                drawCircle(color = Color(0xFF5D4037))
                            }
                        },
                        track = { sliderState ->
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            ) {
                                val trackHeight = 10.dp.toPx()
                                val trackCornerRadius = trackHeight / 2
                                val thumbRadius = 14.dp.toPx()
                                val fullTrackWidth = size.width + 2 * thumbRadius
                                val activeWidth = (sliderState.value * fullTrackWidth).coerceIn(0f, fullTrackWidth)

                                drawRoundRect(
                                    color = Color(0xFFD7CCC8),
                                    topLeft = androidx.compose.ui.geometry.Offset(-thumbRadius, 0f),
                                    size = androidx.compose.ui.geometry.Size(width = fullTrackWidth, height = trackHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackCornerRadius)
                                )

                                if (activeWidth > 0) {
                                    drawRoundRect(
                                        color = Color(0xFF5D4037),
                                        topLeft = androidx.compose.ui.geometry.Offset(-thumbRadius, 0f),
                                        size = androidx.compose.ui.geometry.Size(
                                            width = activeWidth,
                                            height = trackHeight
                                        ),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackCornerRadius)
                                    )
                                }
                            }
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

@Composable
internal fun AnalysisSettingsDialog(
    currentMode: String,
    currentInterval: Int,
    onDismiss: () -> Unit,
    onConfirm: (mode: String, interval: Int) -> Unit,
    onClearThumbnails: (() -> Unit)? = null
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedInterval by remember { mutableStateOf(currentInterval.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分析设置") },
        text = {
            Column {
                Text("分析模式", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedMode == "smart",
                            onClick = { selectedMode = "smart" }
                        )
                        Text("智能检测", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(
                            selected = selectedMode == "interval",
                            onClick = { selectedMode = "interval" }
                        )
                        Text("间隔截取", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (selectedMode == "interval") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedInterval.toIntOrNull()?.coerceIn(5, 60) ?: 30}秒",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Slider(
                            value = (selectedInterval.toIntOrNull()?.coerceIn(5, 60) ?: 30).toFloat(),
                            onValueChange = { selectedInterval = it.toInt().toString() },
                            valueRange = 5f..60f,
                            steps = 54,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClearThumbnails != null) {
                    TextButton(
                        onClick = {
                            onClearThumbnails()
                            onDismiss()
                        }
                    ) {
                        Text("清除分析结果", color = MaterialTheme.colorScheme.error)
                    }
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(onClick = {
                        val interval = selectedInterval.toIntOrNull()?.coerceIn(5, 60) ?: 30
                        onConfirm(selectedMode, interval)
                    }) {
                        Text("开始分析")
                    }
                }
            }
        }
    )
}

private fun getDecoderType(decoderName: String): String {
    return if (Build.VERSION.SDK_INT >= 29) {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        val info = codecInfos.find { it.name == decoderName }
        if (info?.isSoftwareOnly == true) "软解码" else "硬件解码"
    } else {
        if (decoderName.startsWith("OMX.google.", ignoreCase = true)) "软解码" else "硬件解码"
    }
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
