# Requirements Document

## Introduction

This specification defines the requirements for implementing basic sampling and pad playback functionality in TheOne MPC app. This feature represents the core functionality that allows users to record audio samples from the microphone and trigger them via virtual drum pads, forming the foundation of the MPC-style workflow. This module builds upon the existing audio engine (C1), file management system (C3), and UI framework (C4) to deliver the essential sampling experience.

## Requirements

### Requirement 1

**User Story:** As a beat maker, I want to record audio samples from my device's microphone, so that I can capture sounds from my environment to use in my beats.

#### Acceptance Criteria

1. WHEN the user taps the record button THEN the system SHALL start recording audio from the default microphone
2. WHEN recording is active THEN the system SHALL display a visual indicator showing recording status and duration
3. WHEN the user taps stop recording THEN the system SHALL stop recording and save the audio sample to the project
4. WHEN recording exceeds 30 seconds THEN the system SHALL automatically stop recording and notify the user
5. IF the microphone is not available THEN the system SHALL display an error message and disable recording functionality
6. WHEN a sample is successfully recorded THEN the system SHALL generate metadata including duration, sample rate, and file size

### Requirement 2

**User Story:** As a producer, I want to assign recorded samples to virtual drum pads, so that I can organize my sounds for easy triggering during beat creation.

#### Acceptance Criteria

1. WHEN the user selects a pad and chooses "assign sample" THEN the system SHALL display a list of available samples in the project
2. WHEN the user selects a sample from the list THEN the system SHALL assign that sample to the selected pad
3. WHEN a sample is assigned to a pad THEN the pad SHALL display visual feedback indicating it contains a sample
4. WHEN the user long-presses a pad THEN the system SHALL show pad settings including sample assignment options
5. IF a pad already has a sample assigned THEN the system SHALL allow the user to replace or remove the existing sample
6. WHEN a sample is assigned THEN the system SHALL store the pad-to-sample mapping in the project data

### Requirement 3

**User Story:** As a musician, I want to trigger samples by tapping drum pads, so that I can play beats and create rhythms in real-time.

#### Acceptance Criteria

1. WHEN the user taps a pad with an assigned sample THEN the system SHALL immediately play the sample through the audio engine
2. WHEN multiple pads are tapped simultaneously THEN the system SHALL play all corresponding samples concurrently without audio dropouts
3. WHEN a pad is tapped with different pressure/velocity THEN the system SHALL adjust the sample playback volume accordingly
4. WHEN a sample is already playing and the same pad is tapped THEN the system SHALL restart the sample from the beginning
5. IF the audio engine is not initialized THEN tapping pads SHALL display an error message
6. WHEN samples are triggered THEN the system SHALL maintain audio latency below 50ms for responsive performance

### Requirement 4

**User Story:** As a beat maker, I want to control how samples play back (one-shot vs loop), so that I can create different types of rhythmic elements.

#### Acceptance Criteria

1. WHEN the user accesses pad settings THEN the system SHALL provide options for "One-Shot" and "Loop" playback modes
2. WHEN a pad is set to "One-Shot" mode THEN the sample SHALL play once from start to finish when triggered
3. WHEN a pad is set to "Loop" mode THEN the sample SHALL continuously repeat until stopped or another pad is triggered
4. WHEN a looping sample is playing and the same pad is tapped THEN the system SHALL stop the loop
5. WHEN the user changes playback mode THEN the system SHALL immediately apply the new setting without requiring restart
6. WHEN playback mode is changed THEN the system SHALL save the setting to the project data

### Requirement 5

**User Story:** As a producer, I want to adjust the volume and pan of individual pads, so that I can balance my sounds in the mix.

#### Acceptance Criteria

1. WHEN the user accesses pad settings THEN the system SHALL provide volume and pan controls for each pad
2. WHEN the volume slider is adjusted THEN the system SHALL immediately apply the volume change to sample playback
3. WHEN the pan control is adjusted THEN the system SHALL adjust the stereo positioning of the sample
4. WHEN volume is set to 0% THEN the pad SHALL be effectively muted but still show visual feedback when tapped
5. WHEN pan is set to center THEN the sample SHALL play equally in both left and right channels
6. WHEN pad settings are modified THEN the system SHALL save all changes to the project data automatically

### Requirement 6

**User Story:** As a user, I want to see visual feedback when pads are triggered, so that I can understand which sounds are playing and when.

#### Acceptance Criteria

1. WHEN a pad is tapped THEN the pad SHALL display a brief visual highlight or animation
2. WHEN a sample is playing THEN the pad SHALL show a visual indicator of playback status
3. WHEN a pad has no sample assigned THEN the pad SHALL appear visually distinct from pads with samples
4. WHEN multiple pads are playing simultaneously THEN each active pad SHALL show its individual playback status
5. WHEN a looping sample is active THEN the pad SHALL show a continuous visual indicator until stopped
6. WHEN the audio engine is not available THEN all pads SHALL appear disabled with appropriate visual styling

### Requirement 7

**User Story:** As a beat maker, I want to load existing audio files as samples, so that I can use pre-recorded sounds in addition to recording new ones.

#### Acceptance Criteria

1. WHEN the user selects "Load Sample" THEN the system SHALL open a file browser showing supported audio formats (WAV, MP3, FLAC)
2. WHEN the user selects an audio file THEN the system SHALL load the file and add it to the project sample pool
3. WHEN loading a large audio file THEN the system SHALL show a progress indicator during the loading process
4. IF an unsupported file format is selected THEN the system SHALL display an error message with supported formats
5. WHEN a file fails to load THEN the system SHALL display a specific error message explaining the issue
6. WHEN a sample is successfully loaded THEN the system SHALL make it available for assignment to pads

### Requirement 8

**User Story:** As a producer, I want to trim samples to remove unwanted portions, so that I can use only the best parts of my recordings.

#### Acceptance Criteria

1. WHEN the user selects "Edit Sample" from pad settings THEN the system SHALL open a basic sample editor
2. WHEN in the sample editor THEN the system SHALL display a waveform visualization of the sample
3. WHEN the user drags trim handles THEN the system SHALL update the waveform display to show the selected region
4. WHEN the user taps "Preview" THEN the system SHALL play only the trimmed portion of the sample
5. WHEN the user confirms the trim operation THEN the system SHALL apply the changes and update the pad assignment
6. WHEN trim changes are applied THEN the system SHALL maintain the original sample file and store trim settings in project data