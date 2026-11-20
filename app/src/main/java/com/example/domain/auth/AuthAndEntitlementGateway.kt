package com.example.domain.auth

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthUserSession(
  val uid: String = "",
  val isSignedIn: Boolean = false,
  val displayName: String = "Guest Creator",
  val email: String? = null,
  val photoUrl: String? = null,
  val entitlementTier: EntitlementTier = EntitlementTier.LOCAL_BYOK,
)

enum class EntitlementTier(val label: String) {
  LOCAL_BYOK("Multi-Model Engine"),
  MANAGED_CLOUD("Cloud Sync & AI Logic"),
}

sealed interface GoogleSignInOutcome {
  data class Success(val session: AuthUserSession) : GoogleSignInOutcome
  data object Cancelled : GoogleSignInOutcome
  data class Error(val message: String) : GoogleSignInOutcome
}

interface OptionalAuthGateway {
  fun observeSession(): Flow<AuthUserSession>

  fun getCurrentSession(): AuthUserSession

  suspend fun attemptSilentSignIn(): Boolean = false

  suspend fun signInWithGoogle(activity: Activity): GoogleSignInOutcome =
    GoogleSignInOutcome.Error("Interactive sign-in not configured in this test gateway.")

  fun registerBeforeSignOutHook(hook: () -> Unit) {}

  suspend fun signOut()
}

@Singleton
class DefaultNoOpAuthGateway @Inject constructor() : OptionalAuthGateway {
  private val sessionFlow = MutableStateFlow(AuthUserSession())

  override fun observeSession(): Flow<AuthUserSession> = sessionFlow.asStateFlow()

  override fun getCurrentSession(): AuthUserSession = sessionFlow.value

  override suspend fun signOut() {
    sessionFlow.value = AuthUserSession()
  }
}
