package com.rd.livedash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp, color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 15.sp, color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal, fontSize = 13.sp, color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp, color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = TextMuted
    )
)
