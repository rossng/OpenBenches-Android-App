package org.openbenches.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DraftStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun draft(id: String, updatedAt: Long) = BenchDraft(
        id = id, createdAt = 1, updatedAt = updatedAt,
        photos = listOf(DraftPhotoRecord("a.jpg", "inscription", 51.5, -0.1)),
        latitude = 51.5, longitude = -0.1,
    )

    @Test
    fun draftsSurviveARestartNewestFirst() {
        val root = tmp.newFolder("drafts")
        DraftStore(root).apply {
            val (a, _) = newDraftDir(); save(draft(a, 100))
            val (b, _) = newDraftDir(); save(draft(b, 200).copy(inscription = "IN LOVING MEMORY", tags = listOf("dog")))
        }

        val reopened = DraftStore(root).drafts.value

        assertEquals(listOf(200L, 100L), reopened.map { it.updatedAt })
        assertEquals("IN LOVING MEMORY", reopened.first().inscription)
        assertEquals(listOf("dog"), reopened.first().tags)
        assertEquals(51.5 to -0.1, reopened.first().location)
    }

    @Test
    fun deletingADraftRemovesItsPhotos() {
        val store = DraftStore(tmp.newFolder("drafts"))
        val (id, dir) = store.newDraftDir()
        File(dir, "a.jpg").writeText("photo")
        store.save(draft(id, 1))

        store.delete(id)

        assertTrue(store.drafts.value.isEmpty())
        assertFalse(dir.exists())
        assertNull(store.load(id))
    }

    @Test
    fun unsavedSessionsAreCleanedUpButDraftsAreKept() {
        val root = tmp.newFolder("drafts")
        val store = DraftStore(root)
        val (_, abandoned) = store.newDraftDir()
        val (kept, _) = store.newDraftDir()
        store.save(draft(kept, 1))

        store.removeAbandoned(olderThanMs = 0, now = System.currentTimeMillis() + 1000)

        assertFalse(abandoned.exists())
        assertEquals(listOf(kept), store.drafts.value.map { it.id })
    }
}
