package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.FirebaseAuthGateway
import com.example.domain.auth.AuthUserSession
import com.example.domain.auth.GoogleSignInOutcome
import com.example.domain.auth.OptionalAuthGateway
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseGoogleSignInConfigTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `FirebaseAuthGateway rejects missing or placeholder web client IDs and accepts valid client ID format`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gateway = FirebaseAuthGateway(context)

    assertFalse(gateway.isValidWebClientId(""))
    assertFalse(gateway.isValidWebClientId("missing-google-services-json"))
    assertFalse(gateway.isValidWebClientId("your_web_client_id_here"))
    assertFalse(gateway.isValidWebClientId("placeholder-client-id-12345.test"))
    assertTrue(gateway.isValidWebClientId("123456789012-abc123def456.oauth-client.example"))
  }

  @Test
  fun `FirebaseAuthGateway reports explicit diagnostic error when google-services json is absent`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val gateway = FirebaseAuthGateway(context)

      if (!gateway.isGoogleSignInConfigured()) {
        val message = gateway.googleSignInConfigurationMessage()
        assertNotNull(message)
        assertTrue(message!!.isNotBlank())

        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val outcome = gateway.signInWithGoogle(activity)
        assertTrue(outcome is GoogleSignInOutcome.Error)
        assertFalse(gateway.attemptSilentSignIn())
      }
    }

  @Test
  fun `AuthScreen displays configuration notice when Google Sign-In is not configured`() {
    val unconfiguredGateway =
      object : OptionalAuthGateway {
        private val flow = MutableStateFlow(AuthUserSession())
        override fun isGoogleSignInConfigured(): Boolean = false
        override fun googleSignInConfigurationMessage(): String =
          "Firebase is not configured in this build (google-services.json missing)."
        override fun observeSession() = flow
        override fun getCurrentSession() = flow.value
        override suspend fun signOut() {
          flow.value = AuthUserSession()
        }
      }

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        AuthScreen(authGateway = unconfiguredGateway, onAuthSuccess = {})
      }
    }

    composeRule.onNodeWithTag("auth_google_config_notice").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("continue_without_sign_in_button").performScrollTo().assertIsDisplayed()
  }
}
