package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.image.ImageProcessor
import com.example.core.permissions.EmergencyStopController
import com.example.core.screen.AttachedVisualItem
import com.example.core.screen.CircleSelectionProcessor
import com.example.core.screen.CropAspectPreset
import com.example.core.screen.CropCoordinateMapper
import com.example.core.screen.CropHandle
import com.example.core.screen.DetectedRegionShape
import com.example.core.screen.FreehandPathProcessor
import com.example.core.screen.ImageAttachmentManager
import com.example.core.screen.ImageEditState
import com.example.core.screen.ImageEditingController
import com.example.core.screen.MediaProjectionManager
import com.example.core.screen.RectangleSelectionProcessor
import com.example.core.screen.ScreenCaptureMetadata
import com.example.core.screen.ScreenSelectionManager
import com.example.core.screen.ScreenSelectionUiState
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.screen.ScreenshotCaptureManager
import com.example.core.screen.SelectionGeometryEngine
import com.example.core.screen.SelectionGeometryResult
import com.example.core.screen.SelectionStage
import com.example.core.screen.SelectionToolMode
import com.example.core.screen.SmartCropEngine
import com.example.core.screen.VisualQueryProcessor
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.domain.model.NeonComposerState
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NormPoint
import com.example.domain.model.VoicePreferences
import com.example.ui.components.CompactImageEditorSheet
import com.example.ui.components.NeonChatComposer
import com.example.ui.components.ScreenSelectionOverlay
import com.example.ui.model.AttachmentPreview
import com.example.ui.model.InputMode
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenSelectionSystemTest {

  @get:Rule val composeRule = createComposeRule()

  private fun createGeometryEngine(): SelectionGeometryEngine =
    SelectionGeometryEngine(
      freehandProcessor = FreehandPathProcessor(),
      circleProcessor = CircleSelectionProcessor(),
      rectangleProcessor = RectangleSelectionProcessor(),
    )

  private fun createQuadrantSampleBitmapFile(context: Context): File {
    val w = 400
    val h = 600
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
      for (x in 0 until w) {
        pixels[y * w + x] =
          when {
            y < 300 -> Color.BLUE
            x < 200 -> Color.RED
            else -> Color.GREEN
          }
      }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    val file = File(context.cacheDir, "sel_full_test_${System.nanoTime()}.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    return file
  }

  @Test
  fun `FreehandPathProcessor and SelectionGeometryEngine recognize closed loops and snap cleanly`() {
    val engine = createGeometryEngine()
    val circleStroke =
      (0..24).map { i ->
        val angle = (i / 24.0) * Math.PI * 2.0
        NormPoint(
          x = (0.5f + 0.20f * cos(angle).toFloat()),
          y = (0.5f + 0.20f * sin(angle).toFloat()),
        )
      }

    val result = engine.processStroke(circleStroke, SelectionToolMode.SMART_LOOP)
    assertNotNull(result)
    result!!
    assertTrue(
      result.detectedShape == DetectedRegionShape.CIRCLE ||
        result.detectedShape == DetectedRegionShape.OVAL ||
        result.detectedShape == DetectedRegionShape.FREEHAND_LOOP
    )
    assertTrue(result.boundingBox.left in 0.25f..0.35f)
    assertTrue(result.boundingBox.right in 0.65f..0.75f)
    assertTrue(result.boundingBox.top in 0.25f..0.35f)
    assertTrue(result.boundingBox.bottom in 0.65f..0.75f)
  }

  @Test
  fun `RectangleSelectionProcessor handles boundary adjustments, clamping, and aspect ratios`() {
    val engine = createGeometryEngine()
    val rectStroke =
      listOf(
        NormPoint(0.2f, 0.2f),
        NormPoint(0.8f, 0.2f),
        NormPoint(0.8f, 0.7f),
        NormPoint(0.2f, 0.7f),
        NormPoint(0.2f, 0.2f),
      )

    val snapped = engine.processStroke(rectStroke, SelectionToolMode.RECTANGLE)
    assertNotNull(snapped)
    snapped!!
    assertEquals(DetectedRegionShape.RECTANGLE, snapped.detectedShape)

    assertEquals(
      CropHandle.TOP_LEFT,
      engine.hitTestHandle(NormPoint(snapped.boundingBox.left, snapped.boundingBox.top), snapped.boundingBox),
    )
    assertEquals(
      CropHandle.MOVE,
      engine.hitTestHandle(NormPoint(0.5f, 0.45f), snapped.boundingBox),
    )

    val expanded =
      engine.rectangleProcessor.adjustBox(
        box = snapped.boundingBox,
        handle = CropHandle.BOTTOM_RIGHT,
        deltaXNorm = 0.5f,
        deltaYNorm = 0.5f,
      )
    assertTrue(expanded.right <= 1.0f)
    assertTrue(expanded.bottom <= 1.0f)

    val locked16x9 = engine.rectangleProcessor.enforceAspectRatio(snapped.boundingBox, 16f / 9f)
    val ratio = (locked16x9.right - locked16x9.left) / (locked16x9.bottom - locked16x9.top)
    assertTrue(kotlin.math.abs(ratio - (16f / 9f)) < 0.08f)
  }

  @Test
  fun `CropCoordinateMapper and SmartCropEngine extract accurate cropped sub-region without black borders`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val cache = TempMediaCacheManager(context)
      val mapper = CropCoordinateMapper()
      val engine = SmartCropEngine(mapper, cache)
      val sourceFile = createQuadrantSampleBitmapFile(context)

      val mappedRect =
        mapper.mapToBitmapRect(
          normBox = NormBoundingBox(0.55f, 0.55f, 0.95f, 0.95f),
          metadata = ScreenCaptureMetadata(400, 600, 400, 600),
        )
      assertTrue(mappedRect.left >= 200 && mappedRect.top >= 300)

      val cropResult =
        engine.cropFromFile(
          sourceFilePath = sourceFile.absolutePath,
          normBox = NormBoundingBox(0.55f, 0.55f, 0.95f, 0.95f),
          metadata = ScreenCaptureMetadata(400, 600, 400, 600),
        )
      assertTrue(cropResult.isSuccess)
      val out = cropResult.getOrThrow()
      assertTrue(out.file.exists())
      val croppedBmp = BitmapFactory.decodeFile(out.file.absolutePath)
      assertNotNull(croppedBmp)
      val centerPixel = croppedBmp.getPixel(croppedBmp.width / 2, croppedBmp.height / 2)
      assertTrue(Color.green(centerPixel) > 180)
      assertTrue(Color.red(centerPixel) < 80)
      assertTrue(Color.blue(centerPixel) < 80)
      croppedBmp.recycle()

      val rotatedCrop =
        engine.cropFromFile(
          sourceFilePath = sourceFile.absolutePath,
          normBox = NormBoundingBox(0.1f, 0.1f, 0.9f, 0.4f),
          rotationDegrees = 90,
        ).getOrThrow()
      assertTrue(rotatedCrop.width < rotatedCrop.height)

      cache.cleanupAllTempFiles()
      sourceFile.delete()
    }

  @Test
  fun `ImageEditingController updates crop non-destructively from original source frame and supports Cancel vs Confirm`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val cache = TempMediaCacheManager(context)
      val sourceFile = createQuadrantSampleBitmapFile(context)
      val geometryEngine = createGeometryEngine()
      val smartCropEngine = SmartCropEngine(CropCoordinateMapper(), cache)
      val attachMgr = ImageAttachmentManager(cache)
      val initialCropped =
        smartCropEngine.cropFromFile(
          sourceFilePath = sourceFile.absolutePath,
          normBox = NormBoundingBox(0.1f, 0.1f, 0.5f, 0.4f),
        ).getOrThrow()

      val attached =
        attachMgr.attachCroppedVisual(
          croppedFile = initialCropped.file,
          sourceFrameFilePath = sourceFile.absolutePath,
          captureMetadata = ScreenCaptureMetadata(400, 600, 400, 600),
          normCropRect = NormBoundingBox(0.1f, 0.1f, 0.5f, 0.4f),
          detectedShape = DetectedRegionShape.RECTANGLE,
        )

      val editCtrl = ImageEditingController(geometryEngine, smartCropEngine, attachMgr)
      assertTrue(editCtrl.beginEditing(attached))
      assertNotNull(editCtrl.editState.value)

      editCtrl.adjustHandle(CropHandle.BOTTOM_RIGHT, 0.2f, 0.2f)
      editCtrl.cancelEdit()
      assertNull(editCtrl.editState.value)
      assertEquals(attached.croppedUriString, attachMgr.latestEditableVisual()?.croppedUriString)

      assertTrue(editCtrl.beginEditing(attached))
      editCtrl.setAspectPreset(CropAspectPreset.SQUARE)
      editCtrl.rotate90()
      assertEquals(90, editCtrl.editState.value?.rotationDegrees)
      val confirmRes = editCtrl.confirmEdit()
      assertTrue(confirmRes.isSuccess)
      val updated = confirmRes.getOrThrow()
      assertEquals(90, updated.rotationDegrees)
      assertEquals(CropAspectPreset.SQUARE, updated.aspectPreset)
      assertNull(editCtrl.editState.value)

      cache.cleanupAllTempFiles()
      sourceFile.delete()
    }

  @Test
  fun `VisualQueryProcessor handles image-only empty prompt and image plus typed prompt queries`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val cache = TempMediaCacheManager(context)
      val attachMgr = ImageAttachmentManager(cache)
      val smartCropEngine = SmartCropEngine(CropCoordinateMapper(), cache)
      val sourceFile = createQuadrantSampleBitmapFile(context)
      val cropped =
        smartCropEngine.cropFromFile(
          sourceFilePath = sourceFile.absolutePath,
          normBox = NormBoundingBox(0.1f, 0.55f, 0.45f, 0.9f),
        ).getOrThrow()

      val vis =
        attachMgr.attachCroppedVisual(
          croppedFile = cropped.file,
          sourceFrameFilePath = sourceFile.absolutePath,
          captureMetadata = null,
          normCropRect = NormBoundingBox(0.1f, 0.55f, 0.45f, 0.9f),
          detectedShape = DetectedRegionShape.OVAL,
        )

      val processor = VisualQueryProcessor(ImageProcessor(context), attachMgr)
      val attachments = listOf(AttachmentPreview.PickedPhotoUri(vis.croppedUriString))

      val emptyPromptQuery = processor.prepareQuery("", attachments)
      assertEquals("Analyze selected screen region", emptyPromptQuery.userDisplayPrompt)
      assertTrue(emptyPromptQuery.modelPrompt.contains("extract any visible text"))
      assertEquals(1, emptyPromptQuery.payloads.size)

      val customQuery = processor.prepareQuery("What error code is this?", attachments)
      assertEquals("What error code is this?", customQuery.userDisplayPrompt)
      assertEquals("What error code is this?", customQuery.modelPrompt)
      assertEquals(1, customQuery.payloads.size)

      cache.cleanupAllTempFiles()
      sourceFile.delete()
    }

  @Test
  fun `ScreenSelectionManager and EmergencyStopController cleanly tear down active selection state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = TempMediaCacheManager(context)
    val geometryEngine = createGeometryEngine()
    val smartCrop = SmartCropEngine(CropCoordinateMapper(), cache)
    val projectionMgr = MediaProjectionManager(context)
    val shareMgr = ScreenShareSessionManager(cache)
    val attachMgr = ImageAttachmentManager(cache)
    val capMgr = ScreenshotCaptureManager(projectionMgr, shareMgr, cache)
    val selectionMgr =
      ScreenSelectionManager(geometryEngine, smartCrop, capMgr, attachMgr, projectionMgr)

    selectionMgr.onAuthorizedFrameReady(null, null, useSystemOverlay = false)
    assertTrue(selectionMgr.state.value.isActive)
    selectionMgr.onStrokeStart(NormPoint(0.2f, 0.2f))
    selectionMgr.onStrokeMove(NormPoint(0.8f, 0.2f))
    selectionMgr.onStrokeMove(NormPoint(0.8f, 0.7f))
    selectionMgr.onStrokeMove(NormPoint(0.2f, 0.7f))
    selectionMgr.onStrokeEnd()
    assertEquals(SelectionStage.SNAPPED_HIGHLIGHT, selectionMgr.state.value.stage)

    selectionMgr.undo()
    assertEquals(SelectionStage.READY_TO_DRAW, selectionMgr.state.value.stage)

    val dummyStt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stopListening() = Unit

        override fun release() = Unit
      }
    val dummyTts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) = Unit

        override fun stop() = Unit

        override fun release() = Unit
      }

    val stopCtrl =
      EmergencyStopController(context, VoiceController(dummyStt, dummyTts), shareMgr, cache)
    stopCtrl.registerStopListener { selectionMgr.stopAndCleanup() }
    stopCtrl.triggerEmergencyStop("Emergency stop check")
    assertFalse(selectionMgr.state.value.isActive)
    cache.cleanupAllTempFiles()
  }

  @Test
  fun `NeonChatComposer displays embedded thumbnail, X remove badge, and floating Edit images pill`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = createQuadrantSampleBitmapFile(context)
    val uriStr = Uri.fromFile(imgFile).toString()
    val visualItem =
      AttachedVisualItem(
        id = "v_1",
        croppedFilePath = imgFile.absolutePath,
        croppedUriString = uriStr,
        sourceFrameFilePath = imgFile.absolutePath,
        captureMetadata = null,
        normCropRect = NormBoundingBox(0.1f, 0.1f, 0.8f, 0.8f),
        detectedShape = DetectedRegionShape.RECTANGLE,
      )

    var removedClicked = false
    var editImagesClicked = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        NeonChatComposer(
          composerState = NeonComposerState.FOCUSED,
          glowIntensity = 0.9f,
          reducedMotion = false,
          inputMode = InputMode.CHAT,
          inputText = "",
          pendingAttachments = listOf(AttachmentPreview.PickedPhotoUri(uriStr)),
          hasImageContext = true,
          activeEditTarget = null,
          isGenerating = false,
          isEditingMessage = false,
          onSelectMode = {},
          onInputChanged = {},
          onOpenAttachments = {},
          onOpenCameraCapture = {},
          onRemoveAttachment = { removedClicked = true },
          onCancelEditImageTarget = {},
          onCancelEditing = {},
          onVoiceClick = {},
          onLiveClick = {},
          onSendClick = {},
          onStopGeneration = {},
          onContextualAction = {},
          attachedVisualsByUri = mapOf(uriStr to visualItem),
          onEditImagesClick = { editImagesClicked = true },
        )
      }
    }

    composeRule.onNodeWithTag("composer_edit_images_pill").assertIsDisplayed()
    composeRule.onNodeWithText("Edit images").assertIsDisplayed()
    composeRule.onNodeWithTag("composer_attachment_strip").assertIsDisplayed()
    composeRule.onNodeWithTag("composer_thumbnail_0").assertIsDisplayed()
    composeRule.onNodeWithTag("attachment_remove_button_0").assertIsDisplayed()
    composeRule.onNodeWithTag("send_message_button").assertIsDisplayed()

    composeRule.onNodeWithTag("composer_edit_images_pill").performClick()
    assertTrue(editImagesClicked)

    composeRule.onNodeWithTag("attachment_remove_button_0").performClick()
    assertTrue(removedClicked)

    imgFile.delete()
  }

  @Test
  fun `ScreenSelectionOverlay and CompactImageEditorSheet render tools and respond to interactions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = createQuadrantSampleBitmapFile(context)
    val geometryEngine = createGeometryEngine()
    var selectedTool: SelectionToolMode? = null
    var confirmCalled = false

    val selState =
      ScreenSelectionUiState(
        isActive = true,
        stage = SelectionStage.SNAPPED_HIGHLIGHT,
        toolMode = SelectionToolMode.SMART_LOOP,
        snappedResult =
          SelectionGeometryResult(
            boundingBox = NormBoundingBox(0.2f, 0.2f, 0.8f, 0.6f),
            detectedShape = DetectedRegionShape.RECTANGLE,
            cornerRadiusFraction = 0.08f,
            confidence = 0.96f,
          ),
        sourceFrameFilePath = imgFile.absolutePath,
      )

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ScreenSelectionOverlay(
          state = selState,
          geometryEngine = geometryEngine,
          onSelectTool = { selectedTool = it },
          onStrokeStart = {},
          onStrokeMove = {},
          onStrokeEnd = {},
          onHandleDrag = { _, _, _ -> },
          onUndo = {},
          onClear = {},
          onCancel = {},
          onConfirmSelection = { _, _ -> confirmCalled = true },
          reducedMotion = true,
        )
      }
    }

    composeRule.onNodeWithTag("screen_selection_overlay").assertIsDisplayed()
    composeRule.onNodeWithTag("selection_toolbar").assertIsDisplayed()
    composeRule.onNodeWithTag("selection_tool_CIRCLE").performClick()
    assertEquals(SelectionToolMode.CIRCLE, selectedTool)
    composeRule.onNodeWithTag("selection_confirm_button").performClick()
    assertTrue(confirmCalled)

    imgFile.delete()
  }

  @Test
  fun `CompactImageEditorSheet displays crop controls, presets, Rotate, Retake, Cancel, and Confirm`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = createQuadrantSampleBitmapFile(context)
    var rotated = false
    var presetPicked: CropAspectPreset? = null
    var retakeClicked = false
    var confirmed = false

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        CompactImageEditorSheet(
          editState =
            ImageEditState(
              attachmentId = "vis_1",
              sourceFrameFilePath = imgFile.absolutePath,
              originalNormCropRect = NormBoundingBox(0.15f, 0.15f, 0.85f, 0.85f),
              currentNormCropRect = NormBoundingBox(0.15f, 0.15f, 0.85f, 0.85f),
            ),
          onAdjustHandle = { _, _, _ -> },
          onSelectAspectPreset = { presetPicked = it },
          onRotate90 = { rotated = true },
          onRetakeSelection = { retakeClicked = true },
          onResetCrop = {},
          onCancel = {},
          onConfirm = { confirmed = true },
        )
      }
    }

    composeRule.onNodeWithTag("compact_image_editor_sheet").assertIsDisplayed()
    composeRule.onNodeWithTag("editor_aspect_SQUARE").performClick()
    assertEquals(CropAspectPreset.SQUARE, presetPicked)
    composeRule.onNodeWithTag("editor_rotate_button").performClick()
    assertTrue(rotated)
    composeRule.onNodeWithTag("editor_retake_button").performClick()
    assertTrue(retakeClicked)
    composeRule.onNodeWithTag("editor_confirm_button").performClick()
    assertTrue(confirmed)

    imgFile.delete()
  }
}
