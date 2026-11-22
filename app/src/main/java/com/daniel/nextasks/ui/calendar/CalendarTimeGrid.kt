package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

private val HourHeight = 56.dp
private val AxisWidth = 48.dp
private const val HoursInDay = 24

/**
 * Grade de horários usada pelas visualizações Dia, 3 dias e Semana.
 *
 * Mesma estrutura do Google Calendar: cabeçalho dos dias, faixa de eventos de
 * dia inteiro, eixo de horas à esquerda, blocos posicionados pelo horário e a
 * linha vermelha do horário atual.
 */
@Composable
fun CalendarTimeGrid(
    days: List<LocalDate>,
    events: List<CalendarEvent>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timedEvents = remember(events, days) {
        events.filter { !it.isAllDay && it.start.toLocalDate() in days }
    }
    val allDayEvents = remember(events, days) {
        events.filter { it.isAllDay && it.start.toLocalDate() in days }
    }
    val now = remember { LocalTime.now() }

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
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (background.luminance() > 0.55f) Color.Black else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(background)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
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
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val initialScroll = remember(density, now) {
        with(density) {
            (HourHeight * (now.hour - 1).coerceAtLeast(0)).roundToPx()
        }
    }
    val scrollState = rememberScrollState(initialScroll)

    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // Eixo de horas
        Column(Modifier.width(AxisWidth)) {
            repeat(HoursInDay) { hour ->
                Box(
                    modifier = Modifier
                        .height(HourHeight)
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
                .height(HourHeight * HoursInDay),
        ) {
            days.forEach { day ->
                DayColumn(
                    events = events.filter { it.start.toLocalDate() == day },
                    isToday = day == today,
                    now = now,
                    onEventClick = onEventClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    events: List<CalendarEvent>,
    isToday: Boolean,
    now: LocalTime,
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
                val hourPx = HourHeight.toPx()
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
        events.forEach { event -> EventBlock(event, onClick = { onEventClick(event) }) }

        if (isToday) {
            val minutes = now.hour * 60 + now.minute
            val y = ((minutes / 60f) * HourHeight.value).dp
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
private fun EventBlock(event: CalendarEvent, onClick: () -> Unit) {
    val startMinutes = event.start.hour * 60 + event.start.minute
    val endMinutes = event.end.hour * 60 + event.end.minute
    val top = ((startMinutes / 60f) * HourHeight.value).dp
    val height = (((endMinutes - startMinutes) / 60f) * HourHeight.value)
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
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
