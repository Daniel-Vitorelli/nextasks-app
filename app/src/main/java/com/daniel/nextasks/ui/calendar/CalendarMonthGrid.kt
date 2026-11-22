package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Grade do mês expandido: só as semanas necessárias (5 ou 6), começando no domingo.
 * Dias de outros meses ficam esmaecidos; dias com evento ganham um ponto.
 */
@Composable
fun CalendarMonthGrid(
    month: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstOfMonth = month.withDayOfMonth(1)
    val gridStart = firstOfMonth.startOfWeek()
    val lastOfMonth = firstOfMonth.plusMonths(1).minusDays(1)
    val daysShown = ChronoUnit.DAYS.between(gridStart, lastOfMonth).toInt() + 1
    val weeks = (daysShown + 6) / 7

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            Row(Modifier.fillMaxWidth()) {
                for (day in 0..6) {
                    val date = gridStart.plusDays((week * 7 + day).toLong())
                    MonthDayCell(
                        date = date,
                        inMonth = date.month == firstOfMonth.month,
                        selected = date == selectedDate,
                        isToday = date == today,
                        hasEvents = date in eventDays,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> primary
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.background(primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = if (hasEvents) primary else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}
