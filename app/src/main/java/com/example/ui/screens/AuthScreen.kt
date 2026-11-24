package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MotionPhotosOff
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.auth.GoogleSignInOutcome
import com.example.domain.auth.OptionalAuthGateway
import com.example.ui.components.DeepSpaceNeonBackground
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  authGateway: OptionalAuthGateway,
  onAuthSuccess: () -> Unit,
  initialReducedMotion: Boolean = false,
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val scope = rememberCoroutineScope()

  var isAttemptingSilent by remember { mutableStateOf(true) }
  var isSigningIn by remember { mutableStateOf(false) }
  var isEnteringGuest by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showPrivacyTermsDialog by remember { mutableStateOf(false) }
  var reducedMotion by remember(initialReducedMotion) { mutableStateOf(initialReducedMotion) }
  val isGoogleSignInConfigured = remember(authGateway) { authGateway.isGoogleSignInConfigured() }
  val googleSignInConfigNotice = remember(authGateway) { authGateway.googleSignInConfigurationMessage() }

  LaunchedEffect(Unit) {
    val signedIn = authGateway.attemptSilentSignIn()
    isAttemptingSilent = false
    if (signedIn) {
      onAuthSuccess()
    }
  }

  val borderTransition = rememberInfiniteTransition(label = "auth_card_neon_border")
  val borderAngle by
    borderTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 7000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "auth_border_angle",
    )
  val effectiveBorderAngle = if (reducedMotion) 25f else borderAngle

  Box(modifier = Modifier.fillMaxSize()) {
    DeepSpaceNeonBackground(
      modifier = Modifier.matchParentSize(),
      reducedMotion = reducedMotion,
    )

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        IconButton(
          onClick = { reducedMotion = !reducedMotion },
          modifier = Modifier.size(48.dp).testTag("auth_reduced_motion_toggle"),
        ) {
          Icon(
            imageVector =
              if (reducedMotion) Icons.Outlined.MotionPhotosOff
              else Icons.Outlined.MotionPhotosOn,
            contentDescription =
              if (reducedMotion) "Enable background motion" else "Enable reduced motion",
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Box(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(vertical = 8.dp),
        ) {
          // Central Glassmorphism Card with continuous 4-corner neon border & halo
          val cardCornerRadius = 28.dp
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .drawBehind {
                  val cr = cardCornerRadius.toPx()
                  val angleRad = Math.toRadians(effectiveBorderAngle.toDouble())
                  val dx = cos(angleRad).toFloat() * size.width * 0.55f
                  val dy = sin(angleRad).toFloat() * size.height * 0.55f
                  val center = Offset(size.width / 2f, size.height / 2f)
                  val neonBrush =
                    Brush.linearGradient(
                      colors =
                        listOf(
                          Color(0xFF00E5FF),
                          Color(0xFF2563EB),
                          Color(0xFF7C3AED),
                          Color(0xFFEC4899),
                        ),
                      start = center - Offset(dx, dy),
                      end = center + Offset(dx, dy),
                    )
                  // Outer diffused halo glow matching exact corner geometry
                  drawRoundRect(
                    brush = neonBrush,
                    cornerRadius = CornerRadius(cr, cr),
                    style = Stroke(width = 12.dp.toPx()),
                    alpha = 0.16f,
                  )
                  drawRoundRect(
                    brush = neonBrush,
                    cornerRadius = CornerRadius(cr, cr),
                    style = Stroke(width = 5.dp.toPx()),
                    alpha = 0.42f,
                  )
                  // Crisp continuous edge stroke
                  drawRoundRect(
                    brush = neonBrush,
                    cornerRadius = CornerRadius(cr, cr),
                    style = Stroke(width = 1.9.dp.toPx()),
                    alpha = 0.98f,
                  )
                }
                .clip(RoundedCornerShape(cardCornerRadius))
                .background(Color(0xFF070C1C).copy(alpha = 0.86f))
                .testTag("auth_screen_card"),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              MousomiOrbitalLogo(reducedMotion = reducedMotion)

              Spacer(modifier = Modifier.height(14.dp))

              Text(
                text = "Mousomi AI",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 29.sp,
                color = Color(0xFFF8FAFC),
                letterSpacing = (-0.3).sp,
              )

              Spacer(modifier = Modifier.height(8.dp))

              Text(
                text =
                  "Sign in with Google to unlock cloud conversation sync, Firebase AI Logic, and cross-device preferences.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
              )

              Spacer(modifier = Modifier.height(20.dp))

              // Dark inner security info panel
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0D152E).copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Shield,
                      contentDescription = null,
                      tint = NeonCyan,
                      modifier = Modifier.size(20.dp),
                    )
                    Box(
                      modifier =
                        Modifier
                          .width(1.5.dp)
                          .height(26.dp)
                          .background(NeonCyan.copy(alpha = 0.55f)),
                    )
                    Text(
                      text = "App Check & Firebase Private Workspace",
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.Medium,
                      color = Color(0xFFF1F5F9),
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Lock,
                      contentDescription = null,
                      tint = Color(0xFFE2E8F0),
                      modifier = Modifier.size(20.dp),
                    )
                    Box(
                      modifier =
                        Modifier
                          .width(1.5.dp)
                          .height(26.dp)
                          .background(Color(0xFF64748B).copy(alpha = 0.50f)),
                    )
                    Text(
                      text = "API keys remain encrypted locally on your device.",
                      style = MaterialTheme.typography.bodySmall,
                      fontSize = 12.5.sp,
                      lineHeight = 17.sp,
                      color = Color(0xFFCBD5E1),
                    )
                  }
                }
              }

              if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.errorContainer,
                  modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
                ) {
                  Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                  )
                }
              } else if (!isGoogleSignInConfigured && !googleSignInConfigNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = Color(0xFF3B240C).copy(alpha = 0.85f),
                  border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                  modifier = Modifier.fillMaxWidth().testTag("auth_google_config_notice"),
                ) {
                  Text(
                    text = googleSignInConfigNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFDE68A),
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                  )
                }
              }

              Spacer(modifier = Modifier.height(22.dp))

              // Primary full-width gradient "Sign in with Google" pill button
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
                enabled = !isSigningIn && !isAttemptingSilent && !isEnteringGuest,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                  ),
                border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.55f)),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.horizontalGradient(
                        listOf(Color(0xFF2563EB), Color(0xFF1D63FF), Color(0xFF0EA5E9))
                      )
                    )
                    .testTag("google_sign_in_button"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  if (isSigningIn || isAttemptingSilent) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(20.dp),
                      color = Color.White,
                      strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                      text = if (isAttemptingSilent) "Checking account…" else "Signing in…",
                      color = Color.White,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 15.5.sp,
                    )
                  } else {
                    GoogleGLogoBadge(size = 28.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                      text = "Sign in with Google",
                      color = Color.White,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 16.sp,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Guest Mode trigger below card with cyan & violet hairlines
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                  enabled = !isSigningIn && !isEnteringGuest,
                  role = Role.Button,
                ) {
                  isEnteringGuest = true
                  errorMessage = null
                  scope.launch {
                    authGateway.enterGuestMode()
                    isEnteringGuest = false
                    onAuthSuccess()
                  }
                }
                .padding(horizontal = 8.dp)
                .testTag("continue_without_sign_in_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(1.dp)
                  .background(
                    Brush.horizontalGradient(
                      listOf(Color.Transparent, NeonCyan.copy(alpha = 0.72f))
                    )
                  ),
            )
            Text(
              text = "Continue without signing in",
              color = Color(0xFFE2E8F0),
              fontSize = 14.5.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(horizontal = 14.dp),
            )
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(1.dp)
                  .background(
                    Brush.horizontalGradient(
                      listOf(Color(0xFFA855F7).copy(alpha = 0.72f), Color.Transparent)
                    )
                  ),
            )
          }
        }
      }

      // Centered bottom footer: "Privacy • Terms"
      Box(
        modifier =
          Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { showPrivacyTermsDialog = true }
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("auth_privacy_terms_footer"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Privacy  •  Terms",
          color = Color.White.copy(alpha = 0.88f),
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }
  }

  if (showPrivacyTermsDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyTermsDialog = false },
      title = { Text("Privacy & Terms") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Data & Security",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              "• Signed-in Google accounts enable private per-user Firestore cloud conversation sync protected by Firebase App Check.\n" +
                "• Continuing without signing in stores chats strictly in your device's local Room database without cloud sync.\n" +
                "• Personal API keys (Gemini, Groq, External Search) are encrypted with Android Keystore and never written to cloud storage or logs.",
            style = MaterialTheme.typography.bodySmall,
          )
          Text(
            text = "Terms of Use",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text =
              "AI outputs, search citations, and device automations depend on user-authorized permissions and provider availability. Review safety warnings and confirm sensitive device actions before execution.",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showPrivacyTermsDialog = false }) {
          Text("Close")
        }
      },
      modifier = Modifier.testTag("privacy_terms_dialog"),
    )
  }
}

@Composable
private fun MousomiOrbitalLogo(
  reducedMotion: Boolean,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "mousomi_orbital_logo")
  val orbitShift by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 6f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3600),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "orbit_tilt",
    )
  val tiltAdjust = if (reducedMotion) 0f else (orbitShift - 3f)

  Canvas(modifier = modifier.size(width = 148.dp, height = 98.dp)) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val sphereR = minOf(w, h) * 0.44f

    // Outer ambient glow
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color(0xFF00E5FF).copy(alpha = 0.28f),
              Color(0xFF6366F1).copy(alpha = 0.16f),
              Color.Transparent,
            ),
          center = Offset(cx, cy),
          radius = sphereR * 1.45f,
        ),
      radius = sphereR * 1.45f,
      center = Offset(cx, cy),
    )

    // Back tilted orbital ellipses
    val ringW = sphereR * 3.05f
    val ringH = sphereR * 0.86f
    rotate(degrees = -12f + tiltAdjust, pivot = Offset(cx, cy)) {
      drawOval(
        brush =
          Brush.linearGradient(
            listOf(Color(0xFF00E5FF).copy(alpha = 0.55f), Color(0xFF7C3AED).copy(alpha = 0.35f))
          ),
        topLeft = Offset(cx - ringW / 2f, cy - ringH / 2f),
        size = Size(ringW, ringH),
        style = Stroke(width = 1.6.dp.toPx()),
      )
    }
    rotate(degrees = 11f - tiltAdjust, pivot = Offset(cx, cy)) {
      drawOval(
        color = Color(0xFF38BDF8).copy(alpha = 0.32f),
        topLeft = Offset(cx - ringW * 0.94f / 2f, cy - ringH * 0.86f / 2f),
        size = Size(ringW * 0.94f, ringH * 0.86f),
        style = Stroke(width = 1.1.dp.toPx()),
      )
    }

    // Glowing spherical orb body (cyan top-right highlight -> cobalt blue -> electric violet bottom)
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              Color(0xFF22D3EE),
              Color(0xFF2563EB),
              Color(0xFF6366F1),
              Color(0xFF7C3AED),
            ),
          center = Offset(cx + sphereR * 0.22f, cy - sphereR * 0.28f),
          radius = sphereR * 1.35f,
        ),
      radius = sphereR,
      center = Offset(cx, cy),
    )

    // Subtle crisp glass rim
    drawCircle(
      brush =
        Brush.linearGradient(
          listOf(Color(0xFF67E8F9).copy(alpha = 0.65f), Color(0xFFA855F7).copy(alpha = 0.50f))
        ),
      radius = sphereR,
      center = Offset(cx, cy),
      style = Stroke(width = 1.2.dp.toPx()),
    )

    // Central crisp 4-point rounded star emblem
    val starR = sphereR * 0.58f
    val waist = starR * 0.23f
    val starPath =
      Path().apply {
        moveTo(cx, cy - starR)
        cubicTo(cx + waist * 0.42f, cy - waist, cx + waist, cy - waist * 0.42f, cx + starR, cy)
        cubicTo(cx + waist, cy + waist * 0.42f, cx + waist * 0.42f, cy + waist, cx, cy + starR)
        cubicTo(cx - waist * 0.42f, cy + waist, cx - waist, cy + waist * 0.42f, cx - starR, cy)
        cubicTo(cx - waist, cy - waist * 0.42f, cx - waist * 0.42f, cy - waist, cx, cy - starR)
        close()
      }
    drawPath(
      path = starPath,
      brush =
        Brush.linearGradient(
          colors = listOf(Color(0xFFE0F2FE), Color(0xFF7DD3FC), Color(0xFF60A5FA)),
          start = Offset(cx, cy - starR),
          end = Offset(cx, cy + starR),
        ),
    )
  }
}

@Composable
private fun GoogleGLogoBadge(size: Dp = 28.dp) {
  Box(
    modifier = Modifier.size(size).clip(CircleShape).background(Color.White),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(size * 0.68f)) {
      val w = this.size.width
      val h = this.size.height
      val strokeW = w * 0.21f
      val inset = strokeW / 2f
      val arcSize = Size(w - strokeW, h - strokeW)
      val arcTopLeft = Offset(inset, inset)
      val stroke = Stroke(width = strokeW, cap = StrokeCap.Butt)

      // Blue right/bottom-right sweep
      drawArc(
        color = Color(0xFF4285F4),
        startAngle = -14f,
        sweepAngle = 72f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = stroke,
      )
      // Green bottom sweep
      drawArc(
        color = Color(0xFF34A853),
        startAngle = 55f,
        sweepAngle = 92f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = stroke,
      )
      // Yellow left sweep
      drawArc(
        color = Color(0xFFFBBC05),
        startAngle = 144f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = stroke,
      )
      // Red top sweep
      drawArc(
        color = Color(0xFFEA4335),
        startAngle = 212f,
        sweepAngle = 104f,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = stroke,
      )
      // Google G horizontal blue bar
      drawLine(
        color = Color(0xFF4285F4),
        start = Offset(w * 0.51f, h * 0.50f),
        end = Offset(w - inset * 0.55f, h * 0.50f),
        strokeWidth = strokeW * 0.94f,
        cap = StrokeCap.Butt,
      )
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
