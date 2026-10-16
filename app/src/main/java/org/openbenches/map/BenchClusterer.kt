package org.openbenches.map

import org.openbenches.data.Bench
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** A group of benches drawn as one map symbol. A cluster of one is a single bench. */
data class BenchCluster(
    val latitude: Double,
    val longitude: Double,
    val benches: List<Bench>,
) {
    val size: Int get() = benches.size
    val isSingle: Boolean get() = benches.size == 1
}

/**
 * Greedy grid clustering in Web Mercator pixel space, the same idea as the website's
 * MapLibre clustering (clusterRadius 50, clusterMaxZoom 17).
 */
object BenchClusterer {
    const val MAX_CLUSTER_ZOOM = 17

    /**
     * Clusters [benches] for display at integer [zoom]. Points closer than [radiusPx] screen
     * pixels (at a 256px tile size scaled by [tileScale]) join the first cluster that reaches them.
     * Above [MAX_CLUSTER_ZOOM] only benches at the same spot are grouped.
     */
    fun cluster(benches: List<Bench>, zoom: Int, radiusPx: Double = 50.0, tileScale: Double = 1.0): List<BenchCluster> {
        if (benches.isEmpty()) return emptyList()
        val radius = if (zoom > MAX_CLUSTER_ZOOM) 0.5 else radiusPx
        val worldSize = 256.0 * tileScale * (1 shl zoom.coerceIn(0, 22))
        val xs = DoubleArray(benches.size)
        val ys = DoubleArray(benches.size)
        val grid = HashMap<Long, MutableList<Int>>(benches.size)
        benches.forEachIndexed { i, bench ->
            xs[i] = (bench.lng + 180.0) / 360.0 * worldSize
            val sinLat = sin(bench.lat.coerceIn(-85.05, 85.05) * PI / 180.0)
            ys[i] = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldSize
            grid.getOrPut(cellKey(xs[i], ys[i], radius)) { ArrayList(1) }.add(i)
        }

        val assigned = BooleanArray(benches.size)
        val clusters = ArrayList<BenchCluster>()
        for (i in benches.indices) {
            if (assigned[i]) continue
            assigned[i] = true
            val members = arrayListOf(i)
            val cx = floor(xs[i] / radius).toLong()
            val cy = floor(ys[i] / radius).toLong()
            for (dx in -1L..1L) for (dy in -1L..1L) {
                val cell = grid[key(cx + dx, cy + dy)] ?: continue
                for (j in cell) {
                    if (assigned[j]) continue
                    val ddx = xs[j] - xs[i]
                    val ddy = ys[j] - ys[i]
                    if (sqrt(ddx * ddx + ddy * ddy) <= radius) {
                        assigned[j] = true
                        members.add(j)
                    }
                }
            }
            val memberBenches = members.map { benches[it] }
            clusters.add(
                BenchCluster(
                    latitude = memberBenches.sumOf { it.lat } / members.size,
                    longitude = memberBenches.sumOf { it.lng } / members.size,
                    benches = memberBenches,
                )
            )
        }
        return clusters
    }

    private fun cellKey(x: Double, y: Double, size: Double): Long =
        key(floor(x / size).toLong(), floor(y / size).toLong())

    private fun key(cx: Long, cy: Long): Long = (cx shl 32) xor (cy and 0xFFFFFFFFL)
}

/** "1.2k" style counts, like MapLibre's point_count_abbreviated. */
fun abbreviateCount(count: Int): String = when {
    count >= 10_000 -> "${count / 1000}k"
    count >= 1_000 -> {
        val tenths = count / 100
        if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
    }
    else -> count.toString()
}

/** Great-circle distance in metres. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * Math.atan2(sqrt(a), sqrt(1 - a))
}

/** "350 m" or "2.4 km" ("under 10 m" when it's this close). */
fun formatDistance(meters: Double): String = when {
    meters < 10 -> "under 10 m"
    meters < 1000 -> "${(meters / 10).toInt() * 10} m"
    meters < 100_000 -> String.format(java.util.Locale.UK, "%.1f km", meters / 1000)
    else -> "${(meters / 1000).toInt()} km"
}
