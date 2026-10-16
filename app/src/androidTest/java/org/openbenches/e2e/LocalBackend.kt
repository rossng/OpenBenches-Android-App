package org.openbenches.e2e

import org.junit.Assume.assumeTrue
import org.openbenches.BuildConfig

/**
 * Fixture data seeded into the local backend by e2e/backend/db/fixtures.sql.
 */
object Fixtures {
    const val BENCH_ID = 900001
    const val LATITUDE = 0.5
    const val LONGITUDE = 0.5
    const val INSCRIPTION_MARKER = "Zebra Kumquat"
    const val PHOTO_SHA1 = "909e8e481c851f1dc14742d61f690243d8f701a7"
    const val PHOTO_WIDTH = 490
    const val FAKE_ADDRESS = "1 Fixture Way, Testville, E2E 0AA"
}

/**
 * Skips the calling test unless the app was built against a local backend, so these tests
 * never run against openbenches.org. Run them with e2e/run-android-e2e.sh.
 */
fun assumeLocalBackend() {
    assumeTrue(
        "Built against ${BuildConfig.OPENBENCHES_BASE_URL}; pass -Popenbenches.baseUrl=http://10.0.2.2:8000/ to run e2e tests",
        BuildConfig.OPENBENCHES_BASE_URL.startsWith("http://10.0.2.2") ||
            BuildConfig.OPENBENCHES_BASE_URL.startsWith("http://localhost")
    )
}
