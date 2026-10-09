package cn.happyoyster.opensdk.demo.app

import android.content.Context
import cn.happyoyster.opensdk.demo.gateway.ScriptAct
import cn.happyoyster.opensdk.demo.gateway.ScriptListPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bundled 45-turn scripts for world creation and travel updates. */
@Serializable
internal data class ScriptListPreset(
    val id: String,
    val name: String,
    val scenario: String,
    val firstFrameImageUrl: String,
    val scriptList: ScriptListPayload,
) {
    val actCount: Int get() = scriptList.acts.size
    val subjectDisplayNames: List<String>
        get() = scriptList.subjects.orEmpty().mapIndexed { index, subject ->
            subject.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: subject.label?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Subject ${index + 1}"
        }
}

internal object ScriptListPresets {
    private const val ASSET_NAME = "scriptlist_presets.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<ScriptListPreset> =
        context.assets.open(ASSET_NAME).use { stream ->
            json.decodeFromString<List<ScriptListPreset>>(stream.reader().readText())
        }
}

internal object ScriptListDrafts {
    private const val MAX_ACT_COUNT = 45
    private const val MAX_SUBJECT_COUNT = 6
    private val allowedCameraTypes = setOf("Static", "Push-in", "Pull-out", "Pan Right", "Tilt Up", "Tracking")
    private val allowedShotSizes = setOf("Close-up", "Medium", "Wide")
    private val allowedCuts = setOf("hard-cut", "long-take")
    private val allowedSubjectTypes = setOf("character", "animal", "creature", "narrator")
    private val subjectLabelRegex = Regex("\\[character_[1-6]]")
    private val subjectReferenceRegex = Regex("\\[character_\\d+]")

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        prettyPrint = true
    }

    fun encode(scriptList: ScriptListPayload): String =
        json.encodeToString(scriptList)

    fun parse(text: String): ScriptListPayload =
        json.decodeFromString(text)

    fun isValidCreateDraft(text: String): Boolean =
        runCatching {
            parse(text).isValid(requireSubjects = true, requireFullTurns = true)
        }.getOrDefault(false)

    fun isValidFullUpdateDraft(text: String): Boolean =
        runCatching {
            parse(text).isValid(requireSubjects = false, requireFullTurns = true)
        }.getOrDefault(false)

    private fun ScriptListPayload.isValid(requireSubjects: Boolean, requireFullTurns: Boolean): Boolean {
        val subjectLabels = subjects.orEmpty().map { it.label.orEmpty().trim() }
        if (requireSubjects && subjectLabels.isEmpty()) return false
        if (subjectLabels.size > MAX_SUBJECT_COUNT) return false
        if (subjectLabels.any { !subjectLabelRegex.matches(it) }) return false
        if (subjectLabels.distinct().size != subjectLabels.size) return false
        if (subjects.orEmpty().any { subject ->
                !subject.type.isNullOrAllowed(allowedSubjectTypes) ||
                    !subject.name.withinCodePoints(64) || !subject.gender.withinCodePoints(64) ||
                    !subject.age.withinCodePoints(64) || !subject.ethnicity.withinCodePoints(64) ||
                    !subject.position.withinCodePoints(64) || !subject.appearance.withinCodePoints(500) ||
                    !subject.voice.withinCodePoints(200)
            }
        ) return false
        if (!videoTitle.withinCodePoints(128) || !synopsis.withinCodePoints(2000)) return false

        val requiredActCounts = if (requireFullTurns) MAX_ACT_COUNT..MAX_ACT_COUNT else 1..MAX_ACT_COUNT
        if (acts.size !in requiredActCounts) return false
        if (acts.withIndex().any { (index, act) -> act.turn != index + 1 }) return false

        val subjectLabelSet = subjectLabels.toSet()
        return acts.all { act ->
            act.content.isNotBlank() && act.content.withinCodePoints(2000) &&
                act.cameraType.isNullOrAllowed(allowedCameraTypes) &&
                act.shotSize.isNullOrAllowed(allowedShotSizes) &&
                act.cut.isNullOrAllowed(allowedCuts) &&
                act.referencedSubjectsExistIn(subjectLabelSet)
        }
    }

    private fun String?.isNullOrAllowed(allowedValues: Set<String>): Boolean =
        this == null || trim() in allowedValues

    private fun String?.withinCodePoints(max: Int): Boolean =
        this == null || codePointCount(0, length) <= max

    private fun ScriptAct.referencedSubjectsExistIn(subjectLabels: Set<String>): Boolean {
        if (subjectLabels.isEmpty()) return true
        return subjectReferenceRegex.findAll(content)
            .map { it.value }
            .all { it in subjectLabels }
    }
}
