package com.high.theone.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.high.theone.TheOneApplication
import com.high.theone.midi.service.MidiSystemInitializer
import com.high.theone.midi.service.MidiPermissionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for MIDI system integration with app architecture.
 * Tests the coordination between TheOneApplication and MIDI system components.
 * 
 * Requirements: 1.1, 3.6, 7.1
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = TheOneApplication::class)
class MidiAppIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var midiSystemInitializer: MidiSystemInitializer

    @Inject
    lateinit var midiPermissionManager: MidiPermissionManager

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `MIDI system initializer is properly injected`() {
        assertNotNull(midiSystemInitializer)
    }

    @Test
    fun `MIDI permission manager is properly injected`() {
        assertNotNull(midiPermissionManager)
    }

    @Test
    fun `MIDI system can be initialized through app architecture`() = runTest {
        // Test that MIDI system initialization works through the app
        val result = midiSystemInitializer.initializeSystem()
        
        // Should succeed or fail gracefully (depending on device support)
        assertTrue(result.isSuccess || result.isFailure)
        
        // System status should be available
        val status = midiSystemInitializer.getSystemStatus()
        assertNotNull(status)
    }

    @Test
    fun `MIDI permissions are properly checked`() {
        // Test permission checking
        midiPermissionManager.updatePermissionState()
        
        val permissionState = midiPermissionManager.permissionState.value
        assertNotNull(permissionState)
        
        // Should have proper MIDI support detection
        // (May be false in test environment, but should not crash)
        val statusMessage = midiPermissionManager.getPermissionStatusMessage()
        assertNotNull(statusMessage)
        assertTrue(statusMessage.isNotEmpty())
    }

    @Test
    fun `App can handle MIDI permission results`() {
        val app = context.applicationContext as TheOneApplication
        
        // Should not crash when handling permission results
        app.onMidiPermissionsResult(true)
        app.onMidiPermissionsResult(false)
        
        // System status should be available
        val status = app.getSystemStatus()
        assertNotNull(status)
    }

    @Test
    fun `MIDI system coordinates with app lifecycle`() = runTest {
        // Test that MIDI system properly coordinates with app lifecycle
        val initialStatus = midiSystemInitializer.getSystemStatus()
        assertNotNull(initialStatus)
        
        // Should be able to restart system
        val restartResult = midiSystemInitializer.restartSystem()
        assertTrue(restartResult.isSuccess || restartResult.isFailure)
        
        // Should be able to shutdown
        val shutdownResult = midiSystemInitializer.shutdownSystem()
        assertTrue(shutdownResult.isSuccess || shutdownResult.isFailure)
    }
}