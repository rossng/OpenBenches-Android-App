package org.openbenches.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How often to download the whole bench list; in between, only new benches are fetched. */
const val FULL_REFRESH_MS = 24L * 60 * 60 * 1000

/** How many of the newest benches to ask for when checking for new ones. */
const val DELTA_SIZE = 50

/** The bench list as last downloaded, and when the full list was fetched. */
data class CachedBenches(
    @SerializedName("fetchedAt") val fullFetchAt: Long,
    @SerializedName("benches") val benches: List<Bench>,
)

/**
 * The bench list kept on the phone, so the app starts instantly and doesn't download all
 * ~45,000 benches (about 1.8 MB compressed) on every launch.
 */
class BenchCache(private val file: File) {
    private val gson = Gson()

    suspend fun read(): CachedBenches? = withContext(Dispatchers.IO) {
        runCatching { file.bufferedReader().use { gson.fromJson(it, CachedBenches::class.java) } }
            .getOrNull()?.takeIf { it.benches != null }
    }

    suspend fun write(cached: CachedBenches) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { gson.toJson(cached, it) }
        tmp.renameTo(file)
    }
}

/** What to do at launch, given the cached list's age. */
enum class RefreshPlan { Full, Delta }

fun refreshPlan(cached: CachedBenches?, now: Long): RefreshPlan =
    if (cached == null || cached.benches.isEmpty() || now - cached.fullFetchAt >= FULL_REFRESH_MS) RefreshPlan.Full
    else RefreshPlan.Delta

/**
 * Adds the newest benches to the cached list. Returns null when the delta can't be trusted to be
 * complete: if every one of the [DELTA_SIZE] newest benches is new to us, there may be more we
 * missed, so the whole list should be downloaded instead.
 */
fun mergeLatest(cached: List<Bench>, latest: List<Bench>): List<Bench>? {
    if (latest.isEmpty()) return cached
    val known = cached.mapTo(HashSet(cached.size)) { it.id }
    val fresh = latest.filter { it.id !in known }
    if (fresh.size >= DELTA_SIZE && latest.size >= DELTA_SIZE) return null
    return if (fresh.isEmpty()) cached else fresh + cached
}
