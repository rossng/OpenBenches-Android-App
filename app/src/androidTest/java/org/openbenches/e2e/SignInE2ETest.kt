package org.openbenches.e2e

import android.app.Application
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openbenches.BuildConfig
import org.openbenches.data.AccountService
import org.openbenches.data.FakeAccountService
import org.openbenches.data.fetchBenchDetails
import org.openbenches.ui.add.AddBenchViewModel
import java.io.File
import java.util.UUID

/**
 * Adds benches while signed in to the local fake Auth0 (e2e/backend/fake-auth0). The website
 * verifies the ID tokens for real, against the fake's published keys.
 */
@RunWith(AndroidJUnit4::class)
class SignInE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val accounts get() = AccountService.get(app) as FakeAccountService

    @Before
    fun requireFakes() {
        assumeLocalBackend()
        assumeTrue("Built without -Pauth.fakeUrl", BuildConfig.FAKE_AUTH_URL.isNotEmpty())
        runBlocking { accounts.signOut(app) }
    }

    @After
    fun signOut() = runBlocking { accounts.signOut(app) }

    @Test
    fun aSignedInUploadIsCreditedToTheAccount() {
        runBlocking { accounts.signInAs("ada") }

        val bench = addBench()

        val media = runBlocking { fetchBenchDetails(bench) }.features!!.single().properties!!.media!!.single()
        assertEquals("ada-benches", media.username)
        assertEquals("github", media.userProvider)
    }

    @Test
    fun anExpiredTokenIsRefreshedBeforeUploading() {
        runBlocking { accounts.signInAs("discord", expired = true) }

        val bench = addBench()

        val media = runBlocking { fetchBenchDetails(bench) }.features!!.single().properties!!.media!!.single()
        assertEquals("parkbench", media.username)
        assertEquals("discord", media.userProvider)
    }

    @Test
    fun aSignInThatCantBeRefreshedAsksToSignInAgain() {
        runBlocking { accounts.signInAs("osm", expired = true) }
        accounts.disableRefresh()

        val vm = prepareBench()
        submitAndWait(vm)

        assertNull(vm.addedBenchId)
        assertTrue(vm.submitError!!, vm.submitError!!.contains("sign-in has expired"))
    }

    @Test
    fun signedOutUploadsAreAnonymous() {
        val bench = addBench()

        val media = runBlocking { fetchBenchDetails(bench) }.features!!.single().properties!!.media!!.single()
        assertEquals("anon", media.username)
    }

    private fun addBench(): Int {
        val vm = prepareBench()
        submitAndWait(vm)
        assertNull(vm.submitError)
        return vm.addedBenchId!!
    }

    private fun prepareBench(): AddBenchViewModel {
        var vm: AddBenchViewModel? = null
        instrumentation.runOnMainSync {
            vm = AddBenchViewModel(app).also {
                it.start(null)
                val photo = it.newPhotoFile()
                instrumentation.context.assets.open("bench-no-gps.jpg").use { input -> photo.outputStream().use { o -> input.copyTo(o) } }
                photo.appendBytes(UUID.randomUUID().toString().toByteArray()) // a unique photo each time
                ExifInterface(photo).apply { setLatLong(51.3812, -2.3599); saveAttributes() }
                it.addCapturedPhoto(photo, 51.3812 to -2.3599)
                it.inscription = "E2E SIGNED IN ${UUID.randomUUID().toString().take(8)}"
            }
        }
        return vm!!
    }

    private fun submitAndWait(vm: AddBenchViewModel) {
        instrumentation.runOnMainSync { vm.submit() }
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = !vm.submitting && (vm.addedBenchId != null || vm.submitError != null) }
            if (done) return
            Thread.sleep(200)
        }
        fail("Timed out waiting for the upload")
    }
}
