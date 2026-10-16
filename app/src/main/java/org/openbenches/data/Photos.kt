package org.openbenches.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** A photo copied into the app's cache as a JPEG, with the location it was taken at if it says. */
data class PreparedPhoto(val file: File, val takenAt: Pair<Double, Double>?)

private const val MAX_EDGE = 4096

/**
 * Copies a photo from the camera or picker into [dir] as a JPEG. JPEGs are copied byte for byte;
 * anything else (HEIC, PNG…) is re-encoded. The photo picker removes location data, so
 * [PreparedPhoto.takenAt] is usually only set for photos from the camera.
 */
suspend fun preparePhoto(context: Context, source: Uri, dir: File): PreparedPhoto = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val out = File(dir, "photo-${UUID.randomUUID()}.jpg")
    val resolver = context.contentResolver
    val type = resolver.getType(source) ?: if (source.path.orEmpty().endsWith(".jpg", true) || source.path.orEmpty().endsWith(".jpeg", true)) "image/jpeg" else null
    if (type == "image/jpeg") {
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Couldn't open the photo" }
            out.outputStream().use { input.copyTo(it) }
        }
    } else {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
            val edge = max(info.size.width, info.size.height)
            if (edge > MAX_EDGE) decoder.setTargetSampleSize((edge + MAX_EDGE - 1) / MAX_EDGE)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
    }
    val takenAt = runCatching { ExifInterface(out).latLong }.getOrNull()?.let { it[0] to it[1] }
    PreparedPhoto(out, takenAt)
}

/**
 * Writes the bench's location into the photo's EXIF. The website only accepts a bench whose
 * first photo is geotagged, and Android strips location from photos picked from the gallery.
 */
suspend fun stampLocation(file: File, latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
    ExifInterface(file).apply {
        setLatLong(latitude, longitude)
        saveAttributes()
    }
}

/** Reads the text in a photo on the device (ML Kit), for pre-filling the inscription. */
suspend fun readText(context: Context, file: File): String = suspendCancellableCoroutine { cont ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file)))
        .addOnSuccessListener { cont.resume(tidyRecognisedText(it.text)) }
        .addOnFailureListener { cont.resumeWithException(it) }
        .addOnCompleteListener { recognizer.close() }
}

/** One line per plaque line, without stray blank lines or edge whitespace. */
fun tidyRecognisedText(text: String): String =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
