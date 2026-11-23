package com.example.core.display

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.RoundedCorner
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Shared screen-coordinate geometry snapshot across Border Light, Floating Bubble,
 * Dismiss Target, and IME-aware Floating Chat Panel.
 */
data class DisplayGeometry(
  val screenWidthPx: Int,
  val screenHeightPx: Int,
  val density: Float,
  val rotation: Int = Surface.ROTATION_0,
  val statusBarTopPx: Int = 0,
  val navBarBottomPx: Int = 0,
  val navBarLeftPx: Int = 0,
  val navBarRightPx: Int = 0,
  val cutoutTopPx: Int = 0,
  val cutoutBottomPx: Int = 0,
  val cutoutLeftPx: Int = 0,
  val cutoutRightPx: Int = 0,
  val imeBottomPx: Int = 0,
  val cornerTopLeftPx: Float = 32f * density,
  val cornerTopRightPx: Float = 32f * density,
  val cornerBottomRightPx: Float = 32f * density,
  val cornerBottomLeftPx: Float = 32f * density,
) {
  val isImeVisible: Boolean
    get() = imeBottomPx > (48f * density).toInt()

  val safeTopPx: Int
    get() = maxOf(statusBarTopPx, cutoutTopPx)

  val safeLeftPx: Int
    get() = maxOf(navBarLeftPx, cutoutLeftPx)

  val safeRightPx: Int
    get() = maxOf(navBarRightPx, cutoutRightPx)

  val safeBottomWithoutImePx: Int
    get() = maxOf(navBarBottomPx, cutoutBottomPx)

  val safeBottomWithImePx: Int
    get() = maxOf(safeBottomWithoutImePx, imeBottomPx)

  val usableWidthPx: Int
    get() = (screenWidthPx - safeLeftPx - safeRightPx).coerceAtLeast((180f * density).roundToInt())

  val usableHeightAboveImePx: Int
    get() =
      (screenHeightPx - safeTopPx - safeBottomWithImePx).coerceAtLeast((160f * density).roundToInt())

  val cornerRadii8f: FloatArray
    get() =
      floatArrayOf(
        cornerTopLeftPx,
        cornerTopLeftPx,
        cornerTopRightPx,
        cornerTopRightPx,
        cornerBottomRightPx,
        cornerBottomRightPx,
        cornerBottomLeftPx,
        cornerBottomLeftPx,
      )
}

data class PanelPlacementSpec(
  val widthPx: Int,
  val maxHeightDp: Int,
  val bottomMarginPx: Int,
)

data class DismissTargetSpec(
  val centerX: Float,
  val centerY: Float,
  val radiusPx: Float,
)

@Singleton
class DisplayGeometryManager @Inject constructor() {

  fun measure(context: Context, rootView: View? = null): DisplayGeometry {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val dm = context.resources.displayMetrics
    val density = dm.density.coerceAtLeast(1f)
    val defaultCornerPx = 32f * density
    val rotation =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display?.rotation ?: Surface.ROTATION_0 }.getOrDefault(Surface.ROTATION_0)
      } else {
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
      }

    var widthPx = dm.widthPixels.coerceAtLeast(320)
    var heightPx = dm.heightPixels.coerceAtLeast(480)
    var statusTop = 0
    var navBottom = 0
    var navLeft = 0
    var navRight = 0
    var cutTop = 0
    var cutBottom = 0
    var cutLeft = 0
    var cutRight = 0
    var imeBottom = 0
    var cTL = defaultCornerPx
    var cTR = defaultCornerPx
    var cBR = defaultCornerPx
    var cBL = defaultCornerPx

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
      val metrics = wm.currentWindowMetrics
      val bounds = metrics.bounds
      if (bounds.width() > 0 && bounds.height() > 0) {
        widthPx = bounds.width()
        heightPx = bounds.height()
      }
      val wi = metrics.windowInsets
      val sysBars = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
      statusTop = sysBars.top
      navBottom = sysBars.bottom
      navLeft = sysBars.left
      navRight = sysBars.right
      val cutout = wi.displayCutout
      if (cutout != null) {
        cutTop = cutout.safeInsetTop
        cutBottom = cutout.safeInsetBottom
        cutLeft = cutout.safeInsetLeft
        cutRight = cutout.safeInsetRight
      }
      val wmIme = wi.getInsets(WindowInsets.Type.ime())
      imeBottom = wmIme.bottom

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cTL =
          wi.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()?.takeIf { it > 4f }
            ?: defaultCornerPx
        cTR =
          wi.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f }
            ?: defaultCornerPx
        cBR =
          wi.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat()?.takeIf { it > 4f }
            ?: defaultCornerPx
        cBL =
          wi.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat()?.takeIf { it > 4f }
            ?: defaultCornerPx
      }
    }

    if (rootView != null) {
      val rootInsets = ViewCompat.getRootWindowInsets(rootView)
      if (rootInsets != null) {
        val sys = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = rootInsets.getInsets(WindowInsetsCompat.Type.ime())
        if (sys.top > 0) statusTop = maxOf(statusTop, sys.top)
        if (sys.bottom > 0) navBottom = maxOf(navBottom, sys.bottom)
        if (sys.left > 0) navLeft = maxOf(navLeft, sys.left)
        if (sys.right > 0) navRight = maxOf(navRight, sys.right)
        imeBottom = maxOf(imeBottom, ime.bottom)
      }
      val visibleRect = Rect()
      rootView.getWindowVisibleDisplayFrame(visibleRect)
      if (visibleRect.height() > 0 && visibleRect.bottom in 100 until heightPx) {
        val inferredBottomObscured = (heightPx - visibleRect.bottom).coerceAtLeast(0)
        if (inferredBottomObscured > maxOf(navBottom, cutBottom) + (64f * density).roundToInt()) {
          imeBottom = maxOf(imeBottom, inferredBottomObscured)
        }
      }
    }

    return DisplayGeometry(
      screenWidthPx = widthPx,
      screenHeightPx = heightPx,
      density = density,
      rotation = rotation,
      statusBarTopPx = statusTop,
      navBarBottomPx = navBottom,
      navBarLeftPx = navLeft,
      navBarRightPx = navRight,
      cutoutTopPx = cutTop,
      cutoutBottomPx = cutBottom,
      cutoutLeftPx = cutLeft,
      cutoutRightPx = cutRight,
      imeBottomPx = imeBottom,
      cornerTopLeftPx = cTL,
      cornerTopRightPx = cTR,
      cornerBottomRightPx = cBR,
      cornerBottomLeftPx = cBL,
    )
  }

  companion object {

    fun computePanelPlacement(
      geometry: DisplayGeometry,
      windowAlreadyShiftedByIme: Boolean = false,
    ): PanelPlacementSpec {
      val maxWidthPx = (420f * geometry.density).roundToInt()
      val widthPx =
        ((geometry.usableWidthPx * 0.94f).roundToInt())
          .coerceIn((260f * geometry.density).roundToInt().coerceAtMost(geometry.usableWidthPx), maxWidthPx)

      val clearanceDp = 12
      val bottomMarginPx =
        if (geometry.isImeVisible) {
          if (windowAlreadyShiftedByIme) {
            (clearanceDp * geometry.density).roundToInt()
          } else {
            geometry.imeBottomPx + (clearanceDp * geometry.density).roundToInt()
          }
        } else {
          geometry.safeBottomWithoutImePx + (16f * geometry.density).roundToInt()
        }

      val usableAboveDp = (geometry.usableHeightAboveImePx / geometry.density).roundToInt()
      val maxHeightDp =
        if (geometry.isImeVisible) {
          (usableAboveDp - 24).coerceIn(180, 340)
        } else {
          (usableAboveDp - 24).coerceIn(190, 460)
        }
      return PanelPlacementSpec(
        widthPx = widthPx,
        maxHeightDp = maxHeightDp,
        bottomMarginPx = bottomMarginPx,
      )
    }

    fun computeDismissTargetSpec(geometry: DisplayGeometry): DismissTargetSpec {
      val radiusPx = (36f * geometry.density).coerceAtLeast(48f)
      val bottomOffset = geometry.safeBottomWithoutImePx + (58f * geometry.density)
      return DismissTargetSpec(
        centerX = geometry.screenWidthPx / 2f,
        centerY = (geometry.screenHeightPx - bottomOffset).coerceAtLeast(radiusPx + 40f),
        radiusPx = radiusPx * 1.45f,
      )
    }

    fun isPointInsideDismissTarget(
      pointX: Float,
      pointY: Float,
      target: DismissTargetSpec,
    ): Boolean = hypot(pointX - target.centerX, pointY - target.centerY) <= target.radiusPx

    fun clampBubbleCoordinates(
      xPx: Int,
      yPx: Int,
      bubbleSizePx: Int,
      geometry: DisplayGeometry,
    ): Pair<Int, Int> {
      val edgePad = (8f * geometry.density).roundToInt()
      val minX = (geometry.safeLeftPx + edgePad).coerceAtLeast(0)
      val maxX = (geometry.screenWidthPx - geometry.safeRightPx - bubbleSizePx - edgePad).coerceAtLeast(minX)
      val minY = (geometry.safeTopPx + (16f * geometry.density).roundToInt()).coerceAtLeast(0)
      val maxY =
        (geometry.screenHeightPx - geometry.safeBottomWithoutImePx - bubbleSizePx - (16f * geometry.density).roundToInt())
          .coerceAtLeast(minY)
      return Pair(xPx.coerceIn(minX, maxX), yPx.coerceIn(minY, maxY))
    }
  }
}
