package org.openbenches.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.openbenches.data.Account
import org.openbenches.data.AccountService
import org.openbenches.data.SignInCancelled

/** The signed-in account, and actions to sign in and out from anywhere in the UI. */
class SignInActions(val account: State<Account?>, val signIn: () -> Unit, val signOut: () -> Unit)

@Composable
fun rememberSignIn(): SignInActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { AccountService.get(context) }
    val account = service.account.collectAsState()
    return remember(service) {
        SignInActions(
            account = account,
            signIn = {
                scope.launch {
                    try {
                        val a = service.signIn(context)
                        Toast.makeText(context, "Signed in as ${a.nickname}", Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: SignInCancelled) {
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't sign in. Check your connection and try again.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            signOut = { scope.launch { service.signOut(context) } },
        )
    }
}

/** "ada-benches (GitHub)" */
fun Account.label(): String = nickname + (provider?.let { " ($it)" } ?: "")
