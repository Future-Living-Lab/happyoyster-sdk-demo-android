package cn.happyoyster.opensdk.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import cn.happyoyster.opensdk.demo.app.SdkDemoAppState
import cn.happyoyster.opensdk.demo.app.SdkDemoError
import cn.happyoyster.opensdk.demo.app.DemoTab
import cn.happyoyster.opensdk.demo.config.SdkDemoConfigStore
import cn.happyoyster.opensdk.demo.features.create.CreateTab
import cn.happyoyster.opensdk.demo.features.history.HistoryTab
import cn.happyoyster.opensdk.demo.features.play.PlayTab
import cn.happyoyster.opensdk.demo.features.profile.ProfileTab
import cn.happyoyster.opensdk.demo.features.travel.TravelScreen
import cn.happyoyster.opensdk.demo.sdk.DemoSdkSession
import cn.happyoyster.opensdk.demo.ui.ErrorBanner
import cn.happyoyster.opensdk.demo.ui.SdkDemoTabBar
import cn.happyoyster.opensdk.demo.ui.withSdkDemoLanguage

class SdkDemoActivity : ComponentActivity() {
    private val requestRecordAudio =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        // Adventure controls usually work without RECORD_AUDIO. The Demo requests it only to
        // exercise the SDK's recommended compatibility path; no real audio is captured or uploaded.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                SdkDemoApp()
            }
        }
    }
}

@Composable
private fun SdkDemoError.message(): String =
    when (this) {
        is SdkDemoError.Action -> stringResource(
            R.string.status_error_message,
            stringResource(actionResId),
            detail,
        )
        is SdkDemoError.Sdk -> stringResource(R.string.status_sdk_error, code, detail)
    }

@Composable
private fun SdkDemoApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember(appContext) {
        SdkDemoAppState(
            appContext = appContext,
            scope = scope,
            store = SdkDemoConfigStore(appContext),
            sdkSession = DemoSdkSession(appContext),
        )
    }
    val localizedContext = remember(context, state.config.language) {
        context.withSdkDemoLanguage(state.config.language)
    }

    LaunchedEffect(state.config.gatewayBaseUrl) {
        state.onGatewayBaseUrlChanged()
    }
    DisposableEffect(state.config.sdkApiHost) {
        state.initializeSdk()
        onDispose { state.disposeSdkListener() }
    }
    LaunchedEffect(state.config.token) {
        state.updateSdkToken()
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val travel = state.activeTravel
            if (travel != null) {
                BackHandler(enabled = true, onBack = state::endTravel)
                TravelScreen(
                    errorMessage = state.error?.message(),
                    travel = travel,
                    status = state.travelStatus,
                    pausing = state.pausing,
                    ending = state.endingTravel,
                    directingInstruct = state.directingInstruct,
                    rewindToSec = state.rewindToSec,
                    scriptListPresets = state.scriptListPresets,
                    updateScriptPresetId = state.updateScriptPresetId,
                    updateScriptDraft = state.updateScriptDraft,
                    updatingScript = state.updatingScript,
                    onInstructChange = { state.directingInstruct = it },
                    onRewindChange = { state.rewindToSec = it },
                    onCommand = state::sendCommand,
                    onSendInstruct = state::sendInstruct,
                    onUpdatePresetSelect = state::selectUpdateScriptPreset,
                    onUpdateScriptDraftChange = { state.updateScriptDraft = it },
                    onUpdateScript = state::updateScript,
                    onPause = state::pauseTravel,
                    onResume = state::resumeTravel,
                    onRewind = state::rewindTravel,
                    onEnd = state::endTravel,
                )
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    bottomBar = {
                        SdkDemoTabBar(
                            selectedTab = state.selectedTab,
                            onSelect = state::selectTab,
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.selectedTab != DemoTab.Play) {
                            ErrorBanner(state.error?.message())
                        }
                        when (state.selectedTab) {
                            DemoTab.Create -> CreateTab(
                                form = state.createForm,
                                scriptListPresets = state.scriptListPresets,
                                onFormChange = { state.createForm = it },
                                onCreate = state::createWorld,
                            )
                            DemoTab.Play -> PlayTab(
                                errorMessage = state.error?.message(),
                                worlds = state.worlds,
                                language = state.config.language,
                                coverRefreshKey = state.coverRefreshKey,
                                loadingCoverWorldIds = state.coverLoadingWorldIds,
                                refreshing = state.refreshingWorlds,
                                onRefresh = { state.refreshWorlds(showResultToast = true) },
                                onStart = state::startTravel,
                                onDelete = state::deleteWorld,
                                onLoadCover = state::loadWorldCover,
                            )
                            DemoTab.History -> HistoryTab(
                                travels = state.travels,
                                artifactsByTravelId = state.artifactsByTravelId,
                                artifactLoadingTravelIds = state.artifactLoadingTravelIds,
                                artifactPendingTravelIds = state.artifactPendingTravelIds,
                                artifactRefreshKey = state.artifactRefreshKey,
                                worldDetailsById = state.worldDetailsById,
                                language = state.config.language,
                                refreshing = state.refreshingTravels,
                                onRefresh = { state.refreshTravels(showResultToast = true) },
                                onLoadArtifacts = { state.loadTravelArtifacts(it, showError = true) },
                                onDownloadVideo = state::downloadVideo,
                            )
                            DemoTab.Profile -> ProfileTab(
                                config = state.config,
                                onConfigChange = state::persist,
                            )
                        }
                    }
                }
            }
        }
    }
}
