package com.high.theone.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import androidx.core.net.toUri
import com.high.theone.model.SampleMetadata
import com.high.theone.model.AudioInputSource
import com.high.theone.features.drumtrack.model.PadSettings
import com.high.theone.model.SynthModels.LFOSettings
import com.high.theone.model.SynthModels.EnvelopeSettings
import com.high.theone.model.PlaybackMode
import java.io.File

// ...existing code...
