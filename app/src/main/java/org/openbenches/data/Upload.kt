package org.openbenches.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import org.openbenches.BuildConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A photo ready to upload, and what it shows. The first one must have a location in its EXIF. */
data class UploadPhoto(val file: File, val mediaType: String)

/** Everything the website's /upload endpoint takes for a new bench. */
data class NewBench(
    val inscription: String,
    val latitude: Double,
    val longitude: Double,
    val tags: List<String>,
    val photos: List<UploadPhoto>,
)

class UploadException(message: String) : Exception(message)

private val uploadClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(3, TimeUnit.MINUTES) // several full-size photos on a slow connection
    .readTimeout(90, TimeUnit.SECONDS) // the server geocodes and stores photos before replying
    // A successful upload answers directly; a redirect means the website wants a fresh sign-in.
    .followRedirects(false)
    .build()

/**
 * Adds a bench the way the website's add page does (POST /upload, multipart). Without
 * [headers] (from AccountService.uploadHeaders) the bench is added anonymously; with them it's credited to that user.
 * Reports progress from 0 to 1 as the photos are sent. Returns the new bench's ID.
 */
suspend fun uploadBench(
    bench: NewBench,
    headers: Map<String, String> = emptyMap(),
    path: String = "upload",
    onProgress: (Float) -> Unit = {},
): Int =
    withContext(Dispatchers.IO) {
        require(bench.photos.size in 1..4) { "A bench needs 1 to 4 photos" }
        val total = bench.photos.sumOf { it.file.length() }.coerceAtLeast(1)
        var sent = 0L
        val jpeg = "image/jpeg".toMediaType()

        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            bench.photos.forEachIndexed { i, photo ->
                val n = i + 1
                addFormDataPart(
                    "userfile$n", photo.file.name,
                    CountingBody(photo.file.asRequestBody(jpeg)) { bytes ->
                        sent += bytes
                        onProgress((sent.toFloat() / total).coerceAtMost(1f))
                    },
                )
                addFormDataPart("media_type$n", photo.mediaType)
            }
            addFormDataPart("inscription", bench.inscription)
            addFormDataPart("newLatitude", String.format(Locale.ROOT, "%.6f", bench.latitude))
            addFormDataPart("newLongitude", String.format(Locale.ROOT, "%.6f", bench.longitude))
            addFormDataPart("tags", bench.tags.joinToString(","))
        }.build()

        val request = Request.Builder()
            .url(BuildConfig.OPENBENCHES_BASE_URL + path)
            .post(body)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        val call = uploadClient.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { if (it != null) call.cancel() }
        call.execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code != 200) throw UploadException(uploadErrorMessage(response.code, text))
            text.trim().toIntOrNull() ?: throw UploadException("The server didn't say which bench it added. Check the map before trying again.")
        }
    }

/** Turns the website's HTML error pages into something to show the user. */
fun uploadErrorMessage(code: Int, body: String): String {
    val plain = body.replace(Regex("<a[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    return when {
        code == 415 && plain.contains("location", ignoreCase = true) ->
            "The first photo has no location. Choose a photo taken with location turned on in your camera."
        code == 415 -> "The server didn't receive a photo. Try again."
        code in 300..399 -> "Your OpenBenches sign-in has expired. Sign in again, or sign out to add the bench anonymously."
        code == 401 -> "OpenBenches didn't accept your sign-in. Sign out to add the bench anonymously, or try signing in again."
        code == 403 -> plain.ifEmpty { "You can't add benches with this account." }
        code == 413 -> "The photos are too large to upload. Try fewer photos."
        code >= 500 -> "The OpenBenches server had a problem (error $code). Try again in a moment."
        else -> "The upload didn't work (error $code). Try again."
    }
}

/** Reports bytes as OkHttp writes them, for a progress bar. */
private class CountingBody(private val delegate: RequestBody, private val onBytes: (Long) -> Unit) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val counting = object : ForwardingSink(sink as Sink) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                onBytes(byteCount)
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
