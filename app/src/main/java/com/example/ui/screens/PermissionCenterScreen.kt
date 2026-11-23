package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.domain.model.PermissionCategory
import com.example.domain.model.PermissionCategoryItem
import com.example.domain.model.PermissionState
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarmAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
  categories: List<PermissionCategoryItem>,
  isEmergencyActive: Boolean,
  onRefreshPermissions: () -> Unit,
  onTriggerEmergencyStop: () -> Unit,
  onOpenScreenShare: () -> Unit,
  onStopScreenShare: () -> Unit,
  onOpenOverlaySettings: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenAssistantSettings: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var pendingExplanation by remember { mutableStateOf<PermissionCategoryItem?>(null) }
  var pickedContactNote by remember { mutableStateOf<String?>(null) }

  val runtimePermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
      onRefreshPermissions()
    }

  val contactPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickContact()
    ) { uri ->
      pickedContactNote =
        if (uri != null) "Selected contact via zero-permission Android Contact Picker."
        else null
    }

  DisposableEffect(lifecycleOwner) {
    onRefreshPermissions()
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        onRefreshPermissions()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.permission_center_title)) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("permission_center_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          IconButton(onClick = onRefreshPermissions, modifier = Modifier.testTag("refresh_permissions_button")) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh permission states")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PrivacyDashboard(
          categories = categories,
          isEmergencyActive = isEmergencyActive,
          onTriggerEmergencyStop = onTriggerEmergencyStop,
          onManageAllPermissions = {
            runCatching {
              context.startActivity(
                Intent(
                  Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  Uri.parse("package:${context.packageName}"),
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
              )
            }
          },
          onTestContactPicker = { contactPickerLauncher.launch(null) },
          onOpenDialer = {
            runCatching {
              context.startActivity(
                Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
              )
            }
          },
          pickedContactNote = pickedContactNote,
        )

        categories.forEach { item ->
          PermissionCard(
            item = item,
            onRequestOrManage = {
              when {
                item.isRuntimeRequestable && item.manifestPermission != null -> {
                  pendingExplanation = item
                }
                item.category == PermissionCategory.SCREEN_CAPTURE -> {
                  if (item.state == PermissionState.GRANTED) onStopScreenShare() else onOpenScreenShare()
                }
                item.category == PermissionCategory.OVERLAY -> onOpenOverlaySettings()
                item.category == PermissionCategory.NOTIFICATION_LISTENER -> onOpenNotificationAccess()
                item.category == PermissionCategory.ACCESSIBILITY ||
                  item.category == PermissionCategory.DEFAULT_ASSISTANT -> onOpenAssistantSettings()
                item.category == PermissionCategory.EXACT_ALARM -> {
                  runCatching {
                    val intent =
                      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Intent(
                          Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                          Uri.parse("package:${context.packageName}"),
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                      } else {
                        Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:${context.packageName}"),
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                      }
                    context.startActivity(intent)
                  }
                }
                item.category == PermissionCategory.NOTIFICATIONS -> {
                  runCatching {
                    context.startActivity(
                      Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                  }
                }
                item.category == PermissionCategory.BATTERY_OPTIMIZATION -> {
                  runCatching {
                    context.startActivity(
                      Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      }
                    )
                  }
                }
                item.category == PermissionCategory.NFC_HARDWARE -> {
                  runCatching {
                    context.startActivity(
                      Intent(Settings.ACTION_NFC_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                  }
                }
                item.category == PermissionCategory.CONTACTS_PICKER -> contactPickerLauncher.launch(null)
                item.category == PermissionCategory.PHONE_DIAL -> {
                  runCatching {
                    context.startActivity(
                      Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                  }
                }
                else -> {
                  runCatching {
                    context.startActivity(
                      Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                      ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                  }
                }
              }
            },
            onOpenAppSettings = {
              runCatching {
                context.startActivity(
                  Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                  ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
              }
            },
          )
        }
      }
    }
  }

  pendingExplanation?.let { item ->
    PermissionExplanationDialog(
      item = item,
      onContinue = {
        val perm = item.manifestPermission
        pendingExplanation = null
        if (perm != null) {
          runtimePermissionLauncher.launch(perm)
        }
      },
      onDismiss = { pendingExplanation = null },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyDashboard(
  categories: List<PermissionCategoryItem>,
  isEmergencyActive: Boolean,
  onTriggerEmergencyStop: () -> Unit,
  onManageAllPermissions: () -> Unit,
  onTestContactPicker: () -> Unit,
  onOpenDialer: () -> Unit,
  pickedContactNote: String?,
) {
  val colors = LocalMousomiColors.current
  val grantedCount = categories.count { it.state == PermissionState.GRANTED }
  val zeroPermCount = categories.count { it.state == PermissionState.ZERO_PERMISSION_PICKER }
  val blockedOrSettingsCount = categories.size - grantedCount - zeroPermCount

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF7F1D1D).copy(alpha = 0.25f),
      border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("emergency_stop_card"),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(R.string.emergency_stop_label),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFFFCA5A5),
        )
        Text(
          text = stringResource(R.string.emergency_stop_description),
          style = MaterialTheme.typography.bodySmall,
          color = colors.mutedText,
        )
        Button(
          onClick = onTriggerEmergencyStop,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
          shape = CircleShape,
          modifier = Modifier.fillMaxWidth().testTag("permission_emergency_stop_button"),
        ) {
          Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color.White)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isEmergencyActive) "Stop Active Sessions Now" else "Emergency Stop (All Idle)",
            color = Color.White,
          )
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(20.dp),
      color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
      border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
      modifier = Modifier.fillMaxWidth().testTag("privacy_dashboard_card"),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = NeonCyan)
          Text("Privacy & Permission Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
          text = "Live Android OS state (${categories.size} categories): $grantedCount active • $zeroPermCount zero-permission pickers • $blockedOrSettingsCount optional / awaiting consent",
          style = MaterialTheme.typography.bodySmall,
          color = colors.mutedText,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onManageAllPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            modifier = Modifier.testTag("manage_all_permissions_button"),
          ) {
            Text(stringResource(R.string.manage_all_permissions), color = Color.White)
          }
          OutlinedButton(onClick = onTestContactPicker) {
            Icon(Icons.Outlined.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Contact Picker")
          }
          OutlinedButton(onClick = onOpenDialer) {
            Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Dialer")
          }
        }
        if (!pickedContactNote.isNullOrBlank()) {
          Text(pickedContactNote, style = MaterialTheme.typography.labelSmall, color = NeonGreen)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionCard(
  item: PermissionCategoryItem,
  onRequestOrManage: () -> Unit,
  onOpenAppSettings: () -> Unit = {},
) {
  val colors = LocalMousomiColors.current
  val checkedFormatted =
    remember(item.lastCheckedAtMs) {
      java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(item.lastCheckedAtMs))
    }

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    modifier = Modifier.fillMaxWidth().testTag("permission_card_${item.category.name}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("${item.category.group} • Feature: ${item.relatedFeature}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
          Text(item.category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        PermissionStatusIndicator(state = item.state)
      }
      Text(item.whyNeeded, style = MaterialTheme.typography.bodySmall)
      Text("Data use: ${item.dataUsageDisclosure}", style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
      Text("Control: ${item.howToManage} • Checked $checkedFormatted", style = MaterialTheme.typography.labelSmall, color = colors.mutedText)

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (item.isRuntimeRequestable || item.requiresSpecialSettings || item.category == PermissionCategory.CONTACTS_PICKER || item.category == PermissionCategory.PHONE_DIAL) {
          OutlinedButton(
            onClick = onRequestOrManage,
            modifier = Modifier.testTag("permission_action_${item.category.name}"),
          ) {
            Text(
              when {
                item.state == PermissionState.GRANTED && item.isRuntimeRequestable -> "Allowed (Review)"
                item.isRuntimeRequestable -> "Request Permission"
                item.state == PermissionState.ZERO_PERMISSION_PICKER -> "Try Zero-Permission Action"
                item.category == PermissionCategory.EXACT_ALARM -> "Configure Exact Alarm"
                else -> "Open System Settings"
              }
            )
          }
        }
        OutlinedButton(
          onClick = onOpenAppSettings,
          modifier = Modifier.testTag("permission_app_settings_${item.category.name}"),
        ) {
          Text("App Settings")
        }
      }
    }
  }
}

@Composable
fun PermissionStatusIndicator(state: PermissionState) {
  val (tint, icon) =
    when (state) {
      PermissionState.GRANTED -> NeonGreen to Icons.Outlined.CheckCircle
      PermissionState.ZERO_PERMISSION_PICKER -> NeonCyan to Icons.Outlined.CheckCircle
      PermissionState.DENIED -> Color(0xFFEF4444) to Icons.Outlined.ErrorOutline
      PermissionState.REQUIRES_SETTINGS,
      PermissionState.RESTRICTED -> WarmAmber to Icons.Outlined.Security
      PermissionState.NOT_REQUESTED,
      PermissionState.NOT_AVAILABLE,
      PermissionState.NOT_APPLICABLE -> LocalMousomiColors.current.mutedText to Icons.Outlined.Security
    }

  Surface(
    shape = CircleShape,
    color = tint.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
      Text(text = state.label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
  }
}

@Composable
fun PermissionExplanationDialog(
  item: PermissionCategoryItem,
  onContinue: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Allow ${item.category.title}?") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.whyNeeded, style = MaterialTheme.typography.bodyMedium)
        Text(item.dataUsageDisclosure, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = {
      Button(onClick = onContinue, modifier = Modifier.testTag("permission_dialog_allow")) {
        Text("Continue")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Not now") }
    },
  )
}
