package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.vision.LensFrameAnalyzer
import com.example.domain.model.LensAnalysisResult
import com.example.domain.model.LensAutoScanState
import com.example.domain.model.LensDetectedBox
import com.example.domain.model.LensMode
import com.example.ui.components.GeminiFourPointSparkle
import com.example.ui.components.GeminiSmokyAmbientBackground
import com.example.ui.components.LensVisualResponsePanel
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonViolet
import com.example.ui.viewmodel.LensUiState
import com.example.ui.viewmodel.LensViewModel
import java.util.concurrent.Executors

@Composable
fun MousomiLensScreen(
  viewModel: LensViewModel,
  onContinueInChat: (LensAnalysisResult) -> Unit,
  onBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      hasCameraPermission = granted
    }

  val galleryPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.analyzePickedUri(uri)
      } else if (uiState.isPickingCompareSecondImage) {
        viewModel.onCancelCompareCaptureMode()
      }
    }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  BackHandler {
    when {
      uiState.isPickingCompareSecondImage -> viewModel.onCancelCompareCaptureMode()
      uiState.isShowingResults -> viewModel.resetToLiveCamera()
      else -> onBack()
    }
  }

  MousomiLensScreenContent(
    uiState = uiState,
    hasCameraPermission = hasCameraPermission,
    onRequestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
    onPickGallery = {
      galleryPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    },
    onToggleCameraFacing = viewModel::onToggleCameraFacing,
    onToggleFlash = viewModel::onToggleFlash,
    onToggleAutoScan = viewModel::onToggleAutoScan,
    onCancelCountdown = viewModel::onCancelCountdown,
    onSelectMode = viewModel::onSelectMode,
    onFrameTelemetry = viewModel::onFrameTelemetry,
    createOutputFile = viewModel::createCaptureOutputFile,
    onCapturedFile = { file -> viewModel.analyzeCapturedFile(file) },
    onSelectComponent = viewModel::onSelectComponent,
    onToggleAnnotations = viewModel::onToggleAnnotations,
    onAskFollowUp = viewModel::askFollowUp,
    onStartCompareSecondObject = viewModel::onStartCompareCaptureMode,
    onCancelCompareCaptureMode = viewModel::onCancelCompareCaptureMode,
    onDismissComparison = viewModel::onDismissComparison,
    onContinueInChat = onContinueInChat,
    onScanAnother = viewModel::resetToLiveCamera,
    onBack = {
      when {
        uiState.isPickingCompareSecondImage -> viewModel.onCancelCompareCaptureMode()
        uiState.isShowingResults -> viewModel.resetToLiveCamera()
        else -> onBack()
      }
    },
  )
}

@Composable
fun MousomiLensScreenContent(
  uiState: LensUiState,
  hasCameraPermission: Boolean,
  onRequestCameraPermission: () -> Unit,
  onPickGallery: () -> Unit,
  onToggleCameraFacing: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleAutoScan: (Boolean) -> Unit,
  onCancelCountdown: () -> Unit,
  onSelectMode: (LensMode) -> Unit,
  onFrameTelemetry: (com.example.core.lens.LensFrameTelemetry) -> Unit,
  createOutputFile: () -> java.io.File,
  onCapturedFile: (java.io.File) -> Unit,
  onSelectComponent: (String?) -> Unit,
  onToggleAnnotations: () -> Unit,
  onAskFollowUp: (String) -> Unit,
  onStartCompareSecondObject: () -> Unit,
  onCancelCompareCaptureMode: () -> Unit,
  onDismissComparison: () -> Unit,
  onContinueInChat: (LensAnalysisResult) -> Unit,
  onScanAnother: () -> Unit,
  onBack: () -> Unit,
) {
  var showOverflowMenu by remember { mutableStateOf(false) }

  // Hold latest ImageCapture trigger handle cleanly
  var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
  val context = LocalContext.current

  val capturePhotoNow: () -> Unit = {
    val cap = imageCaptureRef
    if (cap != null) {
      val file = createOutputFile()
      val opts = ImageCapture.OutputFileOptions.Builder(file).build()
      cap.takePicture(
        opts,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
          override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onCapturedFile(file)
          }

          override fun onError(exception: ImageCaptureException) = Unit
        },
      )
    }
  }

  // Automatic 10s observation trigger executes capture once when nonce increments
  LaunchedEffect(uiState.autoCaptureRequestNonce) {
    if (uiState.autoCaptureRequestNonce > 0L) {
      capturePhotoNow()
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Color(0xFF03050C))
        .testTag("mousomi_lens_screen"),
  ) {
    // Smoky multi-color atmospheric background inspired by Gemini & IRAS Lens
    GeminiSmokyAmbientBackground(modifier = Modifier.matchParentSize(), intensity = 1.15f)

    // Continuous neon purple / electric blue / cyan perimeter frame matching IRAS Lens reference
    LensNeonPerimeterBorder(modifier = Modifier.matchParentSize())

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      // Top Glass Header Bar
      LensTopBar(
        isFlashEnabled = uiState.isFlashEnabled,
        isFrontCamera = uiState.isFrontCamera,
        autoScanEnabled = uiState.autoScanEnabled,
        showMenu = showOverflowMenu,
        isCompareMode = uiState.isPickingCompareSecondImage,
        onBack = onBack,
        onToggleFlash = onToggleFlash,
        onToggleMenu = { showOverflowMenu = !showOverflowMenu },
        onDismissMenu = { showOverflowMenu = false },
        onToggleAutoScan = {
          onToggleAutoScan(!uiState.autoScanEnabled)
          showOverflowMenu = false
        },
        onCancelCountdown = {
          onCancelCountdown()
          showOverflowMenu = false
        },
        onResetToCamera = {
          onScanAnother()
          showOverflowMenu = false
        },
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Main Content: Live Camera Viewfinder vs Smart Visual Response View
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val showLiveViewfinder = !uiState.isShowingResults || uiState.isPickingCompareSecondImage
        AnimatedContent(
          targetState = showLiveViewfinder,
          transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(200)) },
          label = "lens_view_switcher",
        ) { isLive ->
          if (isLive) {
            LensLiveCameraViewfinder(
              mode = uiState.selectedMode,
              isFrontCamera = uiState.isFrontCamera,
              isFlashEnabled = uiState.isFlashEnabled,
              hasCameraPermission = hasCameraPermission,
              autoScanState = uiState.autoScanState,
              detectedBoxes = uiState.detectedBoxes,
              isCompareSecondCapture = uiState.isPickingCompareSecondImage,
              onRequestCameraPermission = onRequestCameraPermission,
              onImageCaptureReady = { cap -> imageCaptureRef = cap },
              onFrameTelemetry = onFrameTelemetry,
              onGalleryClick = onPickGallery,
              onScanClick = capturePhotoNow,
              onSwitchCameraClick = onToggleCameraFacing,
              onCancelCountdown = onCancelCountdown,
              onCancelCompare = onCancelCompareCaptureMode,
            )
          } else {
            LensVisualResponsePanel(
              capturedFilePath = uiState.capturedImagePath,
              isAnalyzing = uiState.isAnalyzing,
              activeStageLabel = uiState.activeStageLabel,
              analysisResult = uiState.analysisResult,
              selectedComponentId = uiState.selectedComponentId,
              showAnnotations = uiState.showAnnotations,
              followUpTurns = uiState.followUpTurns,
              isAnsweringFollowUp = uiState.isAnsweringFollowUp,
              isComparing = uiState.isComparing,
              comparisonResult = uiState.comparisonResult,
              errorMessage = uiState.errorMessage,
              onSelectComponent = onSelectComponent,
              onToggleAnnotations = onToggleAnnotations,
              onAskFollowUp = onAskFollowUp,
              onStartCompareSecondObject = onStartCompareSecondObject,
              onDismissComparison = onDismissComparison,
              onContinueInChat = onContinueInChat,
              onScanAnother = onScanAnother,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Bottom 5-Mode Selector Strip (Search | Translate | Text | Identify | Shopping)
      LensModeSelectorStrip(
        selectedMode = uiState.selectedMode,
        onSelectMode = onSelectMode,
      )
    }
  }
}

@Composable
private fun LensNeonPerimeterBorder(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "lens_outer_neon")
  val rotationDeg by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
      label = "lens_border_angle",
    )

  val palette =
    remember {
      intArrayOf(
        Color(0xFF9333EA).toArgb(),
        Color(0xFF3B82F6).toArgb(),
        Color(0xFF06B6D4).toArgb(),
        Color(0xFF6366F1).toArgb(),
        Color(0xFFA855F7).toArgb(),
        Color(0xFF9333EA).toArgb(),
      )
    }
  val paint =
    remember {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
      }
    }
  val matrix = remember { Matrix() }
  val rectF = remember { RectF() }

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    if (w <= 20f || h <= 20f) return@Canvas

    val inset = 5.dp.toPx()
    val corner = 28.dp.toPx()
    val shader = SweepGradient(0f, 0f, palette, null)
    matrix.reset()
    matrix.setRotate(rotationDeg)
    matrix.postScale(w * 0.5f, h * 0.5f)
    matrix.postTranslate(w * 0.5f, h * 0.5f)
    shader.setLocalMatrix(matrix)
    paint.shader = shader
    rectF.set(inset, inset, w - inset, h - inset)

    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas
      paint.strokeWidth = 6.dp.toPx()
      paint.alpha = 115
      paint.maskFilter = BlurMaskFilter(9.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
      nc.drawRoundRect(rectF, corner, corner, paint)

      paint.strokeWidth = 2.2.dp.toPx()
      paint.alpha = 245
      paint.maskFilter = null
      nc.drawRoundRect(rectF, corner, corner, paint)
    }
  }
}

@Composable
private fun LensTopBar(
  isFlashEnabled: Boolean,
  isFrontCamera: Boolean,
  autoScanEnabled: Boolean,
  showMenu: Boolean,
  isCompareMode: Boolean,
  onBack: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleMenu: () -> Unit,
  onDismissMenu: () -> Unit,
  onToggleAutoScan: () -> Unit,
  onCancelCountdown: () -> Unit,
  onResetToCamera: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    GlassCircularIconButton(
      onClick = onBack,
      testTag = "lens_back_button",
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = "Back",
        tint = Color.White,
        modifier = Modifier.size(20.dp),
      )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LensEmblemRing(size = 24.dp)
        Text(
          text =
            buildAnnotatedString {
              withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                append("Mousomi ")
              }
              withStyle(SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)) {
                append("Lens")
              }
            },
          fontFamily = DisplayFontFamily,
          fontSize = 20.sp,
        )
      }
      Text(
        text = if (isCompareMode) "Capture or pick 2nd object to compare" else "See  •  Search  •  Discover",
        color = Color.White.copy(alpha = 0.72f),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.4.sp,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GlassCircularIconButton(
        onClick = onToggleFlash,
        active = isFlashEnabled && !isFrontCamera,
        testTag = "lens_flash_button",
      ) {
        Icon(
          imageVector = if (isFlashEnabled && !isFrontCamera) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
          contentDescription = if (isFlashEnabled) "Turn off flashlight" else "Turn on flashlight",
          tint =
            when {
              isFrontCamera -> Color.White.copy(alpha = 0.35f)
              isFlashEnabled -> NeonCyan
              else -> Color.White
            },
          modifier = Modifier.size(19.dp),
        )
      }

      Box {
        GlassCircularIconButton(
          onClick = onToggleMenu,
          testTag = "lens_menu_button",
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "Lens options",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }

        DropdownMenu(
          expanded = showMenu,
          onDismissRequest = onDismissMenu,
          containerColor = Color(0xFF10172B),
        ) {
          DropdownMenuItem(
            text = {
              Text(
                text = if (autoScanEnabled) "Auto 10s Scan: ON (Tap to Disable)" else "Auto 10s Scan: OFF (Tap to Enable)",
                color = Color.White,
              )
            },
            onClick = onToggleAutoScan,
          )
          DropdownMenuItem(
            text = { Text("Cancel Active Countdown", color = Color.White) },
            onClick = onCancelCountdown,
          )
          DropdownMenuItem(
            text = { Text("Reset Live Viewfinder", color = Color.White) },
            onClick = onResetToCamera,
          )
        }
      }
    }
  }
}

@Composable
private fun LensLiveCameraViewfinder(
  mode: LensMode,
  isFrontCamera: Boolean,
  isFlashEnabled: Boolean,
  hasCameraPermission: Boolean,
  autoScanState: LensAutoScanState,
  detectedBoxes: List<LensDetectedBox>,
  isCompareSecondCapture: Boolean,
  onRequestCameraPermission: () -> Unit,
  onImageCaptureReady: (ImageCapture?) -> Unit,
  onFrameTelemetry: (com.example.core.lens.LensFrameTelemetry) -> Unit,
  onGalleryClick: () -> Unit,
  onScanClick: () -> Unit,
  onSwitchCameraClick: () -> Unit,
  onCancelCountdown: () -> Unit,
  onCancelCompare: () -> Unit,
) {
  val scanTransition = rememberInfiniteTransition(label = "lens_scan_frame")
  val beamProgress by
    scanTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
      label = "scan_beam_y",
    )
  val bracketPulse by
    scanTransition.animateFloat(
      initialValue = 0.72f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
      label = "bracket_pulse",
    )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = Color(0xFF070B18),
    border =
      BorderStroke(
        width = 1.2.dp,
        brush = Brush.verticalGradient(listOf(NeonPurple.copy(alpha = 0.7f), NeonCyan.copy(alpha = 0.65f))),
      ),
    modifier = Modifier.fillMaxSize().testTag("lens_viewfinder_card"),
  ) {
    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
      if (hasCameraPermission) {
        CameraXLivePreview(
          isFrontCamera = isFrontCamera,
          isFlashEnabled = isFlashEnabled,
          onImageCaptureReady = onImageCaptureReady,
          onFrameTelemetry = onFrameTelemetry,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        // Permission Needed / Gallery Fallback Card inside viewfinder
        Column(
          modifier = Modifier.fillMaxSize().padding(28.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          GeminiFourPointSparkle(size = 44.dp, animated = true)
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "Camera Access Needed for Live Lens",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Grant Camera permission for real-time object recognition & 10-second auto-scan, or upload a photo from your Gallery.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.74f),
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onRequestCameraPermission,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
            modifier = Modifier.testTag("lens_grant_camera_button"),
          ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Enable Camera", color = Color.White)
          }
        }
      }

      // Live ML Kit Object Bounding Boxes + Animated Purple/Cyan Corner Reticle + Scanning Beam
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 10f || h <= 10f) return@Canvas

        // Real-time ML Kit detected boxes
        detectedBoxes.forEach { box ->
          val left = box.normLeft * w
          val top = box.normTop * h
          val bw = (box.normRight - box.normLeft) * w
          val bh = (box.normBottom - box.normTop) * h
          drawRoundRect(
            color = NeonCyan.copy(alpha = 0.12f),
            topLeft = Offset(left, top),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(18f, 18f),
          )
          drawRoundRect(
            brush = Brush.linearGradient(listOf(NeonPurple, NeonCyan)),
            topLeft = Offset(left, top),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 3f),
          )
        }

        // Animated Center Scanning Corner Brackets (matching reference IRAS Lens reticle)
        val rectSize = minOf(w, h) * 0.46f
        val left = (w - rectSize) * 0.5f
        val top = h * 0.48f - rectSize * 0.5f
        val right = left + rectSize
        val bottom = top + rectSize
        val arm = rectSize * 0.22f
        val cr = 22.dp.toPx()

        val bracketBrush =
          Brush.linearGradient(
            colors = listOf(Color(0xFFC084FC).copy(alpha = bracketPulse), NeonCyan.copy(alpha = bracketPulse)),
            start = Offset(left, top),
            end = Offset(right, bottom),
          )
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)

        // Top-Left Bracket
        drawPath(
          path =
            Path().apply {
              moveTo(left, top + arm)
              lineTo(left, top + cr)
              quadraticTo(left, top, left + cr, top)
              lineTo(left + arm, top)
            },
          brush = bracketBrush,
          style = stroke,
        )
        // Top-Right Bracket
        drawPath(
          path =
            Path().apply {
              moveTo(right - arm, top)
              lineTo(right - cr, top)
              quadraticTo(right, top, right, top + cr)
              lineTo(right, top + arm)
            },
          brush = bracketBrush,
          style = stroke,
        )
        // Bottom-Left Bracket
        drawPath(
          path =
            Path().apply {
              moveTo(left, bottom - arm)
              lineTo(left, bottom - cr)
              quadraticTo(left, bottom, left + cr, bottom)
              lineTo(left + arm, bottom)
            },
          brush = bracketBrush,
          style = stroke,
        )
        // Bottom-Right Bracket
        drawPath(
          path =
            Path().apply {
              moveTo(right - arm, bottom)
              lineTo(right - cr, bottom)
              quadraticTo(right, bottom, right, bottom - cr)
              lineTo(right, bottom - arm)
            },
          brush = bracketBrush,
          style = stroke,
        )

        // Smooth scanning light beam inside reticle during Scanning / CountingDown
        if (hasCameraPermission && (autoScanState is LensAutoScanState.CountingDown || detectedBoxes.isNotEmpty())) {
          val beamY = top + 14f + (rectSize - 28f) * beamProgress
          drawLine(
            brush =
              Brush.horizontalGradient(
                listOf(Color.Transparent, NeonCyan.copy(alpha = 0.85f), Color(0xFFC084FC), Color.Transparent)
              ),
            start = Offset(left + 12f, beamY),
            end = Offset(right - 12f, beamY),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }
      }

      // Top Floating Translucent Glass Prompt & Live 10s Auto-Scan Status Pill
      Surface(
        shape = CircleShape,
        color = Color(0xB80B1022),
        border =
          BorderStroke(
            width = 1.dp,
            brush =
              Brush.horizontalGradient(
                when (autoScanState) {
                  is LensAutoScanState.CountingDown -> listOf(NeonCyan, NeonPurple)
                  is LensAutoScanState.Unstable -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
                  else -> listOf(Color.White.copy(alpha = 0.25f), NeonCyan.copy(alpha = 0.35f))
                }
              ),
          ),
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = 18.dp, start = 18.dp, end = 18.dp)
            .testTag("lens_status_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          GeminiFourPointSparkle(
            size = 20.dp,
            animated = autoScanState is LensAutoScanState.CountingDown,
          )
          Column(modifier = Modifier.weight(1f, fill = false)) {
            when {
              isCompareSecondCapture -> {
                Text("Compare 2nd Object", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
                Text("Snap a photo or pick from Gallery", color = NeonCyan, fontSize = 11.5.sp)
              }
              autoScanState is LensAutoScanState.CountingDown -> {
                Text(
                  text = "Locking on ${autoScanState.targetLabel} • ${autoScanState.secondsRemaining}s",
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.5.sp,
                )
                Text(
                  text = "Hold steady for automatic analysis or tap scan now",
                  color = NeonCyan,
                  fontSize = 11.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              autoScanState is LensAutoScanState.Unstable -> {
                Text(autoScanState.reason, color = Color(0xFFFDE68A), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("Keep camera steady to start 10s scan", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
              }
              else -> {
                Text(
                  text = "Point your camera at anything",
                  color = Color.White,
                  fontWeight = FontWeight.Medium,
                  fontSize = 13.5.sp,
                )
                Text(
                  text = "— ${mode.hintText.substringAfter("— ", mode.hintText)} —",
                  color = Color.White.copy(alpha = 0.74f),
                  fontSize = 11.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          if (autoScanState is LensAutoScanState.CountingDown) {
            Surface(
              onClick = onCancelCountdown,
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.14f),
              modifier = Modifier.testTag("lens_cancel_countdown_button"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel auto-scan countdown", tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Cancel", color = Color.White, fontSize = 11.sp)
              }
            }
          } else if (isCompareSecondCapture) {
            Surface(
              onClick = onCancelCompare,
              shape = CircleShape,
              color = Color.White.copy(alpha = 0.14f),
            ) {
              Text("Cancel", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
          }
        }
      }

      // Bottom-Inside Viewfinder Controls: Gallery | Neon Shutter + 10s Arc | Switch Camera
      Row(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
              Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xC8030612))
              )
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Gallery Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          GlassCircularIconButton(
            onClick = onGalleryClick,
            sizeDp = 50,
            testTag = "lens_gallery_button",
          ) {
            Icon(
              imageVector = Icons.Outlined.PhotoLibrary,
              contentDescription = "Upload from Gallery",
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text("Gallery", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }

        // Main Neon AI Scan Shutter Button with Live 10s Progress Arc
        val countdownProgress =
          (autoScanState as? LensAutoScanState.CountingDown)?.progress ?: 0f
        val countdownSeconds =
          (autoScanState as? LensAutoScanState.CountingDown)?.secondsRemaining

        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .size(84.dp)
              .clip(CircleShape)
              .clickable(enabled = hasCameraPermission, onClick = onScanClick)
              .testTag("lens_scan_button"),
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.5f
            drawCircle(
              brush = Brush.radialGradient(listOf(Color(0xFF4F46E5).copy(alpha = 0.55f), Color.Transparent)),
              radius = r,
            )
            drawCircle(
              brush = Brush.sweepGradient(listOf(NeonCyan, NeonPurple, ElectricBlue, NeonCyan)),
              radius = r - 4.dp.toPx(),
              style = Stroke(width = 2.8.dp.toPx()),
            )
            if (countdownProgress > 0f) {
              drawArc(
                color = NeonCyan,
                startAngle = -90f,
                sweepAngle = 360f * countdownProgress,
                useCenter = false,
                style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round),
              )
            }
          }
          Surface(
            shape = CircleShape,
            color = Color(0xFF1D2458).copy(alpha = 0.88f),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
            modifier = Modifier.size(62.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (countdownSeconds != null) {
                Text(
                  text = "${countdownSeconds}s",
                  color = NeonCyan,
                  fontFamily = DisplayFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 19.sp,
                )
              } else {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = "Scan Now",
                  tint = Color.White,
                  modifier = Modifier.size(28.dp),
                )
              }
            }
          }
        }

        // Front/Rear Switch Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          GlassCircularIconButton(
            onClick = onSwitchCameraClick,
            sizeDp = 50,
            testTag = "lens_switch_camera_button",
          ) {
            Icon(
              imageVector = Icons.Outlined.Cameraswitch,
              contentDescription = "Switch front or rear camera",
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text("Switch", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
      }
    }
  }
}

@Composable
private fun CameraXLivePreview(
  isFrontCamera: Boolean,
  isFlashEnabled: Boolean,
  onImageCaptureReady: (ImageCapture?) -> Unit,
  onFrameTelemetry: (com.example.core.lens.LensFrameTelemetry) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
  val latestIsFront by rememberUpdatedState(isFrontCamera)
  val latestOnTelemetry by rememberUpdatedState(onFrameTelemetry)

  val frameAnalyzer =
    remember {
      LensFrameAnalyzer(
        isFrontCameraProvider = { latestIsFront },
        onTelemetry = { telemetry -> latestOnTelemetry(telemetry) },
      )
    }

  val previewView =
    remember {
      PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      }
    }

  var boundCamera by remember { mutableStateOf<Camera?>(null) }

  LaunchedEffect(isFlashEnabled, isFrontCamera, boundCamera) {
    runCatching {
      boundCamera?.cameraControl?.enableTorch(isFlashEnabled && !isFrontCamera)
    }
  }

  DisposableEffect(lifecycleOwner, isFrontCamera) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    var cameraProvider: ProcessCameraProvider? = null
    val listener =
      Runnable {
        runCatching {
          val provider = cameraProviderFuture.get()
          cameraProvider = provider
          val preview =
            Preview.Builder().build().also {
              it.surfaceProvider = previewView.surfaceProvider
            }
          val imageCapture =
            ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .build()
          val imageAnalysis =
            ImageAnalysis.Builder()
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()
              .also { it.setAnalyzer(analyzerExecutor, frameAnalyzer) }

          val selector =
            if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

          provider.unbindAll()
          val camera =
            provider.bindToLifecycle(
              lifecycleOwner,
              selector,
              preview,
              imageCapture,
              imageAnalysis,
            )
          boundCamera = camera
          onImageCaptureReady(imageCapture)
        }
      }
    cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

    onDispose {
      runCatching { cameraProvider?.unbindAll() }
      boundCamera = null
      onImageCaptureReady(null)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      frameAnalyzer.close()
      analyzerExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier,
  )
}

@Composable
private fun LensModeSelectorStrip(
  selectedMode: LensMode,
  onSelectMode: (LensMode) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    LensMode.entries.forEach { mode ->
      val selected = mode == selectedMode
      val icon =
        when (mode) {
          LensMode.SEARCH -> Icons.Outlined.Search
          LensMode.TRANSLATE -> Icons.Outlined.Translate
          LensMode.TEXT -> Icons.Outlined.TextFields
          LensMode.IDENTIFY -> Icons.Outlined.Spa
          LensMode.SHOPPING -> Icons.Outlined.ShoppingBag
        }

      Surface(
        onClick = { onSelectMode(mode) },
        shape = RoundedCornerShape(16.dp),
        color =
          if (selected) Color(0xFF2B1C5A).copy(alpha = 0.72f)
          else Color(0xFF0A1024).copy(alpha = 0.72f),
        border =
          if (selected) {
            BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonPurple, NeonCyan)))
          } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
          },
        modifier = Modifier.weight(1f).height(68.dp).testTag("lens_mode_${mode.name}"),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = mode.label,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(21.dp),
          )
          Spacer(modifier = Modifier.height(5.dp))
          Text(
            text = mode.label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.74f),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun GlassCircularIconButton(
  onClick: () -> Unit,
  sizeDp: Int = 44,
  active: Boolean = false,
  testTag: String,
  content: @Composable () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (active) NeonCyan.copy(alpha = 0.22f) else Color(0x990D142B),
    border = BorderStroke(1.dp, if (active) NeonCyan else Color.White.copy(alpha = 0.22f)),
    modifier = Modifier.size(sizeDp.dp).minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      content()
    }
  }
}

@Composable
private fun LensEmblemRing(size: androidx.compose.ui.unit.Dp) {
  Canvas(modifier = Modifier.size(size)) {
    val s = this.size.minDimension
    val center = Offset(s * 0.5f, s * 0.5f)
    drawArc(
      brush = Brush.sweepGradient(listOf(NeonPurple, NeonCyan, ElectricBlue, NeonPurple)),
      startAngle = 20f,
      sweepAngle = 290f,
      useCenter = false,
      style = Stroke(width = s * 0.16f, cap = StrokeCap.Round),
    )
    drawCircle(
      color = NeonCyan,
      radius = s * 0.14f,
      center = center,
    )
  }
}
