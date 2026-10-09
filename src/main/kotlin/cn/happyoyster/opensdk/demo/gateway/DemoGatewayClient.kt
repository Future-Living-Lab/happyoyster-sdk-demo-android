package cn.happyoyster.opensdk.demo.gateway

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class DemoGatewayClient(
    baseUrl: String,
    private val client: OkHttpClient = DEFAULT_CLIENT,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
    private val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    suspend fun mintTempApiKey(expireSeconds: Int = 1800): TempApiKey =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    baseHttpUrl.newBuilder()
                        .addPathSegment("temp-api-key")
                        .addQueryParameter("expire_in_seconds", expireSeconds.toString())
                        .build(),
                )
                .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
                .gatewayHeaders()
                .build()
            executeRoot(request) { root ->
                json.decodeFromJsonElement(TempApiKey.serializer(), root)
            }
        }

    suspend fun createWorld(
        kind: WorldKind,
        prompt: String,
        firstFrameImageUrl: String,
        cameraView: CameraView,
        resolution: StoryResolution,
        actingAspectRatio: String = "9:16",
        creationModel: StoryCreationModel = StoryCreationModel.Simple,
        scriptList: ScriptListPayload? = null,
        inputImages: List<String> = emptyList(),
    ): DemoWorld {
        val body = buildCreateWorldRequest(
            kind, prompt, firstFrameImageUrl, cameraView, resolution,
            actingAspectRatio, creationModel, scriptList, inputImages, USER_AGENT,
        )
        return postData("worlds", body, kind) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data)
                .copy(mode = kind.worldMode)
        }
    }

    suspend fun updateScript(
        encryptedTravelId: String,
        scriptList: ScriptListPayload,
        mode: WorldKind,
    ): UpdateScriptResult =
        postData(
            "travels/update-script",
            UpdateScriptRequest(
                encryptedTravelId = encryptedTravelId,
                scriptList = scriptList,
                userAgent = USER_AGENT,
            ),
            mode,
        ) { data ->
            json.decodeFromJsonElement(UpdateScriptResult.serializer(), data)
        }

    suspend fun listWorlds(pageSize: Int = 100): List<DemoWorld> =
        WorldKind.entries.flatMap { mode ->
            val worlds = mutableListOf<DemoWorld>()
            var page = 1
            while (true) {
                val result = getData(
                    "worlds", mode,
                    "page" to page.toString(),
                    "pageSize" to pageSize.toString(),
                    "userAgent" to USER_AGENT,
                    "mode" to mode.serverMode.toString(),
                ) { data -> json.decodeFromJsonElement(WorldsPage.serializer(), data) }
                if (result.pagination?.hasMore == true && result.items.isEmpty()) {
                    throw fail("Invalid gateway pagination")
                }
                worlds += result.items.map { world ->
                    if (world.mode.toWorldKindOrNull() == null) world.copy(mode = mode.worldMode) else world
                }
                if (result.pagination?.hasMore ?: (result.items.size == pageSize)) page++ else break
            }
            worlds
        }.distinctBy { it.encryptedWorldId }

    suspend fun getBuildStatus(encryptedWorldId: String, mode: WorldKind): DemoWorld =
        getData(
            "worlds/build-status",
            mode,
            "encryptedWorldId" to encryptedWorldId,
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data).copy(mode = mode.worldMode)
        }

    suspend fun getWorldDetail(encryptedWorldId: String, mode: WorldKind): DemoWorld =
        getData("worlds/detail", mode, "encryptedWorldId" to encryptedWorldId) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data)
        }

    suspend fun getTravelCredential(encryptedWorldId: String, mode: WorldKind): TravelCredential =
        postData("travel-credential", WorldIdRequest(encryptedWorldId), mode) { data ->
            json.decodeFromJsonElement(TravelCredential.serializer(), data)
        }

    suspend fun listTravels(pageSize: Int = 100): List<DemoTravel> =
        WorldKind.entries.flatMap { mode ->
            val travels = mutableListOf<DemoTravel>()
            var page = 1
            while (true) {
                val result = getData(
                    "travels", mode,
                    "page" to page.toString(),
                    "pageSize" to pageSize.toString(),
                    "userAgent" to USER_AGENT,
                ) { data -> json.decodeFromJsonElement(TravelsPage.serializer(), data) }
                if (result.pagination?.hasMore == true && result.items.isEmpty()) {
                    throw fail("Invalid gateway pagination")
                }
                travels += result.items.map { travel ->
                    if (travel.mode.toWorldKindOrNull() == null) travel.copy(mode = mode.worldMode) else travel
                }
                if (result.pagination?.hasMore ?: (result.items.size == pageSize)) page++ else break
            }
            travels
        }.distinctBy { it.encryptedTravelId }

    suspend fun getTravelArtifacts(encryptedTravelId: String, mode: WorldKind): TravelArtifacts =
        getData(
            "travels/artifacts",
            mode,
            "encryptedTravelId" to encryptedTravelId,
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(TravelArtifacts.serializer(), data)
        }

    suspend fun deleteWorld(encryptedWorldId: String, mode: WorldKind): Unit =
        postData("worlds/delete", WorldIdRequest(encryptedWorldId), mode) { }

    private suspend fun <R> getData(
        pathSegments: String,
        mode: WorldKind,
        vararg query: Pair<String, String>,
        decode: (JsonElement) -> R,
    ): R = withContext(Dispatchers.IO) {
        val url = baseHttpUrl.newBuilder().addPathSegments(pathSegments)
        query.forEach { (name, value) -> url.addQueryParameter(name, value) }
        val request = Request.Builder()
            .url(url.build())
            .get()
            .gatewayHeaders(mode)
            .build()
        executeData(request, decode)
    }

    private suspend inline fun <reified T : Any, R> postData(
        pathSegments: String,
        body: T,
        mode: WorldKind,
        noinline decode: (JsonElement) -> R,
    ): R = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseHttpUrl.newBuilder().addPathSegments(pathSegments).build())
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .gatewayHeaders(mode)
            .build()
        executeData(request, decode)
    }

    private fun Request.Builder.gatewayHeaders(mode: WorldKind? = null): Request.Builder {
        header("Accept", "application/json")
        header("Content-Type", "application/json")
        if (mode != null) header("X-Happy-Oyster-Mode", mode.serverMode.toString())
        return this
    }

    private fun <T> executeRoot(request: Request, decode: (JsonElement) -> T): T {
        val root = readJson(request)
        failIfLocalError(root)
        return try {
            decode(root)
        } catch (error: SerializationException) {
            throw fail("Unexpected gateway response", cause = error)
        }
    }

    private fun <T> executeData(request: Request, decode: (JsonElement) -> T): T {
        val root = readJson(request)
        failIfLocalError(root)
        val envelopeRoot = (root as? JsonObject)?.get("output") ?: root
        val envelope = try {
            json.decodeFromJsonElement(ApiEnvelope.serializer(), envelopeRoot)
        } catch (error: SerializationException) {
            throw fail("Invalid gateway envelope", cause = error)
        }
        val rawCode = (envelope.code as? JsonPrimitive)?.content
            ?: throw fail("Invalid gateway code")
        val code = rawCode.toIntOrNull()
            ?: throw fail(
                "Gateway business error $rawCode: ${envelope.message.orEmpty()}",
                errorCode = rawCode,
            )
        if (code != 0) {
            throw fail(
                rawMessage = "Gateway business error $code: ${envelope.message.orEmpty()}",
                code = code,
            )
        }
        val data = envelope.data ?: JsonObject(emptyMap())
        return try {
            decode(data)
        } catch (error: SerializationException) {
            throw fail("Unexpected gateway data", cause = error)
        }
    }

    private fun readJson(request: Request): JsonElement {
        val response = try {
            client.newCall(request).execute().use { result ->
                HttpResponse(result.code, result.isSuccessful, result.body?.string().orEmpty())
            }
        } catch (error: IOException) {
            throw fail("Network error", cause = error)
        }
        val root = try {
            json.parseToJsonElement(response.body)
        } catch (error: SerializationException) {
            throw fail("Invalid JSON response (HTTP ${response.statusCode})", cause = error)
        }
        if (!response.isSuccessful) {
            throw fail("HTTP ${response.statusCode}")
        }
        return root
    }

    private fun failIfLocalError(root: JsonElement) {
        val obj = root as? JsonObject ?: return
        val success = (obj["success"] as? JsonPrimitive)?.content
        if (success == "false") {
            throw fail(
                rawMessage = "${obj.string("errorCode").orEmpty()}: ${obj.string("message").orEmpty()}",
                errorCode = obj.string("errorCode"),
            )
        }
    }

    private fun fail(
        rawMessage: String,
        code: Int? = null,
        errorCode: String? = null,
        cause: Throwable? = null,
    ): DemoGatewayException {
        return DemoGatewayException(rawMessage, code = code, errorCode = errorCode, cause = cause)
    }

    private companion object {
        private const val USER_AGENT = "happy-oyster-sdk-demo/android/1.0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DEFAULT_CLIENT = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        private fun JsonObject.string(name: String): String? =
            (get(name) as? JsonPrimitive)?.content
    }
}

internal class DemoGatewayException(
    message: String,
    val code: Int? = null,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

@Serializable
private data class ApiEnvelope(
    val code: JsonElement? = null,
    val message: String? = null,
    val data: JsonElement? = null,
)

private data class HttpResponse(
    val statusCode: Int,
    val isSuccessful: Boolean,
    val body: String,
)

/** Builds only the fields accepted by the selected OpenAPI creation mode. */
internal fun buildCreateWorldRequest(
    kind: WorldKind,
    prompt: String,
    firstFrameImageUrl: String,
    cameraView: CameraView,
    resolution: StoryResolution,
    actingAspectRatio: String = "9:16",
    creationModel: StoryCreationModel = StoryCreationModel.Simple,
    scriptList: ScriptListPayload? = null,
    inputImages: List<String> = emptyList(),
    userAgent: String = "happy-oyster-sdk-demo/android/1.0",
): CreateWorldRequest {
    val isScriptList = kind == WorldKind.Story && creationModel == StoryCreationModel.ScriptList
    val trimmedPrompt = prompt.trim()
    if (!isScriptList) {
        require(trimmedPrompt.codePointCount(0, trimmedPrompt.length) in 1..2000) {
            "A prompt of 1 to 2000 characters is required"
        }
    }
    val requiresFirstFrame = kind != WorldKind.Story || isScriptList
    val imageRef = if (requiresFirstFrame) {
        require(firstFrameImageUrl.isNotBlank()) { "A first-frame image is required" }
        firstFrameImageUrl.trim().toDemoImageRef()
    } else null
    return when {
        kind == WorldKind.Acting -> {
            require(actingAspectRatio == "9:16" || actingAspectRatio == "16:9") {
                "Acting requires a 9:16 or 16:9 aspect ratio"
            }
            CreateWorldRequest(
                mode = kind.serverMode, prompt = trimmedPrompt, uploadMode = "first_frame",
                firstFrameImage = imageRef, resolution = resolution.wireValue,
                aspectRatio = actingAspectRatio, userAgent = userAgent,
            )
        }
        isScriptList -> {
            require(scriptList != null) { "ScriptList content is required" }
            CreateWorldRequest(
                mode = kind.serverMode, creationModel = creationModel.wireValue,
                resolution = resolution.wireValue, firstFrameImage = imageRef,
                scriptList = scriptList, userAgent = userAgent,
            )
        }
        kind == WorldKind.Story -> {
            val references = inputImages.map(String::trim).filter(String::isNotEmpty)
            require(references.size <= MAX_STORY_REFERENCE_IMAGES) {
                "Story supports at most $MAX_STORY_REFERENCE_IMAGES reference images"
            }
            CreateWorldRequest(
                mode = kind.serverMode, creationModel = StoryCreationModel.Simple.wireValue,
                prompt = trimmedPrompt, resolution = resolution.wireValue,
                inputImages = references.map { it.toDemoImageRef() }.takeIf { it.isNotEmpty() },
                userAgent = userAgent,
            )
        }
        else -> CreateWorldRequest(
            mode = kind.serverMode, creationModel = StoryCreationModel.Simple.wireValue,
            prompt = trimmedPrompt, perspective = cameraView.wireValue, uploadMode = "first_frame",
            firstFrameImage = imageRef, resolution = resolution.wireValue, userAgent = userAgent,
        )
    }
}

private fun String.toDemoImageRef(): DemoImageRef =
    if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
        DemoImageRef(url = this, referenceType = "default")
    } else {
        DemoImageRef(base64 = this)
    }
