package org.openbenches.ui.add

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.openbenches.R
import org.openbenches.data.Bench
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.GroundBrown
import org.openbenches.ui.theme.InscriptionStyle
import org.openbenches.ui.theme.PanelBorder
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.SkyBlue
import org.openbenches.ui.theme.SoftBlack
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.theme.Texture
import org.openbenches.ui.theme.RoundIconButton
import org.openbenches.ui.theme.panel
import org.openbenches.ui.theme.textured
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.util.Locale
import kotlin.math.abs

/**
 * Adding a bench, laid out like the website's add page: photos, the inscription on a plaque,
 * where it is, and tags. As on the website, the first photo must be geotagged: its location places
 * the pin, which can then be fine-tuned.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBenchScreen(
    vm: AddBenchViewModel,
    onTakePhoto: () -> Unit,
    onClose: (savedForLater: Boolean) -> Unit,
    onAdded: (Bench) -> Unit,
) {
    LaunchedEffect(vm.addedBenchId) {
        val id = vm.addedBenchId ?: return@LaunchedEffect
        val where = vm.location ?: return@LaunchedEffect
        onAdded(Bench(id, listOf(where.second, where.first), vm.inscription.trim()))
    }

    var confirmClose by remember { mutableStateOf(false) }
    val close = {
        when {
            // A draft keeps whatever was done to it, like an email draft.
            vm.isSavedDraft -> { vm.saveDraft(); onClose(true) }
            vm.photos.isNotEmpty() -> confirmClose = true
            else -> onClose(false)
        }
    }
    BackHandler(enabled = !vm.submitting, onBack = close)

    // The system file chooser rather than the photo picker: the picker strips photos' location.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addPhotos(uris.take(MAX_PHOTOS - vm.photos.size))
    }

    Column(Modifier.fillMaxSize().textured(SoftWhite, Texture.Wood, RectangleShape).testTag("addBench")) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Filled.Close, "Close", onClick = close, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (vm.isSavedDraft) "Finish this bench" else "Add a bench",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.panel(SkyBlue, Texture.Paper, RoundedCornerShape(50), 2.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepLabel("1 · Photos")
            PhotosPanel(
                vm = vm,
                onTakePhoto = onTakePhoto,
                onChoosePhotos = { gallery.launch(arrayOf("image/*")) },
            )

            StepLabel("2 · Inscription")
            InscriptionPanel(vm)

            StepLabel("3 · Where is it?")
            LocationPanel(vm)

            if (vm.tags.isNotEmpty()) {
                StepLabel("4 · Tags (optional)")
                FlowRow(
                    Modifier.fillMaxWidth().panel(SoftWhite, Texture.Paper).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    vm.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.text in vm.selectedTags,
                            onClick = { vm.toggleTag(tag.text) },
                            label = { Text(tag.text) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = SoftWhite, selectedContainerColor = SkyBlue),
                            border = BorderStroke(1.5.dp, BorderBlack),
                        )
                    }
                }
            }

            Text(
                "Your photos will be published under CC BY-SA 4.0. You're adding this bench anonymously.",
                style = MaterialTheme.typography.bodySmall,
                color = SoftWhite,
                modifier = Modifier.fillMaxWidth().panel(GroundBrown, Texture.Paper).padding(12.dp),
            )
        }

        Column(
            Modifier.fillMaxWidth().textured(SoftWhite, Texture.Paper, RectangleShape).navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.submitError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            vm.missing?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.photos.isNotEmpty() && vm.addedBenchId == null) {
                    OutlinedButton(
                        onClick = { if (vm.saveDraft()) onClose(true) },
                        enabled = !vm.submitting,
                        modifier = Modifier.height(52.dp).testTag("saveForLater"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SoftWhite, contentColor = SoftBlack),
                        border = BorderStroke(2.dp, BorderBlack),
                    ) { Text("Save for later", style = MaterialTheme.typography.labelLarge) }
                }
                Button(
                    onClick = { vm.submit() },
                    enabled = vm.missing == null && !vm.submitting,
                    modifier = Modifier.weight(1f).height(52.dp).testTag("submitBench"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = PlaqueGold, contentColor = SoftBlack),
                    border = BorderStroke(2.dp, BorderBlack),
                ) { Text(if (vm.submitError != null) "Try again" else "Add bench", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    if (vm.submitting) UploadingDialog(vm.progress)

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Keep this bench?") },
            text = { Text("Save it to finish later, or discard its photos and inscription.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; vm.saveDraft(); onClose(true) }) { Text("Save for later") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmClose = false; vm.discard(); onClose(false) }) { Text("Discard") }
                    TextButton(onClick = { confirmClose = false }) { Text("Keep editing") }
                }
            },
        )
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = SoftWhite,
        modifier = Modifier.padding(top = 4.dp).panel(GroundBrown, Texture.Paper, RoundedCornerShape(50), 1.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PhotosPanel(vm: AddBenchViewModel, onTakePhoto: () -> Unit, onChoosePhotos: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().panel(SkyBlue, Texture.Paper).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vm.photos.isEmpty()) {
            Text(
                "Start with a geotagged photo of the inscription: one taken with location turned on in your camera. Up to $MAX_PHOTOS photos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("draftPhotos")) {
                items(vm.photos, key = { it.id }) { photo -> PhotoTile(photo, vm) }
            }
        }
        if (vm.preparingPhoto) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SoftBlack, trackColor = PlaqueGold)
        vm.photoError?.let { message ->
            val uriHandler = LocalUriHandler.current
            Column(
                Modifier.fillMaxWidth().panel(SoftWhite, Texture.Paper, MaterialTheme.shapes.small, 2.dp).padding(12.dp).testTag("photoError"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { uriHandler.openUri(GPS_HELP_URL) }, contentPadding = PaddingValues(0.dp)) {
                    Text("How to turn on photo location", color = SoftBlack, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (vm.photos.size < MAX_PHOTOS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("Take photo", Icons.Filled.PhotoCamera, onTakePhoto, Modifier.weight(1f))
                PanelButton("Choose photos", Icons.Filled.AddPhotoAlternate, onChoosePhotos, Modifier.weight(1f).testTag("choosePhotos"))
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: DraftPhoto, vm: AddBenchViewModel) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            AsyncImage(
                model = photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(126.dp).border(PanelBorder, BorderBlack, shape).clip(shape)
                    .semantics { contentDescription = "Photo: ${MEDIA_TYPES.first { it.first == photo.mediaType }.second}" },
            )
            RoundIconButton(
                Icons.Filled.Close, "Remove photo", onClick = { vm.removePhoto(photo) },
                size = 30.dp, modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // What the photo shows, as the website's "This photo is a:" choice.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.selectableGroup()) {
            MEDIA_TYPES.forEach { (type, label) ->
                val selected = photo.mediaType == type
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                        .panel(
                            if (selected) PlaqueGold else SoftWhite,
                            if (selected) Texture.Brushed else Texture.Paper,
                            MaterialTheme.shapes.small,
                            if (selected) 2.dp else 1.dp,
                        )
                        .selectable(selected = selected, role = Role.RadioButton) { vm.setMediaType(photo, type) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun InscriptionPanel(vm: AddBenchViewModel) {
    Column(
        Modifier.fillMaxWidth().panel(PlaqueGold, Texture.Brushed).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            value = vm.inscription,
            onValueChange = { vm.inscription = it },
            textStyle = InscriptionStyle.copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(SoftBlack),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).testTag("inscriptionField"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (vm.inscription.isEmpty()) {
                        Text(
                            if (vm.readingText) "Reading the inscription…" else "Type the inscription exactly as it appears on the bench",
                            style = InscriptionStyle.copy(color = SoftBlack.copy(alpha = 0.45f), fontSize = 18.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    inner()
                }
            },
        )
        vm.textNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        if (vm.photos.isNotEmpty()) {
            TextButton(onClick = { vm.readInscription() }, enabled = !vm.readingText) {
                if (vm.readingText) CircularProgressIndicator(Modifier.size(16.dp), color = SoftBlack, strokeWidth = 2.dp)
                else Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = SoftBlack, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Read from photo", color = SoftBlack, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LocationPanel(vm: AddBenchViewModel) {
    Column(
        Modifier.fillMaxWidth().panel(SkyBlue, Texture.Paper).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val location = vm.location
        if (location == null) {
            Text(
                if (vm.photos.isEmpty()) "The location comes from your first photo."
                else "Your first photo has no location. Remove it, or add a geotagged photo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.fillMaxWidth().height(240.dp).border(PanelBorder, BorderBlack, shape).clip(shape).testTag("locationMap")) {
            PinMap(location, onMoved = vm::moveLocation)
            // The pin stays put while the map moves under it, as in map apps' "set location" screens.
            Image(
                painterResource(R.drawable.marker),
                contentDescription = "Bench position",
                modifier = Modifier.align(Alignment.Center).width(48.dp).offset(y = (-16).dp),
            )
        }
        Text(
            if (vm.pinMoved) "Moved from your photo's location."
            else "From your photo's location. Move the map if the pin isn't on the bench.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.UK, "%.6f, %.6f", location.first, location.second),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.weight(1f),
            )
            if (vm.pinMoved) {
                TextButton(onClick = vm::resetPin) {
                    Text("Reset", color = SoftBlack, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A small map whose centre is the bench's position; panning it moves the bench. */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun PinMap(location: Pair<Double, Double>?, onMoved: (Pair<Double, Double>) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                minZoomLevel = 3.0
                maxZoomLevel = 19.0
                controller.setZoom(if (location != null) 18.0 else 5.0)
                controller.setCenter(location?.let { GeoPoint(it.first, it.second) } ?: GeoPoint(54.2, -2.6))
                // Only the user's own panning moves the bench; the map centring itself doesn't.
                var touched = false
                setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) touched = true
                    if (event.action == MotionEvent.ACTION_UP) view.performClick()
                    false
                }
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        if (touched) onMoved(mapCenter.latitude to mapCenter.longitude)
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?) = false
                })
            }
        },
        update = { map ->
            val target = location ?: return@AndroidView
            val c = map.mapCenter
            if (abs(c.latitude - target.first) > 1e-6 || abs(c.longitude - target.second) > 1e-6) {
                if (map.zoomLevelDouble < 15) map.controller.setZoom(18.0)
                map.controller.setCenter(GeoPoint(target.first, target.second))
            }
        },
        onRelease = { it.onDetach() },
    )
}

@Composable
private fun PanelButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = PlaqueGold, contentColor = SoftBlack),
        border = BorderStroke(2.dp, BorderBlack),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun UploadingDialog(progress: Float) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxWidth().panel(SkyBlue, Texture.Paper).padding(20.dp).testTag("uploading"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Adding your bench", style = MaterialTheme.typography.titleLarge)
            if (progress < 1f) {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = SoftBlack, trackColor = PlaqueGold)
                Text("Uploading photos… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = SoftBlack, trackColor = PlaqueGold)
                Text("Saving the bench…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
