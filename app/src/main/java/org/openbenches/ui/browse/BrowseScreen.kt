package org.openbenches.ui.browse

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import org.openbenches.ui.add.AddBenchViewModel
import org.openbenches.ui.add.AddBenchScreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openbenches.BuildConfig
import org.openbenches.R
import org.openbenches.data.Bench
import org.openbenches.map.BenchCluster
import org.openbenches.map.distanceMeters
import org.openbenches.map.formatDistance
import org.openbenches.ui.theme.BorderBlack
import org.openbenches.ui.theme.PanelBorder
import org.openbenches.ui.theme.PlaqueGold
import org.openbenches.ui.theme.SkyBlue
import org.openbenches.ui.theme.SoftBlack
import org.openbenches.ui.theme.SoftWhite
import org.openbenches.ui.theme.Texture
import org.openbenches.ui.theme.panel
import org.openbenches.ui.theme.textured
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.resume

private val SheetPeek = 330.dp
private val Britain = Camera(54.2, -2.6, 6.0)
private const val WEBSITE = "https://www.openbenches.org/"

/** The map with the search bar on top and the selected bench in a bottom sheet, as in other map apps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: BrowseViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapController = remember { BenchMapController() }
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peek = SheetPeek + navBarBottom

    // Location: ask once on first launch, but the map works without it.
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var centeredOnUser by rememberSaveable { mutableStateOf(vm.camera != null) }
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result.values.any { it }
    }
    LaunchedEffect(Unit) {
        if (!hasLocation && !askedForLocation) {
            askedForLocation = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(hasLocation) {
        if (!hasLocation) return@LaunchedEffect
        val here = lastKnownLocation(context) ?: return@LaunchedEffect
        userLocation = here
        if (!centeredOnUser) {
            centeredOnUser = true
            mapController.flyTo(here.first, here.second, zoom = 16.0)
        }
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val selected = vm.selected
    LaunchedEffect(selected?.id) {
        if (selected != null) sheetState.partialExpand() else sheetState.hide()
    }
    LaunchedEffect(sheetState) {
        // Swiping the sheet away deselects the bench.
        snapshotFlow { sheetState.currentValue }.drop(1).collect { value ->
            if (value == SheetValue.Hidden) vm.clearSelection()
        }
    }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addSession by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(enabled = selected != null && !searchOpen && !adding) {
        if (sheetState.currentValue == SheetValue.Expanded) scope.launch { sheetState.partialExpand() } else vm.clearSelection()
    }

    fun showBench(bench: Bench, stack: List<Bench> = listOf(bench), zoom: Double? = null) {
        vm.select(bench, stack)
        mapController.flyTo(bench.lat, bench.lng, zoom, aboveCenterDp = SheetPeek.value / 2.0)
    }

    fun onCluster(cluster: BenchCluster) {
        val first = cluster.benches.first()
        val samePlace = cluster.benches.all { distanceMeters(first.lat, first.lng, it.lat, it.lng) < 15 }
        if (samePlace || mapController.zoom >= 18.5) showBench(first, cluster.benches) else mapController.zoomInto(cluster)
    }

    val fabBottom by animateDpAsState(
        if (selected != null && sheetState.targetValue != SheetValue.Hidden) peek + 16.dp else navBarBottom + 32.dp,
        label = "fab",
    )

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peek,
            sheetDragHandle = null,
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            sheetContainerColor = Color.Transparent,
            sheetShadowElevation = 12.dp,
            sheetContent = {
                val bench = selected
                if (bench == null) {
                    Spacer(Modifier.height(1.dp))
                } else {
                    val details = vm.details[bench.id]
                    BenchSheet(
                        bench = bench,
                        stack = vm.selection,
                        details = details,
                        deviceAddress = vm.deviceAddresses[bench.id],
                        distance = userLocation?.let { formatDistance(distanceMeters(it.first, it.second, bench.lat, bench.lng)) },
                        actions = BenchSheetActions(
                            onClose = { vm.clearSelection() },
                            onDirections = { openDirections(context, bench) },
                            onShare = { shareBench(context, bench) },
                            onOpenWebsite = { openUrl(context, "${BuildConfig.OPENBENCHES_BASE_URL}bench/${bench.id}") },
                            onPhoto = { viewerIndex = it },
                            onRetry = { vm.retryDetails(bench) },
                            onPickFromStack = { vm.showFromStack(it) },
                        ),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                    val photos = (details as? DetailsState.Loaded)?.feature?.properties?.media.orEmpty().filter { it.url.startsWith("/image/") }
                    viewerIndex?.let { index ->
                        if (index < photos.size) PhotoViewer(photos, index, onDismiss = { viewerIndex = null })
                    }
                }
            },
            containerColor = SoftWhite,
        ) {
            Box(Modifier.fillMaxSize()) {
                BenchMap(
                    benches = vm.benches,
                    selected = selected,
                    mapController = mapController,
                    initialCamera = remember { vm.camera ?: Britain },
                    showMyLocation = hasLocation,
                    onCameraChanged = { vm.camera = it },
                    onBenchTap = { showBench(it) },
                    onClusterTap = { onCluster(it) },
                    onMapTap = { if (selected != null) vm.clearSelection() },
                    modifier = Modifier.fillMaxSize().testTag("map"),
                )
                Text(
                    "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftBlack,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = navBarBottom + 4.dp)
                        .background(SoftWhite.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .clickable { openUrl(context, "https://www.openstreetmap.org/copyright") }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = fabBottom),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                FloatingActionButton(
                    onClick = {
                        if (!hasLocation) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        } else scope.launch {
                            val here = mapController.myLocation()?.let { it.latitude to it.longitude } ?: lastKnownLocation(context)
                            if (here != null) {
                                userLocation = here
                                val sheetUp = vm.selected != null && sheetState.currentValue != SheetValue.Hidden
                                mapController.flyTo(
                                    here.first, here.second, zoom = maxOf(mapController.zoom, 16.0),
                                    aboveCenterDp = if (sheetUp) SheetPeek.value / 2.0 else 0.0,
                                )
                            }
                        }
                    },
                    containerColor = SoftWhite,
                    contentColor = SoftBlack,
                    shape = CircleShape,
                    modifier = Modifier.border(PanelBorder, BorderBlack, CircleShape),
                ) { Icon(Icons.Filled.MyLocation, contentDescription = "Show my location") }
                // The website's gold "Add bench" button, as the map's main action.
                AnimatedVisibility(visible = selected == null) {
                    ExtendedFloatingActionButton(
                        onClick = { addSession++; adding = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Add bench", style = MaterialTheme.typography.titleMedium) },
                        containerColor = PlaqueGold,
                        contentColor = SoftBlack,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.border(PanelBorder, BorderBlack, MaterialTheme.shapes.small).testTag("addBenchButton"),
                    )
                }
                }
            }
        }

        // Keeps the status bar readable over busy map tiles.
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(SoftWhite.copy(alpha = 0.85f))
        )

        SearchPill(
            benchCount = vm.benches.size,
            loading = vm.loadingBenches,
            loadError = vm.loadError,
            onOpen = { searchOpen = true },
            onRetry = { vm.loadBenches() },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = navBarBottom + 96.dp))

        AnimatedVisibility(visible = adding, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            // A fresh form each time the button is pressed, kept across rotation and the camera app.
            val addVm: AddBenchViewModel = viewModel(key = "add-bench-$addSession")
            AddBenchScreen(
                vm = addVm,
                onClose = { adding = false },
                onAdded = { bench ->
                    adding = false
                    vm.addUploadedBench(bench)
                    showBench(bench, zoom = 18.0)
                    scope.launch { snackbar.showSnackbar("Bench added. Thank you!") }
                },
            )
        }

        AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
            SearchScreen(
                vm = vm,
                userLocation = userLocation,
                onClose = { searchOpen = false },
                onResult = { bench ->
                    searchOpen = false
                    showBench(bench, zoom = 17.0)
                },
            )
        }
    }
}

/** The collapsed search bar floating over the map: logo, prompt and the overflow menu. */
@Composable
private fun SearchPill(
    benchCount: Int,
    loading: Boolean,
    loadError: String?,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().height(56.dp)
                .panel(SoftWhite, Texture.Paper, RoundedCornerShape(28.dp))
                .clickable(onClick = onOpen)
                .padding(start = 8.dp, end = 4.dp)
                .testTag("searchBar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Logo()
            Spacer(Modifier.width(12.dp))
            Text(
                if (benchCount > 0) "Search ${NumberFormat.getIntegerInstance(Locale.UK).format(benchCount)} benches" else "Search inscriptions",
                style = MaterialTheme.typography.bodyLarge,
                color = SoftBlack.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(
                        "About OpenBenches" to "${WEBSITE}blog/about/",
                        "Leaderboard" to "${WEBSITE}leaderboard",
                        "Privacy and terms" to "${WEBSITE}blog/privacy/",
                        "Colophon" to "${WEBSITE}colophon",
                    ).forEach { (label, url) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; openUrl(context, url) })
                    }
                }
            }
        }
        when {
            loading -> StatusChip { CircularProgressIndicator(Modifier.size(14.dp), color = SoftBlack, strokeWidth = 2.dp); Text("Loading benches…") }
            loadError != null -> StatusChip(Modifier.clickable(onClick = onRetry)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Text("Couldn't load benches. Tap to retry")
            }
        }
    }
}

@Composable
private fun StatusChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.padding(top = 8.dp).panel(SkyBlue, Texture.Paper, RoundedCornerShape(50), 2.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) { content() }
    }
}

/** The website's round header logo (#header-image), with its white ring. */
@Composable
private fun Logo(size: androidx.compose.ui.unit.Dp = 36.dp) {
    Image(
        painterResource(R.drawable.openbenches_logo),
        contentDescription = "OpenBenches",
        modifier = Modifier.size(size).border(2.dp, SoftWhite, CircleShape).clip(CircleShape),
    )
}

/** Full-screen search: type straight away, results update as you type, tap one to see it on the map. */
@Composable
private fun SearchScreen(
    vm: BrowseViewModel,
    userLocation: Pair<Double, Double>?,
    onClose: () -> Unit,
    onResult: (Bench) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().textured(SoftWhite, Texture.Paper, RectangleShape).statusBarsPadding().imePadding().testTag("searchScreen")) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(56.dp)
                .panel(Color.White, Texture.Paper, RoundedCornerShape(28.dp)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to map") }
            BasicTextField(
                value = vm.query,
                onValueChange = vm::updateQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = SoftBlack),
                cursorBrush = SolidColor(SoftBlack),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("searchField"),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.query.isEmpty()) Text("Search inscriptions", style = MaterialTheme.typography.bodyLarge, color = SoftBlack.copy(alpha = 0.5f))
                        inner()
                    }
                },
            )
            if (vm.query.isNotEmpty()) {
                IconButton(onClick = { vm.updateQuery(""); focus.requestFocus() }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            }
        }
        if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp), color = SoftBlack, trackColor = PlaqueGold)

        // Nearest first, as map apps do, when we know where the user is.
        val results = vm.results?.let { list ->
            userLocation?.let { here -> list.sortedBy { distanceMeters(here.first, here.second, it.lat, it.lng) } } ?: list
        }
        when {
            vm.query.trim().length < 3 -> SearchHints(onPick = { vm.updateQuery(it) })
            vm.searchError != null -> Message("Search failed: ${vm.searchError}")
            results == null -> Unit
            results.isEmpty() -> Message("No benches match “${vm.query.trim()}”.")
            else -> LazyColumn(Modifier.fillMaxSize().testTag("searchResults")) {
                item {
                    Text(
                        if (results.size == 1) "1 bench" else "${results.size} benches",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(results, key = { it.id }) { bench ->
                    SearchResultRow(bench, vm.query.trim(), userLocation, onClick = { keyboard?.hide(); onResult(bench) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = BorderBlack.copy(alpha = 0.15f))
                }
                item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SearchResultRow(bench: Bench, query: String, userLocation: Pair<Double, Double>?, onClick: () -> Unit) {
    val lines = bench.popupContent.lines().filter { it.isNotBlank() }
    val title = lines.firstOrNull().orEmpty()
    val subtitle = matchSnippet(lines.drop(1).joinToString(" "), query, skipIfIn = title)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag("searchResult"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(painterResource(R.drawable.marker), contentDescription = null, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftBlack.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        userLocation?.let {
            Text(
                formatDistance(distanceMeters(it.first, it.second, bench.lat, bench.lng)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** The part of [text] around [query], so the reason a bench matched is visible in one line. */
internal fun matchSnippet(text: String, query: String, skipIfIn: String = ""): String {
    if (query.isEmpty() || skipIfIn.contains(query, ignoreCase = true)) return text
    val at = text.indexOf(query, ignoreCase = true)
    if (at <= 24) return text
    val start = text.lastIndexOf(' ', at - 16).let { if (it < 0) 0 else it + 1 }
    return "…" + text.substring(start)
}

@Composable
private fun SearchHints(onPick: (String) -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search the words on any bench.", style = MaterialTheme.typography.bodyLarge)
        // The website's search box placeholder, plus a couple of common themes.
        listOf("in loving memory of", "loved this place", "dog").forEach { hint ->
            Text(
                "“$hint”",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.panel(PlaqueGold, Texture.Brushed, MaterialTheme.shapes.small, 2.dp)
                    .clickable { onPick(hint) }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
}

private fun hasLocationPermission(context: Context) = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@SuppressLint("MissingPermission")
private suspend fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    return suspendCancellableCoroutine { cont ->
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { cont.resume(it?.let { l -> l.latitude to l.longitude }) }
            .addOnFailureListener { cont.resume(null) }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun openDirections(context: Context, bench: Bench) {
    val label = Uri.encode("Bench ${bench.id}")
    val geo = Uri.parse("geo:${bench.lat},${bench.lng}?q=${bench.lat},${bench.lng}($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (_: ActivityNotFoundException) {
        openUrl(context, "https://www.openstreetmap.org/?mlat=${bench.lat}&mlon=${bench.lng}#map=19/${bench.lat}/${bench.lng}")
    }
}

private fun shareBench(context: Context, bench: Bench) {
    val firstLine = bench.popupContent.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val text = "“$firstLine” – a memorial bench on OpenBenches\n${BuildConfig.OPENBENCHES_BASE_URL}bench/${bench.id}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share bench"))
}
