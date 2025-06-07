package com.example.theone.domain

import com.example.theone.model.SampleMetadata
import com.example.theone.features.drumtrack.model.PadSettings // Import for PadSettings
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

    private lateinit var padSettings1: PadSettings
    private lateinit var padSettings2: PadSettings


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

    // --- Tests for PadSettings ---
    @Before
    fun setUpPadSettings() { // Separate @Before for PadSettings or include in main setUp if always needed
        projectManager = ProjectManagerImpl() // Ensure fresh instance
        padSettings1 = PadSettings(padSettingsId = "pad1_id", volume = 0.7f)
        padSettings2 = PadSettings(padSettingsId = "pad2_id", volume = 0.9f)
    }

    @Test
    fun `savePadSettings_addsOrUpdatesInMemoryMap`() = runTest {
        assertTrue(projectManager.getAllPadSettings().isEmpty())

        projectManager.savePadSettings(padSettings1)
        var allSettings = projectManager.getAllPadSettings()
        assertEquals(1, allSettings.size)
        assertEquals(padSettings1, allSettings[padSettings1.padSettingsId])

        val updatedPadSettings1 = padSettings1.copy(volume = 0.6f)
        projectManager.savePadSettings(updatedPadSettings1)
        allSettings = projectManager.getAllPadSettings()
        assertEquals(1, allSettings.size) // Should update, not add new
        assertEquals(0.6f, allSettings[padSettings1.padSettingsId]?.volume)

        projectManager.savePadSettings(padSettings2)
        allSettings = projectManager.getAllPadSettings()
        assertEquals(2, allSettings.size)
        assertEquals(padSettings2, allSettings[padSettings2.padSettingsId])

        // Check StateFlow
        val flowSettings = projectManager.padSettingsMap.first()
        assertEquals(2, flowSettings.size)
        assertEquals(padSettings2, flowSettings[padSettings2.padSettingsId])
    }

    @Test
    fun `loadPadSettings_retrievesFromInMemoryMap_orReturnsNull`() = runTest {
        projectManager.savePadSettings(padSettings1)
        projectManager.savePadSettings(padSettings2)

        val loaded1 = projectManager.loadPadSettings(padSettings1.padSettingsId)
        assertEquals(padSettings1, loaded1)

        val loaded2 = projectManager.loadPadSettings(padSettings2.padSettingsId)
        assertEquals(padSettings2, loaded2)

        val notFound = projectManager.loadPadSettings("non_existent_id")
        assertNull(notFound)
    }

    @Test
    fun `getAllPadSettings_returnsCopyOfInMemoryMap`() = runTest {
        assertTrue(projectManager.getAllPadSettings().isEmpty())

        projectManager.savePadSettings(padSettings1)
        projectManager.savePadSettings(padSettings2)

        val allSettings = projectManager.getAllPadSettings()
        assertEquals(2, allSettings.size)
        assertEquals(padSettings1, allSettings[padSettings1.padSettingsId])
        assertEquals(padSettings2, allSettings[padSettings2.padSettingsId])

        // Ensure it's a copy (though current implementation returns .toMap() which is a shallow copy)
        // For a deeper test of "copy", one might modify the returned map and check original,
        // but for StateFlow value (which is immutable Map by toMap()), this is sufficient.
        val originalMap = projectManager.padSettingsMap.value
        val returnedMap = projectManager.getAllPadSettings()
        assertEquals(originalMap, returnedMap)
        assertNotSame("Should return a copy, not the same instance of the map if mutable", originalMap, returnedMap)
        // Note: .toMap() on a StateFlow's value (which is already a Map) might return the same instance if the underlying map is already immutable.
        // The important part is that the internal _padSettingsMap is not directly exposed.
    }
}
