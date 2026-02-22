package com.high.theone.features.compactui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.high.theone.model.PanelState
import com.high.theone.model.PanelType

/**
 * Integration component that demonstrates the complete quick access panel system
 * This shows how to integrate the panels with the main UI
 */
@Composable
fun QuickAccessPanelIntegration(
    modifier: Modifier = Modifier
) {
    var panelState by remember { 
        mutableStateOf(
            PanelState(
                isVisible = false,
                isExpanded = false,
                contentType = PanelType.SAMPLING
            )
        )
    }
    var currentPanelType by remember { mutableStateOf(PanelType.SAMPLING) }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main content area
        MainContentWithPanelTrigger(
            onShowPanel = { type ->
                panelState = panelState.copy(
                    isVisible = true,
                    contentType = type
                )
                currentPanelType = type
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Quick access panel overlay
        QuickAccessPanel(
            panelState = panelState,
            panelType = currentPanelType,
            onPanelStateChange = { newState ->
                panelState = newState
            },
            onPanelTypeChange = { newType ->
                currentPanelType = newType
                panelState = panelState.copy(contentType = newType)
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) { type ->
            QuickAccessPanelContent(
                panelType = type,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Panel trigger button (for demonstration)
        FloatingActionButton(
            onClick = {
                panelState = panelState.copy(isVisible = !panelState.isVisible)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Toggle Quick Access Panel"
            )
        }
    }
}

/**
 * Main content area with panel trigger functionality
 */
@Composable
private fun MainContentWithPanelTrigger(
    onShowPanel: (PanelType) -> Unit,
    modifier: Modifier = Modifier
) {
    QuickAccessPanelGestureHandler(
        onShowPanel = onShowPanel,
        modifier = modifier
    ) {
        // This would be your main UI content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Main UI Content",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Demo buttons to show different panels
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onShowPanel(PanelType.SAMPLING) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show Sampling Panel")
                }
                
                Button(
                    onClick = { onShowPanel(PanelType.MIDI) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show MIDI Panel")
                }
                
                Button(
                    onClick = { onShowPanel(PanelType.MIXER) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show Mixer Panel")
                }
                
                Button(
                    onClick = { onShowPanel(PanelType.SETTINGS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show Settings Panel")
                }
            }
        }
    }
}

/**
 * State management for quick access panels
 */
@Composable
fun rememberQuickAccessPanelState(
    initialType: PanelType = PanelType.SAMPLING,
    initiallyVisible: Boolean = false
): QuickAccessPanelStateHolder {
    return remember {
        QuickAccessPanelStateHolder(
            initialType = initialType,
            initiallyVisible = initiallyVisible
        )
    }
}

/**
 * State holder for quick access panel management
 */
class QuickAccessPanelStateHolder(
    initialType: PanelType,
    initiallyVisible: Boolean
) {
    var panelState by mutableStateOf(
        PanelState(
            isVisible = initiallyVisible,
            isExpanded = false,
            contentType = initialType
        )
    )
        private set
    
    var currentType by mutableStateOf(initialType)
        private set
    
    fun showPanel(type: PanelType) {
        currentType = type
        panelState = panelState.copy(
            isVisible = true,
            contentType = type
        )
    }
    
    fun hidePanel() {
        panelState = panelState.copy(isVisible = false)
    }
    
    fun togglePanel() {
        panelState = panelState.copy(isVisible = !panelState.isVisible)
    }
    
    fun expandPanel() {
        panelState = panelState.copy(isExpanded = true)
    }
    
    fun collapsePanel() {
        panelState = panelState.copy(isExpanded = false)
    }
    
    fun toggleExpansion() {
        panelState = panelState.copy(isExpanded = !panelState.isExpanded)
    }
    
    fun updatePanelState(newState: PanelState) {
        panelState = newState
        newState.contentType?.let { currentType = it }
    }
    
    fun switchPanelType(type: PanelType) {
        currentType = type
        panelState = panelState.copy(contentType = type)
    }
}

/**
 * Utility composable for integrating quick access panels into existing screens
 */
@Composable
fun WithQuickAccessPanel(
    panelStateHolder: QuickAccessPanelStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (onShowPanel: (PanelType) -> Unit) -> Unit
) {
    Box(modifier = modifier) {
        // Main content
        content { type ->
            panelStateHolder.showPanel(type)
        }
        
        // Panel overlay
        QuickAccessPanel(
            panelState = panelStateHolder.panelState,
            panelType = panelStateHolder.currentType,
            onPanelStateChange = panelStateHolder::updatePanelState,
            onPanelTypeChange = panelStateHolder::switchPanelType,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) { type ->
            QuickAccessPanelContent(
                panelType = type,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}