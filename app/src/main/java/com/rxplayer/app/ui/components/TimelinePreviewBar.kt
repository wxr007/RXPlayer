package com.rxplayer.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rxplayer.app.media.SceneData
import com.rxplayer.app.media.SceneDetector
import java.io.File

@Composable
fun TimelinePreviewBar(
    scenes: List<SceneData>,
    currentPosition: Long,
    onSceneClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    if (scenes.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "镜头切换",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(scenes, key = { it.sceneIndex }) { scene ->
                SceneThumbnail(
                    scene = scene,
                    isCurrent = isCurrentScene(scene, currentPosition, scenes),
                    onClick = { onSceneClick(scene.timestampMs) },
                    compact = true,
                    timestampColor = Color.White
                )
            }
        }
    }
}

@Composable
private fun SceneThumbnail(
    scene: SceneData,
    isCurrent: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    timestampColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current

    val imageRatio = remember(scene.thumbnailPath) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(scene.thumbnailPath, opts)
        if (opts.outWidth > 0 && opts.outHeight > 0)
            opts.outWidth.toFloat() / opts.outHeight.toFloat()
        else 16f / 9f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (compact) Modifier
                        .width((64.dp * imageRatio).coerceAtMost(120.dp))
                        .height(64.dp)
                    else Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageRatio)
                )
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (isCurrent) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)
                    ) else Modifier
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(scene.thumbnailPath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Scene at ${SceneDetector.formatTimestamp(scene.timestampMs)}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Text(
            text = SceneDetector.formatTimestamp(scene.timestampMs),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = timestampColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun SceneAnalysisProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "正在分析镜头切换... ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

private fun isCurrentScene(scene: SceneData, position: Long, allScenes: List<SceneData>): Boolean {
    val index = allScenes.indexOf(scene)
    if (index < 0) return false
    val nextTimestamp = if (index + 1 < allScenes.size) allScenes[index + 1].timestampMs else Long.MAX_VALUE
    return position in scene.timestampMs until nextTimestamp
}

@Composable
fun SceneGrid(
    scenes: List<SceneData>,
    currentPosition: Long,
    onSceneClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (scenes.isEmpty()) return

    val gridColumns = remember(scenes) {
        val firstPath = scenes.firstOrNull()?.thumbnailPath
        if (firstPath != null) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(firstPath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0 && opts.outHeight > opts.outWidth) 6 else 4
        } else 4
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().height(420.dp)
    ) {
        items(scenes, key = { it.sceneIndex }) { scene ->
            SceneThumbnail(
                scene = scene,
                isCurrent = isCurrentScene(scene, currentPosition, scenes),
                onClick = { onSceneClick(scene.timestampMs) }
            )
        }
    }
}
