package com.high.theone.features.compactui.accessibility

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Accessibility testing utilities for validating compact UI components.
 * Provides validation helpers, color contrast checking, and keyboard navigation support.
 * 
 * Requirements: 9.1 (accessibility validation), 9.5 (keyboard navigation)
 */

/**
 * Accessibility validation result
 */
sealed class AccessibilityValidationResult {
    object Valid : AccessibilityValidationResult()
    data class Invalid(val issues: List<AccessibilityIssue>) : AccessibilityValidationResult()
}

/**
 * Accessibility issue types
 */
data class AccessibilityIssue(
    val type: AccessibilityIssueType,
    val description: String,
    val severity: AccessibilityIssueSeverity,
    val suggestion: String? = null
)

enum class AccessibilityIssueType {
    TOUCH_TARGET_TOO_SMALL,
    INSUFFICIENT_CONTRAST,
    MISSING_CONTENT_DESCRIPTION,
    MISSING_SEMANTIC_ROLE,
    MISSING_STATE_DESCRIPTION,
    KEYBOARD_NAVIGATION_ISSUE,
    FOCUS_ORDER_ISSUE,
    MISSING_CUSTOM_ACTIONS
}

enum class AccessibilityIssueSeverity {
    CRITICAL,    // WCAG AA violations
    HIGH,        // WCAG A violations
    MEDIUM,      // Best practice violations
    LOW          // Enhancement suggestions
}

/**
 * Comprehensive accessibility validator for UI components
 */
class AccessibilityValidator {
    
    /**
     * Validate touch target sizes
     */
    fun validateTouchTargets(
        nodes: List<SemanticsNode>,
        minimumSize: Dp = MinimumTouchTargetSize
    ): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        
        nodes.forEach { node ->
            val config = node.config
            if (config.contains(SemanticsActions.OnClick) || 
                config.contains(SemanticsActions.OnLongClick)) {
                
                val bounds = node.boundsInRoot
                val width = bounds.width
                val height = bounds.height
                val minSizePx = minimumSize.value * 160 / 160 // Convert to px (simplified)
                
                if (width < minSizePx || height < minSizePx) {
                    issues.add(
                        AccessibilityIssue(
                            type = AccessibilityIssueType.TOUCH_TARGET_TOO_SMALL,
                            description = "Touch target is ${width.toInt()}x${height.toInt()}px, " +
                                "minimum required is ${minSizePx.toInt()}x${minSizePx.toInt()}px",
                            severity = AccessibilityIssueSeverity.CRITICAL,
                            suggestion = "Increase touch target size to at least $minimumSize or add padding"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Validate content descriptions
     */
    fun validateContentDescriptions(nodes: List<SemanticsNode>): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        
        nodes.forEach { node ->
            val config = node.config
            val isInteractive = config.contains(SemanticsActions.OnClick) ||
                config.contains(SemanticsActions.OnLongClick) ||
                config.contains(SemanticsProperties.Role)
            
            if (isInteractive && !config.contains(SemanticsProperties.ContentDescription)) {
                issues.add(
                    AccessibilityIssue(
                        type = AccessibilityIssueType.MISSING_CONTENT_DESCRIPTION,
                        description = "Interactive element missing content description",
                        severity = AccessibilityIssueSeverity.HIGH,
                        suggestion = "Add contentDescription to describe the element's purpose"
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Validate semantic roles
     */
    fun validateSemanticRoles(nodes: List<SemanticsNode>): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        
        nodes.forEach { node ->
            val config = node.config
            val hasClickAction = config.contains(SemanticsActions.OnClick)
            val hasRole = config.contains(SemanticsProperties.Role)
            
            if (hasClickAction && !hasRole) {
                issues.add(
                    AccessibilityIssue(
                        type = AccessibilityIssueType.MISSING_SEMANTIC_ROLE,
                        description = "Clickable element missing semantic role",
                        severity = AccessibilityIssueSeverity.MEDIUM,
                        suggestion = "Add appropriate Role (Button, Switch, etc.)"
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Validate state descriptions for stateful components
     */
    fun validateStateDescriptions(nodes: List<SemanticsNode>): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        
        nodes.forEach { node ->
            val config = node.config
            val hasToggleableState = config.contains(SemanticsProperties.ToggleableState)
            val hasStateDescription = config.contains(SemanticsProperties.StateDescription)
            
            if (hasToggleableState && !hasStateDescription) {
                issues.add(
                    AccessibilityIssue(
                        type = AccessibilityIssueType.MISSING_STATE_DESCRIPTION,
                        description = "Toggleable element missing state description",
                        severity = AccessibilityIssueSeverity.MEDIUM,
                        suggestion = "Add stateDescription to announce current state"
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Validate complete accessibility compliance
     */
    fun validateAccessibility(nodes: List<SemanticsNode>): AccessibilityValidationResult {
        val allIssues = mutableListOf<AccessibilityIssue>()
        
        allIssues.addAll(validateTouchTargets(nodes))
        allIssues.addAll(validateContentDescriptions(nodes))
        allIssues.addAll(validateSemanticRoles(nodes))
        allIssues.addAll(validateStateDescriptions(nodes))
        
        return if (allIssues.isEmpty()) {
            AccessibilityValidationResult.Valid
        } else {
            AccessibilityValidationResult.Invalid(allIssues)
        }
    }
}

/**
 * Color contrast checker following WCAG guidelines
 */
class ColorContrastChecker {
    
    companion object {
        const val WCAG_AA_NORMAL_RATIO = 4.5f
        const val WCAG_AA_LARGE_RATIO = 3.0f
        const val WCAG_AAA_NORMAL_RATIO = 7.0f
        const val WCAG_AAA_LARGE_RATIO = 4.5f
    }
    
    /**
     * Calculate contrast ratio between two colors using WCAG formula
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    /**
     * Check if color combination meets WCAG AA standards
     */
    fun meetsWCAG_AA(
        foreground: Color, 
        background: Color, 
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        val requiredRatio = if (isLargeText) WCAG_AA_LARGE_RATIO else WCAG_AA_NORMAL_RATIO
        return ratio >= requiredRatio
    }
    
    /**
     * Check if color combination meets WCAG AAA standards
     */
    fun meetsWCAG_AAA(
        foreground: Color, 
        background: Color, 
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        val requiredRatio = if (isLargeText) WCAG_AAA_LARGE_RATIO else WCAG_AAA_NORMAL_RATIO
        return ratio >= requiredRatio
    }
    
    /**
     * Get contrast validation result with detailed information
     */
    fun validateContrast(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false,
        targetLevel: ContrastLevel = ContrastLevel.AA
    ): ContrastValidationResult {
        val ratio = calculateContrastRatio(foreground, background)
        val meetsAA = meetsWCAG_AA(foreground, background, isLargeText)
        val meetsAAA = meetsWCAG_AAA(foreground, background, isLargeText)
        
        val passes = when (targetLevel) {
            ContrastLevel.AA -> meetsAA
            ContrastLevel.AAA -> meetsAAA
            ContrastLevel.FAIL -> false
        }
        
        return ContrastValidationResult(
            ratio = ratio,
            passes = passes,
            level = when {
                meetsAAA -> ContrastLevel.AAA
                meetsAA -> ContrastLevel.AA
                else -> ContrastLevel.FAIL
            },
            requiredRatio = when (targetLevel) {
                ContrastLevel.AA -> if (isLargeText) WCAG_AA_LARGE_RATIO else WCAG_AA_NORMAL_RATIO
                ContrastLevel.AAA -> if (isLargeText) WCAG_AAA_LARGE_RATIO else WCAG_AAA_NORMAL_RATIO
                ContrastLevel.FAIL -> 0.0f // Not applicable for failed contrast
            }
        )
    }
    
    /**
     * Suggest improved colors to meet contrast requirements
     */
    fun suggestImprovedColors(
        foreground: Color,
        background: Color,
        targetLevel: ContrastLevel = ContrastLevel.AA,
        isLargeText: Boolean = false
    ): ColorSuggestion? {
        val currentRatio = calculateContrastRatio(foreground, background)
        val requiredRatio = when (targetLevel) {
            ContrastLevel.AA -> if (isLargeText) WCAG_AA_LARGE_RATIO else WCAG_AA_NORMAL_RATIO
            ContrastLevel.AAA -> if (isLargeText) WCAG_AAA_LARGE_RATIO else WCAG_AAA_NORMAL_RATIO
            ContrastLevel.FAIL -> 0f
        }
        
        if (currentRatio >= requiredRatio) return null
        
        // Simple suggestion: darken foreground or lighten background
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        
        return if (foregroundLuminance > backgroundLuminance) {
            // Lighten foreground or darken background
            ColorSuggestion(
                improvedForeground = adjustColorLuminance(foreground, 0.2f),
                improvedBackground = adjustColorLuminance(background, -0.2f),
                explanation = "Increase contrast by lightening foreground or darkening background"
            )
        } else {
            // Darken foreground or lighten background
            ColorSuggestion(
                improvedForeground = adjustColorLuminance(foreground, -0.2f),
                improvedBackground = adjustColorLuminance(background, 0.2f),
                explanation = "Increase contrast by darkening foreground or lightening background"
            )
        }
    }
    
    private fun adjustColorLuminance(color: Color, adjustment: Float): Color {
        // Simplified luminance adjustment
        val factor = 1f + adjustment
        return Color(
            red = (color.red * factor).coerceIn(0f, 1f),
            green = (color.green * factor).coerceIn(0f, 1f),
            blue = (color.blue * factor).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
}

/**
 * Keyboard navigation support utilities
 */
class KeyboardNavigationSupport {
    
    /**
     * Validate focus order for keyboard navigation
     */
    fun validateFocusOrder(nodes: List<SemanticsNode>): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        val focusableNodes = nodes.filter { node ->
            node.config.contains(SemanticsProperties.Focused) ||
            node.config.contains(SemanticsActions.OnClick) ||
            node.config.contains(SemanticsActions.RequestFocus)
        }
        
        // Check for logical focus order (simplified)
        focusableNodes.forEachIndexed { index, node ->
            if (index > 0) {
                val previousNode = focusableNodes[index - 1]
                val currentBounds = node.boundsInRoot
                val previousBounds = previousNode.boundsInRoot
                
                // Check if focus order follows visual order (top-to-bottom, left-to-right)
                if (currentBounds.top < previousBounds.bottom && 
                    currentBounds.left < previousBounds.left) {
                    issues.add(
                        AccessibilityIssue(
                            type = AccessibilityIssueType.FOCUS_ORDER_ISSUE,
                            description = "Focus order may not follow visual layout",
                            severity = AccessibilityIssueSeverity.MEDIUM,
                            suggestion = "Ensure focus order follows reading order (top-to-bottom, left-to-right)"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check for keyboard navigation support
     */
    fun validateKeyboardNavigation(nodes: List<SemanticsNode>): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        
        nodes.forEach { node ->
            val config = node.config
            val isInteractive = config.contains(SemanticsActions.OnClick) ||
                config.contains(SemanticsActions.OnLongClick)
            
            if (isInteractive && !config.contains(SemanticsActions.RequestFocus)) {
                issues.add(
                    AccessibilityIssue(
                        type = AccessibilityIssueType.KEYBOARD_NAVIGATION_ISSUE,
                        description = "Interactive element not focusable via keyboard",
                        severity = AccessibilityIssueSeverity.HIGH,
                        suggestion = "Add focusable modifier to enable keyboard navigation"
                    )
                )
            }
        }
        
        return issues
    }
}

/**
 * Data classes for validation results
 */
data class ContrastValidationResult(
    val ratio: Float,
    val passes: Boolean,
    val level: ContrastLevel,
    val requiredRatio: Float
)

data class ColorSuggestion(
    val improvedForeground: Color,
    val improvedBackground: Color,
    val explanation: String
)

enum class ContrastLevel {
    AA, AAA, FAIL
}

/**
 * Utility functions for testing
 */

/**
 * Assert that colors meet contrast requirements
 */
fun assertContrastMeetsWCAG(
    foreground: Color,
    background: Color,
    level: ContrastLevel = ContrastLevel.AA,
    isLargeText: Boolean = false
) {
    val checker = ColorContrastChecker()
    val result = checker.validateContrast(foreground, background, isLargeText, level)
    
    if (!result.passes) {
        throw AssertionError(
            "Color contrast ratio ${result.ratio} does not meet WCAG $level requirements " +
            "(required: ${result.requiredRatio})"
        )
    }
}

/**
 * Comprehensive accessibility test suite
 */
class AccessibilityTestSuite {
    private val validator = AccessibilityValidator()
    private val contrastChecker = ColorContrastChecker()
    private val keyboardSupport = KeyboardNavigationSupport()
    
    fun runBasicAccessibilityTest(
        nodes: List<androidx.compose.ui.semantics.SemanticsNode>
    ): AccessibilityTestReport {
        val validationResult = validator.validateAccessibility(nodes)
        val focusOrderIssues = keyboardSupport.validateFocusOrder(nodes)
        val keyboardNavIssues = keyboardSupport.validateKeyboardNavigation(nodes)
        
        val allIssues = when (validationResult) {
            is AccessibilityValidationResult.Valid -> emptyList()
            is AccessibilityValidationResult.Invalid -> validationResult.issues
        } + focusOrderIssues + keyboardNavIssues
        
        return AccessibilityTestReport(
            totalNodes = nodes.size,
            issues = allIssues,
            criticalIssues = allIssues.count { it.severity == AccessibilityIssueSeverity.CRITICAL },
            highIssues = allIssues.count { it.severity == AccessibilityIssueSeverity.HIGH },
            mediumIssues = allIssues.count { it.severity == AccessibilityIssueSeverity.MEDIUM },
            lowIssues = allIssues.count { it.severity == AccessibilityIssueSeverity.LOW }
        )
    }
}

data class AccessibilityTestReport(
    val totalNodes: Int,
    val issues: List<AccessibilityIssue>,
    val criticalIssues: Int,
    val highIssues: Int,
    val mediumIssues: Int,
    val lowIssues: Int
) {
    val hasIssues: Boolean get() = issues.isNotEmpty()
    val hasCriticalIssues: Boolean get() = criticalIssues > 0
    val passesBasicAccessibility: Boolean get() = criticalIssues == 0 && highIssues == 0
    
    fun generateReport(): String = buildString {
        appendLine("Accessibility Test Report")
        appendLine("========================")
        appendLine("Total nodes tested: $totalNodes")
        appendLine("Total issues found: ${issues.size}")
        appendLine("  Critical: $criticalIssues")
        appendLine("  High: $highIssues") 
        appendLine("  Medium: $mediumIssues")
        appendLine("  Low: $lowIssues")
        appendLine()
        
        if (issues.isNotEmpty()) {
            appendLine("Issues by severity:")
            appendLine("------------------")
            
            issues.groupBy { it.severity }.forEach { (severity, issueList) ->
                appendLine("$severity (${issueList.size}):")
                issueList.forEach { issue ->
                    appendLine("  - ${issue.description}")
                    issue.suggestion?.let { appendLine("    Suggestion: $it") }
                }
                appendLine()
            }
        } else {
            appendLine("✅ All accessibility tests passed!")
        }
    }
}