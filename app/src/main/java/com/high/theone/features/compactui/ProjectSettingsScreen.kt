package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.high.theone.features.compactui.PreferenceManager
import com.high.theone.model.TransportState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Project Settings screen for configuring global project parameters
 * Currently includes BPM/tempo settings, with room for expansion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val viewModel: ProjectSettingsViewModel = hiltViewModel()
    val bpm by viewModel.bpm.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BPM Settings Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Tempo Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // BPM Display and Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Decrease BPM button
                        IconButton(
                            onClick = { viewModel.onBpmChange((bpm - 1).coerceAtLeast(60)) }
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease BPM")
                        }

                        // BPM display
                        Card(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = "${bpm} BPM",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Increase BPM button
                        IconButton(
                            onClick = { viewModel.onBpmChange((bpm + 1).coerceAtMost(200)) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase BPM")
                        }
                    }

                    // BPM Range info
                    Text(
                        text = "Range: 60 - 200 BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Future settings can be added here
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "More Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Additional project settings will be added here in future updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * ViewModel for Project Settings screen
 */
@HiltViewModel
class ProjectSettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : androidx.lifecycle.ViewModel() {

    // Get BPM from preferences
    val bpm: StateFlow<Int> = preferenceManager.bpm
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 120
        )

    /**
     * Update BPM setting
     */
    fun onBpmChange(newBpm: Int) {
        viewModelScope.launch {
            preferenceManager.saveBpm(newBpm)
        }
    }
}