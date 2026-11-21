package com.example.drawthrough.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drawthrough.R
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

    // Keep screen awake & apply lightbox brightness to the Activity window
    LightboxWindowEffect(brightness = uiState.brightness)

    // Prevent accidental back swipe while tracing with paper on screen
    BackHandler(enabled = uiState.isLocked) {
        // Intentionally ignored while locked; user unlocks via hold-to-unlock pill.
    }

    DrawThroughContent(
        uiState = uiState,
        onPickPhoto = launchPicker,
        onBrightnessChange = viewModel::onBrightnessChange,
        onSelectBrightness = viewModel::onSelectBrightnessTool,
        onOutlineClick = viewModel::onOutlineTabClick,
        onOutlineStrengthChange = viewModel::onOutlineStrengthChange,
        onToggleGrid = viewModel::onToggleGrid,
        onSetLocked = viewModel::onSetLocked,
        onTransform = viewModel::onTransform,
        onResetTransform = viewModel::onResetTransform,
        modifier = modifier,
    )
}

@Composable
private fun LightboxWindowEffect(brightness: Float) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }

    LaunchedEffect(activity, brightness) {
        val window = activity?.window ?: return@LaunchedEffect
        val params = window.attributes
        params.screenBrightness = brightness.coerceIn(0.1f, 1.0f)
        window.attributes = params
    }

    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { w ->
                val params = w.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = params
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun DrawThroughContent(
    uiState: MainScreenUiState,
    onPickPhoto: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSelectBrightness: () -> Unit,
    onOutlineClick: () -> Unit,
    onOutlineStrengthChange: (Float) -> Unit,
    onToggleGrid: () -> Unit,
    onSetLocked: (Boolean) -> Unit,
    onTransform: (zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit,
    onResetTransform: () -> Unit,
    modifier: Modifier = Modifier,
    previewOverlay: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioCanvas),
    ) {
        // 1. Subtle dotted studio grid
        if (uiState.showGrid) {
            DottedTracingGrid(modifier = Modifier.fillMaxSize())
        }

        // 2. Transformable tracing canvas (or empty state)
        TracingCanvasArea(
            uiState = uiState,
            onPickPhoto = onPickPhoto,
            onTransform = onTransform,
            previewOverlay = previewOverlay,
            modifier = Modifier.fillMaxSize(),
        )

        // 3. Top bar (fades away cleanly when locked)
        AnimatedVisibility(
            visible = !uiState.isLocked,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StudioTopBar(
                    showGrid = uiState.showGrid,
                    onToggleGrid = onToggleGrid,
                    onPickPhoto = onPickPhoto,
                )
                if (uiState.isTransformed && uiState.hasImage) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ResetTransformChip(onClick = onResetTransform)
                }
            }
        }

        // 4. Floating contextual slider + bottom pill dock (fades away when locked)
        AnimatedVisibility(
            visible = !uiState.isLocked,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContextualSliderPill(
                    uiState = uiState,
                    onBrightnessChange = onBrightnessChange,
                    onOutlineStrengthChange = onOutlineStrengthChange,
                )
                StudioBottomDock(
                    activeTool = uiState.activeTool,
                    outlineEnabled = uiState.outlineEnabled,
                    onPhotoClick = onPickPhoto,
                    onBrightnessClick = onSelectBrightness,
                    onOutlineClick = onOutlineClick,
                    onLockClick = { onSetLocked(true) },
                )
            }
        }

        // 5. Locked-mode unobtrusive "Hold to unlock" pill
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

        // 6. Loading / error feedback
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
                    .padding(top = 76.dp, start = 24.dp, end = 24.dp),
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

        var x = spacing
        while (x < size.width) {
            drawLine(
                color = StudioGridLine,
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
                color = StudioGridLine,
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
private fun TracingCanvasArea(
    uiState: MainScreenUiState,
    onPickPhoto: () -> Unit,
    onTransform: (zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit,
    previewOverlay: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gestureModifier = if (!uiState.isLocked && uiState.hasImage) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, rotation ->
                onTransform(zoom, pan.x, pan.y, rotation)
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = uiState.displayedBitmap
        when {
            bitmap != null -> {
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Tracing image",
                    contentScale = ContentScale.Fit,
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
            previewOverlay != null -> {
                previewOverlay()
            }
            else -> {
                EmptyStudioState(onPickPhoto = onPickPhoto)
            }
        }
    }
}

@Composable
private fun EmptyStudioState(onPickPhoto: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(horizontal = 36.dp)
            .padding(bottom = 64.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(TerracottaSoft.copy(alpha = 0.7f)),
        ) {
            Icon(
                imageVector = DrawThroughIcons.Photo,
                contentDescription = null,
                tint = Terracotta,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = "Place paper over your screen to trace",
            style = MaterialTheme.typography.titleMedium,
            color = StudioInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Pick any photo, pinch to resize or rotate, and lock the screen before drawing.",
            style = MaterialTheme.typography.bodyMedium,
            color = StudioInkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onPickPhoto,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Terracotta,
                contentColor = OnTerracotta,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
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

@Composable
private fun StudioTopBar(
    showGrid: Boolean,
    onToggleGrid: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Surface(
        color = StudioHeader,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
private fun ResetTransformChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = StudioPillSurface,
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
                text = "Reset position",
                style = MaterialTheme.typography.labelMedium,
                color = StudioInk,
            )
        }
    }
}

@Composable
private fun ContextualSliderPill(
    uiState: MainScreenUiState,
    onBrightnessChange: (Float) -> Unit,
    onOutlineStrengthChange: (Float) -> Unit,
) {
    val isBrightness = uiState.activeTool == ActiveTool.BRIGHTNESS
    val icon = if (isBrightness) DrawThroughIcons.Sun else DrawThroughIcons.Outline
    val value = if (isBrightness) uiState.brightness else uiState.outlineStrength
    val percent = (value * 100f).roundToInt()
    val label = if (isBrightness) {
        "Brightness $percent%"
    } else if (uiState.outlineEnabled) {
        "Outline $percent%"
    } else {
        "Outline Off"
    }

    Surface(
        shape = CircleShape,
        color = StudioPillSurface,
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
                    if (isBrightness) onBrightnessChange(next) else onOutlineStrengthChange(next)
                },
                valueRange = 0.1f..1.0f,
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
    onPhotoClick: () -> Unit,
    onBrightnessClick: () -> Unit,
    onOutlineClick: () -> Unit,
    onLockClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = StudioPillSurface,
        border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.65f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            DockActionItem(
                icon = DrawThroughIcons.Photo,
                label = "Photo",
                highlighted = false,
                onClick = onPhotoClick,
                modifier = Modifier.weight(1f),
            )
            DockActionItem(
                icon = DrawThroughIcons.Sun,
                label = "Brightness",
                highlighted = activeTool == ActiveTool.BRIGHTNESS,
                softCircle = false,
                onClick = onBrightnessClick,
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

            // Prominent terracotta Lock pill
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
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = DrawThroughIcons.Lock,
                        contentDescription = "Lock screen for tracing",
                        tint = OnTerracotta,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnTerracotta,
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
        color = StudioPillSurface.copy(alpha = 0.85f),
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
private fun DrawThroughEmptyPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(),
            onPickPhoto = {},
            onBrightnessChange = {},
            onSelectBrightness = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DrawThroughActiveStudioPreview() {
    DrawThroughTheme {
        DrawThroughContent(
            uiState = MainScreenUiState(
                brightness = 1.0f,
                outlineEnabled = true,
                activeTool = ActiveTool.BRIGHTNESS,
                showGrid = true,
            ),
            onPickPhoto = {},
            onBrightnessChange = {},
            onSelectBrightness = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            previewOverlay = {
                // Clean sample botanical sketch for visual preview
                Canvas(modifier = Modifier.size(240.dp)) {
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
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
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
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
                isLocked = true,
                showGrid = true,
            ),
            onPickPhoto = {},
            onBrightnessChange = {},
            onSelectBrightness = {},
            onOutlineClick = {},
            onOutlineStrengthChange = {},
            onToggleGrid = {},
            onSetLocked = {},
            onTransform = { _, _, _, _ -> },
            onResetTransform = {},
            previewOverlay = {
                Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(
                        color = StudioInk,
                        radius = size.minDimension * 0.28f,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            },
        )
    }
}
