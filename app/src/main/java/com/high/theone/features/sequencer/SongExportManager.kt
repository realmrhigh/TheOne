package com.high.theone.features.sequencer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.high.theone.model.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages song export and sharing functionality
 */
@Singleton
class SongExportManager @Inject constructor(
    private val context: Context
) {
    
    private val _exportState = MutableStateFlow(SongExportState())
    val exportState: StateFlow<SongExportState> = _exportState.asStateFlow()
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Export song arrangement to JSON file
     */
    suspend fun exportSongArrangement(
        songMode: SongMode,
        patterns: List<Pattern>,
        projectName: String = "Untitled Song"
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            _exportState.update { it.copy(isExporting = true, progress = 0f) }
            
            // Create export data
            val exportData = SongExportData(
                projectName = projectName,
                songMode = songMode,
                patterns = patterns,
                exportedAt = System.currentTimeMillis(),
                exportVersion = EXPORT_VERSION
            )
            
            _exportState.update { it.copy(progress = 0.3f) }
            
            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "${projectName.replace(Regex("[^a-zA-Z0-9]"), "_")}_$timestamp.json"
            
            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            _exportState.update { it.copy(progress = 0.5f) }
            
            // Write JSON file
            val exportFile = File(exportDir, filename)
            val jsonString = json.encodeToString(exportData)
            
            FileWriter(exportFile).use { writer ->
                writer.write(jsonString)
            }
            
            _exportState.update { it.copy(progress = 0.8f) }
            
            // Create result
            val result = ExportResult(
                file = exportFile,
                filename = filename,
                size = exportFile.length(),
                format = ExportFormat.JSON,
                exportType = ExportType.SONG_ARRANGEMENT
            )
            
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 1f,
                    lastExport = result
                )
            }
            
            Result.success(result)
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 0f,
                    error = e.message
                )
            }
            Result.failure(e)
        }
    }
    
    /**
     * Export individual pattern to JSON
     */
    suspend fun exportPattern(
        pattern: Pattern,
        includeMetadata: Boolean = true
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            _exportState.update { it.copy(isExporting = true, progress = 0f) }
            
            val exportData = if (includeMetadata) {
                PatternExportData(
                    pattern = pattern,
                    exportedAt = System.currentTimeMillis(),
                    exportVersion = EXPORT_VERSION
                )
            } else {
                pattern
            }
            
            _exportState.update { it.copy(progress = 0.5f) }
            
            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "${pattern.name.replace(Regex("[^a-zA-Z0-9]"), "_")}_$timestamp.json"
            
            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "exports/patterns")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // Write JSON file
            val exportFile = File(exportDir, filename)
            val jsonString = json.encodeToString(exportData)
            
            FileWriter(exportFile).use { writer ->
                writer.write(jsonString)
            }
            
            val result = ExportResult(
                file = exportFile,
                filename = filename,
                size = exportFile.length(),
                format = ExportFormat.JSON,
                exportType = ExportType.PATTERN
            )
            
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 1f,
                    lastExport = result
                )
            }
            
            Result.success(result)
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 0f,
                    error = e.message
                )
            }
            Result.failure(e)
        }
    }
    
    /**
     * Export song timeline as text format
     */
    suspend fun exportSongTimeline(
        songMode: SongMode,
        patterns: List<Pattern>,
        includePatternDetails: Boolean = true
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            _exportState.update { it.copy(isExporting = true, progress = 0f) }
            
            val timeline = buildString {
                appendLine("Song Timeline Export")
                appendLine("===================")
                appendLine()
                appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("Total Patterns: ${songMode.sequence.size}")
                appendLine("Total Steps: ${songMode.getTotalSteps()}")
                appendLine("Loop Enabled: ${songMode.loopEnabled}")
                appendLine()
                
                songMode.sequence.forEachIndexed { index, songStep ->
                    val pattern = patterns.find { it.id == songStep.patternId }
                    
                    appendLine("${index + 1}. ${pattern?.name ?: "Unknown Pattern"}")
                    appendLine("   Pattern ID: ${songStep.patternId}")
                    appendLine("   Repeats: ${songStep.repeatCount}x")
                    
                    if (includePatternDetails && pattern != null) {
                        appendLine("   Length: ${pattern.length} steps")
                        appendLine("   Tempo: ${pattern.tempo} BPM")
                        appendLine("   Swing: ${(pattern.swing * 100).toInt()}%")
                        appendLine("   Active Steps: ${pattern.steps.values.flatten().size}")
                    }
                    appendLine()
                }
                
                if (includePatternDetails) {
                    appendLine("Pattern Details")
                    appendLine("===============")
                    appendLine()
                    
                    patterns.forEach { pattern ->
                        appendLine("Pattern: ${pattern.name}")
                        appendLine("ID: ${pattern.id}")
                        appendLine("Length: ${pattern.length} steps")
                        appendLine("Tempo: ${pattern.tempo} BPM")
                        appendLine("Swing: ${(pattern.swing * 100).toInt()}%")
                        appendLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(pattern.createdAt))}")
                        
                        pattern.steps.forEach { (padIndex, steps) ->
                            if (steps.isNotEmpty()) {
                                appendLine("  Pad $padIndex: ${steps.size} steps")
                                steps.forEach { step ->
                                    appendLine("    Step ${step.position}: velocity ${step.velocity}")
                                }
                            }
                        }
                        appendLine()
                    }
                }
            }
            
            _exportState.update { it.copy(progress = 0.7f) }
            
            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "song_timeline_$timestamp.txt"
            
            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            // Write text file
            val exportFile = File(exportDir, filename)
            FileWriter(exportFile).use { writer ->
                writer.write(timeline)
            }
            
            val result = ExportResult(
                file = exportFile,
                filename = filename,
                size = exportFile.length(),
                format = ExportFormat.TEXT,
                exportType = ExportType.TIMELINE
            )
            
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 1f,
                    lastExport = result
                )
            }
            
            Result.success(result)
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(
                    isExporting = false,
                    progress = 0f,
                    error = e.message
                )
            }
            Result.failure(e)
        }
    }
    
    /**
     * Share exported file
     */
    fun shareExportedFile(exportResult: ExportResult): Intent? {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportResult.file
            )
            
            Intent(Intent.ACTION_SEND).apply {
                type = when (exportResult.format) {
                    ExportFormat.JSON -> "application/json"
                    ExportFormat.TEXT -> "text/plain"
                    ExportFormat.MIDI -> "audio/midi"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "TheOne ${exportResult.exportType.displayName}")
                putExtra(Intent.EXTRA_TEXT, "Exported from TheOne app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(error = "Failed to share file: ${e.message}")
            }
            null
        }
    }
    
    /**
     * Import song arrangement from JSON file
     */
    suspend fun importSongArrangement(uri: Uri): Result<SongImportResult> = withContext(Dispatchers.IO) {
        try {
            _exportState.update { it.copy(isImporting = true, progress = 0f) }
            
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))
            
            _exportState.update { it.copy(progress = 0.3f) }
            
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            
            _exportState.update { it.copy(progress = 0.6f) }
            
            val exportData = json.decodeFromString<SongExportData>(jsonString)
            
            _exportState.update { it.copy(progress = 0.9f) }
            
            val result = SongImportResult(
                songMode = exportData.songMode,
                patterns = exportData.patterns,
                projectName = exportData.projectName,
                originalExportTime = exportData.exportedAt
            )
            
            _exportState.update { 
                it.copy(
                    isImporting = false,
                    progress = 1f,
                    lastImport = result
                )
            }
            
            Result.success(result)
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(
                    isImporting = false,
                    progress = 0f,
                    error = "Import failed: ${e.message}"
                )
            }
            Result.failure(e)
        }
    }
    
    /**
     * Get list of exported files
     */
    fun getExportedFiles(): List<ExportedFileInfo> {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) return emptyList()
        
        return exportDir.listFiles()?.mapNotNull { file ->
            if (file.isFile) {
                ExportedFileInfo(
                    file = file,
                    name = file.name,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    format = when (file.extension.lowercase()) {
                        "json" -> ExportFormat.JSON
                        "txt" -> ExportFormat.TEXT
                        "mid", "midi" -> ExportFormat.MIDI
                        else -> ExportFormat.JSON
                    }
                )
            } else null
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }
    
    /**
     * Delete exported file
     */
    fun deleteExportedFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            _exportState.update { 
                it.copy(error = "Failed to delete file: ${e.message}")
            }
            false
        }
    }
    
    /**
     * Clear export state
     */
    fun clearState() {
        _exportState.update { 
            it.copy(
                error = null,
                progress = 0f,
                isExporting = false,
                isImporting = false
            )
        }
    }
    
    companion object {
        private const val EXPORT_VERSION = "1.0"
    }
}

/**
 * Export state for UI feedback
 */
data class SongExportState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val lastExport: ExportResult? = null,
    val lastImport: SongImportResult? = null
)

/**
 * Result of export operation
 */
data class ExportResult(
    val file: File,
    val filename: String,
    val size: Long,
    val format: ExportFormat,
    val exportType: ExportType
)

/**
 * Result of import operation
 */
data class SongImportResult(
    val songMode: SongMode,
    val patterns: List<Pattern>,
    val projectName: String,
    val originalExportTime: Long
)

/**
 * Information about exported file
 */
data class ExportedFileInfo(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val format: ExportFormat
)

/**
 * Export formats
 */
enum class ExportFormat(val displayName: String, val extension: String) {
    JSON("JSON", "json"),
    TEXT("Text", "txt"),
    MIDI("MIDI", "mid")
}

/**
 * Export types
 */
enum class ExportType(val displayName: String) {
    SONG_ARRANGEMENT("Song Arrangement"),
    PATTERN("Pattern"),
    TIMELINE("Timeline")
}

/**
 * Data structure for song export
 */
@Serializable
data class SongExportData(
    val projectName: String,
    val songMode: SongMode,
    val patterns: List<Pattern>,
    val exportedAt: Long,
    val exportVersion: String
)

/**
 * Data structure for pattern export
 */
@Serializable
data class PatternExportData(
    val pattern: Pattern,
    val exportedAt: Long,
    val exportVersion: String
)