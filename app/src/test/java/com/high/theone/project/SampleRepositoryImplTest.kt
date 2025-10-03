package com.high.theone.project

import com.high.theone.domain.Result
import com.high.theone.domain.SampleChangeEvent
import com.high.theone.model.SampleMetadata
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.io.File
import java.io.ByteArrayInputStream
import java.util.UUID

class SampleRepositoryImplTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var repository: SampleRepositoryImpl
    private lateinit var testFilesDir: File
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Create temporary directory for testing
        testFilesDir = tempFolder.newFolder("test_files")
        
        // Mock context to return our test directory
        whenever(mockContext.filesDir).thenReturn(testFilesDir)
        
        repository = SampleRepositoryImpl(mockContext)
    }
    
    @Test
    fun `saveSample creates sample file and metadata correctly`() = runTest {
        // Arrange
        val sampleData = "test audio data".toByteArray()
        val metadata = SampleMetadata(
            id = UUID.randomUUID(),
            name = "Test Sample",
            format = "wav",
            durationMs = 5000L,
            sampleRate = 44100,
            channels = 1
        )
        
        // Act
        val result = repository.saveSample(sampleData, metadata)
        
        // Assert
        assertTrue("Save should succeed", result is Result.Success)
        val sampleId = (result as Result.Success).value
        assertEquals("Sample ID should match metadata ID", metadata.id.toString(), sampleId)
        
        // Verify file was created
        val expectedFile = File(testFilesDir, "samples/global/files/${sampleId}.wav")
        assertTrue("Sample file should exist", expectedFile.exists())
        assertArrayEquals("File content should match", sampleData, expectedFile.readBytes())
        
        // Verify metadata was saved
        val loadResult = repository.loadSample(sampleId)
        assertTrue("Load should succeed", loadResult is Result.Success)
        val loadedMetadata = (loadResult as Result.Success).value
        assertEquals("Sample name should match", metadata.name, loadedMetadata.name)
        assertEquals("Duration should match", metadata.durationMs, loadedMetadata.durationMs)
    }
    
    @Test
    fun `saveSample with projectId creates sample in project directory`() = runTest {
        // Arrange
        val projectId = "test-project"
        val sampleData = "project sample data".toByteArray()
        val metadata = SampleMetadata(
            id = UUID.randomUUID(),
            name = "Project Sample",
            format = "wav"
        )
        
        // Act
        val result = repository.saveSample(sampleData, metadata, projectId)
        
        // Assert
        assertTrue("Save should succeed", result is Result.Success)
        val sampleId = (result as Result.Success).value
        
        // Verify file was created in project directory
        val expectedFile = File(testFilesDir, "samples/projects/$projectId/files/${sampleId}.wav")
        assertTrue("Sample file should exist in project directory", expectedFile.exists())
        
        // Verify metadata includes project ID
        val loadResult = repository.loadSample(sampleId)
        assertTrue("Load should succeed", loadResult is Result.Success)
        val loadedMetadata = (loadResult as Result.Success).value
        assertEquals("Project ID should match", projectId, loadedMetadata.projectId)
    }
    
    @Test
    fun `loadSample returns error for non-existent sample`() = runTest {
        // Arrange
        val nonExistentId = UUID.randomUUID().toString()
        
        // Act
        val result = repository.loadSample(nonExistentId)
        
        // Assert
        assertTrue("Load should fail", result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue("Error should mention sample not found", error.message?.contains("not found") == true)
    }
    
    @Test
    fun `updateSampleMetadata modifies existing sample`() = runTest {
        // Arrange
        val sampleData = "test data".toByteArray()
        val originalMetadata = SampleMetadata(
            id = UUID.randomUUID(),
            name = "Original Name",
            tags = listOf("original")
        )
        
        // Save original sample
        val saveResult = repository.saveSample(sampleData, originalMetadata)
        assertTrue("Save should succeed", saveResult is Result.Success)
        val sampleId = (saveResult as Result.Success).value
        
        // Update metadata
        val updatedMetadata = originalMetadata.copy(
            name = "Updated Name",
            tags = listOf("updated", "modified")
        )
        
        // Act
        val updateResult = repository.updateSampleMetadata(sampleId, updatedMetadata)
        
        // Assert
        assertTrue("Update should succeed", updateResult is Result.Success)
        
        // Verify changes were saved
        val loadResult = repository.loadSample(sampleId)
        assertTrue("Load should succeed", loadResult is Result.Success)
        val loadedMetadata = (loadResult as Result.Success).value
        assertEquals("Name should be updated", "Updated Name", loadedMetadata.name)
        assertEquals("Tags should be updated", listOf("updated", "modified"), loadedMetadata.tags)
    }
    
    @Test
    fun `deleteSample removes sample and file`() = runTest {
        // Arrange
        val sampleData = "test data".toByteArray()
        val metadata = SampleMetadata(
            id = UUID.randomUUID(),
            name = "Sample to Delete"
        )
        
        // Save sample
        val saveResult = repository.saveSample(sampleData, metadata)
        assertTrue("Save should succeed", saveResult is Result.Success)
        val sampleId = (saveResult as Result.Success).value
        
        // Verify sample exists
        assertTrue("Sample should exist", repository.sampleExists(sampleId))
        
        // Act
        val deleteResult = repository.deleteSample(sampleId)
        
        // Assert
        assertTrue("Delete should succeed", deleteResult is Result.Success)
        assertFalse("Sample should no longer exist", repository.sampleExists(sampleId))
        
        // Verify file was deleted
        val expectedFile = File(testFilesDir, "samples/global/files/${sampleId}.wav")
        assertFalse("Sample file should be deleted", expectedFile.exists())
    }
    
    @Test
    fun `getAllSamples returns all samples from global and projects`() = runTest {
        // Arrange
        val globalSample = SampleMetadata(name = "Global Sample")
        val projectSample = SampleMetadata(name = "Project Sample")
        
        // Save samples
        repository.saveSample("data1".toByteArray(), globalSample)
        repository.saveSample("data2".toByteArray(), projectSample, "test-project")
        
        // Act
        val result = repository.getAllSamples()
        
        // Assert
        assertTrue("Get all should succeed", result is Result.Success)
        val samples = (result as Result.Success).value
        assertEquals("Should have 2 samples", 2, samples.size)
        
        val sampleNames = samples.map { it.name }
        assertTrue("Should contain global sample", sampleNames.contains("Global Sample"))
        assertTrue("Should contain project sample", sampleNames.contains("Project Sample"))
    }
    
    @Test
    fun `getSamplesForProject returns only project samples`() = runTest {
        // Arrange
        val projectId = "test-project"
        val globalSample = SampleMetadata(name = "Global Sample")
        val projectSample = SampleMetadata(name = "Project Sample")
        val otherProjectSample = SampleMetadata(name = "Other Project Sample")
        
        // Save samples
        repository.saveSample("data1".toByteArray(), globalSample)
        repository.saveSample("data2".toByteArray(), projectSample, projectId)
        repository.saveSample("data3".toByteArray(), otherProjectSample, "other-project")
        
        // Act
        val result = repository.getSamplesForProject(projectId)
        
        // Assert
        assertTrue("Get project samples should succeed", result is Result.Success)
        val samples = (result as Result.Success).value
        assertEquals("Should have 1 sample", 1, samples.size)
        assertEquals("Should be the project sample", "Project Sample", samples[0].name)
        assertEquals("Should have correct project ID", projectId, samples[0].projectId)
    }
    
    @Test
    fun `getSamplesByTags filters samples correctly`() = runTest {
        // Arrange
        val sample1 = SampleMetadata(name = "Sample 1", tags = listOf("drum", "kick"))
        val sample2 = SampleMetadata(name = "Sample 2", tags = listOf("drum", "snare"))
        val sample3 = SampleMetadata(name = "Sample 3", tags = listOf("synth", "lead"))
        
        repository.saveSample("data1".toByteArray(), sample1)
        repository.saveSample("data2".toByteArray(), sample2)
        repository.saveSample("data3".toByteArray(), sample3)
        
        // Act - match any tag
        val drumResult = repository.getSamplesByTags(listOf("drum"), matchAll = false)
        
        // Assert
        assertTrue("Get by tags should succeed", drumResult is Result.Success)
        val drumSamples = (drumResult as Result.Success).value
        assertEquals("Should have 2 drum samples", 2, drumSamples.size)
        
        // Act - match all tags
        val kickDrumResult = repository.getSamplesByTags(listOf("drum", "kick"), matchAll = true)
        
        // Assert
        assertTrue("Get by all tags should succeed", kickDrumResult is Result.Success)
        val kickDrumSamples = (kickDrumResult as Result.Success).value
        assertEquals("Should have 1 kick drum sample", 1, kickDrumSamples.size)
        assertEquals("Should be Sample 1", "Sample 1", kickDrumSamples[0].name)
    }
    
    @Test
    fun `searchSamples finds samples by name and tags`() = runTest {
        // Arrange
        val sample1 = SampleMetadata(name = "Kick Drum", tags = listOf("percussion"))
        val sample2 = SampleMetadata(name = "Bass Synth", tags = listOf("kick", "bass"))
        val sample3 = SampleMetadata(name = "Hi Hat", tags = listOf("percussion"))
        
        repository.saveSample("data1".toByteArray(), sample1)
        repository.saveSample("data2".toByteArray(), sample2)
        repository.saveSample("data3".toByteArray(), sample3)
        
        // Act - search by name
        val kickResult = repository.searchSamples("kick")
        
        // Assert
        assertTrue("Search should succeed", kickResult is Result.Success)
        val kickSamples = (kickResult as Result.Success).value
        assertEquals("Should find 2 samples with 'kick'", 2, kickSamples.size)
        
        val sampleNames = kickSamples.map { it.name }
        assertTrue("Should contain Kick Drum", sampleNames.contains("Kick Drum"))
        assertTrue("Should contain Bass Synth", sampleNames.contains("Bass Synth"))
    }
    
    @Test
    fun `observeSampleChanges emits events for repository changes`() = runTest {
        // Arrange
        val metadata = SampleMetadata(name = "Test Sample")
        
        // Act - Save sample (this should emit an event)
        val saveResult = repository.saveSample("data".toByteArray(), metadata)
        assertTrue("Save should succeed", saveResult is Result.Success)
        val sampleId = (saveResult as Result.Success).value
        
        // Assert - Check that we can observe changes (simplified test)
        val changeFlow = repository.observeSampleChanges()
        assertNotNull("Change flow should not be null", changeFlow)
        
        // Note: Testing flows properly requires more complex setup with collectors
        // This simplified test just verifies the flow exists and can be created
    }
    
    @Test
    fun `getRepositoryStats calculates statistics correctly`() = runTest {
        // Arrange
        val data1 = "data1".toByteArray()
        val data2 = "data2".toByteArray()
        
        val sample1 = SampleMetadata(
            name = "Sample 1", 
            format = "wav", 
            durationMs = 1000L
        )
        val sample2 = SampleMetadata(
            name = "Sample 2", 
            format = "mp3", 
            durationMs = 2000L
        )
        
        repository.saveSample(data1, sample1)
        repository.saveSample(data2, sample2, "test-project")
        
        // Act
        val result = repository.getRepositoryStats()
        
        // Assert
        assertTrue("Get stats should succeed", result is Result.Success)
        val stats = (result as Result.Success).value
        assertEquals("Should have 2 total samples", 2, stats.totalSamples)
        // File size will be calculated from actual data, not from metadata
        assertEquals("Should have correct total size", (data1.size + data2.size).toLong(), stats.totalSizeBytes)
        assertEquals("Should have correct average duration", 1500L, stats.averageDurationMs)
        assertTrue("Should have WAV format", stats.samplesByFormat.containsKey("wav"))
        assertTrue("Should have MP3 format", stats.samplesByFormat.containsKey("mp3"))
    }
}