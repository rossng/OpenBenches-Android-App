package org.openbenches.e2e

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbenches.data.fetchAddressFromLatLng
import org.openbenches.data.fetchBenchDetails
import org.openbenches.data.fetchBenches
import org.openbenches.data.fetchBenchesBySearch
import org.openbenches.data.fetchBenchesNearby
import org.openbenches.data.mediaUrl
import java.net.HttpURLConnection
import java.net.URL

/**
 * Exercises the app's data layer against the local OpenBenches backend (e2e/backend).
 */
@RunWith(AndroidJUnit4::class)
class LocalBackendApiTest {
    @Before
    fun requireLocalBackend() = assumeLocalBackend()

    @Test
    fun benchDetails_returnsFixtureBenchWithMedia() = runBlocking {
        val feature = fetchBenchDetails(Fixtures.BENCH_ID).features!!.single()

        assertEquals(Fixtures.BENCH_ID, feature.id)
        assertEquals(listOf(Fixtures.LONGITUDE, Fixtures.LATITUDE), feature.geometry!!.coordinates)
        assertTrue(feature.properties!!.popupContent!!.contains(Fixtures.INSCRIPTION_MARKER))
        val media = feature.properties!!.media!!.single()
        assertEquals(Fixtures.PHOTO_SHA1, media.sha1)
        assertEquals("inscription", media.mediaType)
    }

    @Test
    fun benchPhoto_isServedByBackend() = runBlocking {
        val media = fetchBenchDetails(Fixtures.BENCH_ID).features!!.single().properties!!.media!!.single()

        val bitmap = withContext(Dispatchers.IO) {
            val connection = URL(mediaUrl(media.url)).openConnection() as HttpURLConnection
            try {
                assertEquals(200, connection.responseCode)
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        }
        assertNotNull("photo should decode as an image", bitmap)
        assertEquals(Fixtures.PHOTO_WIDTH, bitmap.width)
    }

    @Test
    fun nearby_findsFixtureBench() = runBlocking {
        val benches = fetchBenchesNearby(Fixtures.LATITUDE, Fixtures.LONGITUDE, distance = 10)

        assertEquals(listOf(Fixtures.BENCH_ID), benches.map { it.id })
        assertEquals(Fixtures.LATITUDE, benches.single().lat, 1e-6)
        assertEquals(Fixtures.LONGITUDE, benches.single().lng, 1e-6)
    }

    @Test
    fun search_findsFixtureBenchByInscription() = runBlocking {
        val benches = fetchBenchesBySearch(Fixtures.INSCRIPTION_MARKER)

        assertTrue(benches.any { it.id == Fixtures.BENCH_ID })
    }

    @Test
    fun allBenches_includesSampleDataAndFixture() = runBlocking {
        val benches = fetchBenches()

        assertTrue("expected the sample database, got ${benches.size} benches", benches.size > 1000)
        assertTrue(benches.any { it.id == Fixtures.BENCH_ID })
    }

    @Test
    fun reverseGeocode_usesFakeOpenCage() = runBlocking {
        assertEquals(Fixtures.FAKE_ADDRESS, fetchAddressFromLatLng(Fixtures.LATITUDE, Fixtures.LONGITUDE))
    }
}
