package com.example.online

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    trySend(currentUser)
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun signInWithGoogleCredential(credential: Credential): Boolean {
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        return true
    }
    return false
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val activity = context.findActivity()
            val targetContext: Context = if (activity != null) MutableContextWrapper(activity) else context
            val cred = credentialManager.getCredential(targetContext, request).credential
            if (signInWithGoogleCredential(cred)) {
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String?) -> Unit = {},
    forceAccountPickerDialog: Boolean = false
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Falta la configuración de Google Sign-In en el proyecto.")
        return
    }

    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("No se encontró la pantalla activa para iniciar sesión.")
            return@launch
        }
        val uiContext = MutableContextWrapper(activity)

        if (!forceAccountPickerDialog) {
            val deviceAccountsOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val oneTapRequest = GetCredentialRequest.Builder()
                .addCredentialOption(deviceAccountsOption)
                .build()
            try {
                val cred = credentialManager.getCredential(uiContext, oneTapRequest).credential
                if (signInWithGoogleCredential(cred)) {
                    onAuthSuccess()
                } else {
                    onAuthError("Tipo de credencial inesperado al validar tu cuenta.")
                }
                return@launch
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google One-Tap cancelled or dismissed: ${e.message}", e)
                onAuthCancelled(
                    "No se completó el inicio de sesión. Toca 'ELEGIR CUENTA DE GOOGLE' otra vez o usa 'Usar o agregar otra cuenta'."
                )
                return@launch
            } catch (_: NoCredentialException) {
                // Fall through to explicit Sign In With Google sheet below
            } catch (e: Exception) {
                Log.w("Auth", "Device account picker unavailable, falling back to SignInWithGoogle: ${e.message}")
            }
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val buttonRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        try {
            val cred = credentialManager.getCredential(uiContext, buttonRequest).credential
            if (signInWithGoogleCredential(cred)) {
                onAuthSuccess()
            } else {
                onAuthError("Tipo de credencial inesperado al validar tu cuenta.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled(
                "El inicio de sesión fue cancelado o no se completó en Google. Inténtalo de nuevo."
            )
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "No se pudo iniciar sesión con Google.")
        }
    }
}

fun signOutFromFirebase(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun GoogleSignInGateCard(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCard),
        border = BorderStroke(1.5.dp, ChampionshipGold.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Surface(
                color = ChampionshipGold.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ChampionshipGold,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(32.dp)
                )
            }

            Text(
                text = "ARENA ONLINE 1v1",
                style = MaterialTheme.typography.headlineMedium,
                color = ChampionshipGold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Elige directamente una de las cuentas de Google de tu dispositivo con un toque para crear salas y pelear online.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            if (errorMessage != null) {
                Surface(
                    color = CrimsonCorner.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, CrimsonCorner),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        forceAccountPickerDialog = false
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampionshipGold,
                    contentColor = ArenaObsidian
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = ArenaObsidian,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ELEGIR CUENTA DE GOOGLE",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        forceAccountPickerDialog = true
                    )
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("google_other_account_button")
            ) {
                Text(
                    text = "Usar o agregar otra cuenta de Google",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampionshipGold
                )
            }

            Text(
                text = "También puedes usar el modo 1v1 Local sin conexión.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSteel,
                textAlign = TextAlign.Center
            )
        }
    }
}
