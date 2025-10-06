package com.high.theone.features.compactui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.high.theone.model.TransportState
import com.high.theone.model.MidiSyncStatus
import com.high.theone.model.AudioLevels
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransportControlBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun transportControlBar_displaysCorrectBpm() {
        val testBpm = 140
        
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(bpm = testBpm),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        composeTestRule
            .onNodeWithText(testBpm.toString())
            .assertIsDisplayed()
    }

    @Test
    fun transportControlBar_playButtonShowsCorrectState() {
        var isPlaying = false
        
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(isPlaying = isPlaying),
                onPlayPause = { isPlaying = !isPlaying },
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        // Initially shows play button
        composeTestRule
            .onNodeWithContentDescription("Play")
            .assertIsDisplayed()
    }

    @Test
    fun transportControlBar_recordButtonShowsCorrectState() {
        var isRecording = false
        
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(isRecording = isRecording),
                onPlayPause = {},
                onStop = {},
                onRecord = { isRecording = !isRecording },
                onBpmChange = {}
            )
        }

        // Initially shows record button
        composeTestRule
            .onNodeWithContentDescription("Record")
            .assertIsDisplayed()
    }

    @Test
    fun transportControlBar_bpmControlsWork() {
        var currentBpm = 120
        
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(bpm = currentBpm),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = { newBpm -> currentBpm = newBpm }
            )
        }

        // Test increase BPM
        composeTestRule
            .onNodeWithContentDescription("Increase BPM")
            .performClick()

        // Test decrease BPM
        composeTestRule
            .onNodeWithContentDescription("Decrease BPM")
            .performClick()
    }

    @Test
    fun transportControlBar_showsMidiSyncStatus() {
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(
                    midiSyncStatus = MidiSyncStatus.SYNCED
                ),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("MIDI Synced")
            .assertIsDisplayed()
    }

    @Test
    fun transportControlBar_showsAudioLevels() {
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(
                    audioLevels = AudioLevels(
                        masterLevel = 0.7f,
                        inputLevel = 0.5f
                    )
                ),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription(
                "Audio Levels - Master: 70%, Input: 50%",
                useUnmergedTree = true
            )
            .assertIsDisplayed()
    }

    @Test
    fun transportControlBar_showsBatteryStatus() {
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        // Battery indicator should be present
        composeTestRule
            .onAllNodesWithContentDescription("Battery:", substring = true)
            .assertCountEquals(1)
    }

    @Test
    fun transportControlBar_showsPerformanceStatus() {
        composeTestRule.setContent {
            TransportControlBar(
                transportState = TransportState(),
                onPlayPause = {},
                onStop = {},
                onRecord = {},
                onBpmChange = {}
            )
        }

        // Performance indicator should be present
        composeTestRule
            .onAllNodesWithContentDescription("Performance:", substring = true)
            .assertCountEquals(1)
    }
}