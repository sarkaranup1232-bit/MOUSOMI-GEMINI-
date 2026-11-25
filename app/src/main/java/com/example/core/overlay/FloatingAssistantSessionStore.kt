package com.example.core.overlay

import com.example.core.display.DisplayGeometry
import com.example.core.screen.AttachedVisualItem
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FloatingAttachmentItem(
  val id: String = "f_att_${UUID.randomUUID()}",
  val uriString: String,
  val filePath: String? = null,
  val label: String = "Image",
  val isFromScreenSelection: Boolean = false,
)

data class FloatingChatTurn(
  val id: String = "f_turn_${UUID.randomUUID()}",
  val isUser: Boolean,
  val text: String,
  val attachmentUris: List<String> = emptyList(),
  val modelLabel: String = "",
  val timestampMs: Long = System.currentTimeMillis(),
)

data class FloatingAssistantOverlayState(
  val isControlBarVisible: Boolean = false,
  val isPanelOpen: Boolean = false,
  val isPanelMinimized: Boolean = false,
  val isProfileQuickMenuOpen: Boolean = false,
  val promptText: String = "",
  val turns: List<FloatingChatTurn> = emptyList(),
  val pendingAttachments: List<FloatingAttachmentItem> = emptyList(),
  val previewingAttachment: FloatingAttachmentItem? = null,
  val activeModelLabel: String = "",
  val statusBanner: String? = null,
  val controlBarXFraction: Float = 0.5f,
  val controlBarYFraction: Float = 0.04f,
  val selectionTriggeredFromOverlay: Boolean = false,
)

object FloatingControlBarGeometry {

  fun computeDefaultBarPosition(
    barWidthPx: Int,
    barHeightPx: Int,
    geometry: DisplayGeometry,
  ): Pair<Int, Int> {
    val centerX = ((geometry.screenWidthPx - barWidthPx) * 0.5f).roundToInt()
    val topY = geometry.safeTopPx + (10f * geometry.density).roundToInt()
    return clampControlBarCoordinates(centerX, topY, barWidthPx, barHeightPx, geometry)
  }

  fun clampControlBarCoordinates(
    xPx: Int,
    yPx: Int,
    barWidthPx: Int,
    barHeightPx: Int,
    geometry: DisplayGeometry,
  ): Pair<Int, Int> {
    val sidePad = (8f * geometry.density).roundToInt()
    val topPad = (6f * geometry.density).roundToInt()
    val bottomPad = (16f * geometry.density).roundToInt()
    val minX = (geometry.safeLeftPx + sidePad).coerceAtLeast(0)
    val maxX = (geometry.screenWidthPx - geometry.safeRightPx - barWidthPx - sidePad).coerceAtLeast(minX)
    val minY = (geometry.safeTopPx + topPad).coerceAtLeast(0)
    val maxY =
      (geometry.screenHeightPx - geometry.safeBottomWithoutImePx - barHeightPx - bottomPad)
        .coerceAtLeast(minY)
    return Pair(xPx.coerceIn(minX, maxX), yPx.coerceIn(minY, maxY))
  }

  fun fractionFromCoordinates(
    xPx: Int,
    yPx: Int,
    barWidthPx: Int,
    barHeightPx: Int,
    geometry: DisplayGeometry,
  ): Pair<Float, Float> {
    val usableW = (geometry.screenWidthPx - geometry.safeLeftPx - geometry.safeRightPx - barWidthPx).coerceAtLeast(1)
    val usableH = (geometry.screenHeightPx - geometry.safeTopPx - geometry.safeBottomWithoutImePx - barHeightPx).coerceAtLeast(1)
    val xf = ((xPx - geometry.safeLeftPx).toFloat() / usableW).coerceIn(0f, 1f)
    val yf = ((yPx - geometry.safeTopPx).toFloat() / usableH).coerceIn(0f, 1f)
    return Pair(xf, yf)
  }

  fun coordinatesFromFraction(
    xFraction: Float,
    yFraction: Float,
    barWidthPx: Int,
    barHeightPx: Int,
    geometry: DisplayGeometry,
  ): Pair<Int, Int> {
    val usableW = (geometry.screenWidthPx - geometry.safeLeftPx - geometry.safeRightPx - barWidthPx).coerceAtLeast(1)
    val usableH = (geometry.screenHeightPx - geometry.safeTopPx - geometry.safeBottomWithoutImePx - barHeightPx).coerceAtLeast(1)
    val rawX = geometry.safeLeftPx + (xFraction.coerceIn(0f, 1f) * usableW).roundToInt()
    val rawY = geometry.safeTopPx + (yFraction.coerceIn(0f, 1f) * usableH).roundToInt()
    return clampControlBarCoordinates(rawX, rawY, barWidthPx, barHeightPx, geometry)
  }
}

/**
 * Persistent singleton state holder for the system-level Floating Control Bar and
 * Floating AI Chat Panel so conversation history, pending image attachments, draft input,
 * and toolbar position survive app switching, orientation changes, and panel minimize/reopen.
 */
@Singleton
class FloatingAssistantSessionStore @Inject constructor() {

  private val _state = MutableStateFlow(FloatingAssistantOverlayState())
  val state: StateFlow<FloatingAssistantOverlayState> = _state.asStateFlow()

  fun setControlBarVisible(visible: Boolean) {
    _state.update { it.copy(isControlBarVisible = visible) }
  }

  fun setPanelState(open: Boolean, minimized: Boolean = false) {
    _state.update {
      it.copy(
        isPanelOpen = open,
        isPanelMinimized = if (open) false else minimized,
        isProfileQuickMenuOpen = if (open) false else it.isProfileQuickMenuOpen,
      )
    }
  }

  fun minimizePanel() {
    _state.update {
      it.copy(
        isPanelOpen = false,
        isPanelMinimized = true,
        previewingAttachment = null,
        isProfileQuickMenuOpen = false,
      )
    }
  }

  fun setProfileQuickMenuOpen(open: Boolean) {
    _state.update { it.copy(isProfileQuickMenuOpen = open) }
  }

  fun updatePromptText(text: String) {
    _state.update { it.copy(promptText = text, statusBanner = null) }
  }

  fun updateControlBarFraction(xFraction: Float, yFraction: Float) {
    _state.update {
      it.copy(
        controlBarXFraction = xFraction.coerceIn(0f, 1f),
        controlBarYFraction = yFraction.coerceIn(0f, 1f),
      )
    }
  }

  fun setSelectionTriggeredFromOverlay(fromOverlay: Boolean) {
    _state.update { it.copy(selectionTriggeredFromOverlay = fromOverlay) }
  }

  fun attachImage(item: FloatingAttachmentItem, openPreviewIfFirst: Boolean = false) {
    _state.update { cur ->
      val deduped = cur.pendingAttachments.filterNot { it.uriString == item.uriString }
      val next = (deduped + item).takeLast(4)
      cur.copy(
        pendingAttachments = next,
        previewingAttachment = if (openPreviewIfFirst) item else cur.previewingAttachment,
        statusBanner = null,
      )
    }
  }

  fun attachVisualFromScreenSelection(visual: AttachedVisualItem) {
    val item =
      FloatingAttachmentItem(
        id = visual.id,
        uriString = visual.croppedUriString,
        filePath = visual.croppedFilePath,
        label = "Screen region",
        isFromScreenSelection = true,
      )
    attachImage(item, openPreviewIfFirst = false)
  }

  fun removePendingAttachment(uriString: String) {
    _state.update { cur ->
      val remaining = cur.pendingAttachments.filterNot { it.uriString == uriString }
      val preview =
        if (cur.previewingAttachment?.uriString == uriString) null else cur.previewingAttachment
      cur.copy(pendingAttachments = remaining, previewingAttachment = preview)
    }
  }

  fun clearPendingAttachments() {
    _state.update { it.copy(pendingAttachments = emptyList(), previewingAttachment = null) }
  }

  fun openAttachmentPreview(item: FloatingAttachmentItem?) {
    _state.update { it.copy(previewingAttachment = item) }
  }

  fun appendUserTurnAndPrepareAssistantPlaceholder(
    userDisplayText: String,
    attachmentUris: List<String>,
  ): String {
    val assistantId = "f_ai_${UUID.randomUUID()}"
    _state.update { cur ->
      val userTurn =
        FloatingChatTurn(
          isUser = true,
          text = userDisplayText,
          attachmentUris = attachmentUris,
        )
      val aiPlaceholder =
        FloatingChatTurn(
          id = assistantId,
          isUser = false,
          text = "",
          modelLabel = cur.activeModelLabel,
        )
      cur.copy(
        turns = (cur.turns + userTurn + aiPlaceholder).takeLast(30),
        promptText = "",
        pendingAttachments = emptyList(),
        previewingAttachment = null,
        statusBanner = null,
      )
    }
    return assistantId
  }

  fun updateAssistantTurn(
    assistantTurnId: String,
    text: String,
    modelLabel: String? = null,
  ) {
    _state.update { cur ->
      val resolvedLabel = modelLabel ?: cur.activeModelLabel
      val updated =
        cur.turns.map { t ->
          if (t.id == assistantTurnId) {
            t.copy(text = text, modelLabel = resolvedLabel)
          } else {
            t
          }
        }
      cur.copy(
        turns = updated,
        activeModelLabel = resolvedLabel,
      )
    }
  }

  fun appendLiveTranscriptTurn(
    isUser: Boolean,
    text: String,
    modelLabel: String = "Gemini Live",
  ) {
    val clean = text.trim()
    if (clean.isBlank()) return
    _state.update { cur ->
      val last = cur.turns.lastOrNull()
      val isSameAuthor = last != null && last.isUser == isUser && last.modelLabel == modelLabel
      val updated =
        if (isSameAuthor &&
          (last.text == clean || (System.currentTimeMillis() - last.timestampMs) < 12_000L)
        ) {
          cur.turns.dropLast(1) + last.copy(text = clean, timestampMs = System.currentTimeMillis())
        } else {
          (cur.turns + FloatingChatTurn(isUser = isUser, text = clean, modelLabel = modelLabel)).takeLast(30)
        }
      cur.copy(turns = updated)
    }
  }

  fun setActiveModelLabel(label: String) {
    _state.update { it.copy(activeModelLabel = label) }
  }

  fun setStatusBanner(message: String?) {
    _state.update { it.copy(statusBanner = message) }
  }

  fun clearConversationHistory() {
    _state.update {
      it.copy(
        turns = emptyList(),
        pendingAttachments = emptyList(),
        previewingAttachment = null,
        statusBanner = null,
      )
    }
  }

  fun resetTransientOverlayVisibilityOnly() {
    _state.update {
      it.copy(
        isControlBarVisible = false,
        isPanelOpen = false,
        isPanelMinimized = false,
        isProfileQuickMenuOpen = false,
        previewingAttachment = null,
        selectionTriggeredFromOverlay = false,
      )
    }
  }
}
