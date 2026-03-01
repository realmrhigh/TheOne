package com.high.theone.features.mpcui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.features.compactui.CompactMainViewModel
import com.high.theone.features.drumtrack.DrumTrackViewModel
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.features.midi.ui.MidiSettingsViewModel
import com.high.theone.features.sampling.SamplingViewModel
import com.high.theone.features.sequencer.SimpleSequencerViewModel
import com.high.theone.features.sequencer.TrackMuteSoloState
import com.high.theone.features.sequencer.TransportControlAction
import com.high.theone.model.DrumPadState
import com.high.theone.model.FilterMode
import com.high.theone.model.FilterSettings
import com.high.theone.model.Pattern
import com.high.theone.model.PlaybackMode
import com.high.theone.model.Step
import com.high.theone.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

// ─── Pad border color palette (MPC ONE+ rainbow layout) ─────────────────────
private val PAD_COLORS = listOf(
    MpcPadPink, MpcPadMagenta, MpcPadPurple, MpcPadGreen,
    MpcPadOrange, MpcPadYellow, MpcPadTeal, MpcPadBlue,
    MpcPadLightPink, MpcPadLavender, MpcPadSky, MpcPadLime,
    MpcPadRed, MpcPadAmber, MpcPadGold, MpcPadMint
)

private val DISPLAY_ORDER = listOf(
    12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3
)

// ═════════════════════════════════════════════════════════════════════════════
//  Root entry — all ViewModels, state, popup routing
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MPCMainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // ── ViewModels ──────────────────────────────────────────────────────────
    val viewModel: CompactMainViewModel     = hiltViewModel()
    val drumViewModel: DrumTrackViewModel   = hiltViewModel()
    val seqViewModel: SimpleSequencerViewModel = hiltViewModel()
    val samplingVM: SamplingViewModel       = hiltViewModel()
    val midiVM: MidiSettingsViewModel       = hiltViewModel()

    // ── State ───────────────────────────────────────────────────────────────
    val transportState  by viewModel.transportState.collectAsState()
    val drumPadState    by viewModel.drumPadState.collectAsState()
    val sequencerState  by seqViewModel.sequencerState.collectAsState()
    val patterns        by seqViewModel.patterns.collectAsState()
    val muteSoloState   by seqViewModel.muteSoloState.collectAsState()
    val canUndo         by seqViewModel.canUndo.collectAsState()
    val canRedo         by seqViewModel.canRedo.collectAsState()
    val padSettingsMap  by drumViewModel.padSettingsMap.collectAsState()
    val samplingUi      by samplingVM.uiState.collectAsState()
    val midiUi          by midiVM.uiState.collectAsState()
    val midiDevices     by midiVM.connectedDevices.collectAsState()
    val midiAvailable   by midiVM.availableDevices.collectAsState()

    // ── Sync ────────────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            drumViewModel.padSettingsMap, drumViewModel.activePadId,
            drumViewModel.isPlaying, drumViewModel.isRecording
        ) { pads, active, playing, recording ->
            DrumPadState(padSettings = pads, isPlaying = playing, isRecording = recording, activePadId = active)
        }.collect { viewModel.updateDrumPadState(it) }
    }
    LaunchedEffect(Unit) { seqViewModel.sequencerState.collect { viewModel.updateSequencerState(it) } }

    // ── Local UI state ──────────────────────────────────────────────────────
    var selectedPadId    by remember { mutableStateOf<String?>(null) }
    var currentBank      by remember { mutableIntStateOf(0) }
    var shiftActive      by remember { mutableStateOf(false) }
    var tapTempoTimes    by remember { mutableStateOf(listOf<Long>()) }

    // Pop-up visibility flags
    var showBrowseDialog  by remember { mutableStateOf(false) }
    var showMixerSheet    by remember { mutableStateOf(false) }
    var showSamplingSheet by remember { mutableStateOf(false) }
    var showMidiSheet     by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPatternMgmt   by remember { mutableStateOf(false) }
    var showTCDialog      by remember { mutableStateOf(false) }

    // Performance mode flags
    var fullLevelMode     by remember { mutableStateOf(false) }
    var sixteenLevelMode  by remember { mutableStateOf(false) }
    var noteRepeatActive  by remember { mutableStateOf(false) }

    val currentPattern = patterns.find { it.id == sequencerState.currentPattern } ?: patterns.firstOrNull()
    val activePadId = drumPadState.activePadId
    val padNames: Map<Int, String> = remember(padSettingsMap) {
        padSettingsMap.mapKeys { it.key.toIntOrNull() ?: -1 }.filterKeys { it >= 0 }.mapValues { it.value.name }
    }

    // ── Note repeat coroutine ───────────────────────────────────────────────
    var noteRepeatPad by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(noteRepeatActive, noteRepeatPad) {
        if (noteRepeatActive && noteRepeatPad != null) {
            val intervalMs = (60_000L / transportState.bpm / 4).toLong().coerceAtLeast(50)
            while (noteRepeatActive && noteRepeatPad != null) {
                drumViewModel.onPadTriggered(noteRepeatPad.toString(), 1.0f)
                delay(intervalMs)
            }
        }
    }

    fun handleTapTempo() {
        val now = System.currentTimeMillis()
        val taps = (tapTempoTimes + now).takeLast(4)
        tapTempoTimes = taps
        if (taps.size >= 2) {
            val bpm = (60_000.0 / taps.zipWithNext { a, b -> b - a }.average()).toInt().coerceIn(60, 200)
            viewModel.onBpmChange(bpm)
            seqViewModel.handleTransportAction(TransportControlAction.SetTempo(bpm.toFloat()))
        }
    }

    // Velocity function for pad taps
    fun padVelocity(padIndex: Int): Float {
        return when {
            fullLevelMode -> 1.0f
            sixteenLevelMode -> ((padIndex % 16) + 1) / 16f
            else -> 1.0f
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Layout
    // ═════════════════════════════════════════════════════════════════════════
    Column(
        modifier = modifier.fillMaxSize().background(MpcBodyDark).statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ① Header
        MpcHeaderBar(
            currentStep = sequencerState.currentStep, patternLength = currentPattern?.length ?: 16,
            bpm = transportState.bpm, isPlaying = transportState.isPlaying, isRecording = transportState.isRecording,
            onSettings = { showSettingsSheet = true }, onBrowse = { showBrowseDialog = true },
            onPatterns = { showPatternMgmt = true }
        )

        // ② Display
        MpcDisplayScreen(
            pattern = currentPattern, currentStep = sequencerState.currentStep,
            isPlaying = transportState.isPlaying, isRecording = transportState.isRecording,
            bpm = transportState.bpm, padNames = padNames,
            modifier = Modifier.fillMaxWidth().weight(0.28f)
        )

        // ③ Navigation row
        MpcNavigationRow(
            shiftActive = shiftActive,
            onMain    = { navController.navigate("project_settings") },
            onBrowse  = { showBrowseDialog = true },
            onMix     = { showMixerSheet = true },
            onMute    = { activePadId?.toIntOrNull()?.let { seqViewModel.togglePadMute(it) } },
            onNextSeq = { navController.navigate("step_sequencer_screen") }
        )

        // ④ Pads + right panel
        Row(Modifier.fillMaxWidth().weight(0.40f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MpcLeftButtons(
                shiftActive = shiftActive,
                fullLevelMode = fullLevelMode,
                sixteenLevelMode = sixteenLevelMode,
                noteRepeatActive = noteRepeatActive,
                onShift  = { shiftActive = !shiftActive },
                onErase  = {
                    currentPattern?.let { p ->
                        sequencerState.selectedPads.forEach { padId ->
                            (0 until p.length).forEach { step ->
                                if (p.steps[padId]?.any { it.position == step } == true) seqViewModel.toggleStep(padId, step)
                            }
                        }
                    }
                },
                onCopy        = { currentPattern?.id?.let { seqViewModel.duplicatePattern(it) } },
                onDelete      = { currentPattern?.id?.let { seqViewModel.deletePattern(it) } },
                onFullLevel   = { fullLevelMode = !fullLevelMode; sixteenLevelMode = false },
                onSixteenLvl  = { sixteenLevelMode = !sixteenLevelMode; fullLevelMode = false },
                onNoteRepeat  = { noteRepeatActive = !noteRepeatActive; if (!noteRepeatActive) noteRepeatPad = null },
                modifier = Modifier.width(36.dp).fillMaxHeight()
            )

            MpcDrumPadGrid(
                padSettingsMap = padSettingsMap, activePadId = activePadId,
                muteSoloState = muteSoloState, currentBank = currentBank,
                onPadTap = { idx, _ ->
                    val vel = padVelocity(idx)
                    drumViewModel.onPadTriggered(idx.toString(), vel)
                    if (noteRepeatActive) noteRepeatPad = idx
                },
                onPadLongPress = { idx -> selectedPadId = idx.toString() },
                modifier = Modifier.weight(1f)
            )

            MpcRightPanel(
                bpm = transportState.bpm, currentBank = currentBank, activePadId = activePadId,
                padSettingsMap = padSettingsMap,
                onBpmDelta = { d ->
                    val b = (transportState.bpm + d).coerceIn(60, 200)
                    viewModel.onBpmChange(b)
                    seqViewModel.handleTransportAction(TransportControlAction.SetTempo(b.toFloat()))
                },
                onBankSelect = { currentBank = it },
                onPadVolume = { id, v -> padSettingsMap[id]?.let { drumViewModel.updatePadSettings(it.copy(volume = v)) } },
                onPadPan = { id, p -> padSettingsMap[id]?.let { drumViewModel.updatePadSettings(it.copy(pan = p)) } },
                modifier = Modifier.width(72.dp).fillMaxHeight()
            )
        }

        // ⑤ Function row
        MpcFunctionRow(
            shiftActive = shiftActive,
            onStepSeq    = { navController.navigate("step_sequencer_screen") },
            onTC         = { showTCDialog = true },
            onSampler    = { if (shiftActive) showSamplingSheet = true else navController.navigate("drum_pad_screen") },
            onSampleEdit = { selectedPadId = activePadId ?: "0" },
            onProgramEdit = {
                if (shiftActive) showMidiSheet = true
                else navController.navigate("midi_mapping")
            }
        )

        // ⑤b Utility row
        MpcUtilityRow(
            canUndo = canUndo, canRedo = canRedo, shiftActive = shiftActive,
            onUndo = { seqViewModel.undo() }, onRedo = { seqViewModel.redo() },
            onTapTempo = { handleTapTempo() }, onShift = { shiftActive = !shiftActive },
            onMenu = { showSettingsSheet = true },
            onPlus = { seqViewModel.handleTransportAction(TransportControlAction.SetPatternLength(((currentPattern?.length ?: 16) + 8).coerceAtMost(32))) },
            onMinus = { seqViewModel.handleTransportAction(TransportControlAction.SetPatternLength(((currentPattern?.length ?: 16) - 8).coerceAtLeast(8))) }
        )

        // ⑥ Transport row
        MpcTransportRow(
            isPlaying = transportState.isPlaying, isRecording = transportState.isRecording,
            onRec = { viewModel.onRecord(); seqViewModel.handleTransportAction(TransportControlAction.ToggleRecord) },
            onOverDub = { seqViewModel.handleTransportAction(TransportControlAction.ToggleRecord) },
            onStop = { viewModel.onStop(); seqViewModel.handleTransportAction(TransportControlAction.Stop) },
            onPlay = {
                if (transportState.isPlaying) { viewModel.onPlayPause(); seqViewModel.handleTransportAction(TransportControlAction.Pause) }
                else { viewModel.onPlayPause(); seqViewModel.handleTransportAction(TransportControlAction.Play) }
            },
            onPlayStart = {
                viewModel.onStop(); seqViewModel.handleTransportAction(TransportControlAction.Stop)
                viewModel.onPlayPause(); seqViewModel.handleTransportAction(TransportControlAction.Play)
            }
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  POP-UP SHEETS & DIALOGS
    // ═════════════════════════════════════════════════════════════════════════

    // ── Pad Config (long-press) ─────────────────────────────────────────────
    selectedPadId?.let { pid -> padSettingsMap[pid]?.let { ps ->
        MpcPadConfigSheet(padSettings = ps,
            onSave = { drumViewModel.updatePadSettings(it); selectedPadId = null },
            onDismiss = { selectedPadId = null })
    }}

    // ── Mixer Sheet ─────────────────────────────────────────────────────────
    if (showMixerSheet) {
        MpcMixerSheet(
            padSettingsMap = padSettingsMap, drumViewModel = drumViewModel,
            onDismiss = { showMixerSheet = false }
        )
    }

    // ── Sampling / Recording Sheet ──────────────────────────────────────────
    if (showSamplingSheet) {
        MpcSamplingSheet(
            samplingVM = samplingVM, samplingUi = samplingUi,
            onDismiss = { showSamplingSheet = false }
        )
    }

    // ── MIDI Sheet ──────────────────────────────────────────────────────────
    if (showMidiSheet) {
        MpcMidiSheet(
            midiVM = midiVM, midiUi = midiUi,
            connectedDevices = midiDevices, availableDevices = midiAvailable,
            onOpenFull = { showMidiSheet = false; navController.navigate("midi_settings") },
            onDismiss = { showMidiSheet = false }
        )
    }

    // ── Settings Sheet ──────────────────────────────────────────────────────
    if (showSettingsSheet) {
        MpcSettingsSheet(
            navController = navController,
            onDismiss = { showSettingsSheet = false }
        )
    }

    // ── Pattern Management Sheet ────────────────────────────────────────────
    if (showPatternMgmt) {
        MpcPatternSheet(
            patterns = patterns, currentPatternId = sequencerState.currentPattern,
            seqViewModel = seqViewModel,
            onDismiss = { showPatternMgmt = false }
        )
    }

    // ── TC (Timing Correct / Quantize) Dialog ───────────────────────────────
    if (showTCDialog) {
        MpcTCDialog(
            currentSwing = currentPattern?.swing ?: 0f,
            seqViewModel = seqViewModel,
            onDismiss = { showTCDialog = false }
        )
    }

    // ── Browse Dialog ───────────────────────────────────────────────────────
    if (showBrowseDialog) {
        MpcBrowseDialog(
            navController = navController, samplingUi = samplingUi,
            onDismiss = { showBrowseDialog = false }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  HEADER BAR
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun MpcHeaderBar(
    currentStep: Int, patternLength: Int, bpm: Int,
    isPlaying: Boolean, isRecording: Boolean,
    onSettings: () -> Unit, onBrowse: () -> Unit, onPatterns: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bar = (currentStep / 4) + 1; val beat = (currentStep % 4) + 1
    Row(
        modifier.fillMaxWidth().background(MpcBodyMid, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SEQ 1", color = MpcTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp)); Text("|", color = MpcButtonMid, fontSize = 10.sp); Spacer(Modifier.width(6.dp))
            Text("$patternLength stp", color = MpcTextAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosDigit(bar.toString()); Text(":", color = MpcTextSecondary, fontSize = 12.sp)
            PosDigit(beat.toString()); Text(":", color = MpcTextSecondary, fontSize = 12.sp); PosDigit("0")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isRecording) Box(Modifier.size(8.dp).background(MpcRedBright, CircleShape))
            if (isPlaying) Icon(Icons.Default.PlayArrow, null, tint = MpcDisplayGreen, modifier = Modifier.size(14.dp))
            Text("$bpm", color = MpcDisplayCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HdrIcon(Icons.Default.ViewList, "Patterns") { onPatterns() }
            HdrIcon(Icons.Default.FolderOpen, "Browse") { onBrowse() }
            HdrIcon(Icons.Default.Settings, "Settings") { onSettings() }
        }
    }
}

@Composable private fun PosDigit(v: String) { Text(v.padStart(2, ' '), color = MpcTextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.background(MpcScreenBg, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) }
@Composable private fun HdrIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) { Box(Modifier.size(24.dp).background(MpcBodyLight, RoundedCornerShape(3.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, tint = MpcTextSecondary, modifier = Modifier.size(14.dp)) } }

// ═════════════════════════════════════════════════════════════════════════════
//  DISPLAY — real-data piano roll
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun MpcDisplayScreen(
    pattern: Pattern?, currentStep: Int, isPlaying: Boolean, isRecording: Boolean,
    bpm: Int, padNames: Map<Int, String>, modifier: Modifier = Modifier
) {
    val len = pattern?.length ?: 16; val steps = pattern?.steps ?: emptyMap()
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MpcScreenBg).border(1.dp, MpcButtonMid, RoundedCornerShape(6.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawPianoRoll(steps, len, currentStep, isPlaying) }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 2.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            (0 until 8).forEach { i ->
                Text(padNames[i]?.take(4) ?: "P${i + 1}", color = PAD_COLORS.getOrElse(i) { MpcTextSecondary }.copy(alpha = 0.7f), fontSize = 6.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(MpcScreenBg.copy(alpha = 0.8f), RoundedCornerShape(1.dp)).padding(horizontal = 2.dp))
            }
        }
        if (isRecording) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).background(MpcRedBright.copy(alpha = 0.8f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("● REC", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(MpcBodyMid.copy(alpha = 0.85f), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("$bpm BPM", color = MpcDisplayCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPianoRoll(steps: Map<Int, List<Step>>, patternLength: Int, currentStep: Int, isPlaying: Boolean) {
    val w = size.width; val h = size.height; val tracks = 8; val left = 28f
    val gw = w - left; val sw = gw / patternLength; val rh = (h * 0.82f) / tracks; val vh = h * 0.15f; val vy = h * 0.82f
    for (s in 0..patternLength) { val x = left + s * sw; val beat = s % 4 == 0; drawLine(if (beat) Color(0xFF3A3A4A) else Color(0xFF252535), Offset(x, 0f), Offset(x, vy), if (beat) 1.2f else 0.5f) }
    for (t in 0..tracks) drawLine(Color(0xFF22223A), Offset(left, t * rh), Offset(w, t * rh), 0.5f)
    drawLine(Color(0xFF3A3A4A), Offset(left, vy), Offset(w, vy), 1f)
    val tc = listOf(MpcDisplayYellow, MpcDisplayOrange, MpcDisplayBlue, MpcDisplayGreen, MpcPadPink, MpcPadTeal, MpcPadLavender, MpcPadAmber)
    steps.forEach { (pad, list) -> val row = pad.coerceIn(0, tracks - 1); val c = tc[row % tc.size]; list.forEach { step -> if (step.isActive && step.position < patternLength) { val x = left + step.position * sw; val y = row * rh; drawRect(c, Offset(x + 1f, y + 1f), Size(sw - 2f, rh - 2f)); val vf = step.velocity / 127f; drawRect(MpcDisplayYellow.copy(alpha = 0.9f), Offset(x + 1f, vy + vh * (1f - vf)), Size(sw - 2f, vh * vf)) } } }
    if (steps.isEmpty()) { for (m in 0 until (patternLength / 4)) drawCircle(Color(0xFF3A3A5A), 2f, Offset(left + m * 4 * sw + sw * 0.5f, h * 0.4f)) }
    if (isPlaying && currentStep < patternLength) { val px = left + currentStep * sw + sw / 2f; drawLine(MpcDisplayCyan, Offset(px, 0f), Offset(px, vy + vh), 2.5f); val tri = Path().apply { moveTo(px - 4f, 0f); lineTo(px + 4f, 0f); lineTo(px, 6f); close() }; drawPath(tri, MpcDisplayCyan) }
}

// ═════════════════════════════════════════════════════════════════════════════
//  NAVIGATION ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcNavigationRow(shiftActive: Boolean, onMain: () -> Unit, onBrowse: () -> Unit, onMix: () -> Unit, onMute: () -> Unit, onNextSeq: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        NavBtn("MAIN","GRID", shiftActive, onMain, Modifier.weight(1f)); NavBtn("BROWSE","SAVE", shiftActive, onBrowse, Modifier.weight(1f))
        NavBtn("TRACK MIX","PAD MIX", shiftActive, onMix, Modifier.weight(1f)); NavBtn("TRACK MUTE","PAD MUTE", shiftActive, onMute, Modifier.weight(1f))
        NavBtn("NEXT SEQ","XYFX", shiftActive, onNextSeq, Modifier.weight(1f))
    }
}
@Composable private fun NavBtn(primary: String, secondary: String, shift: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.background(if (shift) MpcButtonActive else MpcButtonDark, RoundedCornerShape(3.dp)).border(0.5.dp, if (shift) MpcDisplayCyan.copy(alpha = 0.5f) else MpcButtonMid, RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(primary, color = if (shift) MpcTextSecondary else MpcButtonText, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(secondary, color = if (shift) MpcDisplayCyan else MpcDisplayCyan.copy(alpha = 0.6f), fontSize = 5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  LEFT BUTTONS
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcLeftButtons(
    shiftActive: Boolean, fullLevelMode: Boolean, sixteenLevelMode: Boolean, noteRepeatActive: Boolean,
    onShift: () -> Unit, onErase: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit,
    onFullLevel: () -> Unit, onSixteenLvl: () -> Unit, onNoteRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SmBtn("FULL\nLVL", fullLevelMode, activeColor = MpcDisplayYellow, onClick = onFullLevel)
        SmBtn("16\nLVLS", sixteenLevelMode, activeColor = MpcDisplayOrange, onClick = onSixteenLvl)
        SmBtn("NOTE", false) {}
        SmBtn("COPY", false, onClick = onCopy)
        SmBtn("DEL", false, onClick = onDelete)
        Spacer(Modifier.weight(1f))
        SmBtn("ERASE", false, onClick = onErase)
        SmBtn("NOTE\nRPT", noteRepeatActive, activeColor = MpcPadPink, onClick = onNoteRepeat)
        SmBtn("SHIFT", shiftActive, activeColor = MpcDisplayCyan, onClick = onShift)
    }
}
@Composable private fun SmBtn(label: String, active: Boolean, activeColor: Color = MpcRedBright, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(if (active) activeColor.copy(alpha = 0.2f) else MpcButtonDark, RoundedCornerShape(3.dp)).border(0.5.dp, if (active) activeColor else MpcButtonMid, RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) activeColor else MpcButtonText, fontSize = 5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 7.sp)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  DRUM PAD GRID
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcDrumPadGrid(padSettingsMap: Map<String, PadSettings>, activePadId: String?, muteSoloState: TrackMuteSoloState, currentBank: Int, onPadTap: (Int, Float) -> Unit, onPadLongPress: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until 4) { Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) { for (col in 0 until 4) {
            val di = row * 4 + col; val padNum = DISPLAY_ORDER[di] + currentBank * 16; val ps = padSettingsMap[padNum.toString()]; val color = PAD_COLORS[di]; val isActive = activePadId == padNum.toString()
            MpcPad(label = "PAD ${padNum + 1}", sampleName = ps?.name ?: "PAD ${padNum + 1}", borderColor = color, isActive = isActive, isMuted = muteSoloState.mutedTracks.contains(padNum), isSoloed = muteSoloState.soloedTracks.contains(padNum), hasSample = ps?.sampleId != null || (ps?.layers?.isNotEmpty() == true), onTap = { onPadTap(padNum, 1.0f) }, onLongPress = { onPadLongPress(padNum) }, modifier = Modifier.weight(1f))
        } } }
    }
}
@Composable private fun MpcPad(label: String, sampleName: String, borderColor: Color, isActive: Boolean, isMuted: Boolean, isSoloed: Boolean, hasSample: Boolean, onTap: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current; var pressed by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (isActive || pressed) 0.55f else 0f, tween(80), label = "g"); val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "s")
    val bdr = when { isMuted -> borderColor.copy(alpha = 0.25f); isSoloed -> Color.White; else -> borderColor }
    Box(modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(6.dp)).background(if (isActive || pressed) borderColor.copy(alpha = 0.22f) else if (isMuted) MpcBodyDark else MpcBodyLight).border(if (isActive) 2.5.dp else 1.8.dp, if (isActive) bdr else bdr.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).drawBehind { if (glow > 0f) drawRect(Brush.radialGradient(listOf(borderColor.copy(alpha = glow * 0.6f), Color.Transparent), Offset(size.width / 2, size.height / 2), size.minDimension * 0.75f)) }.pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); tryAwaitRelease(); pressed = false }, onTap = { onTap() }, onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() }) }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(2.dp)) {
            Text(sampleName.take(8), color = if (hasSample) borderColor.copy(alpha = 0.9f) else MpcTextSecondary.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = borderColor.copy(alpha = 0.5f), fontSize = 5.sp, textAlign = TextAlign.Center)
            if (isMuted) Text("M", color = MpcRedBright, fontSize = 6.sp, fontWeight = FontWeight.Bold)
            if (isSoloed) Text("S", color = MpcDisplayYellow, fontSize = 6.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  RIGHT PANEL
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcRightPanel(bpm: Int, currentBank: Int, activePadId: String?, padSettingsMap: Map<String, PadSettings>, onBpmDelta: (Int) -> Unit, onBankSelect: (Int) -> Unit, onPadVolume: (String, Float) -> Unit, onPadPan: (String, Float) -> Unit, modifier: Modifier = Modifier) {
    val ps = activePadId?.let { padSettingsMap[it] }
    val vals = listOf(bpm / 200f, ps?.volume ?: 0.8f, ((ps?.pan ?: 0f) + 1f) / 2f, ps?.filterSettings?.cutoffHz?.div(20000f) ?: 0.5f)
    val labels = listOf("BPM", "VOL", "PAN", "FLT"); val colors = listOf(MpcDisplayCyan, MpcDisplayGreen, MpcDisplayOrange, MpcPadLavender)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        vals.forEachIndexed { i, v -> QKnob(v, labels[i], colors[i], onInc = { when (i) { 0 -> onBpmDelta(1); 1 -> activePadId?.let { onPadVolume(it, (vals[1] + 0.05f).coerceAtMost(1f)) }; 2 -> activePadId?.let { onPadPan(it, ((vals[2] * 2f - 1f) + 0.1f).coerceIn(-1f, 1f)) } } }, onDec = { when (i) { 0 -> onBpmDelta(-1); 1 -> activePadId?.let { onPadVolume(it, (vals[1] - 0.05f).coerceAtLeast(0f)) }; 2 -> activePadId?.let { onPadPan(it, ((vals[2] * 2f - 1f) - 0.1f).coerceIn(-1f, 1f)) } } }, modifier = Modifier.fillMaxWidth().weight(1f)) }
        Box(Modifier.fillMaxWidth().background(MpcButtonDark, RoundedCornerShape(3.dp)).border(0.5.dp, MpcButtonMid, RoundedCornerShape(3.dp)).padding(vertical = 3.dp), contentAlignment = Alignment.Center) { Text("Q-LINK", color = MpcDisplayCyan, fontSize = 6.sp, fontWeight = FontWeight.Bold) }
        val bl = listOf("A","B","C","D","E","F","G","H"); for (r in 0 until 4) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { for (c in 0 until 2) { val idx = r * 2 + c; BankBtn(bl[idx], currentBank == idx, { onBankSelect(idx) }, Modifier.weight(1f)) } } }
    }
}
@Composable private fun QKnob(value: Float, label: String, knobColor: Color, onInc: () -> Unit, onDec: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { val cx = size.width / 2f; val cy = size.height / 2f; val r = minOf(cx, cy) * 0.78f; val sw = r * 0.24f; drawArc(MpcKnobRing, 135f, 270f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round)); drawArc(knobColor, 135f, 270f * value.coerceIn(0f, 1f), false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round)); drawCircle(MpcButtonMid, r * 0.3f, Offset(cx, cy)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text("−", color = knobColor.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDec() }); Text("+", color = knobColor.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onInc() }) }; Text(label, color = MpcTextSecondary, fontSize = 6.sp) }
    }
}
@Composable private fun BankBtn(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) { Box(modifier.background(if (active) MpcButtonActive else MpcButtonDark, RoundedCornerShape(3.dp)).border(0.5.dp, if (active) MpcRedBright else MpcButtonMid, RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(3.dp), contentAlignment = Alignment.Center) { Text(label, color = if (active) MpcTextPrimary else MpcButtonText, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }

// ═════════════════════════════════════════════════════════════════════════════
//  FUNCTION ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcFunctionRow(shiftActive: Boolean, onStepSeq: () -> Unit, onTC: () -> Unit, onSampler: () -> Unit, onSampleEdit: () -> Unit, onProgramEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        FnBtn("STEP SEQ","AUTOMATION", shiftActive, onStepSeq, Modifier.weight(1f))
        FnBtn("TC","ON/OFF", shiftActive, onTC, Modifier.weight(1f))
        FnBtn("SAMPLER","LOOPER", shiftActive, onSampler, Modifier.weight(1f))
        FnBtn("SAMPLE\nEDIT","Q-LINK EDIT", shiftActive, onSampleEdit, Modifier.weight(1f))
        FnBtn("PROGRAM\nEDIT","MIDI CTRL", shiftActive, onProgramEdit, Modifier.weight(1f))
    }
}
@Composable private fun FnBtn(primary: String, secondary: String, shift: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.background(if (shift) MpcButtonActive else MpcButtonDark, RoundedCornerShape(4.dp)).border(0.5.dp, if (shift) MpcDisplayCyan.copy(alpha = 0.4f) else MpcButtonMid, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(primary, color = if (shift) MpcTextSecondary else MpcButtonText, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 8.sp); Text(secondary, color = if (shift) MpcDisplayCyan else MpcDisplayCyan.copy(alpha = 0.5f), fontSize = 5.sp, textAlign = TextAlign.Center, lineHeight = 7.sp)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  UTILITY ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcUtilityRow(canUndo: Boolean, canRedo: Boolean, shiftActive: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onTapTempo: () -> Unit, onShift: () -> Unit, onMenu: () -> Unit, onPlus: () -> Unit, onMinus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        UtBtn("MENU", false, { onMenu() }, Modifier.weight(1f)); UtBtn("SHIFT", shiftActive, { onShift() }, Modifier.weight(1f), ac = MpcDisplayCyan)
        UtBtn("−", false, { onMinus() }, Modifier.weight(0.6f)); UtBtn("+", false, { onPlus() }, Modifier.weight(0.6f))
        UtBtn("TAP\nTEMPO", false, { onTapTempo() }, Modifier.weight(1f)); UtBtn("UNDO", canUndo, { onUndo() }, Modifier.weight(1f), ac = MpcDisplayOrange); UtBtn("REDO", canRedo, { onRedo() }, Modifier.weight(1f), ac = MpcDisplayOrange)
    }
}
@Composable private fun UtBtn(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, ac: Color = MpcTextPrimary) { Box(modifier.height(26.dp).background(if (active) ac.copy(alpha = 0.15f) else MpcButtonDark, RoundedCornerShape(3.dp)).border(0.5.dp, if (active) ac else MpcButtonMid, RoundedCornerShape(3.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, color = if (active) ac else MpcButtonText, fontSize = 6.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 8.sp) } }

// ═════════════════════════════════════════════════════════════════════════════
//  TRANSPORT ROW
// ═════════════════════════════════════════════════════════════════════════════
@Composable private fun MpcTransportRow(isPlaying: Boolean, isRecording: Boolean, onRec: () -> Unit, onOverDub: () -> Unit, onStop: () -> Unit, onPlay: () -> Unit, onPlayStart: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TpBtn("REC", isRecording, MpcRedBright, onRec, Modifier.weight(1f)); TpBtn("OVER\nDUB", false, MpcDisplayOrange, onOverDub, Modifier.weight(1f))
        TpBtn("STOP", !isPlaying && !isRecording, MpcButtonText, onStop, Modifier.weight(1f)); TpBtn("PLAY", isPlaying && !isRecording, MpcDisplayGreen, onPlay, Modifier.weight(1f))
        TpBtn("PLAY\nSTART", false, MpcDisplayGreen, onPlayStart, Modifier.weight(1f))
    }
}
@Composable private fun TpBtn(label: String, active: Boolean, ac: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current; var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(when { pressed -> ac.copy(alpha = 0.35f); active -> ac.copy(alpha = 0.18f); else -> MpcButtonDark }, tween(80), label = "tbg")
    Box(modifier.height(34.dp).background(bg, RoundedCornerShape(4.dp)).border(if (active) 1.5.dp else 0.5.dp, if (active) ac else MpcButtonMid, RoundedCornerShape(4.dp)).pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); tryAwaitRelease(); pressed = false }, onTap = { onClick() }) }, contentAlignment = Alignment.Center) {
        Text(label, color = if (active) ac else MpcButtonText, fontSize = 7.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 9.sp)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  PAD CONFIG SHEET — full: Basic + Filter tabs (matches CompactMainScreen)
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcPadConfigSheet(padSettings: PadSettings, onSave: (PadSettings) -> Unit, onDismiss: () -> Unit) {
    var volume       by remember { mutableFloatStateOf(padSettings.volume) }
    var pan          by remember { mutableFloatStateOf(padSettings.pan) }
    var playbackMode by remember { mutableStateOf(padSettings.playbackMode) }
    var muteGroup    by remember { mutableIntStateOf(padSettings.muteGroup) }
    var polyphony    by remember { mutableIntStateOf(padSettings.polyphony) }
    var tuningCoarse by remember { mutableIntStateOf(padSettings.tuningCoarse) }
    var tuningFine   by remember { mutableIntStateOf(padSettings.tuningFine) }
    // Filter
    var filterEnabled  by remember { mutableStateOf(padSettings.filterSettings.enabled) }
    var filterMode     by remember { mutableStateOf(padSettings.filterSettings.mode) }
    var filterCutoff   by remember { mutableFloatStateOf(padSettings.filterSettings.cutoffHz) }
    var filterRes      by remember { mutableFloatStateOf(padSettings.filterSettings.resonance) }
    // Tab
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(padSettings.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
            TabRow(selectedTabIndex = selectedTab, containerColor = MpcBodyDark, contentColor = MpcDisplayCyan) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Basic", Modifier.padding(10.dp), color = if (selectedTab == 0) MpcDisplayCyan else MpcTextSecondary, fontSize = 13.sp) }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Filter", Modifier.padding(10.dp), color = if (selectedTab == 1) MpcDisplayCyan else MpcTextSecondary, fontSize = 13.sp) }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text("Tuning", Modifier.padding(10.dp), color = if (selectedTab == 2) MpcDisplayCyan else MpcTextSecondary, fontSize = 13.sp) }
            }
            when (selectedTab) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MpcSlider("Volume", "${(volume * 100).toInt()}%", volume, { volume = it }, MpcDisplayGreen)
                    MpcSlider("Pan", when { pan < -0.05f -> "L ${(-pan * 100).toInt()}"; pan > 0.05f -> "R ${(pan * 100).toInt()}"; else -> "Center" }, (pan + 1f) / 2f, { pan = it * 2f - 1f }, MpcDisplayOrange)
                    Text("Playback Mode", color = MpcTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaybackMode.values().forEach { m -> FilterChip(selected = playbackMode == m, onClick = { playbackMode = m }, label = { Text(m.name.replace("_", " "), fontSize = 11.sp) }) }
                    }
                    Text("Mute Group: ${if (muteGroup == 0) "None" else muteGroup}", color = MpcTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (0..4).forEach { g -> FilterChip(selected = muteGroup == g, onClick = { muteGroup = g }, label = { Text(if (g == 0) "Off" else "$g") }) } }
                    Text("Polyphony: $polyphony", color = MpcTextSecondary, fontSize = 12.sp)
                    Slider(value = polyphony / 32f, onValueChange = { polyphony = (it * 32).toInt().coerceIn(1, 32) }, colors = SliderDefaults.colors(thumbColor = MpcPadTeal, activeTrackColor = MpcPadTeal))
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Filter", color = MpcTextPrimary, fontSize = 14.sp); Switch(checked = filterEnabled, onCheckedChange = { filterEnabled = it })
                    }
                    if (filterEnabled) {
                        Text("Mode", color = MpcTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterMode.values().forEach { m -> FilterChip(selected = filterMode == m, onClick = { filterMode = m }, label = { Text(m.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }) }
                        }
                        val cutoffSlider = (log2(filterCutoff.coerceAtLeast(20f) / 20f) / 10f).coerceIn(0f, 1f)
                        MpcSlider("Cutoff", if (filterCutoff >= 1000f) "${"%.1f".format(filterCutoff / 1000f)} kHz" else "${filterCutoff.toInt()} Hz", cutoffSlider, { filterCutoff = (20.0 * 2.0.pow(it.toDouble() * 10.0)).toFloat().coerceIn(20f, 20000f) }, MpcPadLavender)
                        MpcSlider("Resonance", "%.2f".format(filterRes), ((filterRes - 0.5f) / 24.5f).coerceIn(0f, 1f), { filterRes = (0.5f + it * 24.5f).coerceIn(0.5f, 25f) }, MpcPadSky)
                    }
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Coarse Tuning: ${if (tuningCoarse >= 0) "+$tuningCoarse" else "$tuningCoarse"} st", color = MpcTextSecondary, fontSize = 12.sp)
                    Slider(value = (tuningCoarse + 24f) / 48f, onValueChange = { tuningCoarse = (it * 48f - 24f).toInt().coerceIn(-24, 24) }, colors = SliderDefaults.colors(thumbColor = MpcDisplayCyan, activeTrackColor = MpcDisplayCyan))
                    Text("Fine Tuning: ${if (tuningFine >= 0) "+$tuningFine" else "$tuningFine"} ct", color = MpcTextSecondary, fontSize = 12.sp)
                    Slider(value = (tuningFine + 100f) / 200f, onValueChange = { tuningFine = (it * 200f - 100f).toInt().coerceIn(-100, 100) }, colors = SliderDefaults.colors(thumbColor = MpcDisplayCyan, activeTrackColor = MpcDisplayCyan))
                    // Envelope preview
                    Text("Amp Envelope", color = MpcTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("A: ${padSettings.ampEnvelope.attackMs.toInt()}ms", "D: ${padSettings.ampEnvelope.decayMs.toInt()}ms", "S: ${(padSettings.ampEnvelope.sustainLevel * 100).toInt()}%", "R: ${padSettings.ampEnvelope.releaseMs.toInt()}ms").forEach { Text(it, color = MpcDisplayGreen, fontSize = 9.sp, modifier = Modifier.background(MpcBodyDark, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MpcTextPrimary)) { Text("Cancel") }
                Button(onClick = { onSave(padSettings.copy(volume = volume, pan = pan, playbackMode = playbackMode, muteGroup = muteGroup, polyphony = polyphony, tuningCoarse = tuningCoarse, tuningFine = tuningFine, filterSettings = FilterSettings(enabled = filterEnabled, mode = filterMode, cutoffHz = filterCutoff, resonance = filterRes))) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MpcDisplayGreen)) { Text("Save", color = Color.Black) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MpcSlider(label: String, valueText: String, value: Float, onChange: (Float) -> Unit, accentColor: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MpcTextSecondary, fontSize = 12.sp); Text(valueText, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Slider(value = value, onValueChange = onChange, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  MIXER SHEET — per-pad volume & pan (from QuickAccessPanelContent)
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcMixerSheet(padSettingsMap: Map<String, PadSettings>, drumViewModel: DrumTrackViewModel, onDismiss: () -> Unit) {
    val pads = remember(padSettingsMap) { padSettingsMap.entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("🎚️ Mixer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(pads.toList()) { (padId, settings) ->
                    Column(Modifier.fillMaxWidth().background(MpcBodyDark, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(settings.name, color = MpcTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.VolumeUp, null, tint = MpcDisplayGreen, modifier = Modifier.size(14.dp))
                            Slider(value = settings.volume, onValueChange = { drumViewModel.updatePadSettings(settings.copy(volume = it)) }, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = MpcDisplayGreen, activeTrackColor = MpcDisplayGreen))
                            Text("${(settings.volume * 100).toInt()}%", color = MpcTextSecondary, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Pan", color = MpcTextSecondary, fontSize = 10.sp, modifier = Modifier.width(24.dp))
                            Slider(value = settings.pan, onValueChange = { drumViewModel.updatePadSettings(settings.copy(pan = it)) }, valueRange = -1f..1f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = MpcDisplayOrange, activeTrackColor = MpcDisplayOrange))
                            Text(when { settings.pan < -0.05f -> "L${(-settings.pan * 100).toInt()}"; settings.pan > 0.05f -> "R${(settings.pan * 100).toInt()}"; else -> "C" }, color = MpcTextSecondary, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SAMPLING / RECORDING SHEET
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcSamplingSheet(samplingVM: SamplingViewModel, samplingUi: com.high.theone.model.SamplingUiState, onDismiss: () -> Unit) {
    val recordingState = samplingUi.recordingState
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🎤 Sampling", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
            // Record / Stop
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (recordingState.isRecording) samplingVM.stopRecording() else samplingVM.startRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (recordingState.isRecording) MpcRedBright else MpcDisplayGreen)
                ) {
                    Icon(if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (recordingState.isRecording) "Stop" else "Record", color = if (recordingState.isRecording) Color.White else Color.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (recordingState.isRecording) recordingState.formattedDuration else "Input Level", color = MpcTextSecondary, fontSize = 11.sp)
                    LinearProgressIndicator(progress = { recordingState.peakLevel }, modifier = Modifier.fillMaxWidth(), color = MpcDisplayGreen, trackColor = MpcBodyDark)
                }
            }
            if (recordingState.error != null) Text(recordingState.error, color = MpcRedBright, fontSize = 11.sp)
            // Sample list
            Text("Samples (${samplingUi.availableSamples.size})", color = MpcTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (samplingUi.availableSamples.isEmpty()) {
                Text("No samples yet. Tap Record to capture audio.", color = MpcTextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(samplingUi.availableSamples) { sample ->
                        Row(Modifier.fillMaxWidth().background(MpcBodyDark, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AudioFile, null, tint = MpcDisplayCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sample.name, color = MpcTextPrimary, fontSize = 12.sp)
                                Text("${sample.formattedDuration}  •  ${sample.formattedFileSize}", color = MpcTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  MIDI SHEET
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcMidiSheet(
    midiVM: MidiSettingsViewModel,
    midiUi: com.high.theone.features.midi.ui.MidiSettingsUiState,
    connectedDevices: Map<String, com.high.theone.midi.model.MidiDeviceInfo>,
    availableDevices: List<com.high.theone.midi.model.MidiDeviceInfo>,
    onOpenFull: () -> Unit, onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🎹 MIDI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
                Box(Modifier.background(if (midiUi.midiEnabled) MpcDisplayGreen else MpcBodyLight, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) { Text(if (midiUi.midiEnabled) "ON" else "OFF", fontSize = 10.sp, color = if (midiUi.midiEnabled) Color.Black else MpcTextSecondary) }
            }
            if (midiUi.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MpcDisplayCyan)
            // Connected
            if (connectedDevices.isNotEmpty()) {
                Text("Connected (${connectedDevices.size})", color = MpcDisplayGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                connectedDevices.values.forEach { dev ->
                    Row(Modifier.fillMaxWidth().background(MpcBodyDark, RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MpcDisplayGreen, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(dev.name, color = MpcTextPrimary, fontSize = 12.sp)
                    }
                }
            } else {
                Text("No MIDI devices connected", color = MpcTextSecondary, fontSize = 12.sp)
            }
            // Available
            if (availableDevices.isNotEmpty()) {
                Text("Available", color = MpcTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(Modifier.heightIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(availableDevices) { dev ->
                        Row(Modifier.fillMaxWidth().background(MpcBodyDark, RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Usb, null, tint = MpcTextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                            Text(dev.name, color = MpcTextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { midiVM.connectDevice(dev.id) }) { Text("Connect", color = MpcDisplayCyan, fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (midiUi.errorMessage != null) Text(midiUi.errorMessage!!, color = MpcRedBright, fontSize = 11.sp)
            Button(onClick = onOpenFull, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MpcDisplayCyan)) {
                Icon(Icons.Default.Settings, null, tint = Color.Black); Spacer(Modifier.width(6.dp)); Text("Open Full MIDI Settings", color = Color.Black)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  SETTINGS SHEET
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcSettingsSheet(navController: NavHostController, onDismiss: () -> Unit) {
    var audioLatency by remember { mutableStateOf("Low") }
    var enableAnimations by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⚙️ Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
            // Audio
            Text("Audio", color = MpcDisplayCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SettingsRow("Latency", audioLatency)
            SettingsRow("Sample Rate", "44.1 kHz")
            SettingsRow("Buffer Size", "256 samples")
            HorizontalDivider(color = MpcButtonMid.copy(alpha = 0.4f))
            // UI
            Text("UI", color = MpcDisplayCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Animations", color = MpcTextPrimary, fontSize = 12.sp)
                Switch(checked = enableAnimations, onCheckedChange = { enableAnimations = it })
            }
            HorizontalDivider(color = MpcButtonMid.copy(alpha = 0.4f))
            // Navigation shortcuts
            Text("Quick Access", color = MpcDisplayCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SettingsNavBtn("Project Settings") { navController.navigate("project_settings"); onDismiss() }
            SettingsNavBtn("Classic View") { navController.navigate("compact_main"); onDismiss() }
            SettingsNavBtn("Debug Screen") { navController.navigate("debug_screen"); onDismiss() }
            HorizontalDivider(color = MpcButtonMid.copy(alpha = 0.4f))
            // Info
            Text("App", color = MpcDisplayCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            SettingsRow("Version", "1.0.0")
            SettingsRow("Build", "2026.02")
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable private fun SettingsRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MpcTextPrimary, fontSize = 12.sp); Text(value, color = MpcTextSecondary, fontSize = 12.sp) } }
@Composable private fun SettingsNavBtn(label: String, onClick: () -> Unit) { TextButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(label, color = MpcTextPrimary, fontSize = 12.sp) } }

// ═════════════════════════════════════════════════════════════════════════════
//  PATTERN MANAGEMENT SHEET
// ═════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MpcPatternSheet(patterns: List<Pattern>, currentPatternId: String?, seqViewModel: SimpleSequencerViewModel, onDismiss: () -> Unit) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText   by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MpcBodyMid) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("📋 Patterns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MpcTextPrimary)
                IconButton(onClick = { seqViewModel.createPattern("Pattern ${patterns.size + 1}", 16) }) { Icon(Icons.Default.Add, "New", tint = MpcDisplayGreen) }
            }
            if (patterns.isEmpty()) {
                Text("No patterns yet. Tap + to create one.", color = MpcTextSecondary, fontSize = 12.sp)
            } else {
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(patterns) { pat ->
                        val isCurrent = pat.id == currentPatternId
                        Row(Modifier.fillMaxWidth().background(if (isCurrent) MpcDisplayCyan.copy(alpha = 0.12f) else MpcBodyDark, RoundedCornerShape(6.dp)).border(if (isCurrent) 1.dp else 0.dp, if (isCurrent) MpcDisplayCyan else Color.Transparent, RoundedCornerShape(6.dp)).clickable { seqViewModel.selectPattern(pat.id) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (renameTarget == pat.id) {
                                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, modifier = Modifier.weight(1f), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MpcDisplayCyan, cursorColor = MpcDisplayCyan), trailingIcon = {
                                    IconButton(onClick = { seqViewModel.renamePattern(pat.id, renameText); renameTarget = null }) { Icon(Icons.Default.Check, null, tint = MpcDisplayGreen) }
                                })
                            } else {
                                Column(Modifier.weight(1f)) {
                                    Text(pat.name, color = if (isCurrent) MpcDisplayCyan else MpcTextPrimary, fontSize = 13.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                                    Text("${pat.length} steps  •  ${pat.tempo.toInt()} BPM  •  ${pat.steps.values.sumOf { it.size }} notes", color = MpcTextSecondary, fontSize = 10.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { renameTarget = pat.id; renameText = pat.name }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit, "Rename", tint = MpcTextSecondary, modifier = Modifier.size(14.dp)) }
                                IconButton(onClick = { seqViewModel.duplicatePattern(pat.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy", tint = MpcTextSecondary, modifier = Modifier.size(14.dp)) }
                                if (patterns.size > 1) IconButton(onClick = { seqViewModel.deletePattern(pat.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Delete", tint = MpcRedBright.copy(alpha = 0.7f), modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  TC (TIMING CORRECT / QUANTIZE) DIALOG
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun MpcTCDialog(currentSwing: Float, seqViewModel: SimpleSequencerViewModel, onDismiss: () -> Unit) {
    var swing by remember { mutableFloatStateOf(currentSwing) }
    var selectedQuantize by remember { mutableStateOf("1/16") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏱ Timing Correct", color = MpcTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quantize", color = MpcTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("OFF", "1/4", "1/8", "1/16", "1/32").forEach { q -> FilterChip(selected = selectedQuantize == q, onClick = { selectedQuantize = q }, label = { Text(q, fontSize = 10.sp) }) }
                }
                Text("Swing: ${(swing * 100).toInt()}%", color = MpcTextSecondary, fontSize = 12.sp)
                Slider(value = swing / 0.75f, onValueChange = { swing = it * 0.75f }, colors = SliderDefaults.colors(thumbColor = MpcDisplayOrange, activeTrackColor = MpcDisplayOrange))
            }
        },
        confirmButton = {
            TextButton(onClick = { seqViewModel.handleTransportAction(TransportControlAction.SetSwing(swing)); onDismiss() }) { Text("Apply", color = MpcDisplayGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MpcTextSecondary) } },
        containerColor = MpcBodyMid
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  BROWSE DIALOG — enhanced with sample list + nav
// ═════════════════════════════════════════════════════════════════════════════
@Composable
private fun MpcBrowseDialog(navController: NavHostController, samplingUi: com.high.theone.model.SamplingUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📂 Browse", color = MpcTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Samples section
                if (samplingUi.availableSamples.isNotEmpty()) {
                    Text("Samples (${samplingUi.availableSamples.size})", color = MpcDisplayCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.heightIn(max = 120.dp)) {
                        items(samplingUi.availableSamples) { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AudioFile, null, tint = MpcDisplayCyan, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                Text(s.name, color = MpcTextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(s.formattedDuration, color = MpcTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = MpcButtonMid.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
                }
                // Navigation
                Text("Navigate", color = MpcDisplayCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                BrowseNavBtn("Step Sequencer") { navController.navigate("step_sequencer_screen"); onDismiss() }
                BrowseNavBtn("Drum Pads") { navController.navigate("drum_pad_screen"); onDismiss() }
                BrowseNavBtn("MIDI Settings") { navController.navigate("midi_settings"); onDismiss() }
                BrowseNavBtn("MIDI Mapping") { navController.navigate("midi_mapping"); onDismiss() }
                BrowseNavBtn("MIDI Monitor") { navController.navigate("midi_monitor"); onDismiss() }
                BrowseNavBtn("Synth") { navController.navigate("synth_screen"); onDismiss() }
                BrowseNavBtn("Project Settings") { navController.navigate("project_settings"); onDismiss() }
                BrowseNavBtn("Classic View") { navController.navigate("compact_main"); onDismiss() }
                BrowseNavBtn("Debug") { navController.navigate("debug_screen"); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MpcBodyMid
    )
}
@Composable private fun BrowseNavBtn(label: String, onClick: () -> Unit) { Button(onClick = onClick, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MpcButtonDark)) { Text(label, color = MpcButtonText, fontSize = 12.sp) } }
