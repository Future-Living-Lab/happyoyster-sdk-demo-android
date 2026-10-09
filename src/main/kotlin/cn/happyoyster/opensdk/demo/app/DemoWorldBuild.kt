package cn.happyoyster.opensdk.demo.app

import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import cn.happyoyster.opensdk.demo.gateway.mergeFrom
import kotlinx.coroutines.delay

internal fun DemoWorld.needsBuildTracking(): Boolean =
    status.lowercase() in setOf("pending", "generating", "init", "building", "unknown") ||
        (status.equals("failed", ignoreCase = true) && errorCode.isNullOrBlank())

/** A list snapshot may lag behind the create response or a terminal build-status response. */
internal fun mergeWorldListSnapshot(
    cached: List<DemoWorld>,
    incoming: List<DemoWorld>,
    retainedCreatedIds: Set<String>,
): List<DemoWorld> {
    val cachedById = cached.associateBy { it.encryptedWorldId }
    val incomingIds = incoming.mapTo(mutableSetOf()) { it.encryptedWorldId }
    val merged = incoming.map { world ->
        val previous = cachedById[world.encryptedWorldId] ?: return@map world
        val combined = previous.mergeFrom(world)
        if (previous.status.lowercase() in setOf("ready", "failed") &&
            world.status.lowercase() !in setOf("ready", "failed")) {
            combined.copy(status = previous.status, errorCode = previous.errorCode, errorMessage = previous.errorMessage)
        } else combined
    }
    return cached.filter { it.encryptedWorldId in retainedCreatedIds && it.encryptedWorldId !in incomingIds } + merged
}

/** Poll independently of image availability; timeout leaves the world pending for a later refresh. */
internal suspend fun pollDemoWorldBuild(
    query: suspend () -> DemoWorld,
    onUpdate: (DemoWorld) -> Unit,
    attempts: Int = 30,
    waitForNext: suspend () -> Unit = { delay(4_000L) },
): Boolean {
    repeat(attempts) { attempt ->
        val world = query()
        onUpdate(world)
        if (world.status.lowercase() in setOf("ready", "failed")) return true
        if (attempt < attempts - 1) waitForNext()
    }
    return false
}
