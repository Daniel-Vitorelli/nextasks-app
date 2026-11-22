package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R

/**
 * Ações de uma ocorrência do calendário.
 *
 * Deixa explícito que a alteração vale só para aquele dia: a rotina em si não
 * muda na tela de Rotinas.
 */
@Composable
fun OccurrenceActionsDialog(
    event: CalendarEvent,
    isOverridden: Boolean,
    isExtra: Boolean = event.isExtra,
    isEvent: Boolean = event.isEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeLabel = if (event.isAllDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        "${event.start.toLocalTime().hourMinute()} – ${event.end.toLocalTime().hourMinute()}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = listOfNotNull(timeLabel, event.location).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                ActionRow(
                    icon = R.drawable.ic_edit,
                    label = stringResource(
                        when {
                            isEvent -> R.string.event_edit_action
                            isExtra -> R.string.occurrence_edit_extra
                            else -> R.string.occurrence_edit
                        },
                    ),
                    onClick = onEdit,
                )
                ActionRow(
                    icon = R.drawable.ic_delete,
                    label = stringResource(
                        when {
                            isEvent -> R.string.event_delete
                            isExtra -> R.string.occurrence_delete_extra
                            else -> R.string.occurrence_delete
                        },
                    ),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
                if (isOverridden && !isExtra && !isEvent) {
                    ActionRow(
                        icon = R.drawable.ic_repeat,
                        label = stringResource(R.string.occurrence_restore),
                        onClick = onRestore,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routine_cancel))
            }
        },
    )
}

@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}
