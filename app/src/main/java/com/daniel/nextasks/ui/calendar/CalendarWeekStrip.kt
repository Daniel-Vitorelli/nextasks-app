package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Tira de dias da semana, paginável na horizontal — arrastar troca de semana
 * mantendo o mesmo dia da semana selecionado, como no Google Calendar.
 */
@Composable
fun CalendarWeekStrip(
    pagerState: PagerState,
    baseWeekStart: LocalDate,
    basePage: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) { page ->
        val weekStart = baseWeekStart.plusWeeks((page - basePage).toLong())
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0..6) {
                val date = weekStart.plusDays(index.toLong())
                DayCell(
                    date = date,
                    selected = date == selectedDate,
                    isToday = date == today,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDay(date) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.weekdayLetter(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected || isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (selected) Modifier.background(primary, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
