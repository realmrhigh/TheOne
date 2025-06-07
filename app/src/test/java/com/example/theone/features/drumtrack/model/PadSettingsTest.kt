package com.example.theone.features.drumtrack.model

import com.example.theone.model.EnvelopeSettings
import com.example.theone.model.LayerTriggerRule
import com.example.theone.model.SampleLayer
import org.junit.Assert.*
import org.junit.Test

class PadSettingsTest {

    @Test
    fun `defaultValues_areCorrect`() {
        val defaultSettings = PadSettings()

        assertNotNull("padSettingsId should not be null", defaultSettings.padSettingsId)
        assertTrue("padSettingsId should not be empty", defaultSettings.padSettingsId.isNotEmpty())
        assertTrue("layers list should be empty by default", defaultSettings.layers.isEmpty())
        assertEquals("Default layerTriggerRule should be VELOCITY", LayerTriggerRule.VELOCITY, defaultSettings.layerTriggerRule)

        // Base sound parameters
        assertEquals("Default volume should be 1.0f", 1.0f, defaultSettings.volume)
        assertEquals("Default pan should be 0.0f", 0.0f, defaultSettings.pan)
        assertEquals("Default tuningCoarse should be 0", 0, defaultSettings.tuningCoarse)
        assertEquals("Default tuningFine should be 0", 0, defaultSettings.tuningFine)

        // Playback mode
        assertEquals("Default playbackMode should be ONE_SHOT", PlaybackMode.ONE_SHOT, defaultSettings.playbackMode)

        // Envelopes
        assertNotNull("ampEnvelope should not be null", defaultSettings.ampEnvelope)
        assertEquals("Default ampEnvelope type should be ADSR", com.example.theone.model.EnvelopeType.ADSR, defaultSettings.ampEnvelope.type)
        assertNull("filterEnvelope should be null by default", defaultSettings.filterEnvelope)
        assertNull("pitchEnvelope should be null by default", defaultSettings.pitchEnvelope)

        // LFOs
        assertTrue("lfos list should be empty by default", defaultSettings.lfos.isEmpty())
    }

    @Test
    fun `addLayer_addsToLayersListAndReturnsLayer`() {
        val padSettings = PadSettings()
        val sampleUri = "test_sample_uri"

        assertTrue("Initial layers list should be empty", padSettings.layers.isEmpty())

        val newLayer = padSettings.addLayer(sampleUri)

        assertEquals("Layers list size should be 1 after adding", 1, padSettings.layers.size)
        assertEquals("Returned layer should be the one added", newLayer, padSettings.layers[0])
        assertEquals("SampleId of the added layer should match", sampleUri, newLayer.sampleId)
        assertNotNull("ID of the added layer should not be null", newLayer.id)
    }

    @Test
    fun `getLayerById_returnsCorrectLayerOrNull`() {
        val padSettings = PadSettings()
        val sampleUri1 = "sample1_uri"
        val sampleUri2 = "sample2_uri"

        val layer1 = padSettings.addLayer(sampleUri1)
        val layer2 = padSettings.addLayer(sampleUri2)

        // Test finding existing layers
        assertEquals("Should find layer1 by its ID", layer1, padSettings.getLayerById(layer1.id))
        assertEquals("Should find layer2 by its ID", layer2, padSettings.getLayerById(layer2.id))

        // Test finding non-existing layer
        assertNull("Should return null for non-existing ID", padSettings.getLayerById("non_existing_id"))
    }

    @Test
    fun `removeLayerById_removesLayerAndReturnsTrue_orFalseIfNotExists`() {
        val padSettings = PadSettings()
        val sampleUri1 = "sample1_uri"
        val sampleUri2 = "sample2_uri"

        val layer1 = padSettings.addLayer(sampleUri1)
        val layer2 = padSettings.addLayer(sampleUri2)

        assertEquals("Layers list size should be 2 initially", 2, padSettings.layers.size)

        // Test removing an existing layer
        val resultRemoveLayer1 = padSettings.removeLayerById(layer1.id)
        assertTrue("removeLayerById should return true for existing layer", resultRemoveLayer1)
        assertEquals("Layers list size should be 1 after removing layer1", 1, padSettings.layers.size)
        assertFalse("Layers list should not contain layer1 after removal", padSettings.layers.contains(layer1))
        assertTrue("Layers list should still contain layer2", padSettings.layers.contains(layer2))

        // Test removing a non-existing layer
        val resultRemoveNonExisting = padSettings.removeLayerById("non_existing_id")
        assertFalse("removeLayerById should return false for non-existing layer", resultRemoveNonExisting)
        assertEquals("Layers list size should still be 1", 1, padSettings.layers.size)

        // Test removing the second layer
        val resultRemoveLayer2 = padSettings.removeLayerById(layer2.id)
        assertTrue("removeLayerById should return true for layer2", resultRemoveLayer2)
        assertTrue("Layers list should be empty after removing layer2", padSettings.layers.isEmpty())
    }
}
