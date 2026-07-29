package cn.guoyujie666.music.compose.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3E Enhanced Shape System for LX Music App.
 * 
 * This provides consistent corner sizes throughout the app using Material Design 3
 * principles with some expressive enhancements.
 * 
 * All rounded corners should use these predefined shapes instead of hardcoded values.
 */
object LxMusicShapes {
    // Standard M3 corner sizes
    val extraSmall = RoundedCornerShape(4.dp)      // Dense UI elements
    val small = RoundedCornerShape(8.dp)           // Buttons, text fields
    val medium = RoundedCornerShape(12.dp)         // Cards, sheets, dialogs
    val large = RoundedCornerShape(16.dp)          // Large cards, surfaces
    val extraLarge = RoundedCornerShape(28.dp)     // Search bars, special containers
    
    // Circular/rounded shapes
    val circle = RoundedCornerShape(50%)           // Avatars, circular buttons
    val pill = RoundedCornerShape(50.dp)           // Pills, chips
    
    // Specialized shapes
    val searchBar = RoundedCornerShape(28.dp)      // M3E Search bar specific
    val dialog = RoundedCornerShape(16.dp)         // Dialogs
    val navigationRailItem = RoundedCornerShape(8.dp) // Navigation rail items
}

// Extension functions for MaterialTheme.shapes compatibility
val Shapes.extraSmall: RoundedCornerShape
    get() = RoundedCornerShape(4.dp)

val Shapes.small: RoundedCornerShape
    get() = RoundedCornerShape(8.dp)

val Shapes.medium: RoundedCornerShape
    get() = RoundedCornerShape(12.dp)

val Shapes.large: RoundedCornerShape
    get() = RoundedCornerShape(16.dp)

val Shapes.extraLarge: RoundedCornerShape
    get() = RoundedCornerShape(28.dp)

val Shapes.circle: RoundedCornerShape
    get() = RoundedCornerShape(50%)

val Shapes.pill: RoundedCornerShape
    get() = RoundedCornerShape(50.dp)
