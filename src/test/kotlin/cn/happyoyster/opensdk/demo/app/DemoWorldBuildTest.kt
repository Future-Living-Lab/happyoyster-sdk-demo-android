package cn.happyoyster.opensdk.demo.app

import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DemoWorldBuildTest {
    @Test fun laggingListCannotRemoveNewWorldOrRegressConfirmedBuild() {
        val ready = DemoWorld("created", status = "ready", firstFrame = "https://example.com/ready.png")
        val unrelated = DemoWorld("removed", status = "ready")
        assertEquals(listOf(ready), mergeWorldListSnapshot(listOf(ready, unrelated), emptyList(), setOf("created")))
        val merged = mergeWorldListSnapshot(listOf(ready), listOf(ready.copy(status = "generating")), emptySet())
        assertEquals("ready", merged.single().status)
        val failed = ready.copy(status = "failed", errorCode = "WORLD_IMAGE_GENERATION_FAILED")
        assertEquals(failed, mergeWorldListSnapshot(listOf(failed), listOf(ready.copy(status = "pending")), emptySet()).single())
    }

    @Test fun unsupportedBuildModeDoesNotReplaceKnownWorldMode() {
        val created = DemoWorld("created", status = "generating", mode = "story")
        val status = created.copy(status = "ready", mode = "story_simple")
        val merged = mergeWorldListSnapshot(listOf(created), listOf(status), emptySet()).single()
        assertEquals("story", merged.mode)
        assertEquals("ready", merged.status)
    }

    @Test fun existingCoverDoesNotSkipBuildAndFailureStopsPolling() = runBlocking {
        val initial = DemoWorld("world", status = "generating", firstFrame = "https://example.com/frame.png")
        assertTrue(initial.needsBuildTracking())
        val failed = initial.copy(status = "failed", errorCode = "WORLD_IMAGE_GENERATION_FAILED")
        val queue = ArrayDeque(listOf(initial, failed))
        val seen = mutableListOf<DemoWorld>()
        var waits = 0
        assertTrue(pollDemoWorldBuild({ queue.removeFirst() }, seen::add, waitForNext = { waits++ }))
        assertEquals(listOf(initial, failed), seen)
        assertEquals(1, waits)
        assertFalse(failed.needsBuildTracking())
    }

    @Test fun readyStopsImmediatelyAndFailedWithoutReasonStillNeedsQuery() = runBlocking {
        val failed = DemoWorld("world", status = "failed", previewUrl = "https://example.com/preview.png")
        assertTrue(failed.needsBuildTracking())
        var queries = 0
        assertTrue(pollDemoWorldBuild(
            query = { queries++; failed.copy(status = "ready") },
            onUpdate = {},
            waitForNext = { fail("Ready must not schedule another query") },
        ))
        assertEquals(1, queries)
    }

    @Test fun timeoutDoesNotInventFailedStatus() = runBlocking {
        val seen = mutableListOf<DemoWorld>()
        assertFalse(pollDemoWorldBuild(
            { DemoWorld("world", status = "pending") }, seen::add, attempts = 2, waitForNext = {},
        ))
        assertEquals(listOf("pending", "pending"), seen.map { it.status })
    }

    @Test fun cancellationAndQueryFailuresPropagate() = runBlocking {
        listOf(CancellationException("cancelled"), IllegalStateException("unavailable")).forEach { cause ->
            try {
                pollDemoWorldBuild(query = { throw cause }, onUpdate = { fail("No synthetic status") })
                fail("Expected query failure")
            } catch (actual: Exception) {
                assertSame(cause, actual)
            }
        }
    }
}
