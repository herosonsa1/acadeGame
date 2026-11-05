package com.jjalddalmark.minigamerullet

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeTitleIsDisplayed() {
        composeTestRule.onNodeWithText("미니게임 - 만능 룰렛 & 골프 복불복").assertExists()
    }
}
