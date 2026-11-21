package com.example.drawthrough.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drawthrough.R
import com.example.drawthrough.ar.ArRenderParams
import com.example.drawthrough.ar.ArSurfaceController
import com.example.drawthrough.ar.ArSurfaceState
import com.example.drawthrough.ar.ArSurfaceStatus
import com.example.drawthrough.ui.components.ArSurfaceCameraLayer
import com.example.drawthrough.ui.components.CameraFeedLayer
import com.example.drawthrough.ui.components.DrawThroughIcons
import com.example.drawthrough.ui.theme.DrawThroughTheme
import com.example.drawthrough.ui.theme.OnTerracotta
import com.example.drawthrough.ui.theme.StudioBorder
import com.example.drawthrough.ui.theme.StudioCanvas
import com.example.drawthrough.ui.theme.StudioGridLine
import com.example.drawthrough.ui.theme.StudioHeader
import com.example.drawthrough.ui.theme.StudioInk
import com.example.drawthrough.ui.theme.StudioInkMuted
import com.example.drawthrough.ui.theme.StudioPillSurface
import com.example.drawthrough.ui.theme.Terracotta
import com.example.drawthrough.ui.theme.TerracottaSoft
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoPicked(context, uri)
        }
    }

    val launchPicker = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val arController = remember { ArSurfaceController() }

    // Keep screen awake while drawing over the camera feed
    KeepScreenAwakeEffect()

    // Prevent accidental back swipe while locked
    BackHandler(enabled = uiState.isLocked) {
        // Intentionally ignored while locked; unlock via hold-to-unlock pill.
    }

    DrawThroughContent(
        uiState = uiState,
        hasCameraPermission = hasCameraPermission,
        onRequestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onPickPhoto = launchPicker,
        onOpacityChange = viewModel::onOpacityChange,
        onSelectOpacity = viewModel::onSelectOpacityTool,
        onOutlineClick = viewModel::onOutlineTabClick,
        onOutlineStrengthChange = viewModel::onOutlineStrengthChange,
        onToggleGrid = viewModel::onToggleGrid,
        onToggleTorch = viewModel::onToggleTorch,
        onToggleArLock = viewModel::onToggleArLock,
        onArRepin = viewModel::onArRepin,
        onArPlaneDrag = { prevX, prevY, currX, currY ->
            arController.enqueuePlaneDrag(prevX, prevY, currX, currY)
        },
        onArTapToPin = { x, y ->
            arController.requestAnchorAt(x, y)
        },
        onSetLocked = viewModel::onSetLocked,
        onTransform = viewModel::onTransform,
        onResetTransform = viewModel::onResetTransform,
        onDismissError = viewModel::onDismissError,
        cameraContent = {
            if (uiState.arLockEnabled) {
                ArSurfaceCameraLayer(
                    renderParams = ArRenderParams(
                        bitmap = uiState.displayedBitmap,
                        opacity = uiState.opacity,
                        scale = uiState.scale,
                        rotationDeg = uiState.rotation,
                        showGrid = uiState.showGrid,
                        isTorchEnabled = uiState.isTorchEnabled,
                        isLocked = uiState.isLocked,
                        anchorVersion = uiState.arAnchorVersion,
                        resetVersion = uiState.transformResetVersion,
                    ),
                    controller = arController,
                    onStatusChanged = viewModel::onArStatusChanged,
                    onPlaneOffsetChanged = viewModel::onArPlaneOffsetChanged,
                    onUnavailable = viewModel::onArUnavailable,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CameraFeedLayer(
                    isTorchEnabled = uiState.isTorchEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun KeepScreenAwakeEffect() {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }

    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DrawThroughContent(
    uiState: MainScreenUiState,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSelectOpacity: () -> Unit,
    onOutlineClick: () -> Unit,
    onOutlineStrengthChange: (Float) -> Unit,
    onToggleGrid: () -> Unit,
    onToggleTorch: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onTransform: (zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit,
    onResetTransform: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleArLock: () -> Unit = {},
    onArRepin: () -> Unit = {},
    onArPlaneDrag: (prevX: Float, prevY: Float, currX: Float, currY: Float) -> Unit = { _, _, _, _ -> },
    onArTapToPin: (x: Float, y: Float) -> Unit = { _, _ -> },
    onDismissError: () -> Unit = {},
    cameraContent: (@Composable () -> Unit)? = null,
    previewOverlay: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioCanvas),
    ) {
        // 1. Live rear camera feed (CameraX 60fps or ARCore 3D surface-anchored OpenGL layer)
        if (hasCameraPermission && cameraContent != null) {
            cameraContent()
        }

        // 2. Optional subtle dotted alignment grid (in AR mode, rendered in 3D on the paper plane)
        if (uiState.showGrid && !(uiState.isArOverlayRendering && uiState.hasImage)) {
            DottedTracingGrid(modifier = Modifier.fillMaxSize())
        }

        // 3. Translucent transformable image overlay (or permission / empty prompt + AR gestures)
        TracingOverlayArea(
            uiState = uiState,
            hasCameraPermission = hasCameraPermission,
            onRequestCameraPermission = onRequestCameraPermission,
            onPickPhoto = onPickPhoto,
            onTransform = onTransform,
            onArPlaneDrag = onArPlaneDrag,
            onArTapToPin = onArTapToPin,
            previewOverlay = previewOverlay,
            modifier = Modifier.fillMaxSize(),
        )

        // 4. Top bar + contextual AR / reset chips (fades away cleanly when locked)
        AnimatedVisibility(
            visible = !uiState.isLocked,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StudioTopBar(
                    showGrid = uiState.showGrid,
                    isTorchEnabled = uiState.isTorchEnabled,
                    showTorch = hasCameraPermission,
                    onToggleGrid = onToggleGrid,
                    onToggleTorch = onToggleTorch,
                    onPickPhoto = onPickPhoto,
                )
                if (uiState.arLockEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        ArStatusChip(status = uiState.arStatus)
                        if (uiState.arStatus.isActivelyAnchored) {
                            RepinSurfaceChip(onClick = onArRepin)
                        }
                        if (uiState.isTransformed && uiState.hasImage) {
                            ResetTransformChip(
                                label = "Reset",
                                onClick = onResetTransform,
                            )
                        }
                    }
                } else if (uiState.isTransformed && uiState.hasImage) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ResetTransformChip(
                        label = "Reset position",
                        onClick = onResetTransform,
                    )
                }
            }
        }

        // 5. Floating contextual slider + bottom pill dock (fades away when locked)
        AnimatedVisibility(
            visible = !uiState.isLocked,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContextualSliderPill(
                    uiState = uiState,
                    onOpacityChange = onOpacityChange,
                    onOutlineStrengthChange = onOutlineStrengthChange,
                )
                StudioBottomDock(
                    activeTool = uiState.activeTool,
                    outlineEnabled = uiState.outlineEnabled,
                    arLockEnabled = uiState.arLockEnabled,
                    onPhotoClick = onPickPhoto,
                    onOpacityClick = onSelectOpacity,
                    onOutlineClick = onOutlineClick,
                    onArLockClick = onToggleArLock,
                    onLockClick = { onSetLocked(true) },
                )
            }
        }

        // 6. Locked-mode unobtrusive "Hold to unlock" pill (+ subtle tracking warning if AR pauses)
        AnimatedVisibility(
            visible = uiState.isLocked &&
                uiState.arLockEnabled &&
                uiState.arStatus.state == ArSurfaceState.TRACKING_LIMITED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp),
        ) {
            ArStatusChip(status = uiState.arStatus)
        }

        AnimatedVisibility(
            visible = uiState.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            HoldToUnlockPill(onUnlocked = { onSetLocked(false) })
        }

        // 7. Loading / error feedback
        if (uiState.isLoadingImage || uiState.isProcessingOutline) {
            Surface(
                shape = CircleShape,
                color = StudioPillSurface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) {
                CircularProgressIndicator(
                    color = Terracotta,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                )
            }
        }

        uiState.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TerracottaSoft,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 76.dp, start = 24.dp, end = 24.dp)
                    .clickable(onClick = onDismissError),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioInk,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DottedTracingGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val spacing = 56.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f)
        val strokeWidth = 1.dp.toPx()
        val gridColor = StudioGridLine.copy(alpha = 0.75f)

        var x = spacing
        while (x < size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth,
                pathEffect = dash,
            )
            x += spacing
        }

        var y = spacing
        while (y < size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth,
                pathEffect = dash,
            )
            y += spacing
        }
    }
}

@Composable
private fun TracingOverlayArea(
    uiState: MainScreenUiState,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPickPhoto: () -> Unit,
    onTransform: (zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit,
    onArPlaneDrag: (prevX: Float, prevY: Float, currX: Float, currY: Float) -> Unit,
    onArTapToPin: (x: Float, y: Float) -> Unit,
    previewOverlay: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val transformModifier = if (!uiState.isLocked && uiState.hasImage) {
        Modifier.pointerInput(uiState.arLockEnabled, uiState.isArOverlayRendering) {
            detectTransformGestures { centroid, pan, zoom, rotation ->
                if (uiState.arLockEnabled && uiState.isArOverlayRendering) {
                    if (pan != Offset.Zero) {
                        onArPlaneDrag(centroid.x - pan.x, centroid.y - pan.y, centroid.x, centroid.y)
                    }
                    if (zoom != 1f || rotation != 0f) {
                        onTransform(zoom, 0f, 0f, rotation)
                    }
                } else {
                    onTransform(zoom, pan.x, pan.y, rotation)
                }
            }
        }
    } else {
        Modifier
    }

    val tapToPinModifier = if (
        !uiState.isLocked &&
        uiState.arLockEnabled &&
        uiState.arStatus.state == ArSurfaceState.SEARCHING
    ) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onArTapToPin(offset.x, offset.y)
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(transformModifier)
            .then(tapToPinModifier),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = uiState.displayedBitmap
        when {
            !hasCameraPermission -> {
                CameraPermissionCard(onRequestCameraPermission = onRequestCameraPermission)
            }
            bitmap != null && !uiState.isArOverlayRendering -> {
                // Rendered in 2D screen space when AR is off, or gently kept visible while AR scans
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Translucent tracing overlay",
                    contentScale = ContentScale.Fit,
                    alpha = uiState.opacity,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .graphicsLayer {
                            scaleX = uiState.scale
                            scaleY = uiState.scale
                            rotationZ = uiState.rotation
                            translationX = uiState.offsetX
                            translationY = uiState.offsetY
                        },
                )
            }
            bitmap != null && uiState.isArOverlayRendering -> {
                // Image is rendered directly in 3D surface space by ArSurfaceCameraLayer
            }
            previewOverlay != null -> {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = uiState.opacity },
                ) {
                    previewOverlay()
                }
            }
            else -> {
                PickPhotoPromptCard(onPickPhoto = onPickPhoto)
            }
        }
    }
}

@Composable
private fun CameraPermissionCard(onRequestCameraPermission: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = StudioPillSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .padding(bottom = 64.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(TerracottaSoft),
            ) {
                Icon(
                    imageVector = DrawThroughIcons.Camera,
                    contentDescription = null,
                    tint = Terracotta,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "Point your camera at paper",
                style = MaterialTheme.typography.titleMedium,
                color = StudioInk,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "DrawThrough overlays a translucent image onto your live camera feed so you can trace it onto paper underneath.",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioInkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRequestCameraPermission,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Terracotta,
                    contentColor = OnTerracotta,
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Enable Camera",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PickPhotoPromptCard(onPickPhoto: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = StudioPillSurface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.8f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = 36.dp)
            .padding(bottom = 64.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TerracottaSoft),
            ) {
                Icon(
                    imageVector = DrawThroughIcons.Photo,
                    contentDescription = null,
                    tint = Terracotta,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = "Pick an image to overlay",
                style = MaterialTheme.typography.titleMedium,
                color = StudioInk,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Prop your phone above your paper, adjust opacity, and lock the overlay while you draw.",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioInkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onPickPhoto,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Terracotta,
                    contentColor = OnTerracotta,
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = DrawThroughIcons.Photo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pick Photo",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    showGrid: Boolean,
    isTorchEnabled: Boolean,
    showTorch: Boolean,
    onToggleGrid: () -> Unit,
    onToggleTorch: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Surface(
        color = StudioHeader.copy(alpha = 0.90f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = StudioInk,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showTorch) {
                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = DrawThroughIcons.Torch,
                            contentDescription = if (isTorchEnabled) "Turn off flashlight" else "Turn on flashlight",
                            tint = if (isTorchEnabled) Terracotta else StudioInkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = DrawThroughIcons.Grid,
                        contentDescription = if (showGrid) "Hide grid" else "Show grid",
                        tint = if (showGrid) Terracotta else StudioInkMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                OutlinedButton(
                    onClick = onPickPhoto,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, StudioInk.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StudioInk,
                        containerColor = Color.Transparent,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp),
                ) {
                    Icon(
                        imageVector = DrawThroughIcons.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pick Photo",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResetTransformChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = StudioPillSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 2.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = DrawThroughIcons.Reset,
                contentDescription = null,
                tint = StudioInkMuted,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = StudioInk,
            )
        }
    }
}

@Composable
private fun ArStatusChip(status: ArSurfaceStatus) {
    val label = when (status.state) {
        ArSurfaceState.INITIALIZING -> status.hint ?: "Starting AR…"
        ArSurfaceState.SEARCHING -> status.hint ?: "Point at paper & move slightly"
        ArSurfaceState.ANCHORED -> "Pinned to surface"
        ArSurfaceState.TRACKING_LIMITED -> status.hint ?: "Tracking paused"
        ArSurfaceState.UNAVAILABLE -> status.hint ?: "AR unavailable"
        ArSurfaceState.INACTIVE -> ""
    }
    val dotColor = when (status.state) {
        ArSurfaceState.ANCHORED -> Terracotta
        ArSurfaceState.TRACKING_LIMITED -> Color(0xFFC27A2B)
        else -> StudioInkMuted
    }

    Surface(
        shape = CircleShape,
        color = StudioPillSurface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = if (status.state == ArSurfaceState.ANCHORED) {
                Terracotta.copy(alpha = 0.45f)
            } else {
                StudioBorder
            },
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = StudioInk,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RepinSurfaceChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = StudioPillSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 2.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = DrawThroughIcons.Repin,
                contentDescription = null,
                tint = Terracotta,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Re-pin",
                style = MaterialTheme.typography.labelMedium,
                color = StudioInk,
            )
        }
    }
}

@Composable
private fun ContextualSliderPill(
    uiState: MainScreenUiState,
    onOpacityChange: (Float) -> Unit,
    onOutlineStrengthChange: (Float) -> Unit,
) {
    val isOpacity = uiState.activeTool == ActiveTool.OPACITY
    val icon = if (isOpacity) DrawThroughIcons.Opacity else DrawThroughIcons.Outline
    val value = if (isOpacity) uiState.opacity else uiState.outlineStrength
    val percent = (value * 100f).roundToInt()
    val label = if (isOpacity) {
        "Opacity $percent%"
    } else if (uiState.outlineEnabled) {
        "Outline $percent%"
    } else {
        "Outline Off"
    }

    Surface(
        shape = CircleShape,
        color = StudioPillSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.65f)),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Terracotta,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = value,
                onValueChange = { next ->
                    if (isOpacity) onOpacityChange(next) else onOutlineStrengthChange(next)
                },
                valueRange = if (isOpacity) 0.05f..1.0f else 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Terracotta,
                    activeTrackColor = Terracotta,
                    inactiveTrackColor = TerracottaSoft,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = StudioInk,
            )
        }
    }
}

@Composable
private fun StudioBottomDock(
    activeTool: ActiveTool,
    outlineEnabled: Boolean,
    arLockEnabled: Boolean,
    onPhotoClick: () -> Unit,
    onOpacityClick: () -> Unit,
    onOutlineClick: () -> Unit,
    onArLockClick: () -> Unit,
    onLockClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = StudioPillSurface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.65f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            DockActionItem(
                icon = DrawThroughIcons.Photo,
                label = "Photo",
                highlighted = false,
                onClick = onPhotoClick,
                modifier = Modifier.weight(1f),
            )
            DockActionItem(
                icon = DrawThroughIcons.Opacity,
                label = "Opacity",
                highlighted = activeTool == ActiveTool.OPACITY,
                softCircle = false,
                onClick = onOpacityClick,
                modifier = Modifier.weight(1f),
            )
            DockActionItem(
                icon = DrawThroughIcons.Outline,
                label = "Outline",
                highlighted = outlineEnabled,
                softCircle = true,
                onClick = onOutlineClick,
                modifier = Modifier.weight(1f),
            )
            DockActionItem(
                icon = DrawThroughIcons.ArSurface,
                label = "AR Pin",
                highlighted = arLockEnabled,
                softCircle = true,
                onClick = onArLockClick,
                modifier = Modifier.weight(1f),
            )

            Surface(
                onClick = onLockClick,
                shape = RoundedCornerShape(24.dp),
                color = Terracotta,
                modifier = Modifier
                    .weight(1.05f)
                    .padding(start = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                ) {
                    Icon(
                        imageVector = DrawThroughIcons.Lock,
                        contentDescription = "Lock overlay while tracing",
                        tint = OnTerracotta,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnTerracotta,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockActionItem(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    softCircle: Boolean = false,
) {
    val contentColor = if (highlighted && softCircle) Terracotta else StudioInk
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        highlighted && softCircle -> TerracottaSoft
                        highlighted -> StudioBorder.copy(alpha = 0.35f)
                        else -> Color.Transparent
                    },
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HoldToUnlockPill(onUnlocked: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = StudioPillSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, StudioBorder),
        shadowElevation = 4.dp,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isHolding = true
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 900, easing = LinearEasing),
                        )
                        onUnlocked()
                    }
                    tryAwaitRelease()
                    isHolding = false
                    if (job.isActive) {
                        job.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                },
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp),
            ) {
                if (isHolding || progress.value > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Terracotta,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
                Icon(
                    imageVector = DrawThroughIcons.Lock,
                    contentDescription = "Hold to unlock",
                    tint = Terracotta,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hold to unlock",
                style = MaterialTheme.typography.labelMedium,
                color = StudioInk,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawThroughActiveStudioPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(
                opacity = 0.55f,
                outlineEnabled = true,
                activeTool = ActiveTool.OPACITY,
                showGrid = true,
            ),
            hasCameraPermission = true,
            onRequestCameraPermission = {},
            onPickPhoto = {},
            onOpacityChange = {},
            onSelectOpacity = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onToggleTorch = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            cameraContent = {
                // Simulated sunlit sketchbook paper seen through rear camera
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEDE8DF)),
                )
            },
            previewOverlay = {
                Canvas(modifier = Modifier.size(240.dp)) {
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(
                        color = StudioInk,
                        radius = size.minDimension * 0.28f,
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        style = stroke,
                    )
                    drawLine(
                        color = StudioInk,
                        start = Offset(size.width * 0.5f, size.height * 0.17f),
                        end = Offset(size.width * 0.5f, size.height * 0.85f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawThroughEmptyPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(),
            hasCameraPermission = true,
            onRequestCameraPermission = {},
            onPickPhoto = {},
            onOpacityChange = {},
            onSelectOpacity = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onToggleTorch = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            cameraContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE9E3D8)),
                )
            },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawThroughLockedPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(
                opacity = 0.5f,
                isLocked = true,
                showGrid = false,
            ),
            hasCameraPermission = true,
            onRequestCameraPermission = {},
            onPickPhoto = {},
            onOpacityChange = {},
            onSelectOpacity = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onToggleTorch = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            cameraContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEDE8DF)),
                )
            },
            previewOverlay = {
                Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(
                        color = StudioInk,
                        radius = size.minDimension * 0.28f,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawThroughArPinnedPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(
                opacity = 0.55f,
                arLockEnabled = true,
                arStatus = ArSurfaceStatus(state = ArSurfaceState.ANCHORED),
                arHasPlaneOffset = true,
            ),
            hasCameraPermission = true,
            onRequestCameraPermission = {},
            onPickPhoto = {},
            onOpacityChange = {},
            onSelectOpacity = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onToggleTorch = {},
            onToggleArLock = {},
            onArRepin = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            cameraContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEDE8DF)),
                )
            },
            previewOverlay = {
                Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(
                        color = StudioInk,
                        radius = size.minDimension * 0.28f,
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            },
        )
    }
}
