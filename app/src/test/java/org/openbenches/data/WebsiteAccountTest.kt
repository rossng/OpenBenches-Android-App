package org.openbenches.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteAccountTest {
    @Test
    fun readsWhoIsSignedInFromTheAddPage() {
        // As rendered by templates/add.html.twig: "Welcome {{ provider|capitalize }} user {{ username }}"
        val page = """<h2><img src="/x.png" alt="">
		Welcome Github user ada-benches</h2>"""
        val account = parseWebsiteWelcome(page)!!
        assertEquals("ada-benches", account.nickname)
        assertEquals("Github", account.provider)
    }

    @Test
    fun theSignedOutPageHasNoAccount() {
        assertNull(parseWebsiteWelcome("""<a class="button" href="/add/logged_in">👤 Sign in</a><p>or be anonymous."""))
    }
}
