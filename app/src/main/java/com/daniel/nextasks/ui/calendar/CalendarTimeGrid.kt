package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.ui.components.rememberClockTime
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

private val BaseHourHeight = 56.dp
private val AxisWidth = 48.dp
private const val HoursInDay = 24
private const val MinutesInDay = HoursInDay * 60

/** Limites do zoom por pinça, em múltiplos da altura padrão de uma hora. */
private const val MinZoom = 0.6f
private const val MaxZoom = 2.5f

/**
 * Grade de horários usada pelas visualizações Dia, 3 dias e Semana.
 *
 * Mesma estrutura do Google Calendar: cabeçalho dos dias, faixa de eventos de
 * dia inteiro, eixo de horas à esquerda, blocos posicionados pelo horário e a
 * linha vermelha do horário atual.
 *
 * O zoom é controlado por [zoom]/[onZoom] porque o estado precisa sobreviver à
 * troca de dia e de visualização — dentro da grade ele seria recriado a cada
 * `AnimatedContent`.
 */
@Composable
fun CalendarTimeGrid(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit = {},
    /** 1 = altura padrão da hora; a pinça de dois dedos muda este fator. */
    zoom: Float = 1f,
    onZoom: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timedEvents = remember(events, days) {
        events.filter { !it.isAllDay && it.start.toLocalDate() in days }
    }
    val allDayEvents = remember(events, days) {
        events.filter { it.isAllDay && it.start.toLocalDate() in days }
    }
    val now = rememberClockTime()

    Column(modifier.fillMaxSize()) {
        DayHeaders(
            days = days,
            today = today,
            selectedDate = selectedDate,
            onSelectDay = onSelectDay,
        )
        if (allDayEvents.isNotEmpty()) {
            AllDayRow(days = days, allDayEvents = allDayEvents)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TimeGridBody(
            days = days,
            events = timedEvents,
            today = today,
            now = now,
            hourHeight = BaseHourHeight * zoom,
            zoom = zoom,
            onZoom = onZoom,
            onEventClick = onEventClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DayHeaders(
    days: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Spacer(Modifier.width(AxisWidth))
        days.forEach { day ->
            val selected = day == selectedDate
            val isToday = day == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectDay(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.weekdayLetter(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected || isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (selected) {
                                Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            selected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayRow(days: List<LocalDate>, allDayEvents: List<CalendarEvent>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Spacer(Modifier.width(AxisWidth))
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                allDayEvents
                    .filter { it.start.toLocalDate() == day }
                    .forEach { event ->
                        val background = event.color.accent()
                        val contentColor = if (background.luminance() > 0.55f) Color.Black else Color.White
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(background)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            if (event.isUnforeseen) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_unforeseen),
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun TimeGridBody(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    today: LocalDate,
    now: LocalTime,
    hourHeight: Dp,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val initialScroll = remember(density, now) {
        with(density) {
            (hourHeight * (now.hour - 1).coerceAtLeast(0)).roundToPx()
        }
    }
    val scrollState = rememberScrollState(initialScroll)
    val scope = rememberCoroutineScope()
    // O `pointerInput` não reinicia a cada recomposição: ele precisa de um State
    // para ler o zoom atual no começo de cada pinça, senão a segunda pinça partiria
    // sempre de 1x.
    val zoomState = rememberUpdatedState(zoom)

    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            // Depois do `verticalScroll` de propósito: assim a pinça vê o evento
            // antes da rolagem e pode consumi-lo, em vez de rolar e dar zoom juntos.
            .pinchToZoom(zoom = zoomState, range = MinZoom..MaxZoom) { newZoom, oldZoom, centroidY ->
                val oldPx = with(density) { (BaseHourHeight * oldZoom).toPx() }
                val newPx = with(density) { (BaseHourHeight * newZoom).toPx() }
                onZoom(newZoom)

                // Mantém sob os dedos o mesmo horário de antes do zoom.
                scope.launch {
                    scrollState.scrollTo(
                        zoomedScrollOffset(scrollState.value, oldPx, newPx, centroidY),
                    )
                }
            },
    ) {
        // Eixo de horas
        Column(Modifier.width(AxisWidth)) {
            repeat(HoursInDay) { hour ->
                Box(
                    modifier = Modifier
                        .height(hourHeight)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "%02d:00".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp),
                    )
                }
            }
        }

        // Colunas dos dias
        Row(
            modifier = Modifier
                .weight(1f)
                .height(hourHeight * HoursInDay),
        ) {
            days.forEach { day ->
                DayColumn(
                    events = events.filter { it.start.toLocalDate() == day },
                    isToday = day == today,
                    now = now,
                    hourHeight = hourHeight,
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Posição de rolagem que deixa o horário sob o dedo no mesmo lugar depois do zoom.
 *
 * `centroidY` é a altura do ponto médio dos dedos dentro da área visível, então
 * `currentScroll + centroidY` é o ponto do conteúdo que o usuário está mirando.
 * Convertido em horas e multiplicado pela nova altura, volta a virar pixel.
 */
internal fun zoomedScrollOffset(
    currentScroll: Int,
    oldHourHeightPx: Float,
    newHourHeightPx: Float,
    centroidY: Float,
): Int {
    if (oldHourHeightPx <= 0f) return currentScroll
    val hoursUnderFinger = (currentScroll + centroidY) / oldHourHeightPx
    return (hoursUnderFinger * newHourHeightPx - centroidY).roundToInt().coerceAtLeast(0)
}

/**
 * Pinça de dois dedos para dar zoom.
 *
 * Só reage quando há **dois ou mais** dedos: com um dedo, a rolagem vertical e o
 * toque nos blocos continuam funcionando normalmente. Os eventos da pinça são
 * consumidos para a rolagem não andar junto.
 *
 * [onZoom] recebe o zoom **absoluto** (não o fator do evento), junto do zoom que
 * estava aplicado antes deste passo — é o que permite reancorar a rolagem sem
 * depender de estado que ainda não recompôs.
 */
internal fun Modifier.pinchToZoom(
    zoom: State<Float>,
    range: ClosedFloatingPointRange<Float>,
    onZoom: (newZoom: Float, oldZoom: Float, centroidY: Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        var base: Float? = null      // zoom no instante em que a pinça começou
        var accumulated = 1f         // fator acumulado dentro desta pinça
        var applied = 1f             // último zoom que já mandamos aplicar

        do {
            val event = awaitPointerEvent()

            // Voltou a um dedo: o próximo movimento de dois dedos é outra pinça.
            if (event.changes.count { it.pressed } < 2) {
                base = null
                accumulated = 1f
                continue
            }

            val factor = event.calculateZoom()
            if (factor == 1f) continue

            val start = base ?: zoom.value.also {
                base = it
                applied = it
                accumulated = 1f
            }

            accumulated *= factor
            val target = (start * accumulated).coerceIn(range.start, range.endInclusive)

            onZoom(target, applied, event.calculateCentroid().y)
            applied = target

            // Sem consumir, a rolagem vertical anda junto com a pinça.
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun DayColumn(
    events: List<CalendarEvent>,
    isToday: Boolean,
    now: LocalTime,
    hourHeight: Dp,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val halfLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val nowColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                val hourPx = hourHeight.toPx()
                for (hour in 0..HoursInDay) {
                    val y = hour * hourPx
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                for (hour in 0 until HoursInDay) {
                    val y = (hour + 0.5f) * hourPx
                    drawLine(halfLineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            },
    ) {
        events.forEach { event ->
            EventBlock(event = event, hourHeight = hourHeight, onClick = { onEventClick(event) })
        }

        if (isToday) {
            val minutes = now.hour * 60 + now.minute
            val y = ((minutes / 60f) * hourHeight.value).dp
            Box(
                modifier = Modifier
                    .offset(y = y)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(nowColor),
            )
        }
    }
}

@Composable
private fun EventBlock(event: CalendarEvent, hourHeight: Dp, onClick: () -> Unit) {
    val startMinutes = event.start.hour * 60 + event.start.minute
    // Duração de verdade: um bloco 22:00–01:00 atravessa o dia e não pode virar
    // um toco de 20 dp por causa da conta `fim - início` em minutos do mesmo dia.
    val durationMinutes = Duration.between(event.start, event.end).toMinutes().toInt()
    val visibleMinutes = (MinutesInDay - startMinutes).coerceAtLeast(0)
    val top = ((startMinutes / 60f) * hourHeight.value).dp
    val height = ((durationMinutes.coerceIn(0, visibleMinutes) / 60f) * hourHeight.value)
        .dp
        .coerceAtLeast(20.dp)
    val background = event.color.accent()
    val contentColor = if (background.luminance() > 0.55f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .padding(start = 1.dp, end = 1.dp)
            .offset(y = top)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (event.isUnforeseen) {
                Icon(
                    painter = painterResource(R.drawable.ic_unforeseen),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (height >= 34.dp) {
            Text(
                text = "${event.start.toLocalTime().hourMinute()} – ${event.end.toLocalTime().hourMinute()}",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}
