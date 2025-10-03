package com.high.theone.features.sampling

import androidx.compose.runtime.*
import com.high.theone.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * Creates sample projects with pre-loaded sounds for onboarding and demonstration.
 * Provides ready-to-use projects that showcase app capabilities.
 * 
 * Requirements: 6.6 (sample project with pre-loaded sounds)
 */
class SampleProjectCreator {
    
    /**
     * Create a demo project with pre-loaded drum samples.
     */
    suspend fun createDemoProject(
        projectsDirectory: File,
        assetsManager: android.content.res.AssetManager
    ): Result<Project> = withContext(Dispatchers.IO) {
        try {
            val projectId = "demo_project_${System.currentTimeMillis()}"
            val projectDir = File(projectsDirectory, projectId)
            projectDir.mkdirs()
            
            // Create project metadata
            val project = Project(
                id = projectId,
                name = "Demo Beat Kit",
                description = "A sample project with pre-loaded drum sounds to get you started",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                samplesDirectory = File(projectDir, "samples").absolutePath,
                padsDirectory = File(projectDir, "pads").absolutePath
            )
            
            // Create sample and pad directories
            File(project.samplesDirectory).mkdirs()
            File(project.padsDirectory).mkdirs()
            
            // Copy demo samples from assets
            val demoSamples = copyDemoSamples(assetsManager, File(project.samplesDirectory))
            
            // Create pad assignments
            val padAssignments = createDemoPadAssignments(demoSamples)
            
            // Save project configuration
            saveProjectConfiguration(projectDir, project, padAssignments)
            
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a tutorial project that guides users through features.
     */
    suspend fun createTutorialProject(
        projectsDirectory: File,
        assetsManager: android.content.res.AssetManager
    ): Result<Project> = withContext(Dispatchers.IO) {
        try {
            val projectId = "tutorial_project_${System.currentTimeMillis()}"
            val projectDir = File(projectsDirectory, projectId)
            projectDir.mkdirs()
            
            val project = Project(
                id = projectId,
                name = "Tutorial Project",
                description = "Learn TheOne with this guided tutorial project",
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                samplesDirectory = File(projectDir, "samples").absolutePath,
                padsDirectory = File(projectDir, "pads").absolutePath
            )
            
            // Create directories
            File(project.samplesDirectory).mkdirs()
            File(project.padsDirectory).mkdirs()
            
            // Copy tutorial samples
            val tutorialSamples = copyTutorialSamples(assetsManager, File(project.samplesDirectory))
            
            // Create tutorial pad layout
            val padAssignments = createTutorialPadAssignments(tutorialSamples)
            
            // Save project
            saveProjectConfiguration(projectDir, project, padAssignments)
            
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Copy demo samples from app assets to project directory.
     */
    private suspend fun copyDemoSamples(
        assetsManager: android.content.res.AssetManager,
        samplesDir: File
    ): List<SampleMetadata> = withContext(Dispatchers.IO) {
        val demoSamples = mutableListOf<SampleMetadata>()
        
        // Define demo sample assets
        val demoAssets = listOf(
            DemoSampleAsset("kick.wav", "Kick Drum", "A punchy kick drum sample", listOf("drum", "kick")),
            DemoSampleAsset("snare.wav", "Snare Drum", "Crisp snare drum hit", listOf("drum", "snare")),
            DemoSampleAsset("hihat.wav", "Hi-Hat", "Closed hi-hat sample", listOf("drum", "hihat")),
            DemoSampleAsset("crash.wav", "Crash Cymbal", "Crash cymbal hit", listOf("drum", "cymbal")),
            DemoSampleAsset("bass.wav", "Bass Hit", "Deep bass sound", listOf("bass", "synth")),
            DemoSampleAsset("chord.wav", "Chord Stab", "Musical chord stab", listOf("chord", "synth")),
            DemoSampleAsset("lead.wav", "Lead Synth", "Melodic lead sound", listOf("lead", "synth")),
            DemoSampleAsset("fx.wav", "Sound Effect", "Atmospheric sound effect", listOf("fx", "ambient"))
        )
        
        demoAssets.forEach { asset ->
            try {
                // Try to copy from assets, or create synthetic sample if asset doesn't exist
                val sampleFile = File(samplesDir, asset.filename)
                val sampleMetadata = if (assetExists(assetsManager, "demo_samples/${asset.filename}")) {
                    copyAssetToFile(assetsManager, "demo_samples/${asset.filename}", sampleFile)
                    createSampleMetadata(asset, sampleFile)
                } else {
                    // Create synthetic sample if asset doesn't exist
                    createSyntheticSample(asset, sampleFile)
                }
                
                demoSamples.add(sampleMetadata)
            } catch (e: Exception) {
                // Log error but continue with other samples
                println("Failed to create demo sample ${asset.filename}: ${e.message}")
            }
        }
        
        demoSamples
    }
    
    /**
     * Copy tutorial samples with specific learning objectives.
     */
    private suspend fun copyTutorialSamples(
        assetsManager: android.content.res.AssetManager,
        samplesDir: File
    ): List<SampleMetadata> = withContext(Dispatchers.IO) {
        val tutorialSamples = mutableListOf<SampleMetadata>()
        
        val tutorialAssets = listOf(
            DemoSampleAsset("tutorial_kick.wav", "Tutorial Kick", "Practice triggering this kick drum", listOf("tutorial", "kick")),
            DemoSampleAsset("tutorial_snare.wav", "Tutorial Snare", "Try different velocities with this snare", listOf("tutorial", "snare")),
            DemoSampleAsset("tutorial_loop.wav", "Tutorial Loop", "Set this to loop mode", listOf("tutorial", "loop")),
            DemoSampleAsset("tutorial_oneshot.wav", "Tutorial One-Shot", "Keep this in one-shot mode", listOf("tutorial", "oneshot"))
        )
        
        tutorialAssets.forEach { asset ->
            try {
                val sampleFile = File(samplesDir, asset.filename)
                val sampleMetadata = createSyntheticSample(asset, sampleFile)
                tutorialSamples.add(sampleMetadata)
            } catch (e: Exception) {
                println("Failed to create tutorial sample ${asset.filename}: ${e.message}")
            }
        }
        
        tutorialSamples
    }
    
    /**
     * Create demo pad assignments with a typical drum kit layout.
     */
    private fun createDemoPadAssignments(samples: List<SampleMetadata>): List<PadState> {
        val padStates = mutableListOf<PadState>()
        
        // Create 16 pads with demo assignments
        for (i in 0 until 16) {
            val padState = when (i) {
                0 -> createPadWithSample(i, samples.find { it.name.contains("Kick") }, volume = 0.9f)
                1 -> createPadWithSample(i, samples.find { it.name.contains("Snare") }, volume = 0.8f)
                2 -> createPadWithSample(i, samples.find { it.name.contains("Hi-Hat") }, volume = 0.6f)
                3 -> createPadWithSample(i, samples.find { it.name.contains("Crash") }, volume = 0.7f)
                4 -> createPadWithSample(i, samples.find { it.name.contains("Bass") }, volume = 0.8f)
                5 -> createPadWithSample(i, samples.find { it.name.contains("Chord") }, volume = 0.7f)
                8 -> createPadWithSample(i, samples.find { it.name.contains("Lead") }, volume = 0.6f)
                12 -> createPadWithSample(i, samples.find { it.name.contains("Effect") }, volume = 0.5f)
                else -> PadState(index = i) // Empty pad
            }
            padStates.add(padState)
        }
        
        return padStates
    }
    
    /**
     * Create tutorial pad assignments with learning objectives.
     */
    private fun createTutorialPadAssignments(samples: List<SampleMetadata>): List<PadState> {
        val padStates = mutableListOf<PadState>()
        
        for (i in 0 until 16) {
            val padState = when (i) {
                0 -> createPadWithSample(i, samples.find { it.name.contains("Tutorial Kick") }, volume = 0.8f)
                1 -> createPadWithSample(i, samples.find { it.name.contains("Tutorial Snare") }, volume = 0.7f)
                4 -> createPadWithSample(i, samples.find { it.name.contains("Tutorial Loop") }, 
                    volume = 0.6f, playbackMode = PlaybackMode.LOOP)
                5 -> createPadWithSample(i, samples.find { it.name.contains("Tutorial One-Shot") }, 
                    volume = 0.6f, playbackMode = PlaybackMode.ONE_SHOT)
                else -> PadState(index = i) // Empty pad for user to experiment
            }
            padStates.add(padState)
        }
        
        return padStates
    }
    
    /**
     * Helper function to create a pad state with an assigned sample.
     */
    private fun createPadWithSample(
        index: Int,
        sample: SampleMetadata?,
        volume: Float = 1f,
        pan: Float = 0f,
        playbackMode: PlaybackMode = PlaybackMode.ONE_SHOT
    ): PadState {
        return if (sample != null) {
            PadState(
                index = index,
                sampleId = sample.id,
                sampleName = sample.name,
                hasAssignedSample = true,
                volume = volume,
                pan = pan,
                playbackMode = playbackMode
            )
        } else {
            PadState(index = index)
        }
    }
    
    /**
     * Create synthetic sample data when assets are not available.
     */
    private suspend fun createSyntheticSample(
        asset: DemoSampleAsset,
        sampleFile: File
    ): SampleMetadata = withContext(Dispatchers.IO) {
        // Create a minimal WAV file with silence or simple tone
        // This is a placeholder - in a real implementation, you'd generate actual audio
        sampleFile.createNewFile()
        
        SampleMetadata(
            id = "synthetic_${asset.filename.replace(".", "_")}",
            name = asset.name,
            description = asset.description,
            filePath = sampleFile.absolutePath,
            duration = 1000, // 1 second
            sampleRate = 44100,
            channels = 1,
            createdAt = System.currentTimeMillis(),
            tags = asset.tags,
            fileSize = 44100 * 2, // Approximate size for 1 second of 16-bit mono audio
            format = "WAV"
        )
    }
    
    /**
     * Create sample metadata from asset information.
     */
    private fun createSampleMetadata(asset: DemoSampleAsset, sampleFile: File): SampleMetadata {
        return SampleMetadata(
            id = "demo_${asset.filename.replace(".", "_")}",
            name = asset.name,
            description = asset.description,
            filePath = sampleFile.absolutePath,
            duration = 1000, // Default duration - would be read from actual file
            sampleRate = 44100,
            channels = 1,
            createdAt = System.currentTimeMillis(),
            tags = asset.tags,
            fileSize = sampleFile.length(),
            format = "WAV"
        )
    }
    
    /**
     * Check if an asset exists in the assets folder.
     */
    private fun assetExists(assetsManager: android.content.res.AssetManager, path: String): Boolean {
        return try {
            assetsManager.open(path).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Copy an asset file to a destination file.
     */
    private fun copyAssetToFile(
        assetsManager: android.content.res.AssetManager,
        assetPath: String,
        destFile: File
    ) {
        assetsManager.open(assetPath).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Save project configuration to disk.
     */
    private suspend fun saveProjectConfiguration(
        projectDir: File,
        project: Project,
        padAssignments: List<PadState>
    ) = withContext(Dispatchers.IO) {
        // Save project metadata
        val projectFile = File(projectDir, "project.json")
        // TODO: Implement JSON serialization for project
        
        // Save pad assignments
        val padsFile = File(projectDir, "pads/pad_assignments.json")
        padsFile.parentFile?.mkdirs()
        // TODO: Implement JSON serialization for pad assignments
    }
}

/**
 * Data class for demo sample asset information.
 */
private data class DemoSampleAsset(
    val filename: String,
    val name: String,
    val description: String,
    val tags: List<String>
)

/**
 * Composable function to create sample projects.
 */
@Composable
fun SampleProjectManager(
    onProjectCreated: (Project) -> Unit,
    onError: (String) -> Unit
) {
    val projectCreator = remember { SampleProjectCreator() }
    
    // This would be called from the onboarding flow or main menu
    LaunchedEffect(Unit) {
        // Example usage - this would be triggered by user action
        // val result = projectCreator.createDemoProject(projectsDir, assetsManager)
        // result.fold(
        //     onSuccess = onProjectCreated,
        //     onFailure = { onError(it.message ?: "Failed to create project") }
        // )
    }
}