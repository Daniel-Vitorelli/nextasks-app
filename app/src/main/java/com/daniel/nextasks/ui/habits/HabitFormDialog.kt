package com.daniel.nextasks.ui.habits

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.daniel.nextasks.data.EventColorKey
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.HabitFrequency
import com.daniel.nextasks.data.HabitKind
import com.daniel.nextasks.ui.calendar.toEventColor

private val WeekdayLetters = listOf("D", "S", "T", "Q", "Q", "S", "S")

/** Formulário de hábito: tipo, frequência, dias, meta, cor e ícone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormDialog(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: "target") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: EventColorKey.Green) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: HabitKind.Good) }
    var frequency by rememberSaveable {
        mutableStateOf(initial?.frequency ?: HabitFrequency.Daily)
    }
    var days by rememberSaveable {
        mutableStateOf(initial?.daysOfWeek ?: (0..6).toList())
    }
    var hasGoal by rememberSaveable { mutableStateOf(initial?.hasGoal ?: true) }
    var target by rememberSaveable { mutableStateOf(initial?.targetCount ?: 1) }
    var allowExceed by rememberSaveable { mutableStateOf(initial?.allowExceed ?: false) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }

    val showDays = kind == HabitKind.Good && frequency == HabitFrequency.Daily
    val nameInvalid = name.isBlank()

    // Um diálogo por vez: empilhar o seletor sobre o formulário fazia ele
    // aparecer atrás (duas janelas de diálogo).
    if (!showIconPicker) {
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
                                if (initial == null) R.string.habit_new else R.string.habit_edit,
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
                            enabled = !nameInvalid,
                            onClick = {
                                touched = true
                                val base = initial ?: Habit(name = name.trim())
                                onSave(
                                    base.copy(
                                        name = name.trim(),
                                        description = description.trim().ifBlank { null },
                                        icon = icon,
                                        color = color,
                                        kind = kind,
                                        frequency = frequency,
                                        daysOfWeek = if (showDays) days else (0..6).toList(),
                                        targetCount = if (hasGoal) {
                                            target.coerceAtLeast(1)
                                        } else {
                                            null
                                        },
                                        allowExceed = allowExceed && hasGoal &&
                                            kind == HabitKind.Good,
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
                        value = name,
                        onValueChange = {
                            name = it
                            touched = true
                        },
                        label = { Text(stringResource(R.string.habit_name_label)) },
                        placeholder = { Text(stringResource(R.string.habit_name_placeholder)) },
                        singleLine = true,
                        isError = touched && nameInvalid,
                        supportingText = if (touched && nameInvalid) {
                            { Text(stringResource(R.string.habit_name_required)) }
                        } else {
                            null
                        },
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
                    Label(stringResource(R.string.habit_kind))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            selected = kind == HabitKind.Good,
                            label = stringResource(R.string.habit_kind_good),
                            onClick = { kind = HabitKind.Good },
                        )
                        Chip(
                            selected = kind == HabitKind.Bad,
                            label = stringResource(R.string.habit_kind_bad),
                            onClick = { kind = HabitKind.Bad },
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Label(stringResource(R.string.habit_frequency))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            selected = frequency == HabitFrequency.Daily,
                            label = stringResource(R.string.habit_daily),
                            onClick = { frequency = HabitFrequency.Daily },
                        )
                        Chip(
                            selected = frequency == HabitFrequency.Weekly,
                            label = stringResource(R.string.habit_weekly),
                            onClick = { frequency = HabitFrequency.Weekly },
                        )
                    }

                    if (showDays) {
                        Spacer(Modifier.height(16.dp))
                        Label(stringResource(R.string.habit_days))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WeekdayLetters.forEachIndexed { index, letter ->
                                FilterChip(
                                    selected = index in days,
                                    onClick = {
                                        days = if (index in days) days - index else days + index
                                    },
                                    label = { Text(letter) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary
                                            .copy(alpha = 0.16f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.habit_goal_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = hasGoal, onCheckedChange = { hasGoal = it })
                    }

                    if (hasGoal) {
                        Spacer(Modifier.height(8.dp))
                        Label(
                            stringResource(
                                if (frequency == HabitFrequency.Weekly) {
                                    R.string.habit_target_weekly
                                } else {
                                    R.string.habit_target_daily
                                },
                            ),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (target > 1) target -= 1 },
                                enabled = target > 1,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_remove),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                text = target.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            IconButton(
                                onClick = { if (target < 99) target += 1 },
                                enabled = target < 99,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (kind == HabitKind.Good) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.habit_allow_exceed),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = allowExceed,
                                    onCheckedChange = { allowExceed = it },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Label(stringResource(R.string.habit_color))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EventColorKey.entries.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(key.toEventColor().accent())
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

                    Label(stringResource(R.string.habit_icon))
                    // A linha toda abre o seletor: alvo grande e óbvio.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showIconPicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(habitIconRes(icon)),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.habit_icon_change),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        }
    }

    if (showIconPicker) {
        HabitIconPickerDialog(
            selected = icon,
            onSelect = {
                icon = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Chip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
