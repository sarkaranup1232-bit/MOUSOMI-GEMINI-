package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.auth.GoogleSignInOutcome
import com.example.domain.auth.OptionalAuthGateway
import com.example.ui.components.GeminiFourPointSparkle
import com.example.ui.components.GeminiSmokyAmbientBackground
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  authGateway: OptionalAuthGateway,
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val scope = rememberCoroutineScope()
  val colors = LocalMousomiColors.current

  var isAttemptingSilent by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    val signedIn = authGateway.attemptSilentSignIn()
    isAttemptingSilent = false
    if (signedIn) {
      onAuthSuccess()
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
  ) {
    GeminiSmokyAmbientBackground(modifier = Modifier.matchParentSize())

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
      border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.28f)),
      tonalElevation = 8.dp,
      modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).testTag("auth_screen_card"),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier
              .size(68.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF1D4ED8), Color(0xFF06B6D4), Color(0xFF8B5CF6))
                )
              ),
          contentAlignment = Alignment.Center,
        ) {
          GeminiFourPointSparkle(size = 34.dp, animated = true)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "Mousomi AI",
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 26.sp,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Sign in with Google to unlock cloud conversation sync, Firebase AI Logic, and cross-device preferences.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.activeChipContainer.copy(alpha = 0.55f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.Shield, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "App Check & Firestore Private Workspace",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(18.dp))
              Text(
                text = "API keys remain encrypted locally on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedText,
              )
            }
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(14.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp),
              textAlign = TextAlign.Center,
            )
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Button(
          onClick = {
            if (activity == null) {
              errorMessage = "Activity context unavailable for Google Sign-In."
              return@Button
            }
            isSigningIn = true
            errorMessage = null
            scope.launch {
              when (val outcome = authGateway.signInWithGoogle(activity)) {
                is GoogleSignInOutcome.Success -> {
                  isSigningIn = false
                  onAuthSuccess()
                }
                GoogleSignInOutcome.Cancelled -> {
                  isSigningIn = false
                }
                is GoogleSignInOutcome.Error -> {
                  isSigningIn = false
                  errorMessage = outcome.message
                }
              }
            }
          },
          enabled = !isSigningIn && !isAttemptingSilent,
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = colors.liveButtonBlue),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("google_sign_in_button"),
        ) {
          if (isSigningIn || isAttemptingSilent) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = if (isAttemptingSilent) "Checking account…" else "Signing in…",
              color = Color.White,
              fontWeight = FontWeight.Medium,
            )
          } else {
            Text(
              text = "Sign in with Google",
              color = Color.White,
              fontWeight = FontWeight.Medium,
              fontSize = 15.5.sp,
            )
          }
        }
      }
    }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
