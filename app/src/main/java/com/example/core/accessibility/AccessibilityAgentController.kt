package com.example.core.accessibility

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AccessibilityGlobalAction(val label: String) {
  BACK("Back"),
  HOME("Home"),
  RECENTS("Recents"),
  NOTIFICATIONS("Notifications Shade"),
}

data class AccessibleUiNode(
  val nodeId: String,
  val text: String,
  val contentDescription: String,
  val viewIdResourceName: String = "",
  val hintText: String = "",
  val className: String = "",
  val packageName: String = "",
  val isClickable: Boolean = false,
  val isEditable: Boolean = false,
  val isScrollable: Boolean = false,
  val isPassword: Boolean = false,
  val boundsSummary: String = "",
) {
  val primaryLabel: String
    get() =
      text.trim().ifBlank {
        contentDescription.trim().ifBlank { hintText.trim() }
      }
}

data class AccessibilityActionResult(
  val success: Boolean,
  val statusLabel: String,
  val detail: String,
  val requiresConfirmation: Boolean = false,
  val pendingActionSummary: String = "",
  val targetLabelToConfirm: String = "",
)

data class AccessibilityWorkflowState(
  val isServiceConnected: Boolean = false,
  val activePackageName: String = "",
  val isWorkflowRunning: Boolean = false,
  val currentStepLabel: String = "",
  val lastActionResult: String = "",
  val visibleNodesPreview: List<AccessibleUiNode> = emptyList(),
  val lastUpdatedMs: Long = 0L,
)

/**
 * Pure-Kotlin safety & target matching rules for Accessibility automation.
 * Guarantees sensitive UI fields (passwords, OTPs, PINs, card/CVV, payments) are strictly blocked
 * and destructive/external submission buttons require explicit user confirmation.
 */
object AccessibilitySafetyFilter {

  private val sensitiveFieldKeywords =
    Regex(
      "\\b(password|passcode|pin|otp|2fa|verification\\s*code|security\\s*code|cvv|cvc|card\\s*number|credit\\s*card|debit\\s*card|bank\\s*account|routing\\s*number|ssn|seed\\s*phrase|private\\s*key|secret\\s*key)\\b",
      RegexOption.IGNORE_CASE,
    )

  private val confirmationRequiredKeywords =
    Regex(
      "\\b(send|post|publish|submit|pay|purchase|buy|order|transfer|delete|remove|uninstall|confirm\\s*payment|checkout)\\b",
      RegexOption.IGNORE_CASE,
    )

  fun isSensitiveNode(
    isPassword: Boolean,
    viewId: String? = null,
    labelText: String? = null,
    contentDesc: String? = null,
    hintText: String? = null,
  ): Boolean {
    if (isPassword) return true
    val combined =
      listOfNotNull(viewId, labelText, contentDesc, hintText)
        .joinToString(" ")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
    if (combined.isBlank()) return false
    return sensitiveFieldKeywords.containsMatchIn(combined)
  }

  fun isSensitiveNode(node: AccessibleUiNode): Boolean =
    isSensitiveNode(
      isPassword = node.isPassword,
      viewId = node.viewIdResourceName,
      labelText = node.text,
      contentDesc = node.contentDescription,
      hintText = node.hintText,
    )

  fun requiresExplicitConfirmation(targetOrActionText: String): Boolean {
    val clean = targetOrActionText.trim()
    if (clean.isBlank()) return false
    return confirmationRequiredKeywords.containsMatchIn(clean)
  }

  fun findBestMatchingNode(
    nodes: List<AccessibleUiNode>,
    targetQuery: String,
    requireEditable: Boolean = false,
  ): AccessibleUiNode? {
    val clean = targetQuery.trim().lowercase(Locale.US)
    if (clean.isBlank()) return null
    val safeCandidates =
      nodes.filter { node ->
        !isSensitiveNode(node) &&
          (!requireEditable || node.isEditable)
      }
    if (safeCandidates.isEmpty()) return null

    // 1. Exact label match (preferring interactive nodes)
    safeCandidates
      .filter {
        it.text.trim().lowercase(Locale.US) == clean ||
          it.contentDescription.trim().lowercase(Locale.US) == clean ||
          it.hintText.trim().lowercase(Locale.US) == clean
      }
      .sortedByDescending { if (it.isClickable || it.isEditable) 1 else 0 }
      .firstOrNull()
      ?.let { return it }

    // 2. StartsWith match
    safeCandidates
      .filter {
        it.primaryLabel.lowercase(Locale.US).startsWith(clean)
      }
      .sortedByDescending { if (it.isClickable || it.isEditable) 1 else 0 }
      .firstOrNull()
      ?.let { return it }

    // 3. Contains match
    return safeCandidates
      .filter {
        it.primaryLabel.lowercase(Locale.US).contains(clean) ||
          it.viewIdResourceName.lowercase(Locale.US).substringAfterLast('/').contains(clean.replace(' ', '_'))
      }
      .sortedByDescending { if (it.isClickable || it.isEditable) 1 else 0 }
      .firstOrNull()
  }
}

/**
 * Coordinates user-authorized Android Accessibility automation safely across UI, Overlay, and
 * AgentTaskPlanner. Never guesses blind screen coordinates; interacts strictly via verified nodes.
 */
@Singleton
class AccessibilityAgentController @Inject constructor() {

  interface AccessibilityBridge {
    fun getActivePackageName(): String
    fun readVisibleNodes(): List<AccessibleUiNode>
    fun clickNode(nodeId: String, targetLabel: String): AccessibilityActionResult
    fun inputText(nodeId: String?, text: String): AccessibilityActionResult
    fun scrollScreen(forward: Boolean): AccessibilityActionResult
    fun performGlobal(action: AccessibilityGlobalAction): AccessibilityActionResult
  }

  @Volatile
  private var bridge: AccessibilityBridge? = null

  private val _state = MutableStateFlow(AccessibilityWorkflowState())
  val state: StateFlow<AccessibilityWorkflowState> = _state.asStateFlow()

  fun attachServiceBridge(serviceBridge: AccessibilityBridge) {
    bridge = serviceBridge
    _state.update {
      it.copy(
        isServiceConnected = true,
        activePackageName = serviceBridge.getActivePackageName(),
        lastActionResult = "Accessibility Assistant connected cleanly.",
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
  }

  fun detachServiceBridge() {
    bridge = null
    _state.update {
      it.copy(
        isServiceConnected = false,
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = "Accessibility Assistant disconnected.",
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
  }

  fun updateWindowSnapshot(packageName: String, nodes: List<AccessibleUiNode>) {
    val sanitized =
      nodes
        .filter { !AccessibilitySafetyFilter.isSensitiveNode(it) && it.primaryLabel.isNotBlank() }
        .take(30)
    _state.update {
      it.copy(
        activePackageName = packageName,
        visibleNodesPreview = sanitized,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
  }

  fun refreshVisibleNodes(): List<AccessibleUiNode> {
    val active = bridge ?: return emptyList()
    val pkg = active.getActivePackageName()
    val raw = active.readVisibleNodes()
    val safe = raw.filter { !AccessibilitySafetyFilter.isSensitiveNode(it) }
    _state.update {
      it.copy(
        activePackageName = pkg,
        visibleNodesPreview = safe.filter { n -> n.primaryLabel.isNotBlank() }.take(30),
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
    return safe
  }

  fun performNodeClick(
    targetText: String,
    confirmedSensitiveAction: Boolean = false,
  ): AccessibilityActionResult {
    val active =
      bridge
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Accessibility Off",
          detail = "Accessibility Assistant is not enabled. Enable it in Android Accessibility Settings to allow UI taps.",
        )

    if (AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, labelText = targetText)) {
      return AccessibilityActionResult(
        success = false,
        statusLabel = "Blocked Sensitive Field",
        detail = "For security, Mousomi never interacts with password, PIN, OTP, or payment fields.",
      )
    }

    if (!confirmedSensitiveAction && AccessibilitySafetyFilter.requiresExplicitConfirmation(targetText)) {
      return AccessibilityActionResult(
        success = false,
        statusLabel = "Confirmation Required",
        detail = "Confirm before tapping \"$targetText\" because it may submit, send, pay, or delete data.",
        requiresConfirmation = true,
        pendingActionSummary = "Tap button \"$targetText\" in ${active.getActivePackageName().ifBlank { "current app" }}",
        targetLabelToConfirm = targetText,
      )
    }

    _state.update { it.copy(isWorkflowRunning = true, currentStepLabel = "Finding \"$targetText\"") }
    val nodes = active.readVisibleNodes()
    val rawMatch =
      AccessibilitySafetyFilter.findBestMatchingNode(nodes, targetText)
        ?: run {
          // Check whether a blocked sensitive node existed matching that name
          val blockedSensitive =
            nodes.any {
              AccessibilitySafetyFilter.isSensitiveNode(it) &&
                it.primaryLabel.contains(targetText.trim(), ignoreCase = true)
            }
          val msg =
            if (blockedSensitive) {
              "Target \"$targetText\" is a protected password/OTP/payment element and cannot be clicked automatically."
            } else {
              "Could not find visible UI element matching \"$targetText\" on the current screen."
            }
          _state.update {
            it.copy(isWorkflowRunning = false, currentStepLabel = "", lastActionResult = msg)
          }
          return AccessibilityActionResult(
            success = false,
            statusLabel = if (blockedSensitive) "Blocked" else "Element Not Found",
            detail = msg,
          )
        }

    val res = active.clickNode(rawMatch.nodeId, rawMatch.primaryLabel.ifBlank { targetText })
    _state.update {
      it.copy(
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = res.detail,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
    return res
  }

  fun performTextInput(
    targetFieldHintOrNull: String?,
    inputText: String,
  ): AccessibilityActionResult {
    val active =
      bridge
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Accessibility Off",
          detail = "Accessibility Assistant is not enabled in Android Settings.",
        )

    if (AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, labelText = targetFieldHintOrNull) ||
      AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, labelText = inputText)
    ) {
      return AccessibilityActionResult(
        success = false,
        statusLabel = "Blocked Sensitive Input",
        detail = "Mousomi refuses to type passwords, PINs, OTPs, or payment details.",
      )
    }

    _state.update { it.copy(isWorkflowRunning = true, currentStepLabel = "Entering text") }
    val nodes = active.readVisibleNodes()
    val matchedNode =
      if (!targetFieldHintOrNull.isNullOrBlank()) {
        AccessibilitySafetyFilter.findBestMatchingNode(nodes, targetFieldHintOrNull, requireEditable = true)
      } else {
        nodes.firstOrNull { it.isEditable && !AccessibilitySafetyFilter.isSensitiveNode(it) }
      }

    val res = active.inputText(matchedNode?.nodeId, inputText)
    _state.update {
      it.copy(
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = res.detail,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
    return res
  }

  fun performScroll(forward: Boolean): AccessibilityActionResult {
    val active =
      bridge
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Accessibility Off",
          detail = "Enable Mousomi Accessibility Assistant to scroll other apps.",
        )
    _state.update {
      it.copy(
        isWorkflowRunning = true,
        currentStepLabel = if (forward) "Scrolling down" else "Scrolling up",
      )
    }
    val res = active.scrollScreen(forward)
    _state.update {
      it.copy(
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = res.detail,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
    return res
  }

  fun performGlobalNav(action: AccessibilityGlobalAction): AccessibilityActionResult {
    val active =
      bridge
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Accessibility Off",
          detail = "Enable Mousomi Accessibility Assistant to trigger system ${action.label}.",
        )
    val res = active.performGlobal(action)
    _state.update {
      it.copy(
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = res.detail,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
    return res
  }

  fun stopAllAutomation(reason: String = "Accessibility automation stopped by user.") {
    _state.update {
      it.copy(
        isWorkflowRunning = false,
        currentStepLabel = "",
        lastActionResult = reason,
        lastUpdatedMs = System.currentTimeMillis(),
      )
    }
  }
}
