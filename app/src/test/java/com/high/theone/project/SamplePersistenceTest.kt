package com.high.theone.project

import com.high.theone.domain.Result
import com.high.theone.model.SampleMetadata
import android.content.Context
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
import java.util.UUID

/**
 * Tests for the enhanced sample persistence system (Task 2.3).
 * Tests JSON-based metadata storage, file organization, indexing, and path validation.
 */
class SamplePersistenceTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var repository: SampleRepositoryImpl
    private lateinit var testProjectId: String
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock context to use temporary folder
        whenever(mockContext.filesDir).thenReturn(tempFolder.root)
        
        repository = SampleRepositoryImpl(mockContext)
        testProjectId = "test-project-${UUID.randomUUID()}"
    }
    
    @Test
    fun `repository creates proper directory structure`() = runTest {
        // Arrange
        val globalSample = SampleMetadata(
            name = "Global Sample",
            tags = listOf("global", "test")
        )
        val projectSample = SampleMetadata(
            name = "Project Sample",
            tags = listOf("project", "test"),
            projectId = testProjectId
        )
        
        // Act
        repository.saveSample("global-data".toByteArray(), globalSample)
        repository.saveSample("project-data".toByteArray(), projectSample, testProjectId)
        
        // Assert - Check directory structure
        val samplesDir = File(tempFolder.root, "samples")
        assertTrue("Samples directory should exist", samplesDir.exists())
        
        val globalDir = File(samplesDir, "global")
        assertTrue("Global directory should exist", globalDir.exists())
        assertTrue("Global metadata should exist", File(globalDir, "metadata.json").exists())
        assertTrue("Global files directory should exist", File(globalDir, "files").exists())
        
        val projectDir = File(samplesDir, "projects/$testProjectId")
        assertTrue("Project directory should exist", projectDir.exists())
        assertTrue("Project metadata should exist", File(projectDir, "metadata.json").exists())
        assertTrue("Project files directory should exist", File(projectDir, "files").exists())
    }
    
    @Test
    fun `metadata persistence works correctly`() = runTest {
        // Arrange
        val sample = SampleMetadata(
            name = "Test Sample",
            durationMs = 5000L,
            sampleRate = 44100,
            channels = 2,
            format = "wav",
            tags = listOf("test", "drum", "kick"),
            projectId = testProjectId
        )
        
        // Act
        val saveResult = repository.saveSample("test-data".toByteArray(), sample, testProjectId)
        
        // Assert
        assertTrue("Save should succeed", saveResult is Result.Success)
        val sampleId = (saveResult as Result.Success).value
        
        // Load and verify
        val loadResult = repository.loadSample(sampleId)
        assertTrue("Load should succeed", loadResult is Result.Success)
        
        val loadedSample = (loadResult as Result.Success).value
        assertEquals("Name should match", sample.name, loadedSample.name)
        assertEquals("Duration should match", sample.durationMs, loadedSample.durationMs)
        assertEquals("Sample rate should match", sample.sampleRate, loadedSample.sampleRate)
        assertEquals("Channels should match", sample.channels, loadedSample.channels)
        assertEquals("Format should match", sample.format, loadedSample.format)
        assertEquals("Tags should match", sample.tags, loadedSample.tags)
        assertEquals("Project ID should match", testProjectId, loadedSample.projectId)
        assertNotNull("File path should be set", loadedSample.filePath)
        assertTrue("File should exist", File(loadedSample.filePath).exists())
    }
    
    @Test
    fun `file path validation works correctly`() = runTest {
        // Arrange
        val sample = SampleMetadata(
            name = "Test Sample",
            projectId = testProjectId
        )
        
        // Act - Save sample
        val saveResult = repository.saveSample("test-data".toByteArray(), sample, testProjectId)
        assertTrue("Save should succeed", saveResult is Result.Success)
        val sampleId = (saveResult as Result.Success).value
        
        // Load sample to get file path
        val loadResult = repository.loadSample(sampleId)
        assertTrue("Load should succeed", loadResult is Result.Success)
        val loadedSample = (loadResult as Result.Success).value
        
        // Verify file exists and is readable
        val file = File(loadedSample.filePath)
        assertTrue("File should exist", file.exists())
        assertTrue("File should be readable", file.canRead())
        assertEquals("File size should match", "test-data".toByteArray().size.toLong(), file.length())
    }
    
    @Test
    fun `repository validation and repair works`() = runTest {
        // Arrange - Create some samples
        val sample1 = SampleMetadata(name = "Sample 1", projectId = testProjectId)
        val sample2 = SampleMetadata(name = "Sample 2", projectId = testProjectId)
        
        repository.saveSample("data1".toByteArray(), sample1, testProjectId)
        repository.saveSample("data2".toByteArray(), sample2, testProjectId)
        
        // Act - Validate repository
        val validationResult = repository.validateAndRepairRepository()
        
        // Assert
        assertTrue("Validation should succeed", validationResult is Result.Success)
        val stats = (validationResult as Result.Success).value
        assertEquals("Should have 2 total samples", 2, stats.totalSamples)
        assertEquals("Should have 2 valid samples", 2, stats.validSamples)
        assertEquals("Should have 0 repaired paths", 0, stats.repairedPaths)
        assertEquals("Should have 0 invalid samples", 0, stats.invalidSamples)
        assertEquals("Should have 0 corrupted files", 0, stats.corruptedFiles)
    }
    
    @Test
    fun `sample indexing provides fast lookups`() = runTest {
        // Arrange - Create samples with different characteristics
        val samples = listOf(
            SampleMetadata(name = "Kick Drum", tags = listOf("drum", "kick"), format = "wav"),
            SampleMetadata(name = "Snare Hit", tags = listOf("drum", "snare"), format = "wav"),
            SampleMetadata(name = "Bass Synth", tags = listOf("synth", "bass"), format = "mp3"),
            SampleMetadata(name = "Lead Synth", tags = listOf("synth", "lead"), format = "flac")
        )
        
        // Save all samples
        for (sample in samples) {
            repository.saveSample("test-data".toByteArray(), sample)
        }
        
        // Act & Assert - Test tag-based filtering
        val drumResult = repository.getSamplesByTags(listOf("drum"), matchAll = false)
        assertTrue("Drum search should succeed", drumResult is Result.Success)
        val drumSamples = (drumResult as Result.Success).value
        assertEquals("Should find 2 drum samples", 2, drumSamples.size)
        
        // Test name-based search
        val synthResult = repository.searchSamples("synth")
        assertTrue("Synth search should succeed", synthResult is Result.Success)
        val synthSamples = (synthResult as Result.Success).value
        assertEquals("Should find 2 synth samples", 2, synthSamples.size)
        
        // Test format-based organization
        val allSamplesResult = repository.getAllSamples()
        assertTrue("Get all should succeed", allSamplesResult is Result.Success)
        val allSamples = (allSamplesResult as Result.Success).value
        
        val wavSamples = allSamples.filter { it.format == "wav" }
        val mp3Samples = allSamples.filter { it.format == "mp3" }
        val flacSamples = allSamples.filter { it.format == "flac" }
        
        assertEquals("Should have 2 WAV samples", 2, wavSamples.size)
        assertEquals("Should have 1 MP3 sample", 1, mp3Samples.size)
        assertEquals("Should have 1 FLAC sample", 1, flacSamples.size)
    }
    
    @Test
    fun `project-scoped sample organization works`() = runTest {
        // Arrange
        val project1Id = "project-1"
        val project2Id = "project-2"
        
        val project1Sample = SampleMetadata(name = "Project 1 Sample", projectId = project1Id)
        val project2Sample = SampleMetadata(name = "Project 2 Sample", projectId = project2Id)
        val globalSample = SampleMetadata(name = "Global Sample") // No project ID
        
        // Act
        repository.saveSample("data1".toByteArray(), project1Sample, project1Id)
        repository.saveSample("data2".toByteArray(), project2Sample, project2Id)
        repository.saveSample("data3".toByteArray(), globalSample)
        
        // Assert - Project 1 samples
        val project1Result = repository.getSamplesForProject(project1Id)
        assertTrue("Project 1 query should succeed", project1Result is Result.Success)
        val project1Samples = (project1Result as Result.Success).value
        assertEquals("Project 1 should have 1 sample", 1, project1Samples.size)
        assertEquals("Should be project 1 sample", "Project 1 Sample", project1Samples[0].name)
        
        // Assert - Project 2 samples
        val project2Result = repository.getSamplesForProject(project2Id)
        assertTrue("Project 2 query should succeed", project2Result is Result.Success)
        val project2Samples = (project2Result as Result.Success).value
        assertEquals("Project 2 should have 1 sample", 1, project2Samples.size)
        assertEquals("Should be project 2 sample", "Project 2 Sample", project2Samples[0].name)
        
        // Assert - All samples
        val allResult = repository.getAllSamples()
        assertTrue("Get all should succeed", allResult is Result.Success)
        val allSamples = (allResult as Result.Success).value
        assertEquals("Should have 3 total samples", 3, allSamples.size)
    }
}