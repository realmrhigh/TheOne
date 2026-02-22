# TheOne MPC App - Codebase Analysis & Prioritized Fix List

## Current State Summary

The app has substantial code (~5,600 lines Kotlin + ~3,600 lines C++), but much of it is
**disabled, stubbed, or has critical issues**. The architecture is solid (MVVM/Hilt/Compose/Oboe),
and the low-level audio engine works for basic sample triggering. However, the gap between
"code exists" and "code works end-to-end" is significant.

---

## Build Map Comparison

### Completed (verified working)
- **C1: Audio Engine** - Oboe stream, JNI bridge, sample loading, test sample generation, legacy drum pad triggering
- **C4: UI Framework (partial)** - Compose + Material3 + Hilt + Navigation all working
- **M1: Basic Sampling (partial)** - Legacy `triggerDrumPad()` path works; formal `playPadSample()` is a no-op
- **M2: Basic Sequencing (Kotlin side)** - State management, step toggling, pattern management
- **M7: Sample Editing (partial)** - Waveform display, trimming, normalize, reverse
- **AVST Plugin System** - SketchingSynth plugin, parameter system, JNI bridge (90% complete)

### Partially Done (code exists but broken/disabled)
- **Compact UI** - 30+ files all renamed to `.bak`, ViewModel has compilation errors
- **C3: Project Management** - `ProjectManagerImpl` is 100% stubbed (every method returns Failure)
- **M3: Sound Design** - ADSR/LFO exist in C++ SynthEngine but `setSampleEnvelope()`/`setSampleLFO()` are empty stubs
- **M12A: Performance Features** - Pad mute/solo implemented; note repeat, performance effects missing
- **Recording** - Can start recording; `stopAudioRecording()` JNI returns null (metadata lost)
- **MIDI** - Models/parser/validator exist; no device connectivity or real-time I/O

### Not Started
- **M5: Effects Processing** - No delay, reverb, or filter effects
- **M4: Keygroup/Pitched Instruments** - No piano roll, multi-sampling, or pitch-shifting
- **M9-M11: MIDI Tracks, Audio Tracks, Mixer** - Future phase
- **VirtualKnob, VirtualSlider, PianoRoll, XYPad** - UI components not built

---

## TIER 1: BLOCKERS

### 1. Compact UI entirely disabled
- **Files:** 30+ `.bak` files in `features/compactui/`
- **Impact:** The entire designed UX is invisible to users
- `CompactMainScreen.kt.bak`, `ResponsiveMainLayout.kt.bak`, `TransportControlBar.kt.bak`, etc.
- `compact_main` route in `MainActivity.kt:102-110` redirects back to legacy button list

### 2. CompactMainViewModel won't compile
- **File:** `features/compactui/CompactMainViewModel.kt`
- References `drumTrackViewModel`, `sequencerViewModel`, `samplingViewModel`, `midiSettingsViewModel`,
  `midiMappingViewModel`, `midiMonitorViewModel` — none are constructor params or declared fields
- Lines 247-258: duplicate property declarations inside `init` block (syntax error)

### 3. `playPadSample()` is a no-op in C++
- **File:** `AudioEngine.cpp:776-786`
- Logs and returns `true` but never plays audio
- This is the formal API path used by the compact UI

### 4. `stopNote()` / `stopAllNotes()` are empty
- **File:** `AudioEngine.h:102-103`
- Notes triggered via formal API will never stop

### 5. ProjectManagerImpl is 100% stubbed
- **File:** `project/ProjectManagerImpl.kt:15-67`
- Every method returns `Failure` — no project persistence at all

---

## TIER 2: HIGH PRIORITY

### 6. Legacy MainScreen is a plain button list
- **File:** `MainActivity.kt:209-264`
- Just "Welcome to The One!" with a column of buttons — no MPC feel

### 7. Navigation dead ends
- `sequencer_settings` → placeholder text
- `project_settings` → "Coming Soon"
- `compact_main` → redirect loop

### 8. C++ Envelope/LFO stubs
- **Files:** `AudioEngine.cpp:384-386, 523-525`
- `setSampleEnvelope()` and `setSampleLFO()` are empty bodies

### 9. `stopAudioRecording()` JNI returns null
- **File:** `native-lib.cpp:581-588`
- Recording stops but metadata isn't returned to Kotlin layer

### 10. Multiple JNI conversion stubs
- **File:** `native-lib.cpp:600-665`
- `addTrackEffect()` → always returns false
- `setSampleEnvelope()`/`setSampleLFO()` → calls commented out
- `getAudioLevels()` → returns zeros

---

## TIER 3: FEATURE GAPS

### 11. C2: MIDI Engine
- Models and parsing exist but no device discovery or real-time I/O

### 12. M5: Effects Processing
- No framework — no delay, reverb, or filter effects in C++

### 13. Missing UI Components
- VirtualKnob, VirtualSlider, PianoRoll, XYPad

### 14. M4: Keygroup/Pitched Instruments
- Not started

---

## TIER 4: POLISH

### 15. Testing gaps
- 1 skeleton instrumented test; compact UI tests reference `.bak` files
- No Compose UI tests running

### 16. Build config
- Release minification disabled
- No ProGuard rules

### 17. Minor stubs
- CPU/memory monitoring returns zeros
- Plugin factory hardcoded to SketchingSynth
- Transport state not connected to plugin processing

---

## Recommended Fix Order (to be UX-ready)

| Priority | Fix | Rationale |
|----------|-----|-----------|
| **P0** | Fix `CompactMainViewModel.kt` — remove duplicate properties, resolve ViewModel references | Nothing compiles without this |
| **P1** | Restore compact UI `.bak` files → `.kt`, fix imports/compilation | This IS the UX |
| **P2** | Re-enable `compact_main` route in `MainActivity.kt` as start destination | Users need to reach the real UI |
| **P3** | Implement `playPadSample()` in C++ (wire to existing triggerSample logic) | Core feature must produce sound |
| **P4** | Implement `stopNote()` / `stopAllNotes()` in C++ | Prevent stuck notes |
| **P5** | Fix `stopAudioRecording()` JNI metadata return | Recording workflow is broken end-to-end |
| **P6** | Basic `ProjectManagerImpl` — at minimum in-memory project with sample pool | Session persistence needed |
| **P7** | Fix navigation dead ends (settings, project settings) | UX completeness |

**P0-P2** = Get the real UI visible
**P3-P5** = Make audio work end-to-end
**P6-P7** = Complete the workflow

---

## What's Working Well

- Audio engine initialization and Oboe stream management
- Legacy drum pad triggering (`triggerDrumPad()` / `native_triggerDrumPad()`)
- Sample loading from assets and files
- Step sequencer state management (Kotlin side)
- AVST plugin system and SketchingSynth
- MIDI data models and parsing
- Material Design 3 theming
- Hilt DI wiring
- Unit test framework

The foundation is genuinely solid. The main blocker is that the **compact UI was disabled**
(likely due to ViewModel dependency issues), leaving users with just the basic button menu.
Fixing the ViewModel and restoring the `.bak` files is the fastest path to a real UX.
