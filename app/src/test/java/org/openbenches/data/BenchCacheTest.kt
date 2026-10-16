package org.openbenches.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BenchCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun bench(id: Int) = Bench(id, listOf(-2.36, 51.38), "Bench $id")
    private val day = FULL_REFRESH_MS

    @Test
    fun downloadsEverythingWithoutACacheOrOnceADay() {
        assertEquals(RefreshPlan.Full, refreshPlan(null, now = 0))
        assertEquals(RefreshPlan.Full, refreshPlan(CachedBenches(0, emptyList()), now = 1))
        assertEquals(RefreshPlan.Delta, refreshPlan(CachedBenches(0, listOf(bench(1))), now = day - 1))
        assertEquals(RefreshPlan.Full, refreshPlan(CachedBenches(0, listOf(bench(1))), now = day))
    }

    @Test
    fun newBenchesAreAddedToTheFront() {
        val cached = (1..100).map(::bench)
        val latest = (95..103).reversed().map(::bench) // 101–103 are new

        val merged = mergeLatest(cached, latest)!!

        assertEquals(listOf(103, 102, 101), merged.take(3).map { it.id })
        assertEquals(103, merged.size)
    }

    @Test
    fun nothingNewKeepsTheSameList() {
        val cached = (1..100).map(::bench)
        assertSame(cached, mergeLatest(cached, (51..100).reversed().map(::bench)))
    }

    @Test
    fun aFullPageOfNewBenchesMeansSomeMayBeMissing() {
        val cached = (1..100).map(::bench)
        val latest = (101 until 101 + DELTA_SIZE).reversed().map(::bench)

        assertNull(mergeLatest(cached, latest))
    }

    @Test
    fun theCacheSurvivesARestart() = runBlocking {
        val file = tmp.newFile("benches.json")
        BenchCache(file).write(CachedBenches(1234, listOf(bench(7), bench(8))))

        val read = BenchCache(file).read()!!

        assertEquals(1234L, read.fullFetchAt)
        assertEquals(listOf(7, 8), read.benches.map { it.id })
        assertEquals(51.38, read.benches.first().lat, 1e-9)
    }

    @Test
    fun aMissingOrCorruptCacheReadsAsNone() = runBlocking {
        assertNull(BenchCache(tmp.root.resolve("nope.json")).read())
        val bad = tmp.newFile("bad.json").apply { writeText("{not json") }
        assertNull(BenchCache(bad).read())
    }
}
