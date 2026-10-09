@file:OptIn(ExperimentalSerializationApi::class)

package cn.happyoyster.opensdk.demo.gateway

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive

internal enum class WorldKind(val serverMode: Int, val worldMode: String) {
    Wander(1, "wander"),
    Story(2, "story"),
    Acting(3, "acting"),
}

internal const val MAX_STORY_REFERENCE_IMAGES = 6

internal fun String?.toWorldKindOrNull(): WorldKind? = when (this?.trim()?.lowercase()) {
    "1", "wander", "adventure" -> WorldKind.Wander
    "2", "story", "direct", "directing" -> WorldKind.Story
    "3", "acting" -> WorldKind.Acting
    else -> null
}

internal enum class CameraView(val wireValue: String) {
    FirstPerson("first_person"),
    ThirdPerson("third_person"),
}

internal enum class StoryResolution(val wireValue: String, val label: String) {
    P480("480p", "480p"),
    P720("720p", "720p"),
}

internal enum class StoryCreationModel(val wireValue: String) {
    Simple("simple"),
    ScriptList("scriptlist"),
}

@Serializable
internal data class TempApiKey(
    val token: String,
    @SerialName("expires_at")
    val expiresAtSec: Long,
)

@Serializable
internal data class CreateWorldRequest(
    val mode: Int,
    val async: Boolean = true,
    val prompt: String? = null,
    val perspective: String? = null,
    val uploadMode: String? = null,
    val firstFrameImage: DemoImageRef? = null,
    val inputImages: List<DemoImageRef>? = null,
    val resolution: String? = null,
    val layout: String? = null,
    val narrative: String? = null,
    val creationModel: String? = null,
    val scriptList: ScriptListPayload? = null,
    val aspectRatio: String? = null,
    val userAgent: String,
)

@Serializable
internal data class DemoImageRef(
    val url: String? = null,
    val base64: String? = null,
    val referenceType: String? = null,
)

@Serializable
internal data class ScriptListPayload(
    val synopsis: String? = null,
    val videoTitle: String? = null,
    val subjects: List<ScriptSubject>? = null,
    val acts: List<ScriptAct>,
)

@Serializable
internal data class ScriptSubject(
    val label: String? = null,
    val name: String? = null,
    val type: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val ethnicity: String? = null,
    val appearance: String? = null,
    val position: String? = null,
    val voice: String? = null,
    val refImage: DemoImageRef? = null,
)

@Serializable
internal data class ScriptAct(
    val turn: Int,
    val content: String,
    val cameraType: String? = null,
    val shotSize: String? = null,
    val cut: String? = null,
)

@Serializable
internal data class UpdateScriptRequest(
    val encryptedTravelId: String,
    val scriptList: ScriptListPayload,
    val userAgent: String,
)

@Serializable
internal data class UpdateScriptResult(
    val encryptedTravelId: String? = null,
    val accepted: Boolean? = null,
    val turnCount: Int? = null,
)

// Gateway responses mix camelCase and snake_case field names depending on the
// endpoint; @JsonNames accepts both spellings into a single property.
@Serializable
internal data class DemoWorld(
    val encryptedWorldId: String,
    val name: String? = null,
    val title: String? = null,
    val status: String = "unknown",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    @Serializable(with = WorldModeSerializer::class)
    val mode: String = "unknown",
    val prompt: String? = null,
    @JsonNames("first_frame")
    val firstFrame: String? = null,
    @JsonNames("first_frame_image_url")
    val firstFrameImageUrl: String? = null,
    @JsonNames("preview_url")
    val previewUrl: String? = null,
    @JsonNames("cover_url")
    val coverUrl: String? = null,
    @JsonNames("image_context")
    val imageContext: DemoImageContext? = null,
    @JsonNames("home_page_config")
    val homePageConfig: DemoHomePageConfig? = null,
    @JsonNames("created_at")
    val createdAt: String? = null,
    @JsonNames("updated_at")
    val updatedAt: String? = null,
) {
    val displayName: String? get() = firstNonEmpty(title, name)
    val isReady: Boolean get() = status.equals("ready", ignoreCase = true)
    val modeLabel: String get() = when (mode) {
        "adventure" -> "Wander"
        "directing" -> "Story"
        "story" -> "Story"
        "wander" -> "Wander"
        "acting" -> "Acting"
        else -> "Unknown"
    }
    val imageUrl: String?
        get() = firstNonEmpty(
            coverUrl,
            homePageConfig?.thumbnailUrl,
            firstFrameImageUrl,
            imageContext?.fallbackImageAsset?.url,
            imageContext?.referenceImages?.firstOrNull()?.url,
            firstFrame,
            previewUrl,
        )
}

private fun firstNonEmpty(vararg candidates: String?): String? {
    for (candidate in candidates) {
        val value = candidate?.trim()
        if (!value.isNullOrEmpty()) return value
    }
    return null
}

@Serializable
internal data class DemoImageContext(
    @JsonNames("first_frame_image")
    val firstFrameImage: DemoImageAsset? = null,
    @JsonNames("scene_image")
    val sceneImage: DemoImageAsset? = null,
    @JsonNames("role_image")
    val roleImage: DemoImageAsset? = null,
    @JsonNames("reference_images")
    val referenceImages: List<DemoImageAsset> = emptyList(),
) {
    val fallbackImageAsset: DemoImageAsset?
        get() = firstFrameImage ?: sceneImage ?: roleImage
}

@Serializable
internal data class DemoImageAsset(
    @JsonNames("image_url")
    val imageUrl: String? = null,
) {
    val url: String? get() = firstNonEmpty(imageUrl)
}

@Serializable
internal data class DemoHomePageConfig(
    @JsonNames("first_frame")
    val firstFrame: DemoMediaGroup? = null,
) {
    val thumbnailUrl: String? get() = firstFrame?.thumbnailUrl
}

@Serializable
internal data class DemoMediaGroup(
    @JsonNames("resolution_480p")
    val resolution480p: DemoMediaVariant? = null,
    @JsonNames("resolution_1080p")
    val resolution1080p: DemoMediaVariant? = null,
    val origin: DemoMediaVariant? = null,
) {
    val thumbnailUrl: String?
        get() = firstNonEmpty(resolution480p?.url, resolution1080p?.url, origin?.url)
}

@Serializable
internal data class DemoMediaVariant(
    val url: String? = null,
)

@Serializable
internal data class WorldsPage(
    val items: List<DemoWorld> = emptyList(),
    val pagination: PageInfo? = null,
)

@Serializable
internal data class PageInfo(
    val hasMore: Boolean = false,
)

@Serializable
internal data class WorldIdRequest(
    val encryptedWorldId: String,
)

@Serializable
internal data class TravelCredential(
    val ticket: String,
    val expiresIn: Int? = null,
    val encryptedWorldId: String,
)

@Serializable
internal data class DemoTravel(
    val encryptedTravelId: String,
    val encryptedWorldId: String? = null,
    val status: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    @Serializable(with = WorldModeSerializer::class)
    val mode: String? = null,
    val durationSec: Int? = null,
    val createdAt: String? = null,
    val endedAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class TravelsPage(
    val items: List<DemoTravel> = emptyList(),
    val pagination: PageInfo? = null,
)

@Serializable
internal data class TravelArtifacts(
    val video: ArtifactVideo? = null,
)

@Serializable
internal data class ArtifactVideo(
    val original: ArtifactMedia? = null,
)

@Serializable
internal data class ArtifactMedia(
    val url: String? = null,
    val resolution: String? = null,
    val durationSec: Int? = null,
)

private object WorldModeSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WorldMode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val raw = (decoder as? JsonDecoder)
            ?.decodeJsonElement()
            ?.let { (it as? JsonPrimitive)?.content ?: it.toString() }
            ?: decoder.decodeString()
        return when (raw.trim().lowercase()) {
            "1", "wander", "adventure" -> "wander"
            "2", "story", "direct", "directing" -> "story"
            "3", "acting" -> "acting"
            else -> raw
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

internal fun DemoWorld.mergeFrom(incoming: DemoWorld): DemoWorld =
    incoming.copy(
        name = incoming.name ?: name,
        title = incoming.title ?: title,
        status = incoming.status.takeUnless { it.isBlank() || it == "unknown" } ?: status,
        errorCode = incoming.errorCode ?: errorCode,
        errorMessage = incoming.errorMessage ?: errorMessage,
        mode = incoming.mode.takeIf { it.toWorldKindOrNull() != null } ?: mode,
        prompt = incoming.prompt ?: prompt,
        firstFrame = incoming.firstFrame ?: firstFrame,
        firstFrameImageUrl = incoming.firstFrameImageUrl ?: firstFrameImageUrl,
        previewUrl = incoming.previewUrl ?: previewUrl,
        coverUrl = incoming.coverUrl ?: coverUrl,
        imageContext = incoming.imageContext ?: imageContext,
        homePageConfig = incoming.homePageConfig ?: homePageConfig,
        createdAt = incoming.createdAt ?: createdAt,
        updatedAt = incoming.updatedAt ?: updatedAt,
    )

internal fun DemoWorld.withFirstFrame(firstFrameUrl: String?): DemoWorld =
    if (firstFrameUrl.isNullOrBlank()) {
        this
    } else {
        copy(firstFrame = firstFrame ?: firstFrameUrl)
    }
