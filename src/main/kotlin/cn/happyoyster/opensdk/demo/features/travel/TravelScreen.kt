package cn.happyoyster.opensdk.demo.features.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.AdventureCommand
import cn.happyoyster.opensdk.TravelStatusValue
import cn.happyoyster.opensdk.demo.app.ActiveTravel
import cn.happyoyster.opensdk.demo.app.ScriptListPreset
import cn.happyoyster.opensdk.demo.ui.ErrorBanner

@Composable
internal fun TravelScreen(
    errorMessage: String?,
    travel: ActiveTravel,
    status: TravelStatusValue?,
    pausing: Boolean,
    ending: Boolean,
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
    var logExpanded by rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ErrorBanner(errorMessage)
        TravelVideoView(travel.videoView)
        TravelLogView(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (logExpanded) Modifier.weight(1f) else Modifier),
            expanded = logExpanded,
            onToggleExpanded = { logExpanded = !logExpanded },
        )
        TravelControls(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (logExpanded) Modifier else Modifier.weight(1f)),
            travel = travel,
            status = status,
            pausing = pausing,
            ending = ending,
            capHeight = logExpanded,
            directingInstruct = directingInstruct,
            rewindToSec = rewindToSec,
            scriptListPresets = scriptListPresets,
            updateScriptPresetId = updateScriptPresetId,
            updateScriptDraft = updateScriptDraft,
            updatingScript = updatingScript,
            onInstructChange = onInstructChange,
            onRewindChange = onRewindChange,
            onCommand = onCommand,
            onSendInstruct = onSendInstruct,
            onUpdatePresetSelect = onUpdatePresetSelect,
            onUpdateScriptDraftChange = onUpdateScriptDraftChange,
            onUpdateScript = onUpdateScript,
            onPause = onPause,
            onResume = onResume,
            onRewind = onRewind,
            onEnd = onEnd,
        )
    }
}
