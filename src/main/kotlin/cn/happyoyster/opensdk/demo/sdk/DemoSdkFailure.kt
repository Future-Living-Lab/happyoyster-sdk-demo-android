package cn.happyoyster.opensdk.demo.sdk

import cn.happyoyster.opensdk.SDKError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads only the public 0.2.3 failed-status payload; raw diagnostic text is never displayed. */
internal fun SDKError.travelFailureCode(): String? {
    if (code != 500001) return null
    val payload = raw as? JsonObject ?: return null
    val status = payload["status"] as? JsonPrimitive ?: return null
    if (!status.isString || status.content != "failed") return null
    val reason = payload["errorCode"] as? JsonPrimitive ?: return null
    return reason.content.takeIf { reason.isString && it.matches(Regex("[A-Za-z0-9_]{1,128}")) }
}
