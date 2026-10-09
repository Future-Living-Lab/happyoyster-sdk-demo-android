package cn.happyoyster.opensdk.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.app.DemoTab

@Composable
internal fun SdkDemoTabBar(
    selectedTab: DemoTab,
    onSelect: (DemoTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DemoTab.entries.forEach { tab ->
                SdkDemoTabItem(
                    tab = tab,
                    selected = selectedTab == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SdkDemoTabItem(
    tab: DemoTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null,
        ),
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            DemoTabIcon(tab, selected, Modifier.size(20.dp))
            Text(
                text = tab.label(),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DemoTab.label(): String =
    when (this) {
        DemoTab.Create -> stringResource(R.string.tab_create)
        DemoTab.Play -> stringResource(R.string.tab_play)
        DemoTab.History -> stringResource(R.string.tab_history)
        DemoTab.Profile -> stringResource(R.string.tab_config)
    }

@Composable
private fun DemoTabIcon(
    tab: DemoTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = modifier) {
        when (tab) {
            DemoTab.Create -> drawCreateIcon(iconColor)
            DemoTab.Play -> drawPlayIcon(iconColor)
            DemoTab.History -> drawHistoryIcon(iconColor)
            DemoTab.Profile -> drawProfileIcon(iconColor)
        }
    }
}

private fun DrawScope.drawCreateIcon(color: Color) {
    drawLine(
        color = color,
        start = center.copy(x = size.width * 0.25f),
        end = center.copy(x = size.width * 0.75f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = center.copy(y = size.height * 0.25f),
        end = center.copy(y = size.height * 0.75f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPlayIcon(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.32f, size.height * 0.22f)
        lineTo(size.width * 0.76f, size.height * 0.5f)
        lineTo(size.width * 0.32f, size.height * 0.78f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawHistoryIcon(color: Color) {
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(color, radius = size.minDimension * 0.38f, style = stroke)
    drawLine(
        color = color,
        start = center,
        end = center.copy(y = size.height * 0.28f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = center,
        end = center.copy(x = size.width * 0.68f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawProfileIcon(color: Color) {
    drawLine(
        color = color,
        start = center.copy(x = size.width * 0.2f, y = size.height * 0.32f),
        end = center.copy(x = size.width * 0.8f, y = size.height * 0.32f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = center.copy(x = size.width * 0.2f, y = size.height * 0.68f),
        end = center.copy(x = size.width * 0.8f, y = size.height * 0.68f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = color,
        radius = 2.2.dp.toPx(),
        center = center.copy(x = size.width * 0.4f, y = size.height * 0.32f),
    )
    drawCircle(
        color = color,
        radius = 2.2.dp.toPx(),
        center = center.copy(x = size.width * 0.62f, y = size.height * 0.68f),
    )
}
