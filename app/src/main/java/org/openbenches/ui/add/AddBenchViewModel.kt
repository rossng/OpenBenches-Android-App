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
import org.openbenches.data.stampLocation
import org.openbenches.data.uploadBench
import java.io.File
import java.io.IOException
import java.util.UUID

/** A photo in the bench being added, and what it shows (one of [MEDIA_TYPES]). */
data class DraftPhoto(val id: String, val file: File, val mediaType: String)

/** Where the pin's position came from, so the screen can say. */
enum class LocationSource { Photo, Device, Map }

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

    var location by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    var locationSource by mutableStateOf<LocationSource?>(null)
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
            photos.isEmpty() -> "Add at least one photo of the bench"
            inscription.isBlank() -> "Type the inscription"
            location == null -> "Set where the bench is"
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

    /** Adds photos in the order given (the picker's selection order), one at a time. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        preparingPhoto = true
        photoError = null
        viewModelScope.launch {
            for (uri in uris) {
                if (photos.size >= MAX_PHOTOS) break
                addPreparedPhoto(uri)
            }
            preparingPhoto = false
        }
    }

    private suspend fun addPreparedPhoto(uri: Uri) {
        try {
            val prepared = preparePhoto(app, uri, workDir)
            // Like the website's add page: the first photo is the inscription, then the bench, then the view.
            val type = MEDIA_TYPES.getOrNull(photos.size)?.first ?: "bench"
            val photo = DraftPhoto(prepared.file.name, prepared.file, type)
            photos.add(photo)
            val takenAt = prepared.takenAt
            if (takenAt != null && locationSource != LocationSource.Map) setLocation(takenAt, LocationSource.Photo)
            if (type == "inscription" && inscription.isBlank()) readInscription(photo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            photoError = "Couldn't use that photo. Try another one."
        }
    }

    fun removePhoto(photo: DraftPhoto) {
        photos.remove(photo)
        photo.file.delete()
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

    /** The user's current location, used only until a photo or the map says otherwise. */
    fun offerDeviceLocation(latLng: Pair<Double, Double>) {
        if (location == null || locationSource == LocationSource.Device) setLocation(latLng, LocationSource.Device)
    }

    fun useDeviceLocation(latLng: Pair<Double, Double>) = setLocation(latLng, LocationSource.Device)

    /** The user moved the map under the pin. */
    fun moveLocation(latLng: Pair<Double, Double>) {
        val current = location
        if (current != null && kotlin.math.abs(current.first - latLng.first) < 1e-7 && kotlin.math.abs(current.second - latLng.second) < 1e-7) return
        setLocation(latLng, LocationSource.Map)
    }

    private fun setLocation(latLng: Pair<Double, Double>, source: LocationSource) {
        location = latLng
        locationSource = source
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
                photos.forEach { stampLocation(it.file, where.first, where.second) }
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
