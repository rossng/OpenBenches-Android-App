package org.openbenches.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadErrorTest {
    @Test
    fun missingLocationIsExplained() {
        val body = "No location metadata found in image.<br><a href=\"/add\">Please reload this page and try a different photo</a>"
        assertEquals(
            "The server couldn't find a location in the first photo. Set the bench's location and try again.",
            uploadErrorMessage(415, body),
        )
    }

    @Test
    fun bannedUsersSeeTheServersReason() {
        assertEquals(
            "You are unable to contribute to OpenBenches. Reason: spam",
            uploadErrorMessage(403, "You are unable to contribute to OpenBenches.<br>Reason: spam"),
        )
    }

    @Test
    fun serverErrorsSayToTryAgain() {
        assertEquals("The OpenBenches server had a problem (error 500). Try again in a moment.", uploadErrorMessage(500, "<html>…</html>"))
    }

    @Test
    fun recognisedTextIsTidied() {
        assertEquals("IN LOVING MEMORY\nOF BOB", tidyRecognisedText("  IN LOVING MEMORY \n\n OF BOB \n"))
    }
}
