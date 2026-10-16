package org.openbenches.data

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openbenches.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Data class representing a bench with only id, coordinates, and popupContent.
 */
data class Bench(
    val id: Int,
    val coordinates: List<Double>,
    val popupContent: String
) {
    val lat: Double get() = coordinates[1]
    val lng: Double get() = coordinates[0]
}

/**
 * Data class for media in bench details
 */
data class BenchMedia(
    @SerializedName("URL") val url: String,
    @SerializedName("mediaID") val mediaId: Int,
    @SerializedName("licence") val licence: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("sha1") val sha1: String?,
    @SerializedName("user") val user: Int?,
    @SerializedName("userprovider") val userProvider: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

/**
 * Data class for bench details properties
 */
data class BenchDetailsProperties(
    @SerializedName("popupContent") val popupContent: String?,
    @SerializedName("created_at") val createdAt: String?,
    /** The place name stored when the bench was added (reverse geocoded by the website). */
    @SerializedName("address") val address: String?,
    @SerializedName("media") val media: List<BenchMedia>?
)

/**
 * Data class for bench details geometry
 */
data class BenchDetailsGeometry(
    @SerializedName("type") val type: String?,
    @SerializedName("coordinates") val coordinates: List<Double>?
)

/**
 * Data class for a single bench feature in details
 */
data class BenchDetailsFeature(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val type: String?,
    @SerializedName("geometry") val geometry: BenchDetailsGeometry?,
    @SerializedName("properties") val properties: BenchDetailsProperties?
)

/**
 * Data class for the bench details API response
 */
data class BenchDetailsResponse(
    @SerializedName("type") val type: String?,
    @SerializedName("features") val features: List<BenchDetailsFeature>?
)

/**
 * Retrofit service for OpenBenches API.
 */
interface OpenBenchesService {
    @Headers("Accept: application/json")
    @GET("benches/")
    suspend fun getBenches(): BenchesRawResponse

    /** The newest [count] benches, newest first. */
    @Headers("Accept: application/json")
    @GET("benches/")
    suspend fun getLatestBenches(@Query("latest") count: Int): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("nearest/")
    suspend fun getBenchesNearby(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("distance") distance: Int = 400
    ): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("bench/{id}")
    suspend fun getBenchDetails(
        @Path("id") id: Int,
        @Query("truncated") truncated: Boolean = false,
        // The API omits media unless asked for it.
        @Query("media") media: Boolean = true
    ): BenchDetailsResponse

    @Headers("Accept: application/json")
    @GET("search")
    suspend fun searchBenches(@Query("search") search: String): BenchesRawResponse

    @Headers("Accept: application/json")
    @GET("tags/")
    suspend fun getTags(): List<Tag>
}

/** A folksonomy tag, e.g. "dog" or "beach". The website only lets people pick from these. */
data class Tag(
    @SerializedName("id") val id: Int,
    @SerializedName("text") val text: String,
)

/** Every tag a bench can be given. */
suspend fun fetchTags(): List<Tag> = openBenchesService.getTags()

data class BenchesRawResponse(
    @SerializedName("features") val features: List<BenchRaw>
)

data class BenchRaw(
    @SerializedName("id") val id: Int,
    @SerializedName("geometry") val geometry: Geometry,
    @SerializedName("properties") val properties: Properties
)

data class Geometry(
    @SerializedName("coordinates") val coordinates: List<Double>
)

data class Properties(
    @SerializedName("popupContent") val popupContent: String
)

/**
 * Turns an API inscription (HTML with <br /> tags and CRLFs) into plain text that keeps the
 * plaque's line breaks.
 */
fun cleanInscription(html: String): String {
    // Most inscriptions are plain text with <br /> line breaks; skip the work when there's nothing to do.
    var text = if ('<' in html) html.replace(BR_TAG, "").replace(ANY_TAG, "") else html
    if ('&' in text) text = text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
    return text.replace("\r\n", "\n").lineSequence().joinToString("\n") { it.trim() }.replace(BLANK_LINES, "\n\n").trim()
}

// Compiled once: this runs for every one of the ~45,000 benches on each launch.
private val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val ANY_TAG = Regex("<[^>]+>")
private val BLANK_LINES = Regex("\n{3,}")

/** Turns API features into benches, off the main thread: the full list is tens of thousands. */
private suspend fun toBenches(features: List<BenchRaw>): List<Bench> = withContext(Dispatchers.Default) {
    features.map { Bench(id = it.id, coordinates = it.geometry.coordinates, popupContent = cleanInscription(it.properties.popupContent)) }
}

private val openBenchesService: OpenBenchesService by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.OPENBENCHES_BASE_URL + "api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenBenchesService::class.java)
}

/**
 * Returns the absolute URL of a media item, whose API `URL` is a site-relative path like `/image/<sha1>`.
 */
fun mediaUrl(path: String): String = BuildConfig.OPENBENCHES_BASE_URL.trimEnd('/') + path

/**
 * Fetches benches from the OpenBenches API and maps to simplified Bench objects.
 * @return List of benches
 */
suspend fun fetchBenches(): List<Bench> {
    return toBenches(openBenchesService.getBenches().features)
}

/** The newest [count] benches, to bring a cached list up to date. */
suspend fun fetchLatestBenches(count: Int): List<Bench> = toBenches(openBenchesService.getLatestBenches(count).features)

/**
 * Fetches benches near a given location from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesNearby(latitude: Double, longitude: Double, distance: Int = 200): List<Bench> {
    return toBenches(openBenchesService.getBenchesNearby(latitude, longitude, distance).features)
}

/**
 * Fetches details for a single bench by ID from the OpenBenches API.
 * @return BenchDetailsResponse for the bench
 */
suspend fun fetchBenchDetails(id: Int): BenchDetailsResponse {
    return openBenchesService.getBenchDetails(id, truncated = false)
}

/**
 * Fetches benches by search query from the OpenBenches API.
 * @return List of benches
 */
suspend fun fetchBenchesBySearch(query: String): List<Bench> {
    return toBenches(openBenchesService.searchBenches(query).features)
}
