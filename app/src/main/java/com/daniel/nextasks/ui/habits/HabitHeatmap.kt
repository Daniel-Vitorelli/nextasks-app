package com.daniel.nextasks.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.data.HabitDay
import com.daniel.nextasks.R
import com.daniel.nextasks.data.HabitKind
import com.daniel.nextasks.data.Habit
import kotlin.math.ceil
import kotlin.math.min

/**
 * Mapa de calor compacto do hábito: uma coluna por semana, um quadrado por dia.
 *
 * Repete os níveis do app web (0 = cinza, depois 30/50/70/90/100% da cor do
 * hábito), com uma diferença: em hábito ruim, a recaída fica vermelha em vez de
 * cinza — cinza não comunica "escorreguei".
 */
@Composable
fun HabitHeatmap(
    habit: Habit,
    progress: List<HabitDay>,
    accent: Color,
    emptyColor: Color,
    slipColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier,
) {
    if (progress.isEmpty()) return

    val leading = progress.first().date.dayOfWeek.value % 7
    val columns = ceil((progress.size + leading) / 7.0).toInt()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val gap = 3f * density
        val cell = min(
            (size.width - gap * (columns - 1)) / columns,
            (size.height - gap * 6) / 7f,
        )
        // Alinhado à esquerda: centralizado sobrava margem dos dois lados.
        val startX = 0f
        val corner = CornerRadius(cell * 0.22f)

        progress.forEachIndexed { index, day ->
            val slot = index + leading
            val column = slot / 7
            val row = slot % 7
            val value = day.value ?: return@forEachIndexed

            val color = when {
                habit.kind == HabitKind.Bad -> if (value == 0) slipColor else accent
                value == 0 -> emptyColor
                value < 25 -> accent.copy(alpha = 0.30f)
                value < 50 -> accent.copy(alpha = 0.50f)
                value < 75 -> accent.copy(alpha = 0.70f)
                value < 100 -> accent.copy(alpha = 0.90f)
                else -> accent
            }

            val topLeft = Offset(
                x = startX + column * (cell + gap),
                y = row * (cell + gap),
            )
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = Size(cell, cell),
                cornerRadius = corner,
            )

            // Hoje ganha contorno para a pessoa se localizar.
            if (index == progress.lastIndex) {
                drawRoundRect(
                    color = outlineColor,
                    topLeft = topLeft,
                    size = Size(cell, cell),
                    cornerRadius = corner,
                    style = Stroke(width = 1.4f * density),
                )
            }
        }
    }
}

/** Legenda compacta "menos → mais" para acompanhar o mapa. */
@Composable
fun HabitHeatmapLegend(
    accent: Color,
    emptyColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val levels = listOf(
        emptyColor,
        accent.copy(alpha = 0.30f),
        accent.copy(alpha = 0.50f),
        accent.copy(alpha = 0.70f),
        accent.copy(alpha = 0.90f),
        accent,
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(R.string.heatmap_less),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        levels.forEach { color ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Text(
            text = stringResource(R.string.heatmap_more),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}
