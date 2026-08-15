package cn.happyoyster.opensdk.demo.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.happyoyster.opensdk.AdventureCommand
import cn.happyoyster.opensdk.HappyOysterListener
import cn.happyoyster.opensdk.SDKError
import cn.happyoyster.opensdk.TravelStatusValue
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.config.SdkDemoConfig
import cn.happyoyster.opensdk.demo.config.SdkDemoConfigStore
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import cn.happyoyster.opensdk.demo.gateway.DemoGatewayClient
import cn.happyoyster.opensdk.demo.gateway.DemoGatewayException
import cn.happyoyster.opensdk.demo.gateway.DemoTravel
import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.gateway.ScriptListPayload
import cn.happyoyster.opensdk.demo.gateway.StoryCreationModel
import cn.happyoyster.opensdk.demo.gateway.TravelArtifacts
import cn.happyoyster.opensdk.demo.gateway.WorldKind
import cn.happyoyster.opensdk.demo.gateway.mergeFrom
import cn.happyoyster.opensdk.demo.gateway.withFirstFrame
import cn.happyoyster.opensdk.demo.sdk.DemoSdkSession
import cn.happyoyster.opensdk.demo.sdk.isTravelBusyError
import cn.happyoyster.opensdk.demo.ui.isApiHost
import cn.happyoyster.opensdk.demo.ui.isHttpUrl
import cn.happyoyster.opensdk.demo.ui.sdkDemoMessage
import cn.happyoyster.opensdk.demo.ui.replaceWorld
import cn.happyoyster.opensdk.demo.ui.withSdkDemoLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun Int.isFatalTravelError(): Boolean =
    this == 105001 || // RTC connection failed.
        this == 105002 || // RTC join timed out.
        this == 105003 || // First video frame timed out.
        this == 105006 || // No-stream auto end.
        this == 108001 // Remote feature gate disabled the SDK.

private const val DOWNLOAD_STATUS_POLL_INTERVAL_MS = 1_000L

/**
 * State holder for the demo app: owns all UI state and every SDK/Gateway
 * action, so the composables only render and forward events.
 */
internal class SdkDemoAppState(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val store: SdkDemoConfigStore,
    private val sdkSession: DemoSdkSession,
) {
    var config by mutableStateOf(store.load())
        private set
    var selectedTab by mutableStateOf(DemoTab.Profile)
        private set
    var error by mutableStateOf<SdkDemoError?>(null)
        private set

    var worlds by mutableStateOf<List<DemoWorld>>(emptyList())
        private set
    var refreshingWorlds by mutableStateOf(false)
        private set
    var coverLoadingWorldIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var coverRefreshKey by mutableStateOf(0)
        private set

    var travels by mutableStateOf<List<DemoTravel>>(emptyList())
        private set
    var refreshingTravels by mutableStateOf(false)
        private set
    var artifactsByTravelId by mutableStateOf<Map<String, TravelArtifacts>>(emptyMap())
        private set
    var artifactLoadingTravelIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var artifactPendingTravelIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var artifactRefreshKey by mutableStateOf(0)
        private set
    var worldDetailsById by mutableStateOf<Map<String, DemoWorld>>(emptyMap())
        private set

    var activeTravel by mutableStateOf<ActiveTravel?>(null)
        private set
    var travelStatus by mutableStateOf<TravelStatusValue?>(null)
        private set
    var pausing by mutableStateOf(false)
        private set
    var endingTravel by mutableStateOf(false)
        private set

    var createForm by mutableStateOf(CreateWorldForm())
    var directingInstruct by mutableStateOf("Add a golden bridge and move closer.")
    var rewindToSec by mutableStateOf("8")
    var updateScriptPresetId by mutableStateOf<String?>(null)
    var updateScriptDraft by mutableStateOf("")
    var updatingScript by mutableStateOf(false)
        private set

    /** Bundled 45-turn ScriptList presets; empty when the asset fails to load. */
    val scriptListPresets: List<ScriptListPreset> by lazy {
        runCatching { ScriptListPresets.load(appContext) }
            .onFailure {
                SdkDemoLog.add(SdkDemoLogKind.ERROR, "scriptlist presets load", it.sdkDemoLogString())
            }
            .getOrDefault(emptyList())
    }

    private var startingTravel = false
    private var coverRequestedWorldIds by mutableStateOf<Set<String>>(emptySet())
    private var artifactRequestedTravelIds by mutableStateOf<Set<String>>(emptySet())
    private val downloadIds = mutableSetOf<Long>()
    private val coverRequestLimiter = Semaphore(permits = 2)
    private var sdkListenerRegistered = false

    private var cachedLocalizedContext: Pair<SdkDemoLanguage, Context>? = null
    private val localizedContext: Context
        get() {
            val language = config.language
            cachedLocalizedContext
                ?.let { (cachedLanguage, context) -> if (cachedLanguage == language) return context }
            return appContext.withSdkDemoLanguage(language)
                .also { cachedLocalizedContext = language to it }
        }

    private var cachedGatewayClient: Pair<String, DemoGatewayClient>? = null
    private val gatewayClient: DemoGatewayClient?
        get() {
            val url = config.gatewayBaseUrl.takeIf { it.isHttpUrl() } ?: return null
            cachedGatewayClient
                ?.let { (cachedUrl, client) -> if (cachedUrl == url) return client }
            return DemoGatewayClient(url).also { cachedGatewayClient = url to it }
        }

    private val listener = object : HappyOysterListener {
        override fun onStatusChanged(status: TravelStatusValue) {
            travelStatus = status
            SdkDemoLog.add(SdkDemoLogKind.SDK_EVENT, "onStatusChanged", status.rawValue)
            if (status != TravelStatusValue.Running) {
                pausing = false
            }
            if (status == TravelStatusValue.Completed || status == TravelStatusValue.Failed) {
                val hadActiveTravel = activeTravel != null
                leaveTravelScreen()
                if (hadActiveTravel) {
                    toast(
                        if (status == TravelStatusValue.Completed) {
                            R.string.travel_auto_exited_completed
                        } else {
                            R.string.travel_auto_exited_failed
                        },
                        long = true,
                    )
                }
                refreshTravels(clearError = false)
            }
        }

        override fun onError(sdkError: SDKError) {
            val shouldLeaveTravel = activeTravel != null && sdkError.code.isFatalTravelError()
            error = SdkDemoError.Sdk(
                code = sdkError.code,
                detail = sdkError.sdkDemoMessage(localizedContext),
            )
            pausing = false
            if (shouldLeaveTravel) {
                leaveTravelScreen()
                toast(R.string.travel_auto_exited_failed, long = true)
                refreshTravels(clearError = false)
            }
            SdkDemoLog.add(SdkDemoLogKind.ERROR, "onError", sdkError.sdkDemoLogString())
        }
    }

    fun initializeSdk() {
        if (!config.sdkApiHost.isApiHost()) return
        val label = "HappyOyster.initialize"
        SdkDemoLog.add(SdkDemoLogKind.SDK_CALL, label, config.sdkApiHost)
        val result = runCatching {
            sdkSession.initialize(config.sdkApiHost, config.token)
        }
        result.exceptionOrNull()?.let { cause ->
            if (cause.isTravelBusyError()) {
                sdkSession.addListener(listener)
                sdkListenerRegistered = true
                sdkSession.updateToken(config.token)
                SdkDemoLog.add(
                    SdkDemoLogKind.SDK_RESULT,
                    label,
                    "travel already active; callbacks restored",
                )
                return
            }
            report(R.string.action_initialize_sdk, cause)
            return
        }
        SdkDemoLog.add(SdkDemoLogKind.SDK_RESULT, label, "ok")
        sdkSession.addListener(listener)
        sdkListenerRegistered = true
    }

    fun disposeSdkListener() {
        if (!sdkListenerRegistered) return
        sdkSession.removeListener(listener)
        sdkListenerRegistered = false
    }

    fun updateSdkToken() {
        if (!sdkListenerRegistered) return
        sdkSession.updateToken(config.token)
    }

    fun applyEndpointConfiguration(gatewayBaseUrl: String, sdkApiHost: String) {
        persist(config.copy(gatewayBaseUrl = gatewayBaseUrl, sdkApiHost = sdkApiHost))
        if (!config.gatewayBaseUrl.isHttpUrl() || !config.sdkApiHost.isApiHost()) {
            worlds = emptyList()
            travels = emptyList()
            refreshingTravels = false
            artifactsByTravelId = emptyMap()
            artifactLoadingTravelIds = emptySet()
            artifactPendingTravelIds = emptySet()
            artifactRequestedTravelIds = emptySet()
            worldDetailsById = emptyMap()
            coverRequestedWorldIds = emptySet()
            coverLoadingWorldIds = emptySet()
            error = null
            return
        }
        error = null
        scope.launch {
            runCatching { refreshTokenNow(refreshWorldsAfter = false) }
                .rethrowCancellation()
                .onSuccess { toast(R.string.config_applied_success) }
                .onFailure { cause ->
                    error = SdkDemoError.Action(
                        actionResId = R.string.apply_configuration,
                        detail = localizedContext.getString(R.string.gateway_connection_failed),
                    )
                    SdkDemoLog.add(
                        SdkDemoLogKind.ERROR,
                        localizedContext.getString(R.string.apply_configuration),
                        cause.sdkDemoLogString(),
                    )
                }
        }
    }

    fun selectTab(tab: DemoTab) {
        selectedTab = tab
        if (!config.gatewayBaseUrl.isHttpUrl() || !config.sdkApiHost.isApiHost()) {
            error = null
            return
        }
        if (tab == DemoTab.Play) refreshWorlds()
        if (tab == DemoTab.History) refreshTravels()
    }

    fun persist(next: SdkDemoConfig) {
        config = next
        store.save(next)
    }

    fun clearToken() {
        store.clearToken()
        persist(config.copy(token = "", tokenExpiresAtSec = 0L))
    }

    fun refreshWorlds(showResultToast: Boolean = false) {
        if (refreshingWorlds) return
        scope.launch {
            refreshingWorlds = true
            error = null
            coverRequestedWorldIds = emptySet()
            SdkDemoLog.gatewayCall(
                label = "GET /server-api/worlds",
                render = { "count=${it.size}" },
            ) { requireGatewayClient().listWorlds() }
                .onSuccess { incoming ->
                    worlds = incoming.map { world ->
                        worlds.firstOrNull { cached -> cached.encryptedWorldId == world.encryptedWorldId }
                            ?.mergeFrom(world)
                            ?: world
                    }
                    coverRefreshKey += 1
                    if (showResultToast) toast(R.string.refresh_worlds_success)
                }
                .onFailure {
                    report(R.string.action_load_worlds, it)
                    if (showResultToast) toast(R.string.refresh_worlds_failed, long = true)
                }
            refreshingWorlds = false
        }
    }

    fun loadWorldCover(world: DemoWorld) {
        val worldId = world.encryptedWorldId
        if (world.imageUrl != null || worldId in coverRequestedWorldIds || worldId in coverLoadingWorldIds) return
        coverRequestedWorldIds = coverRequestedWorldIds + worldId
        coverLoadingWorldIds = coverLoadingWorldIds + worldId
        scope.launch {
            coverRequestLimiter.withPermit {
                SdkDemoLog.gatewayCall(
                    label = "GET /server-api/worlds/build-status",
                    render = { status -> "world=${status.encryptedWorldId}, firstFrame=${status.firstFrame != null}" },
                ) { requireGatewayClient().getBuildStatus(worldId) }
                    .onSuccess { status ->
                        worlds = worlds.replaceWorld(status)
                    }
                    .onFailure {
                        coverRequestedWorldIds = coverRequestedWorldIds - worldId
                        SdkDemoLog.add(
                            SdkDemoLogKind.ERROR,
                            "cover status load",
                            "world=$worldId, ${it.sdkDemoLogString()}",
                        )
                    }
                    .also {
                        coverLoadingWorldIds = coverLoadingWorldIds - worldId
                    }
            }
        }
    }

    fun refreshToken() {
        scope.launch {
            error = null
            runCatching { refreshTokenNow(refreshWorldsAfter = true) }
                .rethrowCancellation()
                .onFailure { report(R.string.action_refresh_token, it) }
        }
    }

    fun refreshTravels(clearError: Boolean = true, showResultToast: Boolean = false) {
        if (refreshingTravels) return
        scope.launch {
            refreshingTravels = true
            if (clearError) {
                error = null
                artifactPendingTravelIds = emptySet()
                artifactRequestedTravelIds = emptySet()
            }
            SdkDemoLog.gatewayCall(
                label = "GET /server-api/travels",
                render = { "count=${it.size}" },
            ) { requireGatewayClient().listTravels() }
                .onSuccess { incoming ->
                    travels = incoming
                    artifactRefreshKey += 1
                    loadWorldDetailsForTravels(incoming)
                    if (showResultToast) toast(R.string.refresh_travels_success)
                }
                .onFailure {
                    report(R.string.action_load_travels, it)
                    if (showResultToast) toast(R.string.refresh_travels_failed, long = true)
                }
            refreshingTravels = false
        }
    }

    fun loadTravelArtifacts(travel: DemoTravel, showError: Boolean = false) {
        val travelId = travel.encryptedTravelId
        if (
            travelId in artifactRequestedTravelIds ||
            travelId in artifactLoadingTravelIds ||
            artifactsByTravelId[travelId]?.video?.original?.url != null
        ) {
            return
        }
        artifactRequestedTravelIds = artifactRequestedTravelIds + travelId
        scope.launch {
            artifactLoadingTravelIds = artifactLoadingTravelIds + travelId
            SdkDemoLog.gatewayCall(
                label = "GET /server-api/travels/artifacts",
                render = { "video=${it.video?.original?.url != null}" },
            ) { requireGatewayClient().getTravelArtifacts(travelId) }
                .onSuccess { artifacts ->
                    artifactsByTravelId = artifactsByTravelId + (travelId to artifacts)
                    artifactPendingTravelIds = if (artifacts.video?.original?.url.isNullOrBlank()) {
                        artifactPendingTravelIds + travelId
                    } else {
                        artifactPendingTravelIds - travelId
                    }
                }
                .onFailure {
                    if ((it as? DemoGatewayException)?.code == 404000) {
                        artifactPendingTravelIds = artifactPendingTravelIds + travelId
                        SdkDemoLog.add(
                            SdkDemoLogKind.INFO,
                            "travel artifact processing",
                            "travel=$travelId, ${it.sdkDemoLogString()}",
                        )
                    } else if (showError) {
                        report(R.string.action_load_artifacts, it)
                    } else {
                        SdkDemoLog.add(
                            SdkDemoLogKind.ERROR,
                            "travel artifact load",
                            "travel=$travelId, ${it.sdkDemoLogString()}",
                        )
                    }
                }
            artifactLoadingTravelIds = artifactLoadingTravelIds - travelId
        }
    }

    fun downloadVideo(url: String) {
        runCatching {
            val uri = Uri.parse(url)
            val fileName = uri.lastPathSegment
                ?.substringBefore('?')
                ?.takeIf { it.isNotBlank() }
                ?: "happy-oyster-travel.mp4"
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription(localizedContext.getString(R.string.download_artifact_video))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)
            downloadIds += downloadId
            scope.launch { watchDownloadCompletion(manager, downloadId) }
            toast(R.string.download_started)
        }.onFailure {
            report(R.string.action_download_artifact_video, it)
        }
    }

    private suspend fun watchDownloadCompletion(manager: DownloadManager, downloadId: Long) {
        while (downloadId in downloadIds) {
            when (manager.queryDownloadStatus(downloadId)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloadIds -= downloadId
                    toast(R.string.download_completed)
                    return
                }
                DownloadManager.STATUS_FAILED, null -> {
                    downloadIds -= downloadId
                    return
                }
            }
            delay(DOWNLOAD_STATUS_POLL_INTERVAL_MS)
        }
    }

    private fun DownloadManager.queryDownloadStatus(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            return if (statusIndex >= 0) cursor.getInt(statusIndex) else null
        }
        return null
    }

    fun createWorld() {
        val form = createForm
        val isScriptList = form.worldKind == WorldKind.Story &&
            form.storyCreationModel == StoryCreationModel.ScriptList
        val preset = form.scriptListPresetId
            ?.let { id -> scriptListPresets.firstOrNull { it.id == id } }
        scope.launch {
            error = null
            SdkDemoLog.gatewayCall(
                label = "POST /server-api/worlds",
                render = { "world=${it.encryptedWorldId}, status=${it.status}" },
            ) {
                requireGatewayClient().createWorld(
                    kind = form.worldKind,
                    wanderUploadMode = form.wanderUploadMode,
                    prompt = form.prompt,
                    firstFrameImageUrl = form.firstFrameImageUrl,
                    scenePrompt = form.scenePrompt,
                    sceneImageUrl = form.sceneImageUrl,
                    rolePrompt = form.rolePrompt,
                    roleImageUrl = form.roleImageUrl,
                    cameraView = form.cameraView,
                    resolution = form.resolution,
                    creationModel = if (isScriptList) StoryCreationModel.ScriptList else StoryCreationModel.Simple,
                    scriptList = if (isScriptList) {
                        val draft = ScriptListDrafts.parse(form.scriptListDraft)
                        draft.copy(
                            synopsis = form.scriptListSynopsis.trim(),
                            videoTitle = preset?.name ?: draft.videoTitle,
                        )
                    } else {
                        null
                    },
                )
            }.onSuccess {
                selectedTab = DemoTab.Play
                refreshWorlds()
            }.onFailure { report(R.string.action_create_world, it) }
        }
    }

    fun selectUpdateScriptPreset(preset: ScriptListPreset) {
        updateScriptPresetId = preset.id
        updateScriptDraft = ScriptListDrafts.encode(preset.scriptList)
    }

    fun updateScript() {
        val travel = activeTravel ?: return
        val draft = updateScriptDraft
        if (updatingScript) return
        updatingScript = true
        scope.launch {
            error = null
            SdkDemoLog.gatewayCall(
                label = "POST /server-api/travels/update-script",
                render = { "accepted=${it.accepted}, turnCount=${it.turnCount}" },
            ) {
                requireGatewayClient().updateScript(
                    encryptedTravelId = travel.data.encryptedTravelId,
                    // Full update replaces acts only; subjects and world-level
                    // fields stay as stored at creation time.
                    scriptList = ScriptListPayload(acts = ScriptListDrafts.parse(draft).acts),
                )
            }
                .onSuccess { toast(R.string.update_script_accepted) }
                .onFailure { report(R.string.action_update_script, it) }
            updatingScript = false
        }
    }

    fun deleteWorld(world: DemoWorld) {
        scope.launch {
            error = null
            SdkDemoLog.gatewayCall(
                label = "POST /server-api/worlds/delete",
                render = { "deleted" },
            ) { requireGatewayClient().deleteWorld(world.encryptedWorldId) }
                .onSuccess {
                    worlds = worlds.filterNot { it.encryptedWorldId == world.encryptedWorldId }
                }
                .onFailure { report(R.string.action_delete_world, it) }
        }
    }

    fun startTravel(world: DemoWorld) {
        if (startingTravel || activeTravel != null) return
        startingTravel = true
        scope.launch {
            error = null
            try {
                runCatching {
                    if (!hasFreshToken()) {
                        refreshTokenNow(refreshWorldsAfter = false)
                    }
                    val credential = SdkDemoLog.gatewayCall(
                        label = "POST /server-api/travel-credential",
                        render = { "world=${it.encryptedWorldId}, expiresIn=${it.expiresIn}" },
                    ) { requireGatewayClient().getTravelCredential(world.encryptedWorldId) }.getOrThrow()
                    val started = SdkDemoLog.sdkCall(
                        label = "HappyOyster.startTravel(ticket)",
                        render = { "travel=${it.encryptedTravelId}, mode=${it.mode.rawValue}, firstFrame=${!it.firstFrame.isNullOrBlank()}" },
                    ) { sdkSession.startTravel(credential.ticket) }.getOrThrow()
                    try {
                        val worldWithCover = world.withFirstFrame(started.firstFrame)
                        worlds = worlds.replaceWorld(worldWithCover)
                        ActiveTravel(
                            world = worldWithCover,
                            data = started,
                            videoView = sdkSession.attachVideo(),
                        )
                    } catch (postStartError: Throwable) {
                        SdkDemoLog.sdkCall(
                            label = "HappyOyster.endTravel() (rollback)",
                            render = { "status=${it.status.rawValue}" },
                        ) { sdkSession.endTravel() }
                        throw postStartError
                    }
                }.rethrowCancellation().onSuccess {
                    activeTravel = it
                    travelStatus = TravelStatusValue.Init
                    pausing = false
                    endingTravel = false
                }.onFailure(::reportStartTravelFailure)
            } finally {
                startingTravel = false
            }
        }
    }

    fun endTravel() {
        if (activeTravel == null || endingTravel) return
        error = null
        endingTravel = true
        scope.launch {
            SdkDemoLog.sdkCall(
                label = "HappyOyster.endTravel()",
                render = { "status=${it.status.rawValue}, duration=${it.durationSec}s" },
            ) { sdkSession.endTravel() }
                .onSuccess {
                    leaveTravelScreen()
                    refreshTravels()
                }
                .onFailure {
                    report(R.string.action_end_travel, it)
                    leaveTravelScreen()
                }
        }
    }

    fun sendCommand(command: AdventureCommand, logSuccess: Boolean): Boolean {
        if (logSuccess) {
            error = null
        }
        val label = "HappyOyster.sendCommand($command)"
        val result = if (logSuccess) {
            SdkDemoLog.sdkCallSync(
                label = label,
                render = { "ok" },
            ) { sdkSession.sendCommand(command) }
        } else {
            runCatching { sdkSession.sendCommand(command) }
                .onFailure {
                    SdkDemoLog.add(
                        SdkDemoLogKind.ERROR,
                        label,
                        it.sdkDemoLogString(),
                    )
                }
        }
        result.onFailure { setError(R.string.action_send_command, it) }
        return result.isSuccess
    }

    fun sendInstruct() {
        scope.launch {
            error = null
            SdkDemoLog.sdkCall(
                label = "HappyOyster.sendInstruct(...)",
                render = { "accepted=${it.accepted}" },
            ) { sdkSession.sendInstruct(directingInstruct.trim()) }
                .onFailure { report(R.string.action_send_instruct, it) }
        }
    }

    fun pauseTravel() {
        scope.launch {
            error = null
            pausing = true
            SdkDemoLog.sdkCall(
                label = "HappyOyster.pauseTravel()",
                render = { "status=${it.status.rawValue}" },
            ) { sdkSession.pauseTravel() }
                .onFailure {
                    pausing = false
                    report(R.string.action_pause_travel, it)
                }
        }
    }

    fun resumeTravel() {
        scope.launch {
            error = null
            SdkDemoLog.sdkCall(
                label = "HappyOyster.resumeTravel()",
                render = { "status=${it.status.rawValue}" },
            ) { sdkSession.resumeTravel() }
                .onFailure { report(R.string.action_resume_travel, it) }
        }
    }

    fun rewindTravel() {
        val seconds = rewindToSec.toDoubleOrNull() ?: 0.0
        scope.launch {
            error = null
            SdkDemoLog.sdkCall(
                label = "HappyOyster.rewindTravel($seconds)",
                render = { "status=${it.status.rawValue}, resumedAt=${it.resumedAtSec}" },
            ) { sdkSession.rewindTravel(seconds) }
                .onFailure { report(R.string.action_rewind_travel, it) }
        }
    }

    private fun leaveTravelScreen() {
        activeTravel = null
        travelStatus = null
        pausing = false
        endingTravel = false
        updatingScript = false
        updateScriptPresetId = null
        updateScriptDraft = ""
    }

    private suspend fun refreshTokenNow(refreshWorldsAfter: Boolean) {
        val token = SdkDemoLog.gatewayCall(
            label = "POST /server-api/temp-api-key",
            render = { "expiresAt=${it.expiresAtSec}" },
        ) { requireGatewayClient().mintTempApiKey() }.getOrThrow()
        val next = config.copy(token = token.token, tokenExpiresAtSec = token.expiresAtSec)
        persist(next)
        sdkSession.updateToken(next.token)
        if (refreshWorldsAfter) refreshWorlds()
    }

    private suspend fun loadWorldDetailsForTravels(incoming: List<DemoTravel>) {
        val worldIds = incoming
            .mapNotNull { it.encryptedWorldId }
            .distinct()
            .filterNot { it in worldDetailsById }
        if (worldIds.isEmpty()) return
        val details = supervisorScope {
            worldIds.map { worldId ->
                async {
                    SdkDemoLog.gatewayCall(
                        label = "GET /server-api/worlds/detail",
                        render = { "world=${it.encryptedWorldId}, status=${it.status}" },
                    ) { requireGatewayClient().getWorldDetail(worldId) }
                        .onFailure {
                            SdkDemoLog.add(
                                SdkDemoLogKind.ERROR,
                                "world detail load",
                                "world=$worldId, ${it.sdkDemoLogString()}",
                            )
                        }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        if (details.isEmpty()) return
        worldDetailsById = worldDetailsById + details.associateBy { it.encryptedWorldId }
        worlds = details.fold(worlds) { cached, world -> cached.replaceWorld(world) }
    }

    private fun hasFreshToken(): Boolean =
        config.token.isNotBlank() && config.tokenExpiresAtSec > (System.currentTimeMillis() / 1000L) + 30L

    private fun requireGatewayClient(): DemoGatewayClient =
        gatewayClient ?: throw IllegalArgumentException(localizedContext.getString(R.string.missing_gateway_base_url))

    private fun setError(@StringRes actionResId: Int, cause: Throwable) {
        val detail = if (cause is SDKError) {
            cause.sdkDemoMessage(localizedContext)
        } else {
            localizedContext.getString(R.string.error_detail_check_log)
        }
        error = SdkDemoError.Action(actionResId, detail)
    }

    private fun report(@StringRes actionResId: Int, cause: Throwable) {
        setError(actionResId, cause)
        SdkDemoLog.add(
            SdkDemoLogKind.ERROR,
            localizedContext.getString(actionResId),
            cause.sdkDemoLogString(),
        )
    }

    private fun reportStartTravelFailure(cause: Throwable) {
        if (cause.isTravelBusyError()) {
            error = SdkDemoError.Action(
                actionResId = R.string.action_start_travel,
                detail = localizedContext.getString(R.string.travel_already_active),
            )
            return
        }
        report(R.string.action_start_travel, cause)
    }

    private fun toast(@StringRes resId: Int, long: Boolean = false) {
        Toast.makeText(
            localizedContext,
            localizedContext.getString(resId),
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
}
