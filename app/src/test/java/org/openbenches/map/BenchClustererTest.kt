package org.openbenches.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openbenches.data.Bench

class BenchClustererTest {
    private fun bench(id: Int, lat: Double, lng: Double) = Bench(id, listOf(lng, lat), "Bench $id")

    // Parade Gardens, Bath: benches a few metres apart.
    private val parade = listOf(
        bench(1, 51.381004, -2.359142),
        bench(2, 51.381485, -2.359447),
        bench(3, 51.381447, -2.359533),
    )
    private val oxford = bench(4, 51.729153, -1.240306)

    @Test
    fun nearbyBenchesClusterWhenZoomedOut() {
        val clusters = BenchClusterer.cluster(parade + oxford, zoom = 8)

        assertEquals(listOf(3, 1), clusters.map { it.size }.sortedDescending())
    }

    @Test
    fun benchesSplitWhenZoomedIn() {
        val clusters = BenchClusterer.cluster(parade + oxford, zoom = 18)

        assertEquals(4, clusters.size)
        assertTrue(clusters.all { it.isSingle })
    }

    @Test
    fun benchesAtTheSameSpotStayTogetherAtMaxZoom() {
        val twins = listOf(bench(5, 51.5, -0.1), bench(6, 51.5, -0.1))

        val clusters = BenchClusterer.cluster(twins, zoom = 20)

        assertEquals(1, clusters.size)
        assertEquals(2, clusters.single().size)
    }

    @Test
    fun everyBenchIsInExactlyOneCluster() {
        val grid = (0 until 400).map { bench(it, 51.0 + (it / 20) * 0.01, -2.0 + (it % 20) * 0.01) }

        for (zoom in 2..18) {
            val ids = BenchClusterer.cluster(grid, zoom).flatMap { c -> c.benches.map { it.id } }
            assertEquals("zoom $zoom", grid.map { it.id }.sorted(), ids.sorted())
        }
    }

    @Test
    fun countsAreAbbreviatedLikeTheWebsite() {
        assertEquals("999", abbreviateCount(999))
        assertEquals("1k", abbreviateCount(1000))
        assertEquals("1.2k", abbreviateCount(1234))
        assertEquals("39k", abbreviateCount(39_904))
    }

    @Test
    fun distancesAreHumanReadable() {
        assertEquals("under 10 m", formatDistance(3.0))
        assertEquals("350 m", formatDistance(354.0))
        assertEquals("2.4 km", formatDistance(2_412.0))
        assertEquals(
            "Bath to Oxford is about 86 km as the crow flies",
            86L, (distanceMeters(51.3811, -2.3590, 51.7520, -1.2577) / 1000).toLong(),
        )
    }
}
