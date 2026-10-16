package org.openbenches.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/** Web Mercator in density-independent pixels, matching osmdroid with tiles scaled to DPI. */
object Mercator {
    private fun worldSizeDp(zoom: Double) = 256.0 * 2.0.pow(zoom)

    fun latToY(lat: Double, zoom: Double): Double {
        val s = sin(lat.coerceIn(-85.05, 85.05) * PI / 180.0)
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * worldSizeDp(zoom)
    }

    fun yToLat(y: Double, zoom: Double): Double {
        val n = PI - 2.0 * PI * y / worldSizeDp(zoom)
        return Math.toDegrees(atan(sinh(n)))
    }

    /**
     * The latitude to centre the map on so that [lat] appears [offsetDp] above the centre,
     * e.g. to keep a bench visible above a bottom sheet.
     */
    fun centerLatitudeFor(lat: Double, zoom: Double, offsetDp: Double): Double =
        yToLat(latToY(lat, zoom) + offsetDp, zoom)
}
