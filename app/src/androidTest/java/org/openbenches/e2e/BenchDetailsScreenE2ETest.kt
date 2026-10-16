package org.openbenches.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbenches.ui.BenchDetailsScreen

/**
 * Renders the bench details screen against the local OpenBenches backend (e2e/backend).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BenchDetailsScreenE2ETest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun requireLocalBackend() = assumeLocalBackend()

    @Test
    fun showsInscriptionPhotoAndAddressFromBackend() {
        composeTestRule.setContent {
            BenchDetailsScreen(benchId = Fixtures.BENCH_ID, onBack = { _, _ -> })
        }

        composeTestRule.waitUntilAtLeastOneExists(
            hasText(Fixtures.INSCRIPTION_MARKER, substring = true),
            timeoutMillis = 20_000
        )
        composeTestRule.onNodeWithText("Inscription Details").assertExists()
        composeTestRule.onNodeWithContentDescription("Bench photo").assertExists()

        composeTestRule.waitUntilAtLeastOneExists(hasText(Fixtures.FAKE_ADDRESS), timeoutMillis = 20_000)
    }
}
