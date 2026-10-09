package cn.happyoyster.opensdk.demo.gateway

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DemoGatewayClientTest {
    @Test fun listsRouteEveryModeAndNormalizeMissingMode() = runBlocking {
        val requests = mutableListOf<Request>()
        val gateway = gateway(requests) { request ->
            val mode = request.header("X-Happy-Oyster-Mode")
            val idField = if (request.url.encodedPath.endsWith("/worlds")) "encryptedWorldId" else "encryptedTravelId"
            """{"output":{"code":0,"data":{"items":[{"$idField":"record-$mode"}]}}}"""
        }
        assertEquals(listOf("wander", "story", "acting"), gateway.listWorlds().map { it.mode })
        assertEquals(listOf("wander", "story", "acting"), gateway.listTravels().map { it.mode })
        assertEquals(listOf("1", "2", "3", "1", "2", "3"), requests.map { it.header("X-Happy-Oyster-Mode") })
        assertEquals(listOf("1", "2", "3"), requests.take(3).map { it.url.queryParameter("mode") })
    }

    @Test fun listsEveryPageForEachMode() = runBlocking {
        val requests = mutableListOf<Request>()
        val gateway = gateway(requests) { request ->
            val mode = request.header("X-Happy-Oyster-Mode")
            val page = request.url.queryParameter("page")
            val idField = if (request.url.encodedPath.endsWith("/worlds")) "encryptedWorldId" else "encryptedTravelId"
            val hasMore = mode == "1" && page == "1"
            """{"code":0,"data":{"items":[{"$idField":"record-$mode-$page"}],"pagination":{"hasMore":$hasMore}}}"""
        }
        assertEquals(4, gateway.listWorlds().size)
        assertEquals(4, gateway.listTravels().size)
        assertEquals(listOf("1", "2", "1", "1"), requests.take(4).map { it.url.queryParameter("page") })
        assertEquals(listOf("1", "2", "1", "1"), requests.drop(4).map { it.url.queryParameter("page") })
    }

    @Test fun credentialAndDetailKeepTheSelectedMode() = runBlocking {
        val requests = mutableListOf<Request>()
        val gateway = gateway(requests) { request ->
            val data = if (request.method == "POST") {
                """{"encryptedWorldId":"world","ticket":"test-ticket","expiresIn":1800}"""
            } else """{"encryptedWorldId":"world","status":"ready"}"""
            """{"code":0,"data":$data}"""
        }
        assertEquals("test-ticket", gateway.getTravelCredential("world", WorldKind.Acting).ticket)
        assertEquals("ready", gateway.getWorldDetail("world", WorldKind.Acting).status)
        assertEquals(listOf("3", "3"), requests.map { it.header("X-Happy-Oyster-Mode") })
        assertEquals(listOf("POST", "GET"), requests.map { it.method })
    }

    @Test fun numericAndSymbolicGatewayFailuresRemainStructured() = runBlocking {
        for ((wireCode, numeric, symbolic) in listOf(
            Triple("403001", 403001, null),
            Triple("\"WORLD_NOT_READY\"", null, "WORLD_NOT_READY"),
        )) {
            val gateway = gateway(mutableListOf()) { """{"code":$wireCode,"message":"not ready"}""" }
            try {
                gateway.getWorldDetail("world", WorldKind.Story)
                fail("Expected a gateway failure")
            } catch (error: DemoGatewayException) {
                assertEquals(numeric, error.code)
                assertEquals(symbolic, error.errorCode)
            }
        }
    }

    @Test fun malformedGatewayResponsesDoNotEchoTheirBodies() = runBlocking {
        val gateway = gateway(mutableListOf()) { """{"output":"private-diagnostic"}""" }
        try {
            gateway.getWorldDetail("world", WorldKind.Story)
            fail("Expected a gateway failure")
        } catch (error: DemoGatewayException) {
            assertEquals("Invalid gateway envelope", error.message)
        }
    }

    private fun gateway(requests: MutableList<Request>, body: (Request) -> String): DemoGatewayClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body(request).toResponseBody("application/json".toMediaType())).build()
        }.build()
        return DemoGatewayClient("https://example.com/server-api", client)
    }
}
