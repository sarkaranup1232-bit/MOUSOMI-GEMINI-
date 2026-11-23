package com.example.core.edge

import com.example.core.assistant.AssistantSessionPhase
import com.example.core.assistant.EdgeActivationSignal
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightIntensity
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.ScreenShareStatus

data class EdgeLightRenderParams(
  val isVisible: Boolean,
  val visualState: EdgeLightVisualState,
  val effectivePreset: EdgeLightPreset,
  val brightness: Float,
  val speedMultiplier: Float,
  val thicknessDp: Float,
  val cornerRadiusDp: Float,
  val colorArgs: List<Long>,
  val glowLayerCount: Int,
  val voiceReactiveBoost: Float,
  val breathingOnly: Boolean,
  val glowScale: Float = 1.0f,
  val activationSignal: EdgeActivationSignal = EdgeActivationSignal(),
)

object EdgeLightStateMapper {

  fun isWithinSchedule(
    config: EdgeLightConfig,
    currentHour: Int,
  ): Boolean {
    if (!config.scheduleEnabled) return true
    val start = config.scheduleStartHour.coerceIn(0, 23)
    val end = config.scheduleEndHour.coerceIn(0, 23)
    val hour = currentHour.coerceIn(0, 23)
    return if (start <= end) {
      hour in start..end
    } else {
      hour >= start || hour <= end
    }
  }

  fun resolve(
    config: EdgeLightConfig,
    responseState: AiResponseState,
    voiceState: VoiceState,
    voiceRmsNormalized: Float,
    screenShareStatus: ScreenShareStatus,
    notificationAlertActive: Boolean,
    isSystemPowerSave: Boolean,
    currentHour: Int,
    forcePreview: Boolean = false,
    assistantSessionPhase: AssistantSessionPhase = AssistantSessionPhase.INACTIVE,
    edgeActivationSignal: EdgeActivationSignal = EdgeActivationSignal(),
    isOverlayHost: Boolean = false,
  ): EdgeLightRenderParams {
    val visualState =
      when {
        assistantSessionPhase == AssistantSessionPhase.ERROR || responseState is AiResponseState.Error ->
          EdgeLightVisualState.ERROR
        assistantSessionPhase == AssistantSessionPhase.USER_SPEAKING ||
          assistantSessionPhase == AssistantSessionPhase.LISTENING ||
          voiceState is VoiceState.Listening -> EdgeLightVisualState.LISTENING
        assistantSessionPhase == AssistantSessionPhase.AI_RESPONDING ||
          voiceState is VoiceState.Speaking -> EdgeLightVisualState.SPEAKING
        responseState is AiResponseState.Searching -> {
          if (responseState.detail.contains("Research", ignoreCase = true)) {
            EdgeLightVisualState.RESEARCHING
          } else {
            EdgeLightVisualState.SEARCHING
          }
        }
        responseState is AiResponseState.Thinking || responseState is AiResponseState.Loading ->
          EdgeLightVisualState.THINKING
        responseState is AiResponseState.Generating ||
          responseState is AiResponseState.Streaming ||
          assistantSessionPhase == AssistantSessionPhase.DRAWING ||
          assistantSessionPhase == AssistantSessionPhase.SCREEN_SHARING ||
          screenShareStatus == ScreenShareStatus.ACTIVE ||
          screenShareStatus == ScreenShareStatus.ANALYZING -> EdgeLightVisualState.GENERATING
        responseState is AiResponseState.Completed -> EdgeLightVisualState.COMPLETED
        else -> EdgeLightVisualState.IDLE
      }

    val scheduled = isWithinSchedule(config, currentHour)
    val sessionActiveForGlow =
      assistantSessionPhase != AssistantSessionPhase.INACTIVE &&
        assistantSessionPhase != AssistantSessionPhase.PAUSED &&
        assistantSessionPhase != AssistantSessionPhase.STOPPING

    val shouldShow =
      when {
        forcePreview -> config.enabled || config.assistantEdgeGlowEnabled
        notificationAlertActive -> true
        isOverlayHost -> {
          // System overlay never stays permanently stuck bright when assistant is idle/inactive/stopped
          config.assistantEdgeGlowEnabled && (config.enabled || sessionActiveForGlow) && sessionActiveForGlow
        }
        else -> config.enabled && scheduled
      }

    val effectivePreset =
      when {
        forcePreview -> config.preset
        notificationAlertActive -> EdgeLightPreset.NOTIFICATION_ALERT
        visualState == EdgeLightVisualState.LISTENING -> EdgeLightPreset.VOICE_LISTENING
        visualState == EdgeLightVisualState.SEARCHING -> EdgeLightPreset.LIVE_SEARCH
        visualState == EdgeLightVisualState.RESEARCHING -> EdgeLightPreset.DEEP_RESEARCH
        visualState == EdgeLightVisualState.THINKING -> EdgeLightPreset.AI_THINKING
        visualState == EdgeLightVisualState.GENERATING ||
          visualState == EdgeLightVisualState.SPEAKING -> EdgeLightPreset.AI_GENERATING
        else -> config.preset
      }

    val colors = colorsForPreset(effectivePreset, visualState, config)
    val lowPower = config.batterySaverMode || isSystemPowerSave
    val intensity = config.intensity
    val rawBrightness = (config.brightness.coerceIn(0.20f, 1.0f) * intensity.brightnessMultiplier).coerceIn(0.20f, 1.0f)
    val isIdleAmbient =
      visualState == EdgeLightVisualState.IDLE &&
        !forcePreview &&
        !notificationAlertActive &&
        !sessionActiveForGlow

    val effectiveBrightness =
      when {
        !shouldShow -> 0f
        lowPower -> (rawBrightness * 0.45f).coerceIn(0.22f, 0.45f)
        isIdleAmbient -> (rawBrightness * 0.78f).coerceIn(0.42f, 0.86f)
        else -> rawBrightness.coerceIn(0.62f, 1.0f)
      }

    val effectiveSpeed =
      when {
        config.reducedMotion -> 0f
        lowPower -> (config.animationSpeed * 0.30f).coerceIn(0.15f, 0.48f)
        visualState == EdgeLightVisualState.LISTENING || visualState == EdgeLightVisualState.SPEAKING ->
          (config.animationSpeed * (1f + voiceRmsNormalized * 0.5f)).coerceIn(0.4f, 2.4f)
        isIdleAmbient -> (config.animationSpeed * 0.85f).coerceIn(0.3f, 1.8f)
        else -> config.animationSpeed.coerceIn(0.3f, 2.5f)
      }

    val baseThickness = config.borderThicknessDp * intensity.thicknessMultiplier
    val effectiveThickness =
      when {
        lowPower -> (baseThickness * 0.78f).coerceIn(3f, 9f)
        visualState == EdgeLightVisualState.LISTENING || visualState == EdgeLightVisualState.SPEAKING ->
          (baseThickness + voiceRmsNormalized * 2.8f).coerceIn(3.5f, 15f)
        isIdleAmbient -> (baseThickness * 0.92f).coerceIn(3.2f, 10f)
        else -> baseThickness.coerceIn(3.5f, 14f)
      }

    val glowLayers =
      when {
        lowPower || config.reducedMotion -> 1
        intensity == EdgeLightIntensity.SUBTLE && isIdleAmbient -> 2
        else -> 3
      }

    return EdgeLightRenderParams(
      isVisible = shouldShow,
      visualState = visualState,
      effectivePreset = effectivePreset,
      brightness = effectiveBrightness,
      speedMultiplier = effectiveSpeed,
      thicknessDp = effectiveThickness,
      cornerRadiusDp = config.cornerRadiusDp.coerceIn(0f, 52f),
      colorArgs = colors,
      glowLayerCount = glowLayers,
      voiceReactiveBoost = if (config.reducedMotion) 0f else voiceRmsNormalized.coerceIn(0f, 1f),
      breathingOnly =
        effectivePreset == EdgeLightPreset.BLUE_PURPLE_BREATHING ||
          effectivePreset == EdgeLightPreset.NOTIFICATION_ALERT ||
          config.reducedMotion,
      glowScale =
        when {
          lowPower -> 0.72f
          else -> intensity.glowMultiplier
        },
      activationSignal = edgeActivationSignal,
    )
  }

  fun colorsForPreset(
    preset: EdgeLightPreset,
    visualState: EdgeLightVisualState,
    config: EdgeLightConfig,
  ): List<Long> {
    if (visualState == EdgeLightVisualState.ERROR) {
      return listOf(0xFFEF4444L, 0xFFF97316L, 0xFFDC2626L, 0xFFEF4444L)
    }
    if (visualState == EdgeLightVisualState.COMPLETED) {
      return listOf(0xFF10B981L, 0xFF22D3EEL, 0xFF3B82F6L, 0xFF10B981L)
    }
    return when (preset) {
      EdgeLightPreset.GEMINI_FLOW ->
        listOf(
          0xFF06B6D4L, // Crisp Neon Cyan
          0xFF22D3EEL, // Luminous Bright Cyan
          0xFF3B82F6L, // Electric Blue
          0xFF6366F1L, // Crisp Indigo
          0xFF8B5CF6L, // Vibrant Violet
          0xFFA855F7L, // Deep Neon Purple
          0xFF3B82F6L, // Blue Return Bridge
          0xFF06B6D4L, // Seamless Closure
        )
      EdgeLightPreset.RAINBOW_SWEEP ->
        listOf(0xFFEF4444L, 0xFFF59E0BL, 0xFF10B981L, 0xFF22D3EEL, 0xFF3B82F6L, 0xFFA855F7L, 0xFFEF4444L)
      EdgeLightPreset.BLUE_PURPLE_BREATHING ->
        listOf(0xFF22D3EEL, 0xFF3B82F6L, 0xFF8B5CF6L, 0xFFA855F7L, 0xFF22D3EEL)
      EdgeLightPreset.ROTATING_GRADIENT ->
        listOf(0xFF22D3EEL, 0xFF3B82F6L, 0xFF6366F1L, 0xFFA855F7L, 0xFFEC4899L, 0xFF22D3EEL)
      EdgeLightPreset.AI_THINKING ->
        listOf(0xFF2563EBL, 0xFF6366F1L, 0xFF8B5CF6L, 0xFFA855F7L, 0xFF2563EBL)
      EdgeLightPreset.VOICE_LISTENING ->
        listOf(0xFF22D3EEL, 0xFF06B6D4L, 0xFF3B82F6L, 0xFF8B5CF6L, 0xFFA855F7L, 0xFF22D3EEL)
      EdgeLightPreset.LIVE_SEARCH ->
        listOf(0xFF0EA5E9L, 0xFF38BDF8L, 0xFF6366F1L, 0xFF8B5CF6L, 0xFF0EA5E9L)
      EdgeLightPreset.DEEP_RESEARCH ->
        listOf(0xFF14B8A6L, 0xFF22D3EEL, 0xFF3B82F6L, 0xFFA855F7L, 0xFF14B8A6L)
      EdgeLightPreset.AI_GENERATING ->
        listOf(
          0xFF22D3EEL,
          0xFF3B82F6L,
          0xFF6366F1L,
          0xFF8B5CF6L,
          0xFFA855F7L,
          0xFFEC4899L,
          0xFF38BDF8L,
          0xFF22D3EEL,
        )
      EdgeLightPreset.NOTIFICATION_ALERT ->
        listOf(0xFF22D3EEL, 0xFF3B82F6L, 0xFFA855F7L, 0xFF22D3EEL)
      EdgeLightPreset.CUSTOM_GRADIENT ->
        listOf(
          config.primaryColorArgb,
          config.secondaryColorArgb,
          config.tertiaryColorArgb,
          config.quaternaryColorArgb,
          config.primaryColorArgb,
        )
    }
  }
}
