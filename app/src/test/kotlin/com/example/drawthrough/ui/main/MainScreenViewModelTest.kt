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
        assertEquals(1.0f, state.brightness)
        assertFalse(state.outlineEnabled)
        assertEquals(ActiveTool.BRIGHTNESS, state.activeTool)
        assertTrue(state.showGrid)
        assertFalse(state.isLocked)
    }

    @Test
    fun brightnessChange_clampsWithinRange() {
        val viewModel = MainScreenViewModel()
        viewModel.onBrightnessChange(0.01f)
        assertEquals(0.1f, viewModel.uiState.value.brightness)

        viewModel.onBrightnessChange(1.5f)
        assertEquals(1.0f, viewModel.uiState.value.brightness)
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
