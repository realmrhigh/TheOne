package com.high.theone.features.sequencer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.high.theone.model.Pattern

/**
 * Groove template selector dialog for choosing and applying groove templates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrooveTemplateSelector(
    currentPattern: Pattern?,
    onTemplateApply: (GrooveTemplate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("Swing") }
    var showCustomGrooveCreator by remember { mutableStateOf(false) }
    var showGrooveAnalysis by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Groove Templates",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row {
                        IconButton(
                            onClick = { showGrooveAnalysis = true },
                            enabled = currentPattern != null
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = "Analyze Groove")
                        }
                        
                        IconButton(onClick = { showCustomGrooveCreator = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Create Custom Groove")
                        }
                        
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Category tabs
                CategoryTabs(
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Template list
                GrooveTemplateList(
                    category = selectedCategory,
                    onTemplateSelect = { template ->
                        onTemplateApply(template)
                        onDismiss()
                    }
                )
            }
        }
    }
    
    // Custom groove creator dialog
    if (showCustomGrooveCreator) {
        CustomGrooveCreator(
            onGrooveCreate = { template ->
                onTemplateApply(template)
                showCustomGrooveCreator = false
                onDismiss()
            },
            onDismiss = { showCustomGrooveCreator = false }
        )
    }
    
    // Groove analysis dialog
    if (showGrooveAnalysis && currentPattern != null) {
        GrooveAnalysisDialog(
            pattern = currentPattern,
            onTemplateApply = { template ->
                onTemplateApply(template)
                showGrooveAnalysis = false
                onDismiss()
            },
            onDismiss = { showGrooveAnalysis = false }
        )
    }
}

@Composable
private fun CategoryTabs(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    val categories = listOf("Swing", "MPC", "Genre", "Human")
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                onClick = { onCategorySelect(category) },
                label = { Text(category) },
                selected = selectedCategory == category
            )
        }
    }
}

@Composable
private fun GrooveTemplateList(
    category: String,
    onTemplateSelect: (GrooveTemplate) -> Unit
) {
    val templates = when (category) {
        "Swing" -> GroovePresets.getSwingPresets()
        "MPC" -> GroovePresets.getMpcPresets()
        "Genre" -> GroovePresets.getGenrePresets()
        "Human" -> GroovePresets.getHumanizationPresets()
        else -> GroovePresets.ALL_PRESETS
    }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(templates) { template ->
            GrooveTemplateCard(
                template = template,
                onSelect = { onTemplateSelect(template) }
            )
        }
    }
}

@Composable
private fun GrooveTemplateCard(
    template: GrooveTemplate,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${(template.swingAmount * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Visual representation of swing
            if (template.swingAmount > 0f) {
                SwingVisualization(
                    swingAmount = template.swingAmount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SwingVisualization(
    swingAmount: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(8) { index ->
            val isOffBeat = index % 2 == 1
            val height = if (isOffBeat) {
                0.6f + (swingAmount * 0.4f) // Off-beats get taller with more swing
            } else {
                0.6f
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .background(
                        color = if (isOffBeat) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun CustomGrooveCreator(
    onGrooveCreate: (GrooveTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var swingType by remember { mutableStateOf(SwingType.LINEAR) }
    var swingAmount by remember { mutableStateOf(0f) }
    var humanization by remember { mutableStateOf(0f) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create Custom Groove",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Groove Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Swing type selection
                Text(
                    text = "Swing Type",
                    style = MaterialTheme.typography.titleMedium
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SwingType.values()) { type ->
                        FilterChip(
                            onClick = { swingType = type },
                            label = { Text(type.displayName) },
                            selected = swingType == type
                        )
                    }
                }
                
                // Swing amount
                Text(
                    text = "Swing Amount: ${(swingAmount * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Slider(
                    value = swingAmount,
                    onValueChange = { swingAmount = it },
                    valueRange = 0f..0.75f
                )
                
                // Humanization
                Text(
                    text = "Humanization: ${(humanization * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Slider(
                    value = humanization,
                    onValueChange = { humanization = it },
                    valueRange = 0f..1f
                )
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val template = GrooveTemplates().createCustomGroove(
                                    name = name,
                                    swingType = swingType,
                                    swingAmount = swingAmount,
                                    humanization = humanization
                                )
                                onGrooveCreate(template)
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun GrooveAnalysisDialog(
    pattern: Pattern,
    onTemplateApply: (GrooveTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val analysis = remember { pattern.analyzeGroove() }
    val suggestedTemplate = remember { analysis.getSuggestedTemplate() }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Groove Analysis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Analysis results
                AnalysisResultCard("Swing Amount", "${(analysis.swingAmount * 100).toInt()}%")
                AnalysisResultCard("Humanization", "${(analysis.humanizationLevel * 100).toInt()}%")
                AnalysisResultCard("Timing Range", "${String.format("%.1f", analysis.timingRange)}ms")
                AnalysisResultCard("Confidence", "${(analysis.confidence * 100).toInt()}%")
                
                // Suggested template
                if (suggestedTemplate != null) {
                    Divider()
                    
                    Text(
                        text = "Suggested Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    GrooveTemplateCard(
                        template = suggestedTemplate,
                        onSelect = { onTemplateApply(suggestedTemplate) }
                    )
                }
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    
                    if (suggestedTemplate != null) {
                        Button(
                            onClick = { onTemplateApply(suggestedTemplate) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Suggested")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisResultCard(
    label: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}