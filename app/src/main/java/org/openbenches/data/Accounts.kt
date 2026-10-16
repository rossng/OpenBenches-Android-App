package org.openbenches.data

import android.app.AlertDialog
import android.content.Context
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationAPIClient
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.provider.WebAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.openbenches.BuildConfig
import kotlin.coroutines.resume

/** Who's signed in, as shown in the app and credited on the website. */
data class Account(val nickname: String, val name: String?, val provider: String?, val pictureUrl: String?)

/** The user closed the sign-in page without signing in. */
class SignInCancelled : Exception("Sign-in cancelled")

/** The stored sign-in can no longer be used; the user needs to sign in again. */
class SignInExpired : Exception("Your sign-in has expired. Sign in again.")

/**
 * Signing in to OpenBenches. The website accepts an Auth0 ID token for this app's client in the
 * Auth0-Authorization header on /upload, and credits the bench to that account.
 */
interface AccountService {
    val account: StateFlow<Account?>

    /** Shows the sign-in page. Throws [SignInCancelled] if the user backs out. */
    suspend fun signIn(context: Context): Account

    suspend fun signOut(context: Context)

    /** A current ID token for uploads, refreshed if needed; null when signed out. Throws [SignInExpired]. */
    suspend fun idToken(): String?

    companion object {
        @Volatile private var instance: AccountService? = null

        /**
         * The app's account service: Auth0, or in debug builds made with -Pauth.fakeUrl, the local
         * fake Auth0 (e2e/backend/fake-auth0) so sign-in can be tested without a real tenant.
         */
        fun get(context: Context): AccountService = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                if (BuildConfig.FAKE_AUTH_URL.isNotEmpty()) FakeAccountService(app, BuildConfig.FAKE_AUTH_URL)
                else Auth0AccountService(app)
            }.also { instance = it }
        }
    }
}

/** Seconds since the epoch at which a JWT expires, read without verifying it (the server verifies). */
internal fun jwtExpiry(jwt: String): Long? = runCatching {
    val payload = jwt.split(".")[1]
    val json = String(java.util.Base64.getUrlDecoder().decode(payload))
    Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong()
}.getOrNull()

/** "GitHub" for "github|123", "OpenStreetMap" for "oidc|openstreetmap-openid|1", and so on. */
fun providerName(sub: String?): String? {
    val parts = sub?.split("|") ?: return null
    val connection = (if (parts.size >= 3) parts[1] else parts.firstOrNull()) ?: return null
    return when (connection.lowercase()) {
        "github" -> "GitHub"
        "twitter" -> "Twitter"
        "facebook" -> "Facebook"
        "google-oauth2" -> "Google"
        "openstreetmap-openid" -> "OpenStreetMap"
        "discord" -> "Discord"
        "auth0", "email" -> "email"
        else -> connection.replaceFirstChar { it.uppercase() }
    }
}

/** Refresh an ID token this close to expiry, so it's still valid when the upload reaches the server. */
private const val REFRESH_MARGIN_S = 5 * 60

/** Auth0's hosted login (Universal Login), as configured in app/build.gradle.kts. */
private class Auth0AccountService(private val context: Context) : AccountService {
    private val auth0 = Auth0(BuildConfig.AUTH0_CLIENT_ID, BuildConfig.AUTH0_DOMAIN)
    private val credentials = SecureCredentialsManager(context, AuthenticationAPIClient(auth0), SharedPreferencesStorage(context))
    private val _account = MutableStateFlow(if (credentials.hasValidCredentials()) readStoredAccount() else null)
    override val account: StateFlow<Account?> = _account.asStateFlow()

    override suspend fun signIn(context: Context): Account {
        val result = try {
            WebAuthProvider.login(auth0)
                .withScheme(BuildConfig.AUTH0_SCHEME)
                // offline_access gives a refresh token, so people stay signed in.
                .withScope("openid profile email offline_access")
                .await(context)
        } catch (e: AuthenticationException) {
            if (e.isCanceled) throw SignInCancelled() else throw e
        }
        credentials.saveCredentials(result)
        val user = result.user
        return Account(user.nickname ?: user.name ?: "you", user.name, providerName(user.getId()), user.pictureURL)
            .also { remember(it); _account.value = it }
    }

    override suspend fun signOut(context: Context) {
        // End the Auth0 session too, so the next sign-in can pick a different account.
        runCatching { WebAuthProvider.logout(auth0).withScheme(BuildConfig.AUTH0_SCHEME).await(context) }
        credentials.clearCredentials()
        prefs().edit().clear().apply()
        _account.value = null
    }

    override suspend fun idToken(): String? {
        if (_account.value == null) return null
        return try {
            val current = credentials.awaitCredentials()
            val exp = jwtExpiry(current.idToken) ?: 0
            if (exp - System.currentTimeMillis() / 1000 > REFRESH_MARGIN_S) current.idToken
            else credentials.awaitCredentials(null, 0, emptyMap(), true).idToken
        } catch (e: CredentialsManagerException) {
            throw SignInExpired()
        }
    }

    // The profile isn't readable from SecureCredentialsManager without decrypting, so keep the
    // displayed details alongside it.
    private fun prefs() = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private fun remember(a: Account) = prefs().edit()
        .putString("nickname", a.nickname).putString("name", a.name)
        .putString("provider", a.provider).putString("picture", a.pictureUrl).apply()

    private fun readStoredAccount(): Account? = prefs().getString("nickname", null)?.let {
        Account(it, prefs().getString("name", null), prefs().getString("provider", null), prefs().getString("picture", null))
    }
}

/**
 * Debug builds only: signs in as a test user of the local fake Auth0, which issues ID tokens the
 * local website verifies exactly as it does real ones. The real Auth0 login page isn't involved.
 */
class FakeAccountService(private val context: Context, private val baseUrl: String) : AccountService {
    private val http = OkHttpClient()
    private val prefs = context.getSharedPreferences("fake-account", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(readStoredAccount())
    override val account: StateFlow<Account?> = _account.asStateFlow()

    /** Test users offered by the fake: id to label. */
    suspend fun users(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val body = http.newCall(Request.Builder().url(baseUrl + "test/users").build()).execute().use { it.body!!.string() }
        val array = JSONArray(body)
        (0 until array.length()).map { i ->
            val u = array.getJSONObject(i)
            u.getString("id") to "${u.getString("name")} (${u.getString("provider")})"
        }
    }

    override suspend fun signIn(context: Context): Account {
        val users = users()
        val chosen = suspendCancellableCoroutine<String?> { cont ->
            val dialog = AlertDialog.Builder(context)
                .setTitle("Sign in as a test user")
                .setItems(users.map { it.second }.toTypedArray()) { _, which -> if (cont.isActive) cont.resume(users[which].first) }
                .setOnCancelListener { if (cont.isActive) cont.resume(null) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        } ?: throw SignInCancelled()
        return signInAs(chosen)
    }

    /** Signs in as the fake's user [userId] without any UI, for tests. [expired] gets an already-expired token. */
    suspend fun signInAs(userId: String, expired: Boolean = false): Account = withContext(Dispatchers.IO) {
        val json = mint(userId, expired)
        prefs.edit().putString("user", userId).putString("token", json.getString("id_token"))
            .putString("nickname", json.getString("nickname")).putString("name", json.getString("name"))
            .putString("provider", json.getString("provider")).apply()
        readStoredAccount()!!.also { _account.value = it }
    }

    override suspend fun signOut(context: Context) {
        prefs.edit().clear().apply()
        _account.value = null
    }

    override suspend fun idToken(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString("token", null) ?: return@withContext null
        val exp = jwtExpiry(token) ?: 0
        if (exp - System.currentTimeMillis() / 1000 > REFRESH_MARGIN_S) return@withContext token
        // Like a refresh token: get a fresh one for the same user, unless "refreshing" is switched off.
        if (prefs.getBoolean("noRefresh", false)) throw SignInExpired()
        val user = prefs.getString("user", null) ?: throw SignInExpired()
        mint(user, expired = false).getString("id_token").also { prefs.edit().putString("token", it).apply() }
    }

    /** For tests: stop [idToken] renewing an expired token, as when a refresh token is revoked. */
    fun disableRefresh() = prefs.edit().putBoolean("noRefresh", true).apply()

    private fun mint(userId: String, expired: Boolean): JSONObject {
        val url = baseUrl + "test/token?user=$userId" + if (expired) "&expired=1" else ""
        val body = http.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()).execute().use { it.body!!.string() }
        return JSONObject(body)
    }

    private fun readStoredAccount(): Account? = prefs.getString("nickname", null)?.let {
        Account(it, prefs.getString("name", null), prefs.getString("provider", null), null)
    }
}
