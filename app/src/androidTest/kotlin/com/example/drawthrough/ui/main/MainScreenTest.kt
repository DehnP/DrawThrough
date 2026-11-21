package com.example.drawthrough.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.drawthrough.ar.ArSurfaceState
import com.example.drawthrough.ar.ArSurfaceStatus
import com.example.drawthrough.ui.theme.DrawThroughTheme
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.drawthrough.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun controls_areDisplayed() {
        composeTestRule.setContent {
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
                    onToggleArLock = {},
                    onArRepin = {},
                    onSetLocked = {},
                    onTransform = { _, _, _, _ -> },
                    onResetTransform = {},
                )
            }
        }

        composeTestRule.onNodeWithText("DrawThrough").assertExists()
        composeTestRule.onNodeWithText("Opacity 50%").assertExists()
        composeTestRule.onNodeWithText("AR Pin").assertExists()
        composeTestRule.onNodeWithText("Lock").assertExists()
    }

    @Test
    fun arPinnedMode_displaysStatusAndRepinChips() {
        composeTestRule.setContent {
            DrawThroughTheme {
                DrawThroughContent(
                    uiState = MainScreenUiState(
                        arLockEnabled = true,
                        arStatus = ArSurfaceStatus(state = ArSurfaceState.ANCHORED),
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
                )
            }
        }

        composeTestRule.onNodeWithText("Pinned to surface").assertExists()
        composeTestRule.onNodeWithText("Re-pin").assertExists()
    }
}
