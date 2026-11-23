package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.EventColorKey
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.SuspensionMode
import com.daniel.nextasks.data.coversDay
import com.daniel.nextasks.ui.components.DateField
import com.daniel.nextasks.ui.components.TimeField
import com.daniel.nextasks.ui.components.TimePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Formulário de evento, com duração de vários dias.
 *
 * As pontas são independentes: o dia de início pode ser dia inteiro ou ter hora,
 * e o dia de término idem. Em evento de um dia só, um único interruptor controla
 * as duas pontas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormDialog(
    initial: AgendaEvent?,
    defaultDate: LocalDate,
    routine: Routine?,
    onDismiss: () -> Unit,
    onSave: (AgendaEvent) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var startDate by rememberSaveable {
        mutableStateOf(initial?.startDate ?: defaultDate.toString())
    }
    var endDate by rememberSaveable {
        mutableStateOf(initial?.endDate ?: defaultDate.toString())
    }
    var startAllDay by rememberSaveable { mutableStateOf(initial?.startTime == null) }
    var endAllDay by rememberSaveable { mutableStateOf(initial?.endTime == null) }
    var startTime by rememberSaveable { mutableStateOf(initial?.startTime ?: "09:00") }
    var endTime by rememberSaveable { mutableStateOf(initial?.endTime ?: "10:00") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: EventColorKey.Blue) }
    var suspension by rememberSaveable {
        mutableStateOf(initial?.suspension ?: SuspensionMode.None)
    }
    var pickerTarget by rememberSaveable { mutableIntStateOf(0) }

    val singleDay = startDate == endDate
    val timeInvalid = singleDay && !startAllDay && !endAllDay &&
        (runCatching { LocalTime.parse(endTime) }.getOrNull() ?: LocalTime.MIN) <=
        (runCatching { LocalTime.parse(startTime) }.getOrNull() ?: LocalTime.MAX)

    val preview = remember(
        startDate, endDate, startAllDay, endAllDay, startTime, endTime, suspension, routine,
    ) {
        countSuspendedBlocks(
            routine = routine,
            draft = AgendaEvent(
                title = "",
                startDate = startDate,
                endDate = endDate,
                startTime = if (startAllDay) null else startTime,
                endTime = if (endAllDay) null else endTime,
                suspension = suspension,
            ),
        )
    }

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
                                if (initial == null) R.string.event_new else R.string.event_edit,
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
                            enabled = title.isNotBlank() && !timeInvalid,
                            onClick = {
                                onSave(
                                    (initial ?: AgendaEvent(
                                        title = "",
                                        startDate = startDate,
                                        endDate = endDate,
                                    )).copy(
                                        title = title.trim(),
                                        description = description.trim().ifBlank { null },
                                        startDate = startDate,
                                        endDate = endDate,
                                        startTime = if (startAllDay) null else startTime,
                                        endTime = if (endAllDay) null else endTime,
                                        color = color,
                                        suspension = suspension,
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
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.task_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.task_description_label)) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.event_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EventColorKey.entries.forEach { key ->
                            val fill = key.toEventColor().accent()
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(fill)
                                    .border(
                                        width = if (color == key) 2.dp else 0.dp,
                                        color = if (color == key) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { color = key },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionRow(
                        label = stringResource(R.string.event_start),
                        allDay = if (singleDay) startAllDay else startAllDay,
                        onAllDayChange = {
                            startAllDay = it
                            if (singleDay) endAllDay = it
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField(
                            label = stringResource(R.string.event_start),
                            value = startDate,
                            onPick = { pickerTarget = 1 },
                            onClear = {},
                            clearLabel = "",
                            modifier = Modifier.weight(1f),
                        )
                        if (!startAllDay) {
                            TimeField(
                                label = stringResource(R.string.block_start),
                                value = startTime,
                                onClick = { pickerTarget = 3 },
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    if (!singleDay) {
                        SectionRow(
                            label = stringResource(R.string.event_end),
                            allDay = endAllDay,
                            onAllDayChange = { endAllDay = it },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField(
                            label = stringResource(R.string.event_end),
                            value = endDate,
                            onPick = { pickerTarget = 2 },
                            onClear = {},
                            clearLabel = "",
                            modifier = Modifier.weight(1f),
                        )
                        if (!endAllDay) {
                            TimeField(
                                label = stringResource(R.string.block_end),
                                value = endTime,
                                onClick = { pickerTarget = 4 },
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }

                    if (timeInvalid) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.event_time_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.event_suspension),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SuspensionMode.None to R.string.suspension_none,
                            SuspensionMode.WholeDays to R.string.suspension_days,
                            SuspensionMode.WithinHours to R.string.suspension_hours,
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = suspension == mode,
                                onClick = { suspension = mode },
                                label = { Text(stringResource(label)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when {
                            suspension == SuspensionMode.None ->
                                stringResource(R.string.event_suspension_preview_none)

                            preview == 1 ->
                                stringResource(R.string.event_suspension_preview_one, preview)

                            else -> stringResource(R.string.event_suspension_preview, preview)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (preview > 0 && suspension != SuspensionMode.None) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (pickerTarget != 0) {
        if (pickerTarget <= 2) {
            val current = if (pickerTarget == 1) startDate else endDate
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = runCatching { LocalDate.parse(current) }
                    .getOrNull()
                    ?.atStartOfDay(ZoneOffset.UTC)
                    ?.toInstant()
                    ?.toEpochMilli(),
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
                            if (picked != null) {
                                if (pickerTarget == 1) {
                                    val wasSingle = startDate == endDate
                                    startDate = picked
                                    // Evento de um dia acompanha a data de início.
                                    if (wasSingle || endDate < picked) endDate = picked
                                } else {
                                    endDate = if (picked < startDate) startDate else picked
                                }
                            }
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
        } else {
            val current = runCatching {
                LocalTime.parse(if (pickerTarget == 3) startTime else endTime)
            }.getOrDefault(LocalTime.of(9, 0))
            TimePickerDialog(
                initial = current,
                onDismiss = { pickerTarget = 0 },
                onConfirm = { time ->
                    val formatted = "%02d:%02d".format(time.hour, time.minute)
                    if (pickerTarget == 3) startTime = formatted else endTime = formatted
                    pickerTarget = 0
                },
            )
        }
    }
}

@Composable
private fun SectionRow(
    label: String,
    allDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.event_all_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = allDay, onCheckedChange = onAllDayChange)
    }
}

/**
 * Quantos blocos da rotina o evento esconderia — o número mostrado no formulário.
 *
 * Conta bloco a bloco, respeitando o dia da semana em rotinas semanais.
 */
private fun countSuspendedBlocks(routine: Routine?, draft: AgendaEvent): Int {
    if (routine == null || draft.suspension == SuspensionMode.None) return 0
    if (draft.endDate < draft.startDate) return 0

    val segments = draft.segments()
    var count = 0
    for (day in draft.days()) {
        // Mesma regra do motor de ocorrências: fora da vigência da rotina não há
        // bloco para esconder, então a prévia não pode contar esses dias.
        if (!routine.coversDay(day)) continue
        for (block in routine.blocks) {
            if (routine.frequency == RoutineFrequency.Weekly &&
                day.dayOfWeek.value % 7 != block.weekday
            ) {
                continue
            }
            when (draft.suspension) {
                SuspensionMode.None -> Unit
                SuspensionMode.WholeDays -> count += 1
                SuspensionMode.WithinHours -> {
                    val times = resolveTimes(day, block) ?: continue
                    val overlaps = segments.any {
                        it.start.isBefore(times.second) && times.first.isBefore(it.end)
                    }
                    if (overlaps) count += 1
                }
            }
        }
    }
    return count
}
