package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.core.overlay.FloatingAttachmentItem
import com.example.core.overlay.FloatingChatTurn
import com.example.core.voice.LiveVoiceSessionStatus
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.ui.components.FloatingChatPanelCard
import com.example.ui.components.FloatingControlBar
import com.example.ui.components.FloatingProfileQuickMenu
import com.example.ui.theme.MousomiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatingAssistantSystemTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `FloatingControlBar renders all 5 controls and triggers actions`() {
    var expandCount = 0
    var selectCount = 0
    var profileCount = 0
    var pauseCount = 0
    var closeCount = 0

    composeRule.setContent {
      MousomiTheme {
        FloatingControlBar(
          isPanelOpen = false,
          isProfileMenuOpen = false,
          isPaused = false,
          isBusyOrListening = false,
          onExpandClick = { expandCount++ },
          onScreenSelectionClick = { selectCount++ },
          onProfileClick = { profileCount++ },
          onPauseToggleClick = { pauseCount++ },
          onCloseClick = { closeCount++ },
        )
      }
    }

    composeRule.onNodeWithTag("floating_control_bar").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_bar_expand").performClick()
    composeRule.onNodeWithTag("floating_bar_screen_select").performClick()
    composeRule.onNodeWithTag("floating_bar_profile").performClick()
    composeRule.onNodeWithTag("floating_bar_pause").performClick()
    composeRule.onNodeWithTag("floating_bar_close").performClick()

    assertEquals(1, expandCount)
    assertEquals(1, selectCount)
    assertEquals(1, profileCount)
    assertEquals(1, pauseCount)
    assertEquals(1, closeCount)
  }

  @Test
  fun `FloatingProfileQuickMenu renders edge lighting sliders and actions`() {
    var updatedCfg: EdgeLightConfig? = null
    var studioOpened = false

    composeRule.setContent {
      MousomiTheme {
        FloatingProfileQuickMenu(
          activeModelLabel = "Gemini 2.5 Flash",
          edgeLightConfig = EdgeLightConfig(),
          onUpdateEdgeLight = { updatedCfg = it },
          onOpenControlCenter = { studioOpened = true },
          onOpenSettings = {},
          onClearHistory = {},
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("floating_profile_menu").assertIsDisplayed()
    composeRule.onNodeWithTag("profile_brightness_slider").assertIsDisplayed()
    composeRule.onNodeWithTag("profile_width_slider").assertIsDisplayed()
    composeRule.onNodeWithTag("profile_speed_slider").assertIsDisplayed()

    composeRule.onNodeWithTag("profile_toggle_battery_saver").performClick()
    assertEquals(true, updatedCfg?.batterySaverMode)

    composeRule.onNodeWithTag("profile_open_studio").performClick()
    assertTrue(studioOpened)
  }

  @Test
  fun `FloatingChatPanelCard renders history attachment preview and handles composer actions`() {
    var attachClicked = false
    var micClicked = false
    var minimizeClicked = false
    var sentPrompt: String? = null
    var previewed: FloatingAttachmentItem? = null
    var removedUri: String? = null
    var typedPrompt = ""

    val attachment =
      FloatingAttachmentItem(
        id = "att_1",
        uriString = "file:///tmp/sample_crop.jpg",
        label = "Screen region",
        isFromScreenSelection = true,
      )

    composeRule.setContent {
      MousomiTheme {
        FloatingChatPanelCard(
          promptText = "Summarize this card",
          turns =
            listOf(
              FloatingChatTurn(isUser = true, text = "Previous question"),
              FloatingChatTurn(isUser = false, text = "Previous answer", modelLabel = "Gemini 2.5"),
            ),
          pendingAttachments = listOf(attachment),
          previewingAttachment = null,
          liveUserTranscript = "",
          activeModelLabel = "Gemini 2.5",
          statusBanner = null,
          responseState = AiResponseState.Idle,
          voiceState = VoiceState.Idle,
          liveStatus = LiveVoiceSessionStatus.Disconnected,
          isPaused = false,
          maxCardHeightDp = 420,
          onPromptChanged = { typedPrompt = it },
          onSendPrompt = { sentPrompt = it },
          onAttachImage = { attachClicked = true },
          onStartScreenSelection = {},
          onRemoveAttachment = { removedUri = it },
          onOpenAttachmentPreview = { previewed = it },
          onToggleVoice = { micClicked = true },
          onTogglePause = {},
          onEndLive = {},
          onClearHistory = {},
          onExpandApp = {},
          onMinimizePanel = { minimizeClicked = true },
        )
      }
    }

    composeRule.onNodeWithTag("floating_chat_panel").assertIsDisplayed()
    composeRule.onNodeWithText("Previous question").assertIsDisplayed()
    composeRule.onNodeWithText("Previous answer").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_preview_attachment_0").performClick()
    assertEquals(attachment, previewed)

    composeRule.onNodeWithTag("floating_remove_attachment_0").performClick()
    assertEquals(attachment.uriString, removedUri)

    composeRule.onNodeWithTag("floating_attach_image_button").performClick()
    assertTrue(attachClicked)

    composeRule.onNodeWithTag("floating_mic_button").performClick()
    assertTrue(micClicked)

    composeRule.onNodeWithTag("floating_prompt_input").performTextInput(" now")
    assertTrue(typedPrompt.contains("now"))

    composeRule.onNodeWithTag("floating_send_button").performClick()
    assertEquals("Summarize this card", sentPrompt)

    composeRule.onNodeWithTag("floating_panel_minimize").performClick()
    assertTrue(minimizeClicked)
  }
}
