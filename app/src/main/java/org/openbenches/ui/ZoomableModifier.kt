package org.openbenches.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 3f

/**
 * Pinch to zoom (up to 8×, for reading plaques), drag to pan once zoomed, double-tap to zoom in
 * on that spot or back out. Zooming keeps the point under the fingers in place. Single-finger
 * drags at 1× are left alone, so a surrounding pager can still swipe.
 */
fun Modifier.zoomable(): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float = scale): Offset {
        val maxX = size.width * (s - 1) / 2f
        val maxY = size.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Scales by [zoom] around [focus] (in the element's coordinates), keeping that point still. */
    fun zoomAround(focus: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val applied = newScale / scale
        val center = Offset(size.width / 2f, size.height / 2f)
        // The layer scales about its centre, so a point p on screen moves to center + offset + s·(q − center).
        offset = clamp(offset + (focus - center - offset) * (1 - applied), newScale)
        scale = newScale
    }

    this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { tap ->
                if (scale > 1f) {
                    scale = 1f
                    offset = Offset.Zero
                } else {
                    zoomAround(tap, DOUBLE_TAP_SCALE)
                }
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1 || scale > 1f) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) zoomAround(event.calculateCentroid(useCurrent = true), zoom)
                        offset = clamp(offset + event.calculatePan())
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
}
