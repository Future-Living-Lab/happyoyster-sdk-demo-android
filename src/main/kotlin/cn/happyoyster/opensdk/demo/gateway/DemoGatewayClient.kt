package cn.happyoyster.opensdk.demo.gateway

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
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
        // Tolerate string-typed numbers (for example expiresIn) from the gateway.
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
        wanderUploadMode: WanderUploadMode,
        prompt: String,
        firstFrameImageUrl: String,
        scenePrompt: String,
        sceneImageUrl: String,
        rolePrompt: String,
        roleImageUrl: String,
        cameraView: CameraView,
        resolution: StoryResolution,
        creationModel: StoryCreationModel = StoryCreationModel.Simple,
        scriptList: ScriptListPayload? = null,
    ): DemoWorld {
        val trimmedPrompt = prompt.trim().ifBlank { null }
        val imageRef = firstFrameImageUrl.trim()
            .takeIf { it.isNotBlank() }
            ?.toDemoImageRef()
        val sceneRef = sceneImageUrl.trim()
            .takeIf { it.isNotBlank() }
            ?.toDemoImageRef()
        val roleRef = roleImageUrl.trim()
            .takeIf { it.isNotBlank() }
            ?.toDemoImageRef()
        val isScenarioRole = kind == WorldKind.Wander && wanderUploadMode == WanderUploadMode.ScenarioRole
        val isScriptList = kind == WorldKind.Story && creationModel == StoryCreationModel.ScriptList
        // ScriptList worlds reject every prompt/Wander field; the request carries
        // only mode + creationModel + resolution + firstFrameImage + scriptList.
        val body = if (isScriptList) {
            CreateWorldRequest(
                mode = kind.serverMode,
                creationModel = creationModel.wireValue,
                resolution = resolution.wireValue,
                firstFrameImage = imageRef,
                scriptList = scriptList,
                userAgent = USER_AGENT,
            )
        } else {
            CreateWorldRequest(
                mode = kind.serverMode,
                prompt = trimmedPrompt.takeUnless { isScenarioRole },
                perspective = cameraView.wireValue.takeIf { kind == WorldKind.Wander },
                uploadMode = if (kind == WorldKind.Wander) wanderUploadMode.wireValue else null,
                firstFrameImage = imageRef.takeIf {
                    (kind == WorldKind.Wander && wanderUploadMode == WanderUploadMode.FirstFrame) ||
                        kind == WorldKind.Story
                },
                sceneImage = sceneRef.takeIf { isScenarioRole },
                scenePrompt = scenePrompt.trim().ifBlank { null }.takeIf { isScenarioRole },
                roleImage = roleRef.takeIf { isScenarioRole },
                rolePrompt = rolePrompt.trim().ifBlank { null }.takeIf { isScenarioRole },
                inputImages = null,
                resolution = if (kind == WorldKind.Story) resolution.wireValue else null,
                layout = if (kind == WorldKind.Story) "Stable" else null,
                narrative = if (kind == WorldKind.Story) "Normal" else null,
                userAgent = USER_AGENT,
            )
        }
        return postData("worlds", body) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data)
                .let { world ->
                    if (world.mode == "unknown") {
                        world.copy(mode = if (kind == WorldKind.Story) "story" else "wander")
                    } else {
                        world
                    }
                }
        }
    }

    private fun String.toDemoImageRef(): DemoImageRef =
        if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
            DemoImageRef(url = this, referenceType = "default")
        } else {
            DemoImageRef(base64 = this)
        }

    suspend fun updateScript(encryptedTravelId: String, scriptList: ScriptListPayload): UpdateScriptResult =
        postData(
            "travels/update-script",
            UpdateScriptRequest(
                encryptedTravelId = encryptedTravelId,
                scriptList = scriptList,
                userAgent = USER_AGENT,
            ),
        ) { data ->
            json.decodeFromJsonElement(UpdateScriptResult.serializer(), data)
        }

    suspend fun listWorlds(page: Int = 1, pageSize: Int = 100): List<DemoWorld> =
        getData(
            "worlds",
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(WorldsPage.serializer(), data).items
        }

    suspend fun getBuildStatus(encryptedWorldId: String): DemoWorld =
        getData(
            "worlds/build-status",
            "encryptedWorldId" to encryptedWorldId,
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data)
        }

    suspend fun getWorldDetail(encryptedWorldId: String): DemoWorld =
        getData("worlds/detail", "encryptedWorldId" to encryptedWorldId) { data ->
            json.decodeFromJsonElement(DemoWorld.serializer(), data)
        }

    suspend fun getTravelCredential(encryptedWorldId: String): TravelCredential =
        postData("travel-credential", WorldIdRequest(encryptedWorldId)) { data ->
            json.decodeFromJsonElement(TravelCredential.serializer(), data)
        }

    suspend fun listTravels(page: Int = 1, pageSize: Int = 100): List<DemoTravel> =
        getData(
            "travels",
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(TravelsPage.serializer(), data).items
        }

    suspend fun getTravelArtifacts(encryptedTravelId: String): TravelArtifacts =
        getData(
            "travels/artifacts",
            "encryptedTravelId" to encryptedTravelId,
            "userAgent" to USER_AGENT,
        ) { data ->
            json.decodeFromJsonElement(TravelArtifacts.serializer(), data)
        }

    suspend fun deleteWorld(encryptedWorldId: String): Unit =
        postData("worlds/delete", WorldIdRequest(encryptedWorldId)) { }

    private suspend fun <R> getData(
        pathSegments: String,
        vararg query: Pair<String, String>,
        decode: (JsonElement) -> R,
    ): R = withContext(Dispatchers.IO) {
        val url = baseHttpUrl.newBuilder().addPathSegments(pathSegments)
        query.forEach { (name, value) -> url.addQueryParameter(name, value) }
        val request = Request.Builder()
            .url(url.build())
            .get()
            .gatewayHeaders()
            .build()
        executeData(request, decode)
    }

    private suspend inline fun <reified T : Any, R> postData(
        pathSegments: String,
        body: T,
        noinline decode: (JsonElement) -> R,
    ): R = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseHttpUrl.newBuilder().addPathSegments(pathSegments).build())
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .gatewayHeaders()
            .build()
        executeData(request, decode)
    }

    private fun Request.Builder.gatewayHeaders(): Request.Builder {
        header("Accept", "application/json")
        header("Content-Type", "application/json")
        return this
    }

    private fun <T> executeRoot(request: Request, decode: (JsonElement) -> T): T {
        val root = readJson(request)
        failIfLocalError(request, root)
        return try {
            decode(root)
        } catch (error: SerializationException) {
            throw fail(request, "Unexpected gateway response: $root", cause = error)
        }
    }

    private fun <T> executeData(request: Request, decode: (JsonElement) -> T): T {
        val root = readJson(request)
        failIfLocalError(request, root)
        val envelopeRoot = (root as? JsonObject)?.get("output") ?: root
        val envelope = try {
            json.decodeFromJsonElement(ApiEnvelope.serializer(), envelopeRoot)
        } catch (error: SerializationException) {
            throw fail(request, "Invalid gateway envelope: $root", cause = error)
        }
        val code = envelope.code?.asIntOrNull()
            ?: throw fail(request, "Invalid gateway code: ${envelope.code}")
        if (code != 0) {
            throw fail(
                request = request,
                rawMessage = "Gateway business error $code: ${envelope.message.orEmpty()}",
                code = code,
            )
        }
        val data = envelope.data ?: JsonObject(emptyMap())
        return try {
            decode(data)
        } catch (error: SerializationException) {
            throw fail(request, "Unexpected gateway data: $data", cause = error)
        }
    }

    private fun readJson(request: Request): JsonElement {
        val response = try {
            client.newCall(request).execute().use { result ->
                HttpResponse(result.code, result.isSuccessful, result.body?.string().orEmpty())
            }
        } catch (error: IOException) {
            throw fail(request, "Network error: ${error.message}", cause = error)
        }
        val root = try {
            json.parseToJsonElement(response.body)
        } catch (error: SerializationException) {
            throw fail(request, "Invalid JSON response: ${response.body}", cause = error)
        }
        if (!response.isSuccessful) {
            throw fail(request, "HTTP ${response.statusCode}: ${response.body}")
        }
        return root
    }

    private fun failIfLocalError(request: Request, root: JsonElement) {
        val obj = root as? JsonObject ?: return
        val success = (obj["success"] as? JsonPrimitive)?.content
        if (success == "false") {
            throw fail(
                request = request,
                rawMessage = "${obj.string("errorCode").orEmpty()}: ${obj.string("message").orEmpty()}",
                errorCode = obj.string("errorCode"),
            )
        }
    }

    private fun fail(
        request: Request,
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

        private fun JsonElement.asIntOrNull(): Int? =
            (this as? JsonPrimitive)?.content?.toIntOrNull()

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
