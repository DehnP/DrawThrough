package com.example.drawthrough.ui.main

import com.example.drawthrough.ar.ArSurfaceState
import com.example.drawthrough.ar.ArSurfaceStatus
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun initialState_hasExpectedDefaults() {
        val viewModel = MainScreenViewModel()
        val state = viewModel.uiState.value

        assertFalse(state.hasImage)
        assertEquals(0.50f, state.opacity)
        assertFalse(state.outlineEnabled)
        assertEquals(ActiveTool.OPACITY, state.activeTool)
        assertFalse(state.showGrid)
        assertFalse(state.isTorchEnabled)
        assertFalse(state.isLocked)
        assertFalse(state.arLockEnabled)
        assertEquals(ArSurfaceState.INACTIVE, state.arStatus.state)
        assertFalse(state.arHasPlaneOffset)
    }

    @Test
    fun opacityChange_clampsWithinRange() {
        val viewModel = MainScreenViewModel()
        viewModel.onOpacityChange(0.0f)
        assertEquals(0.05f, viewModel.uiState.value.opacity)

        viewModel.onOpacityChange(1.5f)
        assertEquals(1.0f, viewModel.uiState.value.opacity)
    }

    @Test
    fun torchToggle_flipsTorchState() {
        val viewModel = MainScreenViewModel()
        assertFalse(viewModel.uiState.value.isTorchEnabled)

        viewModel.onToggleTorch()
        assertTrue(viewModel.uiState.value.isTorchEnabled)
    }

    @Test
    fun transformAndReset_updatesAndClearsTransform() {
        val viewModel = MainScreenViewModel()
        viewModel.onTransform(zoomChange = 2f, panX = 40f, panY = -20f, rotationChange = 15f)
        assertTrue(viewModel.uiState.value.isTransformed)
        assertEquals(2f, viewModel.uiState.value.scale)
        assertEquals(40f, viewModel.uiState.value.offsetX)
        assertEquals(-20f, viewModel.uiState.value.offsetY)
        assertEquals(15f, viewModel.uiState.value.rotation)

        viewModel.onResetTransform()
        assertFalse(viewModel.uiState.value.isTransformed)
        assertEquals(1f, viewModel.uiState.value.scale)
    }

    @Test
    fun lockedState_ignoresTransforms() {
        val viewModel = MainScreenViewModel()
        viewModel.onSetLocked(true)
        viewModel.onTransform(zoomChange = 2f, panX = 50f, panY = 50f, rotationChange = 30f)

        assertFalse(viewModel.uiState.value.isTransformed)
        assertEquals(1f, viewModel.uiState.value.scale)
    }

    @Test
    fun arLockToggle_updatesStateAndIgnoresStaleStatusAfterDisable() {
        val viewModel = MainScreenViewModel()

        viewModel.onToggleArLock()
        assertTrue(viewModel.uiState.value.arLockEnabled)
        assertEquals(ArSurfaceState.INITIALIZING, viewModel.uiState.value.arStatus.state)

        viewModel.onArStatusChanged(ArSurfaceStatus(ArSurfaceState.ANCHORED))
        assertEquals(ArSurfaceState.ANCHORED, viewModel.uiState.value.arStatus.state)
        assertTrue(viewModel.uiState.value.isArOverlayRendering)

        viewModel.onArPlaneOffsetChanged(true)
        assertTrue(viewModel.uiState.value.isTransformed)

        // Turning AR off clears anchor state and plane offset
        viewModel.onToggleArLock()
        assertFalse(viewModel.uiState.value.arLockEnabled)
        assertEquals(ArSurfaceState.INACTIVE, viewModel.uiState.value.arStatus.state)
        assertFalse(viewModel.uiState.value.isArOverlayRendering)
        assertFalse(viewModel.uiState.value.arHasPlaneOffset)

        // Stale async AR callback after turning AR off must be ignored
        viewModel.onArStatusChanged(ArSurfaceStatus(ArSurfaceState.ANCHORED))
        assertEquals(ArSurfaceState.INACTIVE, viewModel.uiState.value.arStatus.state)
    }

    @Test
    fun arUnavailable_disablesArAndShowsMessage() {
        val viewModel = MainScreenViewModel()
        viewModel.onToggleArLock()
        assertTrue(viewModel.uiState.value.arLockEnabled)

        viewModel.onArUnavailable("AR Surface Pin isn't supported on this device")

        val state = viewModel.uiState.value
        assertFalse(state.arLockEnabled)
        assertEquals(ArSurfaceState.INACTIVE, state.arStatus.state)
        assertEquals("AR Surface Pin isn't supported on this device", state.errorMessage)

        viewModel.onDismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun arRepin_bumpsVersionAndResetsOffsetOnlyWhenUnlockedAndEnabled() {
        val viewModel = MainScreenViewModel()
        // Ignored while AR is disabled
        viewModel.onArRepin()
        assertEquals(0, viewModel.uiState.value.arAnchorVersion)

        viewModel.onToggleArLock()
        viewModel.onArStatusChanged(ArSurfaceStatus(ArSurfaceState.ANCHORED))
        viewModel.onArPlaneOffsetChanged(true)

        // Ignored while touch-locked
        viewModel.onSetLocked(true)
        viewModel.onArRepin()
        assertEquals(0, viewModel.uiState.value.arAnchorVersion)

        // Succeeds when unlocked
        viewModel.onSetLocked(false)
        viewModel.onArRepin()
        assertEquals(1, viewModel.uiState.value.arAnchorVersion)
        assertFalse(viewModel.uiState.value.arHasPlaneOffset)
        assertEquals(ArSurfaceState.SEARCHING, viewModel.uiState.value.arStatus.state)
    }

    @Test
    fun resetTransform_clearsArPlaneOffsetAndBumpsResetVersion() {
        val viewModel = MainScreenViewModel()
        viewModel.onToggleArLock()
        viewModel.onArPlaneOffsetChanged(true)
        viewModel.onTransform(zoomChange = 1.5f, panX = 0f, panY = 0f, rotationChange = 20f)
        assertTrue(viewModel.uiState.value.isTransformed)

        val beforeReset = viewModel.uiState.value.transformResetVersion
        viewModel.onResetTransform()

        val state = viewModel.uiState.value
        assertFalse(state.isTransformed)
        assertFalse(state.arHasPlaneOffset)
        assertEquals(beforeReset + 1, state.transformResetVersion)
    }
}
