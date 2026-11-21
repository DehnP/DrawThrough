package com.example.drawthrough.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.drawthrough.ui.theme.DrawThroughTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.drawthrough.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
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
    }

    @Test
    fun controls_areDisplayed() {
        composeTestRule.onNodeWithText("DrawThrough").assertExists()
        composeTestRule.onNodeWithText("Brightness 100%").assertExists()
        composeTestRule.onNodeWithText("Lock").assertExists()
    }
}
