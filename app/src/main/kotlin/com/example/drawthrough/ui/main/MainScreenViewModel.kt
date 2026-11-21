package com.example.drawthrough.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drawthrough.ar.ArSurfaceState
import com.example.drawthrough.ar.ArSurfaceStatus
import com.example.drawthrough.image.TraceImageProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveTool {
    OPACITY,
    OUTLINE,
}

data class MainScreenUiState(
    val originalBitmap: Bitmap? = null,
    val outlineBitmap: Bitmap? = null,
    val isLoadingImage: Boolean = false,
    val isProcessingOutline: Boolean = false,
    val errorMessage: String? = null,
    val opacity: Float = 0.50f,
    val outlineEnabled: Boolean = false,
    val outlineStrength: Float = 0.55f,
    val activeTool: ActiveTool = ActiveTool.OPACITY,
    val showGrid: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isLocked: Boolean = false,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val arLockEnabled: Boolean = false,
    val arStatus: ArSurfaceStatus = ArSurfaceStatus(),
    val arHasPlaneOffset: Boolean = false,
    val arAnchorVersion: Int = 0,
    val transformResetVersion: Int = 0,
) {
    val hasImage: Boolean get() = originalBitmap != null
    val displayedBitmap: Bitmap?
        get() = if (outlineEnabled && outlineBitmap != null) outlineBitmap else originalBitmap
    val isTransformed: Boolean
        get() = scale != 1f || rotation != 0f || offsetX != 0f || offsetY != 0f || arHasPlaneOffset
    val isArOverlayRendering: Boolean
        get() = arLockEnabled && arStatus.isActivelyAnchored
}

class MainScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainScreenUiState())
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private var outlineJob: Job? = null

    fun onPhotoPicked(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        outlineJob?.cancel()
        _uiState.update { it.copy(isLoadingImage = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                TraceImageProcessor.decodeBitmap(appContext, uri)
            }.onSuccess { bitmap ->
                val current = _uiState.value
                _uiState.update {
                    it.copy(
                        originalBitmap = bitmap,
                        outlineBitmap = null,
                        isLoadingImage = false,
                        errorMessage = null,
                        scale = 1f,
                        rotation = 0f,
                        offsetX = 0f,
                        offsetY = 0f,
                        arHasPlaneOffset = false,
                        transformResetVersion = it.transformResetVersion + 1,
                        isLocked = false,
                    )
                }
                if (current.outlineEnabled) {
                    regenerateOutline(bitmap, current.outlineStrength, debounceMs = 0L)
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingImage = false,
                        errorMessage = throwable.message ?: "Could not load photo",
                    )
                }
            }
        }
    }

    fun onOpacityChange(value: Float) {
        _uiState.update {
            it.copy(
                opacity = value.coerceIn(0.05f, 1.0f),
                activeTool = ActiveTool.OPACITY,
            )
        }
    }

    fun onSelectOpacityTool() {
        _uiState.update { it.copy(activeTool = ActiveTool.OPACITY) }
    }

    fun onOutlineTabClick() {
        val current = _uiState.value
        val nextEnabled = if (current.activeTool == ActiveTool.OUTLINE) {
            !current.outlineEnabled
        } else {
            true
        }
        _uiState.update {
            it.copy(
                activeTool = ActiveTool.OUTLINE,
                outlineEnabled = nextEnabled,
            )
        }
        if (nextEnabled) {
            val src = _uiState.value.originalBitmap
            if (src != null && _uiState.value.outlineBitmap == null) {
                regenerateOutline(src, _uiState.value.outlineStrength, debounceMs = 0L)
            }
        }
    }

    fun onOutlineStrengthChange(value: Float) {
        val clamped = value.coerceIn(0.1f, 1.0f)
        _uiState.update {
            it.copy(
                outlineStrength = clamped,
                outlineEnabled = true,
                activeTool = ActiveTool.OUTLINE,
            )
        }
        val src = _uiState.value.originalBitmap ?: return
        regenerateOutline(src, clamped, debounceMs = 120L)
    }

    fun onToggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun onToggleTorch() {
        _uiState.update { it.copy(isTorchEnabled = !it.isTorchEnabled) }
    }

    fun onSetLocked(locked: Boolean) {
        _uiState.update { it.copy(isLocked = locked) }
    }

    fun onTransform(zoomChange: Float, panX: Float, panY: Float, rotationChange: Float) {
        if (_uiState.value.isLocked) return
        _uiState.update { state ->
            state.copy(
                scale = (state.scale * zoomChange).coerceIn(0.25f, 8f),
                rotation = (state.rotation + rotationChange) % 360f,
                offsetX = state.offsetX + panX,
                offsetY = state.offsetY + panY,
            )
        }
    }

    fun onResetTransform() {
        _uiState.update {
            it.copy(
                scale = 1f,
                rotation = 0f,
                offsetX = 0f,
                offsetY = 0f,
                arHasPlaneOffset = false,
                transformResetVersion = it.transformResetVersion + 1,
            )
        }
    }

    fun onToggleArLock() {
        _uiState.update { state ->
            if (state.arLockEnabled) {
                state.copy(
                    arLockEnabled = false,
                    arStatus = ArSurfaceStatus(ArSurfaceState.INACTIVE),
                    arHasPlaneOffset = false,
                )
            } else {
                state.copy(
                    arLockEnabled = true,
                    arStatus = ArSurfaceStatus(ArSurfaceState.INITIALIZING, "Starting AR…"),
                    arHasPlaneOffset = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun onArStatusChanged(status: ArSurfaceStatus) {
        _uiState.update { state ->
            if (!state.arLockEnabled) {
                state
            } else {
                state.copy(arStatus = status)
            }
        }
    }

    fun onArUnavailable(message: String) {
        _uiState.update { state ->
            state.copy(
                arLockEnabled = false,
                arStatus = ArSurfaceStatus(ArSurfaceState.INACTIVE),
                arHasPlaneOffset = false,
                errorMessage = message,
            )
        }
    }

    fun onArRepin() {
        _uiState.update { state ->
            if (!state.arLockEnabled || state.isLocked) {
                state
            } else {
                state.copy(
                    arAnchorVersion = state.arAnchorVersion + 1,
                    arHasPlaneOffset = false,
                    arStatus = ArSurfaceStatus(
                        state = ArSurfaceState.SEARCHING,
                        hint = "Point at paper & move slightly",
                    ),
                )
            }
        }
    }

    fun onArPlaneOffsetChanged(hasOffset: Boolean) {
        _uiState.update { state ->
            if (!state.arLockEnabled) state else state.copy(arHasPlaneOffset = hasOffset)
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun regenerateOutline(source: Bitmap, strength: Float, debounceMs: Long) {
        outlineJob?.cancel()
        outlineJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            _uiState.update { it.copy(isProcessingOutline = true) }
            val outline = TraceImageProcessor.createOutlineBitmap(source, strength)
            _uiState.update {
                it.copy(
                    outlineBitmap = outline,
                    isProcessingOutline = false,
                )
            }
        }
    }
}
