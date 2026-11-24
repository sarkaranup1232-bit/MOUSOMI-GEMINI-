package com.example.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.OperatingMode
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.mousomiDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "mousomi_settings")

@Singleton
class SettingsDataStore @Inject constructor(
  @ApplicationContext private val context: Context,
  private val cryptoManager: KeystoreCryptoManager,
) {
  constructor(context: Context) : this(context, KeystoreCryptoManager())

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  private object Keys {
    val CONNECTION_MODE = stringPreferencesKey("connection_mode")
    val PROXY_BASE_URL = stringPreferencesKey("proxy_base_url")
    val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    val CLOUDFLARE_ACCOUNT_ID = stringPreferencesKey("cloudflare_account_id")
    val CLOUDFLARE_API_TOKEN = stringPreferencesKey("cloudflare_api_token")
    val STRICT_FREE_IMAGE_ONLY = booleanPreferencesKey("strict_free_image_only")
    val ALLOW_PAID_IMAGE_FALLBACK = booleanPreferencesKey("allow_paid_image_fallback")
    val OPERATING_MODE = stringPreferencesKey("operating_mode")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
    val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
    val SPEECH_RATE = floatPreferencesKey("speech_rate")
    val PITCH = floatPreferencesKey("pitch")
    val SELECTED_MODEL_OPTION = stringPreferencesKey("selected_model_option")
    val HYDROGEN_BACKING_MODEL_ID = stringPreferencesKey("hydrogen_backing_model_id")
    val GROQ_REASONING_MODEL_ID = stringPreferencesKey("groq_reasoning_model_id")
    val GROQ_HELPER_GUARD_MODEL_ID = stringPreferencesKey("groq_helper_guard_model_id")
    val IMAGE_MODEL_ID = stringPreferencesKey("image_model_id")
    val EXTERNAL_SEARCH_URL = stringPreferencesKey("external_search_url")
    val EXTERNAL_SEARCH_API_KEY = stringPreferencesKey("external_search_api_key")
    val ANALYTICS_SANDBOX_ENABLED = booleanPreferencesKey("analytics_sandbox_enabled")
    val COMPOSER_GLOW_INTENSITY = floatPreferencesKey("composer_glow_intensity")
    val VOICE_GENDER = stringPreferencesKey("voice_gender")
    val ACTIVATION_SOUND_ENABLED = booleanPreferencesKey("activation_sound_enabled")
    val PREFERRED_VOICE_NAME = stringPreferencesKey("preferred_voice_name")
    val VOICE_VOLUME = floatPreferencesKey("voice_volume")
    val AUTO_LANGUAGE_DETECTION = booleanPreferencesKey("auto_language_detection")
    val EDGE_LIGHT_JSON = stringPreferencesKey("edge_light_json")
    val FLOATING_ORB_JSON = stringPreferencesKey("floating_orb_json")
    val SCREEN_INTEL_JSON = stringPreferencesKey("screen_intel_json")
    val AGENT_BUDGET_JSON = stringPreferencesKey("agent_budget_json")
    val NOTIFICATION_INTEL_JSON = stringPreferencesKey("notification_intel_json")
    val PRIVACY_PERF_JSON = stringPreferencesKey("privacy_perf_json")
  }

  val settingsFlow: Flow<AppSettings> =
    context.mousomiDataStore.data.map { prefs -> mapToAppSettings(prefs) }

  suspend fun getSnapshot(): AppSettings = mapToAppSettings(context.mousomiDataStore.data.first())

  suspend fun save(settings: AppSettings) {
    context.mousomiDataStore.edit { prefs ->
      prefs[Keys.CONNECTION_MODE] = settings.connectionMode.name
      prefs[Keys.PROXY_BASE_URL] = settings.proxyBaseUrl.trim()
      prefs[Keys.GEMINI_API_KEY] = cryptoManager.encryptIfSupported(settings.geminiApiKey.trim())
      prefs[Keys.GROQ_API_KEY] = cryptoManager.encryptIfSupported(settings.groqApiKey.trim())
      prefs[Keys.CLOUDFLARE_ACCOUNT_ID] = cryptoManager.encryptIfSupported(settings.cloudflareAccountId.trim())
      prefs[Keys.CLOUDFLARE_API_TOKEN] = cryptoManager.encryptIfSupported(settings.cloudflareApiToken.trim())
      prefs[Keys.STRICT_FREE_IMAGE_ONLY] = settings.strictFreeImageOnly
      prefs[Keys.ALLOW_PAID_IMAGE_FALLBACK] = settings.allowPaidImageFallbackApproved
      prefs[Keys.OPERATING_MODE] = settings.operatingModeName
      prefs[Keys.THEME_MODE] = settings.themeModeName
      prefs[Keys.PREFERRED_LANGUAGE] = settings.voicePreferences.preferredLanguage.name
      prefs[Keys.AUTO_SPEAK] = settings.voicePreferences.autoSpeakResponses
      prefs[Keys.SPEECH_RATE] = settings.voicePreferences.speechRate
      prefs[Keys.PITCH] = settings.voicePreferences.pitch
      prefs[Keys.SELECTED_MODEL_OPTION] = settings.selectedModelOptionName
      prefs[Keys.HYDROGEN_BACKING_MODEL_ID] = settings.hydrogenBackingModelId.trim()
      prefs[Keys.GROQ_REASONING_MODEL_ID] = settings.groqReasoningModelId.trim()
      prefs[Keys.GROQ_HELPER_GUARD_MODEL_ID] = settings.groqHelperGuardModelId.trim()
      prefs[Keys.IMAGE_MODEL_ID] = settings.imageModelId.trim()
      prefs[Keys.EXTERNAL_SEARCH_URL] = settings.externalSearchUrl.trim()
      prefs[Keys.EXTERNAL_SEARCH_API_KEY] =
        cryptoManager.encryptIfSupported(settings.externalSearchApiKey.trim())
      prefs[Keys.ANALYTICS_SANDBOX_ENABLED] = settings.analyticsSandboxEnabled
      prefs[Keys.COMPOSER_GLOW_INTENSITY] = settings.composerGlowIntensity
      prefs[Keys.VOICE_GENDER] = settings.voicePreferences.voiceGender.name
      prefs[Keys.ACTIVATION_SOUND_ENABLED] = settings.voicePreferences.activationSoundEnabled
      prefs[Keys.PREFERRED_VOICE_NAME] = settings.voicePreferences.preferredVoiceName.trim()
      prefs[Keys.VOICE_VOLUME] = settings.voicePreferences.volume.coerceIn(0.1f, 1.0f)
      prefs[Keys.AUTO_LANGUAGE_DETECTION] = settings.voicePreferences.autoLanguageDetection
      prefs[Keys.EDGE_LIGHT_JSON] = json.encodeToString(settings.edgeLightConfig)
      prefs[Keys.FLOATING_ORB_JSON] = json.encodeToString(settings.floatingOrbConfig)
      prefs[Keys.SCREEN_INTEL_JSON] = json.encodeToString(settings.screenIntelligenceConfig)
      prefs[Keys.AGENT_BUDGET_JSON] = json.encodeToString(settings.agentBudgetConfig)
      prefs[Keys.NOTIFICATION_INTEL_JSON] = json.encodeToString(settings.notificationConfig)
      prefs[Keys.PRIVACY_PERF_JSON] = json.encodeToString(settings.privacyPerformanceConfig)
    }
  }

  private fun mapToAppSettings(prefs: Preferences): AppSettings {
    val mode =
      prefs[Keys.CONNECTION_MODE]
        ?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() }
        ?: ConnectionMode.DIRECT_DEV_KEYS
    val language =
      prefs[Keys.PREFERRED_LANGUAGE]
        ?.let { runCatching { ResponseLanguage.valueOf(it) }.getOrNull() }
        ?: ResponseLanguage.AUTO

    val voiceGender =
      prefs[Keys.VOICE_GENDER]
        ?.let { runCatching { VoiceGender.valueOf(it) }.getOrNull() }
        ?: VoiceGender.FEMALE

    return AppSettings(
      connectionMode = mode,
      proxyBaseUrl = prefs[Keys.PROXY_BASE_URL].orEmpty(),
      geminiApiKey = cryptoManager.decryptIfEncrypted(prefs[Keys.GEMINI_API_KEY].orEmpty()),
      groqApiKey = cryptoManager.decryptIfEncrypted(prefs[Keys.GROQ_API_KEY].orEmpty()),
      cloudflareAccountId = cryptoManager.decryptIfEncrypted(prefs[Keys.CLOUDFLARE_ACCOUNT_ID].orEmpty()),
      cloudflareApiToken = cryptoManager.decryptIfEncrypted(prefs[Keys.CLOUDFLARE_API_TOKEN].orEmpty()),
      strictFreeImageOnly = prefs[Keys.STRICT_FREE_IMAGE_ONLY] ?: true,
      allowPaidImageFallbackApproved = prefs[Keys.ALLOW_PAID_IMAGE_FALLBACK] ?: false,
      operatingModeName = prefs[Keys.OPERATING_MODE] ?: OperatingMode.NORMAL_CHAT.name,
      themeModeName = prefs[Keys.THEME_MODE] ?: "DARK",
      voicePreferences =
        VoicePreferences(
          preferredLanguage = language,
          autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: false,
          speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
          pitch = prefs[Keys.PITCH] ?: 1.0f,
          voiceGender = voiceGender,
          activationSoundEnabled = prefs[Keys.ACTIVATION_SOUND_ENABLED] ?: true,
          preferredVoiceName = prefs[Keys.PREFERRED_VOICE_NAME].orEmpty(),
          volume = (prefs[Keys.VOICE_VOLUME] ?: 1.0f).coerceIn(0.1f, 1.0f),
          autoLanguageDetection = prefs[Keys.AUTO_LANGUAGE_DETECTION] ?: true,
        ),
      selectedModelOptionName = prefs[Keys.SELECTED_MODEL_OPTION] ?: "MOUSOMI_AUTO",
      hydrogenBackingModelId =
        prefs[Keys.HYDROGEN_BACKING_MODEL_ID]?.ifBlank { VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID }
          ?: VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
      groqReasoningModelId =
        prefs[Keys.GROQ_REASONING_MODEL_ID]?.ifBlank { VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID }
          ?: VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
      groqHelperGuardModelId =
        prefs[Keys.GROQ_HELPER_GUARD_MODEL_ID]?.ifBlank { VerifiedModelCatalog.DEFAULT_GROQ_HELPER_MODEL_ID }
          ?: VerifiedModelCatalog.DEFAULT_GROQ_HELPER_MODEL_ID,
      imageModelId =
        prefs[Keys.IMAGE_MODEL_ID]?.ifBlank { VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID }
          ?: VerifiedModelCatalog.DEFAULT_IMAGE_MODEL_ID,
      externalSearchUrl = prefs[Keys.EXTERNAL_SEARCH_URL].orEmpty(),
      externalSearchApiKey =
        cryptoManager.decryptIfEncrypted(prefs[Keys.EXTERNAL_SEARCH_API_KEY].orEmpty()),
      analyticsSandboxEnabled = prefs[Keys.ANALYTICS_SANDBOX_ENABLED] ?: false,
      composerGlowIntensity = prefs[Keys.COMPOSER_GLOW_INTENSITY] ?: 0.85f,
      edgeLightConfig =
        decodeOrDefault(prefs[Keys.EDGE_LIGHT_JSON], EdgeLightConfig()),
      floatingOrbConfig =
        decodeOrDefault(prefs[Keys.FLOATING_ORB_JSON], FloatingOrbConfig()),
      screenIntelligenceConfig =
        decodeOrDefault(prefs[Keys.SCREEN_INTEL_JSON], ScreenIntelligenceConfig()),
      agentBudgetConfig =
        decodeOrDefault(prefs[Keys.AGENT_BUDGET_JSON], AgentBudgetConfig()),
      notificationConfig =
        decodeOrDefault(prefs[Keys.NOTIFICATION_INTEL_JSON], NotificationIntelligenceConfig()),
      privacyPerformanceConfig =
        decodeOrDefault(prefs[Keys.PRIVACY_PERF_JSON], PrivacyAndPerformanceConfig()),
    )
  }

  private inline fun <reified T> decodeOrDefault(raw: String?, defaultVal: T): T {
    if (raw.isNullOrBlank()) return defaultVal
    return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(defaultVal)
  }
}
