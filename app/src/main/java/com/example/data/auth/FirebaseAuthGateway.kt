package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.domain.auth.AuthUserSession
import com.example.domain.auth.EntitlementTier
import com.example.domain.auth.GoogleSignInOutcome
import com.example.domain.auth.OptionalAuthGateway
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class FirebaseAuthGateway @Inject constructor(
  @ApplicationContext private val context: Context,
) : OptionalAuthGateway {

  private val auth: FirebaseAuth by lazy { Firebase.auth }
  private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }
  private val beforeSignOutHooks = CopyOnWriteArrayList<() -> Unit>()

  private val sessionFlow = MutableStateFlow(AuthUserSession())
  private var listenerRegistered = false

  private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
    sessionFlow.value = firebaseAuth.currentUser.toSession()
  }

  private fun ensureListener() {
    if (!listenerRegistered) {
      synchronized(this) {
        if (!listenerRegistered) {
          runCatching {
            sessionFlow.value = auth.currentUser.toSession()
            auth.addAuthStateListener(authListener)
            listenerRegistered = true
          }.onFailure { e ->
            Log.w("Auth", "FirebaseAuth listener init deferred: ${e.message}")
          }
        }
      }
    }
  }

  override fun observeSession(): Flow<AuthUserSession> {
    ensureListener()
    return sessionFlow.asStateFlow()
  }

  override fun getCurrentSession(): AuthUserSession {
    ensureListener()
    return sessionFlow.value
  }

  override suspend fun attemptSilentSignIn(): Boolean {
    ensureListener()
    if (auth.currentUser != null) {
      sessionFlow.value = auth.currentUser.toSession()
      return true
    }
    val clientId = resolveWebClientId() ?: return false
    if (clientId == "missing-google-services-json") return false
    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()
    return try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        sessionFlow.value = auth.currentUser.toSession()
        true
      } else {
        false
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d("Auth", "Silent sign-in unavailable: ${e.message}")
      false
    }
  }

  override suspend fun signInWithGoogle(activity: Activity): GoogleSignInOutcome {
    ensureListener()
    val clientId = resolveWebClientId()
      ?: return GoogleSignInOutcome.Error(
        "Google Sign-In configuration missing: default_web_client_id not found."
      )
    if (clientId == "missing-google-services-json") {
      return GoogleSignInOutcome.Error(
        "Add a valid google-services.json with an OAuth Web Client ID to sign in."
      )
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .build()

    return try {
      val cred = credentialManager.getCredential(activity, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        val updated = auth.currentUser.toSession()
        sessionFlow.value = updated
        GoogleSignInOutcome.Success(updated)
      } else {
        GoogleSignInOutcome.Error("Unexpected credential type returned by Credential Manager.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      GoogleSignInOutcome.Cancelled
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      GoogleSignInOutcome.Error(e.localizedMessage ?: "Google Sign-In failed.")
    }
  }

  override fun registerBeforeSignOutHook(hook: () -> Unit) {
    beforeSignOutHooks.add(hook)
  }

  override suspend fun signOut() {
    ensureListener()
    withContext(NonCancellable) {
      beforeSignOutHooks.forEach { hook ->
        runCatching { hook() }
      }
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        runCatching { auth.signOut() }
        sessionFlow.value = AuthUserSession()
      }
    }
  }

  private fun resolveWebClientId(): String? =
    try {
      context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }

  private fun FirebaseUser?.toSession(): AuthUserSession {
    if (this == null) return AuthUserSession()
    return AuthUserSession(
      uid = uid,
      isSignedIn = true,
      displayName = displayName?.takeIf { it.isNotBlank() } ?: email ?: "Mousomi User",
      email = email,
      photoUrl = photoUrl?.toString(),
      entitlementTier = EntitlementTier.MANAGED_CLOUD,
    )
  }
}
