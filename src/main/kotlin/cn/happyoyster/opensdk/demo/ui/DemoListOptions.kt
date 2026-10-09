package cn.happyoyster.opensdk.demo.ui

import cn.happyoyster.opensdk.demo.gateway.WorldKind
import cn.happyoyster.opensdk.demo.gateway.toWorldKindOrNull

internal enum class DemoModeFilter(val kind: WorldKind?) {
    All(null), Wander(WorldKind.Wander), Story(WorldKind.Story), Acting(WorldKind.Acting),
}

internal enum class DemoTimeOrder { NewestFirst, OldestFirst }

internal fun <T> List<T>.filterAndSortDemoRecords(
    filter: DemoModeFilter,
    order: DemoTimeOrder,
    mode: (T) -> String?,
    time: (T) -> String?,
): List<T> = asSequence()
    .filter { filter.kind == null || mode(it).toWorldKindOrNull() == filter.kind }
    .map { it to time(it)?.trim()?.let(::parseServerDateTime)?.time }
    .sortedWith { left, right ->
        val a = left.second
        val b = right.second
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            order == DemoTimeOrder.NewestFirst -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }
    .map { it.first }
    .toList()
