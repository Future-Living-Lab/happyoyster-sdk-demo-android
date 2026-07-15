package cn.happyoyster.opensdk.demo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.ScriptListPreset

/** Dropdown selector for the bundled 45-turn ScriptList presets. */
@Composable
internal fun ScriptListPresetPicker(
    presets: List<ScriptListPreset>,
    selectedPresetId: String?,
    onSelect: (ScriptListPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = presets.firstOrNull { it.id == selectedPresetId }
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = presets.isNotEmpty(),
        ) {
            Text(
                text = when {
                    presets.isEmpty() -> stringResource(R.string.script_list_presets_missing)
                    selected == null -> stringResource(R.string.script_list_preset_placeholder)
                    else -> stringResource(
                        R.string.script_list_preset_option,
                        selected.name,
                        selected.scenario,
                        selected.actCount,
                    )
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.script_list_preset_option,
                                    preset.name,
                                    preset.scenario,
                                    preset.actCount,
                                ),
                            )
                            Text(
                                text = preset.subjectSummaryText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    },
                )
            }
        }
        if (selected != null) {
            Text(
                text = selected.subjectSummaryText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScriptListPreset.subjectSummaryText(): String =
    stringResource(
        R.string.script_list_subject_summary,
        subjectDisplayNames.size,
        subjectDisplayNames.joinToString("、"),
    )
