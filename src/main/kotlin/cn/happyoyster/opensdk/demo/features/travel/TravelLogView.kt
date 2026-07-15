package cn.happyoyster.opensdk.demo.features.travel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.SdkDemoLog
import cn.happyoyster.opensdk.demo.app.SdkDemoLogEntry
import cn.happyoyster.opensdk.demo.app.SdkDemoLogKind

@Composable
internal fun TravelLogView(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    LaunchedEffect(SdkDemoLog.entries.size) {
        if (expanded && SdkDemoLog.entries.isNotEmpty()) {
            listState.animateScrollToItem(SdkDemoLog.entries.lastIndex)
        }
    }
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sdk_call_log), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        enabled = SdkDemoLog.entries.isNotEmpty(),
                        onClick = {
                            context.copyLogToClipboard(SdkDemoLog.entries.toClipboardText())
                        },
                    ) {
                        Text(stringResource(R.string.copy_log))
                    }
                    TextButton(onClick = SdkDemoLog::clear) {
                        Text(stringResource(R.string.clear_log))
                    }
                    TextButton(onClick = onToggleExpanded) {
                        Text(
                            stringResource(
                                if (expanded) {
                                    R.string.collapse_log
                                } else {
                                    R.string.expand_log
                                },
                            ),
                        )
                    }
                }
            }
            if (expanded) {
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(SdkDemoLog.entries) { entry ->
                        TravelLogEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelLogEntryRow(entry: SdkDemoLogEntry) {
    val (icon, color) = when (entry.kind) {
        SdkDemoLogKind.SDK_CALL -> "*" to Color(0xFF1565C0)
        SdkDemoLogKind.SDK_RESULT -> "+" to Color(0xFF2E7D32)
        SdkDemoLogKind.SDK_EVENT -> "!" to Color(0xFFF57F17)
        SdkDemoLogKind.ERROR -> "x" to MaterialTheme.colorScheme.error
        SdkDemoLogKind.GATEWAY -> "-" to Color(0xFF757575)
        SdkDemoLogKind.INFO -> "." to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = "$icon ${entry.label}",
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
        entry.detail?.let { detail ->
            Text(
                text = "  $detail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun List<SdkDemoLogEntry>.toClipboardText(): String =
    joinToString(separator = "\n") { entry ->
        buildString {
            append("[")
            append(entry.kind.name)
            append("] ")
            append(entry.label)
            entry.detail?.let { detail ->
                append(" | ")
                append(detail)
            }
        }
    }

private fun Context.copyLogToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Happy Oyster SDK call log", text))
}
