package cn.happyoyster.opensdk.demo.gateway

import cn.happyoyster.opensdk.demo.app.CreateWorldForm
import cn.happyoyster.opensdk.demo.app.ReferenceImageInput
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class CreateWorldRequestTest {
    private val json = Json { explicitNulls = false; encodeDefaults = true }

    @Test
    fun actingAndWanderRequireBothPromptAndFirstFrame() {
        listOf(WorldKind.Acting, WorldKind.Wander).forEach { kind ->
            assertThrows(IllegalArgumentException::class.java) { request(kind, prompt = " \n ") }
            assertThrows(IllegalArgumentException::class.java) { request(kind, firstFrame = " ") }
            assertThrows(IllegalArgumentException::class.java) { request(kind, prompt = "a".repeat(2001)) }
            val body = request(kind, references = listOf("https://example.com/ignored.png"))
            assertEquals("first_frame", body.uploadMode)
            assertEquals("https://example.com/first.png", body.firstFrameImage?.url)
            assertNull(body.inputImages)
        }
    }

    @Test
    fun adventureCarriesSelectedResolutionAndPerspective() {
        StoryResolution.entries.forEach { resolution ->
            val body = request(WorldKind.Wander, resolution = resolution)
            assertEquals(resolution.wireValue, body.resolution)
            assertEquals("first_person", body.perspective)
            assertEquals("simple", body.creationModel)
        }
    }

    @Test
    fun simpleStoryUsesOnlyReferenceImagesAndOmitsEmptyList() {
        val withoutReferences = request(WorldKind.Story, firstFrame = "ignored stale first frame")
        assertNull(withoutReferences.inputImages)
        assertNull(withoutReferences.firstFrameImage)
        val encoded = json.parseToJsonElement(json.encodeToString(withoutReferences)).jsonObject
        listOf("inputImages", "firstFrameImage", "layout", "narrative", "uploadMode", "perspective", "scriptList")
            .forEach { assertFalse("Unexpected field: $it", encoded.containsKey(it)) }
        assertEquals("simple", withoutReferences.creationModel)

        val references = listOf("https://example.com/one.png", "data:image/png;base64,aGVsbG8=", "", "  https://example.com/two.png  ")
        val withReferences = request(WorldKind.Story, references = references)
        assertEquals(3, withReferences.inputImages?.size)
        assertEquals("https://example.com/one.png", withReferences.inputImages?.get(0)?.url)
        assertEquals("default", withReferences.inputImages?.get(0)?.referenceType)
        assertEquals("data:image/png;base64,aGVsbG8=", withReferences.inputImages?.get(1)?.base64)
        assertEquals("https://example.com/two.png", withReferences.inputImages?.get(2)?.url)
        assertNull(withReferences.firstFrameImage)
    }

    @Test
    fun simpleStoryRejectsMoreThanSixReferencesAndMissingPrompt() {
        assertEquals(6, request(WorldKind.Story, references = List(6) { "https://example.com/$it.png" }).inputImages?.size)
        assertThrows(IllegalArgumentException::class.java) {
            request(WorldKind.Story, references = List(7) { "https://example.com/$it.png" })
        }
        assertThrows(IllegalArgumentException::class.java) { request(WorldKind.Story, prompt = " ") }
    }

    @Test
    fun scriptListRequiresFirstFrameAndDoesNotLeakSimpleFields() {
        val script = ScriptListPayload(acts = listOf(ScriptAct(1, "Scene")))
        val body = request(
            WorldKind.Story, creationModel = StoryCreationModel.ScriptList,
            references = listOf("https://example.com/ignored.png"), scriptList = script,
        )
        val encoded = json.parseToJsonElement(json.encodeToString(body)).jsonObject
        listOf("prompt", "inputImages", "layout", "narrative", "perspective", "uploadMode")
            .forEach { assertFalse("Unexpected field: $it", encoded.containsKey(it)) }
        assertEquals("scriptlist", body.creationModel)
        assertEquals(script, body.scriptList)
        assertTrue(encoded.containsKey("firstFrameImage"))
        assertThrows(IllegalArgumentException::class.java) {
            request(WorldKind.Story, creationModel = StoryCreationModel.ScriptList, scriptList = script, firstFrame = "")
        }
    }

    @Test
    fun modeSwitchDropsImageAndScriptListFields() {
        val switched = CreateWorldForm(
            firstFrameImageUrl = "https://example.com/first.png",
            referenceImages = listOf(ReferenceImageInput(value = "https://example.com/ref.png")),
            scriptListPresetId = "preset", scriptListSynopsis = "synopsis", scriptListDraft = "draft",
        ).clearScriptListFields()
        assertTrue(switched.firstFrameImageUrl.isEmpty())
        assertTrue(switched.referenceImages.isEmpty())
        assertNull(switched.scriptListPresetId)
        assertTrue(switched.scriptListSynopsis.isEmpty())
        assertTrue(switched.scriptListDraft.isEmpty())
    }

    private fun request(
        kind: WorldKind,
        prompt: String = "Describe the scene",
        firstFrame: String = "https://example.com/first.png",
        resolution: StoryResolution = StoryResolution.P720,
        references: List<String> = emptyList(),
        creationModel: StoryCreationModel = StoryCreationModel.Simple,
        scriptList: ScriptListPayload? = null,
    ): CreateWorldRequest = buildCreateWorldRequest(
        kind = kind, prompt = prompt, firstFrameImageUrl = firstFrame,
        cameraView = CameraView.FirstPerson, resolution = resolution,
        inputImages = references, creationModel = creationModel, scriptList = scriptList,
    )
}
