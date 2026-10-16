package org.openbenches.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openbenches.data.Bench
import org.openbenches.data.BenchDetailsFeature
import org.openbenches.data.deviceAddress
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.BenchCache
import org.openbenches.data.CachedBenches
import org.openbenches.data.DELTA_SIZE
import org.openbenches.data.RefreshPlan
import org.openbenches.data.fetchBenches
import org.openbenches.data.fetchLatestBenches
import org.openbenches.data.mergeLatest
import org.openbenches.data.refreshPlan
import java.io.File
import org.openbenches.data.fetchBenchesBySearch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val feature: BenchDetailsFeature) : DetailsState
    data class Failed(val message: String) : DetailsState
}

data class Camera(val latitude: Double, val longitude: Double, val zoom: Double)

/** State for the map + bench sheet + search, kept across configuration changes. */
class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    // Declared first: init { loadBenches() } below reads it.
    private val cache = BenchCache(File(application.filesDir, "benches.json"))

    /** Every bench, as the website's map loads them (GET /api/benches), for clustering on the device. */
    var benches by mutableStateOf<List<Bench>>(emptyList())
        private set
    var loadingBenches by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    /** The selected bench, plus any others at exactly the same spot. */
    var selection by mutableStateOf<List<Bench>>(emptyList())
        private set
    val selected: Bench? get() = selection.firstOrNull()

    val details = mutableStateMapOf<Int, DetailsState>()

    /**
     * Place names from the device's geocoder, for benches whose details came back without an
     * address (servers that predate the API's address field, or benches never geocoded).
     */
    val deviceAddresses = mutableStateMapOf<Int, String>()

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Bench>?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    /** Where the map was last left, so it comes back to the same place. */
    var camera: Camera? = null

    init {
        loadBenches()
    }

    /**
     * Shows the cached bench list straight away, then brings it up to date: the whole list at most
     * once a day, otherwise just the newest benches (a few KB).
     */
    fun loadBenches() {
        if (loadingBenches) return
        loadingBenches = true
        loadError = null
        viewModelScope.launch {
            try {
                val cached = cache.read()
                if (cached != null && benches.isEmpty()) benches = cached.benches
                val now = System.currentTimeMillis()
                val merged = if (refreshPlan(cached, now) == RefreshPlan.Delta) {
                    runCatching { mergeLatest(cached!!.benches, fetchLatestBenches(DELTA_SIZE)) }.getOrElse { cached!!.benches }
                } else null
                if (merged != null) {
                    benches = merged
                    if (merged !== cached!!.benches) cache.write(cached.copy(benches = merged))
                } else {
                    val all = fetchBenches()
                    benches = all
                    cache.write(CachedBenches(now, all))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // With a cached list the map still works; only say so when there's nothing to show.
                if (benches.isEmpty()) loadError = e.message ?: "Couldn't load benches"
            } finally {
                loadingBenches = false
            }
        }
    }

    fun select(bench: Bench, samePlace: List<Bench> = listOf(bench)) {
        selection = listOf(bench) + samePlace.filter { it.id != bench.id }
        loadDetails(bench)
    }

    /** Switches between benches stacked at the same spot without losing the stack. */
    fun showFromStack(bench: Bench) {
        selection = listOf(bench) + selection.filter { it.id != bench.id }
        loadDetails(bench)
    }

    fun clearSelection() {
        selection = emptyList()
    }

    fun retryDetails(bench: Bench) {
        details.remove(bench.id)
        loadDetails(bench)
    }

    private fun loadDetails(bench: Bench) {
        if (details[bench.id] !is DetailsState.Loaded) {
            details[bench.id] = DetailsState.Loading
            viewModelScope.launch {
                details[bench.id] = try {
                    val feature = fetchBenchDetails(bench.id).features?.firstOrNull()
                    if (feature != null && feature.properties?.address.isNullOrBlank()) lookUpAddressOnDevice(bench)
                    if (feature != null) DetailsState.Loaded(feature) else DetailsState.Failed("Bench not found")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DetailsState.Failed(e.message ?: "Couldn't load this bench")
                }
            }
        }
    }

    private fun lookUpAddressOnDevice(bench: Bench) {
        if (bench.id in deviceAddresses) return
        viewModelScope.launch {
            deviceAddress(getApplication(), bench.lat, bench.lng)?.let { deviceAddresses[bench.id] = it }
        }
    }

    /** Adds a bench the user has just uploaded, so it shows before the server's bench list cache expires. */
    fun addUploadedBench(bench: Bench) {
        benches = listOf(bench) + benches.filter { it.id != bench.id }
        val list = benches
        viewModelScope.launch {
            cache.read()?.let { cache.write(it.copy(benches = list)) }
        }
    }

    /** Searches inscriptions as the user types, after a short pause. */
    fun updateQuery(text: String) {
        query = text
        searchJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.length < 3) {
            results = null
            searching = false
            searchError = null
            return
        }
        searching = true
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                results = fetchBenchesBySearch(trimmed)
                searchError = null
                searching = false
            } catch (e: CancellationException) {
                throw e // A newer query replaced this one and owns `searching` now.
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
                searching = false
            }
        }
    }
}
