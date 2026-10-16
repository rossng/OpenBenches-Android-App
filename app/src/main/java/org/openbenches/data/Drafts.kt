package org.openbenches.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/** One photo in a draft: its file (in the draft's folder), what it shows, and where it was taken. */
data class DraftPhotoRecord(
    val fileName: String,
    val mediaType: String,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * A bench photographed but not yet uploaded. Everything else (inscription, tags, pin) may still
 * be empty: drafts exist so people can photograph several benches while out and finish later.
 */
data class BenchDraft(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val photos: List<DraftPhotoRecord>,
    val inscription: String = "",
    val tags: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pinMoved: Boolean = false,
) {
    val location: Pair<Double, Double>? get() = if (latitude != null && longitude != null) latitude to longitude else null
}

/**
 * Drafts on disk: one folder per draft under [root], holding draft.json and its photos.
 * Kept in app-private storage, so they survive restarts but not uninstalling.
 */
class DraftStore(private val root: File) {
    private val gson = Gson()
    private val _drafts = MutableStateFlow(readAll())

    /** All drafts, newest first. */
    val drafts: StateFlow<List<BenchDraft>> = _drafts.asStateFlow()

    /** A new, empty folder for a draft's photos; nothing is listed until [save] is called. */
    fun newDraftDir(): Pair<String, File> {
        val id = UUID.randomUUID().toString()
        return id to File(root, id).apply { mkdirs() }
    }

    fun dirFor(id: String): File = File(root, id)

    fun load(id: String): BenchDraft? = readDraft(File(root, id))

    @Synchronized
    fun save(draft: BenchDraft) {
        val dir = File(root, draft.id).apply { mkdirs() }
        val tmp = File(dir, "draft.json.tmp")
        tmp.writeText(gson.toJson(draft))
        tmp.renameTo(File(dir, "draft.json"))
        refresh()
    }

    @Synchronized
    fun delete(id: String) {
        File(root, id).deleteRecursively()
        refresh()
    }

    /** Removes folders left behind by sessions that were never saved (e.g. the app was killed). */
    @Synchronized
    fun removeAbandoned(olderThanMs: Long, now: Long = System.currentTimeMillis()) {
        root.listFiles()?.filter { it.isDirectory && !File(it, "draft.json").exists() && now - it.lastModified() > olderThanMs }
            ?.forEach { it.deleteRecursively() }
    }

    private fun refresh() {
        _drafts.value = readAll()
    }

    private fun readAll(): List<BenchDraft> =
        root.listFiles()?.mapNotNull { if (it.isDirectory) readDraft(it) else null }?.sortedByDescending { it.updatedAt }.orEmpty()

    private fun readDraft(dir: File): BenchDraft? = runCatching {
        gson.fromJson(File(dir, "draft.json").readText(), BenchDraft::class.java)
            // Gson leaves Kotlin defaults null when a field is missing from older files.
            ?.let { it.copy(inscription = it.inscription ?: "", tags = it.tags ?: emptyList(), photos = it.photos ?: emptyList()) }
    }.getOrNull()

    companion object {
        @Volatile private var instance: DraftStore? = null

        /** The app's draft store, in filesDir/drafts. */
        fun get(context: android.content.Context): DraftStore =
            instance ?: synchronized(this) {
                instance ?: DraftStore(File(context.applicationContext.filesDir, "drafts").apply { mkdirs() }).also { instance = it }
            }
    }
}
