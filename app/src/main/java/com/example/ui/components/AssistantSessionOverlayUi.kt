package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Rect as AndroidRect
import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.assistant.AssistantSurfaceState
import com.example.core.assistant.AssistantVisualState
import com.example.core.assistant.EdgeActivationEdge
import com.example.core.assistant.EdgeActivationSignal
import com.example.core.device.PendingPhoneAction
import com.example.core.device.PendingSocialAction
import com.example.core.device.ResolvedContact
import com.example.core.edge.EdgeLightRenderParams
import com.example.core.overlay.FloatingAttachmentItem
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropHandle
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.ScreenRecordingManager
import com.example.core.screen.ScreenRecordingPhase
import com.example.core.screen.ScreenRecordingUiState
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionToolMode
import com.example.core.voice.OrbReactiveFrame
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.WarmAmber
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Unified System-Level Assistant Overlay UI strictly aligned with Reference Image 1 & Reference Image 2:
 *
 * State 1 ([AssistantVisualState.STANDARD_ASSISTANT]):
 * - Pitch-black backdrop
 * - Full-screen rounded neon Border Light (cyan -> royal blue -> violet -> magenta -> soft gold)
 * - Left-aligned dark frosted-glass "Share screen with Live" capsule above bottom bar
 * - Bottom glassmorphic Chat Bar: '+' button, "Ask Mousomi..." input, Mic icon, Rainbow circular button
 *
 * State 2 ([AssistantVisualState.LIVE_ASSISTANT]):
 * - Preserves 100% of State 1 unchanged in fixed positions
 * - Overlays the small floating Draggable Iridescent Smoke Orb + Organic Audio Waveform bottom-right
 *   above the Rainbow button, with drag-to-'X' dismissal cleanly returning to State 1.
 */
@Composable
fun AssistantSessionOverlayUi(
  visualState: AssistantVisualState,
  edgeLightParams: EdgeLightRenderParams,
  reactiveFrame: OrbReactiveFrame,
  inputText: String,
  onInputChanged: (String) -> Unit,
  onSendQuery: (String) -> Unit,
  onMicClick: () -> Unit,
  onLiveButtonClick: () -> Unit,
  onDismissLiveToStandard: () -> Unit,
  onShareScreenWithLive: () -> Unit,
  onStopGeneration: () -> Unit = {},
  onOpenFullApp: () -> Unit = {},
  onOpenScreenSelection: () -> Unit = {},
  onOpenDrawingStudio: () -> Unit = {},
  onOpenPhotoPicker: () -> Unit = {},
  onCloseSession: () -> Unit = {},
  isGenerating: Boolean = false,
  isVoiceListening: Boolean = false,
  voiceRmsNormalized: Float = 0f,
  isDictationSessionActive: Boolean = false,
  isDictationPaused: Boolean = false,
  voiceErrorText: String? = null,
  onStartDictation: () -> Unit = onMicClick,
  onPauseDictation: () -> Unit = {},
  onStopDictation: () -> Unit = {},
  onRetryDictation: () -> Unit = onMicClick,
  isLivePaused: Boolean = false,
  isLiveMuted: Boolean = false,
  isScreenShareActive: Boolean = false,
  userTranscript: String = "",
  assistantResponseText: String = "",
  statusBannerText: String? = null,
  pendingPhoneAction: PendingPhoneAction? = null,
  onConfirmPhoneAction: (PendingPhoneAction) -> Unit = {},
  onDismissPhoneAction: () -> Unit = {},
  onSelectPhoneCandidate: (ResolvedContact) -> Unit = {},
  pendingSocialAction: PendingSocialAction? = null,
  onConfirmSocialAction: (PendingSocialAction) -> Unit = {},
  onDismissSocialAction: () -> Unit = {},
  onSelectSocialCandidate: (ResolvedContact) -> Unit = {},
  pendingAccessibilityAction: AccessibilityActionResult? = null,
  onConfirmAccessibilityAction: (AccessibilityActionResult) -> Unit = {},
  onDismissAccessibilityAction: () -> Unit = {},
  isDrawingMode: Boolean = false,
  drawingToolMode: SelectionToolMode = SelectionToolMode.SMART_LOOP,
  activeDrawingPoints: List<NormPoint> = emptyList(),
  snappedDrawingResult: SelectionGeometryResult? = null,
  confirmedSelectionBox: NormBoundingBox? = null,
  selectionGeometryEngine: SelectionGeometryEngine? = null,
  onToggleDrawingMode: () -> Unit = onOpenScreenSelection,
  onSelectDrawingTool: (SelectionToolMode) -> Unit = {},
  onDrawingStrokeStart: (NormPoint) -> Unit = {},
  onDrawingStrokeMove: (NormPoint) -> Unit = {},
  onDrawingStrokeEnd: () -> Unit = {},
  onDrawingHandleDrag: (CropHandle, Float, Float) -> Unit = { _, _, _ -> },
  onConfirmDrawingSelection: () -> Unit = {},
  onRedrawDrawingSelection: () -> Unit = {},
  onCancelDrawingSelection: () -> Unit = {},
  screenRecordingState: ScreenRecordingUiState = ScreenRecordingUiState(),
  onConfirmScreenRecording: () -> Unit = {},
  onStopScreenRecording: () -> Unit = {},
  onCancelScreenRecording: () -> Unit = {},
  onDismissScreenRecordingCard: () -> Unit = {},
  pendingAttachments: List<FloatingAttachmentItem> = emptyList(),
  onRemoveAttachment: (String) -> Unit = {},
  onPreviewAttachment: (FloatingAttachmentItem) -> Unit = {},
  onReplaceAttachment: () -> Unit = {},
  surfaceState: AssistantSurfaceState =
    if (isDrawingMode) AssistantSurfaceState.DRAWING else AssistantSurfaceState.EXPANDED,
  orbXFraction: Float = 0.86f,
  orbYFraction: Float = 0.70f,
  onOrbPositionChanged: (Float, Float) -> Unit = { _, _ -> },
  onCollapseToOrb: () -> Unit = onCloseSession,
  onExpandFromOrb: () -> Unit = {},
  onOrbTouchableRegionsChanged: (AndroidRect, AndroidRect) -> Unit = { _, _ -> },
  onOrbDragStateChanged: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  if (surfaceState == AssistantSurfaceState.CLOSED) return

  if (surfaceState == AssistantSurfaceState.COLLAPSED_ORB) {
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .background(Color.Transparent)
          .testTag("system_assistant_collapsed_root")
    ) {
      if (visualState == AssistantVisualState.LIVE_ASSISTANT) {
        EdgeLightPerimeterOverlay(params = edgeLightParams)
      }
      CollapsedFloatingOrbStage(
        reactiveFrame = reactiveFrame,
        isLiveVoiceActive = visualState == AssistantVisualState.LIVE_ASSISTANT || isVoiceListening,
        isPaused = isLivePaused || isDictationPaused,
        isMuted = isLiveMuted,
        reducedMotion = false,
        xFraction = orbXFraction,
        yFraction = orbYFraction,
        onPositionChanged = onOrbPositionChanged,
        onExpandAssistant = onExpandFromOrb,
        onCloseAssistant = onCloseSession,
        onTouchableRegionsChanged = onOrbTouchableRegionsChanged,
        onDragStateChanged = onOrbDragStateChanged,
      )
    }
    return
  }

  var isPlusMenuOpen by remember { mutableStateOf(false) }
  var enlargedPreviewItem by remember { mutableStateOf<FloatingAttachmentItem?>(null) }
  val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)
  val resolvedEngine =
    remember(selectionGeometryEngine) {
      selectionGeometryEngine
        ?: SelectionGeometryEngine(
          freehandProcessor = FreehandPathProcessor(),
          circleProcessor = CircleSelectionProcessor(),
          rectangleProcessor = RectangleSelectionProcessor(),
        )
    }
  val effectiveDrawing = isDrawingMode || surfaceState == AssistantSurfaceState.DRAWING
  val isSelectionOverlayBlockingDismiss =
    effectiveDrawing || snappedDrawingResult != null || confirmedSelectionBox != null

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color.Transparent)
        .testTag("system_assistant_root")
  ) {
    // Tapping empty space outside the expanded Assistant UI collapses to the compact Floating Orb
    // (NEVER closes the session or interrupts Live Audio). Strictly disabled during Drawing/Selection.
    if (!isSelectionOverlayBlockingDismiss && surfaceState == AssistantSurfaceState.EXPANDED) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onCollapseToOrb,
            )
            .testTag("assistant_outside_dismiss_scrim")
      )
    }

    // Soft bottom vignette scrim ONLY behind the floating dock when expanded (not obscuring drawing)
    if (!effectiveDrawing) {
      Box(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(260.dp)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f), Color.Black.copy(alpha = 0.62f))
              )
            )
      )
    }

    // Passive neon highlight for an already confirmed selection box (keeps underlying touch separation)
    if (!isDrawingMode && confirmedSelectionBox != null) {
      val paint =
        remember {
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            isDither = true
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
          }
        }
      val shaderMatrix = remember { android.graphics.Matrix() }
      val nativeRect = remember { RectF() }
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .testTag("assistant_confirmed_selection_box")
      ) {
        val w = size.width
        val h = size.height
        if (w > 8f && h > 8f) {
          val rect =
            Rect(
              left = (confirmedSelectionBox.left * w).coerceIn(0f, w - 4f),
              top = (confirmedSelectionBox.top * h).coerceIn(0f, h - 4f),
              right = (confirmedSelectionBox.right * w).coerceIn((confirmedSelectionBox.left * w) + 4f, w),
              bottom = (confirmedSelectionBox.bottom * h).coerceIn((confirmedSelectionBox.top * h) + 4f, h),
            )
          val cornerPx = (minOf(rect.width, rect.height) * 0.1f).coerceIn(14f * density, 26f * density)
          drawRect(color = Color.Black.copy(alpha = 0.34f), size = size)
          drawRoundRect(
            color = Color.Transparent,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            blendMode = BlendMode.Clear,
          )
          drawNeonSelectionFrame(
            rect = rect,
            cornerRadiusPx = cornerPx,
            sweepAngleDeg = 42f,
            pulseAlpha = 0.96f,
            paint = paint,
            shaderMatrix = shaderMatrix,
            nativeRect = nativeRect,
            showSideResizePills = false,
          )
        }
      }
    }

    // Active isolated inline Drawing / Area Selection layer (captures drawing gestures without dismissing or moving assistant)
    if (effectiveDrawing) {
      SelectionHighlightRenderer(
        toolMode = drawingToolMode,
        activeStrokePoints = activeDrawingPoints,
        snappedResult = snappedDrawingResult,
        geometryEngine = resolvedEngine,
        onStrokeStart = onDrawingStrokeStart,
        onStrokeMove = onDrawingStrokeMove,
        onStrokeEnd = onDrawingStrokeEnd,
        onHandleDrag = onDrawingHandleDrag,
        dimBackgroundAlpha = 0.42f,
      )
    }

    // Full-screen rounded neon perimeter Border Light
    EdgeLightPerimeterOverlay(params = edgeLightParams)

    // App-side neon corner gesture-feedback arcs immediately following successful system swipe invocation
    CornerGestureNeonFeedbackArcs(activationSignal = edgeLightParams.activationSignal)

    // Optional clean translucent response / transcript / confirmation card (only appears when content exists)
    val hasCardContent =
      assistantResponseText.isNotBlank() ||
        userTranscript.isNotBlank() ||
        !statusBannerText.isNullOrBlank() ||
        pendingPhoneAction != null ||
        pendingSocialAction != null ||
        pendingAccessibilityAction != null ||
        screenRecordingState.phase != ScreenRecordingPhase.IDLE ||
        isPlusMenuOpen

    if (!hasCardContent && !effectiveDrawing && surfaceState == AssistantSurfaceState.EXPANDED) {
      Surface(
        onClick = onCloseSession,
        shape = CircleShape,
        color = Color(0xFF0E1320).copy(alpha = 0.84f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shadowElevation = 8.dp,
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 14.dp, end = 16.dp)
            .size(38.dp)
            .testTag("assistant_explicit_close_pill"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close assistant",
            tint = Color.White.copy(alpha = 0.90f),
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    AnimatedVisibility(
      visible = hasCardContent && !effectiveDrawing,
      enter = fadeIn() + slideInVertically { -it / 6 },
      exit = fadeOut(),
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(horizontal = 18.dp, vertical = 18.dp)
          .widthIn(max = 600.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0E1320).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                if (visualState == AssistantVisualState.LIVE_ASSISTANT) "Mousomi Live"
                else "Mousomi Assistant",
              color = NeonCyan,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              IconButton(
                onClick = onOpenFullApp,
                modifier = Modifier.size(34.dp).testTag("assistant_open_app_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.OpenInNew,
                  contentDescription = "Open full Mousomi app",
                  tint = Color.White.copy(alpha = 0.82f),
                  modifier = Modifier.size(18.dp),
                )
              }
              IconButton(
                onClick = onCloseSession,
                modifier = Modifier.size(34.dp).testTag("assistant_close_session_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Close assistant",
                  tint = Color.White.copy(alpha = 0.82f),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }

          if (!statusBannerText.isNullOrBlank()) {
            Text(
              text = statusBannerText,
              color = WarmAmber,
              fontSize = 12.5.sp,
            )
          }

          if (userTranscript.isNotBlank()) {
            Text(
              text = "\"$userTranscript\"",
              color = Color.White.copy(alpha = 0.78f),
              fontSize = 13.sp,
            )
          }

          if (assistantResponseText.isNotBlank()) {
            Text(
              text = assistantResponseText,
              color = Color.White.copy(alpha = 0.96f),
              fontSize = 14.5.sp,
              lineHeight = 20.sp,
              modifier = Modifier.heightIn(max = 220.dp),
            )
          }

          if (isPlusMenuOpen) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              AssistantQuickToolChip(
                icon = Icons.Outlined.CenterFocusWeak,
                label = "Select Screen",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenScreenSelection()
                },
                modifier = Modifier.weight(1f),
              )
              AssistantQuickToolChip(
                icon = Icons.Outlined.Brush,
                label = "Draw",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenDrawingStudio()
                },
                modifier = Modifier.weight(1f),
              )
              AssistantQuickToolChip(
                icon = Icons.Outlined.PhotoLibrary,
                label = "Photos",
                onClick = {
                  isPlusMenuOpen = false
                  onOpenPhotoPicker()
                },
                modifier = Modifier.weight(1f),
              )
            }
          }

          if (pendingPhoneAction != null) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Icon(Icons.Outlined.Phone, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                  Text(pendingPhoneAction.type.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(pendingPhoneAction.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                if (pendingPhoneAction.candidateContacts.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    pendingPhoneAction.candidateContacts.take(5).forEach { candidate ->
                      OutlinedButton(
                        onClick = { onSelectPhoneCandidate(candidate) },
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Text(
                          text = "${candidate.displayName} • ${candidate.phoneNumber}",
                          color = Color.White,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          fontSize = 12.sp,
                        )
                      }
                    }
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onConfirmPhoneAction(pendingPhoneAction) },
                    enabled = !pendingPhoneAction.isContactAmbiguous,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  ) { Text("Confirm") }
                  OutlinedButton(onClick = onDismissPhoneAction) { Text("Cancel", color = Color.White) }
                }
              }
            }
          }

          if (pendingSocialAction != null) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "Confirm ${pendingSocialAction.platform.displayName}",
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                )
                Text(pendingSocialAction.summary, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                if (pendingSocialAction.noteOrLimitation.isNotBlank()) {
                  Text(
                    text = pendingSocialAction.noteOrLimitation,
                    color = WarmAmber,
                    fontSize = 11.5.sp,
                  )
                }
                if (pendingSocialAction.candidateContacts.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    pendingSocialAction.candidateContacts.take(5).forEach { candidate ->
                      OutlinedButton(
                        onClick = { onSelectSocialCandidate(candidate) },
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                      ) {
                        Text(
                          text = "${candidate.displayName} • ${candidate.phoneNumber}",
                          color = Color.White,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          fontSize = 12.sp,
                        )
                      }
                    }
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onConfirmSocialAction(pendingSocialAction) },
                    enabled = !pendingSocialAction.isContactAmbiguous,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  ) { Text("Open Composer") }
                  OutlinedButton(onClick = onDismissSocialAction) { Text("Cancel", color = Color.White) }
                }
              }
            }
          }

          if (pendingAccessibilityAction != null) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.65f)),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = pendingAccessibilityAction.statusLabel,
                  color = WarmAmber,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                )
                Text(
                  text = pendingAccessibilityAction.pendingActionSummary.ifBlank { pendingAccessibilityAction.detail },
                  color = Color.White.copy(alpha = 0.9f),
                  fontSize = 12.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onConfirmAccessibilityAction(pendingAccessibilityAction) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                  ) { Text("Proceed") }
                  OutlinedButton(onClick = onDismissAccessibilityAction) { Text("Cancel", color = Color.White) }
                }
              }
            }
          }
          if (screenRecordingState.phase != ScreenRecordingPhase.IDLE) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = Color(0xFF151C2F),
              border =
                BorderStroke(
                  1.dp,
                  if (screenRecordingState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFEF4444)
                  else NeonCyan.copy(alpha = 0.5f),
                ),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text =
                      if (screenRecordingState.phase == ScreenRecordingPhase.RECORDING) {
                        "Recording Screen • ${ScreenRecordingManager.formatElapsed(screenRecordingState.elapsedSeconds)}"
                      } else {
                        screenRecordingState.phase.label
                      },
                    color =
                      if (screenRecordingState.phase == ScreenRecordingPhase.RECORDING) Color(0xFFFCA5A5)
                      else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                  )
                }
                screenRecordingState.statusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                  Text(msg, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  when (screenRecordingState.phase) {
                    ScreenRecordingPhase.AWAITING_CONFIRMATION -> {
                      Button(
                        onClick = onConfirmScreenRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                      ) { Text("Confirm") }
                      OutlinedButton(onClick = onDismissScreenRecordingCard) {
                        Text("Cancel", color = Color.White)
                      }
                    }
                    ScreenRecordingPhase.RECORDING -> {
                      Button(
                        onClick = onStopScreenRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                      ) { Text("Stop & Save") }
                      OutlinedButton(onClick = onCancelScreenRecording) {
                        Text("Cancel", color = Color.White)
                      }
                    }
                    ScreenRecordingPhase.SAVED,
                    ScreenRecordingPhase.CANCELLED,
                    ScreenRecordingPhase.FAILED -> {
                      OutlinedButton(onClick = onDismissScreenRecordingCard) {
                        Text("Dismiss", color = Color.White)
                      }
                    }
                    else -> Unit
                  }
                }
              }
            }
          }
        }
      }
    }

    // Bottom Dock: Preserved Identical Across Both Image 1 (Standard) & Image 2 (Live)
    Column(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .widthIn(max = 620.dp)
          .windowInsetsPadding(bottomInsets)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.Start,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Compact inline Drawing / Selection controls when Drawing Mode or Confirmed Selection is active
      if (isDrawingMode) {
        Surface(
          shape = RoundedCornerShape(22.dp),
          color = Color(0xFF0E1320).copy(alpha = 0.94f),
          border =
            BorderStroke(
              1.2.dp,
              Brush.linearGradient(listOf(NeonCyan, ElectricBlue, NeonViolet, NeonMagenta, Color(0xFFFDE68A))),
            ),
          shadowElevation = 12.dp,
          modifier = Modifier.fillMaxWidth().testTag("assistant_drawing_toolbar"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (snappedDrawingResult == null) "Draw rectangle or freehand outline"
                  else "Selection ready • Adjust or Confirm",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val isFreehand = drawingToolMode != SelectionToolMode.RECTANGLE
                Surface(
                  onClick = { onSelectDrawingTool(SelectionToolMode.SMART_LOOP) },
                  shape = CircleShape,
                  color = if (isFreehand) ElectricBlue.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.08f),
                  border = BorderStroke(1.dp, if (isFreehand) NeonCyan else Color.White.copy(alpha = 0.16f)),
                ) {
                  Text(
                    "Outline",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
                Surface(
                  onClick = { onSelectDrawingTool(SelectionToolMode.RECTANGLE) },
                  shape = CircleShape,
                  color = if (!isFreehand) ElectricBlue.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.08f),
                  border = BorderStroke(1.dp, if (!isFreehand) NeonCyan else Color.White.copy(alpha = 0.16f)),
                ) {
                  Text(
                    "Box",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = onCancelDrawingSelection,
                modifier = Modifier.weight(1f).testTag("assistant_drawing_cancel_button"),
              ) {
                Text("Cancel", color = Color.White, maxLines = 1)
              }
              OutlinedButton(
                onClick = onRedrawDrawingSelection,
                modifier = Modifier.weight(1f).testTag("assistant_drawing_redraw_button"),
              ) {
                Text("Redraw", color = Color.White, maxLines = 1)
              }
              Button(
                onClick = onConfirmDrawingSelection,
                enabled = snappedDrawingResult != null,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.weight(1.1f).testTag("assistant_drawing_confirm_button"),
              ) {
                Text("Confirm", color = Color.White, maxLines = 1)
              }
            }
          }
        }
      } else if (confirmedSelectionBox != null) {
        Surface(
          shape = CircleShape,
          color = Color(0xFF141824).copy(alpha = 0.92f),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.72f)),
          modifier = Modifier.testTag("assistant_confirmed_selection_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Area selected", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(
              onClick = onRedrawDrawingSelection,
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
              Text("Redraw", color = Color.White, fontSize = 12.sp)
            }
            TextButton(
              onClick = onCancelDrawingSelection,
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
              Text("Cancel", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
          }
        }
      }

      // Easily accessible frosted-glass quick pills row: "Share screen with Live" & "Draw / Select Area"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          onClick = onShareScreenWithLive,
          shape = RoundedCornerShape(24.dp),
          color = Color(0xFF141824).copy(alpha = 0.88f),
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (isScreenShareActive) NeonCyan.copy(alpha = 0.75f)
                else Color.White.copy(alpha = 0.16f),
            ),
          shadowElevation = 10.dp,
          modifier = Modifier.testTag("assistant_share_screen_live_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
              contentDescription = null,
              tint = if (isScreenShareActive) NeonCyan else Color.White.copy(alpha = 0.92f),
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (isScreenShareActive) "Sharing screen with Live" else "Share screen with Live",
              color = Color.White.copy(alpha = 0.94f),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Surface(
          onClick = onToggleDrawingMode,
          shape = RoundedCornerShape(24.dp),
          color =
            if (isDrawingMode) ElectricBlue.copy(alpha = 0.30f)
            else Color(0xFF141824).copy(alpha = 0.88f),
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (isDrawingMode || confirmedSelectionBox != null) NeonCyan.copy(alpha = 0.80f)
                else Color.White.copy(alpha = 0.16f),
            ),
          shadowElevation = 10.dp,
          modifier = Modifier.testTag("assistant_draw_select_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Brush,
              contentDescription = "Draw or select screen area",
              tint = if (isDrawingMode || confirmedSelectionBox != null) NeonCyan else Color.White.copy(alpha = 0.92f),
              modifier = Modifier.size(17.dp),
            )
            Text(
              text = if (isDrawingMode) "Drawing..." else "Draw / Select",
              color = Color.White.copy(alpha = 0.94f),
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Inline Chat Bar Voice Waveform + Start/Pause/Stop + Error Retry (compact, non-blocking)
      AnimatedVisibility(
        visible =
          visualState == AssistantVisualState.STANDARD_ASSISTANT &&
            (isDictationSessionActive || !voiceErrorText.isNullOrBlank()),
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        ChatBarVoiceWaveformStrip(
          rmsNormalized = voiceRmsNormalized,
          isListening = isVoiceListening && !isDictationPaused,
          isPaused = isDictationPaused,
          errorMessage = voiceErrorText,
          onStartOrResume = onStartDictation,
          onPause = onPauseDictation,
          onStop = onStopDictation,
          onRetry = onRetryDictation,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      // Cropped selection / picked image thumbnail preview strip attached to Chat Bar
      if (pendingAttachments.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = Color(0xFF101626).copy(alpha = 0.94f),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
          shadowElevation = 10.dp,
          modifier = Modifier.fillMaxWidth().testTag("assistant_chat_bar_attachments"),
        ) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            pendingAttachments.forEach { att ->
              Surface(
                onClick = {
                  enlargedPreviewItem = att
                  onPreviewAttachment(att)
                },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF182238),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.testTag("assistant_attachment_thumb_${att.id}"),
              ) {
                Row(
                  modifier = Modifier.padding(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  val model: Any =
                    att.filePath?.takeIf { it.isNotBlank() }?.let { java.io.File(it) } ?: att.uriString
                  coil.compose.AsyncImage(
                    model = model,
                    contentDescription = att.label,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier =
                      Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0F1A)),
                  )
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = att.label,
                      color = Color.White,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                    )
                    Text(
                      text = "Tap to preview",
                      color = NeonCyan.copy(alpha = 0.9f),
                      fontSize = 10.5.sp,
                    )
                  }
                  if (att.isFromScreenSelection) {
                    IconButton(
                      onClick = onReplaceAttachment,
                      modifier = Modifier.size(32.dp).testTag("assistant_attachment_replace"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Brush,
                        contentDescription = "Replace selected region",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }
                  IconButton(
                    onClick = { onRemoveAttachment(att.uriString) },
                    modifier = Modifier.size(32.dp).testTag("assistant_attachment_remove"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Remove attachment",
                      tint = Color.White.copy(alpha = 0.85f),
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Bottom Glassmorphic Chat Bar: '+' | "Ask Mousomi..." | Mic | Rainbow Live Waveform Button
      Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF121622).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth().testTag("assistant_bottom_chat_bar"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { isPlusMenuOpen = !isPlusMenuOpen },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_plus_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Tools & attachments",
              tint = Color.White.copy(alpha = 0.90f),
              modifier = Modifier.size(23.dp),
            )
          }

          Box(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (inputText.isEmpty()) {
              Text(
                text = "Ask Mousomi...",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = inputText,
              onValueChange = onInputChanged,
              textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
              cursorBrush = SolidColor(NeonCyan),
              maxLines = 3,
              keyboardOptions =
                KeyboardOptions(
                  capitalization = KeyboardCapitalization.Sentences,
                  imeAction = ImeAction.Send,
                ),
              keyboardActions =
                KeyboardActions(
                  onSend = {
                    val trimmed = inputText.trim()
                    if ((trimmed.isNotEmpty() || pendingAttachments.isNotEmpty()) && !isGenerating) {
                      onSendQuery(trimmed)
                    }
                  }
                ),
              modifier = Modifier.fillMaxWidth().testTag("assistant_text_input"),
            )
          }

          IconButton(
            onClick = onMicClick,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_mic_button"),
          ) {
            Icon(
              imageVector = if (isLiveMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
              contentDescription = "Voice dictation",
              tint =
                when {
                  isLiveMuted -> WarmAmber
                  isVoiceListening -> NeonCyan
                  else -> Color.White.copy(alpha = 0.88f)
                },
              modifier = Modifier.size(22.dp),
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          AnimatedContent(
            targetState =
              when {
                isGenerating -> 2
                inputText.isNotBlank() || pendingAttachments.isNotEmpty() -> 1
                else -> 0
              },
            label = "assistant_right_button",
          ) { mode ->
            when (mode) {
              2 ->
                Surface(
                  onClick = onStopGeneration,
                  shape = CircleShape,
                  color = Color(0xFFDC2626),
                  modifier = Modifier.size(44.dp).testTag("assistant_stop_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.Stop,
                      contentDescription = "Stop",
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              1 ->
                Surface(
                  onClick = { onSendQuery(inputText.trim()) },
                  shape = CircleShape,
                  color = ElectricBlue,
                  modifier = Modifier.size(44.dp).testTag("assistant_send_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = "Send",
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
              else ->
                RainbowLiveWaveformDiscButton(
                  onClick = onLiveButtonClick,
                  size = 44.dp,
                  isLiveActive = visualState == AssistantVisualState.LIVE_ASSISTANT,
                  isMicActive = isVoiceListening && !isDictationPaused,
                  voiceRmsNormalized = voiceRmsNormalized,
                  contentDescriptionText =
                    if (visualState == AssistantVisualState.LIVE_ASSISTANT) "Stop Live Voice"
                    else "Start Live Voice",
                  modifier = Modifier.testTag("assistant_live_button"),
                )
            }
          }
        }
      }
    }

    // State 2 Overlay: Small Draggable Iridescent Smoke Orb + Organic Audio Waveform above it
    // Suppressed during Drawing Mode and Collapsed Orb state so duplicate Orbs or drag conflicts never occur.
    if (visualState == AssistantVisualState.LIVE_ASSISTANT &&
      !effectiveDrawing &&
      surfaceState == AssistantSurfaceState.EXPANDED
    ) {
      DraggableLiveOrbStage(
        reactiveFrame = reactiveFrame,
        isPaused = isLivePaused,
        isMuted = isLiveMuted,
        reducedMotion = false,
        onOrbTap = onMicClick,
        onDismissLive = onDismissLiveToStandard,
        persistedXFraction = orbXFraction,
        persistedYFraction = orbYFraction,
        onPositionChanged = onOrbPositionChanged,
        onDragStateChanged = onOrbDragStateChanged,
        modifier = Modifier.windowInsetsPadding(bottomInsets),
      )
    }

    enlargedPreviewItem?.let { previewItem ->
      androidx.compose.material3.AlertDialog(
        onDismissRequest = { enlargedPreviewItem = null },
        title = { Text(previewItem.label) },
        text = {
          val model: Any =
            previewItem.filePath?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
              ?: previewItem.uriString
          coil.compose.AsyncImage(
            model = model,
            contentDescription = previewItem.label,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
          )
        },
        confirmButton = {
          TextButton(onClick = { enlargedPreviewItem = null }) { Text("Done") }
        },
        dismissButton = {
          TextButton(
            onClick = {
              enlargedPreviewItem = null
              onRemoveAttachment(previewItem.uriString)
            }
          ) { Text("Remove") }
        },
      )
    }
  }
}

@Composable
private fun AssistantQuickToolChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = Color(0xFF171E30),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = NeonCyan, modifier = Modifier.size(18.dp))
      Text(text = label, color = Color.White, fontSize = 11.5.sp, maxLines = 1)
    }
  }
}

/**
 * App-side curved neon gesture-feedback indicator rendered inside Mousomi's Assistant Session
 * immediately after successful bottom-corner swipe invocation.
 *
 * Note: Android/Samsung SystemUI owns the pre-invocation white corner handle drawn over other apps;
 * this indicator provides matching native-style rounded neon feedback inside Mousomi's session
 * window without altering the phone-edge Border Light or system navigation bar.
 */
@Composable
private fun CornerGestureNeonFeedbackArcs(
  activationSignal: EdgeActivationSignal,
  modifier: Modifier = Modifier,
) {
  if (activationSignal.token <= 0L || activationSignal.edge == EdgeActivationEdge.NONE) return

  val progress = remember { Animatable(0f) }
  LaunchedEffect(activationSignal.token) {
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 540, easing = FastOutSlowInEasing),
    )
  }

  val p = progress.value
  if (p >= 0.995f) return

  val localView = LocalView.current
  val density = LocalDensity.current.density
  val cornerRadii =
    remember(density, activationSignal.token) {
      val fallback = 34f * density
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val insets = localView.rootWindowInsets
        val bl = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
        val br = insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f } ?: fallback
        bl to br
      } else {
        fallback to fallback
      }
    }

  val paint =
    remember {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
      }
    }
  val blurFilter = remember(density) { BlurMaskFilter((7f * density).coerceAtLeast(4f), BlurMaskFilter.Blur.NORMAL) }
  val fullPath = remember { Path() }
  val segmentPath = remember { Path() }
  val pathMeasure = remember { PathMeasure() }
  val arcRect = remember { RectF() }
  val shaderMatrix = remember { android.graphics.Matrix() }
  var cachedLeftShader by remember { mutableStateOf<LinearGradient?>(null) }
  var cachedRightShader by remember { mutableStateOf<LinearGradient?>(null) }
  var cachedShaderW by remember { mutableStateOf(-1f) }
  var cachedShaderH by remember { mutableStateOf(-1f) }

  // Smooth fade-in then graceful fade-out as the session settles
  val fadeAlpha =
    when {
      p < 0.14f -> (p / 0.14f).coerceIn(0f, 1f)
      p > 0.58f -> (1f - (p - 0.58f) / 0.42f).coerceIn(0f, 1f)
      else -> 1f
    }
  val sweepReveal = (0.28f + (p / 0.55f).coerceIn(0f, 1f) * 0.72f).coerceIn(0.28f, 1f)
  val liftOffsetDp = p * 10f

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .testTag("corner_gesture_neon_indicator")
  ) {
    val w = size.width
    val h = size.height
    if (w < 40f || h < 40f) return@Canvas

    val baseInset = 10f * this.density
    val liftPx = liftOffsetDp * this.density
    val inset = baseInset + liftPx * 0.25f
    val coreWidth = 3.5f * this.density
    val glowWidth = 9.5f * this.density
    val sideRun = 38f * this.density
    val bottomRun = 28f * this.density
    val maxCornerBand = 56f * this.density + sideRun + bottomRun

    if (cachedLeftShader == null || cachedRightShader == null || cachedShaderW != w || cachedShaderH != h) {
      cachedShaderW = w
      cachedShaderH = h
      val colorsLeft =
        intArrayOf(
          0xFF22D3EE.toInt(), // Cyan
          0xFF3B82F6.toInt(), // Electric Blue
          0xFF8B5CF6.toInt(), // Violet
          0xFFEC4899.toInt(), // Magenta
          0xFFFDE68A.toInt(), // Subtle Soft-Gold highlight
        )
      val colorsRight =
        intArrayOf(
          0xFFFDE68A.toInt(), // Subtle Soft-Gold highlight
          0xFFEC4899.toInt(), // Magenta
          0xFF8B5CF6.toInt(), // Violet
          0xFF3B82F6.toInt(), // Electric Blue
          0xFF22D3EE.toInt(), // Cyan
        )
      cachedLeftShader =
        LinearGradient(
          baseInset,
          h - baseInset - maxCornerBand,
          baseInset + maxCornerBand,
          h - baseInset,
          colorsLeft,
          null,
          Shader.TileMode.CLAMP,
        )
      cachedRightShader =
        LinearGradient(
          w - baseInset - maxCornerBand,
          h - baseInset,
          w - baseInset,
          h - baseInset - maxCornerBand,
          colorsRight,
          null,
          Shader.TileMode.CLAMP,
        )
    }

    fun drawCornerArc(isLeft: Boolean) {
      val radius = ((if (isLeft) cornerRadii.first else cornerRadii.second) - inset * 0.65f)
        .coerceIn(18f * this.density, 56f * this.density)
      val yBottom = h - inset - (liftPx * 0.35f)

      fullPath.reset()
      if (isLeft) {
        val xLeft = inset
        fullPath.moveTo(xLeft + radius + bottomRun, yBottom)
        fullPath.lineTo(xLeft + radius, yBottom)
        arcRect.set(xLeft, yBottom - 2f * radius, xLeft + 2f * radius, yBottom)
        fullPath.arcTo(arcRect, 90f, 90f, false)
        fullPath.lineTo(xLeft, yBottom - radius - sideRun)
      } else {
        val xRight = w - inset
        fullPath.moveTo(xRight - radius - bottomRun, yBottom)
        fullPath.lineTo(xRight - radius, yBottom)
        arcRect.set(xRight - 2f * radius, yBottom - 2f * radius, xRight, yBottom)
        fullPath.arcTo(arcRect, 90f, -90f, false)
        fullPath.lineTo(xRight, yBottom - radius - sideRun)
      }

      pathMeasure.setPath(fullPath, false)
      val len = pathMeasure.length
      if (len <= 1f) return
      val startD = (len * 0.12f * p).coerceAtMost(len * 0.3f)
      val endD = (startD + len * sweepReveal).coerceAtMost(len)
      segmentPath.reset()
      pathMeasure.getSegment(startD, endD, segmentPath, true)

      val shader = if (isLeft) cachedLeftShader!! else cachedRightShader!!
      shaderMatrix.reset()
      shaderMatrix.setTranslate(0f, -liftPx * 0.35f)
      shader.setLocalMatrix(shaderMatrix)

      drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        paint.shader = shader

        // Soft outer glow (restrained, not overly bright)
        paint.strokeWidth = glowWidth
        paint.alpha = (fadeAlpha * 0.34f * 255f).toInt().coerceIn(0, 255)
        paint.maskFilter = blurFilter
        nc.drawPath(segmentPath, paint)

        // Crisp rounded neon stroke
        paint.strokeWidth = coreWidth
        paint.alpha = (fadeAlpha * 0.90f * 255f).toInt().coerceIn(0, 255)
        paint.maskFilter = null
        nc.drawPath(segmentPath, paint)
      }
    }

    drawCornerArc(isLeft = true)
    drawCornerArc(isLeft = false)
  }
}
