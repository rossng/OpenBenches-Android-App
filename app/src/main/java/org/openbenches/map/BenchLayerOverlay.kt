package org.openbenches.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.toArgb
import org.openbenches.R
import org.openbenches.data.Bench
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.ClusterLarge
import org.openbenches.ui.theme.ClusterMedium
import org.openbenches.ui.theme.ClusterSmall
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.SoftBlack
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.floor

/**
 * Draws every bench as either a cluster circle or the website's bench marker, and reports taps.
 * Clusters are computed per integer zoom level off the main thread and cached.
 */
class BenchLayerOverlay(
    context: Context,
    private val onBenchTap: (Bench) -> Unit,
    private val onClusterTap: (BenchCluster) -> Unit,
    private val onEmptyTap: () -> Unit,
) : Overlay() {
    private val density = context.resources.displayMetrics.density
    private val executor = Executors.newSingleThreadExecutor()
    private val clustersByZoom = ConcurrentHashMap<Int, List<BenchCluster>>()
    private var generation = 0

    private val marker: Bitmap = scaledMarker(context, 40f)
    private val selectedMarker: Bitmap = scaledMarker(context, 56f)

    private val typeface = ResourcesCompat.getFont(context, R.font.varela_round)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = BorderBlack.toArgb()
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = PlaqueGold.toArgb(); alpha = 220 }
    private val haloStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = BorderBlack.toArgb()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SoftBlack.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = this@BenchLayerOverlay.typeface
        isFakeBoldText = true
    }

    private val hitTargets = ArrayList<Pair<RectF, BenchCluster>>()
    private val point = Point()

    var benches: List<Bench> = emptyList()
        private set
    var selectedBench: Bench? = null

    /** Replaces the benches and precomputes clusters, nearest zoom levels first. */
    fun setBenches(newBenches: List<Bench>, mapView: MapView) {
        if (newBenches === benches) return
        benches = newBenches
        clustersByZoom.clear()
        val gen = ++generation
        val current = floor(mapView.zoomLevelDouble).toInt()
        val order = (0..20).sortedBy { kotlin.math.abs(it - current) }
        executor.execute {
            for (zoom in order) {
                if (gen != generation) return@execute
                clustersByZoom[zoom] = BenchClusterer.cluster(newBenches, zoom)
                if (zoom == current) mapView.postInvalidate()
            }
            mapView.postInvalidate()
        }
    }

    fun shutdown() = executor.shutdownNow()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitTargets.clear()
        val zoom = floor(mapView.zoomLevelDouble).toInt().coerceIn(0, 20)
        val clusters = clustersByZoom[zoom] ?: nearestComputed(zoom) ?: return
        val projection = mapView.projection
        val box = projection.boundingBox
        val latPad = (box.latNorth - box.latSouth) * 0.2
        val lngPad = (box.lonEast - box.lonWest) * 0.2

        for (cluster in clusters) {
            if (cluster.latitude !in (box.latSouth - latPad)..(box.latNorth + latPad)) continue
            if (box.lonWest <= box.lonEast && cluster.longitude !in (box.lonWest - lngPad)..(box.lonEast + lngPad)) continue
            if (cluster.isSingle && cluster.benches[0] == selectedBench) continue
            projection.toPixels(GeoPoint(cluster.latitude, cluster.longitude), point)
            if (cluster.isSingle) drawMarker(canvas, cluster, marker) else drawCluster(canvas, cluster)
        }
        // The selected bench is always drawn on top, larger, on a gold spot, even inside a cluster.
        selectedBench?.let { bench ->
            projection.toPixels(GeoPoint(bench.lat, bench.lng), point)
            val spot = RectF(point.x - 30f * density, point.y - 8f * density, point.x + 30f * density, point.y + 8f * density)
            canvas.drawOval(spot, halo)
            canvas.drawOval(spot, haloStroke)
            drawMarker(canvas, BenchCluster(bench.lat, bench.lng, listOf(bench)), selectedMarker)
        }
    }

    private fun nearestComputed(zoom: Int): List<BenchCluster>? =
        clustersByZoom.keys.minByOrNull { kotlin.math.abs(it - zoom) }?.let { clustersByZoom[it] }

    private fun drawMarker(canvas: Canvas, cluster: BenchCluster, bitmap: Bitmap) {
        val left = point.x - bitmap.width / 2f
        val top = point.y - bitmap.height.toFloat()
        canvas.drawBitmap(bitmap, left, top, null)
        val slop = 6f * density
        hitTargets.add(RectF(left - slop, top - slop, left + bitmap.width + slop, point.y + slop) to cluster)
    }

    private fun drawCluster(canvas: Canvas, cluster: BenchCluster) {
        // Sizes and colours follow the website's MapLibre cluster layer; slightly more opaque for small screens.
        val (radiusDp, color) = when {
            cluster.size < 100 -> 20f to ClusterSmall
            cluster.size < 750 -> 26f to ClusterMedium
            else -> 32f to ClusterLarge
        }
        val r = radiusDp * density
        fill.color = color.toArgb()
        fill.alpha = 200
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        canvas.drawCircle(x, y, r, fill)
        canvas.drawCircle(x, y, r, stroke)
        text.textSize = (if (cluster.size < 100) 15f else 16f) * density
        canvas.drawText(abbreviateCount(cluster.size), x, y - (text.descent() + text.ascent()) / 2, text)
        hitTargets.add(RectF(x - r, y - r, x + r, y + r) to cluster)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = hitTargets.asReversed().firstOrNull { it.first.contains(e.x, e.y) }?.second
        when {
            hit == null -> { onEmptyTap(); return false }
            hit.isSingle -> onBenchTap(hit.benches.single())
            else -> onClusterTap(hit)
        }
        return true
    }

    private companion object {
        fun scaledMarker(context: Context, widthDp: Float): Bitmap {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.marker)
            val width = (widthDp * context.resources.displayMetrics.density).toInt()
            return Bitmap.createScaledBitmap(raw, width, width * raw.height / raw.width, true)
        }
    }
}
