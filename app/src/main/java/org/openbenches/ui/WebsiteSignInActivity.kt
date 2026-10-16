package org.openbenches.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.openbenches.BuildConfig
import org.openbenches.data.WebsiteAccountService
import org.openbenches.data.parseWebsiteWelcome

/**
 * Shows openbenches.org's own sign-in (its "Sign in" on the add page, through Auth0) in a browser
 * view, and closes once the website says who's signed in. The session cookie stays in the app's
 * cookie store for uploads. Temporary: see WebsiteAccountService for how to remove it.
 */
class WebsiteSignInActivity : ComponentActivity() {
    private var reported = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Sign in to OpenBenches"
        val base = Uri.parse(BuildConfig.OPENBENCHES_BASE_URL)
        CookieManager.getInstance().setAcceptCookie(true)

        val web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true // Auth0's login page needs it
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

                override fun onPageFinished(view: WebView, url: String) {
                    val uri = Uri.parse(url)
                    // After signing in the website sends people back to its add page (/add/logged_in),
                    // which greets them by name. Already signed in, it goes straight there.
                    if (uri.host == base.host && uri.path.orEmpty().startsWith("/add")) {
                        view.evaluateJavascript("document.body.innerHTML") { html ->
                            val page = html?.let { org.json.JSONTokener(it).nextValue() as? String }.orEmpty()
                            val account = parseWebsiteWelcome(page)
                            if (account != null) {
                                CookieManager.getInstance().flush()
                                report(account)
                                Toast.makeText(this@WebsiteSignInActivity, "Signed in as ${account.nickname}", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    }
                }
            }
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        // The add page's "Sign in" link: the website's Auth0 login, then back to /add.
        if (savedInstanceState == null) web.loadUrl(BuildConfig.OPENBENCHES_BASE_URL + "add/logged_in")
    }

    private fun report(account: org.openbenches.data.Account?) {
        if (reported) return
        reported = true
        WebsiteAccountService.pending?.complete(account)
    }

    override fun onDestroy() {
        if (isFinishing) report(null) // closed without signing in
        super.onDestroy()
    }
}
