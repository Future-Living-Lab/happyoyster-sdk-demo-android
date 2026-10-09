package cn.happyoyster.opensdk.demo.ui

import cn.happyoyster.opensdk.demo.gateway.DemoWorld
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoListOptionsTest {
    private val worlds = listOf(
        DemoWorld("old-wander", mode = "adventure", createdAt = "2026-09-23T10:00:00Z"),
        DemoWorld("new-story", mode = "directing", createdAt = "2026-09-24T12:00:00+08:00"),
        DemoWorld("middle-acting", mode = "3", createdAt = "2026-09-24T03:00:00Z"),
        DemoWorld("new-wander", mode = "wander", createdAt = "2026-09-24T05:00:00Z"),
    )

    @Test fun allModesAreSortedTogetherByInstantInBothDirections() {
        assertEquals(listOf("new-wander", "new-story", "middle-acting", "old-wander"), ids())
        assertEquals(
            listOf("old-wander", "middle-acting", "new-story", "new-wander"),
            ids(order = DemoTimeOrder.OldestFirst),
        )
    }

    @Test fun filteringSupportsEachModeAndServerAliases() {
        assertEquals(listOf("new-wander", "old-wander"), ids(DemoModeFilter.Wander))
        assertEquals(listOf("new-story"), ids(DemoModeFilter.Story))
        assertEquals(listOf("middle-acting"), ids(DemoModeFilter.Acting))
        assertEquals(listOf("old-wander", "new-wander"), ids(DemoModeFilter.Wander, DemoTimeOrder.OldestFirst))
        val unknown = listOf(DemoWorld("unknown"))
        assertEquals(listOf("unknown"), ids(records = unknown))
        assertEquals(emptyList<String>(), ids(DemoModeFilter.Story, records = unknown))
    }

    @Test fun invalidAndMissingTimesStayLastAndEqualTimesKeepTheirOrder() {
        val records = listOf(
            DemoWorld("invalid", createdAt = "bad-date"),
            DemoWorld("seconds", createdAt = "1000000000"),
            DemoWorld("missing"),
            DemoWorld("milliseconds", createdAt = "1000000000000"),
            DemoWorld("newer", createdAt = "2026-09-24T05:00:00.123456Z"),
        )
        assertEquals(listOf("newer", "seconds", "milliseconds", "invalid", "missing"), ids(records = records))
        assertEquals(
            listOf("seconds", "milliseconds", "newer", "invalid", "missing"),
            ids(order = DemoTimeOrder.OldestFirst, records = records),
        )
    }

    @Test fun fractionalSecondsUseDecimalPrecision() {
        val second = parseServerDateTime("2026-09-24T00:00:00Z")!!.time
        assertEquals(second + 100, parseServerDateTime("2026-09-24T00:00:00.1Z")!!.time)
        assertEquals(second + 120, parseServerDateTime("2026-09-24T00:00:00.12Z")!!.time)
        assertEquals(second + 123, parseServerDateTime("2026-09-24T00:00:00.123456Z")!!.time)
    }

    private fun ids(
        filter: DemoModeFilter = DemoModeFilter.All,
        order: DemoTimeOrder = DemoTimeOrder.NewestFirst,
        records: List<DemoWorld> = worlds,
    ): List<String> = records.filterAndSortDemoRecords(filter, order, { it.mode }, { it.createdAt })
        .map { it.encryptedWorldId }
}
