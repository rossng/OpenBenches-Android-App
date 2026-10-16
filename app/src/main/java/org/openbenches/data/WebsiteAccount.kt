package org.openbenches.data

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openbenches.BuildConfig
import org.openbenches.ui.WebsiteSignInActivity
import kotlin.coroutines.resume

/**
 * Temporary sign-in: sign in to openbenches.org itself, in a browser view inside the app, and
 * upload with the website's session cookie, exactly as its add page does. For while the live site
 * doesn't accept the app's own Auth0 tokens.
 *
 * To remove it once the live site accepts the app's Auth0 sign-ins (AUTH0_ANDROID_CLIENT_ID):
 *  1. Delete this file, ui/WebsiteSignInActivity.kt and test/.../WebsiteAccountTest.kt.
 *  2. Delete the WebsiteSignInActivity <activity> from AndroidManifest.xml.
 *  3. In AccountService.get, drop the `else -> WebsiteAccountService(app)` branch.
 *  4. In app/build.gradle.kts, remove SIGN_IN_MODE (or default it to "auth0").
 * Nothing else depends on it: uploads go through AccountService.uploadHeaders/uploadPath.
 */
class WebsiteAccountService(private val context: Context) : AccountService {
    private val prefs = context.getSharedPreferences("website-account", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().followRedirects(false).build()
    private val _account = MutableStateFlow(readStoredAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    override suspend fun signIn(context: Context): Account {
        val result = CompletableDeferred<Account?>()
        pending = result
        context.startActivity(Intent(context, WebsiteSignInActivity::class.java))
        val account = result.await() ?: throw SignInCancelled()
        prefs.edit().putString("nickname", account.nickname).putString("provider", account.provider).apply()
        _account.value = account
        return account
    }

    override suspend fun signOut(context: Context) {
        prefs.edit().clear().apply()
        _account.value = null
        // Forget the website session and Auth0's own login, so the next sign-in can pick another account.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
            }
            CookieManager.getInstance().flush()
        }
    }

    override suspend fun idToken(): String? = null

    /**
     * Signed in, post to the website's guarded upload address: if the session has lapsed by then,
     * the website refuses (redirects to its login) rather than taking the bench as anonymous.
     */
    override val uploadPath: String get() = if (_account.value != null) "upload/logged_in" else "upload"

    /**
     * The website's session cookie, after checking it's still signed in: if the session has
     * expired the website would quietly take the upload as anonymous.
     */
    override suspend fun uploadHeaders(): Map<String, String> {
        if (_account.value == null) return emptyMap()
        val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(BuildConfig.OPENBENCHES_BASE_URL) }
            ?: throw SignInExpired()
        val page = withContext(Dispatchers.IO) {
            // The guarded add page greets signed-in users, and redirects to the login when the session has lapsed.
            http.newCall(Request.Builder().url(BuildConfig.OPENBENCHES_BASE_URL + "add/logged_in").header("Cookie", cookies).build())
                .execute().use { it.body?.string().orEmpty() }
        }
        if (parseWebsiteWelcome(page) == null) throw SignInExpired()
        return mapOf("Cookie" to cookies)
    }

    private fun readStoredAccount(): Account? =
        prefs.getString("nickname", null)?.let { Account(it, null, prefs.getString("provider", null), null) }

    companion object {
        /** Where WebsiteSignInActivity reports back; one sign-in at a time. */
        @Volatile internal var pending: CompletableDeferred<Account?>? = null
    }
}

/**
 * Reads "Welcome Github user ada" from the website's add page, which it shows once signed in.
 * Returns null when the page is the signed-out version.
 */
fun parseWebsiteWelcome(page: String): Account? {
    val text = page.replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
    val m = Regex("""Welcome\s+(\S+)\s+user\s+(\S+)""").find(text) ?: return null
    return Account(nickname = m.groupValues[2].trim(), name = null, provider = m.groupValues[1], pictureUrl = null)
}
