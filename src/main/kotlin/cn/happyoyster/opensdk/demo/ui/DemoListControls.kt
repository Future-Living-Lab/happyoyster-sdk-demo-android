package cn.happyoyster.opensdk.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R

@Composable
internal fun DemoListControls(
    modeFilter: DemoModeFilter,
    timeOrder: DemoTimeOrder,
    onModeFilterChange: (DemoModeFilter) -> Unit,
    onTimeOrderChange: (DemoTimeOrder) -> Unit,
) {
    val filterLabel = stringResource(R.string.list_filter_label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val segmentWidth = ((maxWidth - 20.dp) / 4).coerceAtLeast(60.dp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DemoModeFilter.entries.forEach { option ->
                        val label = stringResource(option.labelRes())
                        val isSelected = option == modeFilter
                        Surface(
                            onClick = { onModeFilterChange(option) },
                            modifier = Modifier.width(segmentWidth).defaultMinSize(minHeight = 44.dp)
                                .semantics {
                                    selected = isSelected
                                    contentDescription = "$filterLabel: $label"
                                },
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        ) {
                            Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        }
        Box {
            var expanded by remember { mutableStateOf(false) }
            val sortLabel = stringResource(R.string.list_sort_label)
            // Resolve text outside the popup so the app language also applies to the menu.
            val options = DemoTimeOrder.entries.map {
                it to stringResource(if (it == DemoTimeOrder.NewestFirst) R.string.list_newest_first else R.string.list_oldest_first)
            }
            val selectedLabel = options.first { it.first == timeOrder }.second
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "$sortLabel: $selectedLabel"
                    },
                ) {
                    val iconColor = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(22.dp)) {
                        val stroke = 1.6.dp.toPx()
                        listOf(0.25f to 0.5f, 0.5f to 0.4f, 0.75f to 0.3f).forEach { (y, end) ->
                            drawLine(iconColor, Offset(size.width * 0.1f, size.height * y), Offset(size.width * end, size.height * y), stroke, StrokeCap.Round)
                        }
                        val x = size.width * 0.75f
                        val tip = size.height * if (timeOrder == DemoTimeOrder.NewestFirst) 0.8f else 0.2f
                        val wing = size.height * if (timeOrder == DemoTimeOrder.NewestFirst) 0.6f else 0.4f
                        drawLine(iconColor, Offset(x, size.height * 0.2f), Offset(x, size.height * 0.8f), stroke, StrokeCap.Round)
                        drawLine(iconColor, Offset(size.width * 0.6f, wing), Offset(x, tip), stroke, StrokeCap.Round)
                        drawLine(iconColor, Offset(x, tip), Offset(size.width * 0.9f, wing), stroke, StrokeCap.Round)
                    }
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                maxLines = 1,
                                color = if (option == timeOrder) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (option == timeOrder) FontWeight.Bold
                                    else FontWeight.Normal,
                            )
                        },
                        modifier = Modifier.semantics { selected = option == timeOrder },
                        onClick = { expanded = false; onTimeOrderChange(option) },
                    )
                }
            }
        }
    }
}

private fun DemoModeFilter.labelRes(): Int = when (this) {
    DemoModeFilter.All -> R.string.list_all_modes
    DemoModeFilter.Wander -> R.string.mode_wander
    DemoModeFilter.Story -> R.string.mode_story
    DemoModeFilter.Acting -> R.string.mode_acting
}
