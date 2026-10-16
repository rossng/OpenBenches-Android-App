package org.openbenches

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.sentry.android.core.SentryAndroid
import org.openbenches.ui.browse.BrowseScreen
import org.openbenches.ui.theme.OpenBenchesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SentryAndroid.init(this) { options ->
            options.dsn = "https://examplePublicKey@o0.ingest.sentry.io/0" // TODO: Replace with your Sentry DSN
        }
        setContent {
            OpenBenchesTheme {
                BrowseScreen()
            }
        }
    }
}
