package com.daniel.nextasks.ui.theme

import androidx.compose.ui.graphics.Color
import com.daniel.nextasks.data.AccentColor

/**
 * Cores de uma opção de destaque.
 *
 * Cada tom vem de uma rampa só (500 no seletor, 700/100/950 no claro e
 * 400/800/100 no escuro), então todas as opções têm o mesmo contraste entre
 * fundo, texto e destaque.
 */
data class AccentColors(
    val swatch: Color,
    /** Cor do ícone/check sobre o swatch. */
    val onSwatch: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

/**
 * Verde é a cor da marca e mantém **exatamente** os tokens do design system
 * (`Emerald700`/`Emerald500`), para o padrão não mudar de tom.
 */
fun AccentColor.colors(): AccentColors = when (this) {
    AccentColor.Green -> AccentColors(
        swatch = Emerald500,
        onSwatch = Emerald50,
        lightPrimary = Emerald700,
        lightOnPrimary = Emerald50,
        lightContainer = Emerald100,
        lightOnContainer = Emerald950,
        darkPrimary = Emerald500,
        darkOnPrimary = Color(0xFF00281C),
        darkContainer = Emerald800,
        darkOnContainer = Emerald100,
    )

    AccentColor.Blue -> AccentColors(
        swatch = Color(0xFF3B82F6),
        onSwatch = Color(0xFFEFF6FF),
        lightPrimary = Color(0xFF1D4ED8),
        lightOnPrimary = Color(0xFFEFF6FF),
        lightContainer = Color(0xFFDBEAFE),
        lightOnContainer = Color(0xFF172554),
        darkPrimary = Color(0xFF60A5FA),
        darkOnPrimary = Color(0xFF172554),
        darkContainer = Color(0xFF1E40AF),
        darkOnContainer = Color(0xFFDBEAFE),
    )

    AccentColor.Purple -> AccentColors(
        swatch = Color(0xFF8B5CF6),
        onSwatch = Color(0xFFF5F3FF),
        lightPrimary = Color(0xFF6D28D9),
        lightOnPrimary = Color(0xFFF5F3FF),
        lightContainer = Color(0xFFEDE9FE),
        lightOnContainer = Color(0xFF2E1065),
        darkPrimary = Color(0xFFA78BFA),
        darkOnPrimary = Color(0xFF2E1065),
        darkContainer = Color(0xFF5B21B6),
        darkOnContainer = Color(0xFFEDE9FE),
    )

    AccentColor.Pink -> AccentColors(
        swatch = Color(0xFFF43F5E),
        onSwatch = Color(0xFFFFF1F2),
        lightPrimary = Color(0xFFBE123C),
        lightOnPrimary = Color(0xFFFFF1F2),
        lightContainer = Color(0xFFFFE4E6),
        lightOnContainer = Color(0xFF4C0519),
        darkPrimary = Color(0xFFFB7185),
        darkOnPrimary = Color(0xFF4C0519),
        darkContainer = Color(0xFF9F1239),
        darkOnContainer = Color(0xFFFFE4E6),
    )

    AccentColor.Orange -> AccentColors(
        swatch = Color(0xFFF97316),
        onSwatch = Color(0xFFFFF7ED),
        lightPrimary = Color(0xFFC2410C),
        lightOnPrimary = Color(0xFFFFF7ED),
        lightContainer = Color(0xFFFFEDD5),
        lightOnContainer = Color(0xFF431407),
        darkPrimary = Color(0xFFFB923C),
        darkOnPrimary = Color(0xFF431407),
        darkContainer = Color(0xFF9A3412),
        darkOnContainer = Color(0xFFFFEDD5),
    )

    AccentColor.Amber -> AccentColors(
        swatch = Color(0xFFF59E0B),
        // Âmbar é claro: o check escuro é o único que aparece.
        onSwatch = Color(0xFF451A03),
        lightPrimary = Color(0xFFB45309),
        lightOnPrimary = Color(0xFFFFFBEB),
        lightContainer = Color(0xFFFEF3C7),
        lightOnContainer = Color(0xFF451A03),
        darkPrimary = Color(0xFFFBBF24),
        darkOnPrimary = Color(0xFF451A03),
        darkContainer = Color(0xFF92400E),
        darkOnContainer = Color(0xFFFEF3C7),
    )

    AccentColor.Cyan -> AccentColors(
        swatch = Color(0xFF06B6D4),
        onSwatch = Color(0xFFECFEFF),
        lightPrimary = Color(0xFF0E7490),
        lightOnPrimary = Color(0xFFECFEFF),
        lightContainer = Color(0xFFCFFAFE),
        lightOnContainer = Color(0xFF083344),
        darkPrimary = Color(0xFF22D3EE),
        darkOnPrimary = Color(0xFF083344),
        darkContainer = Color(0xFF155E75),
        darkOnContainer = Color(0xFFCFFAFE),
    )
}
