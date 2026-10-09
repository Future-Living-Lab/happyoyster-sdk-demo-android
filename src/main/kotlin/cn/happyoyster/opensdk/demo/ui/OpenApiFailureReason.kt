package cn.happyoyster.opensdk.demo.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R

internal enum class OpenApiFailureKind {
    World,
    Travel,
}

@Composable
internal fun OpenApiFailureReason(
    kind: OpenApiFailureKind,
    errorCode: String?,
    modifier: Modifier = Modifier,
) {
    val reasonRes = openApiFailureReasonRes(kind, errorCode)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(reasonRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        errorCode?.takeIf(String::isNotBlank)?.let { code ->
            Text(
                text = stringResource(R.string.failure_code, code),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
internal fun openApiFailureReasonRes(kind: OpenApiFailureKind, errorCode: String?): Int = when (kind) {
    OpenApiFailureKind.World -> when (errorCode) {
        "CONTENT_MODERATION_REJECTED",
        "CONTENT_INIT_MODERATION_REJECTED",
        "WORLD_SCRIPT_BLOCKED_BY_SAFETY",
        "WORLD_PE_BLOCKED_BY_SAFETY",
        "CONTENT_COPYRIGHT_VIOLATION",
        -> R.string.world_failure_change_content
        "IMAGE_URL_INACCESSIBLE" -> R.string.world_failure_image_unreachable
        "WORLD_MEDIA_REGISTER_FAILED" -> R.string.world_failure_image_processing
        "WORLD_SCRIPT_GENERATION_FAILED" -> R.string.world_failure_script_generation
        "WORLD_IMAGE_GENERATION_FAILED" -> R.string.world_failure_first_frame_generation
        "WORLD_PE_GENERATION_FAILED",
        "WORLD_CAPTION_GENERATION_FAILED",
        -> R.string.world_failure_scene_generation
        else -> R.string.world_failure_generic
    }
    OpenApiFailureKind.Travel -> when (errorCode) {
        "TRAVEL_SESSION_INIT_FAILED" -> R.string.travel_failure_session_init
        "TRAVEL_NO_STREAM_AUTO_END" -> R.string.travel_failure_no_stream
        "TRAVEL_STREAM_CREATE_FAILED" -> R.string.travel_failure_stream_create
        "TRAVEL_NO_SESSION" -> R.string.travel_failure_no_session
        "WORLD_CAPACITY_EXCEEDED" -> R.string.travel_failure_capacity
        "TRAVEL_RUNTIME_FAILED" -> R.string.travel_failure_runtime
        else -> R.string.travel_failure_generic
    }
}
