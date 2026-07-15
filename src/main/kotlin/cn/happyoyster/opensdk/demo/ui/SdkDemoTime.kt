package cn.happyoyster.opensdk.demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.config.SdkDemoLanguage
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun Long.readableSdkDemoDateTime(language: SdkDemoLanguage): String {
    if (this <= 0L) return stringResource(R.string.empty)
    val formatter = remember(language) { sdkDemoDateFormat(language) }
    return formatter.format(Date(this * 1000L))
}

@Composable
internal fun String?.readableSdkDemoDateTime(language: SdkDemoLanguage): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return stringResource(R.string.empty)
    val parsed = remember(raw) { parseServerDateTime(raw) }
    val formatter = remember(language) { sdkDemoDateFormat(language) }
    return parsed?.let(formatter::format) ?: raw
}

private fun sdkDemoDateFormat(language: SdkDemoLanguage): DateFormat =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        language.locale,
    ).apply {
        timeZone = language.sdkDemoTimeZone()
    }

private fun SdkDemoLanguage.sdkDemoTimeZone(): TimeZone =
    TimeZone.getTimeZone(
        when (this) {
            SdkDemoLanguage.Chinese -> "Asia/Shanghai"
            SdkDemoLanguage.English -> "America/Los_Angeles"
        },
    )

private fun parseServerDateTime(raw: String): Date? {
    raw.toLongOrNull()?.let { timestamp ->
        return Date(if (raw.length >= 13) timestamp else timestamp * 1000L)
    }
    val candidates = listOf(raw, raw.withMillisPrecision()).distinct()
    for (candidate in candidates) {
        for (pattern in SERVER_DATE_PATTERNS) {
            parseDate(pattern, candidate)?.let { return it }
        }
    }
    return null
}

private fun String.withMillisPrecision(): String {
    val dotIndex = indexOf('.')
    if (dotIndex < 0) return this
    val fractionStart = dotIndex + 1
    val fractionEnd = indexOfFirstAfter(fractionStart) { !it.isDigit() }
        .let { if (it < 0) length else it }
    val fraction = substring(fractionStart, fractionEnd)
    if (fraction.length <= 3) return this
    val millis = fraction.take(3)
    return substring(0, fractionStart) + millis + substring(fractionEnd)
}

private inline fun String.indexOfFirstAfter(startIndex: Int, predicate: (Char) -> Boolean): Int {
    for (index in startIndex until length) {
        if (predicate(this[index])) return index
    }
    return -1
}

private fun parseDate(pattern: String, raw: String): Date? {
    val parser = SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val date = parser.parse(raw, position) ?: return null
    return if (position.index == raw.length) date else null
}

private val SERVER_DATE_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ssX",
    "yyyy-MM-dd HH:mm:ss",
)
