package cn.happyoyster.opensdk.demo.features.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.SdkDemoLog
import cn.happyoyster.opensdk.demo.app.SdkDemoLogKind
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.ui.ErrorBanner
import cn.happyoyster.opensdk.demo.ui.OpenApiFailureKind
import cn.happyoyster.opensdk.demo.ui.OpenApiFailureReason
import cn.happyoyster.opensdk.demo.ui.DemoButton
import cn.happyoyster.opensdk.demo.ui.DemoCard
import cn.happyoyster.opensdk.demo.ui.SdkDemoSuccessColor
import cn.happyoyster.opensdk.demo.ui.DemoListControls
import cn.happyoyster.opensdk.demo.ui.DemoModeFilter
import cn.happyoyster.opensdk.demo.ui.DemoOutlinedButton
import cn.happyoyster.opensdk.demo.ui.DemoPageHeader
import cn.happyoyster.opensdk.demo.ui.DemoTimeOrder
import cn.happyoyster.opensdk.demo.ui.StatusBadge
import cn.happyoyster.opensdk.demo.ui.filterAndSortDemoRecords
import cn.happyoyster.opensdk.demo.ui.readableSdkDemoDateTime
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayTab(
    errorMessage: String?,
    onDismissError: () -> Unit,
    worlds: List<DemoWorld>,
    modeFilter: DemoModeFilter,
    timeOrder: DemoTimeOrder,
    onModeFilterChange: (DemoModeFilter) -> Unit,
    onTimeOrderChange: (DemoTimeOrder) -> Unit,
    language: SdkDemoLanguage,
    coverRefreshKey: Int,
    loadingCoverWorldIds: Set<String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onStart: (DemoWorld) -> Unit,
    onDelete: (DemoWorld) -> Unit,
    onLoadCover: (DemoWorld) -> Unit,
) {
    val displayedRecords = remember(worlds, modeFilter, timeOrder) {
        worlds.filterAndSortDemoRecords(
            filter = modeFilter,
            order = timeOrder,
            mode = { it.mode },
            time = { it.createdAt },
        )
    }
    val listState = rememberLazyListState()
    val loadedCoverIds = remember { mutableStateMapOf<String, Boolean>() }
    val worldsById = remember(worlds) { worlds.associateBy { it.encryptedWorldId } }
    val visibleWorldIds by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
    }
    val visibleMissingCoverIds by remember(worldsById) {
        derivedStateOf {
            visibleWorldIds
                .filter { id -> worldsById[id]?.imageUrl.isNullOrBlank() }
                .take(MaxConcurrentCoverLoads)
                .toSet()
        }
    }
    val visibleUnloadedImageIds by remember(worldsById) {
        derivedStateOf {
            visibleWorldIds
                .filter { id -> !worldsById[id]?.imageUrl.isNullOrBlank() && loadedCoverIds[id] != true }
                .take(MaxConcurrentCoverLoads)
                .toSet()
        }
    }
    LaunchedEffect(refreshing, modeFilter, timeOrder) {
        if (!refreshing) {
            listState.scrollToItem(0)
        }
    }
    val pullRefreshState = rememberPullToRefreshState()
    val refreshContentOffsetPx = with(LocalDensity.current) { RefreshIndicatorOffset.toPx() }
    val contentTranslationY = if (refreshing) {
        refreshContentOffsetPx
    } else {
        refreshContentOffsetPx * pullRefreshState.distanceFraction.coerceIn(0f, 1f)
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DemoPageHeader(
            title = stringResource(R.string.play_title),
            description = stringResource(R.string.play_description),
        )
        ErrorBanner(errorMessage, onDismissError)
        DemoListControls(modeFilter, timeOrder, onModeFilterChange, onTimeOrderChange)
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = contentTranslationY },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (displayedRecords.isEmpty() && !refreshing) {
                    item { EmptyWorldsCard(filtered = modeFilter != DemoModeFilter.All) }
                }
                items(displayedRecords, key = { it.encryptedWorldId }) { world ->
                    WorldCard(
                        world = world,
                        language = language,
                        coverRefreshKey = coverRefreshKey,
                        loadingCover = world.encryptedWorldId in loadingCoverWorldIds,
                        shouldLoadCover = world.encryptedWorldId in visibleMissingCoverIds,
                        shouldLoadImage = loadedCoverIds[world.encryptedWorldId] == true ||
                            world.encryptedWorldId in visibleUnloadedImageIds,
                        onImageLoaded = { loadedCoverIds[world.encryptedWorldId] = true },
                        onStart = { onStart(world) },
                        onDelete = { onDelete(world) },
                        onLoadCover = { onLoadCover(world) },
                    )
                }
            }
        }
    }
}

private val RefreshIndicatorOffset = 104.dp
private const val CoverImageWidthPx = 720
private const val CoverImageHeightPx = 315
private const val MaxConcurrentCoverLoads = 2

@Composable
private fun EmptyWorldsCard(filtered: Boolean) {
    DemoCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(if (filtered) R.string.list_no_matches else R.string.no_worlds_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (filtered) R.string.list_try_another_mode else R.string.no_worlds_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorldCard(
    world: DemoWorld,
    language: SdkDemoLanguage,
    coverRefreshKey: Int,
    loadingCover: Boolean,
    shouldLoadCover: Boolean,
    shouldLoadImage: Boolean,
    onImageLoaded: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    onLoadCover: () -> Unit,
) {
    val imageUrl = world.imageUrl
    val context = LocalContext.current
    LaunchedEffect(world.encryptedWorldId, imageUrl, coverRefreshKey, shouldLoadCover) {
        if (shouldLoadCover && imageUrl.isNullOrBlank()) onLoadCover()
    }
    DemoCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    world.displayName ?: stringResource(R.string.world_generating_title),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                WorldStatusBadge(world.status)
            }
            val imageModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 7f)
                .clip(MaterialTheme.shapes.small)
            Box(
                modifier = imageModifier
                    .fillMaxWidth()
                    .background(Color(0xFFE9EEF5)),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl.isNullOrBlank()) {
                    if (loadingCover) {
                        CircularProgressIndicator()
                    } else if (world.isPreviewGenerating()) {
                        Text(stringResource(R.string.preview_generating))
                    } else {
                        Text(stringResource(R.string.preview_unavailable))
                    }
                } else if (shouldLoadImage) {
                    val imageRequest = remember(context, imageUrl, world.encryptedWorldId) {
                        val cacheKey = "sdk-demo-cover:${world.encryptedWorldId}:$imageUrl"
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .memoryCacheKey(cacheKey)
                            .diskCacheKey(cacheKey)
                            .size(CoverImageWidthPx, CoverImageHeightPx)
                            .scale(Scale.FILL)
                            .precision(Precision.INEXACT)
                            .listener(
                                onError = { _, result ->
                                    SdkDemoLog.add(
                                        SdkDemoLogKind.ERROR,
                                        "cover image load",
                                        "world=${world.encryptedWorldId}, ${result.throwable.message}",
                                    )
                                },
                            )
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = world.displayName ?: stringResource(R.string.world_generating_title),
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
                        onSuccess = { onImageLoaded() },
                        onError = { onImageLoaded() },
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            Text(
                "${world.localizedModeLabel()} · ${world.createdAt.readableSdkDemoDateTime(language)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (world.status.equals("failed", ignoreCase = true)) {
                OpenApiFailureReason(
                    kind = OpenApiFailureKind.World,
                    errorCode = world.errorCode,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoButton(onClick = onStart, enabled = world.isReady, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.start_travel))
                }
                DemoOutlinedButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun WorldStatusBadge(label: String) {
    val color = when {
        label.equals("ready", ignoreCase = true) -> SdkDemoSuccessColor
        label.equals("failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusBadge(label = label, color = color)
}

private fun DemoWorld.isPreviewGenerating(): Boolean =
    !status.equals("ready", ignoreCase = true) &&
        !status.equals("failed", ignoreCase = true)

@Composable
private fun DemoWorld.localizedModeLabel(): String =
    when (modeLabel) {
        "Story" -> stringResource(R.string.mode_story)
        "Wander" -> stringResource(R.string.mode_wander)
        "Acting" -> stringResource(R.string.mode_acting)
        else -> stringResource(R.string.mode_unknown)
    }
