package com.high.theone.ui.layout

import androidx.compose.ui.unit.dp
import com.high.theone.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ResponsiveLayoutUtils
 */
class ResponsiveLayoutUtilsTest {
    
    @Test
    fun `calculateDrumPadGridDimensions returns correct dimensions for compact portrait`() {
        val dimensions = ResponsiveLayoutUtils.calculateDrumPadGridDimensions(
            layoutMode = LayoutMode.COMPACT_PORTRAIT,
            availableWidth = 360.dp,
            availableHeight = 640.dp
        )
        
        assertEquals(4, dimensions.columns)
        assertEquals(4, dimensions.rows)
        assertEquals(4.dp, dimensions.spacing)
        assertTrue("Pad size should be reasonable for compact portrait", 
            dimensions.padSize.value in 50f..80f)
    }
    
    @Test
    fun `calculateDrumPadGridDimensions returns correct dimensions for tablet`() {
        val dimensions = ResponsiveLayoutUtils.calculateDrumPadGridDimensions(
            layoutMode = LayoutMode.TABLET,
            availableWidth = 800.dp,
            availableHeight = 1200.dp
        )
        
        assertEquals(4, dimensions.columns)
        assertEquals(4, dimensions.rows)
        assertEquals(12.dp, dimensions.spacing)
        assertTrue("Pad size should be larger for tablet", 
            dimensions.padSize.value >= 80f)
    }
    
    @Test
    fun `calculateSequencerDimensions shows more steps in landscape`() {
        val portraitDimensions = ResponsiveLayoutUtils.calculateSequencerDimensions(
            layoutMode = LayoutMode.STANDARD_PORTRAIT,
            availableWidth = 360.dp
        )
        
        val landscapeDimensions = ResponsiveLayoutUtils.calculateSequencerDimensions(
            layoutMode = LayoutMode.LANDSCAPE,
            availableWidth = 640.dp
        )
        
        assertEquals(16, portraitDimensions.visibleSteps)
        assertEquals(32, landscapeDimensions.visibleSteps)
        assertTrue("Landscape should show more steps", 
            landscapeDimensions.visibleSteps > portraitDimensions.visibleSteps)
    }
    
    @Test
    fun `calculateTransportBarDimensions adjusts for layout mode`() {
        val compactDimensions = ResponsiveLayoutUtils.calculateTransportBarDimensions(
            LayoutMode.COMPACT_PORTRAIT
        )
        
        val tabletDimensions = ResponsiveLayoutUtils.calculateTransportBarDimensions(
            LayoutMode.TABLET
        )
        
        assertFalse("Compact mode should not show extended controls", 
            compactDimensions.showExtendedControls)
        assertTrue("Tablet mode should show extended controls", 
            tabletDimensions.showExtendedControls)
        assertTrue("Tablet should have larger buttons", 
            tabletDimensions.buttonSize > compactDimensions.buttonSize)
    }
    
    @Test
    fun `calculatePanelDimensions uses bottom sheet for portrait modes`() {
        val portraitDimensions = ResponsiveLayoutUtils.calculatePanelDimensions(
            layoutMode = LayoutMode.STANDARD_PORTRAIT,
            panelType = PanelType.SAMPLING,
            screenWidth = 360.dp,
            screenHeight = 640.dp
        )
        
        val landscapeDimensions = ResponsiveLayoutUtils.calculatePanelDimensions(
            layoutMode = LayoutMode.LANDSCAPE,
            panelType = PanelType.SAMPLING,
            screenWidth = 640.dp,
            screenHeight = 360.dp
        )
        
        assertTrue("Portrait should use bottom sheet", portraitDimensions.isBottomSheet)
        assertFalse("Landscape should use side panel", landscapeDimensions.isBottomSheet)
    }
}