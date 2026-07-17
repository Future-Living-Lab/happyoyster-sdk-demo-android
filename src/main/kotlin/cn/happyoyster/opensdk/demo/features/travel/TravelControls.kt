package cn.happyoyster.opensdk.demo.features.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import cn.happyoyster.opensdk.AdventureCommand
import cn.happyoyster.opensdk.CreationModelValue
import cn.happyoyster.opensdk.ModeValue
import cn.happyoyster.opensdk.TravelStatusValue
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.ActiveTravel
import cn.happyoyster.opensdk.demo.app.ScriptListDrafts
import cn.happyoyster.opensdk.demo.app.ScriptListPreset
import cn.happyoyster.opensdk.demo.ui.ScriptListPresetPicker
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive

private enum class AdventureCommandDimension {
    Translation,
    Rotation,
    Interaction,
}

private data class ActiveAdventureInput(
    val dimension: AdventureCommandDimension,
    val value: String,
)

private data class TravelControlAction(
    val labelRes: Int,
    val dimension: AdventureCommandDimension,
    val value: String,
    val iconRes: Int? = null,
)

private data class TravelControlSection(
    val titleRes: Int,
    val leadAction: TravelControlAction,
    val rowActions: List<TravelControlAction>,
)

private const val COMMAND_NONE = "None"
private const val ADVENTURE_TAP_MAX_MS = 180L

private val travelCommandSections = listOf(
    TravelControlSection(
        titleRes = R.string.adventure_translation,
        leadAction = TravelControlAction(
            R.string.forward,
            AdventureCommandDimension.Translation,
            "W",
        ),
        rowActions = listOf(
            TravelControlAction(R.string.left, AdventureCommandDimension.Translation, "A"),
            TravelControlAction(R.string.back, AdventureCommandDimension.Translation, "S"),
            TravelControlAction(R.string.right, AdventureCommandDimension.Translation, "D"),
        ),
    ),
    TravelControlSection(
        titleRes = R.string.adventure_rotation,
        leadAction = TravelControlAction(
            R.string.rotate_up,
            AdventureCommandDimension.Rotation,
            "Mouse_Up",
        ),
        rowActions = listOf(
            TravelControlAction(
                R.string.rotate_left,
                AdventureCommandDimension.Rotation,
                "Mouse_Left",
            ),
            TravelControlAction(
                R.string.rotate_down,
                AdventureCommandDimension.Rotation,
                "Mouse_Down",
            ),
            TravelControlAction(
                R.string.rotate_right,
                AdventureCommandDimension.Rotation,
                "Mouse_Right",
            ),
        ),
    ),
)

private val travelInteractionActions = listOf(
    TravelControlAction(
        R.string.attack,
        AdventureCommandDimension.Interaction,
        "Attack",
        R.drawable.ic_wander_attack,
    ),
    TravelControlAction(
        R.string.squat,
        AdventureCommandDimension.Interaction,
        "Squat",
        R.drawable.ic_wander_crouch,
    ),
    TravelControlAction(
        R.string.jump,
        AdventureCommandDimension.Interaction,
        "Jump",
        R.drawable.ic_wander_jump,
    ),
    TravelControlAction(
        R.string.sprint,
        AdventureCommandDimension.Interaction,
        "Sprint",
        R.drawable.ic_wander_sprint,
    ),
)

@Composable
internal fun TravelControls(
    modifier: Modifier = Modifier,
    travel: ActiveTravel,
    status: TravelStatusValue?,
    pausing: Boolean,
    ending: Boolean,
    capHeight: Boolean = true,
    directingInstruct: String,
    rewindToSec: String,
    scriptListPresets: List<ScriptListPreset>,
    updateScriptPresetId: String?,
    updateScriptDraft: String,
    updatingScript: Boolean,
    onInstructChange: (String) -> Unit,
    onRewindChange: (String) -> Unit,
    onCommand: (AdventureCommand, Boolean) -> Boolean,
    onSendInstruct: () -> Unit,
    onUpdatePresetSelect: (ScriptListPreset) -> Unit,
    onUpdateScriptDraftChange: (String) -> Unit,
    onUpdateScript: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRewind: () -> Unit,
    onEnd: () -> Unit,
) {
    val isDirecting = travel.data.mode == ModeValue.Directing
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (capHeight) Modifier.heightIn(max = 300.dp) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.travel_title), style = MaterialTheme.typography.titleMedium)
            Text(
                travel.world.displayName ?: stringResource(R.string.world_generating_title),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${stringResource(R.string.travel_mode, travel.data.mode.rawValue)} · " +
                    stringResource(R.string.travel_status, travelStatusText(pausing, status)),
            )
            if (isDirecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPause,
                        enabled = status == TravelStatusValue.Running && !pausing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (pausing) R.string.travel_status_pausing else R.string.pause,
                            ),
                        )
                    }
                    Button(
                        onClick = onResume,
                        enabled = status == TravelStatusValue.Paused,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.resume))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rewindSecValid = rewindToSec.isValidRewindToSecInput()
                    OutlinedTextField(
                        value = rewindToSec,
                        onValueChange = onRewindChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.rewind_sec)) },
                        singleLine = true,
                        isError = rewindToSec.isNotBlank() && !rewindSecValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text(stringResource(R.string.rewind_sec_hint)) },
                    )
                    Button(
                        onClick = onRewind,
                        enabled = status == TravelStatusValue.Paused && rewindSecValid,
                    ) {
                        Text(stringResource(R.string.rewind))
                    }
                }
            }
            if (travel.data.mode == ModeValue.Adventure) {
                DirectionPad(
                    enabled = status == TravelStatusValue.Running && !ending,
                    onCommand = onCommand,
                )
            } else if (travel.data.creationModel == CreationModelValue.ScriptList) {
                // ScriptList worlds do not accept instruct; the script is
                // replaced as a whole (exactly 45 turns) via update-script.
                Text(
                    text = stringResource(R.string.script_list_instruct_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.script_list_update_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScriptListPresetPicker(
                    presets = scriptListPresets,
                    selectedPresetId = updateScriptPresetId,
                    onSelect = onUpdatePresetSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                val updateDraftValid = ScriptListDrafts.isValidFullUpdateDraft(updateScriptDraft)
                OutlinedTextField(
                    value = updateScriptDraft,
                    onValueChange = onUpdateScriptDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    minLines = 6,
                    maxLines = 10,
                    isError = updateScriptDraft.isNotBlank() && !updateDraftValid,
                    label = { Text(stringResource(R.string.script_list_editor_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (updateDraftValid) {
                                    R.string.update_script_editor_help
                                } else {
                                    R.string.update_script_editor_invalid
                                },
                            ),
                        )
                    },
                )
                Button(
                    onClick = onUpdateScript,
                    enabled = updateDraftValid &&
                        !updatingScript &&
                        (status == TravelStatusValue.Running || status == TravelStatusValue.Pending),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (updatingScript) R.string.update_script_updating else R.string.update_script_button,
                        ),
                    )
                }
            } else {
                OutlinedTextField(
                    value = directingInstruct,
                    onValueChange = onInstructChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.directing_instruct)) },
                )
                Button(
                    onClick = onSendInstruct,
                    enabled = directingInstruct.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.send_instruct))
                }
            }
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onEnd,
                enabled = !ending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.end_travel))
            }
        }
    }
}

@Composable
private fun DirectionPad(
    enabled: Boolean,
    onCommand: (AdventureCommand, Boolean) -> Boolean,
) {
    var activeValues by remember {
        mutableStateOf<Map<AdventureCommandDimension, List<String>>>(emptyMap())
    }
    var activeHolds by remember { mutableStateOf<Set<ActiveAdventureInput>>(emptySet()) }
    val currentCommand = activeValues.toAdventureCommand()
    val latestCommand by rememberUpdatedState(currentCommand)
    val latestOnCommand by rememberUpdatedState(onCommand)

    LaunchedEffect(activeHolds) {
        if (activeHolds.isEmpty()) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            if (!latestOnCommand(latestCommand, false)) break
        }
    }

    fun releaseValue(
        dimension: AdventureCommandDimension,
        value: String,
    ): Boolean {
        val currentValues = activeValues[dimension].orEmpty()
        if (value !in currentValues) return false

        val nextValues = currentValues.filterNot { it == value }
        activeValues = if (nextValues.isEmpty()) {
            activeValues - dimension
        } else {
            activeValues + (dimension to nextValues)
        }
        return true
    }

    fun updateCommand(
        dimension: AdventureCommandDimension,
        value: String,
        phase: HoldPhase,
    ): Boolean {
        val input = ActiveAdventureInput(dimension, value)
        return when (phase) {
            HoldPhase.Down -> {
                val currentValues = activeValues[dimension].orEmpty()
                val nextValues = currentValues.filterNot { it == value } + value
                activeValues = activeValues + (dimension to nextValues)
                val succeeded = onCommand(activeValues.toAdventureCommand(), true)
                if (!succeeded) releaseValue(dimension, value)
                succeeded
            }
            HoldPhase.HoldStart -> {
                activeHolds = activeHolds + input
                true
            }
            HoldPhase.Release -> {
                activeHolds = activeHolds - input
                if (!releaseValue(dimension, value)) return true
                onCommand(activeValues.toAdventureCommand(), true)
            }
            HoldPhase.Clear -> {
                activeHolds = activeHolds - input
                releaseValue(dimension, value)
                true
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        travelCommandSections.forEach { section ->
            TravelCommandSection(
                section = section,
                enabled = enabled,
                onCommandUpdate = ::updateCommand,
            )
        }
        Text(stringResource(R.string.adventure_interaction), fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            travelInteractionActions.forEach { action ->
                TravelInteractionButton(
                    action = action,
                    enabled = enabled,
                    onCommandUpdate = ::updateCommand,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TravelCommandSection(
    section: TravelControlSection,
    enabled: Boolean,
    onCommandUpdate: AdventureCommandUpdate,
) {
    Text(stringResource(section.titleRes), fontWeight = FontWeight.SemiBold)
    TravelCommandButton(
        action = section.leadAction,
        enabled = enabled,
        onCommandUpdate = onCommandUpdate,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        section.rowActions.forEach { action ->
            TravelCommandButton(
                action = action,
                enabled = enabled,
                onCommandUpdate = onCommandUpdate,
            )
        }
    }
}

@Composable
private fun TravelCommandButton(
    action: TravelControlAction,
    enabled: Boolean,
    onCommandUpdate: AdventureCommandUpdate,
) {
    TapOrHoldButton(
        action = action,
        enabled = enabled,
        onCommandUpdate = onCommandUpdate,
        outlined = false,
    )
}

@Composable
private fun TravelInteractionButton(
    action: TravelControlAction,
    enabled: Boolean,
    onCommandUpdate: AdventureCommandUpdate,
    modifier: Modifier = Modifier,
) {
    val iconRes = requireNotNull(action.iconRes)
    TapOrHoldButton(
        action = action,
        enabled = enabled,
        onCommandUpdate = onCommandUpdate,
        outlined = true,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun List<String>.toTranslationCommand(): String {
    val vertical = lastOrNull { it == "W" || it == "S" }
    val horizontal = lastOrNull { it == "A" || it == "D" }
    return if (vertical != null && horizontal != null) {
        "${vertical}_$horizontal"
    } else {
        vertical ?: horizontal ?: COMMAND_NONE
    }
}

private fun List<String>.toRotationCommand(): String {
    val vertical = lastOrNull { it == "Mouse_Up" || it == "Mouse_Down" }
    val horizontal = lastOrNull { it == "Mouse_Left" || it == "Mouse_Right" }
    return if (vertical != null && horizontal != null) {
        "${vertical}_${horizontal.removePrefix("Mouse_")}"
    } else {
        vertical ?: horizontal ?: COMMAND_NONE
    }
}

private fun Map<AdventureCommandDimension, List<String>>.toAdventureCommand(): AdventureCommand =
    AdventureCommand(
        translation = this[AdventureCommandDimension.Translation].orEmpty().toTranslationCommand(),
        rotation = this[AdventureCommandDimension.Rotation].orEmpty().toRotationCommand(),
        interaction = this[AdventureCommandDimension.Interaction].orEmpty().lastOrNull()
            ?: COMMAND_NONE,
    )

private typealias AdventureCommandUpdate = (
    AdventureCommandDimension,
    String,
    HoldPhase,
) -> Boolean

private enum class HoldPhase {
    Down,
    HoldStart,
    Release,
    Clear,
}

@Composable
private fun TapOrHoldButton(
    action: TravelControlAction,
    enabled: Boolean,
    onCommandUpdate: AdventureCommandUpdate,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    val latestOnCommandUpdate by rememberUpdatedState(onCommandUpdate)

    fun sendTap(): Boolean {
        val succeeded = latestOnCommandUpdate(
            action.dimension,
            action.value,
            HoldPhase.Down,
        )
        latestOnCommandUpdate(action.dimension, action.value, HoldPhase.Clear)
        return succeeded
    }

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(action.dimension, action.value) {
            detectTapGestures(
                onPress = {
                    var settled = false
                    try {
                        val downOk = latestOnCommandUpdate(
                            action.dimension,
                            action.value,
                            HoldPhase.Down,
                        )
                        val tapRelease =
                            withTimeoutOrNull(ADVENTURE_TAP_MAX_MS) { tryAwaitRelease() }
                        if (tapRelease != null) {
                            latestOnCommandUpdate(
                                action.dimension,
                                action.value,
                            HoldPhase.Clear,
                            )
                        } else {
                        if (downOk) {
                            latestOnCommandUpdate(
                                action.dimension,
                                action.value,
                                HoldPhase.HoldStart,
                                )
                            }
                        val released = tryAwaitRelease()
                        latestOnCommandUpdate(
                            action.dimension,
                            action.value,
                            if (released && downOk) HoldPhase.Release else HoldPhase.Clear,
                        )
                        }
                        settled = true
                    } finally {
                        if (!settled) {
                            latestOnCommandUpdate(
                                action.dimension,
                                action.value,
                                HoldPhase.Clear,
                            )
                        }
                    }
                },
            )
        }
    } else {
        Modifier
    }

    val buttonContent: @Composable () -> Unit = content ?: {
        Text(stringResource(action.labelRes))
    }
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                role = Role.Button
                if (enabled) {
                    onClick { sendTap() }
                } else {
                    disabled()
                }
            }
            .then(gestureModifier),
        shape = if (outlined) MaterialTheme.shapes.medium else MaterialTheme.shapes.small,
        color = if (outlined) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.primary
        },
        contentColor = if (outlined) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onPrimary
        }.copy(alpha = if (enabled) 1f else 0.38f),
        border = if (outlined) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
        tonalElevation = if (outlined) 0.dp else 2.dp,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = if (outlined) 4.dp else 24.dp,
                vertical = if (outlined) 8.dp else 10.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            buttonContent()
        }
    }
}

@Composable
private fun travelStatusText(pausing: Boolean, status: TravelStatusValue?): String =
    when {
        pausing -> stringResource(R.string.travel_status_pausing)
        status == null -> stringResource(R.string.travel_status_init)
        else -> status.rawValue
    }

/** rewindToSec must be a positive multiple of 4 per SDK / server contract. */
private fun String.isValidRewindToSecInput(): Boolean {
    val value = trim().toDoubleOrNull() ?: return false
    return value > 0.0 && value % 4.0 == 0.0
}
