package com.high.theone.model

import org.junit.Test
import org.junit.Assert.*

class SampleMetadataTest {

    @Test
    fun `SampleMetadata should have default values`() {
        val sampleMetadata = SampleMetadata()
        
        assertEquals(0L, sampleMetadata.durationMs)
        assertEquals(44100, sampleMetadata.sampleRate)
        assertEquals(1, sampleMetadata.channels)
        assertEquals(60, sampleMetadata.rootNote)
        assertTrue(sampleMetadata.tags.isEmpty())
        assertEquals(0L, sampleMetadata.trimStartMs)
        assertEquals(0L, sampleMetadata.trimEndMs)
    }

    @Test
    fun `SampleMetadata should accept custom values`() {
        val sampleMetadata = SampleMetadata(
            durationMs = 5000L,
            sampleRate = 48000,
            channels = 2,
            rootNote = 72,
            tags = listOf("drum", "kick"),
            trimStartMs = 100L,
            trimEndMs = 4900L
        )
        
        assertEquals(5000L, sampleMetadata.durationMs)
        assertEquals(48000, sampleMetadata.sampleRate)
        assertEquals(2, sampleMetadata.channels)
        assertEquals(72, sampleMetadata.rootNote)
        assertEquals(listOf("drum", "kick"), sampleMetadata.tags)
        assertEquals(100L, sampleMetadata.trimStartMs)
        assertEquals(4900L, sampleMetadata.trimEndMs)
    }
}
