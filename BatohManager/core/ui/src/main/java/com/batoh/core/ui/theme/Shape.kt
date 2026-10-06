package com.batoh.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Buttons
    large = RoundedCornerShape(16.dp), // Dialogs
    extraLarge = RoundedCornerShape(24.dp)
)
