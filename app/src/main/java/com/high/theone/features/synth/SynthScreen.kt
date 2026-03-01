package com.high.theone.features.synth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.high.theone.model.*
import kotlin.math.log2
import kotlin.math.pow

// ─── Colour palette ───────────────────────────────────────────────────────────
private val BgColor        = Color(0xFF121212)
private val SurfaceColor   = Color(0xFF1E1E1E)
private val AccentColor    = Color(0xFF00E5FF)
private val AccentDim      = Color(0xFF007A8A)
private val OnSurface      = Color(0xFFE0E0E0)
private val SubtleText     = Color(0xFF808080)
private val WhiteKeyColor  = Color(0xFFF0F0F0)
private val BlackKeyColor  = Color(0xFF1A1A1A)
private val KeyPressedColor = Color(0xFF00E5FF)

// ─── SynthScreen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthScreen(
    navController: NavHostController,
    viewModel: SynthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val pluginLoaded by viewModel.pluginLoaded.collectAsState()

    val tabs = listOf("OSC", "FILTER", "ENV", "LFO", "MASTER")
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sketching Synth",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = OnSurface)
                    }
                },
                actions = {
                    if (!pluginLoaded) {
                        Text("Loading...", color = SubtleText,
                            modifier = Modifier.padding(end = 12.dp))
                    } else {
                        Text("POLY  8v", color = SubtleText, fontSize = 11.sp,
                            modifier = Modifier.padding(end = 12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgColor)
        ) {
            // ── Tab row ──────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceColor,
                contentColor = AccentColor
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) AccentColor else SubtleText)
                        }
                    )
                }
            }

            // ── Tab content ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> OscTab(state, viewModel)
                    1 -> FilterTab(state, viewModel)
                    2 -> EnvTab(state, viewModel)
                    3 -> LfoTab(state, viewModel)
                    4 -> MasterTab(state, viewModel)
                }
            }

            HorizontalDivider(color = AccentDim.copy(alpha = 0.3f), thickness = 1.dp)

            // ── Virtual keyboard (always visible) ────────────────────────────
            VirtualKeyboard(
                octave = state.keyboardOctave,
                onNoteOn  = { note, vel -> viewModel.noteOn(note, vel) },
                onNoteOff = { note -> viewModel.noteOff(note) },
                onOctaveDown = { viewModel.setKeyboardOctave(state.keyboardOctave - 1) },
                onOctaveUp   = { viewModel.setKeyboardOctave(state.keyboardOctave + 1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF0D0D0D))
            )
        }
    }
}

// ─── OSC Tab ─────────────────────────────────────────────────────────────────
@Composable
private fun OscTab(state: SynthState, vm: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SynthSection(title = "OSC 1") {
            WaveformSelector(
                selected = state.osc1Wave,
                onSelect = vm::setOsc1Wave
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OctaveSelector("Oct", state.osc1Octave, -2, 2, onSelect = vm::setOsc1Octave,
                    modifier = Modifier.weight(1f))
                SemiSelector("Semi", state.osc1Semi, -12, 12, vm::setOsc1Semi,
                    modifier = Modifier.weight(1f))
            }
            SynthSlider("Fine", state.osc1Fine, -100f, 100f,
                format = { "${it.toInt()} ct" },
                onValue = vm::setOsc1Fine)
            SynthSlider("Level", state.osc1Level, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setOsc1Level)
        }

        SynthSection(title = "OSC 2") {
            WaveformSelector(
                selected = state.osc2Wave,
                onSelect = vm::setOsc2Wave
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OctaveSelector("Oct", state.osc2Octave, -2, 2, onSelect = vm::setOsc2Octave,
                    modifier = Modifier.weight(1f))
                SemiSelector("Semi", state.osc2Semi, -12, 12, vm::setOsc2Semi,
                    modifier = Modifier.weight(1f))
            }
            SynthSlider("Fine", state.osc2Fine, -100f, 100f,
                format = { "${it.toInt()} ct" },
                onValue = vm::setOsc2Fine)
            SynthSlider("Level", state.osc2Level, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setOsc2Level)
        }

        SynthSection(title = "Mix") {
            SynthSlider("Sub Osc", state.subLevel, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setSubLevel)
            SynthSlider("Noise", state.noiseLevel, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setNoiseLevel)
        }
    }
}

// ─── Filter Tab ───────────────────────────────────────────────────────────────
@Composable
private fun FilterTab(state: SynthState, vm: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SynthSection(title = "Filter") {
            FilterTypeSelector(state.filterType, vm::setFilterType)
            SynthSlider("Cutoff", state.filterCutoff, 20f, 20000f,
                format = { if (it >= 1000f) "${"%.1f".format(it/1000f)}kHz" else "${it.toInt()}Hz" },
                onValue = vm::setFilterCutoff,
                logarithmic = true)
            SynthSlider("Resonance", state.filterResonance, 0.5f, 20f,
                format = { "%.2f".format(it) },
                onValue = vm::setFilterResonance)
            SynthSlider("Env Amount", state.filterEnvAmt, -1f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setFilterEnvAmt)
            SynthSlider("Key Track", state.filterKeyTrack, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setFilterKeyTrack)
            SynthSlider("Vel Sens", state.filterVelSens, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setFilterVelSens)
        }

        SynthSection(title = "Filter Envelope") {
            AdsrVisualizer(
                attack  = state.filtAttack,
                decay   = state.filtDecay,
                sustain = state.filtSustain,
                release = state.filtRelease,
                color   = AccentDim,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            )
            AdsrSliders(
                attack  = state.filtAttack,  onAttack  = vm::setFiltAttack,
                decay   = state.filtDecay,   onDecay   = vm::setFiltDecay,
                sustain = state.filtSustain, onSustain = vm::setFiltSustain,
                release = state.filtRelease, onRelease = vm::setFiltRelease
            )
        }
    }
}

// ─── Env Tab ──────────────────────────────────────────────────────────────────
@Composable
private fun EnvTab(state: SynthState, vm: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SynthSection(title = "Amp Envelope") {
            AdsrVisualizer(
                attack  = state.ampAttack,
                decay   = state.ampDecay,
                sustain = state.ampSustain,
                release = state.ampRelease,
                color   = AccentColor,
                modifier = Modifier.fillMaxWidth().height(96.dp)
            )
            AdsrSliders(
                attack  = state.ampAttack,  onAttack  = vm::setAmpAttack,
                decay   = state.ampDecay,   onDecay   = vm::setAmpDecay,
                sustain = state.ampSustain, onSustain = vm::setAmpSustain,
                release = state.ampRelease, onRelease = vm::setAmpRelease
            )
        }
    }
}

// ─── LFO Tab ─────────────────────────────────────────────────────────────────
@Composable
private fun LfoTab(state: SynthState, vm: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SynthSection(title = "LFO 1") {
            LfoShapeSelector(state.lfo1Shape, vm::setLfo1Shape)
            SynthSlider("Rate", state.lfo1Rate, 0.01f, 20f,
                format = { "%.2f Hz".format(it) },
                onValue = vm::setLfo1Rate, logarithmic = true)
            SynthSlider("Depth", state.lfo1Depth, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setLfo1Depth)
            LfoDestSelector(state.lfo1Dest, vm::setLfo1Dest)
        }

        SynthSection(title = "LFO 2") {
            LfoShapeSelector(state.lfo2Shape, vm::setLfo2Shape)
            SynthSlider("Rate", state.lfo2Rate, 0.01f, 20f,
                format = { "%.2f Hz".format(it) },
                onValue = vm::setLfo2Rate, logarithmic = true)
            SynthSlider("Depth", state.lfo2Depth, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setLfo2Depth)
            LfoDestSelector(state.lfo2Dest, vm::setLfo2Dest)
        }
    }
}

// ─── Master Tab ───────────────────────────────────────────────────────────────
@Composable
private fun MasterTab(state: SynthState, vm: SynthViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SynthSection(title = "Master") {
            SynthSlider("Volume", state.masterVolume, 0f, 1f,
                format = { "${(it * 100).toInt()}%" },
                onValue = vm::setMasterVolume)
            SynthSlider("Pan", state.pan, -1f, 1f,
                format = {
                    when {
                        it < -0.01f -> "L ${(-it * 100).toInt()}%"
                        it > 0.01f  -> "R ${(it * 100).toInt()}%"
                        else        -> "Center"
                    }
                },
                onValue = vm::setPan)
            SynthSlider("Portamento", state.portamento, 0f, 2000f,
                format = { if (it < 1f) "Off" else "${it.toInt()} ms" },
                onValue = vm::setPortamento)
            SynthSlider("Pitch Bend Range", state.pitchBendRange, 0f, 24f,
                format = { "${it.toInt()} st" },
                onValue = vm::setPitchBendRange)
        }
    }
}

// ─── Shared UI widgets ────────────────────────────────────────────────────────

@Composable
private fun SynthSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, shape = RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = AccentColor, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        content()
    }
}

@Composable
private fun SynthSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    format: (Float) -> String,
    onValue: (Float) -> Unit,
    logarithmic: Boolean = false
) {
    // Convert to/from 0..1 slider position
    fun toPos(v: Float): Float {
        if (!logarithmic) return ((v - min) / (max - min)).coerceIn(0f, 1f)
        val logMin = log2(min.coerceAtLeast(0.0001f))
        val logMax = log2(max)
        val logV   = log2(v.coerceAtLeast(0.0001f))
        return ((logV - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
    }
    fun fromPos(pos: Float): Float {
        if (!logarithmic) return min + pos * (max - min)
        val logMin = log2(min.coerceAtLeast(0.0001f))
        val logMax = log2(max)
        return 2f.pow(logMin + pos * (logMax - logMin))
    }

    var sliderPos by remember(value) { mutableFloatStateOf(toPos(value)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SubtleText, fontSize = 11.sp,
            modifier = Modifier.width(90.dp))
        Slider(
            value = sliderPos,
            onValueChange = { pos ->
                sliderPos = pos
                onValue(fromPos(pos))
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AccentColor,
                activeTrackColor = AccentColor,
                inactiveTrackColor = AccentDim.copy(alpha = 0.4f)
            )
        )
        Text(format(value), color = OnSurface, fontSize = 11.sp,
            modifier = Modifier.width(72.dp).padding(start = 8.dp))
    }
}

@Composable
private fun WaveformSelector(selected: Int, onSelect: (Int) -> Unit) {
    val labels = OscWaveformKt.entries.map { it.label }
    SegmentedButtons(labels, selected, onSelect)
}

@Composable
private fun FilterTypeSelector(selected: Int, onSelect: (Int) -> Unit) {
    SegmentedButtons(listOf("LP", "BP", "HP"), selected, onSelect)
}

@Composable
private fun LfoShapeSelector(selected: Int, onSelect: (Int) -> Unit) {
    val labels = LfoShapeKt.entries.map { it.label }
    SegmentedButtons(labels, selected, onSelect)
}

@Composable
private fun LfoDestSelector(selected: Int, onSelect: (Int) -> Unit) {
    val labels = LfoDestKt.entries.map { it.label }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dest", color = SubtleText, fontSize = 11.sp,
            modifier = Modifier.width(90.dp))
        SegmentedButtons(labels, selected, onSelect, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OctaveSelector(
    label: String,
    selected: Int,
    min: Int,
    max: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SubtleText, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected > min) onSelect(selected - 1) },
                modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null,
                    tint = AccentColor, modifier = Modifier.size(18.dp))
            }
            Text("$selected", color = OnSurface, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (selected < max) onSelect(selected + 1) },
                modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null,
                    tint = AccentColor, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SemiSelector(
    label: String,
    selected: Int,
    min: Int,
    max: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = SubtleText, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected > min) onSelect(selected - 1) },
                modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowLeft, null, tint = AccentColor,
                    modifier = Modifier.size(18.dp))
            }
            Text("${if (selected >= 0) "+$selected" else "$selected"}",
                color = OnSurface, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (selected < max) onSelect(selected + 1) },
                modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowRight, null, tint = AccentColor,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SegmentedButtons(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { idx, label ->
            val active = idx == selected
            Button(
                onClick = { onSelect(idx) },
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) AccentColor else AccentDim.copy(alpha = 0.25f),
                    contentColor   = if (active) Color.Black   else SubtleText
                )
            ) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AdsrSliders(
    attack: Float,  onAttack: (Float) -> Unit,
    decay: Float,   onDecay: (Float) -> Unit,
    sustain: Float, onSustain: (Float) -> Unit,
    release: Float, onRelease: (Float) -> Unit
) {
    SynthSlider("Attack",  attack,  1f,    10000f, { "${it.toInt()} ms" }, onAttack,  logarithmic = true)
    SynthSlider("Decay",   decay,   1f,    5000f,  { "${it.toInt()} ms" }, onDecay,   logarithmic = true)
    SynthSlider("Sustain", sustain, 0f,    1f,     { "${(it*100).toInt()}%" }, onSustain)
    SynthSlider("Release", release, 1f,    10000f, { "${it.toInt()} ms" }, onRelease, logarithmic = true)
}

// ─── ADSR Visualizer ─────────────────────────────────────────────────────────
@Composable
private fun AdsrVisualizer(
    attack: Float, decay: Float, sustain: Float, release: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()

        // Normalize times to total width segments
        val totalMs  = attack + decay + 200f + release  // 200ms = sustain visual hold
        val aFrac    = attack  / totalMs
        val dFrac    = decay   / totalMs
        val sFrac    = 200f    / totalMs
        val rFrac    = release / totalMs

        val drawW = w - pad * 2
        val x0 = pad
        val aX = x0 + aFrac * drawW
        val dX = aX  + dFrac * drawW
        val sX = dX  + sFrac * drawW
        val eX = sX  + rFrac * drawW

        val top    = pad
        val bottom = h - pad
        val susY   = top + (1f - sustain) * (bottom - top)

        val path = Path().apply {
            moveTo(x0, bottom)
            lineTo(aX, top)
            lineTo(dX, susY)
            lineTo(sX, susY)
            lineTo(eX, bottom)
        }

        drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx()
        ))

        // Fill under envelope
        val fillPath = Path().apply {
            moveTo(x0, bottom)
            lineTo(aX, top)
            lineTo(dX, susY)
            lineTo(sX, susY)
            lineTo(eX, bottom)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.12f))

        // Sustain level dot
        drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(dX, susY))
    }
}

// ─── Virtual Keyboard ─────────────────────────────────────────────────────────
// Piano layout: maps key within octave -> (white index, isBlack)
private val keyLayout = listOf(
    Pair(0, false),  // C  -> white[0]
    Pair(0, true),   // C# -> black between white[0] and white[1]
    Pair(1, false),  // D  -> white[1]
    Pair(1, true),   // D# -> black between white[1] and white[2]
    Pair(2, false),  // E  -> white[2]
    Pair(3, false),  // F  -> white[3]
    Pair(3, true),   // F# -> black between white[3] and white[4]
    Pair(4, false),  // G  -> white[4]
    Pair(4, true),   // G# -> black between white[4] and white[5]
    Pair(5, false),  // A  -> white[5]
    Pair(5, true),   // A# -> black between white[5] and white[6]
    Pair(6, false),  // B  -> white[6]
)

private const val WHITE_KEYS_PER_OCTAVE = 7
private const val NUM_OCTAVES_SHOWN = 2

@Composable
private fun VirtualKeyboard(
    octave: Int,
    onNoteOn:  (Int, Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    onOctaveDown: () -> Unit,
    onOctaveUp:   () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track pressed notes by pointer ID
    val pressedNotes = remember { mutableStateMapOf<PointerId, Int>() }

    Box(modifier = modifier) {
        // Octave buttons
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = onOctaveDown,
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                border = BorderStroke(1.dp, AccentColor)
            ) {
                Text("-", fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OCT", color = SubtleText, fontSize = 8.sp)
                Text("$octave", color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = onOctaveUp,
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                border = BorderStroke(1.dp, AccentColor)
            ) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }

        // Piano keys
        val startNote = (octave - 1) * 12   // e.g. octave 4 -> C4 = MIDI 60 when base=(4-1)*12=36? no, C4=60
        // MIDI note formula: C4 = 60, octave 4 = notes 60..71
        // Use standard: C(n) = 12*(n+1)
        val midiBase = 12 * (octave + 1)     // C of the current octave

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 88.dp)
                .pointerInput(octave) {
                    awaitEachGesture {
                        // Wait for first finger
                        val firstDown = awaitFirstDown()
                        firstDown.consume()

                        fun noteAtPos(pos: Offset): Int {
                            val totalWhiteKeys = WHITE_KEYS_PER_OCTAVE * NUM_OCTAVES_SHOWN
                            val whiteKeyWidth  = size.width.toFloat() / totalWhiteKeys
                            val blackKeyHeight = size.height * 0.6f

                            // Check black keys first (on top)
                            for (oct in 0 until NUM_OCTAVES_SHOWN) {
                                for (semitone in keyLayout.indices) {
                                    val (whiteIdx, isBlack) = keyLayout[semitone]
                                    if (!isBlack) continue
                                    val bx = ((oct * WHITE_KEYS_PER_OCTAVE + whiteIdx + 0.65f) * whiteKeyWidth)
                                    val bw = whiteKeyWidth * 0.6f
                                    if (pos.x >= bx && pos.x <= bx + bw && pos.y <= blackKeyHeight) {
                                        return midiBase + oct * 12 + semitone
                                    }
                                }
                            }
                            // White keys
                            val whiteIdx = (pos.x / whiteKeyWidth).toInt().coerceIn(0, totalWhiteKeys - 1)
                            val oct      = whiteIdx / WHITE_KEYS_PER_OCTAVE
                            val wInOct   = whiteIdx % WHITE_KEYS_PER_OCTAVE
                            val semitone = keyLayout.indexOfFirst { !it.second && it.first == wInOct }
                            return if (semitone >= 0) midiBase + oct * 12 + semitone else -1
                        }

                        val firstNote = noteAtPos(firstDown.position)
                        if (firstNote >= 0) {
                            val vel = (127 - (firstDown.position.y / size.height * 64).toInt())
                                .coerceIn(40, 127)
                            pressedNotes[firstDown.id] = firstNote
                            onNoteOn(firstNote, vel)
                        }

                        // Handle subsequent pointer events
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                change.consume()
                                when {
                                    change.pressed -> {
                                        val note = noteAtPos(change.position)
                                        val prev = pressedNotes[change.id]
                                        if (note >= 0 && note != prev) {
                                            if (prev != null) onNoteOff(prev)
                                            val vel = (127 - (change.position.y / size.height * 64).toInt())
                                                .coerceIn(40, 127)
                                            pressedNotes[change.id] = note
                                            onNoteOn(note, vel)
                                        }
                                    }
                                    else -> {
                                        pressedNotes.remove(change.id)?.let { onNoteOff(it) }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Clean up any remaining pressed notes
                        pressedNotes.values.forEach { onNoteOff(it) }
                        pressedNotes.clear()
                    }
                }
        ) {
            drawPianoKeys(
                midiBase         = midiBase,
                numOctaves       = NUM_OCTAVES_SHOWN,
                pressedNotes     = pressedNotes.values.toSet(),
                whiteKeyColor    = WhiteKeyColor,
                blackKeyColor    = BlackKeyColor,
                pressedKeyColor  = KeyPressedColor,
                keyBorderColor   = Color(0xFF333333)
            )
        }
    }
}

private fun DrawScope.drawPianoKeys(
    midiBase: Int,
    numOctaves: Int,
    pressedNotes: Set<Int>,
    whiteKeyColor: Color,
    blackKeyColor: Color,
    pressedKeyColor: Color,
    keyBorderColor: Color
) {
    val totalWhiteKeys = WHITE_KEYS_PER_OCTAVE * numOctaves
    val whiteW = size.width / totalWhiteKeys
    val whiteH = size.height
    val blackW = whiteW * 0.58f
    val blackH = whiteH * 0.60f

    // Draw white keys
    for (oct in 0 until numOctaves) {
        for (semitone in keyLayout.indices) {
            val (whiteIdx, isBlack) = keyLayout[semitone]
            if (isBlack) continue
            val midiNote = midiBase + oct * 12 + semitone
            val x = (oct * WHITE_KEYS_PER_OCTAVE + whiteIdx) * whiteW
            val color = if (midiNote in pressedNotes) pressedKeyColor else whiteKeyColor
            drawRoundRect(color, topLeft = Offset(x + 1f, 0f),
                size = Size(whiteW - 2f, whiteH - 2f),
                cornerRadius = CornerRadius(2f))
            drawRoundRect(keyBorderColor,
                topLeft = Offset(x, 0f),
                size = Size(whiteW, whiteH),
                cornerRadius = CornerRadius(2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        }
    }

    // Draw black keys on top
    for (oct in 0 until numOctaves) {
        for (semitone in keyLayout.indices) {
            val (whiteIdx, isBlack) = keyLayout[semitone]
            if (!isBlack) continue
            val midiNote = midiBase + oct * 12 + semitone
            val bx = (oct * WHITE_KEYS_PER_OCTAVE + whiteIdx + 0.65f) * whiteW
            val color = if (midiNote in pressedNotes) pressedKeyColor else blackKeyColor
            drawRoundRect(color,
                topLeft = Offset(bx, 0f),
                size = Size(blackW, blackH),
                cornerRadius = CornerRadius(2f))
        }
    }
}
