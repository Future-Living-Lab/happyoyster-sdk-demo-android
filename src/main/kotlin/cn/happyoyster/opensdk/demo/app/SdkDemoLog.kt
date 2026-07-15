package cn.happyoyster.opensdk.demo.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import cn.happyoyster.opensdk.SDKError
import cn.happyoyster.opensdk.demo.gateway.DemoGatewayException

internal const val SDK_DEMO_LOG_TAG = "HappyOysterSdkDemo"
private const val MAX_LOG_ENTRIES = 200

internal enum class SdkDemoLogKind {
    SDK_CALL,
    SDK_RESULT,
    SDK_EVENT,
    GATEWAY,
    ERROR,
    INFO,
}

internal data class SdkDemoLogEntry(
    val time: Long,
    val kind: SdkDemoLogKind,
    val label: String,
    val detail: String? = null,
)

internal object SdkDemoLog {
    private val mainHandler = Handler(Looper.getMainLooper())
    val entries = mutableStateListOf<SdkDemoLogEntry>()

    fun add(kind: SdkDemoLogKind, label: String, detail: String? = null) {
        val entry = SdkDemoLogEntry(
            time = System.currentTimeMillis(),
            kind = kind,
            label = label,
            detail = detail,
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendEntry(entry)
        } else {
            mainHandler.post { appendEntry(entry) }
        }
        val line = if (detail == null) label else "$label | $detail"
        Log.i(SDK_DEMO_LOG_TAG, "[${kind.name}] $line")
    }

    fun clear() {
        entries.clear()
    }

    private fun appendEntry(entry: SdkDemoLogEntry) {
        entries.add(entry)
        while (entries.size > MAX_LOG_ENTRIES) {
            entries.removeAt(0)
        }
    }

    suspend fun <T> sdkCall(
        label: String,
        render: (T) -> String = { it.toString() },
        block: suspend () -> T,
    ): Result<T> {
        return runCatching { block() }.also { logSdkResult(label, render, it) }
    }

    fun <T> sdkCallSync(
        label: String,
        render: (T) -> String = { it.toString() },
        block: () -> T,
    ): Result<T> {
        return runCatching { block() }.also { logSdkResult(label, render, it) }
    }

    private fun <T> logSdkResult(label: String, render: (T) -> String, result: Result<T>) {
        result.onSuccess { add(SdkDemoLogKind.SDK_RESULT, label, render(it)) }
            .onFailure { add(SdkDemoLogKind.ERROR, label, it.sdkDemoLogString()) }
    }

    suspend fun <T> gatewayCall(
        label: String,
        render: (T) -> String = { it.toString() },
        block: suspend () -> T,
    ): Result<T> {
        val startMs = SystemClock.elapsedRealtime()
        return runCatching { block() }.also { result ->
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            result.onSuccess { add(SdkDemoLogKind.GATEWAY, label, "ok, ${render(it)}, elapsed=${elapsedMs}ms") }
                .onFailure { add(SdkDemoLogKind.ERROR, label, "${it.sdkDemoLogString()}, elapsed=${elapsedMs}ms") }
        }
    }
}

internal fun Throwable.sdkDemoLogString(): String =
    when (this) {
        is SDKError -> "SDKError(code=$code, raw=${raw ?: "null"})"
        is DemoGatewayException -> "GatewayError(code=$code, errorCode=$errorCode, message=$message)"
        else -> message ?: javaClass.simpleName
    }
