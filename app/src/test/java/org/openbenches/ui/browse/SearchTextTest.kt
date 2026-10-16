package org.openbenches.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openbenches.data.cleanInscription

class SearchTextTest {
    @Test
    fun snippetJumpsToTheMatch() {
        val text = "Clare Jenkins who played cello in this square for many happy years"

        val snippet = matchSnippet(text, "cello")

        assertTrue(snippet, snippet.startsWith("…"))
        assertTrue(snippet, snippet.contains("cello"))
    }

    @Test
    fun snippetKeepsTextWhenTheMatchIsNearTheStartOrInTheTitle() {
        assertEquals("Played cello here", matchSnippet("Played cello here", "cello"))
        assertEquals("long line " + "x".repeat(40), matchSnippet("long line " + "x".repeat(40), "memory", skipIfIn = "In loving memory"))
    }

    @Test
    fun inscriptionsKeepTheirLineBreaks() {
        val api = "Donated by the Godfrey Family<br />\r\nin memory of<br />\r\n&quot;THE OXFORD KID&quot;"

        assertEquals("Donated by the Godfrey Family\nin memory of\n\"THE OXFORD KID\"", cleanInscription(api))
    }
}
