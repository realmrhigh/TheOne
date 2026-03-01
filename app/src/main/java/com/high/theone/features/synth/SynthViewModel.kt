package com.high.theone.features.synth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.high.theone.audio.AudioEngineControl
import com.high.theone.model.SynthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SynthViewModel @Inject constructor(
    private val audioEngine: AudioEngineControl
) : ViewModel() {

    companion object {
        const val PLUGIN_ID = "sketchingsynth"
    }

    private val _state = MutableStateFlow(SynthState())
    val state: StateFlow<SynthState> = _state.asStateFlow()

    private val _pluginLoaded = MutableStateFlow(false)
    val pluginLoaded: StateFlow<Boolean> = _pluginLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = audioEngine.loadPlugin(PLUGIN_ID, "SketchingSynth")
            _pluginLoaded.value = loaded
            if (loaded) syncAllParamsToEngine()
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            audioEngine.noteOffToPlugin(PLUGIN_ID, 0, 0) // belt-and-suspenders
        }
    }

    // ── MIDI ─────────────────────────────────────────────────────────────────

    fun noteOn(midiNote: Int, velocity: Int = 100) {
        viewModelScope.launch {
            audioEngine.noteOnToPlugin(PLUGIN_ID, midiNote, velocity)
        }
    }

    fun noteOff(midiNote: Int) {
        viewModelScope.launch {
            audioEngine.noteOffToPlugin(PLUGIN_ID, midiNote, 0)
        }
    }

    fun allNotesOff() {
        viewModelScope.launch {
            // CC 123 = All Notes Off
            // We can't send raw MIDI CC here directly but we can release each note in range
            (0..127).forEach { note ->
                audioEngine.noteOffToPlugin(PLUGIN_ID, note, 0)
            }
        }
    }

    // ── Parameter setters ─────────────────────────────────────────────────────

    private fun setParam(paramId: String, value: Double) {
        viewModelScope.launch {
            audioEngine.setPluginParameter(PLUGIN_ID, paramId, value)
        }
    }

    // OSC 1
    fun setOsc1Wave(wave: Int) {
        _state.update { it.copy(osc1Wave = wave) }
        setParam("osc1_wave", wave.toDouble())
    }
    fun setOsc1Octave(octave: Int) {
        _state.update { it.copy(osc1Octave = octave) }
        setParam("osc1_octave", octave.toDouble())
    }
    fun setOsc1Semi(semi: Int) {
        _state.update { it.copy(osc1Semi = semi) }
        setParam("osc1_semi", semi.toDouble())
    }
    fun setOsc1Fine(fine: Float) {
        _state.update { it.copy(osc1Fine = fine) }
        setParam("osc1_fine", fine.toDouble())
    }
    fun setOsc1Level(level: Float) {
        _state.update { it.copy(osc1Level = level) }
        setParam("osc1_level", level.toDouble())
    }

    // OSC 2
    fun setOsc2Wave(wave: Int) {
        _state.update { it.copy(osc2Wave = wave) }
        setParam("osc2_wave", wave.toDouble())
    }
    fun setOsc2Octave(octave: Int) {
        _state.update { it.copy(osc2Octave = octave) }
        setParam("osc2_octave", octave.toDouble())
    }
    fun setOsc2Semi(semi: Int) {
        _state.update { it.copy(osc2Semi = semi) }
        setParam("osc2_semi", semi.toDouble())
    }
    fun setOsc2Fine(fine: Float) {
        _state.update { it.copy(osc2Fine = fine) }
        setParam("osc2_fine", fine.toDouble())
    }
    fun setOsc2Level(level: Float) {
        _state.update { it.copy(osc2Level = level) }
        setParam("osc2_level", level.toDouble())
    }

    // Sub / Noise
    fun setSubLevel(level: Float) {
        _state.update { it.copy(subLevel = level) }
        setParam("sub_level", level.toDouble())
    }
    fun setNoiseLevel(level: Float) {
        _state.update { it.copy(noiseLevel = level) }
        setParam("noise_level", level.toDouble())
    }

    // Amp Envelope
    fun setAmpAttack(ms: Float) {
        _state.update { it.copy(ampAttack = ms) }
        setParam("amp_attack", ms.toDouble())
    }
    fun setAmpDecay(ms: Float) {
        _state.update { it.copy(ampDecay = ms) }
        setParam("amp_decay", ms.toDouble())
    }
    fun setAmpSustain(level: Float) {
        _state.update { it.copy(ampSustain = level) }
        setParam("amp_sustain", level.toDouble())
    }
    fun setAmpRelease(ms: Float) {
        _state.update { it.copy(ampRelease = ms) }
        setParam("amp_release", ms.toDouble())
    }

    // Filter
    fun setFilterType(type: Int) {
        _state.update { it.copy(filterType = type) }
        setParam("filter_type", type.toDouble())
    }
    fun setFilterCutoff(hz: Float) {
        _state.update { it.copy(filterCutoff = hz) }
        setParam("filter_cutoff", hz.toDouble())
    }
    fun setFilterResonance(q: Float) {
        _state.update { it.copy(filterResonance = q) }
        setParam("filter_resonance", q.toDouble())
    }
    fun setFilterEnvAmt(amt: Float) {
        _state.update { it.copy(filterEnvAmt = amt) }
        setParam("filter_env_amt", amt.toDouble())
    }
    fun setFilterKeyTrack(kt: Float) {
        _state.update { it.copy(filterKeyTrack = kt) }
        setParam("filter_key_track", kt.toDouble())
    }
    fun setFilterVelSens(vs: Float) {
        _state.update { it.copy(filterVelSens = vs) }
        setParam("filter_vel_sens", vs.toDouble())
    }

    // Filter Envelope
    fun setFiltAttack(ms: Float) {
        _state.update { it.copy(filtAttack = ms) }
        setParam("filt_attack", ms.toDouble())
    }
    fun setFiltDecay(ms: Float) {
        _state.update { it.copy(filtDecay = ms) }
        setParam("filt_decay", ms.toDouble())
    }
    fun setFiltSustain(level: Float) {
        _state.update { it.copy(filtSustain = level) }
        setParam("filt_sustain", level.toDouble())
    }
    fun setFiltRelease(ms: Float) {
        _state.update { it.copy(filtRelease = ms) }
        setParam("filt_release", ms.toDouble())
    }

    // LFO 1
    fun setLfo1Rate(rate: Float) {
        _state.update { it.copy(lfo1Rate = rate) }
        setParam("lfo1_rate", rate.toDouble())
    }
    fun setLfo1Depth(depth: Float) {
        _state.update { it.copy(lfo1Depth = depth) }
        setParam("lfo1_depth", depth.toDouble())
    }
    fun setLfo1Shape(shape: Int) {
        _state.update { it.copy(lfo1Shape = shape) }
        setParam("lfo1_shape", shape.toDouble())
    }
    fun setLfo1Dest(dest: Int) {
        _state.update { it.copy(lfo1Dest = dest) }
        setParam("lfo1_dest", dest.toDouble())
    }

    // LFO 2
    fun setLfo2Rate(rate: Float) {
        _state.update { it.copy(lfo2Rate = rate) }
        setParam("lfo2_rate", rate.toDouble())
    }
    fun setLfo2Depth(depth: Float) {
        _state.update { it.copy(lfo2Depth = depth) }
        setParam("lfo2_depth", depth.toDouble())
    }
    fun setLfo2Shape(shape: Int) {
        _state.update { it.copy(lfo2Shape = shape) }
        setParam("lfo2_shape", shape.toDouble())
    }
    fun setLfo2Dest(dest: Int) {
        _state.update { it.copy(lfo2Dest = dest) }
        setParam("lfo2_dest", dest.toDouble())
    }

    // Master
    fun setMasterVolume(vol: Float) {
        _state.update { it.copy(masterVolume = vol) }
        setParam("master_volume", vol.toDouble())
    }
    fun setPan(pan: Float) {
        _state.update { it.copy(pan = pan) }
        setParam("pan", pan.toDouble())
    }
    fun setPortamento(ms: Float) {
        _state.update { it.copy(portamento = ms) }
        setParam("portamento", ms.toDouble())
    }
    fun setPitchBendRange(semitones: Float) {
        _state.update { it.copy(pitchBendRange = semitones) }
        setParam("pitch_bend_range", semitones.toDouble())
    }

    // Keyboard octave (UI-only, no engine param)
    fun setKeyboardOctave(oct: Int) {
        _state.update { it.copy(keyboardOctave = oct.coerceIn(1, 8)) }
    }

    // ── Sync all current UI state to engine (called after plugin load) ────────
    private suspend fun syncAllParamsToEngine() {
        val s = _state.value
        with(audioEngine) {
            setPluginParameter(PLUGIN_ID, "osc1_wave",   s.osc1Wave.toDouble())
            setPluginParameter(PLUGIN_ID, "osc1_octave", s.osc1Octave.toDouble())
            setPluginParameter(PLUGIN_ID, "osc1_semi",   s.osc1Semi.toDouble())
            setPluginParameter(PLUGIN_ID, "osc1_fine",   s.osc1Fine.toDouble())
            setPluginParameter(PLUGIN_ID, "osc1_level",  s.osc1Level.toDouble())
            setPluginParameter(PLUGIN_ID, "osc2_wave",   s.osc2Wave.toDouble())
            setPluginParameter(PLUGIN_ID, "osc2_octave", s.osc2Octave.toDouble())
            setPluginParameter(PLUGIN_ID, "osc2_semi",   s.osc2Semi.toDouble())
            setPluginParameter(PLUGIN_ID, "osc2_fine",   s.osc2Fine.toDouble())
            setPluginParameter(PLUGIN_ID, "osc2_level",  s.osc2Level.toDouble())
            setPluginParameter(PLUGIN_ID, "sub_level",   s.subLevel.toDouble())
            setPluginParameter(PLUGIN_ID, "noise_level", s.noiseLevel.toDouble())
            setPluginParameter(PLUGIN_ID, "amp_attack",  s.ampAttack.toDouble())
            setPluginParameter(PLUGIN_ID, "amp_decay",   s.ampDecay.toDouble())
            setPluginParameter(PLUGIN_ID, "amp_sustain", s.ampSustain.toDouble())
            setPluginParameter(PLUGIN_ID, "amp_release", s.ampRelease.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_type",      s.filterType.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_cutoff",    s.filterCutoff.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_resonance", s.filterResonance.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_env_amt",   s.filterEnvAmt.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_key_track", s.filterKeyTrack.toDouble())
            setPluginParameter(PLUGIN_ID, "filter_vel_sens",  s.filterVelSens.toDouble())
            setPluginParameter(PLUGIN_ID, "filt_attack",  s.filtAttack.toDouble())
            setPluginParameter(PLUGIN_ID, "filt_decay",   s.filtDecay.toDouble())
            setPluginParameter(PLUGIN_ID, "filt_sustain", s.filtSustain.toDouble())
            setPluginParameter(PLUGIN_ID, "filt_release", s.filtRelease.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo1_rate",  s.lfo1Rate.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo1_depth", s.lfo1Depth.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo1_shape", s.lfo1Shape.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo1_dest",  s.lfo1Dest.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo2_rate",  s.lfo2Rate.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo2_depth", s.lfo2Depth.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo2_shape", s.lfo2Shape.toDouble())
            setPluginParameter(PLUGIN_ID, "lfo2_dest",  s.lfo2Dest.toDouble())
            setPluginParameter(PLUGIN_ID, "master_volume",    s.masterVolume.toDouble())
            setPluginParameter(PLUGIN_ID, "pan",               s.pan.toDouble())
            setPluginParameter(PLUGIN_ID, "portamento",        s.portamento.toDouble())
            setPluginParameter(PLUGIN_ID, "pitch_bend_range",  s.pitchBendRange.toDouble())
        }
    }
}
