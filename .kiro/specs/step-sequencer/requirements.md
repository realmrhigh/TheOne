# Requirements Document

## Introduction

The Step Sequencer feature transforms TheOne from a simple sample trigger into a complete beat-making machine. This feature enables users to create rhythmic patterns by programming when samples should play over time, following the classic MPC-style workflow that has defined hip-hop and electronic music production for decades.

The step sequencer integrates seamlessly with the existing sampling system, allowing users to record samples, assign them to pads, and then create complex musical patterns using those samples. This completes the core MPC experience and enables users to create full beats and musical compositions within the app.

## Requirements

### Requirement 1: Pattern Programming Interface

**User Story:** As a beat maker, I want to program drum patterns using a step-based interface, so that I can create rhythmic sequences with precise timing control.

#### Acceptance Criteria

1. WHEN the user opens the sequencer THEN the system SHALL display a 16-step grid interface
2. WHEN the user taps a step for a specific pad THEN the system SHALL toggle that step on/off for that pad
3. WHEN a step is active THEN the system SHALL provide visual indication of the step state
4. WHEN the user selects different pads THEN the system SHALL show the pattern for that specific pad
5. IF the user wants to program longer patterns THEN the system SHALL support pattern lengths of 8, 16, 24, and 32 steps
6. WHEN the user programs steps THEN the system SHALL provide immediate visual feedback for step activation

### Requirement 2: Pattern Playback and Transport Controls

**User Story:** As a music producer, I want to play, pause, and control pattern playback, so that I can hear my programmed beats and make adjustments in real-time.

#### Acceptance Criteria

1. WHEN the user presses play THEN the system SHALL start pattern playback from the current position
2. WHEN the user presses stop THEN the system SHALL stop playback and return to the beginning
3. WHEN the user presses pause THEN the system SHALL pause playback at the current position
4. WHEN a pattern is playing THEN the system SHALL show a visual playback indicator moving through the steps
5. WHEN the pattern reaches the end THEN the system SHALL loop back to the beginning automatically
6. WHEN the user adjusts tempo THEN the system SHALL change playback speed in real-time without stopping
7. IF the user wants precise control THEN the system SHALL support tempo range from 60 to 200 BPM

### Requirement 3: Multiple Pattern Management

**User Story:** As a songwriter, I want to create and manage multiple patterns, so that I can build complete song structures with verses, choruses, and breaks.

#### Acceptance Criteria

1. WHEN the user creates a new pattern THEN the system SHALL provide a new empty 16-step pattern
2. WHEN the user switches between patterns THEN the system SHALL save the current pattern state automatically
3. WHEN the user names a pattern THEN the system SHALL store the custom name with the pattern
4. WHEN the user deletes a pattern THEN the system SHALL confirm the action before permanent deletion
5. IF the user wants to reuse patterns THEN the system SHALL support copying and duplicating patterns
6. WHEN the user has multiple patterns THEN the system SHALL support at least 16 different patterns per project
7. WHEN patterns are created THEN the system SHALL provide clear visual distinction between different patterns

### Requirement 4: Real-time Pattern Recording

**User Story:** As a drummer, I want to record patterns by playing pads in real-time, so that I can capture natural timing and groove that feels more human than step programming.

#### Acceptance Criteria

1. WHEN the user enables record mode THEN the system SHALL capture pad hits in real-time during playback
2. WHEN the user hits pads during recording THEN the system SHALL quantize hits to the nearest step automatically
3. WHEN recording is active THEN the system SHALL provide visual indication of record mode
4. WHEN the user wants to overdub THEN the system SHALL allow adding to existing patterns without erasing previous steps
5. IF the user makes mistakes THEN the system SHALL support undo/redo for recorded patterns
6. WHEN recording multiple passes THEN the system SHALL maintain existing steps while adding new ones
7. WHEN the user stops recording THEN the system SHALL automatically exit record mode

### Requirement 5: Swing and Timing Controls

**User Story:** As a hip-hop producer, I want to add swing and adjust timing feel, so that my beats have the right groove and don't sound too mechanical.

#### Acceptance Criteria

1. WHEN the user adjusts swing THEN the system SHALL delay every second step by the specified amount
2. WHEN swing is applied THEN the system SHALL maintain the overall tempo while adjusting step timing
3. WHEN the user wants subtle groove THEN the system SHALL support swing values from 0% to 75%
4. WHEN timing is adjusted THEN the system SHALL apply changes to all active patterns consistently
5. IF the user wants different feels THEN the system SHALL provide preset swing amounts (none, light, medium, heavy)
6. WHEN swing changes are made THEN the system SHALL update playback timing immediately
7. WHEN patterns are saved THEN the system SHALL store swing settings with each pattern

### Requirement 6: Step Velocity and Accent Programming

**User Story:** As a beat maker, I want to program different velocities for individual steps, so that I can create dynamic patterns with accents and ghost notes.

#### Acceptance Criteria

1. WHEN the user selects a step THEN the system SHALL allow editing the velocity for that specific step
2. WHEN velocity is adjusted THEN the system SHALL provide visual indication of velocity levels
3. WHEN steps play back THEN the system SHALL trigger samples with the programmed velocity
4. WHEN the user wants quick accents THEN the system SHALL support accent mode for emphasizing specific steps
5. IF the user wants subtle dynamics THEN the system SHALL support velocity values from 1 to 127
6. WHEN velocity is programmed THEN the system SHALL show velocity levels in the step grid interface
7. WHEN patterns are complex THEN the system SHALL allow copying velocity settings between steps

### Requirement 7: Pattern Chain and Song Mode

**User Story:** As a composer, I want to chain patterns together in sequence, so that I can create complete song arrangements with different sections.

#### Acceptance Criteria

1. WHEN the user creates a song arrangement THEN the system SHALL allow sequencing multiple patterns in order
2. WHEN patterns are chained THEN the system SHALL transition smoothly between different patterns
3. WHEN the user programs a song THEN the system SHALL support repeating patterns multiple times
4. WHEN song mode is active THEN the system SHALL show the current pattern and next pattern in the sequence
5. IF the user wants complex arrangements THEN the system SHALL support at least 64 pattern slots in a song
6. WHEN songs are played THEN the system SHALL follow the programmed pattern sequence automatically
7. WHEN the user edits arrangements THEN the system SHALL allow inserting, deleting, and reordering pattern sequences

### Requirement 8: Integration with Existing Pad System

**User Story:** As a user of the sampling system, I want the sequencer to work seamlessly with my existing pads and samples, so that I can use all my recorded and assigned samples in patterns.

#### Acceptance Criteria

1. WHEN the sequencer is opened THEN the system SHALL show all currently assigned pad samples
2. WHEN pad assignments change THEN the system SHALL update the sequencer interface automatically
3. WHEN patterns are programmed THEN the system SHALL use the current pad settings (volume, pan, effects)
4. WHEN samples are triggered by the sequencer THEN the system SHALL respect individual pad configurations
5. IF pads are empty THEN the system SHALL indicate which sequencer tracks have no assigned samples
6. WHEN the user switches between live pad mode and sequencer mode THEN the system SHALL maintain all pad states
7. WHEN patterns play THEN the system SHALL trigger the same audio engine functions as manual pad hits

### Requirement 9: Visual Feedback and User Interface

**User Story:** As a mobile user, I want clear visual feedback and an intuitive touch interface, so that I can easily see what's programmed and make quick edits on a small screen.

#### Acceptance Criteria

1. WHEN steps are active THEN the system SHALL use distinct colors and visual states for programmed steps
2. WHEN the sequencer is playing THEN the system SHALL show a clear playback position indicator
3. WHEN the user interacts with the interface THEN the system SHALL provide immediate visual feedback for all actions
4. WHEN patterns are complex THEN the system SHALL use visual hierarchy to show different velocity levels
5. IF the screen is small THEN the system SHALL optimize the interface for mobile touch interaction
6. WHEN multiple patterns exist THEN the system SHALL provide clear navigation between different patterns
7. WHEN the user needs information THEN the system SHALL show current tempo, pattern length, and swing settings prominently

### Requirement 10: Performance and Timing Accuracy

**User Story:** As a professional producer, I want rock-solid timing and low-latency playback, so that my beats stay tight and in sync for professional-quality results.

#### Acceptance Criteria

1. WHEN patterns play THEN the system SHALL maintain accurate timing with less than 10ms jitter
2. WHEN tempo changes THEN the system SHALL adjust timing smoothly without audio dropouts
3. WHEN multiple samples trigger simultaneously THEN the system SHALL handle polyphonic playback without performance degradation
4. WHEN the system is under load THEN the system SHALL prioritize audio timing over UI updates
5. IF the device has limited resources THEN the system SHALL gracefully reduce visual effects to maintain audio performance
6. WHEN patterns are complex THEN the system SHALL pre-load and cache samples for immediate triggering
7. WHEN the user interacts during playback THEN the system SHALL maintain timing accuracy while processing UI events