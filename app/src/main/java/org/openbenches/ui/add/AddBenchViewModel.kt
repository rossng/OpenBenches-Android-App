package org.openbenches.ui.add

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openbenches.data.NewBench
import org.openbenches.data.Tag
import org.openbenches.data.UploadException
import org.openbenches.data.UploadPhoto
import org.openbenches.data.fetchTags
import org.openbenches.data.preparePhoto
import org.openbenches.data.readText
import org.openbenches.data.uploadBench
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * A photo in the bench being added, what it shows (one of [MEDIA_TYPES]), and the location in
 * its own metadata, if any.
 */
data class DraftPhoto(val id: String, val file: File, val mediaType: String, val takenAt: Pair<Double, Double>?)

/** The website's help page for phones that don't geotag photos. */
const val GPS_HELP_URL = "https://www.openbenches.org/blog/android-gps-problems/"

/** The website's media types for photos, in the order its add page offers them. */
val MEDIA_TYPES = listOf("inscription" to "Inscription", "bench" to "The bench", "view" to "View from the bench")

const val MAX_PHOTOS = 4

/** State for adding one bench: photos, inscription, location and tags, then the upload. */
class AddBenchViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val workDir = File(app.cacheDir, "upload-${UUID.randomUUID()}")
    private val cameraDir = File(app.cacheDir, "camera")
    private var pendingCameraFile: File? = null

    val photos = mutableStateListOf<DraftPhoto>()
    var preparingPhoto by mutableStateOf(false)
        private set
    var photoError by mutableStateOf<String?>(null)
        private set

    var inscription by mutableStateOf("")
    var readingText by mutableStateOf(false)
        private set
    var textNote by mutableStateOf<String?>(null)
        private set

    /**
     * Where the bench is. Like the website, it comes from the first photo's own location and can
     * then be fine-tuned on the map; it's never taken from the phone's GPS.
     */
    var location by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var pinMoved by mutableStateOf(false)
        private set

    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    val selectedTags = mutableStateListOf<String>()

    var submitting by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var submitError by mutableStateOf<String?>(null)
        private set
    var addedBenchId by mutableStateOf<Int?>(null)
        private set

    val hasContent: Boolean get() = photos.isNotEmpty() || inscription.isNotBlank()

    /** Why the bench can't be added yet, or null when it can. */
    val missing: String?
        get() = when {
            photos.isEmpty() -> "Add a geotagged photo of the bench"
            location == null -> "The first photo needs a location. Remove it or add a geotagged photo first."
            inscription.isBlank() -> "Type the inscription"
            else -> null
        }

    init {
        viewModelScope.launch {
            tags = runCatching { fetchTags() }.getOrDefault(emptyList()).sortedBy { it.text.lowercase() }
        }
    }

    /** A file for the camera app to write a new photo into. */
    fun newCameraUri(): Uri {
        cameraDir.mkdirs()
        val file = File(cameraDir, "bench-${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    fun onCameraResult(saved: Boolean) {
        val file = pendingCameraFile ?: return
        pendingCameraFile = null
        if (saved && file.length() > 0) addPhoto(Uri.fromFile(file)) else file.delete()
    }

    fun addPhoto(uri: Uri) = addPhotos(listOf(uri))

    /**
     * Adds photos in the order given. As on the website, the bench's first photo must carry a
     * location: if the bench has no photos yet, the first geotagged one in [uris] goes first, and
     * if none is geotagged nothing is added.
     */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        preparingPhoto = true
        photoError = null
        viewModelScope.launch {
            try {
                val prepared = mutableListOf<DraftPhoto>()
                for (uri in uris.take(MAX_PHOTOS - photos.size)) {
                    val p = preparePhoto(app, uri, workDir)
                    prepared += DraftPhoto(p.file.name, p.file, mediaType = "", takenAt = p.takenAt)
                }
                val ordered = if (photos.isEmpty()) {
                    val first = prepared.firstOrNull { it.takenAt != null }
                    if (first == null) {
                        prepared.forEach { it.file.delete() }
                        photoError = if (prepared.size == 1) {
                            "This photo has no location. OpenBenches needs a photo taken with location (geotagging) turned on in your camera."
                        } else {
                            "None of these photos has a location. OpenBenches needs a photo taken with location (geotagging) turned on in your camera."
                        }
                        return@launch
                    }
                    listOf(first) + prepared.filter { it !== first }
                } else prepared
                ordered.forEach { photo ->
                    // Like the website's add page: the first photo is the inscription, then the bench, then the view.
                    photos.add(photo.copy(mediaType = MEDIA_TYPES.getOrNull(photos.size)?.first ?: "bench"))
                }
                updateLocationFromFirstPhoto()
                photos.firstOrNull { it.mediaType == "inscription" }?.let { if (inscription.isBlank()) readInscription(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoError = "Couldn't use that photo. Try another one."
            } finally {
                preparingPhoto = false
            }
        }
    }

    fun removePhoto(photo: DraftPhoto) {
        photos.remove(photo)
        photo.file.delete()
        updateLocationFromFirstPhoto()
    }

    /** The pin starts at the first photo's location, and resets when the first photo changes. */
    private var locationPhotoId: String? = null

    private fun updateLocationFromFirstPhoto() {
        val first = photos.firstOrNull()
        if (first?.id == locationPhotoId) return
        locationPhotoId = first?.id
        location = first?.takenAt
        pinMoved = false
    }

    fun setMediaType(photo: DraftPhoto, type: String) {
        val i = photos.indexOfFirst { it.id == photo.id }
        if (i < 0 || photos[i].mediaType == type) return
        photos[i] = photos[i].copy(mediaType = type)
        // A different photo is now the inscription: read it, unless the user has been typing.
        if (type == "inscription" && (inscription.isBlank() || textWasRead)) readInscription(photos[i])
    }

    /** True while the inscription is still exactly what text recognition produced. */
    private var lastReadText: String? = null
    private val textWasRead: Boolean get() = lastReadText != null && inscription == lastReadText

    /** Reads the inscription off a photo with on-device text recognition. */
    fun readInscription(photo: DraftPhoto? = photos.firstOrNull { it.mediaType == "inscription" } ?: photos.firstOrNull()) {
        photo ?: return
        readingText = true
        textNote = null
        viewModelScope.launch {
            try {
                val text = readText(app, photo.file)
                if (text.isBlank()) {
                    textNote = "No text found in the photo. Type the inscription instead."
                } else {
                    inscription = text
                    lastReadText = text
                    textNote = "Read from your photo. Check it against the plaque."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                textNote = "Couldn't read the photo. Type the inscription instead."
            } finally {
                readingText = false
            }
        }
    }

    /** The user fine-tuned the pin on the map (only possible once a photo has set it). */
    fun moveLocation(latLng: Pair<Double, Double>) {
        val current = location ?: return
        if (kotlin.math.abs(current.first - latLng.first) < 1e-7 && kotlin.math.abs(current.second - latLng.second) < 1e-7) return
        location = latLng
        pinMoved = true
    }

    /** Puts the pin back on the first photo's location. */
    fun resetPin() {
        location = photos.firstOrNull()?.takenAt
        pinMoved = false
    }

    fun toggleTag(tag: String) {
        if (!selectedTags.remove(tag)) selectedTags.add(tag)
    }

    fun submit(idToken: String? = null) {
        val where = location ?: return
        if (missing != null || submitting) return
        submitting = true
        progress = 0f
        submitError = null
        viewModelScope.launch {
            try {
                addedBenchId = uploadBench(
                    NewBench(
                        inscription = inscription.trim(),
                        latitude = where.first,
                        longitude = where.second,
                        tags = selectedTags.toList(),
                        photos = photos.map { UploadPhoto(it.file, it.mediaType) },
                    ),
                    idToken = idToken,
                    onProgress = { progress = it },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: UploadException) {
                submitError = e.message
            } catch (e: IOException) {
                submitError = "Couldn't reach OpenBenches. Check your connection and try again."
            } catch (e: Exception) {
                submitError = "Something went wrong adding the bench. Try again."
            } finally {
                submitting = false
            }
        }
    }

    override fun onCleared() {
        workDir.deleteRecursively()
    }
}
