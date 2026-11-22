package com.daniel.nextasks.ui.tasks

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.ui.components.DateField
import com.daniel.nextasks.data.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formulário de tarefa em tela cheia, no mesmo padrão do formulário de rotina.
 *
 * Campos iguais aos do app web: título, descrição, prioridade (1 a 6) e data limite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    initial: Task?,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: Task.DEFAULT_PRIORITY) }
    var startDate by rememberSaveable { mutableStateOf(initial?.startDate) }
    var dueDate by rememberSaveable { mutableStateOf(initial?.dueDate) }
    // 0 = fechado, 1 = data de início, 2 = data limite
    var pickerTarget by rememberSaveable { mutableStateOf(0) }
    var touched by rememberSaveable { mutableStateOf(false) }

    val titleInvalid = title.isBlank()
    // Datas ISO comparam como texto na ordem cronológica.
    val dateRangeInvalid = startDate != null && dueDate != null && startDate!! > dueDate!!

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
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                if (initial == null) R.string.task_new else R.string.task_edit,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.task_cancel),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = !titleInvalid && !dateRangeInvalid,
                            onClick = {
                                touched = true
                                val base = initial ?: Task(title = title.trim())
                                onSave(
                                    base.copy(
                                        title = title.trim(),
                                        description = description.trim().ifBlank { null },
                                        priority = priority,
                                        startDate = startDate,
                                        dueDate = dueDate,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.task_save))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            touched = true
                        },
                        label = { Text(stringResource(R.string.task_title_label)) },
                        placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
                        singleLine = true,
                        isError = touched && titleInvalid,
                        supportingText = if (touched && titleInvalid) {
                            { Text(stringResource(R.string.task_title_required)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.task_description_label)) },
                        placeholder = {
                            Text(stringResource(R.string.task_description_placeholder))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.task_priority),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Task.PRIORITIES.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { level ->
                                val color = priorityColor(level)
                                FilterChip(
                                    selected = priority == level,
                                    onClick = { priority = level },
                                    label = {
                                        Text(
                                            text = stringResource(priorityLabel(level)),
                                            maxLines = 1,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.16f),
                                        selectedLabelColor = color,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    DateField(
                        label = stringResource(R.string.task_start_date),
                        value = startDate,
                        onPick = { pickerTarget = 1 },
                        onClear = { startDate = null },
                        clearLabel = stringResource(R.string.task_start_date_clear),
                    )

                    Spacer(Modifier.height(12.dp))

                    DateField(
                        label = stringResource(R.string.task_due_date),
                        value = dueDate,
                        onPick = { pickerTarget = 2 },
                        onClear = { dueDate = null },
                        clearLabel = stringResource(R.string.task_due_date_clear),
                    )

                    if (dateRangeInvalid) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.task_date_range_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (pickerTarget != 0) {
        val current = if (pickerTarget == 1) startDate else dueDate
        val pickerState = rememberDatePickerState(
            // Sem data escolhida, abre já no dia de hoje: senão o usuário pode
            // confirmar sem seleção e o campo termina vazio, sem aviso.
            initialSelectedDateMillis = (
                current?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                )
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerTarget = 0 },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        val picked = pickerState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        if (pickerTarget == 1) startDate = picked else dueDate = picked
                        pickerTarget = 0
                    },
                ) {
                    Text(stringResource(R.string.task_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = 0 }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
internal fun CreateTaskButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.task_create))
    }
}
