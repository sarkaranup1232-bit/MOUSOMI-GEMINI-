package com.example.domain.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modular authentication & entitlement boundary for Mousomi AI.
 * Supports immediate frictionless usage with no mandatory sign-in wall,
 * while keeping future Google Account sign-in and entitlement checks cleanly decoupled.
 */
data class AuthUserSession(
  val isSignedIn: Boolean = false,
  val displayName: String = "Guest Creator",
  val email: String? = null,
  val entitlementTier: EntitlementTier = EntitlementTier.LOCAL_BYOK,
)

enum class EntitlementTier(val label: String) {
  LOCAL_BYOK("Multi-Model Engine"),
  MANAGED_CLOUD("Cloud Proxy"),
}

interface OptionalAuthGateway {
  fun observeSession(): Flow<AuthUserSession>

  fun getCurrentSession(): AuthUserSession

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
