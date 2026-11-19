package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.WarmCream
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
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      onUnauthenticated()
      return
    }
  val googleIdOption =
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
  scope.launch {
    try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
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
  onAuthCancelled: () -> Unit = {},
) {
  val clientId =
    try {
      context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
      onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
      return
    }
  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
  scope.launch {
    try {
      val activity = context as? Activity
      val cred =
        if (activity != null) {
          credentialManager.getCredential(activity, request).credential
        } else {
          credentialManager.getCredential(context, request).credential
        }
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed")
    }
  }
}

fun signOut(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {},
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
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isCheckingSilent by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingSilent = false
        onAuthSuccess()
      },
      onUnauthenticated = { isCheckingSilent = false },
      scope = scope,
    )
  }

  Scaffold { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Brush.verticalGradient(listOf(CrimsonDark, CrimsonPrimary, WarmCream)))
          .padding(innerPadding)
          .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = GoldAccent.copy(alpha = 0.16f),
            modifier = Modifier.size(68.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = "Avarani Bastralaya",
                tint = CrimsonPrimary,
                modifier = Modifier.size(38.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "আবরণী বস্ত্রালয়",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = CrimsonPrimary,
            textAlign = TextAlign.Center,
          )
          Text(
            text = "Avarani Bastralaya POS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Nimtala Bazar, Khejuri, Purba Medinipur - 721431",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(18.dp))

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFDF7ED),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CloudDone,
                  contentDescription = null,
                  tint = Color(0xFF047857),
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Secure Cloud Khata & Backup",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text =
                  "Sign in with Google to protect your bills, customer khata, daily accounts, and product stock across devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFFEF2F2),
              modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
            ) {
              androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = "Error",
                  tint = Color(0xFFB91C1C),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = errorMessage ?: "",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFF991B1B),
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          Button(
            onClick = {
              errorMessage = null
              isSigningIn = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isSigningIn = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isSigningIn = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = { isSigningIn = false },
              )
            },
            enabled = !isSigningIn && !isCheckingSilent,
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth().height(52.dp).testTag("google_sign_in_button"),
          ) {
            if (isSigningIn || isCheckingSilent) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = if (isCheckingSilent) "Checking account..." else "Signing in...",
                fontWeight = FontWeight.Bold,
              )
            } else {
              Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}
