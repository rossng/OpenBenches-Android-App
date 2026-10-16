package org.openbenches.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatPlaceTest {
    @Test
    fun joinsPartsSmallestFirstWithoutRepeats() {
        assertEquals(
            "Bath, Bath and North East Somerset, England, United Kingdom",
            formatPlace(null, "Bath", "Bath and North East Somerset", "England", "United Kingdom"),
        )
        assertEquals("London, England, United Kingdom", formatPlace("London", "London", " ", "England", "United Kingdom"))
    }

    @Test
    fun nothingKnownMeansNoAddress() {
        assertNull(formatPlace(null, "", "  ", null))
    }
}
