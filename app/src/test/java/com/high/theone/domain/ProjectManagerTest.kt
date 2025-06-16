package com.high.theone.domain

import com.high.theone.model.SampleMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ProjectManagerTest {

    private lateinit var projectManager: ProjectManagerImpl // Test the implementation

    private val sample1 = SampleMetadata(uri = "uri1", duration = 1000L, name = "Sample1", trimStartMs = 0L, trimEndMs = 1000L)
    private val sample2 = SampleMetadata(uri = "uri2", duration = 2000L, name = "Sample2", trimStartMs = 100L, trimEndMs = 1500L)

    @Before
    fun setUp() {
        projectManager = ProjectManagerImpl()
    }

    @Test
    fun `addSampleToPool adds a new sample`() = runTest {
        assertTrue(projectManager.getSamplesFromPool().isEmpty())
        projectManager.addSampleToPool(sample1)

        val pool = projectManager.getSamplesFromPool()
        assertEquals(1, pool.size)
        assertEquals(sample1, pool[0])

        val poolFlow = projectManager.samplePool.first()
        assertEquals(1, poolFlow.size)
        assertEquals(sample1, poolFlow[0])
    }

    @Test
    fun `addSampleToPool updates existing sample if URI matches`() = runTest {
        projectManager.addSampleToPool(sample1)
        val updatedSample1 = sample1.copy(name = "Updated Sample1", duration = 1200L)

        projectManager.addSampleToPool(updatedSample1) // URI is the same

        val pool = projectManager.getSamplesFromPool()
        assertEquals(1, pool.size) // Size should still be 1
        assertEquals(updatedSample1, pool[0]) // Should be the updated version
        assertEquals("Updated Sample1", pool[0].name)
        assertEquals(1200L, pool[0].duration)
    }

    @Test
    fun `getSamplesFromPool returns current pool`() = runTest {
        projectManager.addSampleToPool(sample1)
        projectManager.addSampleToPool(sample2)

        val pool = projectManager.getSamplesFromPool()
        assertEquals(2, pool.size)
        assertTrue(pool.contains(sample1))
        assertTrue(pool.contains(sample2))
    }

    @Test
    fun `updateSampleMetadataNonSuspend modifies an existing sample`() = runTest {
        projectManager.addSampleToPool(sample1)
        projectManager.addSampleToPool(sample2)

        val updatedSample2 = sample2.copy(name = "Modified Sample2", trimEndMs = 1800L)
        projectManager.updateSampleMetadataNonSuspend(updatedSample2)

        val pool = projectManager.getSamplesFromPool()
        val retrievedSample = pool.find { it.uri == sample2.uri }
        assertNotNull(retrievedSample)
        assertEquals("Modified Sample2", retrievedSample?.name)
        assertEquals(1800L, retrievedSample?.trimEndMs)

        // Check that other samples are not affected
        val retrievedSample1 = pool.find { it.uri == sample1.uri }
        assertEquals(sample1, retrievedSample1)
    }

    @Test
    fun `updateSampleMetadataNonSuspend does nothing if sample URI not found`() = runTest {
        projectManager.addSampleToPool(sample1)
        val nonExistentSample = SampleMetadata(uri = "uri_non_existent", duration = 500L, name = "NonExistent")

        projectManager.updateSampleMetadataNonSuspend(nonExistentSample)

        val pool = projectManager.getSamplesFromPool()
        assertEquals(1, pool.size) // Pool should be unchanged
        assertEquals(sample1, pool[0])
        assertNull(pool.find { it.uri == nonExistentSample.uri })
    }

    // Test interface methods briefly (they call the core logic tested above)
    @Test
    fun `interface addSampleToPool suspend works`() = runTest {
         val result = projectManager.addSampleToPool("Interfaced Sample", "uri_interface", false)
         assertNotNull(result)
         assertEquals("uri_interface", result?.uri)
         assertEquals("Interfaced Sample", result?.name)
         assertTrue(projectManager.getSamplesFromPool().any { it.uri == "uri_interface" })
    }

    @Test
    fun `interface updateSampleMetadata suspend works`() = runTest {
        projectManager.addSampleToPool(sample1)
        val updatedViaInterface = sample1.copy(name = "Updated via Interface")
        val success = projectManager.updateSampleMetadata(updatedViaInterface)

        assertTrue(success)
        val retrieved = projectManager.getSamplesFromPool().find {it.uri == sample1.uri}
        assertEquals("Updated via Interface", retrieved?.name)
    }

    @Test
    fun `interface getSampleById works for existing sample`() = runTest {
        projectManager.addSampleToPool(sample1)
        val retrieved = projectManager.getSampleById(sample1.uri)
        assertEquals(sample1, retrieved)
    }

    @Test
    fun `interface getSampleById returns null for non-existing sample`() = runTest {
        val retrieved = projectManager.getSampleById("uri_non_existent_id")
        assertNull(retrieved)
    }
}
