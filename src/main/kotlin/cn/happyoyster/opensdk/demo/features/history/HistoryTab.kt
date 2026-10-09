package cn.happyoyster.opensdk.demo.features.history

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import cn.happyoyster.opensdk.demo.gateway.ArtifactMedia
import cn.happyoyster.opensdk.demo.gateway.DemoTravel
import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.gateway.TravelArtifacts
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryTab(
    travels: List<DemoTravel>,
    artifactsByTravelId: Map<String, TravelArtifacts>,
    artifactLoadingTravelIds: Set<String>,
    artifactPendingTravelIds: Set<String>,
    artifactFailedTravelIds: Set<String>,
    artifactRefreshKey: Int,
    worldDetailsById: Map<String, DemoWorld>,
    modeFilter: DemoModeFilter,
    timeOrder: DemoTimeOrder,
    onModeFilterChange: (DemoModeFilter) -> Unit,
    onTimeOrderChange: (DemoTimeOrder) -> Unit,
    language: SdkDemoLanguage,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadArtifacts: (DemoTravel) -> Unit,
    onDownloadVideo: (String) -> Unit,
) {
    val displayedRecords = remember(travels, modeFilter, timeOrder, worldDetailsById) {
        travels.filterAndSortDemoRecords(
            filter = modeFilter,
            order = timeOrder,
            mode = { it.mode ?: it.encryptedWorldId?.let(worldDetailsById::get)?.mode },
            time = { it.createdAt ?: it.endedAt },
        )
    }
    val listState = rememberLazyListState()
    LaunchedEffect(modeFilter, timeOrder) { listState.scrollToItem(0) }
    val pullRefreshState = rememberPullToRefreshState()
    val refreshContentOffsetPx = with(LocalDensity.current) { RefreshIndicatorOffset.toPx() }
    val contentTranslationY = if (refreshing) {
        refreshContentOffsetPx
    } else {
        refreshContentOffsetPx * pullRefreshState.distanceFraction.coerceIn(0f, 1f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DemoPageHeader(
            title = stringResource(R.string.history_title),
            description = stringResource(R.string.history_description),
        )
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (displayedRecords.isEmpty() && !refreshing) {
                    item {
                        DemoCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(if (modeFilter != DemoModeFilter.All) R.string.list_no_matches else R.string.no_travels_title), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(if (modeFilter != DemoModeFilter.All) R.string.list_try_another_mode else R.string.no_travels_description),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(displayedRecords, key = { it.encryptedTravelId }) { travel ->
                    val world = travel.encryptedWorldId?.let(worldDetailsById::get)
                    val travelName = world?.displayName ?: stringResource(R.string.travel_record_title)
                    val media = artifactsByTravelId[travel.encryptedTravelId]?.video?.original
                    val loadingArtifact = travel.encryptedTravelId in artifactLoadingTravelIds
                    val failedArtifact = travel.encryptedTravelId in artifactFailedTravelIds
                    val pendingArtifact = travel.encryptedTravelId in artifactPendingTravelIds
                    LaunchedEffect(
                        travel.encryptedTravelId,
                        travel.status,
                        media?.url,
                        artifactRefreshKey,
                    ) {
                        if (travel.status.equals("completed", ignoreCase = true) &&
                            media?.url.isNullOrBlank() &&
                            !loadingArtifact &&
                            !pendingArtifact && !failedArtifact
                        ) {
                            onLoadArtifacts(travel)
                        }
                    }
                    DemoCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    travelName,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TravelStatusBadge(travel.status.orEmpty())
                            }
                            TravelMetaLine(
                                durationSec = travel.durationSec,
                                timeText = (travel.createdAt ?: travel.endedAt).readableSdkDemoDateTime(language),
                            )
                            if (travel.status.equals("failed", ignoreCase = true)) {
                                OpenApiFailureReason(
                                    kind = OpenApiFailureKind.Travel,
                                    errorCode = travel.errorCode,
                                )
                            }
                            when {
                                !media?.url.isNullOrBlank() -> ArtifactVideoCard(
                                    media = media,
                                    onDownloadVideo = onDownloadVideo,
                                )
                                failedArtifact -> {
                                    Text(
                                        stringResource(R.string.artifact_video_load_failed),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    DemoOutlinedButton(onClick = { onLoadArtifacts(travel) }) {
                                        Text(stringResource(R.string.travel_retry))
                                    }
                                }
                                loadingArtifact -> ArtifactLoadingCard()
                                pendingArtifact -> ArtifactProcessingCard()
                                travel.status.equals("completed", ignoreCase = true) -> ArtifactLoadingCard()
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RefreshIndicatorOffset = 104.dp

@Composable
private fun TravelMetaLine(
    durationSec: Int?,
    timeText: String,
) {
    val durationText = durationSec?.let { stringResource(R.string.travel_duration_short, it) }
    val text = listOfNotNull(durationText, timeText.takeIf { it.isNotBlank() }).joinToString(" · ")
    if (text.isNotBlank()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TravelStatusBadge(label: String) {
    val normalized = label.ifBlank { stringResource(R.string.status_unknown) }
    val color = when {
        normalized.equals("completed", ignoreCase = true) -> SdkDemoSuccessColor
        normalized.equals("failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
        normalized.equals("running", ignoreCase = true) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusBadge(label = normalized, color = color)
}

@Composable
private fun ArtifactLoadingCard() {
    DemoCard(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ArtifactProcessingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.artifact_video_processing),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArtifactVideoCard(
    media: ArtifactMedia,
    onDownloadVideo: (String) -> Unit,
) {
    val url = media.url ?: return
    var playing by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArtifactVideoPlayer(player)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoOutlinedButton(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                        }
                        player.play()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (playing) {
                            R.string.pause_video
                        } else {
                            R.string.play_video
                        },
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            DemoButton(onClick = { onDownloadVideo(url) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.download_video_button), maxLines = 1, softWrap = false)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ArtifactVideoPlayer(player: ExoPlayer) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                controllerAutoShow = false
                controllerHideOnTouch = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        },
    )
}
