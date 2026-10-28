package com.example.fairwaypicnic

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeTitleIsDisplayed() {
        composeTestRule.onNodeWithText("페어웨이 소풍").assertExists()
    }
}
