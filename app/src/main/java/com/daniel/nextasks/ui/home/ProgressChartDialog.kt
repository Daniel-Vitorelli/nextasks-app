package com.daniel.nextasks.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val PtBr = Locale.forLanguageTag("pt-BR")
private val AxisDateFormatter = DateTimeFormatter.ofPattern("dd/MM", PtBr)
private val DetailDateFormatter = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", PtBr)
private val PERIODS = listOf(7, 15, 30, 60)

/**
 * Gráfico de progresso da rotina em tela cheia e **na horizontal**.
 *
 * No app web ele fica num card de 30 dias no meio da home; no celular em pé não
 * cabe. Deitado, o eixo do tempo ganha os ~2300 px de largura e a leitura fica
 * confortável — que é justamente o motivo do botão existir.
 */
@Composable
fun ProgressChartDialog(
    routine: Routine?,
    completions: List<Completion>,
    today: LocalDate,
    overrides: List<BlockOverride> = emptyList(),
    extras: List<ExtraBlock> = emptyList(),
    agendaEvents: List<AgendaEvent> = emptyList(),
    onDismiss: () -> Unit,
) {
    ForceLandscape()

    var period by rememberSaveable { mutableStateOf(30) }
    var selected by remember { mutableStateOf<Int?>(null) }

    // A rotina só existe a partir da criação: nada antes disso entra no gráfico.
    val createdOn = remember(routine) {
        routine?.createdAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    }

    val series = remember(routine, completions, period, today, createdOn) {
        val days = (period - 1 downTo 0)
            .map { today.minusDays(it.toLong()) }
            .filter { createdOn == null || !it.isBefore(createdOn) }
        progressSeries(routine, completions, days, overrides, extras, agendaEvents)
    }

    val accent = MaterialTheme.colorScheme.primary
    val hasData = series.any { it.percent != null }
    val activePeriod = period

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
                            text = stringResource(R.string.chart_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = routine?.name.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PERIODS.forEach { option ->
                            FilterChip(
                                selected = activePeriod == option,
                                onClick = {
                                    period = option
                                    selected = null
                                },
                                label = { Text("${option}d") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.16f),
                                    selectedLabelColor = accent,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.size(8.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.chart_close),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = summaryText(series, selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                if (hasData) {
                    ProgressAreaChart(
                        series = series,
                        color = accent,
                        selected = selected,
                        onSelect = { selected = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.chart_no_records),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun summaryText(series: List<DayProgress>, selected: Int?): String {
    val point = selected?.let { series.getOrNull(it) }
    if (point != null) {
        val date = point.date.format(DetailDateFormatter)
            .replaceFirstChar { it.titlecase(PtBr) }
        return if (point.percent != null) {
            stringResource(R.string.chart_day_detail, date, point.percent)
        } else {
            stringResource(R.string.chart_day_empty, date)
        }
    }

    val values = series.mapNotNull { it.percent }
    if (values.isEmpty()) return stringResource(R.string.chart_no_records)
    return stringResource(R.string.chart_average, values.average().roundToInt(), series.size)
}

/** Geometria compartilhada entre o desenho e o toque. */
private class ChartGeom(
    width: Float,
    height: Float,
    val count: Int,
    density: Float,
) {
    private val padLeft = 38f * density
    private val padRight = 16f * density
    private val padTop = 12f * density
    private val padBottom = 26f * density

    val left = padLeft
    val right = width - padRight
    val top = padTop
    val bottom = height - padBottom

    private val spanX = (right - left).coerceAtLeast(1f)
    private val spanY = (bottom - top).coerceAtLeast(1f)

    fun x(index: Int): Float =
        if (count <= 1) (left + right) / 2f else left + spanX * index / (count - 1)

    fun y(percent: Float): Float = bottom - spanY * (percent / 100f)

    fun indexAt(px: Float): Int =
        if (count <= 1) 0 else ((px - left) / spanX * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

@Composable
private fun ProgressAreaChart(
    series: List<DayProgress>,
    color: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = MaterialTheme.colorScheme.background

    Canvas(
        modifier = modifier.pointerInput(series) {
            detectTapGestures { offset ->
                val geom = ChartGeom(size.width.toFloat(), size.height.toFloat(), series.size, density)
                val index = geom.indexAt(offset.x)
                onSelect(if (index == selected) null else index)
            }
        },
    ) {
        val geom = ChartGeom(size.width, size.height, series.size, density)

        // Linhas de grade em 0/25/50/75/100, com rótulo só nos extremos e no meio.
        listOf(0, 25, 50, 75, 100).forEach { value ->
            val y = geom.y(value.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(geom.left, y),
                end = Offset(geom.right, y),
                strokeWidth = if (value == 0) 1.6f else 1f,
            )
            if (value % 50 == 0) {
                val layout = measurer.measure(AnnotatedString("$value%"), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = geom.left - layout.size.width - 6f * density,
                        y = y - layout.size.height / 2f,
                    ),
                )
            }
        }

        // Rótulos do eixo do tempo, espaçados para não colidirem. O último dia
        // sempre aparece, mas sem encostar no anterior (senão viram um borrão).
        val step = ceil(series.size / 8f).toInt().coerceAtLeast(1)
        val labelIndices = buildList {
            var i = 0
            while (i < series.size) {
                add(i)
                i += step
            }
            val last = series.size - 1
            if (last >= 0 && last() != last) {
                if (last - last() < step / 2f) removeAt(lastIndex)
                add(last)
            }
        }
        labelIndices.forEach { index ->
            val point = series[index]
            val layout = measurer.measure(
                AnnotatedString(point.date.format(AxisDateFormatter)),
                labelStyle,
            )
            val x = (geom.x(index) - layout.size.width / 2f)
                .coerceIn(geom.left, geom.right - layout.size.width)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, geom.bottom + 6f * density),
            )
        }

        // Área e linha, quebradas nos dias sem bloco confirmável.
        var run = mutableListOf<Int>()
        fun flush() {
            if (run.isEmpty()) return
            if (run.size == 1) {
                val i = run.first()
                series[i].percent?.let { percent ->
                    drawCircle(
                        color = color,
                        radius = 3f * density,
                        center = Offset(geom.x(i), geom.y(percent.toFloat())),
                    )
                }
            } else {
                val line = Path()
                val area = Path()
                run.forEachIndexed { position, index ->
                    val percent = series[index].percent?.toFloat() ?: return@forEachIndexed
                    val x = geom.x(index)
                    val y = geom.y(percent)
                    if (position == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, geom.bottom)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(geom.x(run.last()), geom.bottom)
                area.close()

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                        startY = geom.top,
                        endY = geom.bottom,
                    ),
                )
                drawPath(
                    path = line,
                    color = color,
                    style = Stroke(width = 2.2f * density, cap = StrokeCap.Round),
                )
            }
            run = mutableListOf()
        }

        series.forEachIndexed { index, point ->
            if (point.percent == null) {
                flush()
            } else {
                run.add(index)
            }
        }
        flush()

        // Pontos: sem eles, um dia isolado (rotina semanal) sumiria.
        series.forEachIndexed { index, point ->
            val percent = point.percent ?: return@forEachIndexed
            drawCircle(
                color = color,
                radius = 2.4f * density,
                center = Offset(geom.x(index), geom.y(percent.toFloat())),
            )
        }

        // Dia selecionado: guia vertical e ponto em destaque.
        selected?.let { index ->
            val percent = series.getOrNull(index)?.percent ?: return@let
            val x = geom.x(index)
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(x, geom.top),
                end = Offset(x, geom.bottom),
                strokeWidth = 1.2f * density,
            )
            drawCircle(
                color = color,
                radius = 5f * density,
                center = Offset(x, geom.y(percent.toFloat())),
            )
            drawCircle(
                color = backgroundColor,
                radius = 2f * density,
                center = Offset(x, geom.y(percent.toFloat())),
            )
        }
    }
}
