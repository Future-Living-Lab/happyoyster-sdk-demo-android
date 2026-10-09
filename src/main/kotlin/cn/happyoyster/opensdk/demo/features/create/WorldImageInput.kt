package cn.happyoyster.opensdk.demo.features.create

import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.ui.DemoOutlinedButton
import cn.happyoyster.opensdk.demo.ui.isHttpUrl
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_BYTES = 30 * 1024 * 1024
private const val MAX_IMAGE_PIXELS = 4096 * 4096
private const val FIRST_FRAME_MIN_ASPECT_RATIO = 1.5
private const val FIRST_FRAME_MAX_ASPECT_RATIO = 2.0
private const val ACTING_MIN_ASPECT_RATIO = 0.5
private const val ACTING_MAX_ASPECT_RATIO = 2.0 / 3.0

/** Image URL/Base64 input with file and clipboard import. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldImageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onImportValidityChange: (Boolean) -> Unit,
    @StringRes labelRes: Int,
    @StringRes supportingTextRes: Int,
    isError: Boolean,
    validateFirstFrameRatio: Boolean,
    validatePortraitRatio: Boolean = false,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnImportValidityChange by rememberUpdatedState(onImportValidityChange)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember(value) { mutableStateOf<ImageImportError?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    fun importImage(load: suspend () -> String) {
        isImporting = true
        currentOnImportValidityChange(false)
        importError = null
        scope.launch {
            try {
                val imported = load()
                currentOnImportValidityChange(true)
                currentOnValueChange(imported)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importError = error.toImageImportError()
                currentOnImportValidityChange(false)
            } finally {
                isImporting = false
            }
        }
    }

    fun importText(text: String) = importImage {
        withContext(Dispatchers.Default) {
            normalizeWorldImageInput(text, validateFirstFrameRatio, validatePortraitRatio)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importImage {
            withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use(InputStream::readImageBytes)
                    ?: throw ImageImportException(ImageImportError.ReadFailed)
                normalizeWorldImageBytes(bytes, validateFirstFrameRatio, validatePortraitRatio)
            }
        }
    }

    val displaysCompactBase64 = value.trim().isLikelyBase64Image()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = if (displaysCompactBase64) {
                stringResource(R.string.image_base64_loaded, value.length)
            } else {
                value
            },
            onValueChange = { incoming ->
                if (!displaysCompactBase64 && incoming.isLikelyBase64Image()) {
                    importText(incoming)
                } else if (!displaysCompactBase64) {
                    importError = null
                    currentOnImportValidityChange(true)
                    currentOnValueChange(incoming)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = displaysCompactBase64,
            enabled = !isImporting,
            isError = isError || importError != null,
            label = { Text(stringResource(labelRes)) },
            supportingText = {
                Text(
                    importError?.let { stringResource(it.messageRes) }
                        ?: stringResource(supportingTextRes),
                )
            },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DemoOutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !isImporting,
            ) {
                Text(
                    stringResource(
                        if (isImporting) R.string.image_input_importing else R.string.image_input_select,
                    ),
                )
            }
            DemoOutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.text
                        ?.toString()
                    if (text.isNullOrBlank()) {
                        importError = ImageImportError.ClipboardEmpty
                        currentOnImportValidityChange(false)
                    } else {
                        importText(text)
                    }
                },
                enabled = !isImporting,
            ) {
                Text(stringResource(R.string.image_input_paste))
            }
            if (value.isNotBlank() || importError != null) {
                TextButton(
                    onClick = {
                        importError = null
                        currentOnImportValidityChange(true)
                        currentOnValueChange("")
                    },
                    enabled = !isImporting,
                ) {
                    Text(stringResource(R.string.image_input_clear))
                }
            }
        }
    }
}

private fun String.isLikelyBase64Image(): Boolean {
    val trimmed = trim()
    if (trimmed.startsWith("data:image/", ignoreCase = true)) return true
    if (trimmed.length < 16 || trimmed.isHttpUrl()) return false
    return trimmed.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it.isWhitespace() }
}

private fun normalizeWorldImageInput(
    input: String,
    validateFirstFrameRatio: Boolean,
    validatePortraitRatio: Boolean,
): String {
    val trimmed = input.trim()
    if (trimmed.isHttpUrl()) return trimmed

    val payload = if (trimmed.startsWith("data:", ignoreCase = true)) {
        val comma = trimmed.indexOf(',')
        if (comma <= 5 || !trimmed.substring(0, comma).startsWith("data:image/", ignoreCase = true) ||
            !trimmed.substring(0, comma).endsWith(";base64", ignoreCase = true)
        ) {
            throw ImageImportException(ImageImportError.InvalidBase64)
        }
        trimmed.substring(comma + 1)
    } else {
        trimmed
    }
    val compact = payload.filterNot(Char::isWhitespace)
    if (compact.length < 16 || compact.length % 4 != 0 ||
        compact.any { !it.isLetterOrDigit() && it != '+' && it != '/' && it != '=' }
    ) {
        throw ImageImportException(ImageImportError.InvalidBase64)
    }
    val bytes = try {
        Base64.decode(compact, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        throw ImageImportException(ImageImportError.InvalidBase64)
    }
    return normalizeWorldImageBytes(bytes, validateFirstFrameRatio, validatePortraitRatio)
}

private fun normalizeWorldImageBytes(
    bytes: ByteArray,
    validateFirstFrameRatio: Boolean,
    validatePortraitRatio: Boolean,
): String {
    if (bytes.isEmpty()) throw ImageImportException(ImageImportError.InvalidImage)
    if (bytes.size >= MAX_IMAGE_BYTES) throw ImageImportException(ImageImportError.TooLarge)

    val mimeType = detectImageMimeType(bytes)
        ?: throw ImageImportException(ImageImportError.UnsupportedFormat)
    if (!hasCompleteImageStructure(mimeType, bytes)) {
        throw ImageImportException(ImageImportError.IncompleteImage)
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val width = options.outWidth
    val height = options.outHeight
    if (width <= 0 || height <= 0) throw ImageImportException(ImageImportError.InvalidImage)
    if (width.toLong() * height > MAX_IMAGE_PIXELS) {
        throw ImageImportException(ImageImportError.TooManyPixels)
    }
    val ratio = width.toDouble() / height
    if (validateFirstFrameRatio) {
        if (ratio !in FIRST_FRAME_MIN_ASPECT_RATIO..FIRST_FRAME_MAX_ASPECT_RATIO) {
            throw ImageImportException(ImageImportError.FirstFrameAspectRatio)
        }
    }
    if (validatePortraitRatio && ratio !in ACTING_MIN_ASPECT_RATIO..ACTING_MAX_ASPECT_RATIO) {
        throw ImageImportException(ImageImportError.PortraitAspectRatio)
    }

    return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

private fun InputStream.readImageBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total >= MAX_IMAGE_BYTES) throw ImageImportException(ImageImportError.TooLarge)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun detectImageMimeType(bytes: ByteArray): String? =
    when {
        bytes.size >= 3 &&
            bytes[0].toInt() and 0xFF == 0xFF &&
            bytes[1].toInt() and 0xFF == 0xD8 &&
            bytes[2].toInt() and 0xFF == 0xFF -> "image/jpeg"
        bytes.size >= 8 &&
            bytes[0].toInt() and 0xFF == 0x89 &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte() -> "image/png"
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte() -> "image/webp"
        else -> null
    }

private fun hasCompleteImageStructure(mimeType: String, bytes: ByteArray): Boolean =
    when (mimeType) {
        "image/jpeg" -> hasJpegEndMarker(bytes)
        "image/png" -> bytes.size >= 12 &&
            bytes[bytes.size - 8] == 'I'.code.toByte() &&
            bytes[bytes.size - 7] == 'E'.code.toByte() &&
            bytes[bytes.size - 6] == 'N'.code.toByte() &&
            bytes[bytes.size - 5] == 'D'.code.toByte()
        "image/webp" -> {
            if (bytes.size < 12) {
                false
            } else {
                val riffSize = (bytes[4].toInt() and 0xFF) or
                    ((bytes[5].toInt() and 0xFF) shl 8) or
                    ((bytes[6].toInt() and 0xFF) shl 16) or
                    ((bytes[7].toInt() and 0xFF) shl 24)
                bytes.size == riffSize + 8
            }
        }
        else -> false
    }

private fun hasJpegEndMarker(bytes: ByteArray): Boolean {
    for (index in 0 until bytes.lastIndex) {
        if (bytes[index].toInt() and 0xFF == 0xFF &&
            bytes[index + 1].toInt() and 0xFF == 0xD9
        ) {
            return true
        }
    }
    return false
}

private enum class ImageImportError(@StringRes val messageRes: Int) {
    ClipboardEmpty(R.string.image_import_clipboard_empty),
    InvalidBase64(R.string.image_import_invalid_base64),
    IncompleteImage(R.string.image_import_incomplete),
    InvalidImage(R.string.image_import_invalid_image),
    UnsupportedFormat(R.string.image_import_unsupported_format),
    TooLarge(R.string.image_import_too_large),
    TooManyPixels(R.string.image_import_too_many_pixels),
    FirstFrameAspectRatio(R.string.image_import_first_frame_aspect_ratio),
    PortraitAspectRatio(R.string.image_import_portrait_aspect_ratio),
    ReadFailed(R.string.image_import_read_failed),
}

private class ImageImportException(val error: ImageImportError) : IllegalArgumentException()

private fun Throwable.toImageImportError(): ImageImportError =
    (this as? ImageImportException)?.error ?: ImageImportError.ReadFailed
