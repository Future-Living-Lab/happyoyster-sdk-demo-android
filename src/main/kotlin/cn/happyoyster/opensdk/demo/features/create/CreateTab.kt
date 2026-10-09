package cn.happyoyster.opensdk.demo.features.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.CreateWorldForm
import cn.happyoyster.opensdk.demo.app.ReferenceImageInput
import cn.happyoyster.opensdk.demo.app.ScriptListDrafts
import cn.happyoyster.opensdk.demo.app.ScriptListPreset
import cn.happyoyster.opensdk.demo.gateway.CameraView
import cn.happyoyster.opensdk.demo.gateway.MAX_STORY_REFERENCE_IMAGES
import cn.happyoyster.opensdk.demo.gateway.StoryCreationModel
import cn.happyoyster.opensdk.demo.gateway.StoryResolution
import cn.happyoyster.opensdk.demo.gateway.WorldKind
import cn.happyoyster.opensdk.demo.ui.OptionRow
import cn.happyoyster.opensdk.demo.ui.DemoButton
import cn.happyoyster.opensdk.demo.ui.DemoCard
import cn.happyoyster.opensdk.demo.ui.DemoOutlinedButton
import cn.happyoyster.opensdk.demo.ui.DemoPageHeader
import cn.happyoyster.opensdk.demo.ui.ScriptListPresetPicker
import cn.happyoyster.opensdk.demo.ui.SelectButton
import cn.happyoyster.opensdk.demo.ui.isHttpUrl

private const val MAX_PROMPT_LENGTH = 2000

@Composable
internal fun CreateTab(
    form: CreateWorldForm,
    scriptListPresets: List<ScriptListPreset>,
    creating: Boolean,
    onFormChange: (CreateWorldForm) -> Unit,
    onCreate: () -> Unit,
) {
    val isScriptList = form.worldKind == WorldKind.Story && form.storyCreationModel == StoryCreationModel.ScriptList
    val isSimpleStory = form.worldKind == WorldKind.Story && !isScriptList
    val referenceImportValidity = remember(form.worldKind, form.storyCreationModel) { mutableStateMapOf<String, Boolean>() }
    val referencesValid = form.referenceImages.size <= MAX_STORY_REFERENCE_IMAGES && form.referenceImages.all {
        it.value.isValidWorldImageInput() && referenceImportValidity[it.id] != false
    }
    var firstFrameImportValid by remember(form.worldKind, form.storyCreationModel, form.firstFrameImageUrl) { mutableStateOf(true) }
    val imageInputValid = form.firstFrameImageUrl.isValidWorldImageInput() && firstFrameImportValid
    val promptLength = form.prompt.trim().promptLength()
    val promptValid = promptLength in 1..MAX_PROMPT_LENGTH
    val synopsisLength = form.scriptListSynopsis.trim().promptLength()
    val scriptListDraftValid = !isScriptList || ScriptListDrafts.isValidCreateDraft(form.scriptListDraft)
    val canCreate = when {
        form.worldKind == WorldKind.Acting ->
            promptValid && form.firstFrameImageUrl.isNotBlank() && imageInputValid
        isScriptList ->
            form.scriptListPresetId != null &&
                synopsisLength in 1..MAX_PROMPT_LENGTH &&
                scriptListDraftValid &&
                form.firstFrameImageUrl.isNotBlank() && imageInputValid
        form.worldKind == WorldKind.Wander -> imageInputValid && promptValid && form.firstFrameImageUrl.isNotBlank()
        else -> referencesValid && promptValid
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DemoPageHeader(
            title = stringResource(R.string.create_title),
            description = stringResource(R.string.create_description),
        )
        DemoCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OptionRow(stringResource(R.string.mode_label)) {
                    SelectButton(stringResource(R.string.mode_wander), form.worldKind == WorldKind.Wander) {
                        if (form.worldKind != WorldKind.Wander) {
                            onFormChange(
                                form.copy(
                                    worldKind = WorldKind.Wander,
                                    storyCreationModel = StoryCreationModel.Simple,
                                ).clearScriptListFields(),
                            )
                        }
                    }
                    SelectButton(stringResource(R.string.mode_story), form.worldKind == WorldKind.Story) {
                        if (form.worldKind != WorldKind.Story) {
                            onFormChange(form.copy(worldKind = WorldKind.Story, storyCreationModel = StoryCreationModel.Simple)
                                .clearScriptListFields())
                        }
                    }
                    SelectButton(stringResource(R.string.mode_acting), form.worldKind == WorldKind.Acting) {
                        if (form.worldKind != WorldKind.Acting) {
                            onFormChange(form.copy(worldKind = WorldKind.Acting, storyCreationModel = StoryCreationModel.Simple)
                                .clearScriptListFields())
                        }
                    }
                }
                if (form.worldKind == WorldKind.Wander) {
                    OptionRow(stringResource(R.string.camera_label)) {
                        CameraView.entries.forEach { item ->
                            SelectButton(item.labelResource(), form.cameraView == item) {
                                onFormChange(form.copy(cameraView = item))
                            }
                        }
                    }
                }
                OptionRow(stringResource(R.string.resolution_label)) {
                    StoryResolution.entries.forEach { item ->
                        SelectButton(item.label, form.resolution == item) {
                            onFormChange(form.copy(resolution = item))
                        }
                    }
                }
                if (form.worldKind == WorldKind.Acting) {
                    OptionRow(stringResource(R.string.acting_aspect_ratio)) {
                        SelectButton("9:16", form.actingAspectRatio == "9:16") {
                            if (form.actingAspectRatio != "9:16") {
                                onFormChange(form.copy(actingAspectRatio = "9:16", firstFrameImageUrl = ""))
                            }
                        }
                        SelectButton("16:9", form.actingAspectRatio == "16:9") {
                            if (form.actingAspectRatio != "16:9") {
                                onFormChange(form.copy(actingAspectRatio = "16:9", firstFrameImageUrl = ""))
                            }
                        }
                    }
                }
                if (form.worldKind == WorldKind.Story) {
                    OptionRow(stringResource(R.string.creation_model_label)) {
                        SelectButton(
                            stringResource(R.string.creation_model_simple),
                            form.storyCreationModel == StoryCreationModel.Simple,
                        ) {
                            if (form.storyCreationModel != StoryCreationModel.Simple) {
                                onFormChange(
                                    form.copy(storyCreationModel = StoryCreationModel.Simple)
                                        .clearScriptListFields(),
                                )
                            }
                        }
                        SelectButton(
                            stringResource(R.string.creation_model_scriptlist),
                            form.storyCreationModel == StoryCreationModel.ScriptList,
                        ) {
                            if (form.storyCreationModel != StoryCreationModel.ScriptList) {
                                onFormChange(
                                    form.copy(storyCreationModel = StoryCreationModel.ScriptList)
                                        .clearScriptListFields(),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isScriptList) {
            ScriptListFields(
                form = form,
                presets = scriptListPresets,
                synopsisLength = synopsisLength,
                scriptListDraftValid = scriptListDraftValid,
                imageInputValid = imageInputValid,
                onImageImportValidityChange = { firstFrameImportValid = it },
                onFormChange = onFormChange,
            )
        } else {
            if (form.worldKind == WorldKind.Acting) {
                Text(
                    text = stringResource(R.string.acting_create_help_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = form.prompt,
                onValueChange = { onFormChange(form.copy(prompt = it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                isError = promptLength > MAX_PROMPT_LENGTH,
                label = { Text(stringResource(R.string.prompt_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (promptLength > MAX_PROMPT_LENGTH) {
                                R.string.characters_count_limit
                            } else {
                                R.string.characters_count_required
                            },
                            promptLength,
                            MAX_PROMPT_LENGTH,
                        ),
                    )
                },
            )
            if (isSimpleStory) {
                Text(
                    stringResource(R.string.story_reference_images_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                form.referenceImages.forEachIndexed { index, reference ->
                    key(reference.id) {
                        DemoCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(stringResource(R.string.story_reference_image_number, index + 1))
                                WorldImageInput(
                                    value = reference.value,
                                    onValueChange = { value ->
                                        onFormChange(form.copy(referenceImages = form.referenceImages.map {
                                            if (it.id == reference.id) it.copy(value = value) else it
                                        }))
                                    },
                                    onImportValidityChange = { referenceImportValidity[reference.id] = it },
                                    isError = !reference.value.isValidWorldImageInput(),
                                    labelRes = R.string.story_reference_image_label,
                                    supportingTextRes = worldImageInputHintRes(
                                        reference.value, R.string.story_reference_image_optional,
                                    ),
                                    validateFirstFrameRatio = false,
                                )
                                TextButton(onClick = {
                                    referenceImportValidity.remove(reference.id)
                                    onFormChange(form.copy(referenceImages = form.referenceImages.filter {
                                        it.id != reference.id
                                    }))
                                }) {
                                    Text(stringResource(R.string.story_reference_image_remove))
                                }
                            }
                        }
                    }
                }
                DemoOutlinedButton(
                    onClick = {
                        onFormChange(form.copy(referenceImages = form.referenceImages + ReferenceImageInput()))
                    },
                    enabled = form.referenceImages.size < MAX_STORY_REFERENCE_IMAGES,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.story_reference_image_add))
                }
            } else {
                key(form.worldKind, form.storyCreationModel, form.actingAspectRatio) {
                    WorldImageInput(
                        value = form.firstFrameImageUrl,
                        onValueChange = { onFormChange(form.copy(firstFrameImageUrl = it)) },
                        onImportValidityChange = { firstFrameImportValid = it },
                        isError = !imageInputValid || form.firstFrameImageUrl.isBlank(),
                        labelRes = R.string.first_frame_image_input_label,
                        supportingTextRes = worldImageInputHintRes(
                            form.firstFrameImageUrl, R.string.first_frame_image_input_required,
                        ),
                        validateFirstFrameRatio = form.worldKind != WorldKind.Acting || form.actingAspectRatio == "16:9",
                        validatePortraitRatio = form.worldKind == WorldKind.Acting && form.actingAspectRatio == "9:16",
                    )
                }
            }
        }
        DemoButton(
            onClick = onCreate,
            enabled = canCreate && !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (creating) R.string.create_world_in_progress else R.string.create_world_button))
        }
    }
}

@Composable
private fun ScriptListFields(
    form: CreateWorldForm,
    presets: List<ScriptListPreset>,
    synopsisLength: Int,
    scriptListDraftValid: Boolean,
    imageInputValid: Boolean,
    onImageImportValidityChange: (Boolean) -> Unit,
    onFormChange: (CreateWorldForm) -> Unit,
) {
    Text(
        text = stringResource(R.string.script_list_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.script_list_preset_label), fontWeight = FontWeight.SemiBold)
    ScriptListPresetPicker(
        presets = presets,
        selectedPresetId = form.scriptListPresetId,
        onSelect = { preset ->
            onFormChange(
                form.copy(
                    scriptListPresetId = preset.id,
                    scriptListSynopsis = preset.scenario,
                    firstFrameImageUrl = preset.firstFrameImageUrl,
                    scriptListDraft = ScriptListDrafts.encode(preset.scriptList),
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.scriptListSynopsis,
        onValueChange = { onFormChange(form.copy(scriptListSynopsis = it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        isError = synopsisLength !in 1..MAX_PROMPT_LENGTH,
        label = { Text(stringResource(R.string.script_list_synopsis_label)) },
        supportingText = {
            Text(stringResource(R.string.characters_count_required, synopsisLength, MAX_PROMPT_LENGTH))
        },
    )
    WorldImageInput(
        value = form.firstFrameImageUrl,
        onValueChange = { onFormChange(form.copy(firstFrameImageUrl = it)) },
        onImportValidityChange = onImageImportValidityChange,
        isError = form.firstFrameImageUrl.isBlank() || !imageInputValid,
        labelRes = R.string.first_frame_image_input_label,
        supportingTextRes = if (form.firstFrameImageUrl.isBlank()) {
            R.string.first_frame_image_input_required
        } else {
            worldImageInputHintRes(form.firstFrameImageUrl, R.string.script_list_first_frame_editable)
        },
        validateFirstFrameRatio = true,
    )
    OutlinedTextField(
        value = form.scriptListDraft,
        onValueChange = { onFormChange(form.copy(scriptListDraft = it)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        minLines = 8,
        maxLines = 12,
        isError = form.scriptListDraft.isNotBlank() && !scriptListDraftValid,
        label = { Text(stringResource(R.string.script_list_editor_label)) },
        supportingText = {
            Text(
                stringResource(
                    if (scriptListDraftValid) {
                        R.string.script_list_editor_help
                    } else {
                        R.string.script_list_editor_invalid
                    },
                ),
            )
        },
    )
}

@Composable
private fun CameraView.labelResource(): String =
    when (this) {
        CameraView.FirstPerson -> stringResource(R.string.camera_first_person)
        CameraView.ThirdPerson -> stringResource(R.string.camera_third_person)
    }

private fun worldImageInputHintRes(input: String, @StringRes optionalRes: Int): Int =
    when (input.validateWorldImageInput()) {
        WorldImageInputValidation.Valid -> optionalRes
        WorldImageInputValidation.Invalid -> R.string.world_image_input_invalid
    }

private fun String.promptLength(): Int = codePointCount(0, length)

private enum class WorldImageInputValidation {
    Valid,
    Invalid,
}

/** Blank is allowed. Non-blank image input can be an http(s) image URL, raw base64, or data URI. */
private fun String.validateWorldImageInput(): WorldImageInputValidation {
    val trimmed = trim()
    if (trimmed.isBlank()) return WorldImageInputValidation.Valid
    if (trimmed.isHttpUrl()) return WorldImageInputValidation.Valid
    if (trimmed.startsWith("data:image/", ignoreCase = true) && ";base64," in trimmed) {
        return WorldImageInputValidation.Valid
    }
    return if (trimmed.isRawBase64()) WorldImageInputValidation.Valid else WorldImageInputValidation.Invalid
}

private fun String.isValidWorldImageInput(): Boolean =
    validateWorldImageInput() == WorldImageInputValidation.Valid

private fun String.isRawBase64(): Boolean {
    val compact = filterNot(Char::isWhitespace)
    return compact.length >= 16 &&
        compact.length % 4 == 0 &&
        compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
}
