package com.example.theone.domain

import com.example.theone.model.Sample
import com.example.theone.model.SampleMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectManagerImplTest {

    private lateinit var projectManager: ProjectManagerImpl

    @Before
    fun setUp() {
        projectManager = ProjectManagerImpl()
    }

    @Test
    fun `loadWavFile placeholder returns success with dummy sample`() = runTest {
        val fileUri = "test://dummy.wav"
        val result = projectManager.loadWavFile(fileUri)

        assertTrue("loadWavFile should return success", result.isSuccess)
        val sample = result.getOrNull()
        assertNotNull("Sample should not be null on success", sample)
        assertEquals("Sample URI should match input URI", fileUri, sample!!.metadata.uri)
        assertEquals("Dummy sample name should be set", "Loaded Sample: dummy.wav", sample.metadata.name)
        assertTrue("Dummy audio data should be present", sample.audioData.isNotEmpty())
    }

    @Test
    fun `saveWavFile placeholder returns success`() = runTest {
        val dummyMetadata = SampleMetadata(uri = "test_uri", duration = 100L, name = "TestSample")
        val dummyAudioData = FloatArray(100)
        val dummySample = Sample(metadata = dummyMetadata, audioData = dummyAudioData)
        val fileUri = "test://output.wav"

        val result = projectManager.saveWavFile(dummySample, fileUri)

        assertTrue("saveWavFile should return success", result.isSuccess)
    }
}
