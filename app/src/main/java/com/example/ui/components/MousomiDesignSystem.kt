package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.screen.AttachedVisualItem
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.NeonComposerState
import com.example.domain.model.OperatingMode
import com.example.ui.model.AttachmentPreview
import com.example.ui.model.InputMode
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.LiquidGlassDark
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SubtleGold
import com.example.ui.theme.WarmAmber

/**
 * Centralized Design Tokens shared across Chat Composer, Screen Edge Light, and Floating AI Orb.
 */
object MousomiDesignTokens {
  val GoogleNeonSpectrum =
    listOf(
      ElectricBlue,
      NeonCyan,
      NeonViolet,
      NeonPurple,
      NeonMagenta,
      NeonPink,
      WarmAmber,
      SubtleGold,
      NeonGreen,
      ElectricBlue,
    )

  val ComposerCornerRadius: Dp = 28.dp
  val ComposerIdleBorderWidth: Dp = 1.9.dp
  val ComposerFocusedBorderWidth: Dp = 2.6.dp
  val ScreenCornerRadius: Dp = 32.dp

  fun composerBorderColors(state: NeonComposerState): List<Color> =
    when (state) {
      NeonComposerState.IDLE -> GoogleNeonSpectrum
      NeonComposerState.FOCUSED ->
        listOf(
          ElectricBlue,
          NeonCyan,
          NeonViolet,
          NeonMagenta,
          NeonPink,
          SubtleGold,
          NeonGreen,
          ElectricBlue,
        )
      NeonComposerState.TYPING ->
        listOf(NeonCyan, ElectricBlue, NeonViolet, NeonMagenta, WarmAmber, NeonCyan)
      NeonComposerState.VOICE_ACTIVE ->
        listOf(NeonCyan, ElectricBlue, NeonGreen, NeonCyan)
      NeonComposerState.AI_THINKING ->
        listOf(NeonViolet, NeonPurple, ElectricBlue, NeonMagenta, NeonViolet)
      NeonComposerState.AI_SPEAKING ->
        listOf(NeonGreen, NeonCyan, ElectricBlue, NeonPurple, NeonGreen)
      NeonComposerState.IMAGE_GENERATING ->
        listOf(NeonMagenta, NeonPink, NeonPurple, WarmAmber, SubtleGold, NeonCyan, NeonMagenta)
      NeonComposerState.COMPLETED ->
        listOf(NeonGreen, NeonCyan, SubtleGold, NeonGreen)
      NeonComposerState.ERROR ->
        listOf(Color(0xFFEF4444), WarmAmber, Color(0xFFDC2626), Color(0xFFEF4444))
    }
}

@Composable
fun LiquidGlassButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  accentColor: Color = NeonCyan,
  testTag: String? = null,
) {
  val colors = LocalMousomiColors.current
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    border =
      BorderStroke(
        width = 1.dp,
        brush = Brush.linearGradient(listOf(accentColor.copy(alpha = 0.65f), NeonPurple.copy(alpha = 0.45f))),
      ),
    modifier = modifier.let { if (testTag != null) it.testTag(testTag) else it },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(18.dp),
        )
      }
      Text(
        text = label,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun StreamingResponseIndicator(
  statusText: String,
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "streaming_pulse")
  val alpha by
    transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "dot_alpha",
    )

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(NeonCyan.copy(alpha = alpha))
    )
    Text(
      text = statusText,
      color = LocalMousomiColors.current.mutedText,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
fun TaskProgressCard(
  goal: String,
  steps: List<AgentStepPlan>,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("task_progress_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
        Text(
          text = "Task Plan • ${goal.take(44)}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }
      steps.forEachIndexed { idx, step ->
        val tint =
          when (step.status) {
            AgentStepStatus.COMPLETED -> NeonGreen
            AgentStepStatus.RUNNING -> NeonCyan
            AgentStepStatus.FAILED, AgentStepStatus.CANCELLED -> Color(0xFFEF4444)
            else -> colors.mutedText
          }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${idx + 1}.",
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = step.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = step.status.name.lowercase().replaceFirstChar { it.uppercase() },
            color = tint,
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }
  }
}

data class ComposerChipSpec(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val accent: Color,
  val onClick: () -> Unit,
)

@Composable
fun NeonChatComposer(
  composerState: NeonComposerState,
  glowIntensity: Float,
  reducedMotion: Boolean,
  inputMode: InputMode,
  inputText: String,
  pendingAttachments: List<AttachmentPreview>,
  hasImageContext: Boolean,
  activeEditTarget: ActiveEditImageTarget?,
  isGenerating: Boolean,
  isEditingMessage: Boolean,
  showDragHandle: Boolean = false,
  operatingMode: OperatingMode = OperatingMode.NORMAL_CHAT,
  onSelectOperatingMode: (OperatingMode) -> Unit = {},
  onOpenImageStudio: () -> Unit = {},
  onSelectMode: (InputMode) -> Unit,
  onInputChanged: (String) -> Unit,
  onOpenAttachments: () -> Unit,
  onOpenCameraCapture: () -> Unit,
  onRemoveAttachment: (AttachmentPreview) -> Unit,
  onCancelEditImageTarget: () -> Unit,
  onCancelEditing: () -> Unit,
  onVoiceClick: () -> Unit,
  onLiveClick: () -> Unit,
  onSendClick: () -> Unit,
  onStopGeneration: () -> Unit,
  onContextualAction: (String) -> Unit,
  modifier: Modifier = Modifier,
  attachedVisualsByUri: Map<String, AttachedVisualItem> = emptyMap(),
  onEditImagesClick: (() -> Unit)? = null,
  composerFocusRequester: FocusRequester? = null,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground
  val showSendButton = inputText.isNotBlank() || pendingAttachments.isNotEmpty()
  var isFocused by remember { mutableStateOf(false) }

  val effectiveState =
    when {
      composerState != NeonComposerState.IDLE -> composerState
      inputText.isNotEmpty() -> NeonComposerState.TYPING
      isFocused -> NeonComposerState.FOCUSED
      else -> NeonComposerState.IDLE
    }

  // Strictly animate bright sweeping neon border ONLY during active AI/Voice/Image execution
  val isVibrantExecutionActive =
    effectiveState in
      setOf(
        NeonComposerState.AI_THINKING,
        NeonComposerState.IMAGE_GENERATING,
        NeonComposerState.VOICE_ACTIVE,
        NeonComposerState.AI_SPEAKING,
        NeonComposerState.ERROR,
      )

  val shouldSpinNeon = !reducedMotion
  val rotationDeg =
    if (shouldSpinNeon) {
      val infiniteTransition = rememberInfiniteTransition(label = "neon_composer_border")
      val animated by
        infiniteTransition.animateFloat(
          initialValue = 0f,
          targetValue = 360f,
          animationSpec =
            infiniteRepeatable(
              animation =
                tween(
                  durationMillis =
                    when (effectiveState) {
                      NeonComposerState.AI_THINKING,
                      NeonComposerState.IMAGE_GENERATING,
                      NeonComposerState.VOICE_ACTIVE,
                      NeonComposerState.ERROR -> 1850
                      NeonComposerState.AI_SPEAKING -> 2400
                      NeonComposerState.FOCUSED,
                      NeonComposerState.TYPING,
                      NeonComposerState.COMPLETED -> 3200
                      NeonComposerState.IDLE -> 4200
                    },
                  easing = LinearEasing,
                ),
              repeatMode = RepeatMode.Restart,
            ),
          label = "neon_rotation",
        )
      animated
    } else {
      28f
    }

  val targetAlpha =
    when {
      isVibrantExecutionActive -> 1.0f
      effectiveState == NeonComposerState.COMPLETED -> 0.88f
      isFocused || effectiveState == NeonComposerState.TYPING -> 0.85f
      else -> 0.68f
    } * glowIntensity.coerceIn(0.45f, 1.0f)

  val animatedGlowAlpha by animateFloatAsState(
    targetValue = targetAlpha.coerceIn(0f, 1f),
    animationSpec = tween(220),
    label = "glow_alpha",
  )

  val borderStrokeDp by animateDpAsState(
    targetValue =
      when {
        isVibrantExecutionActive -> MousomiDesignTokens.ComposerFocusedBorderWidth
        isFocused || effectiveState != NeonComposerState.IDLE -> 2.3.dp
        else -> MousomiDesignTokens.ComposerIdleBorderWidth
      },
    animationSpec = tween(200),
    label = "composer_border_width",
  )

  val mergedBottomInsets =
    WindowInsets.navigationBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(mergedBottomInsets)
        .padding(bottom = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (showDragHandle) {
      Box(
        modifier =
          Modifier
            .padding(bottom = 4.dp)
            .width(34.dp)
            .height(3.5.dp)
            .clip(CircleShape)
            .background(onBg.copy(alpha = 0.22f))
      )
    }

    if (activeEditTarget != null) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.activeChipContainer,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.55f)),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).testTag("active_image_edit_badge"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Outlined.Edit, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(15.dp))
          Text(
            text = "Editing image v${activeEditTarget.versionNumber}",
            color = onBg,
            style = MaterialTheme.typography.labelLarge,
          )
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cancel image edit",
            tint = colors.mutedText,
            modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onCancelEditImageTarget),
          )
        }
      }
    }

    if (isEditingMessage) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.activeChipContainer,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Editing message",
            color = onBg,
            style = MaterialTheme.typography.labelLarge,
          )
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cancel editing",
            tint = colors.mutedText,
            modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onCancelEditing),
          )
        }
      }
    }

    // All 5 Operating Modes Pill Strip + Quick Create Image & Image Studio Chips (horizontally scrollable without clipping)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.width(6.dp))
      ComposerModePill(
        label = stringResource(R.string.mode_normal),
        selected = operatingMode == OperatingMode.NORMAL_CHAT && inputMode != InputMode.TASK,
        onClick = {
          onSelectMode(InputMode.CHAT)
          onSelectOperatingMode(OperatingMode.NORMAL_CHAT)
        },
        testTag = "mode_normal_button",
      )
      ComposerModePill(
        label = stringResource(R.string.mode_analysis),
        selected = operatingMode == OperatingMode.ANALYSIS_THINK,
        onClick = {
          onSelectMode(InputMode.CHAT)
          onSelectOperatingMode(OperatingMode.ANALYSIS_THINK)
        },
        testTag = "mode_analysis_button",
      )
      ComposerModePill(
        label = stringResource(R.string.mode_ai_search),
        selected = operatingMode == OperatingMode.GOOGLE_AI_SEARCH,
        onClick = {
          onSelectMode(InputMode.CHAT)
          onSelectOperatingMode(OperatingMode.GOOGLE_AI_SEARCH)
        },
        testTag = "mode_search_button",
      )
      ComposerModePill(
        label = stringResource(R.string.mode_deep_research),
        selected = operatingMode == OperatingMode.DEEP_RESEARCH,
        onClick = {
          onSelectMode(InputMode.CHAT)
          onSelectOperatingMode(OperatingMode.DEEP_RESEARCH)
        },
        testTag = "mode_deep_research_button",
      )
      ComposerModePill(
        label = stringResource(R.string.mode_task_mode),
        selected = operatingMode == OperatingMode.TASK_MODE || inputMode == InputMode.TASK,
        onClick = {
          onSelectMode(InputMode.TASK)
          onSelectOperatingMode(OperatingMode.TASK_MODE)
        },
        testTag = "mode_task_button",
      )

      // Restored Create Image chip & Image Studio entry point right on composer strip
      Surface(
        onClick = { onContextualAction("create_image") },
        shape = CircleShape,
        color = colors.activeChipContainer,
        border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.45f)),
        modifier = Modifier.testTag("context_chip_create_image"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = NeonMagenta,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = stringResource(R.string.create_image_chip),
            color = onBg,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
          )
        }
      }

      Surface(
        onClick = onOpenImageStudio,
        shape = CircleShape,
        color = colors.activeChipContainer,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
        modifier = Modifier.testTag("context_chip_image_studio"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.AutoFixHigh,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = stringResource(R.string.image_studio_title),
            color = onBg,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
      Spacer(modifier = Modifier.width(6.dp))
    }

    val hasEditableVisualAttachment =
      pendingAttachments.any {
        it is AttachmentPreview.PickedPhotoUri || it is AttachmentPreview.SampleInlineArtwork
      }
    if (hasEditableVisualAttachment && onEditImagesClick != null) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 660.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        FloatingEditImagesPill(
          visible = true,
          onClick = onEditImagesClick,
        )
      }
    }

    // Signature Concentric Multicolor Neon Capsule with reserved outer glow bleed padding
    val glowBleedDp = 7.dp
    val outerCorner = MousomiDesignTokens.ComposerCornerRadius
    val innerCorner = (outerCorner - borderStrokeDp).coerceAtLeast(20.dp)
    val borderColors = remember(effectiveState) { MousomiDesignTokens.composerBorderColors(effectiveState) }
    val borderIntColors = remember(borderColors) { borderColors.map { it.toArgb() }.toIntArray() }
    val paint =
      remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          isDither = true
          strokeJoin = Paint.Join.ROUND
          strokeCap = Paint.Cap.ROUND
        }
      }
    val shaderMatrix = remember { Matrix() }
    val rectF = remember { RectF() }

    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 660.dp)
          .padding(horizontal = 10.dp)
          .testTag("neon_chat_composer"),
    ) {
      // Aspect-corrected continuous vivid neon border + soft unclipped glow around all 4 rounded corners
      Canvas(modifier = Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        if (w <= 8f || h <= 8f) return@Canvas

        val glowBleedPx = glowBleedDp.toPx()
        val strokePx = borderStrokeDp.toPx()
        val halfStroke = strokePx / 2f
        val strokeInset = glowBleedPx + halfStroke
        if (w <= strokeInset * 2f + 4f || h <= strokeInset * 2f + 4f) return@Canvas

        val cx = w / 2f
        val cy = h / 2f
        val outerCornerPx = outerCorner.toPx()
        val angle = if (reducedMotion) 28f else rotationDeg

        val drawW = (w - glowBleedPx * 2f).coerceAtLeast(4f)
        val drawH = (h - glowBleedPx * 2f).coerceAtLeast(4f)

        val shader = SweepGradient(0f, 0f, borderIntColors, null)
        shaderMatrix.reset()
        shaderMatrix.setRotate(angle)
        shaderMatrix.postScale(drawW / 2f, drawH / 2f)
        shaderMatrix.postTranslate(cx, cy)
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader

        rectF.set(strokeInset, strokeInset, w - strokeInset, h - strokeInset)
        val maxRadius = minOf(rectF.width(), rectF.height()) * 0.5f
        val haloCorner = (outerCornerPx - halfStroke).coerceIn(8f, maxRadius)

        drawIntoCanvas { canvas ->
          val nc = canvas.nativeCanvas

          // Layer 1: Smooth outer neon bloom across IDLE, FOCUSED, and active states
          paint.strokeWidth = strokePx * (if (isVibrantExecutionActive) 2.5f else 2.0f)
          paint.alpha =
            (animatedGlowAlpha * (if (isVibrantExecutionActive) 0.45f else 0.34f) * 255f)
              .toInt()
              .coerceIn(14, 255)
          paint.maskFilter = BlurMaskFilter(glowBleedPx * 0.92f, BlurMaskFilter.Blur.NORMAL)
          nc.drawRoundRect(rectF, haloCorner, haloCorner, paint)

          // Layer 2: Crisp luminous concentric border aligned cleanly to all 4 rounded corners
          paint.strokeWidth = strokePx
          paint.alpha = (animatedGlowAlpha * 255f).toInt().coerceIn(64, 255)
          paint.maskFilter = null
          nc.drawRoundRect(rectF, haloCorner, haloCorner, paint)
        }
      }

      Surface(
        shape = RoundedCornerShape(innerCorner),
        color =
          if (colors.isDark) Color(0xF20D111D)
          else colors.inputPillContainer.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth().padding(glowBleedDp + borderStrokeDp),
      ) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
          if (pendingAttachments.isNotEmpty()) {
            ChatComposerAttachmentView(
              pendingAttachments = pendingAttachments,
              attachedVisualsByUri = attachedVisualsByUri,
              onRemoveAttachment = onRemoveAttachment,
              onTapImageAttachment = { onEditImagesClick?.invoke() },
            )
          }

          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onOpenAttachments,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("attachment_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                GeminiFourPointSparkle(
                  size = 21.dp,
                  animated =
                    effectiveState == NeonComposerState.AI_THINKING ||
                      effectiveState == NeonComposerState.IMAGE_GENERATING,
                )
              }
            }

            Box(
              modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 10.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              if (inputText.isEmpty()) {
                Text(
                  text =
                    when {
                      activeEditTarget != null -> "Describe edit for image v${activeEditTarget.versionNumber}…"
                      operatingMode == OperatingMode.TASK_MODE || inputMode == InputMode.TASK ->
                        stringResource(R.string.input_placeholder_task)
                      operatingMode == OperatingMode.ANALYSIS_THINK -> stringResource(R.string.input_placeholder_analysis)
                      operatingMode == OperatingMode.GOOGLE_AI_SEARCH -> stringResource(R.string.input_placeholder_search)
                      operatingMode == OperatingMode.DEEP_RESEARCH -> stringResource(R.string.input_placeholder_research)
                      else -> stringResource(R.string.input_placeholder_chat)
                    },
                  color = colors.mutedText,
                  style = MaterialTheme.typography.bodyLarge,
                  fontSize = 16.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              BasicTextField(
                value = inputText,
                onValueChange = onInputChanged,
                textStyle =
                  MaterialTheme.typography.bodyLarge.copy(
                    color = onBg,
                    fontSize = 16.sp,
                  ),
                cursorBrush = SolidColor(NeonCyan),
                maxLines = 4,
                keyboardOptions =
                  KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                  ),
                keyboardActions = KeyboardActions(onSend = { if (!isGenerating) onSendClick() }),
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .let { if (composerFocusRequester != null) it.focusRequester(composerFocusRequester) else it }
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag("chat_input_field"),
              )
            }

          IconButton(
            onClick = onVoiceClick,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_input_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Mic,
              contentDescription = stringResource(R.string.cd_voice_input),
              tint =
                if (effectiveState == NeonComposerState.VOICE_ACTIVE) NeonCyan
                else onBg.copy(alpha = 0.90f),
              modifier = Modifier.size(23.dp),
            )
          }

          Spacer(modifier = Modifier.width(2.dp))

          AnimatedContent(
            targetState =
              when {
                isGenerating -> 2
                showSendButton -> 1
                else -> 0
              },
            transitionSpec = {
              (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.85f))
            },
            label = "action_circle_mode",
          ) { mode ->
            when (mode) {
              2 ->
                Surface(
                  onClick = onStopGeneration,
                  shape = CircleShape,
                  color = Color(0xFFDC2626),
                  modifier = Modifier.size(44.dp).testTag("stop_generation_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.Stop,
                      contentDescription = "Stop generation",
                      tint = Color.White,
                      modifier = Modifier.size(22.dp),
                    )
                  }
                }
              1 ->
                Surface(
                  onClick = onSendClick,
                  shape = CircleShape,
                  color = ElectricBlue,
                  modifier = Modifier.size(44.dp).testTag("send_message_button"),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = stringResource(R.string.cd_send_message),
                      tint = Color.White,
                      modifier = Modifier.size(20.dp),
                    )
                  }
                }
                else ->
                  Surface(
                    onClick = onLiveClick,
                    shape = CircleShape,
                    color = ElectricBlue,
                    modifier = Modifier.size(44.dp).testTag("gemini_live_button"),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      GeminiLiveWaveformIcon(tint = Color.White, size = 21.dp)
                    }
                  }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ComposerModePill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground

  Box(
    modifier =
      Modifier
        .clip(CircleShape)
        .background(if (selected) colors.activeChipContainer else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 7.dp)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = if (selected) onBg else colors.mutedText,
      style = MaterialTheme.typography.labelLarge,
      fontSize = 14.sp,
      fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
      maxLines = 1,
      softWrap = false,
    )
  }
}
