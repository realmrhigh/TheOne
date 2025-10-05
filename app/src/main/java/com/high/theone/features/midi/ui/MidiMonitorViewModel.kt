package com.high.theone.features.midi.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.midi.input.MidiInputProcessor
import com.high.theone.midi.output.MidiOutputGenerator
import com.high.theone.midi.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for MIDI Monitor screen.
 * Manages real-time MIDI message monitoring, statistics, and diagnostic tools.
 */
@HiltViewModel
class MidiMonitorViewModel @Inject constructor(
    private val inputProcessor: MidiInputProcessor,
    private val outputGenerator: MidiOutputGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(MidiMonitorUiState())
    val uiState: StateFlow<MidiMonitorUiState> = _uiState.asStateFlow()

    private val _midiMessages = MutableStateFlow<List<MidiMessageDisplay>>(emptyList())
    private val _settings = MutableStateFlow(MidiMonitorSettings())

    init {
        observeMidiMessages()
        observeStatistics()
        observeSettings()
    }

    private fun observeMidiMessages() {
        viewModelScope.launch {
            // Observe input messages
            launch {
                inputProcessor.processedMessages.collect { processedMessage ->
                    val displayMessage = convertToDisplayMessage(
                        processedMessage.originalMessage, 
                        MidiDirection.INPUT
                    )
                    addMessage(displayMessage)
                }
            }
            
            // Observe output messages
            launch {
                outputGenerator.outputMessages.collect { message ->
                    val displayMessage = convertToDisplayMessage(message, MidiDirection.OUTPUT)
                    addMessage(displayMessage)
                }
            }
        }
    }
    
    private fun addMessage(message: MidiMessageDisplay) {
        val currentMessages = _midiMessages.value.toMutableList()
        currentMessages.add(message)
        
        // Limit message count based on settings
        val maxMessages = _settings.value.maxMessages
        if (currentMessages.size > maxMessages) {
            currentMessages.removeRange(0, currentMessages.size - maxMessages)
        }
        
        _midiMessages.value = currentMessages
    }

    private fun observeStatistics() {
        viewModelScope.launch {
            // Observe output generator statistics
            outputGenerator.statistics.collect { outputStats ->
                // Combine with input processor statistics
                val inputStats = inputProcessor.getProcessingStatistics()
                
                val combinedStats = MidiStatistics(
                    inputMessageCount = inputStats.processedMessageCount,
                    outputMessageCount = outputStats.outputMessageCount,
                    averageInputLatency = inputStats.averageProcessingTimeMs,
                    droppedMessageCount = inputStats.droppedMessageCount + outputStats.droppedMessageCount,
                    lastErrorMessage = outputStats.lastErrorMessage
                )
                
                _uiState.value = _uiState.value.copy(statistics = combinedStats)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                _midiMessages,
                _settings
            ) { messages, settings ->
                val filteredMessages = if (settings.enabledMessageTypes.isEmpty()) {
                    messages
                } else {
                    messages.filter { it.type in settings.enabledMessageTypes }
                }
                
                _uiState.value = _uiState.value.copy(
                    midiMessages = messages,
                    filteredMessages = filteredMessages,
                    settings = settings
                )
            }.collect()
        }
    }

    private fun convertToDisplayMessage(
        message: MidiMessage,
        direction: MidiDirection
    ): MidiMessageDisplay {
        val description = when (message.type) {
            MidiMessageType.NOTE_ON -> {
                val note = getNoteNameFromNumber(message.data1)
                "Note On: $note (${message.data1}) Velocity: ${message.data2}"
            }
            MidiMessageType.NOTE_OFF -> {
                val note = getNoteNameFromNumber(message.data1)
                "Note Off: $note (${message.data1}) Velocity: ${message.data2}"
            }
            MidiMessageType.CONTROL_CHANGE -> {
                "CC ${message.data1}: ${message.data2}"
            }
            MidiMessageType.PITCH_BEND -> {
                val bendValue = (message.data2 shl 7) or message.data1
                "Pitch Bend: $bendValue"
            }
            MidiMessageType.PROGRAM_CHANGE -> {
                "Program Change: ${message.data1}"
            }
            MidiMessageType.AFTERTOUCH -> {
                "Aftertouch: ${message.data1}"
            }
            MidiMessageType.CLOCK -> "Clock Pulse"
            MidiMessageType.START -> "Transport Start"
            MidiMessageType.STOP -> "Transport Stop"
            MidiMessageType.CONTINUE -> "Transport Continue"
            else -> "Unknown Message"
        }

        val rawData = when (message.type) {
            MidiMessageType.CLOCK, MidiMessageType.START, MidiMessageType.STOP, MidiMessageType.CONTINUE -> {
                String.format("%02X", getStatusByte(message.type, message.channel))
            }
            else -> {
                String.format("%02X %02X %02X", 
                    getStatusByte(message.type, message.channel),
                    message.data1,
                    message.data2
                )
            }
        }

        return MidiMessageDisplay(
            id = UUID.randomUUID().toString(),
            type = message.type,
            channel = if (message.channel > 0) message.channel else null,
            description = description,
            rawData = rawData,
            timestamp = message.timestamp,
            direction = direction,
            deviceName = null // TODO: Get device name from message context
        )
    }

    private fun getNoteNameFromNumber(noteNumber: Int): String {
        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (noteNumber / 12) - 1
        val note = noteNames[noteNumber % 12]
        return "$note$octave"
    }

    private fun getStatusByte(type: MidiMessageType, channel: Int): Int {
        val statusNibble = when (type) {
            MidiMessageType.NOTE_OFF -> 0x80
            MidiMessageType.NOTE_ON -> 0x90
            MidiMessageType.AFTERTOUCH -> 0xA0
            MidiMessageType.CONTROL_CHANGE -> 0xB0
            MidiMessageType.PROGRAM_CHANGE -> 0xC0
            MidiMessageType.PITCH_BEND -> 0xE0
            MidiMessageType.CLOCK -> 0xF8
            MidiMessageType.START -> 0xFA
            MidiMessageType.CONTINUE -> 0xFB
            MidiMessageType.STOP -> 0xFC
            else -> 0xF0
        }
        
        return if (channel > 0) {
            statusNibble or (channel - 1)
        } else {
            statusNibble
        }
    }

    fun onToggleMonitoring() {
        _uiState.value = _uiState.value.copy(
            isMonitoring = !_uiState.value.isMonitoring
        )
    }

    fun onClearMessages() {
        _midiMessages.value = emptyList()
        _uiState.value = _uiState.value.copy(
            midiMessages = emptyList(),
            filteredMessages = emptyList()
        )
    }

    fun onToggleSettings() {
        _uiState.value = _uiState.value.copy(
            showSettings = !_uiState.value.showSettings
        )
    }

    fun onToggleAutoScroll() {
        _uiState.value = _uiState.value.copy(
            autoScroll = !_uiState.value.autoScroll
        )
    }

    fun onUpdateSettings(settings: MidiMonitorSettings) {
        _settings.value = settings
    }
}

/**
 * UI state for MIDI monitor screen
 */
data class MidiMonitorUiState(
    val midiMessages: List<MidiMessageDisplay> = emptyList(),
    val filteredMessages: List<MidiMessageDisplay> = emptyList(),
    val statistics: MidiStatistics = MidiStatistics(
        inputMessageCount = 0,
        outputMessageCount = 0,
        averageInputLatency = 0f,
        droppedMessageCount = 0,
        lastErrorMessage = null
    ),
    val settings: MidiMonitorSettings = MidiMonitorSettings(),
    val isMonitoring: Boolean = true,
    val showSettings: Boolean = false,
    val autoScroll: Boolean = true
)

/**
 * Display representation of a MIDI message
 */
data class MidiMessageDisplay(
    val id: String,
    val type: MidiMessageType,
    val channel: Int?,
    val description: String,
    val rawData: String,
    val timestamp: Long,
    val direction: MidiDirection,
    val deviceName: String?
)

/**
 * MIDI message direction
 */
enum class MidiDirection {
    INPUT, OUTPUT
}

/**
 * Settings for MIDI monitor
 */
data class MidiMonitorSettings(
    val enabledMessageTypes: Set<MidiMessageType> = MidiMessageType.values().toSet(),
    val showTimestamp: Boolean = true,
    val showRawData: Boolean = false,
    val maxMessages: Int = 1000
)