package com.daniel.nextasks.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.BlockOverride
import com.daniel.nextasks.data.Completion
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.Routine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val PtBr = Locale.forLanguageTag("pt-BR")
private val MonthFormatter = DateTimeFormatter.ofPattern("MMM", PtBr)
private val WeekdayLetterFormatter = DateTimeFormatter.ofPattern("EEEEE", PtBr)
private val HeatmapDetailFormatter = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", PtBr)

/** Quantos dias o mapa cobre — mesmo recorte do app web. */
private const val HEATMAP_DAYS = 365

/**
 * Mapa de calor do ano, como o do app web: uma coluna por semana (domingo no
 * topo), um quadrado por dia, intensidade da cor conforme o progresso.
 *
 * Assim como o gráfico, abre em tela cheia e na horizontal: 53 semanas em pé
 * não caberiam.
 */
@Composable
fun HeatmapDialog(
    routine: Routine?,
    completions: List<Completion>,
    today: LocalDate,
    overrides: List<BlockOverride> = emptyList(),
    extras: List<ExtraBlock> = emptyList(),
    agendaEvents: List<AgendaEvent> = emptyList(),
    onDismiss: () -> Unit,
) {
    ForceLandscape()

    val createdOn = remember(routine) {
        routine?.createdAt?.let {
            java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    val series = remember(routine, completions, today, createdOn, overrides, extras, agendaEvents) {
        val days = (HEATMAP_DAYS - 1 downTo 0)
            .map { today.minusDays(it.toLong()) }
            .filter { createdOn == null || !it.isBefore(createdOn) }
        progressSeries(routine, completions, days, overrides, extras, agendaEvents)
    }

    // Colunas = semanas alinhadas no domingo; o começo pode ficar incompleto.
    val columns = remember(series) {
        val leading = series.firstOrNull()?.date?.dayOfWeek?.value?.rem(7) ?: 0
        val padded: List<DayProgress?> = List(leading) { null } + series
        padded.chunked(7)
    }

    var selected by remember { mutableStateOf<Int?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.heatmap_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = routine?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.heatmap_close),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = summaryText(series, selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                HeatmapGrid(
                    columns = columns,
                    series = series,
                    accent = accent,
                    emptyColor = muted,
                    labelColor = onSurfaceVariant,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                Spacer(Modifier.height(8.dp))
                Legend(accent = accent, emptyColor = muted, labelColor = onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun summaryText(series: List<DayProgress>, selected: Int?): String {
    val point = selected?.let { series.getOrNull(it) }
    if (point != null) {
        val date = point.date.format(HeatmapDetailFormatter)
            .replaceFirstChar { it.titlecase(PtBr) }
        return if (point.percent != null) {
            stringResource(R.string.heatmap_day_detail, date, point.percent)
        } else {
            stringResource(R.string.heatmap_day_empty, date)
        }
    }

    val full = series.count { it.percent == 100 }
    return stringResource(R.string.heatmap_summary, full, series.size)
}

/** Cor do dia conforme o progresso — mesmos cortes de `levelClass` do web. */
private fun levelColor(percent: Int?, accent: Color, empty: Color): Color? = when {
    percent == null -> null
    percent == 0 -> empty
    percent < 25 -> accent.copy(alpha = 0.30f)
    percent < 50 -> accent.copy(alpha = 0.50f)
    percent < 75 -> accent.copy(alpha = 0.70f)
    percent < 100 -> accent.copy(alpha = 0.90f)
    else -> accent
}

private class HeatmapGeom(
    width: Float,
    height: Float,
    val columnCount: Int,
    density: Float,
) {
    private val gap = 3f * density
    private val leftLabels = 20f * density
    private val topLabels = 16f * density

    val cell: Float = min(
        (width - leftLabels) / columnCount - gap,
        (height - topLabels) / 7f - gap,
    ).coerceAtLeast(2f)

    private val gridWidth = columnCount * (cell + gap) - gap
    private val gridHeight = 7 * (cell + gap) - gap

    val startX = leftLabels + ((width - leftLabels) - gridWidth) / 2f
    val startY = topLabels + ((height - topLabels) - gridHeight) / 2f

    fun left(column: Int) = startX + column * (cell + gap)
    fun top(row: Int) = startY + row * (cell + gap)

    /** Coluna/linha sob o toque, ou nulo se caiu fora da grade. */
    fun cellAt(offset: Offset): Pair<Int, Int>? {
        if (offset.x < startX || offset.y < startY) return null
        val column = ((offset.x - startX) / (cell + gap)).toInt()
        val row = ((offset.y - startY) / (cell + gap)).toInt()
        if (column !in 0 until columnCount || row !in 0..6) return null
        return column to row
    }

    fun rowCenter(row: Int) = top(row) + cell / 2f
}

@Composable
private fun HeatmapGrid(
    columns: List<List<DayProgress?>>,
    series: List<DayProgress>,
    accent: Color,
    emptyColor: Color,
    labelColor: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = labelColor)
    val selectionColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = modifier.pointerInput(columns) {
            detectTapGestures { offset ->
                val geom = HeatmapGeom(size.width.toFloat(), size.height.toFloat(), columns.size, density)
                val hit = geom.cellAt(offset)
                if (hit == null) {
                    onSelect(null)
                } else {
                    val (column, row) = hit
                    val point = columns.getOrNull(column)?.getOrNull(row)
                    // O índice em `series` vem do próprio dia tocado: a primeira
                    // coluna tem padding nulo para alinhar no domingo, e calcular
                    // `column * 7 + row` deslocava o texto em até 6 dias.
                    val index = point?.let { series.indexOf(it) }?.takeIf { it >= 0 }
                    onSelect(
                        when {
                            index == null -> null
                            index == selected -> null
                            else -> index
                        },
                    )
                }
            }
        },
    ) {
        val geom = HeatmapGeom(size.width, size.height, columns.size, density)
        val corner = CornerRadius(geom.cell * 0.22f)

        // Letras dos dias da semana, começando no domingo. A primeira coluna pode
        // começar com células vazias, então procuramos o primeiro dia de verdade.
        val firstDate = columns.asSequence()
            .flatMap { it.asSequence() }
            .firstOrNull { it != null }
            ?.date
        if (firstDate != null) {
            val firstSunday = firstDate.minusDays(firstDate.dayOfWeek.value.toLong().rem(7))
            for (row in 0..6) {
                val label = firstSunday.plusDays(row.toLong()).format(WeekdayLetterFormatter)
                val layout = measurer.measure(AnnotatedString(label), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = geom.startX - 8f * density - layout.size.width,
                        y = geom.rowCenter(row) - layout.size.height / 2f,
                    ),
                )
            }
        }

        columns.forEachIndexed { column, days ->
            days.forEachIndexed { row, point ->
                if (point == null) return@forEachIndexed
                val color = levelColor(point.percent, accent, emptyColor) ?: return@forEachIndexed
                drawRoundRect(
                    color = color,
                    topLeft = Offset(geom.left(column), geom.top(row)),
                    size = Size(geom.cell, geom.cell),
                    cornerRadius = corner,
                )
            }
        }

        // Rótulo do mês na coluna que contém o dia 1º.
        columns.forEachIndexed { column, days ->
            val firstOfMonth = days.firstOrNull { it?.date?.dayOfMonth == 1 } ?: return@forEachIndexed
            val layout = measurer.measure(
                AnnotatedString(firstOfMonth.date.format(MonthFormatter)),
                labelStyle,
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = geom.left(column),
                    y = geom.startY - layout.size.height - 3f * density,
                ),
            )
        }

        // Realce do dia escolhido.
        selected?.let { index ->
            var remaining = index
            for (column in columns.indices) {
                val days = columns[column]
                if (remaining < days.size) {
                    if (days[remaining] != null) {
                        drawRoundRect(
                            color = selectionColor,
                            topLeft = Offset(geom.left(column), geom.top(remaining)),
                            size = Size(geom.cell, geom.cell),
                            cornerRadius = corner,
                            style = Stroke(width = 1.6f * density),
                        )
                    }
                    return@let
                }
                remaining -= days.size
            }
        }
    }
}

@Composable
private fun Legend(accent: Color, emptyColor: Color, labelColor: Color) {
    val levels = listOf(
        emptyColor,
        accent.copy(alpha = 0.30f),
        accent.copy(alpha = 0.50f),
        accent.copy(alpha = 0.70f),
        accent.copy(alpha = 0.90f),
        accent,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.heatmap_less),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        Spacer(Modifier.width(6.dp))
        levels.forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.heatmap_more),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            textAlign = TextAlign.End,
        )
    }
}
