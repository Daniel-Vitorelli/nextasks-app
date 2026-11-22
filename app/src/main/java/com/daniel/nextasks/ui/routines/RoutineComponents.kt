package com.daniel.nextasks.ui.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.ui.calendar.toEventColor

/** Linha de um bloco de horário dentro do detalhe da rotina. */
@Composable
fun BlockRow(
    block: RoutineBlock,
    showWeekday: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(block.color.toEventColor().accent()),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(blockTimeLabel(block, showWeekday))
                        when (block.confirmation) {
                            EventConfirmation.Checklist -> {
                                append(" · ")
                                append(stringResource(R.string.completion_checklist))
                            }

                            EventConfirmation.Score -> {
                                append(" · ")
                                append(stringResource(R.string.block_score_range, block.scoreMax))
                            }

                            EventConfirmation.None -> Unit
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!block.description.isNullOrBlank()) {
                    Text(
                        text = block.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.block_delete),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Dia inteiro", "08:00 – 09:00" ou "S • 08:00 – 09:00" em rotinas semanais. */
@Composable
internal fun blockTimeLabel(block: RoutineBlock, showWeekday: Boolean): String {
    if (block.isAllDay) return stringResource(R.string.calendar_all_day)
    val range = stringResource(R.string.block_time_range, block.startTime, block.endTime)
    return if (showWeekday) {
        "${WeekdayLetters[block.weekday.coerceIn(0, 6)]} • $range"
    } else {
        range
    }
}

/** Cabeçalho de resumo da rotina, usado no detalhe. */
@Composable
internal fun RoutineSummary(routine: Routine, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (!routine.description.isNullOrBlank()) {
            Text(
                text = routine.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                stringResource(
                    if (routine.frequency == RoutineFrequency.Daily) {
                        R.string.routine_daily
                    } else {
                        R.string.routine_weekly
                    },
                ),
            )
            Pill(
                if (routine.duration == com.daniel.nextasks.data.RoutineDuration.Until &&
                    routine.endDate != null
                ) {
                    stringResource(R.string.routine_until_date, formatIsoDate(routine.endDate))
                } else {
                    stringResource(R.string.routine_indefinite)
                },
            )
            if (!routine.isActive) Pill(stringResource(R.string.routine_inactive))
        }
    }
}

@Composable
internal fun Pill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun EmptyBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.blocks_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.blocks_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
