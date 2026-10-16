package org.openbenches.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class AccountsTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{\"alg\":\"RS256\"}".toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun readsTokenExpiry() {
        assertEquals(1_800_000_000L, jwtExpiry(jwt("""{"sub":"github|1","exp": 1800000000,"nickname":"ada"}""")))
        assertNull(jwtExpiry("not-a-token"))
    }

    @Test
    fun namesTheSignInProvider() {
        assertEquals("GitHub", providerName("github|7001"))
        assertEquals("OpenStreetMap", providerName("oidc|openstreetmap-openid|7002"))
        assertEquals("Discord", providerName("oauth2|discord|7003"))
        assertEquals("Mastodon", providerName("mastodon|7004"))
        assertNull(providerName(null))
    }
}
