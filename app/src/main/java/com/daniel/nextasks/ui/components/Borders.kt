package com.daniel.nextasks.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Borda tracejada usada nos cards de estado vazio — o mesmo recurso do
 * `empty-state-card.tsx` do app web.
 */
internal fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 1.dp,
): Modifier = drawBehind {
    val stroke = strokeWidth.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
        ),
    )
}

/** Recorta o card e aplica a borda tracejada de uma vez. */
internal fun Modifier.dashedCard(color: Color, cornerRadius: Dp = 12.dp): Modifier =
    this.clip(RoundedCornerShape(cornerRadius)).dashedBorder(color, cornerRadius)
