package org.openbenches.e2e

import android.app.Application
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbenches.data.NewBench
import org.openbenches.data.UploadException
import org.openbenches.data.UploadPhoto
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.uploadBench
import org.openbenches.ui.add.AddBenchViewModel
import java.io.File
import java.util.UUID

/**
 * Adds benches to the local OpenBenches backend (e2e/backend) the way the app does, and reads
 * them back through the API. As on the website, the first photo must carry its own location.
 */
@RunWith(AndroidJUnit4::class)
class AddBenchE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    @Before
    fun requireLocalBackend() = assumeLocalBackend()

    /** A fresh copy of the test photo, so each upload is a new file (the server names photos by hash). */
    private fun photoWithoutLocation(): File {
        val file = File(app.cacheDir, "e2e-${UUID.randomUUID()}.jpg")
        instrumentation.context.assets.open("bench-no-gps.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
        file.appendBytes(UUID.randomUUID().toString().toByteArray()) // still a valid JPEG; unique hash
        assertNull("test photo must start without a location", ExifInterface(file).latLong)
        return file
    }

    /** A photo geotagged by its camera at [latitude], [longitude]. */
    private fun photoTakenAt(latitude: Double, longitude: Double): File =
        photoWithoutLocation().also { ExifInterface(it).apply { setLatLong(latitude, longitude); saveAttributes() } }

    @Test
    fun addingABenchStoresItsPhotosLocationAndTags() {
        val marker = "E2E ADDED ${UUID.randomUUID().toString().take(8)}"
        val vm = instrumentation.runOnMainSyncReturning { AddBenchViewModel(app) }

        instrumentation.runOnMainSync {
            vm.inscription = marker
            vm.addPhotos(listOf(Uri.fromFile(photoTakenAt(51.38123, -2.35987)), Uri.fromFile(photoWithoutLocation())))
        }
        waitFor("photos to be prepared") { vm.photos.size == 2 && !vm.preparingPhoto }
        instrumentation.runOnMainSync {
            // The pin starts at the first photo's own location...
            assertEquals(51.38123, vm.location!!.first, 1e-5)
            assertEquals(-2.35987, vm.location!!.second, 1e-5)
            // ...and can be fine-tuned, as on the website.
            vm.moveLocation(51.38130 to -2.35990)
            vm.toggleTag("dog")
            assertNull(vm.missing)
            vm.submit()
        }
        waitFor("the upload to finish", timeoutMs = 60_000) { vm.addedBenchId != null || vm.submitError != null }
        assertNull(vm.submitError)

        val feature = runBlocking { fetchBenchDetails(vm.addedBenchId!!) }.features!!.single()
        val props = feature.properties!!
        assertEquals(marker, props.popupContent)
        assertEquals(51.38130, feature.geometry!!.coordinates!![1], 1e-4)
        assertEquals(-2.35990, feature.geometry!!.coordinates!![0], 1e-4)
        assertEquals(listOf("inscription", "bench"), props.media!!.map { it.mediaType })
        // Geocoded by the server (against the local Stadia Maps fake) when the bench was added.
        assertEquals("Fixture Park, Testville, Testshire, England, United Kingdom", props.address)
    }

    @Test
    fun aFirstPhotoWithoutLocationIsRefused() {
        val vm = instrumentation.runOnMainSyncReturning { AddBenchViewModel(app) }

        instrumentation.runOnMainSync { vm.addPhotos(listOf(Uri.fromFile(photoWithoutLocation()))) }
        waitFor("the photo to be checked") { !vm.preparingPhoto && vm.photoError != null }

        instrumentation.runOnMainSync {
            assertTrue(vm.photos.isEmpty())
            assertNull(vm.location)
            assertTrue(vm.photoError!!, vm.photoError!!.contains("no location"))
        }
    }

    @Test
    fun aGeotaggedPhotoGoesFirstWhenPickedWithOthers() {
        val vm = instrumentation.runOnMainSyncReturning { AddBenchViewModel(app) }
        val geotagged = photoTakenAt(51.5, -0.1)

        instrumentation.runOnMainSync {
            vm.inscription = "x" // skip text recognition
            vm.addPhotos(listOf(Uri.fromFile(photoWithoutLocation()), Uri.fromFile(geotagged)))
        }
        waitFor("photos to be prepared") { vm.photos.size == 2 && !vm.preparingPhoto }

        instrumentation.runOnMainSync {
            assertEquals(51.5, vm.photos.first().takenAt!!.first, 1e-5)
            assertEquals("inscription", vm.photos.first().mediaType)
            assertEquals(51.5, vm.location!!.first, 1e-5)
        }
    }

    @Test
    fun theServerRejectsAPhotoWithoutLocation() = runBlocking {
        // The server's own rule, which the app checks before uploading.
        try {
            uploadBench(NewBench("E2E NO LOCATION", 51.38, -2.36, emptyList(), listOf(UploadPhoto(photoWithoutLocation(), "inscription"))))
            fail("expected the upload to be rejected")
        } catch (e: UploadException) {
            assertTrue(e.message, e.message!!.contains("location"))
        }
    }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var met = false
            instrumentation.runOnMainSync { met = condition() }
            if (met) return
            Thread.sleep(200)
        }
        fail("Timed out waiting for $what")
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncReturning(block: () -> T): T {
        var result: T? = null
        runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
