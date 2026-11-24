package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.core.accessibility.AccessibilityWorkflowState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityOnboardingScreen(
  workflowState: AccessibilityWorkflowState,
  isOsAccessibilityEnabled: Boolean,
  onRefreshState: () -> Unit,
  onStopWorkflow: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val colors = LocalMousomiColors.current

  DisposableEffect(lifecycleOwner) {
    onRefreshState()
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) onRefreshState()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val effectiveConnected = isOsAccessibilityEnabled || workflowState.isServiceConnected

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.accessibility_onboarding_title)) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("accessibility_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          IconButton(onClick = onRefreshState, modifier = Modifier.testTag("accessibility_refresh_button")) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Accessibility status")
          }
        },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = if (effectiveConnected) NeonGreen.copy(alpha = 0.12f) else WarmAmber.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, if (effectiveConnected) NeonGreen else WarmAmber),
          modifier = Modifier.fillMaxWidth().testTag("accessibility_status_card"),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(
                imageVector = if (effectiveConnected) Icons.Outlined.CheckCircle else Icons.Outlined.AccessibilityNew,
                contentDescription = null,
                tint = if (effectiveConnected) NeonGreen else WarmAmber,
              )
              Text(
                text =
                  if (effectiveConnected) "Mousomi Accessibility Assistant: Enabled"
                  else "Optional Feature: Currently Disabled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Text(
              text =
                if (effectiveConnected) {
                  "User-authorized UI navigation is active. Active window: ${workflowState.activePackageName.ifBlank { "System" }}."
                } else {
                  "Mousomi works normally without Accessibility access. Enable this only if you want hands-free scrolling, UI button tapping, or form entry inside other apps."
                },
              style = MaterialTheme.typography.bodySmall,
              color = colors.mutedText,
            )
            if (workflowState.lastActionResult.isNotBlank()) {
              Text(
                text = "Status: ${workflowState.lastActionResult}",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = {
                  runCatching {
                    context.startActivity(
                      Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      }
                    )
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.weight(1f).testTag("open_accessibility_settings_button"),
              ) {
                Text(
                  text = if (effectiveConnected) "Manage in Settings" else "Open Accessibility Settings",
                  color = Color.White,
                )
              }
              if (workflowState.isWorkflowRunning) {
                Button(
                  onClick = onStopWorkflow,
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                  modifier = Modifier.testTag("stop_accessibility_workflow_button"),
                ) {
                  Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Stop", color = Color.White)
                }
              }
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What Mousomi Can Do When Enabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("• Read visible non-sensitive UI labels on your screen when you ask for assistance.", style = MaterialTheme.typography.bodySmall)
            Text("• Tap explicitly matched buttons, links, or tabs by visible label (never blind coordinates).", style = MaterialTheme.typography.bodySmall)
            Text("• Scroll screens up/down or enter text into explicitly targeted fields.", style = MaterialTheme.typography.bodySmall)
            Text("• Pause or ask your confirmation before Send, Post, Pay, Delete, or Submit actions.", style = MaterialTheme.typography.bodySmall)
          }
        }

        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Outlined.Security, contentDescription = null, tint = NeonCyan)
              Text("Strict Privacy & Security Safeguards", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text("• Password, PIN, OTP/2FA, CVV, and payment fields are strictly excluded and never read or filled.", style = MaterialTheme.typography.bodySmall)
            Text("• Mousomi never enables Accessibility silently and never runs remote/hidden automation.", style = MaterialTheme.typography.bodySmall)
            Text("• Screen tree data stays strictly on-device unless you explicitly send a prompt.", style = MaterialTheme.typography.bodySmall)
            Text("• Disable anytime in Android Settings > Accessibility > Mousomi Accessibility Assistant.", style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
          }
        }

        if (workflowState.visibleNodesPreview.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                "Safe Node Preview (${workflowState.activePackageName})",
                style = MaterialTheme.typography.labelLarge,
              )
              workflowState.visibleNodesPreview.take(10).forEach { node ->
                val tag = buildString {
                  if (node.isClickable) append("[Clickable] ")
                  if (node.isEditable) append("[Editable] ")
                  if (node.isScrollable) append("[Scrollable] ")
                }
                Text(
                  text = "$tag${node.primaryLabel}",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.mutedText,
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}
