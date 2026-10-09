package cn.happyoyster.opensdk.demo.sdk

import cn.happyoyster.opensdk.SDKError
import cn.happyoyster.opensdk.demo.R
import cn.happyoyster.opensdk.demo.ui.OpenApiFailureKind
import cn.happyoyster.opensdk.demo.ui.openApiFailureReasonRes
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemoSdkFailureTest {
    @Test
    fun readsPublishedFailurePayloadWithoutDisplayingDiagnosticMessage() {
        val error = SDKError(500001, Json.parseToJsonElement("""
            {"encryptedTravelId":"example", "status":"failed",
             "errorCode":"TRAVEL_NO_STREAM_AUTO_END", "errorMessage":"private diagnostic text"}
        """))
        assertEquals("TRAVEL_NO_STREAM_AUTO_END", error.travelFailureCode())
        assertEquals(R.string.travel_failure_no_stream,
            openApiFailureReasonRes(OpenApiFailureKind.Travel, error.travelFailureCode()))
    }

    @Test
    fun ignoresNonterminalMalformedAndLegacyRawValues() {
        val payloads = listOf(
            """{"status":"running","errorCode":"TRAVEL_RUNTIME_FAILED"}""",
            """{"status":"failed","errorCode":500001}""",
            """{"status":"failed","errorCode":null}""",
            """{"status":"failed","errorCode":{"nested":"value"}}""",
            """{"status":"failed","errorCode":""}""",
            """{"status":"failed","errorCode":"https://private.example/diagnostic"}""",
            "[]",
        )
        payloads.forEach { assertNull(SDKError(500001, Json.parseToJsonElement(it)).travelFailureCode()) }
        assertNull(SDKError(500001, "legacy raw text").travelFailureCode())
        assertNull(SDKError(105006).travelFailureCode())
        assertNull(SDKError(106003, Json.parseToJsonElement(
            """{"status":"failed","errorCode":"TRAVEL_RUNTIME_FAILED"}""",
        )).travelFailureCode())
    }

    @Test
    fun unknownNumericLookingAndModerationCodesUseSafeFallback() {
        listOf("FUTURE_REASON", "500001", "CONTENT_MODERATION_REJECTED").forEach { code ->
            val error = SDKError(500001, Json.parseToJsonElement(
                """{"status":"failed","errorCode":"$code","errorMessage":null}""",
            ))
            assertEquals(code, error.travelFailureCode())
            assertEquals(R.string.travel_failure_generic,
                openApiFailureReasonRes(OpenApiFailureKind.Travel, error.travelFailureCode()))
        }
    }
}
