package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val MONTH_PAGE_COUNT = 1201
private const val MONTH_BASE_PAGE = MONTH_PAGE_COUNT / 2

/**
 * Visualização de Mês em tela cheia.
 *
 * Arrastar na horizontal troca de mês (como no Google Calendar) e cada dia mostra
 * até três eventos com o título, não só a cor.
 */
@Composable
fun CalendarMonthView(
    month: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    onSelectDay: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val baseMonth = remember { month.withDayOfMonth(1) }
    val pagerState = rememberPagerState(initialPage = MONTH_BASE_PAGE) { MONTH_PAGE_COUNT }
    val eventsByDay = remember(events) { events.groupBy { it.start.toLocalDate() } }

    // Avisa o pai quando o usuário para em outro mês (inclusive ao voltar).
    LaunchedEffect(pagerState, baseMonth) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                onMonthChange(baseMonth.plusMonths((page - MONTH_BASE_PAGE).toLong()))
            }
    }

    // Se a data mudar por fora (ex.: seletor de data), acompanha o mês.
    LaunchedEffect(month) {
        val currentMonth = baseMonth.plusMonths((pagerState.currentPage - MONTH_BASE_PAGE).toLong())
        val target = month.withDayOfMonth(1)
        if (target != currentMonth) {
            val page = MONTH_BASE_PAGE + ChronoUnit.MONTHS.between(baseMonth, target).toInt()
            pagerState.scrollToPage(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        val pageMonth = baseMonth.plusMonths((page - MONTH_BASE_PAGE).toLong())
        MonthPage(
            month = pageMonth,
            selectedDate = selectedDate,
            today = today,
            eventsByDay = eventsByDay,
            onSelectDay = onSelectDay,
            onEventClick = onEventClick,
        )
    }
}

@Composable
private fun MonthPage(
    month: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    onSelectDay: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val firstOfMonth = month.withDayOfMonth(1)
    val gridStart = firstOfMonth.startOfWeek()
    val lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1)
    val daysShown = ChronoUnit.DAYS.between(gridStart, lastOfMonth).toInt() + 1
    val weeks = (daysShown + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (index in 0..6) {
                Text(
                    text = gridStart.plusDays(index.toLong()).weekdayLetter(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                for (day in 0..6) {
                    val date = gridStart.plusDays((week * 7 + day).toLong())
                    MonthViewCell(
                        date = date,
                        inMonth = date.month == firstOfMonth.month,
                        selected = date == selectedDate,
                        isToday = date == today,
                        events = eventsByDay[date].orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDay(date) },
                        onEventClick = onEventClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthViewCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    modifier: Modifier = Modifier,
    onEventClick: (CalendarEvent) -> Unit = {},
    onClick: () -> Unit,
) {
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
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
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
        Spacer(Modifier.height(2.dp))

        events.take(3).forEach { event ->
            val background = event.color.accent()
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (background.luminance() > 0.55f) Color.Black else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onEventClick(event) }
                    .background(background)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
            Spacer(Modifier.height(1.dp))
        }

        if (events.size > 3) {
            Text(
                text = "+${events.size - 3}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
