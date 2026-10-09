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
import cn.happyoyster.opensdk.demo.gateway.toWorldKindOrNull
import cn.happyoyster.opensdk.demo.gateway.withFirstFrame
import cn.happyoyster.opensdk.demo.sdk.DemoSdkSession
import cn.happyoyster.opensdk.demo.sdk.isTravelBusyError
import cn.happyoyster.opensdk.demo.ui.isApiHost
import cn.happyoyster.opensdk.demo.ui.isHttpUrl
import cn.happyoyster.opensdk.demo.ui.sdkDemoMessage
import cn.happyoyster.opensdk.demo.ui.replaceWorld
import cn.happyoyster.opensdk.demo.ui.withSdkDemoLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
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
    var artifactFailedTravelIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var artifactRefreshKey by mutableStateOf(0)
        private set
    var worldDetailsById by mutableStateOf<Map<String, DemoWorld>>(emptyMap())
        private set

    var activeTravel by mutableStateOf<ActiveTravel?>(null)
        private set
    var travelStatus by mutableStateOf<TravelStatusValue?>(null)
        private set
    var travelTransition by mutableStateOf(DemoTravelTransition())
        private set
    val pausing: Boolean get() = travelTransition.operation == DemoTravelTransition.Operation.Pause
    val recovering: Boolean get() = travelTransition.operation in setOf(
        DemoTravelTransition.Operation.Resume, DemoTravelTransition.Operation.Rewind,
    )
    var creatingWorld by mutableStateOf(false)
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
    private var awaitingFailureDetail = false
    private val worldBuildJobs = mutableMapOf<String, Job>()
    private val retainedCreatedWorldIds = mutableSetOf<String>()
    private var gatewayEpoch = 0L
    private var coverRequestedWorldIds by mutableStateOf<Set<String>>(emptySet())
    private var artifactRequestedTravelIds by mutableStateOf<Set<String>>(emptySet())
    private val inaccessibleWorldDetailIds = mutableSetOf<String>()
    private val downloadIds = mutableSetOf<Long>()
    private val coverRequestLimiter = Semaphore(permits = 2)
    private val worldDetailRequestLimiter = Semaphore(permits = 4)
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
            travelTransition = travelTransition.confirmed(status)
            travelStatus = status
            SdkDemoLog.add(SdkDemoLogKind.SDK_EVENT, "onStatusChanged", status.rawValue)
            if (status == TravelStatusValue.Completed || status == TravelStatusValue.Failed) {
                val hadActiveTravel = activeTravel != null
                awaitingFailureDetail = hadActiveTravel && status == TravelStatusValue.Failed
                leaveTravelScreen()
                if (hadActiveTravel && status == TravelStatusValue.Completed) {
                    toast(R.string.travel_auto_exited_completed, long = true)
                }
                refreshTravels(clearError = false)
            }
        }

        override fun onError(sdkError: SDKError) {
            val shouldLeaveTravel = activeTravel != null && sdkError.code.isFatalTravelError()
            val detail = sdkError.sdkDemoMessage(localizedContext)
            error = SdkDemoError.Sdk(
                code = sdkError.code,
                detail = detail,
            )
            if (awaitingFailureDetail || shouldLeaveTravel) {
                awaitingFailureDetail = false
                toast(detail, long = true)
            }
            if (shouldLeaveTravel) {
                leaveTravelScreen()
                refreshTravels(clearError = false)
            }
            SdkDemoLog.add(SdkDemoLogKind.ERROR, "onError", sdkError.sdkDemoLogString())
        }
    }

    private fun initializeSdkForWorld(world: DemoWorld, model: String) {
        val label = "HappyOyster.initialize"
        SdkDemoLog.add(SdkDemoLogKind.SDK_CALL, label, "mode=${world.mode}, model=$model")
        awaitingFailureDetail = false
        sdkSession.initialize(config.sdkApiHost, model, config.token)
        // Re-initialization replaces the SDK runtime and clears its listeners.
        sdkListenerRegistered = false
        sdkSession.addListener(listener)
        sdkListenerRegistered = true
        SdkDemoLog.add(SdkDemoLogKind.SDK_RESULT, label, "ok")
    }

    fun disposeSdkListener() {
        worldBuildJobs.values.forEach { it.cancel() }
        worldBuildJobs.clear()
        if (!sdkListenerRegistered) return
        sdkSession.removeListener(listener)
        sdkListenerRegistered = false
    }

    fun updateSdkToken() {
        if (!sdkListenerRegistered) return
        sdkSession.updateToken(config.token)
    }

    fun applyEndpointConfiguration(
        gatewayBaseUrl: String,
        sdkApiHost: String,
    ) {
        val nextGatewayBaseUrl = gatewayBaseUrl.trim()
        val nextSdkApiHost = sdkApiHost.trim()
        val environmentChanged = nextGatewayBaseUrl != config.gatewayBaseUrl ||
            nextSdkApiHost != config.sdkApiHost
        persist(
            config.copy(
                gatewayBaseUrl = nextGatewayBaseUrl,
                sdkApiHost = nextSdkApiHost,
                token = if (environmentChanged) "" else config.token,
                tokenExpiresAtSec = if (environmentChanged) 0L else config.tokenExpiresAtSec,
            ),
        )
        if (environmentChanged || !config.gatewayBaseUrl.isHttpUrl() || !config.sdkApiHost.isApiHost()) {
            gatewayEpoch += 1
            worldBuildJobs.values.forEach { it.cancel() }
            worldBuildJobs.clear()
            retainedCreatedWorldIds.clear()
            worlds = emptyList()
            travels = emptyList()
            refreshingWorlds = false
            refreshingTravels = false
            artifactsByTravelId = emptyMap()
            artifactLoadingTravelIds = emptySet()
            artifactPendingTravelIds = emptySet()
            artifactFailedTravelIds = emptySet()
            artifactRequestedTravelIds = emptySet()
            worldDetailsById = emptyMap()
            coverRequestedWorldIds = emptySet()
            coverLoadingWorldIds = emptySet()
            inaccessibleWorldDetailIds.clear()
        }
        if (!config.gatewayBaseUrl.isHttpUrl() || !config.sdkApiHost.isApiHost()) {
            error = null
            return
        }
        error = null
        val epoch = gatewayEpoch
        scope.launch {
            runCatching { refreshTokenNow() }
                .rethrowCancellation()
                .onSuccess { toast(R.string.config_applied_success) }
                .onFailure { cause ->
                    if (gatewayEpoch != epoch) return@onFailure
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

    fun refreshWorlds(showResultToast: Boolean = false) {
        if (refreshingWorlds) return
        val epoch = gatewayEpoch
        refreshingWorlds = true
        scope.launch {
            error = null
            coverRequestedWorldIds = emptySet()
            SdkDemoLog.gatewayCall(
                label = "GET /server-api/worlds",
                render = { "count=${it.size}" },
            ) { requireGatewayClient().listWorlds() }
                .onSuccess { incoming ->
                    if (gatewayEpoch != epoch) return@onSuccess
                    worlds = mergeWorldListSnapshot(worlds, incoming, retainedCreatedWorldIds)
                    retainedCreatedWorldIds.removeAll(incoming.map { it.encryptedWorldId }.toSet())
                    worlds.filter { it.needsBuildTracking() }.forEach(::trackWorldBuild)
                    coverRefreshKey += 1
                    if (showResultToast) toast(R.string.refresh_worlds_success)
                }
                .onFailure {
                    if (gatewayEpoch != epoch) return@onFailure
                    report(R.string.action_load_worlds, it)
                    if (showResultToast) toast(R.string.refresh_worlds_failed, long = true)
                }
            if (gatewayEpoch == epoch) refreshingWorlds = false
        }
    }

    private fun trackWorldBuild(world: DemoWorld) {
        val worldId = world.encryptedWorldId
        if (!world.needsBuildTracking() || worldId in worldBuildJobs) return
        val mode = world.mode.toWorldKindOrNull() ?: return
        val client = gatewayClient ?: return
        val epoch = gatewayEpoch
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val completed = pollDemoWorldBuild(
                    query = {
                        coverRequestLimiter.withPermit { client.getBuildStatus(worldId, mode) }
                    },
                    onUpdate = { status ->
                        if (gatewayEpoch == epoch && worlds.any { it.encryptedWorldId == worldId }) {
                            worlds = worlds.replaceWorld(status)
                        }
                    },
                )
                if (!completed && gatewayEpoch == epoch) {
                    SdkDemoLog.add(SdkDemoLogKind.INFO, "world build", "still pending; refresh to check again")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                if (gatewayEpoch == epoch) report(R.string.action_load_worlds, cause)
            } finally {
                if (worldBuildJobs[worldId] === currentCoroutineContext()[Job]) worldBuildJobs.remove(worldId)
            }
        }
        worldBuildJobs[worldId] = job
        job.start()
    }

    fun loadWorldCover(world: DemoWorld) {
        if (world.needsBuildTracking()) {
            trackWorldBuild(world)
            return
        }
        val epoch = gatewayEpoch
        val client = gatewayClient ?: return
        val worldId = world.encryptedWorldId
        if (world.imageUrl != null || worldId in coverRequestedWorldIds || worldId in coverLoadingWorldIds) return
        coverRequestedWorldIds = coverRequestedWorldIds + worldId
        coverLoadingWorldIds = coverLoadingWorldIds + worldId
        scope.launch {
            coverRequestLimiter.withPermit {
                SdkDemoLog.gatewayCall(
                    label = "GET /server-api/worlds/build-status",
                    render = { status -> "world=${status.encryptedWorldId}, firstFrame=${status.firstFrame != null}" },
                ) {
                    val mode = requireNotNull(world.mode.toWorldKindOrNull()) { "Unsupported world mode: ${world.mode}" }
                    client.getBuildStatus(worldId, mode)
                }
                    .onSuccess { status ->
                        if (gatewayEpoch != epoch) return@onSuccess
                        worlds = worlds.replaceWorld(status)
                    }
                    .onFailure {
                        if (gatewayEpoch != epoch) return@onFailure
                        coverRequestedWorldIds = coverRequestedWorldIds - worldId
                        SdkDemoLog.add(
                            SdkDemoLogKind.ERROR,
                            "cover status load",
                            "world=$worldId, ${it.sdkDemoLogString()}",
                        )
                    }
                    .also {
                        if (gatewayEpoch == epoch) coverLoadingWorldIds = coverLoadingWorldIds - worldId
                    }
            }
        }
    }

    fun refreshTravels(clearError: Boolean = true, showResultToast: Boolean = false) {
        if (refreshingTravels) return
        val epoch = gatewayEpoch
        refreshingTravels = true
        scope.launch {
            try {
                if (clearError) {
                    error = null
                    artifactPendingTravelIds = emptySet()
                    artifactFailedTravelIds = emptySet()
                    artifactRequestedTravelIds = emptySet()
                }
                SdkDemoLog.gatewayCall(
                    label = "GET /server-api/travels",
                    render = { "count=${it.size}" },
                ) { requireGatewayClient().listTravels() }
                    .onSuccess { incoming ->
                        if (gatewayEpoch != epoch) return@onSuccess
                        travels = incoming
                        artifactRefreshKey += 1
                        loadWorldDetailsForTravels(incoming, epoch)
                        if (gatewayEpoch == epoch && showResultToast) toast(R.string.refresh_travels_success)
                    }
                    .onFailure {
                        if (gatewayEpoch != epoch) return@onFailure
                        report(R.string.action_load_travels, it)
                        if (showResultToast) toast(R.string.refresh_travels_failed, long = true)
                    }
            } finally {
                if (gatewayEpoch == epoch) refreshingTravels = false
            }
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
        val epoch = gatewayEpoch
        artifactRequestedTravelIds = artifactRequestedTravelIds + travelId
        artifactFailedTravelIds = artifactFailedTravelIds - travelId
        scope.launch {
            artifactLoadingTravelIds = artifactLoadingTravelIds + travelId
            SdkDemoLog.gatewayCall(
                label = "GET /server-api/travels/artifacts",
                render = { "video=${it.video?.original?.url != null}" },
            ) {
                val mode = requireNotNull(travel.mode.toWorldKindOrNull()) { "Unsupported travel mode: ${travel.mode}" }
                requireGatewayClient().getTravelArtifacts(travelId, mode)
            }
                .onSuccess { artifacts ->
                    if (gatewayEpoch != epoch) return@onSuccess
                    artifactsByTravelId = artifactsByTravelId + (travelId to artifacts)
                    artifactPendingTravelIds = if (artifacts.video?.original?.url.isNullOrBlank()) {
                        artifactPendingTravelIds + travelId
                    } else {
                        artifactPendingTravelIds - travelId
                    }
                }
                .onFailure {
                    if (gatewayEpoch != epoch) return@onFailure
                    if ((it as? DemoGatewayException)?.code == 404000) {
                        artifactPendingTravelIds = artifactPendingTravelIds + travelId
                        SdkDemoLog.add(
                            SdkDemoLogKind.INFO,
                            "travel artifact processing",
                            "travel=$travelId, ${it.sdkDemoLogString()}",
                        )
                    } else {
                        artifactFailedTravelIds = artifactFailedTravelIds + travelId
                        artifactRequestedTravelIds = artifactRequestedTravelIds - travelId
                        if (showError) report(R.string.action_load_artifacts, it)
                    }
                }
            if (gatewayEpoch == epoch) artifactLoadingTravelIds = artifactLoadingTravelIds - travelId
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
        if (creatingWorld) return
        creatingWorld = true
        val epoch = gatewayEpoch
        val form = createForm
        val isScriptList = form.worldKind == WorldKind.Story &&
            form.storyCreationModel == StoryCreationModel.ScriptList
        val preset = form.scriptListPresetId
            ?.let { id -> scriptListPresets.firstOrNull { it.id == id } }
        scope.launch {
            error = null
            try {
                SdkDemoLog.gatewayCall(
                    label = "POST /server-api/worlds",
                    render = { "world=${it.encryptedWorldId}, status=${it.status}" },
                ) {
                    requireGatewayClient().createWorld(
                        kind = form.worldKind,
                        prompt = form.prompt,
                        firstFrameImageUrl = form.firstFrameImageUrl,
                        cameraView = form.cameraView,
                        resolution = form.resolution,
                        actingAspectRatio = form.actingAspectRatio,
                        inputImages = form.referenceImages.map { it.value },
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
                }.onSuccess { created ->
                    if (gatewayEpoch == epoch) {
                        retainedCreatedWorldIds += created.encryptedWorldId
                        worlds = worlds.replaceWorld(created)
                        trackWorldBuild(created)
                        selectedTab = DemoTab.Play
                        refreshWorlds()
                    }
                }.onFailure { if (gatewayEpoch == epoch) report(R.string.action_create_world, it) }
            } finally {
                creatingWorld = false
            }
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
                    // Updates replace acts; subjects remain bound to the world.
                    scriptList = ScriptListPayload(acts = ScriptListDrafts.parse(draft).acts),
                    mode = requireNotNull(travel.world.mode.toWorldKindOrNull()) {
                        "Unsupported world mode: ${travel.world.mode}"
                    },
                )
            }
                .onSuccess { if (activeTravel === travel) toast(R.string.update_script_accepted) }
                .onFailure { if (activeTravel === travel) report(R.string.action_update_script, it) }
            if (activeTravel === travel) updatingScript = false
        }
    }

    fun deleteWorld(world: DemoWorld) {
        val epoch = gatewayEpoch
        scope.launch {
            error = null
            SdkDemoLog.gatewayCall(
                label = "POST /server-api/worlds/delete",
                render = { "deleted" },
            ) {
                val mode = requireNotNull(world.mode.toWorldKindOrNull()) { "Unsupported world mode: ${world.mode}" }
                requireGatewayClient().deleteWorld(world.encryptedWorldId, mode)
            }
                .onSuccess {
                    if (gatewayEpoch != epoch) return@onSuccess
                    retainedCreatedWorldIds -= world.encryptedWorldId
                    worldBuildJobs.remove(world.encryptedWorldId)?.cancel()
                    worlds = worlds.filterNot { it.encryptedWorldId == world.encryptedWorldId }
                }
                .onFailure { if (gatewayEpoch == epoch) report(R.string.action_delete_world, it) }
        }
    }

    fun startTravel(world: DemoWorld) {
        if (startingTravel || activeTravel != null) return
        val epoch = gatewayEpoch
        startingTravel = true
        scope.launch {
            error = null
            try {
                runCatching {
                    val model = requireNotNull(config.modelForMode(world.mode)) {
                        "Unsupported world mode or missing model: ${world.mode}"
                    }
                    if (!hasFreshToken()) {
                        refreshTokenNow()
                    }
                    ensureGatewayEpoch(epoch)
                    initializeSdkForWorld(world, model)
                    val credential = SdkDemoLog.gatewayCall(
                        label = "POST /server-api/travel-credential",
                        render = { "world=${it.encryptedWorldId}, expiresIn=${it.expiresIn}" },
                    ) {
                        val mode = requireNotNull(world.mode.toWorldKindOrNull()) {
                            "Unsupported world mode: ${world.mode}"
                        }
                        requireGatewayClient().getTravelCredential(world.encryptedWorldId, mode)
                    }.getOrThrow()
                    ensureGatewayEpoch(epoch)
                    val started = SdkDemoLog.sdkCall(
                        label = "HappyOyster.startTravel(ticket)",
                        render = { "travel=${it.encryptedTravelId}, mode=${it.mode.rawValue}, firstFrame=${!it.firstFrame.isNullOrBlank()}" },
                    ) { sdkSession.startTravel(credential.ticket) }.getOrThrow()
                    try {
                        ensureGatewayEpoch(epoch)
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
                    travelTransition = travelTransition.clear()
                    endingTravel = false
                }.onFailure { if (gatewayEpoch == epoch) reportStartTravelFailure(it) }
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

    fun pauseTravel() = runTravelTransition(
        DemoTravelTransition.Operation.Pause,
        R.string.action_pause_travel,
        "HappyOyster.pauseTravel()",
    ) { sdkSession.pauseTravel() }

    fun resumeTravel() = runTravelTransition(
        DemoTravelTransition.Operation.Resume,
        R.string.action_resume_travel,
        "HappyOyster.resumeTravel()",
    ) { sdkSession.resumeTravel() }

    fun rewindTravel() {
        val seconds = travelTransition.rewindToSec ?: rewindToSec.toDoubleOrNull() ?: return
        if (!seconds.isFinite() || seconds <= 0 || seconds % 4.0 != 0.0) return
        runTravelTransition(
            DemoTravelTransition.Operation.Rewind,
            R.string.action_rewind_travel,
            "HappyOyster.rewindTravel($seconds)",
            seconds,
        ) { sdkSession.rewindTravel(seconds) }
    }

    private fun runTravelTransition(
        next: DemoTravelTransition.Operation,
        @StringRes action: Int,
        label: String,
        rewindSeconds: Double? = null,
        operation: suspend () -> Any,
    ) {
        val travel = activeTravel ?: return
        if (endingTravel || !travelTransition.canStart(next, travelStatus)) return
        travelTransition = travelTransition.begin(next, rewindSeconds)
        val attempt = travelTransition.epoch
        error = null
        scope.launch {
            SdkDemoLog.sdkCall(label = label, render = { "accepted; awaiting status confirmation" }, block = operation)
                .onSuccess {
                    if (activeTravel === travel) travelTransition = travelTransition.accepted(attempt)
                }
                .onFailure {
                    if (activeTravel === travel && !endingTravel && travelTransition.epoch == attempt) {
                        travelTransition = travelTransition.failed(attempt)
                        report(action, it)
                    }
                }
        }
    }

    private fun leaveTravelScreen() {
        activeTravel = null
        travelStatus = null
        travelTransition = travelTransition.clear()
        endingTravel = false
        updatingScript = false
        updateScriptPresetId = null
        updateScriptDraft = ""
    }

    private suspend fun refreshTokenNow() {
        val epoch = gatewayEpoch
        val token = SdkDemoLog.gatewayCall(
            label = "POST /server-api/temp-api-key",
            render = { "expiresAt=${it.expiresAtSec}" },
        ) { requireGatewayClient().mintTempApiKey() }.getOrThrow()
        ensureGatewayEpoch(epoch)
        val next = config.copy(token = token.token, tokenExpiresAtSec = token.expiresAtSec)
        persist(next)
        if (sdkListenerRegistered) sdkSession.updateToken(next.token)
    }

    private suspend fun loadWorldDetailsForTravels(incoming: List<DemoTravel>, epoch: Long) {
        val worldRefs = incoming
            .mapNotNull { travel -> travel.encryptedWorldId?.let { it to travel.mode } }
            .distinctBy { it.first }
            .filterNot { (worldId, _) -> worldId in worldDetailsById || worldId in inaccessibleWorldDetailIds }
        if (worldRefs.isEmpty()) return
        val client = requireGatewayClient()
        val inaccessibleBefore = inaccessibleWorldDetailIds.size
        val details = supervisorScope {
            worldRefs.map { (worldId, worldMode) ->
                async {
                    runCatching {
                        val mode = requireNotNull(worldMode.toWorldKindOrNull()) { "Unsupported world mode: $worldMode" }
                        worldDetailRequestLimiter.withPermit {
                            ensureGatewayEpoch(epoch)
                            client.getWorldDetail(worldId, mode).copy(mode = mode.worldMode)
                        }
                    }.rethrowCancellation()
                        .onSuccess {
                            SdkDemoLog.add(SdkDemoLogKind.GATEWAY, "GET /server-api/worlds/detail", "status=${it.status}")
                        }
                        .onFailure { cause ->
                            if (gatewayEpoch != epoch) return@onFailure
                            if ((cause as? DemoGatewayException)?.code == 403001) {
                                inaccessibleWorldDetailIds += worldId
                            } else {
                                SdkDemoLog.add(
                                    SdkDemoLogKind.ERROR,
                                    "world detail load",
                                    cause.sdkDemoLogString(),
                                )
                            }
                        }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        if (gatewayEpoch != epoch) return
        val inaccessibleCount = inaccessibleWorldDetailIds.size - inaccessibleBefore
        if (inaccessibleCount > 0) {
            SdkDemoLog.add(
                SdkDemoLogKind.INFO,
                "historical world details unavailable",
                "count=$inaccessibleCount, code=403001",
            )
        }
        if (details.isEmpty()) return
        worldDetailsById = worldDetailsById + details.associateBy { it.encryptedWorldId }
    }

    private fun ensureGatewayEpoch(epoch: Long) {
        if (gatewayEpoch != epoch) throw CancellationException("Gateway configuration changed")
    }

    private fun hasFreshToken(): Boolean =
        config.token.isNotBlank() && config.tokenExpiresAtSec > (System.currentTimeMillis() / 1000L) + 30L

    private fun requireGatewayClient(): DemoGatewayClient =
        gatewayClient ?: throw IllegalArgumentException(localizedContext.getString(R.string.missing_gateway_base_url))

    private fun setError(@StringRes actionResId: Int, cause: Throwable) {
        val detail = when (cause) {
            is SDKError -> cause.sdkDemoMessage(localizedContext)
            is DemoGatewayException -> cause.sdkDemoMessage(localizedContext)
            else -> localizedContext.getString(R.string.gateway_error_generic)
        }
        error = SdkDemoError.Action(actionResId, detail)
    }

    fun dismissError() {
        error = null
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
        toast(localizedContext.getString(resId), long)
    }

    private fun toast(message: String, long: Boolean = false) {
        Toast.makeText(
            localizedContext,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }
}
