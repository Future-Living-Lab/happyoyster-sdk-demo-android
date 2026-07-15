package cn.happyoyster.opensdk.demo.features.create

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.CreateWorldForm
import cn.happyoyster.opensdk.demo.app.ScriptListDrafts
import cn.happyoyster.opensdk.demo.app.ScriptListPreset
import cn.happyoyster.opensdk.demo.gateway.CameraView
import cn.happyoyster.opensdk.demo.gateway.StoryCreationModel
import cn.happyoyster.opensdk.demo.gateway.StoryResolution
import cn.happyoyster.opensdk.demo.gateway.WanderUploadMode
import cn.happyoyster.opensdk.demo.gateway.WorldKind
import cn.happyoyster.opensdk.demo.ui.OptionRow
import cn.happyoyster.opensdk.demo.ui.ScriptListPresetPicker
import cn.happyoyster.opensdk.demo.ui.SelectButton
import cn.happyoyster.opensdk.demo.ui.isHttpUrl

private const val MAX_PROMPT_LENGTH = 2000

@Composable
internal fun CreateTab(
    form: CreateWorldForm,
    scriptListPresets: List<ScriptListPreset>,
    onFormChange: (CreateWorldForm) -> Unit,
    onCreate: () -> Unit,
) {
    val isScenarioRole = form.worldKind == WorldKind.Wander && form.wanderUploadMode == WanderUploadMode.ScenarioRole
    val isScriptList = form.worldKind == WorldKind.Story && form.storyCreationModel == StoryCreationModel.ScriptList
    val imageInputValid = form.firstFrameImageUrl.isValidWorldImageInput()
    val sceneImageInputValid = form.sceneImageUrl.isValidWorldImageInput()
    val roleImageInputValid = form.roleImageUrl.isValidWorldImageInput()
    val promptLength = form.prompt.trim().promptLength()
    val scenePromptLength = form.scenePrompt.trim().promptLength()
    val rolePromptLength = form.rolePrompt.trim().promptLength()
    val promptValid = promptLength in 1..MAX_PROMPT_LENGTH
    val scenarioPromptsValid = scenePromptLength <= MAX_PROMPT_LENGTH && rolePromptLength <= MAX_PROMPT_LENGTH
    val scenarioHasPrompt = form.scenePrompt.isNotBlank() || form.rolePrompt.isNotBlank()
    val scenarioHasBothImages = form.sceneImageUrl.isNotBlank() && form.roleImageUrl.isNotBlank()
    val synopsisLength = form.scriptListSynopsis.trim().promptLength()
    val scriptListDraftValid = !isScriptList || ScriptListDrafts.isValidCreateDraft(form.scriptListDraft)
    val canCreate = when {
        isScenarioRole ->
            sceneImageInputValid && roleImageInputValid && scenarioPromptsValid &&
                (scenarioHasPrompt || scenarioHasBothImages)
        // ScriptList presets provide a default first-frame image URL; users can edit it.
        isScriptList ->
            form.scriptListPresetId != null &&
                synopsisLength in 1..MAX_PROMPT_LENGTH &&
                scriptListDraftValid &&
                form.firstFrameImageUrl.isNotBlank() && imageInputValid
        else -> imageInputValid && promptValid
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.create_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.create_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
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
                            onFormChange(form.copy(worldKind = WorldKind.Story).clearScriptListFields())
                        }
                    }
                }
                if (form.worldKind == WorldKind.Wander) {
                    OptionRow(stringResource(R.string.upload_mode_label)) {
                        SelectButton(
                            stringResource(R.string.upload_first_frame),
                            form.wanderUploadMode == WanderUploadMode.FirstFrame,
                        ) {
                            onFormChange(form.copy(wanderUploadMode = WanderUploadMode.FirstFrame))
                        }
                        SelectButton(
                            stringResource(R.string.upload_scenario_role),
                            form.wanderUploadMode == WanderUploadMode.ScenarioRole,
                        ) {
                            onFormChange(form.copy(wanderUploadMode = WanderUploadMode.ScenarioRole))
                        }
                    }
                    OptionRow(stringResource(R.string.camera_label)) {
                        CameraView.entries.forEach { item ->
                            SelectButton(item.labelResource(), form.cameraView == item) {
                                onFormChange(form.copy(cameraView = item))
                            }
                        }
                    }
                }
                if (form.worldKind == WorldKind.Story) {
                    OptionRow(stringResource(R.string.resolution_label)) {
                        StoryResolution.entries.forEach { item ->
                            SelectButton(item.label, form.resolution == item) {
                                onFormChange(form.copy(resolution = item))
                            }
                        }
                    }
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
    if (isScenarioRole) {
        Text(
            text = stringResource(R.string.scenario_role_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScenarioRoleFields(
            form = form,
            scenePromptLength = scenePromptLength,
            sceneImageInputValid = sceneImageInputValid,
            rolePromptLength = rolePromptLength,
            roleImageInputValid = roleImageInputValid,
            onFormChange = onFormChange,
        )
    } else if (isScriptList) {
        ScriptListFields(
            form = form,
            presets = scriptListPresets,
            synopsisLength = synopsisLength,
            scriptListDraftValid = scriptListDraftValid,
            imageInputValid = imageInputValid,
            onFormChange = onFormChange,
        )
    } else {
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
            OutlinedTextField(
                value = form.firstFrameImageUrl,
                onValueChange = { onFormChange(form.copy(firstFrameImageUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !imageInputValid,
                label = { Text(stringResource(R.string.first_frame_image_input_label)) },
                supportingText = {
                    Text(stringResource(worldImageInputHintRes(form.firstFrameImageUrl, R.string.first_frame_image_input_optional)))
                },
            )
        }
        Button(
            onClick = onCreate,
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_world_button))
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
    OutlinedTextField(
        value = form.firstFrameImageUrl,
        onValueChange = { onFormChange(form.copy(firstFrameImageUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = form.firstFrameImageUrl.isBlank() || !imageInputValid,
        label = { Text(stringResource(R.string.first_frame_image_input_label)) },
        supportingText = {
            Text(
                stringResource(
                    if (form.firstFrameImageUrl.isBlank()) {
                        R.string.script_list_first_frame_required
                    } else {
                        worldImageInputHintRes(form.firstFrameImageUrl, R.string.script_list_first_frame_editable)
                    },
                ),
            )
        },
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
private fun ScenarioRoleFields(
    form: CreateWorldForm,
    scenePromptLength: Int,
    sceneImageInputValid: Boolean,
    rolePromptLength: Int,
    roleImageInputValid: Boolean,
    onFormChange: (CreateWorldForm) -> Unit,
) {
    OutlinedTextField(
        value = form.scenePrompt,
        onValueChange = { onFormChange(form.copy(scenePrompt = it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        isError = scenePromptLength > MAX_PROMPT_LENGTH,
        label = { Text(stringResource(R.string.scene_prompt_label)) },
        supportingText = {
            Text(stringResource(R.string.characters_count_limit, scenePromptLength, MAX_PROMPT_LENGTH))
        },
    )
    OutlinedTextField(
        value = form.sceneImageUrl,
        onValueChange = { onFormChange(form.copy(sceneImageUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = !sceneImageInputValid,
        label = { Text(stringResource(R.string.scene_image_input_label)) },
        supportingText = {
            Text(stringResource(worldImageInputHintRes(form.sceneImageUrl, R.string.scenario_image_input_optional)))
        },
    )
    OutlinedTextField(
        value = form.rolePrompt,
        onValueChange = { onFormChange(form.copy(rolePrompt = it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        isError = rolePromptLength > MAX_PROMPT_LENGTH,
        label = { Text(stringResource(R.string.role_prompt_label)) },
        supportingText = {
            Text(stringResource(R.string.characters_count_limit, rolePromptLength, MAX_PROMPT_LENGTH))
        },
    )
    OutlinedTextField(
        value = form.roleImageUrl,
        onValueChange = { onFormChange(form.copy(roleImageUrl = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = !roleImageInputValid,
        label = { Text(stringResource(R.string.role_image_input_label)) },
        supportingText = {
            Text(stringResource(worldImageInputHintRes(form.roleImageUrl, R.string.scenario_image_input_optional)))
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
