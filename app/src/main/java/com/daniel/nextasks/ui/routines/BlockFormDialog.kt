package com.daniel.nextasks.ui.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.ui.components.TimeField
import com.daniel.nextasks.ui.components.TimePickerDialog
import com.daniel.nextasks.data.EventColorKey
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineBlockDays
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.scheduledWeekdays
import com.daniel.nextasks.ui.calendar.toEventColor
import java.time.LocalTime

/**
 * Formulário de bloco de horário: título, descrição, dias da semana (rotinas
 * semanais), horários, dia inteiro e cor.
 *
 * Um bloco pode estar em **vários** dias da semana — Seg/Qua/Sex compartilham o
 * mesmo horário, a cor e a confirmação.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockFormDialog(
    initial: RoutineBlock?,
    /** Rotina de origem; nulo quando o bloco é avulso (criado no calendário). */
    routine: Routine? = null,
    onDismiss: () -> Unit,
    onSave: (RoutineBlock) -> Unit,
    /** Dias da semana só fazem sentido ao editar a rotina, não uma ocorrência. */
    showWeekday: Boolean = routine?.frequency == RoutineFrequency.Weekly,
    /** Aviso no topo, usado quando a edição vale só para um dia. */
    notice: String? = null,
    /** Conteúdo extra logo abaixo do aviso (ex.: vínculo com a rotina). */
    extraContent: (@Composable () -> Unit)? = null,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var startTime by rememberSaveable { mutableStateOf(initial?.startTime ?: "08:00") }
    var endTime by rememberSaveable { mutableStateOf(initial?.endTime ?: "09:00") }
    var allDay by rememberSaveable { mutableStateOf(initial?.isAllDay ?: false) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: EventColorKey.Green) }
    // O bloco pode estar em vários dias: lista de 0..6, nunca vazia.
    var days by rememberSaveable {
        mutableStateOf(initial?.scheduledWeekdays ?: listOf(1))
    }
    var confirmation by rememberSaveable {
        mutableStateOf(initial?.confirmation ?: EventConfirmation.None)
    }
    var scoreMaxText by rememberSaveable {
        mutableStateOf((initial?.scoreMax ?: 5).toString())
    }
    val scoreMaxValue = scoreMaxText.toIntOrNull()
    val scoreMaxValid = scoreMaxValue != null && scoreMaxValue in 2..999
    val effectiveScoreMax = scoreMaxValue?.takeIf { it in 2..999 } ?: 5
    var pickerTarget by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false: sem isso a janela do diálogo para abaixo da
        // barra de status e a faixa aparece cinza (o scrim do app por trás).
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
                                if (initial == null) R.string.block_new else R.string.block_edit,
                            ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.routine_cancel),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = title.isNotBlank() &&
                                (confirmation != EventConfirmation.Score || scoreMaxValid),
                            onClick = {
                                val base = initial ?: RoutineBlock(title = title.trim())
                                onSave(
                                    base.copy(
                                        title = title.trim(),
                                        description = description.trim().ifBlank { null },
                                        startTime = startTime,
                                        endTime = endTime,
                                        isAllDay = allDay,
                                        color = color,
                                        // `weekday` acompanha para os dados antigos
                                        // continuarem legíveis por uma versão anterior.
                                        weekday = days.first(),
                                        weekdays = days,
                                        confirmation = confirmation,
                                        scoreMax = effectiveScoreMax,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.routine_save))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (notice != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    extraContent?.invoke()

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.block_title_label)) },
                        placeholder = { Text(stringResource(R.string.block_title_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.block_description_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (showWeekday) {
                        Text(
                            text = stringResource(R.string.block_weekday),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // FlowRow: os 7 chips não cabem numa linha só em tela estreita,
                        // e um `Row` com scroll esconderia os últimos dias. Os rótulos
                        // são completos ("Dom", "Seg"…): com uma letra só, S/T/Q se
                        // repetem e não dá para saber qual dia está marcado.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RoutineBlockDays.All.forEach { index ->
                                FilterChip(
                                    selected = index in days,
                                    // O último dia ligado não sai (`toggle` segura):
                                    // sem dia nenhum o bloco sumiria do calendário.
                                    onClick = { days = RoutineBlockDays.toggle(days, index) },
                                    label = { Text(stringResource(weekdayShortRes(index))) },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_all_day),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = allDay, onCheckedChange = { allDay = it })
                    }

                    if (!allDay) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeField(
                                label = stringResource(R.string.block_start),
                                value = startTime,
                                onClick = { pickerTarget = 1 },
                                modifier = Modifier.weight(1f),
                            )
                            TimeField(
                                label = stringResource(R.string.block_end),
                                value = endTime,
                                onClick = { pickerTarget = 2 },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.block_confirmation),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = confirmation == EventConfirmation.None,
                            onClick = { confirmation = EventConfirmation.None },
                            label = { Text(stringResource(R.string.completion_none)) },
                        )
                        FilterChip(
                            selected = confirmation == EventConfirmation.Checklist,
                            onClick = { confirmation = EventConfirmation.Checklist },
                            label = { Text(stringResource(R.string.completion_checklist)) },
                        )
                        FilterChip(
                            selected = confirmation == EventConfirmation.Score,
                            onClick = { confirmation = EventConfirmation.Score },
                            label = { Text(stringResource(R.string.completion_score)) },
                        )
                    }

                    if (confirmation == EventConfirmation.Score) {
                        OutlinedTextField(
                            value = scoreMaxText,
                            onValueChange = { input ->
                                scoreMaxText = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text(stringResource(R.string.block_score_max)) },
                            supportingText = {
                                Text(
                                    text = if (scoreMaxValid) {
                                        stringResource(R.string.block_score_hint, effectiveScoreMax)
                                    } else {
                                        stringResource(R.string.block_score_min)
                                    },
                                )
                            },
                            isError = !scoreMaxValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        text = stringResource(R.string.block_color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EventColorKey.entries.forEach { key ->
                            val selected = key == color
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(key.toEventColor().accent())
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { color = key },
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (pickerTarget != 0) {
        val current = runCatching {
            LocalTime.parse(if (pickerTarget == 1) startTime else endTime)
        }.getOrDefault(LocalTime.of(8, 0))

        TimePickerDialog(
            initial = current,
            onDismiss = { pickerTarget = 0 },
            onConfirm = { time ->
                val formatted = "%02d:%02d".format(time.hour, time.minute)
                if (pickerTarget == 1) startTime = formatted else endTime = formatted
                pickerTarget = 0
            },
        )
    }
}

