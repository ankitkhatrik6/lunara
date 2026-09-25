package com.dhunya.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DhunyaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),      // Moderately rounded
    medium = RoundedCornerShape(14.dp),     // Standard cards
    large = RoundedCornerShape(20.dp),      // Elevated sheets and dialogs
    extraLarge = RoundedCornerShape(28.dp)
)
