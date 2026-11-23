package com.daniel.nextasks.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineDuration
import com.daniel.nextasks.data.RoutineFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formulário de criação/edição de rotina, em tela cheia — os mesmos campos do
 * app web: nome, descrição, frequência e duração.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFormDialog(
    initial: Routine?,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var frequency by rememberSaveable {
        mutableStateOf(initial?.frequency ?: RoutineFrequency.Daily)
    }
    var duration by rememberSaveable {
        mutableStateOf(initial?.duration ?: RoutineDuration.Indefinite)
    }
    var endDate by rememberSaveable { mutableStateOf(initial?.endDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }

    val nameError = nameTouched && name.isBlank()
    // "Até" sem data virava "Indefinida" sem aviso: `coversDay` trata endDate nulo
    // como "nunca acaba". Melhor bloquear o salvar do que gravar outra coisa.
    val dateMissing = duration == RoutineDuration.Until && endDate == null

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
                                if (initial == null) R.string.routine_new else R.string.routine_edit,
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
                            enabled = name.isNotBlank() && !dateMissing,
                            onClick = {
                                val base = initial ?: Routine(name = name.trim())
                                onSave(
                                    base.copy(
                                        name = name.trim(),
                                        description = description.trim().ifBlank { null },
                                        frequency = frequency,
                                        duration = duration,
                                        endDate = if (duration == RoutineDuration.Until) endDate else null,
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameTouched = true
                        },
                        label = { Text(stringResource(R.string.routine_name_label)) },
                        placeholder = { Text(stringResource(R.string.routine_name_placeholder)) },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.routine_name_required)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.routine_description_label)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionLabel(stringResource(R.string.routine_frequency))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = frequency == RoutineFrequency.Daily,
                            onClick = { frequency = RoutineFrequency.Daily },
                            label = { Text(stringResource(R.string.routine_daily)) },
                        )
                        FilterChip(
                            selected = frequency == RoutineFrequency.Weekly,
                            onClick = { frequency = RoutineFrequency.Weekly },
                            label = { Text(stringResource(R.string.routine_weekly)) },
                        )
                    }

                    SectionLabel(stringResource(R.string.routine_duration))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = duration == RoutineDuration.Indefinite,
                            onClick = { duration = RoutineDuration.Indefinite },
                            label = { Text(stringResource(R.string.routine_indefinite)) },
                        )
                        FilterChip(
                            selected = duration == RoutineDuration.Until,
                            onClick = { duration = RoutineDuration.Until },
                            label = { Text(stringResource(R.string.routine_until)) },
                        )
                    }

                    if (duration == RoutineDuration.Until) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = endDate?.let { stringResource(R.string.routine_until_date, formatIsoDate(it)) }
                                    ?: stringResource(R.string.routine_pick_date),
                            )
                        }
                        if (dateMissing) {
                            Text(
                                text = stringResource(R.string.routine_end_date_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            // Sem data escolhida, abre já no dia de hoje: senão o usuário pode
            // confirmar sem seleção e o campo termina vazio, sem aviso.
            initialSelectedDateMillis = (
                endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                )
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            endDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.routine_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.routine_cancel))
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
