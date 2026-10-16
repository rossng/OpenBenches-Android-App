package org.openbenches.ui.add

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.GroundBrown
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.RoundIconButton
import org.openbenches.ui.theme.SkyBlue
import org.openbenches.ui.theme.SoftBlack
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.theme.Texture
import org.openbenches.ui.theme.panel
import kotlin.math.roundToInt

/** What to photograph for each shot, in the website's order. */
private val SHOTS = listOf(
    "The inscription" to "Fill the frame with the plaque so the words are sharp",
    "The bench" to "Step back to show the whole bench",
    "The view" to "Sit on the bench and photograph what it looks at",
    "One more" to "Anything else worth a photo (optional)",
)

/** A fix older than this isn't used to geotag a photo. */
private const val MAX_FIX_AGE_MS = 2 * 60 * 1000L

/**
 * A camera for photographing benches in a row while out and about. Each photo is geotagged as
 * it's taken, like a camera app with location turned on. After the first photo, "Next bench" saves
 * this one for later and starts another; "Done" goes back to the form.
 *
 * @param savedThisOuting benches saved for later since the camera was opened, for the counter.
 */
@Composable
fun CaptureScreen(
    vm: AddBenchViewModel,
    savedThisOuting: Int,
    onSaveAndNext: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasCamera by remember { mutableStateOf(granted(context, Manifest.permission.CAMERA)) }
    var hasLocation by remember { mutableStateOf(granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasCamera = result[Manifest.permission.CAMERA] ?: hasCamera
        hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocation
    }
    LaunchedEffect(Unit) {
        val needed = listOfNotNull(
            Manifest.permission.CAMERA.takeUnless { hasCamera },
            Manifest.permission.ACCESS_FINE_LOCATION.takeUnless { hasLocation },
        )
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    val fix = rememberLocationFix(enabled = hasLocation)
    // Closing never loses photos: the caller goes back to the form if this bench has any.
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("capture")) {
        if (hasCamera) {
            CameraWithControls(vm, fix, onSaveAndNext, onDone)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("OpenBenches needs the camera to photograph benches.", color = SoftWhite, textAlign = TextAlign.Center)
                GoldButton("Allow camera") { permissions.launch(arrayOf(Manifest.permission.CAMERA)) }
                TextButton(onClick = onClose) { Text("Back", color = SoftWhite) }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Filled.Close, "Close camera", onClick = onClose, size = 40.dp)
            Spacer(Modifier.weight(1f))
            if (savedThisOuting > 0) {
                Text(
                    "$savedThisOuting saved",
                    style = MaterialTheme.typography.labelLarge,
                    color = SoftWhite,
                    modifier = Modifier.panel(GroundBrown, Texture.Paper, RoundedCornerShape(50), 2.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("savedCount")
                        .semantics { contentDescription = "$savedThisOuting benches saved to finish later" },
                )
                Spacer(Modifier.width(8.dp))
            }
            LocationChip(fix, hasLocation, onAllow = { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) })
        }
    }

}

@Composable
private fun CameraWithControls(
    vm: AddBenchViewModel,
    fix: Location?,
    onSaveAndNext: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val flash = remember { Animatable(0f) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply {
                    // A TextureView-backed preview, which also shows up in screenshots and screen recordings.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                    // Show the whole frame, letterboxed: photos are cropped to what the preview shows,
                    // and filling a tall screen would cut most of the bench out.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                } },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))

        val shot = vm.photos.size
        val canShoot = shot < MAX_PHOTOS && !capturing && fix != null
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                .navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // What to photograph next.
            val (title, hint) = SHOTS[shot.coerceAtMost(SHOTS.lastIndex)]
            Text(
                if (shot < MAX_PHOTOS) "Photo ${shot + 1} · $title" else "That's $MAX_PHOTOS photos",
                style = MaterialTheme.typography.titleMedium,
                color = SoftBlack,
                modifier = Modifier.panel(PlaqueGold, Texture.Brushed, RoundedCornerShape(50), 2.dp)
                    .padding(horizontal = 14.dp, vertical = 4.dp).testTag("shotTitle"),
            )
            Text(
                error ?: if (shot < MAX_PHOTOS) hint else "Save this bench or finish it now",
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) PlaqueGold else SoftWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            // Always the same height, so the shutter never moves under the user's thumb.
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(vm.photos, key = { it.id }) { photo ->
                        Box {
                            AsyncImage(
                                model = photo.file,
                                contentDescription = MEDIA_TYPES.firstOrNull { it.first == photo.mediaType }?.second,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, SoftWhite, RoundedCornerShape(8.dp)),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(SoftBlack)
                                    .clickable { vm.removePhoto(photo) }
                                    .semantics { contentDescription = "Remove photo" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, contentDescription = null, tint = SoftWhite, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.photos.isNotEmpty()) {
                        TextButton(onClick = onDone, modifier = Modifier.testTag("cameraDone")) {
                            Text("Done", color = SoftWhite, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Shutter(
                    enabled = canShoot,
                    busy = capturing,
                    onClick = {
                        val location = fix ?: return@Shutter
                        capturing = true
                        error = null
                        val file = vm.newPhotoFile()
                        val options = ImageCapture.OutputFileOptions.Builder(file)
                            // Geotag the photo as it's taken, as a camera app with location on does.
                            .setMetadata(ImageCapture.Metadata().apply { this.location = location })
                            .build()
                        scope.launch { flash.snapTo(0.7f); flash.animateTo(0f, tween(250)) }
                        controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                capturing = false
                                vm.addCapturedPhoto(file, location.latitude to location.longitude)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                file.delete()
                                error = "Couldn't take the photo. Try again."
                            }
                        })
                    },
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (vm.photos.isNotEmpty()) {
                        Button(
                            onClick = onSaveAndNext,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = PlaqueGold, contentColor = SoftBlack),
                            border = BorderStroke(2.dp, BorderBlack),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.testTag("saveAndNext"),
                        ) { Text("Next bench", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

        }
    }
}

/** A big round shutter in plaque gold. */
@Composable
private fun Shutter(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).border(4.dp, SoftWhite, CircleShape).padding(6.dp)
            .clip(CircleShape).background(if (enabled) PlaqueGold else PlaqueGold.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Take photo" }
            .testTag("shutter"),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = SoftBlack, strokeWidth = 3.dp)
    }
}

/** Shows how good the location fix is; photos can only be taken once there is one. */
@Composable
private fun LocationChip(fix: Location?, hasPermission: Boolean, onAllow: () -> Unit) {
    val modifier = Modifier.panel(if (fix != null) SkyBlue else SoftWhite, Texture.Paper, RoundedCornerShape(50), 2.dp)
        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("locationChip")
    Row(
        if (hasPermission) modifier else modifier.clickable(onClick = onAllow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = SoftBlack, modifier = Modifier.size(18.dp))
        Text(
            when {
                !hasPermission -> "Allow location to geotag photos"
                fix == null -> "Finding your location…"
                else -> "Location ±${fix.accuracy.roundToInt().coerceAtLeast(1)} m"
            },
            style = MaterialTheme.typography.labelLarge,
            color = SoftBlack,
        )
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = PlaqueGold, contentColor = SoftBlack),
        border = BorderStroke(2.dp, BorderBlack),
        modifier = Modifier.height(48.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** A fresh, accurate location while the camera is open, or null until there is one. */
@SuppressLint("MissingPermission")
@Composable
private fun rememberLocationFix(enabled: Boolean): Location? {
    val context = LocalContext.current
    var fix by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(context)
        fun accept(l: Location?) {
            if (l != null && System.currentTimeMillis() - l.time < MAX_FIX_AGE_MS) fix = l
        }
        client.lastLocation.addOnSuccessListener(::accept)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) = accept(result.lastLocation)
        }
        client.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).build(),
            callback,
            Looper.getMainLooper(),
        )
        onDispose { client.removeLocationUpdates(callback) }
    }
    return fix
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
