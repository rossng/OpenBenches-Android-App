package org.openbenches.ui.browse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.openbenches.BuildConfig
import org.openbenches.data.Bench
import org.openbenches.map.BenchCluster
import org.openbenches.map.BenchLayerOverlay
import org.openbenches.map.Mercator
import org.openbenches.ui.theme.LocationBlue
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Lets the screen move the map without recreating it. */
class BenchMapController {
    internal var mapView: MapView? = null
    internal var locationOverlay: MyLocationNewOverlay? = null

    val zoom: Double get() = mapView?.zoomLevelDouble ?: 0.0

    /** Flies to a point, keeping it [aboveCenterDp] above the centre (e.g. clear of the bottom sheet). */
    fun flyTo(latitude: Double, longitude: Double, zoom: Double? = null, aboveCenterDp: Double = 0.0) {
        val map = mapView ?: return
        val z = zoom ?: map.zoomLevelDouble
        val centerLat = Mercator.centerLatitudeFor(latitude, z, aboveCenterDp)
        map.controller.animateTo(GeoPoint(centerLat, longitude), z, 600L)
    }

    /** Zooms in on a cluster until it splits, like the website does on a cluster click. */
    fun zoomInto(cluster: BenchCluster) {
        val map = mapView ?: return
        val lats = cluster.benches.map { it.lat }
        val lngs = cluster.benches.map { it.lng }
        val box = BoundingBox(lats.max(), lngs.max(), lats.min(), lngs.min())
        val border = (64 * map.resources.displayMetrics.density).toInt()
        map.zoomToBoundingBox(box, true, border, 18.0, 600L)
    }

    fun myLocation(): GeoPoint? = locationOverlay?.myLocation
}

@Composable
fun BenchMap(
    benches: List<Bench>,
    selected: Bench?,
    mapController: BenchMapController,
    initialCamera: Camera,
    showMyLocation: Boolean,
    onCameraChanged: (Camera) -> Unit,
    onBenchTap: (Bench) -> Unit,
    onClusterTap: (BenchCluster) -> Unit,
    onMapTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val overlay = remember {
        BenchLayerOverlay(context, onBenchTap = { onBenchTap(it) }, onClusterTap = { onClusterTap(it) }, onEmptyTap = { onMapTap() })
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                // The OSM tile usage policy asks for an identifying user agent.
                userAgentValue = "OpenBenches-Android/${BuildConfig.VERSION_NAME}"
            }
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                isHorizontalMapRepetitionEnabled = true
                isVerticalMapRepetitionEnabled = false
                setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                controller.setZoom(initialCamera.zoom)
                controller.setCenter(GeoPoint(initialCamera.latitude, initialCamera.longitude))
                overlays.add(overlay)
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?) = report()
                    override fun onZoom(event: ZoomEvent?) = report()
                    private fun report(): Boolean {
                        val c = mapCenter
                        onCameraChanged(Camera(c.latitude, c.longitude, zoomLevelDouble))
                        return false
                    }
                })
                mapController.mapView = this
            }
        },
        update = { mapView ->
            overlay.setBenches(benches, mapView)
            overlay.selectedBench = selected
            if (showMyLocation && mapController.locationOverlay == null) {
                val location = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
                    val dot = blueDot(context)
                    setPersonIcon(dot)
                    setDirectionIcon(dot)
                    setPersonAnchor(0.5f, 0.5f)
                    setDirectionAnchor(0.5f, 0.5f)
                    enableMyLocation()
                }
                mapView.overlays.add(0, location)
                mapController.locationOverlay = location
            }
            mapView.invalidate()
        },
        onRelease = { mapView ->
            mapController.locationOverlay?.disableMyLocation()
            mapController.locationOverlay = null
            mapController.mapView = null
            overlay.shutdown()
            mapView.onDetach()
        },
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapController.mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapController.mapView?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** A blue location dot with a white ring, the convention in map apps. */
private fun blueDot(context: Context): Bitmap {
    val d = context.resources.displayMetrics.density
    val size = (24 * d).toInt()
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x33000000
        c.drawCircle(size / 2f, size / 2f + d, 10 * d, p)
        p.color = android.graphics.Color.WHITE
        c.drawCircle(size / 2f, size / 2f, 10 * d, p)
        p.color = LocationBlue.toArgb()
        c.drawCircle(size / 2f, size / 2f, 7 * d, p)
    }
}
