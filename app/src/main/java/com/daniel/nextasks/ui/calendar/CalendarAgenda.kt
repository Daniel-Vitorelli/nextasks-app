package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Completion
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.ui.components.ScoreDialog
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** Quantos dias à frente a agenda contínua mostra. */
internal const val AGENDA_DAYS_AHEAD = 45

/** Linha da agenda: cabeçalho de dia, evento ou dia vazio. */
private sealed interface AgendaRow {
    data class Header(val date: LocalDate) : AgendaRow
    data class Event(val event: CalendarEvent) : AgendaRow
    data class Empty(val date: LocalDate) : AgendaRow
}

private fun buildAgenda(selectedDate: LocalDate, events: List<CalendarEvent>): List<AgendaRow> {
    val byDay = events.groupBy { it.start.toLocalDate() }
    val rows = mutableListOf<AgendaRow>()

    // O dia selecionado sempre aparece — mesmo sem eventos.
    rows += AgendaRow.Header(selectedDate)
    val selectedEvents = byDay[selectedDate].orEmpty().sortedBy { it.start }
    if (selectedEvents.isEmpty()) {
        rows += AgendaRow.Empty(selectedDate)
    } else {
        selectedEvents.forEach { rows += AgendaRow.Event(it) }
    }

    // Depois dele, os próximos dias que tenham eventos (agenda contínua).
    for (offset in 1..AGENDA_DAYS_AHEAD) {
        val date = selectedDate.plusDays(offset.toLong())
        val dayEvents = byDay[date]?.sortedBy { it.start } ?: continue
        rows += AgendaRow.Header(date)
        dayEvents.forEach { rows += AgendaRow.Event(it) }
    }
    return rows
}

@Composable
fun CalendarAgenda(
    selectedDate: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    completions: List<Completion>,
    onCompletionChange: (CalendarEvent, String?) -> Unit,
    onEventClick: (CalendarEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = remember(selectedDate, events) { buildAgenda(selectedDate, events) }
    var scoring by remember { mutableStateOf<CalendarEvent?>(null) }

    // Relógio vivo: um bloco só pode ser concluído a partir do horário dele, e a
    // ação precisa aparecer sozinha quando a hora chega — sem sair e voltar da tela.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(20_000)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(rows) { row ->
            when (row) {
                is AgendaRow.Header -> DayHeaderRow(date = row.date, today = today)

                is AgendaRow.Event -> {
                    val event = row.event
                    val periodStart = event.completionPeriodStart?.toString()
                    val completion = if (event.blockId != null && periodStart != null) {
                        completions.firstOrNull {
                            it.blockId == event.blockId && it.periodStart == periodStart
                        }
                    } else {
                        null
                    }
                    EventRow(
                        event = event,
                        completionValue = completion?.value,
                        // Antes do horário de início não há o que concluir.
                        started = !event.start.isAfter(now),
                        onToggle = { checked ->
                            onCompletionChange(event, if (checked) "true" else null)
                        },
                        onScore = { scoring = event },
                        onClick = { onEventClick(event) },
                    )
                }

                is AgendaRow.Empty -> EmptyDayRow()
            }
        }
    }

    scoring?.let { event ->
        val periodStart = event.completionPeriodStart?.toString()
        val stored = completions.firstOrNull {
            it.blockId == event.blockId && it.periodStart == periodStart
        }?.value?.toIntOrNull() ?: 0
        val max = event.scoreMax.coerceAtLeast(2)

        ScoreDialog(
            initial = stored.coerceIn(0, max),
            max = max,
            color = event.color.accent(),
            onDismiss = { scoring = null },
            onConfirm = { value ->
                onCompletionChange(event, value.toString())
                scoring = null
            },
            onClear = {
                onCompletionChange(event, null)
                scoring = null
            },
        )
    }
}

@Composable
private fun DayHeaderRow(date: LocalDate, today: LocalDate) {
    Text(
        text = dayHeaderLabel(date, today),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = if (date == today) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    completionValue: String?,
    started: Boolean,
    onToggle: (Boolean) -> Unit,
    onScore: () -> Unit,
    onClick: () -> Unit,
) {
    val timeLabel = if (event.isAllDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        event.start.toLocalTime().hourMinute()
    }
    val tracked = event.blockId != null && event.confirmation != EventConfirmation.None
    val canComplete = tracked && started
    val done = completionValue == "true" || (completionValue?.toIntOrNull() ?: 0) > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeLabel,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(event.color.accent(), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.isUnforeseen) {
                    Icon(
                        painter = painterResource(R.drawable.ic_unforeseen),
                        contentDescription = stringResource(R.string.unforeseen_label),
                        tint = event.color.accent(),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
            }
            if (!event.location.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (canComplete) {
            if (event.confirmation == EventConfirmation.Score) {
                ScoreAction(
                    value = completionValue?.toIntOrNull()?.coerceAtMost(event.scoreMax),
                    max = event.scoreMax,
                    color = event.color.accent(),
                    onClick = onScore,
                )
            } else {
                CheckAction(
                    checked = completionValue == "true",
                    color = event.color.accent(),
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}

@Composable
private fun CheckAction(
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    IconButton(onClick = { onCheckedChange(!checked) }) {
        Icon(
            painter = painterResource(
                if (checked) R.drawable.ic_check_circle else R.drawable.ic_circle_outline,
            ),
            contentDescription = stringResource(
                if (checked) R.string.completion_uncheck else R.string.completion_check,
            ),
            modifier = Modifier.size(24.dp),
            tint = if (checked) color else color.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun ScoreAction(value: Int?, max: Int, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = if (value != null) "$value/$max" else "–",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (value != null) color else color.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun EmptyDayRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_event_busy),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.calendar_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.calendar_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
