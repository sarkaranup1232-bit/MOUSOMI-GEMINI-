package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedContent
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
import com.example.domain.model.ActiveEditImageTarget
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.NeonComposerState
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

  val infiniteTransition = rememberInfiniteTransition(label = "neon_composer_border")
  val rotationDeg by
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
                  NeonComposerState.VOICE_ACTIVE -> 1900
                  NeonComposerState.TYPING,
                  NeonComposerState.AI_SPEAKING,
                  NeonComposerState.FOCUSED -> 3100
                  NeonComposerState.COMPLETED -> 2400
                  else -> 5200
                },
              easing = LinearEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "neon_rotation",
    )

  val targetAlpha =
    when (effectiveState) {
      NeonComposerState.IDLE -> 0.86f
      NeonComposerState.FOCUSED -> 1.0f
      NeonComposerState.TYPING -> 0.96f
      NeonComposerState.AI_THINKING,
      NeonComposerState.IMAGE_GENERATING,
      NeonComposerState.VOICE_ACTIVE,
      NeonComposerState.AI_SPEAKING -> 1.0f
      NeonComposerState.ERROR -> 1.0f
      NeonComposerState.COMPLETED -> 0.95f
    } * glowIntensity.coerceIn(0.45f, 1.0f)

  val animatedGlowAlpha by animateFloatAsState(
    targetValue = targetAlpha.coerceIn(0f, 1f),
    animationSpec = tween(220),
    label = "glow_alpha",
  )

  val borderStrokeDp by animateDpAsState(
    targetValue =
      if (isFocused || effectiveState != NeonComposerState.IDLE) MousomiDesignTokens.ComposerFocusedBorderWidth
      else MousomiDesignTokens.ComposerIdleBorderWidth,
    animationSpec = tween(200),
    label = "composer_border_width",
  )

  // Unified primary action bar (single collection, zero home-screen duplication)
  val contextualChips =
    remember(hasImageContext, activeEditTarget) {
      if (hasImageContext || activeEditTarget != null) {
        listOf(
          ComposerChipSpec("edit_image", "Edit Image", Icons.Outlined.Edit, NeonCyan) {
            onContextualAction("edit_image")
          },
          ComposerChipSpec("analyze_image", "Analyze Image", Icons.Outlined.ImageSearch, ElectricBlue) {
            onContextualAction("analyze_image")
          },
          ComposerChipSpec("enhance", "Enhance", Icons.Outlined.AutoFixHigh, NeonGreen) {
            onContextualAction("enhance")
          },
          ComposerChipSpec("change_bg", "Background", Icons.Outlined.Wallpaper, NeonPurple) {
            onContextualAction("change_bg")
          },
          ComposerChipSpec("add_text", "Add Text", Icons.Outlined.TextFields, WarmAmber) {
            onContextualAction("add_text")
          },
          ComposerChipSpec("remove_obj", "Remove", Icons.Outlined.CleaningServices, NeonMagenta) {
            onContextualAction("remove_obj")
          },
          ComposerChipSpec("draw_region", "Select Region", Icons.Outlined.Brush, NeonCyan) {
            onContextualAction("draw_region")
          },
        )
      } else {
        listOf(
          ComposerChipSpec("generate_image", "Image", Icons.Outlined.AutoAwesome, NeonMagenta) {
            onContextualAction("open_image_tools")
          },
          ComposerChipSpec("camera", "Camera", Icons.Outlined.CameraAlt, NeonCyan) {
            onOpenCameraCapture()
          },
          ComposerChipSpec("attach_file", "Attach", Icons.Outlined.AttachFile, ElectricBlue) {
            onOpenAttachments()
          },
          ComposerChipSpec("deep_research", "Deep Research", Icons.Outlined.Science, NeonGreen) {
            onContextualAction("deep_research")
          },
          ComposerChipSpec("more_tools", "Tools", Icons.Outlined.Tune, WarmAmber) {
            onContextualAction("open_more_tools")
          },
        )
      }
    }

  val mergedBottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(mergedBottomInsets)
        .padding(bottom = 8.dp),
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

    if (pendingAttachments.isNotEmpty()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pendingAttachments.forEachIndexed { idx, attachment ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.activeChipContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text =
                  when (attachment) {
                    AttachmentPreview.SampleInlineArtwork -> "Sample Art"
                    is AttachmentPreview.PickedPhotoUri -> "Photo ${idx + 1}"
                    is AttachmentPreview.MediaFile -> attachment.item.displayName.take(18)
                  },
                color = onBg,
                style = MaterialTheme.typography.labelLarge,
              )
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove attachment",
                tint = colors.mutedText,
                modifier =
                  Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onRemoveAttachment(attachment) },
              )
            }
          }
        }
      }
    }

    // Primary organized action chip row
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .testTag("contextual_action_chips_row"),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      contextualChips.forEach { chip ->
        Surface(
          onClick = chip.onClick,
          shape = CircleShape,
          color = if (colors.isDark) LiquidGlassDark else MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, chip.accent.copy(alpha = 0.42f)),
          modifier = Modifier.testTag("context_chip_${chip.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = chip.icon,
              contentDescription = null,
              tint = chip.accent,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = chip.label,
              color = onBg,
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ComposerModePill(
        label = stringResource(R.string.mode_chat),
        selected = inputMode == InputMode.CHAT,
        onClick = { onSelectMode(InputMode.CHAT) },
        testTag = "mode_chat_button",
      )
      ComposerModePill(
        label = stringResource(R.string.mode_task),
        selected = inputMode == InputMode.TASK,
        onClick = { onSelectMode(InputMode.TASK) },
        testTag = "mode_task_button",
      )
    }

    // Signature Concentric Multicolor Neon Capsule
    val outerCorner = MousomiDesignTokens.ComposerCornerRadius
    val innerCorner = (outerCorner - borderStrokeDp).coerceAtLeast(22.dp)
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
          .padding(horizontal = 14.dp)
          .testTag("neon_chat_composer"),
    ) {
      // Aspect-corrected continuous vivid neon border + soft contained glow
      Canvas(modifier = Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        val cx = w / 2f
        val cy = h / 2f
        val strokePx = borderStrokeDp.toPx()
        val halfStroke = strokePx / 2f
        val outerCornerPx = outerCorner.toPx()
        val angle = if (reducedMotion) 25f else rotationDeg

        // Normalized square-to-capsule scale transform keeps perimeter angular distribution uniform
        // on wide single-line AND tall multi-line states, eliminating dim top/bottom midsections.
        val shader = SweepGradient(0f, 0f, borderIntColors, null)
        shaderMatrix.reset()
        shaderMatrix.setRotate(angle)
        shaderMatrix.postScale(w / 2f, h / 2f)
        shaderMatrix.postTranslate(cx, cy)
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader

        drawIntoCanvas { canvas ->
          val nc = canvas.nativeCanvas

          // Layer 1: Contained soft bloom (strictly close to capsule, zero bleed into chips)
          paint.strokeWidth = strokePx * 2.5f
          paint.alpha = (animatedGlowAlpha * 0.34f * 255f).toInt().coerceIn(8, 255)
          paint.maskFilter = BlurMaskFilter(strokePx * 2.1f, BlurMaskFilter.Blur.NORMAL)
          rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
          val haloCorner = (outerCornerPx - halfStroke).coerceAtLeast(8f)
          nc.drawRoundRect(rectF, haloCorner, haloCorner, paint)

          // Layer 2: Crisp luminous concentric stroke aligned with container perimeter
          paint.strokeWidth = strokePx
          paint.alpha = (animatedGlowAlpha * 255f).toInt().coerceIn(32, 255)
          paint.maskFilter = null
          nc.drawRoundRect(rectF, haloCorner, haloCorner, paint)
        }
      }

      Surface(
        shape = RoundedCornerShape(innerCorner),
        color =
          if (colors.isDark) Color(0xF20D111D)
          else colors.inputPillContainer.copy(alpha = 0.98f),
        modifier = Modifier.fillMaxWidth().padding(borderStrokeDp),
      ) {
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
                    inputMode == InputMode.TASK -> stringResource(R.string.input_placeholder_task)
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
        .padding(horizontal = 18.dp, vertical = 5.dp)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = if (selected) onBg else colors.mutedText,
      style = MaterialTheme.typography.labelLarge,
      fontSize = 14.sp,
      fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
    )
  }
}
