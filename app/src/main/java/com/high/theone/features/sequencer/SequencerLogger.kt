package com.high.theone.features.sequencer

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comprehensive logging system for sequencer debugging and monitoring.
 * Provides structured logging, performance metrics, and debug information
 * for troubleshooting sequencer issues.
 * 
 * Requirements: 8.7, 10.4
 */
@Singleton
class SequencerLogger @Inject constructor() {

    companion object {
        private const val TAG = "SequencerLogger"
        private const val MAX_LOG_ENTRIES = 1000
        private const val LOG_FLUSH_INTERVAL_MS = 5000L
        private const val MAX_LOG_FILE_SIZE = 10 * 1024 * 1024 // 10MB
    }

    // Log entry queue for async processing
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    
    // In-memory log storage for debugging
    private val memoryLogs = mutableListOf<LogEntry>()
    
    // Log configuration
    private val _logConfig = MutableStateFlow(LogConfig())
    val logConfig: StateFlow<LogConfig> = _logConfig.asStateFlow()
    
    // Performance metrics
    private val _performanceMetrics = MutableStateFlow(LoggingPerformanceMetrics())
    val performanceMetrics: StateFlow<LoggingPerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    // Logging coroutine
    private var loggingJob: Job? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Initialize the logging system
     * Requirements: 8.7
     */
    fun initialize(config: LogConfig = LogConfig()) {
        _logConfig.value = config
        startLogging()
        
        logInfo("SequencerLogger", "Logging system initialized", mapOf(
            "logLevel" to config.logLevel.name,
            "enableFileLogging" to config.enableFileLogging,
            "enablePerformanceLogging" to config.enablePerformanceLogging
        ))
    }

    /**
     * Log debug information
     * Requirements: 8.7
     */
    fun logDebug(
        component: String,
        message: String,
        context: Map<String, Any> = emptyMap()
    ) {
        if (_logConfig.value.logLevel.ordinal <= LogLevel.DEBUG.ordinal) {
            addLogEntry(LogLevel.DEBUG, component, message, context)
        }
    }

    /**
     * Log informational messages
     * Requirements: 8.7
     */
    fun logInfo(
        component: String,
        message: String,
        context: Map<String, Any> = emptyMap()
    ) {
        if (_logConfig.value.logLevel.ordinal <= LogLevel.INFO.ordinal) {
            addLogEntry(LogLevel.INFO, component, message, context)
        }
    }

    /**
     * Log warning messages
     * Requirements: 8.7
     */
    fun logWarning(
        component: String,
        message: String,
        context: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        if (_logConfig.value.logLevel.ordinal <= LogLevel.WARNING.ordinal) {
            addLogEntry(LogLevel.WARNING, component, message, context, exception)
        }
    }

    /**
     * Log error messages
     * Requirements: 8.7
     */
    fun logError(
        component: String,
        message: String,
        context: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        if (_logConfig.value.logLevel.ordinal <= LogLevel.ERROR.ordinal) {
            addLogEntry(LogLevel.ERROR, component, message, context, exception)
        }
    }

    /**
     * Log performance metrics
     * Requirements: 10.4
     */
    fun logPerformance(
        operation: String,
        durationMs: Long,
        context: Map<String, Any> = emptyMap()
    ) {
        if (_logConfig.value.enablePerformanceLogging) {
            val perfContext = context + mapOf(
                "operation" to operation,
                "durationMs" to durationMs,
                "type" to "performance"
            )
            
            addLogEntry(LogLevel.INFO, "Performance", "Operation completed", perfContext)
            
            // Update performance metrics
            updatePerformanceMetrics(operation, durationMs)
        }
    }

    /**
     * Log timing information for debugging
     * Requirements: 8.7, 10.4
     */
    fun logTiming(
        event: String,
        expectedTime: Long,
        actualTime: Long,
        context: Map<String, Any> = emptyMap()
    ) {
        val drift = actualTime - expectedTime
        val timingContext = context + mapOf(
            "event" to event,
            "expectedTime" to expectedTime,
            "actualTime" to actualTime,
            "drift" to drift,
            "type" to "timing"
        )
        
        val level = when {
            kotlin.math.abs(drift) > 50000L -> LogLevel.WARNING // 50ms
            kotlin.math.abs(drift) > 20000L -> LogLevel.INFO    // 20ms
            else -> LogLevel.DEBUG
        }
        
        addLogEntry(level, "Timing", "Timing event", timingContext)
    }

    /**
     * Log audio engine operations
     * Requirements: 8.7
     */
    fun logAudioEngine(
        operation: String,
        success: Boolean,
        context: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        val audioContext = context + mapOf(
            "operation" to operation,
            "success" to success,
            "type" to "audio_engine"
        )
        
        val level = if (success) LogLevel.DEBUG else LogLevel.ERROR
        val message = if (success) {
            "Audio engine operation successful: $operation"
        } else {
            "Audio engine operation failed: $operation"
        }
        
        addLogEntry(level, "AudioEngine", message, audioContext, exception)
    }

    /**
     * Log pattern operations
     * Requirements: 8.7
     */
    fun logPattern(
        operation: String,
        patternId: String,
        success: Boolean,
        context: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        val patternContext = context + mapOf(
            "operation" to operation,
            "patternId" to patternId,
            "success" to success,
            "type" to "pattern"
        )
        
        val level = if (success) LogLevel.DEBUG else LogLevel.WARNING
        val message = if (success) {
            "Pattern operation successful: $operation ($patternId)"
        } else {
            "Pattern operation failed: $operation ($patternId)"
        }
        
        addLogEntry(level, "Pattern", message, patternContext, exception)
    }

    /**
     * Log voice allocation operations
     * Requirements: 8.7
     */
    fun logVoiceAllocation(
        operation: String,
        voiceId: String?,
        padIndex: Int,
        success: Boolean,
        context: Map<String, Any> = emptyMap()
    ) {
        val voiceContext = context + mapOf(
            "operation" to operation,
            "voiceId" to (voiceId ?: "null"),
            "padIndex" to padIndex,
            "success" to success,
            "type" to "voice_allocation"
        )
        
        val level = if (success) LogLevel.DEBUG else LogLevel.INFO
        val message = if (success) {
            "Voice allocation successful: $operation (pad $padIndex)"
        } else {
            "Voice allocation failed: $operation (pad $padIndex)"
        }
        
        addLogEntry(level, "VoiceManager", message, voiceContext)
    }

    /**
     * Log sample cache operations
     * Requirements: 8.7
     */
    fun logSampleCache(
        operation: String,
        sampleId: String,
        success: Boolean,
        context: Map<String, Any> = emptyMap()
    ) {
        val cacheContext = context + mapOf(
            "operation" to operation,
            "sampleId" to sampleId,
            "success" to success,
            "type" to "sample_cache"
        )
        
        val level = if (success) LogLevel.DEBUG else LogLevel.WARNING
        val message = if (success) {
            "Sample cache operation successful: $operation ($sampleId)"
        } else {
            "Sample cache operation failed: $operation ($sampleId)"
        }
        
        addLogEntry(level, "SampleCache", message, cacheContext)
    }

    /**
     * Add log entry to queue
     * Requirements: 8.7
     */
    private fun addLogEntry(
        level: LogLevel,
        component: String,
        message: String,
        context: Map<String, Any> = emptyMap(),
        exception: Throwable? = null
    ) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            component = component,
            message = message,
            context = context,
            exception = exception,
            threadName = Thread.currentThread().name
        )
        
        // Add to queue for async processing
        logQueue.offer(entry)
        
        // Add to memory logs (with size limit)
        synchronized(memoryLogs) {
            memoryLogs.add(entry)
            if (memoryLogs.size > MAX_LOG_ENTRIES) {
                memoryLogs.removeAt(0)
            }
        }
        
        // Also log to Android Log
        logToAndroidLog(entry)
    }

    /**
     * Log to Android Log system
     * Requirements: 8.7
     */
    private fun logToAndroidLog(entry: LogEntry) {
        val logMessage = formatLogMessage(entry)
        
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(entry.component, logMessage, entry.exception)
            LogLevel.INFO -> Log.i(entry.component, logMessage, entry.exception)
            LogLevel.WARNING -> Log.w(entry.component, logMessage, entry.exception)
            LogLevel.ERROR -> Log.e(entry.component, logMessage, entry.exception)
        }
    }

    /**
     * Format log message for output
     * Requirements: 8.7
     */
    private fun formatLogMessage(entry: LogEntry): String {
        val contextStr = if (entry.context.isNotEmpty()) {
            " | Context: ${entry.context.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            ""
        }
        
        return "${entry.message}$contextStr"
    }

    /**
     * Start async logging processing
     * Requirements: 8.7
     */
    private fun startLogging() {
        loggingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    processLogQueue()
                    delay(LOG_FLUSH_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in logging processing", e)
                    delay(5000L) // Longer delay on error
                }
            }
        }
    }

    /**
     * Process queued log entries
     * Requirements: 8.7
     */
    private suspend fun processLogQueue() {
        val config = _logConfig.value
        
        if (config.enableFileLogging && config.logFilePath != null) {
            val entriesToProcess = mutableListOf<LogEntry>()
            
            // Drain the queue
            while (logQueue.isNotEmpty()) {
                logQueue.poll()?.let { entry ->
                    entriesToProcess.add(entry)
                }
            }
            
            if (entriesToProcess.isNotEmpty()) {
                writeToLogFile(entriesToProcess, config.logFilePath)
            }
        }
    }

    /**
     * Write log entries to file
     * Requirements: 8.7
     */
    private suspend fun writeToLogFile(entries: List<LogEntry>, filePath: String) {
        withContext(Dispatchers.IO) {
            try {
                val logFile = File(filePath)
                
                // Check file size and rotate if necessary
                if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                    rotateLogFile(logFile)
                }
                
                FileWriter(logFile, true).use { writer ->
                    entries.forEach { entry ->
                        val formattedEntry = formatLogEntryForFile(entry)
                        writer.appendLine(formattedEntry)
                    }
                    writer.flush()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file", e)
            }
        }
    }

    /**
     * Format log entry for file output
     * Requirements: 8.7
     */
    private fun formatLogEntryForFile(entry: LogEntry): String {
        val timestamp = dateFormatter.format(Date(entry.timestamp))
        val contextJson = if (entry.context.isNotEmpty()) {
            entry.context.entries.joinToString(", ", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        } else {
            "{}"
        }
        
        val exceptionStr = entry.exception?.let { 
            " | Exception: ${it.javaClass.simpleName}: ${it.message}"
        } ?: ""
        
        return "$timestamp | ${entry.level.name} | ${entry.component} | ${entry.threadName} | ${entry.message} | $contextJson$exceptionStr"
    }

    /**
     * Rotate log file when it gets too large
     * Requirements: 8.7
     */
    private fun rotateLogFile(logFile: File) {
        try {
            val backupFile = File("${logFile.absolutePath}.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating log file", e)
        }
    }

    /**
     * Update performance metrics
     * Requirements: 10.4
     */
    private fun updatePerformanceMetrics(operation: String, durationMs: Long) {
        val currentMetrics = _performanceMetrics.value
        val operationMetrics = currentMetrics.operationMetrics.toMutableMap()
        
        val existing = operationMetrics[operation] ?: OperationMetrics()
        operationMetrics[operation] = existing.copy(
            totalCalls = existing.totalCalls + 1,
            totalDurationMs = existing.totalDurationMs + durationMs,
            averageDurationMs = (existing.totalDurationMs + durationMs) / (existing.totalCalls + 1),
            maxDurationMs = maxOf(existing.maxDurationMs, durationMs),
            minDurationMs = if (existing.minDurationMs == 0L) durationMs else minOf(existing.minDurationMs, durationMs)
        )
        
        _performanceMetrics.value = currentMetrics.copy(
            operationMetrics = operationMetrics,
            lastUpdate = System.currentTimeMillis()
        )
    }

    /**
     * Get recent log entries for debugging
     * Requirements: 8.7
     */
    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        synchronized(memoryLogs) {
            return memoryLogs.takeLast(count)
        }
    }

    /**
     * Get logs filtered by component
     * Requirements: 8.7
     */
    fun getLogsByComponent(component: String, count: Int = 50): List<LogEntry> {
        synchronized(memoryLogs) {
            return memoryLogs.filter { it.component == component }.takeLast(count)
        }
    }

    /**
     * Get logs filtered by level
     * Requirements: 8.7
     */
    fun getLogsByLevel(level: LogLevel, count: Int = 50): List<LogEntry> {
        synchronized(memoryLogs) {
            return memoryLogs.filter { it.level == level }.takeLast(count)
        }
    }

    /**
     * Clear memory logs
     * Requirements: 8.7
     */
    fun clearLogs() {
        synchronized(memoryLogs) {
            memoryLogs.clear()
        }
        logQueue.clear()
        
        logInfo("SequencerLogger", "Log history cleared")
    }

    /**
     * Update log configuration
     * Requirements: 8.7
     */
    fun updateConfig(config: LogConfig) {
        _logConfig.value = config
        logInfo("SequencerLogger", "Log configuration updated", mapOf(
            "logLevel" to config.logLevel.name,
            "enableFileLogging" to config.enableFileLogging
        ))
    }

    /**
     * Shutdown the logging system
     */
    fun shutdown() {
        loggingJob?.cancel()
        
        // Process remaining logs
        runBlocking {
            processLogQueue()
        }
        
        logInfo("SequencerLogger", "Logging system shutdown")
    }
}

/**
 * Log entry data structure
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val component: String,
    val message: String,
    val context: Map<String, Any>,
    val exception: Throwable?,
    val threadName: String
)

/**
 * Log levels
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * Logging configuration
 */
data class LogConfig(
    val logLevel: LogLevel = LogLevel.INFO,
    val enableFileLogging: Boolean = false,
    val enablePerformanceLogging: Boolean = true,
    val logFilePath: String? = null
)

/**
 * Performance metrics for operations
 */
data class OperationMetrics(
    val totalCalls: Long = 0,
    val totalDurationMs: Long = 0,
    val averageDurationMs: Long = 0,
    val maxDurationMs: Long = 0,
    val minDurationMs: Long = 0
)

/**
 * Logging performance metrics
 */
data class LoggingPerformanceMetrics(
    val operationMetrics: Map<String, OperationMetrics> = emptyMap(),
    val lastUpdate: Long = 0L
)