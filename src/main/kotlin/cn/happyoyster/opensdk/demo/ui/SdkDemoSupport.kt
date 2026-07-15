package cn.happyoyster.opensdk.demo.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.SDKError
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.gateway.mergeFrom

@Composable
internal fun OptionRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SelectButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

/** Green used for success-like statuses (world ready / travel completed). */
internal val SdkDemoSuccessColor = Color(0xFF2E7D32)

/** Rounded status pill shared by the world and travel lists. */
@Composable
internal fun StatusBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal fun List<DemoWorld>.replaceWorld(world: DemoWorld): List<DemoWorld> =
    if (any { it.encryptedWorldId == world.encryptedWorldId }) {
        map { if (it.encryptedWorldId == world.encryptedWorldId) it.mergeFrom(world) else it }
    } else {
        this + world
    }

internal fun Throwable.userMessage(): String = message ?: javaClass.simpleName

internal fun SDKError.sdkDemoMessage(context: Context): String {
    val rawText = raw?.toString()
    val summaryResId = when (code) {
        101001 -> R.string.sdk_error_invalid_token
        103001 -> R.string.sdk_error_no_active_travel
        103002 -> R.string.sdk_error_invalid_travel_state
        103003 -> R.string.sdk_error_mode_mismatch
        105001 -> R.string.sdk_error_rtc_connection_failed
        105002 -> R.string.sdk_error_rtc_join_timeout
        105003 -> R.string.sdk_error_first_frame_timeout
        105004 -> R.string.sdk_error_datachannel_send_failed
        105005 -> R.string.sdk_error_callback_timeout
        105006 -> R.string.sdk_error_no_push_stream
        106001 -> R.string.sdk_error_network_request_failed
        106002 -> R.string.sdk_error_response_parse_failed
        106003 -> R.string.sdk_error_invalid_gateway_response
        108001 -> R.string.sdk_error_remote_feature_disabled
        else -> null
    }
    val summary = summaryResId?.let(context::getString)
        ?: (message ?: context.getString(R.string.sdk_error_unknown, code))
    return if (rawText.isNullOrBlank()) {
        summary
    } else {
        context.getString(R.string.sdk_error_with_raw, summary, rawText)
    }
}

internal fun Context.withSdkDemoLanguage(language: SdkDemoLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList(language.locale))
    return createConfigurationContext(config)
}

internal fun String.isHttpUrl(): Boolean {
    val uri = Uri.parse(trim())
    return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}

internal fun String.isApiHost(): Boolean {
    val value = trim()
    if (value.isBlank() || value.any(Char::isWhitespace)) return false
    if (value.contains("://") || value.contains('/')) return false
    return Uri.parse("https://$value").host == value
}
