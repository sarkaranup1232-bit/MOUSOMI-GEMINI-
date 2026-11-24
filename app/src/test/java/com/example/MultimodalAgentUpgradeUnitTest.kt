package com.example

import com.example.core.accessibility.AccessibilityActionResult
import com.example.core.accessibility.AccessibilityAgentController
import com.example.core.accessibility.AccessibilityGlobalAction
import com.example.core.accessibility.AccessibilitySafetyFilter
import com.example.core.accessibility.AccessibleUiNode
import com.example.core.agent.AgentTaskPlanner
import com.example.core.agent.AgentToolDomain
import com.example.core.agent.AgentToolRegistry
import com.example.core.device.ContactLookupOutcome
import com.example.core.device.ContactResolver
import com.example.core.device.PendingPhoneActionType
import com.example.core.device.PhoneActionController
import com.example.core.device.ResolvedContact
import com.example.core.device.SocialIntegrationManager
import com.example.core.device.SocialPlatform
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropCoordinateMapper
import com.example.core.screen.CropHandle
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.ScreenCaptureMetadata
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionToolMode
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalAgentUpgradeUnitTest {

  private val geometryEngine =
    SelectionGeometryEngine(
      freehandProcessor = FreehandPathProcessor(),
      circleProcessor = CircleSelectionProcessor(),
      rectangleProcessor = RectangleSelectionProcessor(),
    )

  @Test
  fun `crop coordinate mapper and selection geometry compute display-fit boxes, contours, and handle adjustments`() {
    val mapper = CropCoordinateMapper()
    val normBox =
      mapper.mapDisplayFitRectToNorm(
        containerWidthPx = 1000f,
        containerHeightPx = 1000f,
        bitmapWidth = 1000,
        bitmapHeight = 500,
      )
    assertEquals(0f, normBox.left, 0.01f)
    assertEquals(1f, normBox.right, 0.01f)
    assertEquals(0.25f, normBox.top, 0.01f)
    assertEquals(0.75f, normBox.bottom, 0.01f)

    val meta =
      ScreenCaptureMetadata(
        bitmapWidth = 1080,
        bitmapHeight = 2400,
        overlayWidthPx = 1080,
        overlayHeightPx = 2280,
        overlayOffsetY = 80,
      )
    assertEquals(2280, meta.overlayHeightPx)

    val rectBounds =
      geometryEngine.processStroke(
        listOf(NormPoint(0.2f, 0.2f), NormPoint(0.8f, 0.6f)),
        SelectionToolMode.RECTANGLE,
      )
    assertNotNull(rectBounds)
    assertEquals(DetectedRegionShape.RECTANGLE, rectBounds!!.detectedShape)
    val expanded = geometryEngine.adjustSnappedResult(rectBounds, CropHandle.TOP_LEFT, -0.05f, -0.05f)
    assertTrue(expanded.boundingBox.left < rectBounds.boundingBox.left)
    assertTrue(expanded.boundingBox.top < rectBounds.boundingBox.top)

    val freehand =
      geometryEngine.processStroke(
        listOf(
          NormPoint(0.25f, 0.25f),
          NormPoint(0.60f, 0.20f),
          NormPoint(0.70f, 0.55f),
          NormPoint(0.35f, 0.60f),
          NormPoint(0.25f, 0.26f),
        ),
        SelectionToolMode.SMART_LOOP,
      )
    assertNotNull(freehand)
    assertTrue(freehand!!.boundingBox.isValid())
    assertTrue(freehand.simplifiedContour.size >= 3)
  }

  @Test
  fun `contact resolver classifies direct numbers, unique matches, and ambiguous matches without leaking PII`() {
    assertTrue(ContactResolver.looksLikeDirectPhoneNumber("+91 98765-43210"))
    assertTrue(ContactResolver.looksLikeDirectPhoneNumber("100"))
    assertFalse(ContactResolver.looksLikeDirectPhoneNumber("Alice Johnson"))
    assertEquals("9876543210", ContactResolver.normalizeDigitsForDedupe("+91 98765 43210"))

    val single =
      ContactResolver.classifyLookupMatches(
        query = "Alice",
        matches =
          listOf(
            ResolvedContact("1", "Alice Roy", "+919876543210", isPrimary = true)
          ),
      )
    assertTrue(single is ContactLookupOutcome.UniqueMatch)
    assertEquals("+919876543210", (single as ContactLookupOutcome.UniqueMatch).contact.phoneNumber)

    val ambiguous =
      ContactResolver.classifyLookupMatches(
        query = "Bob",
        matches =
          listOf(
            ResolvedContact("2", "Bob Das", "+919111111111"),
            ResolvedContact("3", "Bob Sen", "+919222222222"),
          ),
      )
    assertTrue(ambiguous is ContactLookupOutcome.AmbiguousMatches)
    assertEquals(2, (ambiguous as ContactLookupOutcome.AmbiguousMatches).candidates.size)

    val empty = ContactResolver.classifyLookupMatches("Unknown", emptyList())
    assertTrue(empty is ContactLookupOutcome.NotFound)
  }

  @Test
  fun `phone action controller parses both numeric and named dial and sms commands`() {
    val dialNum = PhoneActionController.tryParseConfirmationCommand("Call +1 555-0199")
    assertNotNull(dialNum)
    assertEquals(PendingPhoneActionType.DIAL_PHONE, dialNum!!.type)
    assertEquals("+15550199", dialNum.targetNumber)

    val dialNamed = PhoneActionController.tryParseConfirmationCommand("Call Alice")
    assertNotNull(dialNamed)
    assertEquals(PendingPhoneActionType.DIAL_PHONE, dialNamed!!.type)
    assertEquals("Alice", dialNamed.displayLabel)
    assertEquals("", dialNamed.targetNumber)

    val smsNamed = PhoneActionController.tryParseConfirmationCommand("Send SMS to Bob saying Running 10 mins late")
    assertNotNull(smsNamed)
    assertEquals(PendingPhoneActionType.SEND_SMS, smsNamed!!.type)
    assertEquals("Bob", smsNamed.displayLabel)
    assertEquals("Running 10 mins late", smsNamed.messageBody)
  }

  @Test
  fun `social integration manager parses whatsapp named contacts, instagram, facebook, and youtube drafts`() {
    val waNamed =
      SocialIntegrationManager.tryParseSocialCommand("Send a WhatsApp message to Ananya saying See you at 6 PM")
    assertNotNull(waNamed)
    assertEquals(SocialPlatform.WHATSAPP, waNamed!!.platform)
    assertEquals("Ananya", waNamed.recipientOrHandle)
    assertEquals("See you at 6 PM", waNamed.captionOrMessage)

    val waOnPattern = SocialIntegrationManager.tryParseSocialCommand("Message Rahul on WhatsApp saying Check the doc")
    assertNotNull(waOnPattern)
    assertEquals(SocialPlatform.WHATSAPP, waOnPattern!!.platform)
    assertEquals("Rahul", waOnPattern.recipientOrHandle)

    val ig = SocialIntegrationManager.tryParseSocialCommand("Share to Instagram: Sunset by the river #kolkata")
    assertNotNull(ig)
    assertEquals(SocialPlatform.INSTAGRAM, ig!!.platform)
    assertTrue(ig.captionOrMessage.contains("Sunset"))

    val yt = SocialIntegrationManager.tryParseSocialCommand("Upload to YouTube: Mousomi AI Product Demo")
    assertNotNull(yt)
    assertEquals(SocialPlatform.YOUTUBE, yt!!.platform)
    assertTrue(yt.captionOrMessage.contains("Product Demo"))

    val draft = SocialIntegrationManager.generateSocialDraftTemplate(SocialPlatform.INSTAGRAM, "Morning Coffee")
    assertTrue(draft.contains("Morning Coffee"))
  }

  @Test
  fun `notification read aloud respects ringer mode, mic pause, exclusions, privacy mode, and OTP blocking`() {
    val engine = NotificationIntelligenceEngine(KeystoreCryptoManager())
    engine.updateConfig(
      NotificationIntelligenceConfig(
        enabled = true,
        readAloudEnabled = true,
        readAloudContentEnabled = true,
        readAloudPaused = false,
        redactSensitiveCodes = true,
        excludedPackages = setOf("com.blocked.app"),
      )
    )

    val normalMsg =
      NotificationSummaryRecord(
        id = "n1",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        title = "Sayan",
        summary = "Are we meeting at 5?",
        importanceLevel = "Normal",
        receivedAtMs = 1_000L,
      )
    val spoken = engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 2, isMicCurrentlyListening = false)
    assertEquals("WhatsApp from Sayan: Are we meeting at 5?", spoken)

    // Silent / Vibrate must suppress speech
    assertNull(engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 0, isMicCurrentlyListening = false))
    assertNull(engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 1, isMicCurrentlyListening = false))

    // Active STT listening suppresses speech
    assertNull(engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 2, isMicCurrentlyListening = true))

    // Excluded package suppressed
    val excludedMsg = normalMsg.copy(packageName = "com.blocked.app", appLabel = "Blocked")
    assertNull(engine.buildReadAloudAnnouncement(excludedMsg, ringerMode = 2, isMicCurrentlyListening = false))

    // OTP redacted even in read-aloud content mode
    val otpMsg =
      normalMsg.copy(
        packageName = "com.bank.sms",
        appLabel = "Messages",
        title = "Bank",
        summary = "Your OTP verification code is 482910. Do not share.",
      )
    val redacted = engine.buildReadAloudAnnouncement(otpMsg, ringerMode = 2, isMicCurrentlyListening = false)
    assertEquals("Sensitive message from Messages withheld for security.", redacted)

    // Sender-only privacy mode hides body text
    engine.updateConfig(engine.configState.value.copy(readAloudContentEnabled = false))
    val senderOnly = engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 2, isMicCurrentlyListening = false)
    assertEquals("New WhatsApp message from Sayan.", senderOnly)

    // Paused state suppresses read aloud
    engine.setReadAloudPaused(true)
    assertNull(engine.buildReadAloudAnnouncement(normalMsg, ringerMode = 2, isMicCurrentlyListening = false))

    // Posting notification & clearing on disable
    engine.onNotificationPosted("com.whatsapp", "WhatsApp", "Sayan", "Hello there")
    assertEquals(1, engine.summaries.value.size)
    engine.updateConfig(
      NotificationIntelligenceConfig(
        enabled = false,
        readAloudEnabled = false,
      )
    )
    assertEquals(0, engine.summaries.value.size)
  }

  @Test
  fun `accessibility safety filter and controller block sensitive nodes and gate risky taps behind confirmation`() {
    assertTrue(AccessibilitySafetyFilter.isSensitiveNode(isPassword = true, labelText = "Input"))
    assertTrue(AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, viewId = "et_otp_code"))
    assertTrue(AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, hintText = "Enter Card CVV"))
    assertFalse(AccessibilitySafetyFilter.isSensitiveNode(isPassword = false, labelText = "Search products"))

    assertTrue(AccessibilitySafetyFilter.requiresExplicitConfirmation("Send"))
    assertTrue(AccessibilitySafetyFilter.requiresExplicitConfirmation("Pay ₹499"))
    assertTrue(AccessibilitySafetyFilter.requiresExplicitConfirmation("Delete Account"))
    assertFalse(AccessibilitySafetyFilter.requiresExplicitConfirmation("Open Settings"))

    val nodes =
      listOf(
        AccessibleUiNode("n_pass", text = "Password", contentDescription = "", isPassword = true, isEditable = true),
        AccessibleUiNode("n_search", text = "Search", contentDescription = "Search box", isClickable = true),
        AccessibleUiNode("n_send", text = "Send", contentDescription = "Send message", isClickable = true),
      )
    // Sensitive node excluded even if queried directly
    assertNull(AccessibilitySafetyFilter.findBestMatchingNode(nodes, "Password"))
    assertEquals("n_search", AccessibilitySafetyFilter.findBestMatchingNode(nodes, "Search")?.nodeId)

    val controller = AccessibilityAgentController()
    val clickedIds = mutableListOf<String>()
    controller.attachServiceBridge(
      object : AccessibilityAgentController.AccessibilityBridge {
        override fun getActivePackageName(): String = "com.example.demo"
        override fun readVisibleNodes(): List<AccessibleUiNode> = nodes
        override fun clickNode(nodeId: String, targetLabel: String): AccessibilityActionResult {
          clickedIds.add(nodeId)
          return AccessibilityActionResult(true, "Clicked", "Clicked $targetLabel")
        }
        override fun inputText(nodeId: String?, text: String): AccessibilityActionResult =
          AccessibilityActionResult(true, "Entered Text", "Typed")
        override fun scrollScreen(forward: Boolean): AccessibilityActionResult =
          AccessibilityActionResult(true, "Scrolled", "Scrolled")
        override fun performGlobal(action: AccessibilityGlobalAction): AccessibilityActionResult =
          AccessibilityActionResult(true, action.label, "Performed ${action.label}")
      }
    )

    // Password input refused
    val blockedInput = controller.performTextInput("Password", "secret123")
    assertFalse(blockedInput.success)

    // Clicking "Send" without confirmation pauses & requests confirmation
    val needsConfirm = controller.performNodeClick("Send", confirmedSensitiveAction = false)
    assertTrue(needsConfirm.requiresConfirmation)
    assertEquals("Send", needsConfirm.targetLabelToConfirm)
    assertTrue(clickedIds.isEmpty())

    // Confirming allows node click
    val confirmedClick = controller.performNodeClick("Send", confirmedSensitiveAction = true)
    assertTrue(confirmedClick.success)
    assertEquals(listOf("n_send"), clickedIds)

    controller.stopAllAutomation()
    assertFalse(controller.state.value.isWorkflowRunning)
  }

  @Test
  fun `agent task planner parses notification controls, timers, and registry covers all domains`() {
    val pause = AgentTaskPlanner.parseNotificationControl("Pause notification read aloud")
    assertNotNull(pause)
    assertEquals(true, pause!!.setPaused)

    val resume = AgentTaskPlanner.parseNotificationControl("Resume notifications")
    assertNotNull(resume)
    assertEquals(false, resume!!.setPaused)
    assertEquals(true, resume.setReadAloudEnabled)

    val timer = AgentTaskPlanner.parseTimerRequest("Set a timer for 5 minutes for tea")
    assertNotNull(timer)
    assertEquals(300, timer!!.durationSeconds)
    assertEquals("tea", timer.label)

    val domains = AgentToolRegistry.allTools.map { it.domain }.toSet()
    assertTrue(AgentToolDomain.PHONE_CONTROL in domains)
    assertTrue(AgentToolDomain.CONTACTS_AND_MESSAGING in domains)
    assertTrue(AgentToolDomain.SOCIAL_MEDIA in domains)
    assertTrue(AgentToolDomain.NOTIFICATION_ASSISTANT in domains)
    assertTrue(AgentToolDomain.ACCESSIBILITY_UI in domains)
  }
}
