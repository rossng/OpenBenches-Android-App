package org.openbenches.e2e

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openbenches.MainActivity

/**
 * Drives the whole app against the local OpenBenches backend (e2e/backend):
 * search from the map, pick a result, and read the bench in the sheet.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BrowseE2ETest {
    @get:Rule(order = 0)
    val location: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireLocalBackend() = assumeLocalBackend()

    @Test
    fun searchingFromTheMapOpensTheBenchSheet() {
        compose.onNodeWithTag("searchBar").performClick()
        compose.onNodeWithTag("searchField").performTextInput(Fixtures.INSCRIPTION_MARKER)
        compose.waitUntilAtLeastOneExists(hasTestTag("searchResult"), timeoutMillis = 20_000)

        compose.onAllNodesWithTag("searchResult")[0].performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("benchSheet"), timeoutMillis = 10_000)
        compose.onNodeWithTag("inscription").assertTextContains(Fixtures.INSCRIPTION_MARKER, substring = true)
        compose.waitUntilAtLeastOneExists(hasTestTag("photos"), timeoutMillis = 20_000)
        compose.onNodeWithContentDescription("Bench photo: Inscription").assertExists()
        compose.waitUntilAtLeastOneExists(hasText(Fixtures.ADDRESS), timeoutMillis = 20_000)
    }

    @Test
    fun theSearchBarCountsEveryBench() {
        // The sample database has tens of thousands of benches; the fixture adds one more.
        compose.waitUntilAtLeastOneExists(hasText("Search ", substring = true) and hasText(" benches", substring = true), timeoutMillis = 30_000)
    }
}
