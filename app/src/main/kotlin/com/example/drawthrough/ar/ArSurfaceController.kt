package com.example.drawthrough.ar

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Immutable snapshot of Compose UI state consumed by the AR OpenGL renderer on each frame.
 */
data class ArRenderParams(
    val bitmap: Bitmap? = null,
    val opacity: Float = 0.50f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val showGrid: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isLocked: Boolean = false,
    val anchorVersion: Int = 0,
    val resetVersion: Int = 0,
)

internal data class ScreenDragEvent(
    val prevX: Float,
    val prevY: Float,
    val currX: Float,
    val currY: Float,
)

internal data class ScreenTapAnchorEvent(
    val x: Float,
    val y: Float,
)

/**
 * Thread-safe bridge allowing Compose gesture handlers to queue 3D plane-drag and tap-to-pin
 * events that are processed on the AR OpenGL render thread with zero main-thread blocking.
 */
class ArSurfaceController {
    @Volatile
    internal var renderParams: ArRenderParams = ArRenderParams()

    internal val pendingDrags = ConcurrentLinkedQueue<ScreenDragEvent>()
    internal val pendingTapAnchors = ConcurrentLinkedQueue<ScreenTapAnchorEvent>()

    fun updateParams(params: ArRenderParams) {
        renderParams = params
    }

    fun enqueuePlaneDrag(prevX: Float, prevY: Float, currX: Float, currY: Float) {
        if (renderParams.isLocked) return
        pendingDrags.offer(ScreenDragEvent(prevX, prevY, currX, currY))
    }

    fun requestAnchorAt(screenX: Float, screenY: Float) {
        if (renderParams.isLocked) return
        pendingTapAnchors.offer(ScreenTapAnchorEvent(screenX, screenY))
    }

    internal fun clearQueues() {
        pendingDrags.clear()
        pendingTapAnchors.clear()
    }
}
