package com.daniel.nextasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.daniel.nextasks.data.AccentColor
import com.daniel.nextasks.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Emerald700,
    onPrimary = Emerald50,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald950,
    inversePrimary = Emerald500,
    secondary = Neutral100,
    onSecondary = Neutral900,
    secondaryContainer = Neutral100,
    onSecondaryContainer = Neutral900,
    tertiary = Emerald500,
    onTertiary = Emerald50,
    tertiaryContainer = Emerald100,
    onTertiaryContainer = Emerald950,
    background = Color.White,
    onBackground = Neutral950,
    surface = Color.White,
    onSurface = Neutral950,
    surfaceVariant = Neutral50,
    onSurfaceVariant = Neutral500,
    surfaceTint = Emerald700,
    inverseSurface = Neutral900,
    inverseOnSurface = Neutral50,
    error = Red600,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    outline = Neutral200,
    outlineVariant = Color(0xFFEFEFEF),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEDEDED),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral50,
    surfaceContainer = Neutral50,
    surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE9E9E9),
)

private val DarkColors = darkColorScheme(
    primary = Emerald500,
    onPrimary = Color(0xFF00281C),
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,
    inversePrimary = Emerald700,
    secondary = Neutral700,
    onSecondary = Neutral50,
    secondaryContainer = Neutral800,
    onSecondaryContainer = Neutral50,
    tertiary = Emerald500,
    onTertiary = Color(0xFF00281C),
    tertiaryContainer = Emerald800,
    onTertiaryContainer = Emerald100,
    background = Neutral950,
    onBackground = Neutral50,
    surface = Neutral950,
    onSurface = Neutral50,
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral400,
    surfaceTint = Emerald500,
    inverseSurface = Neutral50,
    inverseOnSurface = Neutral950,
    error = Color(0xFFFF6467),
    onError = Color(0xFF3B0006),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF2E2E2E),
    outlineVariant = Neutral800,
    scrim = Color.Black,
    surfaceBright = Neutral800,
    surfaceDim = Neutral950,
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Neutral900,
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Neutral800,
)

/**
 * Tema do NexTasks.
 *
 * As cores dinâmicas (Material You) ficam desligadas de propósito: elas substituiriam
 * o verde da marca pelas cores do papel de parede.
 */
@Composable
fun NexTasksTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: AccentColor = AccentColor.Green,
    content: @Composable () -> Unit,
) {
    // "Sistema" segue o aparelho; as outras duas opções fixam o tema mesmo que o
    // sistema mude (útil para quem usa o app à noite com o sistema no claro).
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val scheme = (if (darkTheme) DarkColors else LightColors)
        .withAccent(accent.colors(), dark = darkTheme)

    MaterialTheme(
        colorScheme = scheme,
        typography = NexTasksTypography,
        content = content,
    )
}

/**
 * Troca só os papéis de destaque; os neutros (fundo, superfície, bordas)
 * continuam os mesmos em qualquer cor escolhida.
 */
private fun ColorScheme.withAccent(accent: AccentColors, dark: Boolean): ColorScheme {
    val primary = if (dark) accent.darkPrimary else accent.lightPrimary
    val onPrimary = if (dark) accent.darkOnPrimary else accent.lightOnPrimary
    val container = if (dark) accent.darkContainer else accent.lightContainer
    val onContainer = if (dark) accent.darkOnContainer else accent.lightOnContainer

    return copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = if (dark) accent.lightPrimary else accent.darkPrimary,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        surfaceTint = primary,
    )
}
