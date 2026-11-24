package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.accessibility.AccessibilityGlobalAction
import com.example.core.accessibility.AccessibilitySafetyFilter
import com.example.core.accessibility.AccessibleUiNode
import com.example.core.permissions.EmergencyStopController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Optional User-Authorized Accessibility Service for Mousomi AI.
 * - Manually enabled by user in Android Accessibility Settings after transparent onboarding.
 * - Performs node-verified UI reads, clicks, non-sensitive text inputs, scrolling, and navigation.
 * - Never logs sensitive node text, blocks password/OTP/PIN/payment fields, and honors Emergency Stop.
 */
@AndroidEntryPoint
class MousomiAccessibilityService :
  AccessibilityService(),
  AccessibilityAgentController.AccessibilityBridge {

  @Inject lateinit var accessibilityController: AccessibilityAgentController
  @Inject lateinit var emergencyStopController: EmergencyStopController

  private val nodeLookupMap = LinkedHashMap<String, AccessibilityNodeInfo>()

  override fun onServiceConnected() {
    super.onServiceConnected()
    accessibilityController.attachServiceBridge(this)
    emergencyStopController.registerStopListener {
      accessibilityController.stopAllAutomation("Emergency Stop stopped Accessibility automation.")
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
      event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    ) {
      val root = rootInActiveWindow ?: return
      try {
        val pkg = root.packageName?.toString().orEmpty()
        val parsed = collectSafeNodes(root)
        accessibilityController.updateWindowSnapshot(pkg, parsed)
      } finally {
        clearCachedNodeRefs()
      }
    }
  }

  override fun onInterrupt() {
    accessibilityController.stopAllAutomation("Android Accessibility session interrupted.")
  }

  override fun onDestroy() {
    clearCachedNodeRefs()
    accessibilityController.detachServiceBridge()
    super.onDestroy()
  }

  override fun getActivePackageName(): String {
    val root = rootInActiveWindow ?: return ""
    val pkg = root.packageName?.toString().orEmpty()
    runCatching { root.recycle() }
    return pkg
  }

  override fun readVisibleNodes(): List<AccessibleUiNode> {
    val root = rootInActiveWindow ?: return emptyList()
    return try {
      collectSafeNodes(root)
    } finally {
      clearCachedNodeRefs()
    }
  }

  override fun clickNode(nodeId: String, targetLabel: String): AccessibilityActionResult {
    val root = rootInActiveWindow
      ?: return AccessibilityActionResult(
        success = false,
        statusLabel = "No Active Window",
        detail = "No active screen window is currently available to inspect.",
      )
    return try {
      collectSafeNodes(root)
      val targetNode = nodeLookupMap[nodeId]
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Element Changed",
          detail = "\"$targetLabel\" moved or disappeared before tap execution.",
        )

      if (isNodeProtected(targetNode)) {
        return AccessibilityActionResult(
          success = false,
          statusLabel = "Blocked Sensitive Field",
          detail = "Password, PIN, OTP, or payment fields cannot be tapped automatically.",
        )
      }

      var clickCandidate: AccessibilityNodeInfo? = targetNode
      var clicked = false
      var depth = 0
      while (clickCandidate != null && depth < 4) {
        if (clickCandidate.isClickable && clickCandidate.isEnabled) {
          clicked = clickCandidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
          if (clicked) break
        }
        clickCandidate = clickCandidate.parent
        depth += 1
      }

      if (clicked) {
        AccessibilityActionResult(
          success = true,
          statusLabel = "Tapped",
          detail = "Tapped verified UI element \"$targetLabel\".",
        )
      } else {
        AccessibilityActionResult(
          success = false,
          statusLabel = "Not Clickable",
          detail = "\"$targetLabel\" did not expose a clickable Accessibility action.",
        )
      }
    } finally {
      clearCachedNodeRefs()
    }
  }

  override fun inputText(nodeId: String?, text: String): AccessibilityActionResult {
    val root = rootInActiveWindow
      ?: return AccessibilityActionResult(
        success = false,
        statusLabel = "No Active Window",
        detail = "No active input screen is available.",
      )
    return try {
      collectSafeNodes(root)
      val target =
        (nodeId?.let { nodeLookupMap[it] })
          ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
          ?: nodeLookupMap.values.firstOrNull { it.isEditable && !isNodeProtected(it) }

      if (target == null) {
        return AccessibilityActionResult(
          success = false,
          statusLabel = "No Input Field",
          detail = "No editable non-sensitive input box is active on the current screen.",
        )
      }
      if (isNodeProtected(target)) {
        return AccessibilityActionResult(
          success = false,
          statusLabel = "Blocked Password Field",
          detail = "Password, PIN, OTP, and payment inputs are blocked by safety policy.",
        )
      }

      val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
      }
      val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
      if (ok) {
        AccessibilityActionResult(
          success = true,
          statusLabel = "Text Entered",
          detail = "Entered text into verified UI field.",
        )
      } else {
        AccessibilityActionResult(
          success = false,
          statusLabel = "Input Rejected",
          detail = "The target app rejected programmatic text insertion into this field.",
        )
      }
    } finally {
      clearCachedNodeRefs()
    }
  }

  override fun scrollScreen(forward: Boolean): AccessibilityActionResult {
    val root = rootInActiveWindow
      ?: return AccessibilityActionResult(
        success = false,
        statusLabel = "No Active Window",
        detail = "No scrollable screen window is available.",
      )
    return try {
      collectSafeNodes(root)
      val scrollable = nodeLookupMap.values.firstOrNull { it.isScrollable }
        ?: return AccessibilityActionResult(
          success = false,
          statusLabel = "Not Scrollable",
          detail = "The current screen does not expose a scrollable container.",
        )
      val action =
        if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
      val ok = scrollable.performAction(action)
      if (ok) {
        AccessibilityActionResult(
          success = true,
          statusLabel = "Scrolled",
          detail = if (forward) "Scrolled screen forward cleanly." else "Scrolled screen back cleanly.",
        )
      } else {
        AccessibilityActionResult(
          success = false,
          statusLabel = "Scroll Boundary",
          detail = "Reached edge of scrollable content or container ignored scroll.",
        )
      }
    } finally {
      clearCachedNodeRefs()
    }
  }

  override fun performGlobal(action: AccessibilityGlobalAction): AccessibilityActionResult {
    val code =
      when (action) {
        AccessibilityGlobalAction.BACK -> GLOBAL_ACTION_BACK
        AccessibilityGlobalAction.HOME -> GLOBAL_ACTION_HOME
        AccessibilityGlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
        AccessibilityGlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
      }
    val ok = performGlobalAction(code)
    return AccessibilityActionResult(
      success = ok,
      statusLabel = if (ok) "Executed" else "Rejected",
      detail =
        if (ok) "Performed Android global action: ${action.label}."
        else "Android declined global action: ${action.label}.",
    )
  }

  private fun isNodeProtected(node: AccessibilityNodeInfo): Boolean {
    val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString() else null
    return AccessibilitySafetyFilter.isSensitiveNode(
      isPassword = node.isPassword,
      viewId = node.viewIdResourceName,
      labelText = node.text?.toString(),
      contentDesc = node.contentDescription?.toString(),
      hintText = hint,
    )
  }

  private fun collectSafeNodes(root: AccessibilityNodeInfo): List<AccessibleUiNode> {
    clearCachedNodeRefs()
    val output = mutableListOf<AccessibleUiNode>()
    val pkg = root.packageName?.toString().orEmpty()
    val rect = Rect()

    fun walk(node: AccessibilityNodeInfo, depth: Int) {
      if (depth > 18 || output.size >= 120) return
      val id = "acc_node_${output.size}"
      nodeLookupMap[id] = node

      val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.hintText?.toString().orEmpty() else ""
      val sensitive =
        AccessibilitySafetyFilter.isSensitiveNode(
          isPassword = node.isPassword,
          viewId = node.viewIdResourceName,
          labelText = node.text?.toString(),
          contentDesc = node.contentDescription?.toString(),
          hintText = hint,
        )

      node.getBoundsInScreen(rect)
      output.add(
        AccessibleUiNode(
          nodeId = id,
          text = if (sensitive) "" else node.text?.toString().orEmpty(),
          contentDescription = if (sensitive) "" else node.contentDescription?.toString().orEmpty(),
          viewIdResourceName = node.viewIdResourceName.orEmpty(),
          hintText = if (sensitive) "" else hint,
          className = node.className?.toString().orEmpty(),
          packageName = pkg,
          isClickable = node.isClickable && !sensitive,
          isEditable = node.isEditable && !sensitive,
          isScrollable = node.isScrollable,
          isPassword = node.isPassword || sensitive,
          boundsSummary = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
        )
      )

      for (i in 0 until node.childCount) {
        val child = node.getChild(i) ?: continue
        walk(child, depth + 1)
      }
    }

    walk(root, 0)
    return output
  }

  private fun clearCachedNodeRefs() {
    nodeLookupMap.values.forEach { n -> runCatching { n.recycle() } }
    nodeLookupMap.clear()
  }
}
