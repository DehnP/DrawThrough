package com.example.drawthrough.ui.main

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
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
}
