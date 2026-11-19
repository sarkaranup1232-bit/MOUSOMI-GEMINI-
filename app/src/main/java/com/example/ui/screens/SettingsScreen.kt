package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ActivationHealthStatus
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DetectedProviderKind
import com.example.domain.model.OneTapActivationResult
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarmAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  geminiApiKey: String,
  groqApiKey: String,
  themeMode: AppThemeMode,
  connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  proxyBaseUrl: String = "",
  hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
  imageModelId: String = VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
  externalSearchUrl: String = "",
  externalSearchApiKey: String = "",
  analyticsSandboxEnabled: Boolean = false,
  composerGlowIntensity: Float = 0.85f,
  voicePreferences: VoicePreferences = VoicePreferences(),
  settingsErrorMessage: String? = null,
  onGeminiApiKeyChange: (String) -> Unit,
  onGroqApiKeyChange: (String) -> Unit,
  onThemeModeChange: (AppThemeMode) -> Unit,
  onConnectionModeChange: (ConnectionMode) -> Unit = {},
  onProxyBaseUrlChange: (String) -> Unit = {},
  onHydrogenBackingModelIdChange: (String) -> Unit = {},
  onGroqReasoningModelIdChange: (String) -> Unit = {},
  onImageModelIdChange: (String) -> Unit = {},
  onExternalSearchUrlChange: (String) -> Unit = {},
  onExternalSearchApiKeyChange: (String) -> Unit = {},
  onAnalyticsSandboxEnabledChange: (Boolean) -> Unit = {},
  onComposerGlowIntensityChange: (Float) -> Unit = {},
  onClearTempMediaCache: () -> Unit = {},
  onPreferredLanguageChange: (ResponseLanguage) -> Unit = {},
  onVoiceGenderChange: (VoiceGender) -> Unit = {},
  onActivationSoundChange: (Boolean) -> Unit = {},
  onAutoSpeakChange: (Boolean) -> Unit = {},
  onSpeechRateChange: (Float) -> Unit = {},
  onOpenPermissionCenter: () -> Unit = {},
  onSaveSettings: () -> Boolean = { true },
  oneTapApiKeyInput: String = "",
  isActivatingApi: Boolean = false,
  activationResult: OneTapActivationResult = OneTapActivationResult(),
  onOneTapApiKeyChange: (String) -> Unit = {},
  onActivateOneTap: (String) -> Unit = {},
  onVerifyConnection: () -> Unit = {},
  onDeactivateCredentials: () -> Unit = {},
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val colors = LocalMousomiColors.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var showOneTapKey by remember { mutableStateOf(false) }
  var showGeminiKey by remember { mutableStateOf(false) }
  var showGroqKey by remember { mutableStateOf(false) }
  var showSearchKey by remember { mutableStateOf(false) }
  var advancedExpanded by remember { mutableStateOf(false) }
  val mergedBottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.windowInsetsPadding(mergedBottomInsets),
      )
    },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_title),
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(mergedBottomInsets)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Text(
          text = stringResource(R.string.settings_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.mutedText,
        )

        // One-Tap API Activation Card
        OneTapApiActivationCard(
          apiKeyInput = oneTapApiKeyInput,
          showKey = showOneTapKey,
          onToggleShowKey = { showOneTapKey = !showOneTapKey },
          isActivating = isActivatingApi,
          activationResult = activationResult,
          hasStoredCredentials =
            geminiApiKey.isNotBlank() ||
              groqApiKey.isNotBlank() ||
              (connectionMode == ConnectionMode.BACKEND_PROXY && proxyBaseUrl.isNotBlank()),
          onApiKeyInputChange = onOneTapApiKeyChange,
          onActivate = { onActivateOneTap(oneTapApiKeyInput) },
          onVerifyConnection = onVerifyConnection,
          onDeactivate = onDeactivateCredentials,
        )

        // Adaptive Theme Mode Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = stringResource(R.string.settings_theme_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            AppThemeMode.entries.forEach { mode ->
              val selected = mode == themeMode
              FilterChip(
                selected = selected,
                onClick = { onThemeModeChange(mode) },
                label = { Text(mode.label) },
                leadingIcon =
                  if (selected) {
                    {
                      Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  } else null,
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.activeChipContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                  ),
                modifier = Modifier.weight(1f).testTag("theme_chip_${mode.name}"),
              )
            }
          }

          Column {
            Text(
              text = "Neon Composer Glow Intensity (${(composerGlowIntensity * 100).toInt()}%)",
              style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
              value = composerGlowIntensity,
              onValueChange = onComposerGlowIntensityChange,
              valueRange = 0.25f..1.0f,
            )
          }
        }

        // Collapsible Advanced Multi-Provider, Model IDs & Proxy Section
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { advancedExpanded = !advancedExpanded }
                  .padding(vertical = 4.dp)
                  .testTag("advanced_settings_toggle"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Advanced Multi-Provider, Models & Proxy",
                  fontFamily = DisplayFontFamily,
                  fontWeight = FontWeight.Medium,
                  fontSize = 17.sp,
                )
                Text(
                  text =
                    if (advancedExpanded) "Tap to collapse detailed configuration"
                    else "Configure secondary keys, custom model IDs, or HTTPS proxy",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Icon(
                imageVector =
                  if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (advancedExpanded) "Collapse advanced settings" else "Expand advanced settings",
                tint = colors.mutedText,
              )
            }

            AnimatedVisibility(visible = advancedExpanded) {
              Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  ConnectionMode.entries.forEach { mode ->
                    val selected = mode == connectionMode
                    FilterChip(
                      selected = selected,
                      onClick = { onConnectionModeChange(mode) },
                      label = { Text(mode.label) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = colors.activeChipContainer,
                          selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                        ),
                      modifier = Modifier.weight(1f),
                    )
                  }
                }

                if (connectionMode == ConnectionMode.BACKEND_PROXY) {
                  OutlinedTextField(
                    value = proxyBaseUrl,
                    onValueChange = onProxyBaseUrlChange,
                    label = { Text("HTTPS Backend Proxy URL") },
                    placeholder = { Text("https://api.your-proxy.com/") },
                    leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().testTag("proxy_url_input"),
                  )
                }

                OutlinedTextField(
                  value = geminiApiKey,
                  onValueChange = onGeminiApiKeyChange,
                  label = { Text(stringResource(R.string.settings_gemini_key_label)) },
                  placeholder = { Text(stringResource(R.string.settings_gemini_key_placeholder)) },
                  leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                  trailingIcon = {
                    IconButton(
                      onClick = { showGeminiKey = !showGeminiKey },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector =
                          if (showGeminiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_visibility),
                      )
                    }
                  },
                  visualTransformation =
                    if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = MaterialTheme.colorScheme.primary,
                      unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                )

                OutlinedTextField(
                  value = groqApiKey,
                  onValueChange = onGroqApiKeyChange,
                  label = { Text(stringResource(R.string.settings_groq_key_label)) },
                  placeholder = { Text(stringResource(R.string.settings_groq_key_placeholder)) },
                  leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                  trailingIcon = {
                    IconButton(
                      onClick = { showGroqKey = !showGroqKey },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector =
                          if (showGroqKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(R.string.cd_toggle_visibility),
                      )
                    }
                  },
                  visualTransformation =
                    if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  colors =
                    OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = MaterialTheme.colorScheme.primary,
                      unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                  modifier = Modifier.fillMaxWidth().testTag("groq_api_key_input"),
                )

                OutlinedTextField(
                  value = hydrogenBackingModelId,
                  onValueChange = onHydrogenBackingModelIdChange,
                  label = { Text("Gemini 4 Hydrogen Backing Model ID") },
                  placeholder = { Text(VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID) },
                  leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("hydrogen_model_id_input"),
                )

                OutlinedTextField(
                  value = groqReasoningModelId,
                  onValueChange = onGroqReasoningModelIdChange,
                  label = { Text("Groq Reasoning Model ID") },
                  placeholder = { Text(VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID) },
                  leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("groq_model_id_input"),
                )

                OutlinedTextField(
                  value = imageModelId,
                  onValueChange = onImageModelIdChange,
                  label = { Text("Image Generation & Editing Model ID") },
                  placeholder = { Text(VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID) },
                  leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.fillMaxWidth().testTag("image_model_id_input"),
                )

                OutlinedTextField(
                  value = externalSearchUrl,
                  onValueChange = onExternalSearchUrlChange,
                  label = { Text("External Search API HTTPS Endpoint (Optional)") },
                  placeholder = { Text("https://google.serper.dev/search") },
                  leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                  modifier = Modifier.fillMaxWidth().testTag("external_search_url_input"),
                )

                if (externalSearchUrl.isNotBlank()) {
                  OutlinedTextField(
                    value = externalSearchApiKey,
                    onValueChange = onExternalSearchApiKeyChange,
                    label = { Text("External Search API Key") },
                    trailingIcon = {
                      IconButton(
                        onClick = { showSearchKey = !showSearchKey },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                      ) {
                        Icon(
                          imageVector =
                            if (showSearchKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                          contentDescription = stringResource(R.string.cd_toggle_visibility),
                        )
                      }
                    },
                    visualTransformation =
                      if (showSearchKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Backend Python Analytics Sandbox", style = MaterialTheme.typography.bodyLarge)
                    Text(
                      text = "Route heavy dataset tasks to HTTPS /v1/analytics/execute when proxy is active",
                      style = MaterialTheme.typography.labelSmall,
                      color = colors.mutedText,
                    )
                  }
                  Switch(
                    checked = analyticsSandboxEnabled,
                    onCheckedChange = onAnalyticsSandboxEnabledChange,
                  )
                }
              }
            }

            OutlinedButton(
              onClick = {
                onClearTempMediaCache()
                scope.launch {
                  snackbarHostState.showSnackbar("Temporary processed media cache cleared")
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Clear Temporary Media Cache")
            }

            OutlinedButton(
              onClick = onOpenPermissionCenter,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("settings_open_permissions_button"),
            ) {
              Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Open Permission & Privacy Center")
            }
          }
        }

        // Multilingual & Voice Preferences
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Language & Voice",
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 18.sp,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ResponseLanguage.entries.forEach { lang ->
                val selected = lang == voicePreferences.preferredLanguage
                FilterChip(
                  selected = selected,
                  onClick = { onPreferredLanguageChange(lang) },
                  label = { Text(lang.label) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = colors.activeChipContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
              }
            }

            Text("Voice Gender", style = MaterialTheme.typography.labelLarge)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              VoiceGender.entries.forEach { gender ->
                FilterChip(
                  selected = gender == voicePreferences.voiceGender,
                  onClick = { onVoiceGenderChange(gender) },
                  label = { Text(gender.label) },
                  modifier = Modifier.weight(1f).testTag("settings_voice_gender_${gender.name}"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Activation chime sound",
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = "Play soft confirmation chime when starting Live Voice",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = voicePreferences.activationSoundEnabled,
                onCheckedChange = onActivationSoundChange,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Auto-speak responses",
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = "Read completed AI replies aloud via Android TTS",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.mutedText,
                )
              }
              Switch(
                checked = voicePreferences.autoSpeakResponses,
                onCheckedChange = onAutoSpeakChange,
              )
            }

            Column {
              Text(
                text = "Speech rate: ${"%.1fx".format(voicePreferences.speechRate)}",
                style = MaterialTheme.typography.bodyMedium,
              )
              Slider(
                value = voicePreferences.speechRate,
                onValueChange = onSpeechRateChange,
                valueRange = 0.7f..1.5f,
              )
            }
          }
        }

        if (!settingsErrorMessage.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = settingsErrorMessage,
              color = Color(0xFFFCA5A5),
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = colors.mutedText,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text =
                "Security: API keys are never embedded in the APK or logged. Use HTTPS Backend Proxy mode in production so keys remain exclusively on your server.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.mutedText,
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            if (onSaveSettings()) {
              scope.launch {
                snackbarHostState.showSnackbar("Settings saved securely to device DataStore")
              }
            }
          },
          shape = RoundedCornerShape(28.dp),
          colors = ButtonDefaults.buttonColors(containerColor = colors.liveButtonBlue),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_settings_button"),
        ) {
          Text(
            text = stringResource(R.string.settings_save_button),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OneTapApiActivationCard(
  apiKeyInput: String,
  showKey: Boolean,
  onToggleShowKey: () -> Unit,
  isActivating: Boolean,
  activationResult: OneTapActivationResult,
  hasStoredCredentials: Boolean,
  onApiKeyInputChange: (String) -> Unit,
  onActivate: () -> Unit,
  onVerifyConnection: () -> Unit,
  onDeactivate: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val statusColor =
    when (activationResult.status) {
      ActivationHealthStatus.ACTIVE -> NeonGreen
      ActivationHealthStatus.PARTIALLY_ACTIVE, ActivationHealthStatus.RATE_LIMITED -> WarmAmber
      ActivationHealthStatus.INVALID_KEY -> Color(0xFFEF4444)
      ActivationHealthStatus.PROVIDER_UNAVAILABLE -> Color(0xFFF97316)
      ActivationHealthStatus.VALIDATING -> NeonCyan
      ActivationHealthStatus.UNCONFIGURED -> colors.mutedText
    }

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.48f)),
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth().testTag("one_tap_activation_card"),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "One-Tap API Activation",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
          )
          if (activationResult.detectedProvider != DetectedProviderKind.UNKNOWN ||
            activationResult.maskedKeyHint.isNotBlank()
          ) {
            val subtitle =
              buildString {
                append(activationResult.detectedProvider.label)
                if (activationResult.maskedKeyHint.isNotBlank()) {
                  append(" • ${activationResult.maskedKeyHint}")
                }
              }
            Text(
              text = subtitle,
              style = MaterialTheme.typography.labelMedium,
              color = NeonCyan,
            )
          }
        }
        Surface(
          shape = CircleShape,
          color = statusColor.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
          modifier = Modifier.testTag("one_tap_status_badge"),
        ) {
          Text(
            text = activationResult.status.label,
            color = statusColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Text(
        text = activationResult.statusMessage,
        style = MaterialTheme.typography.bodySmall,
        color = colors.mutedText,
        modifier = Modifier.testTag("one_tap_status_message"),
      )

      OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onApiKeyInputChange,
        label = { Text("API Key (Gemini or Groq)") },
        placeholder = { Text("Paste Gemini (AIza...) or Groq (gsk_...) key") },
        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        trailingIcon = {
          IconButton(
            onClick = onToggleShowKey,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector =
                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
              contentDescription = stringResource(R.string.cd_toggle_visibility),
            )
          }
        },
        visualTransformation =
          if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonCyan,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
        modifier = Modifier.fillMaxWidth().testTag("one_tap_api_key_input"),
      )

      Button(
        onClick = onActivate,
        enabled = !isActivating,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("one_tap_activate_button"),
      ) {
        if (isActivating) {
          CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Verifying…", color = Color.White, fontWeight = FontWeight.Medium)
        } else {
          Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Activate", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
      }

      if (activationResult.capabilities.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().testTag("one_tap_capabilities_row"),
        ) {
          activationResult.capabilities.forEach { cap ->
            val capTint = if (cap.available) NeonGreen else colors.mutedText
            Surface(
              shape = CircleShape,
              color = capTint.copy(alpha = if (cap.available) 0.14f else 0.08f),
              border = BorderStroke(1.dp, capTint.copy(alpha = 0.4f)),
              modifier = Modifier.testTag("capability_badge_${cap.id}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                if (cap.available) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = capTint,
                    modifier = Modifier.size(13.dp),
                  )
                }
                Text(
                  text = cap.label,
                  color = capTint,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Medium,
                )
              }
            }
          }
        }
      }

      if (hasStoredCredentials || activationResult.status != ActivationHealthStatus.UNCONFIGURED) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onVerifyConnection,
            enabled = !isActivating,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).testTag("verify_connection_button"),
          ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Verify Connection", maxLines = 1)
          }
          TextButton(
            onClick = onDeactivate,
            enabled = !isActivating,
            modifier = Modifier.testTag("deactivate_api_button"),
          ) {
            Text("Deactivate", color = Color(0xFFFCA5A5))
          }
        }
      }
    }
  }
}
