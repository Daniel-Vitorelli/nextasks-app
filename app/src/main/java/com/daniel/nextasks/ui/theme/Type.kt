package com.daniel.nextasks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.daniel.nextasks.R

/** Inter é a fonte do NexTasks (--font-sans no globals.css). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Base = Typography()

/** Escala Material 3 com Inter; títulos em semibold, como no app web. */
val NexTasksTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    displayMedium = Base.displayMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
    displaySmall = Base.displaySmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = Base.bodySmall.copy(fontFamily = Inter),
    labelLarge = Base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontFamily = Inter),
)
