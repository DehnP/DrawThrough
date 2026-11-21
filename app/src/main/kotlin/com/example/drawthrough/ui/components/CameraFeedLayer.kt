package com.example.drawthrough.ui.components

import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraFeedLayer(
    isTorchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameraProvider = provider

        val cameraInfo = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        val bestFpsRange = pickLowLatencyFpsRange(cameraInfo.supportedFrameRateRanges)

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

        val previewBuilder = CameraPreview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetFrameRate(bestFpsRange)

        // Force lowest-latency ISP path: disable multi-frame stabilization & switch ISP stages to FAST
        Camera2Interop.Extender(previewBuilder).apply {
            setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            )
            setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.HOT_PIXEL_MODE,
                CaptureRequest.HOT_PIXEL_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.SHADING_MODE,
                CaptureRequest.SHADING_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE,
                CaptureRequest.TONEMAP_MODE_FAST,
            )
            setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
            )
            setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                bestFpsRange,
            )
        }

        val preview = previewBuilder.build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

        provider.unbindAll()
        boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
        )
    }

    LaunchedEffect(boundCamera, isTorchEnabled) {
        boundCamera?.cameraControl?.enableTorch(isTorchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            boundCamera?.cameraControl?.enableTorch(false)
            cameraProvider?.unbindAll()
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            // SurfaceView-backed hardware layer bypasses GPU texture copy for minimal display latency
            implementationMode = ImplementationMode.EXTERNAL,
            modifier = modifier.fillMaxSize(),
        )
    }
}

private fun pickLowLatencyFpsRange(supportedRanges: Set<Range<Int>>): Range<Int> {
    if (supportedRanges.isEmpty()) return Range(60, 60)

    // Prefer fixed 60fps for crisp, low-latency hand/paper tracking
    supportedRanges.firstOrNull { it.lower == 60 && it.upper == 60 }?.let { return it }

    // Otherwise prefer the highest upper FPS, favoring higher minimum FPS so exposure doesn't drop frames
    return supportedRanges.maxWith(
        compareBy<Range<Int>> { it.upper }.thenBy { it.lower },
    )
}
