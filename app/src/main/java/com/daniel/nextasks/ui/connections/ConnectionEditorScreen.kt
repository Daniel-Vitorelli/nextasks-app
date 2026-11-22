package com.daniel.nextasks.ui.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Connection
import com.daniel.nextasks.data.DayFilter
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.Subtask
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.ui.calendar.toEventColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.Instant
import java.util.UUID

private enum class EntityKind { Task, Subtask, Habit }

/**
 * Formulário de conexão: bloco, entidade, meta e filtro de dia.
 *
 * Uma tela só, em vez de passos: a lista de blocos e entidades é curta e o
 * usuário vê o que escolheu sem voltar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionEditorScreen(
    routines: List<Routine>,
    tasks: List<Task>,
    subtasks: List<Subtask>,
    habits: List<Habit>,
    existing: List<Connection>,
    onBack: () -> Unit,
    onSave: (Connection) -> Unit,
    modifier: Modifier = Modifier,
    /** Conexão sendo editada; nulo = criando uma nova. */
    initial: Connection? = null,
) {
    BackHandler(onBack = onBack)

    val blocks = remember(routines) {
        routines.flatMap { routine -> routine.blocks.map { routine to it } }
    }
    // Concluídas não fazem sentido como conexão: já foram feitas.
    val openTasks = remember(tasks) { tasks.filterNot { it.done } }
    val openSubtasks = remember(subtasks) { subtasks.filterNot { it.done } }

    val startKind = when {
        initial?.subtaskId != null -> EntityKind.Subtask
        initial?.habitId != null -> EntityKind.Habit
        else -> EntityKind.Task
    }

    var blockId by rememberSaveable(initial?.id) { mutableStateOf(initial?.blockId) }
    var kind by rememberSaveable(initial?.id) { mutableStateOf(startKind) }
    var entityId by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.taskId ?: initial?.subtaskId ?: initial?.habitId)
    }
    var requiredCount by rememberSaveable(initial?.id) {
        mutableIntStateOf(initial?.requiredCount ?: 1)
    }
    var weekday by rememberSaveable(initial?.id) {
        mutableStateOf((DayFilter.parse(initial?.dayFilter ?: "all") as? DayFilter.Weekday)?.weekday)
    }
    var dateIso by rememberSaveable(initial?.id) {
        mutableStateOf(
            (DayFilter.parse(initial?.dayFilter ?: "all") as? DayFilter.Date)?.date?.toString(),
        )
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val duplicate = blockId != null && entityId != null && existing.any { connection ->
        connection.id != initial?.id &&
                connection.blockId == blockId && when (kind) {
                EntityKind.Task -> connection.taskId == entityId
                EntityKind.Subtask -> connection.subtaskId == entityId
                EntityKind.Habit -> connection.habitId == entityId
            }
    }

    val routine = blocks.firstOrNull { it.second.id == blockId }?.first
    val weekly = routine?.frequency == RoutineFrequency.Weekly

    /** Meta que o app propõe: hábito com meta 3x pede 3; semanal sempre 1. */
    fun suggestedGoal(): Int = when {
        weekly -> 1
        kind == EntityKind.Habit -> habits.firstOrNull { it.id == entityId }?.targetCount ?: 1
        else -> 1
    }

    val canSave = blockId != null && entityId != null && !duplicate

    if (showDatePicker) {
        DatePickerSheet(
            onDismiss = { showDatePicker = false },
            onPick = { picked ->
                dateIso = picked.toString()
                weekday = null
                // Numa data específica só cabe uma confirmação.
                requiredCount = 1
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (initial == null) {
                                R.string.connection_new_title
                            } else {
                                R.string.connection_edit_title
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.settings_cancel),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            SectionLabel(stringResource(R.string.connection_step_1))

            if (blocks.isEmpty()) {
                HintCard(stringResource(R.string.connection_no_blocks))
            } else {
                Card {
                    var firstGroup = true
                    blocks.groupBy { it.first.name }.forEach { (routineName, group) ->
                        if (!firstGroup) Divider()
                        firstGroup = false
                        Text(
                            text = routineName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                        )
                        group.forEachIndexed { index, (_, block) ->
                            BlockRow(
                                block = block,
                                selected = block.id == blockId,
                                onClick = { blockId = block.id },
                            )
                            if (index < group.lastIndex) Divider()
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.connection_step_2))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntityKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = {
                            kind = option
                            entityId = null
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        EntityKind.Task -> R.string.connection_tab_tasks
                                        EntityKind.Subtask -> R.string.connection_tab_subtasks
                                        EntityKind.Habit -> R.string.connection_tab_habits
                                    },
                                ),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (kind) {
                EntityKind.Task -> {
                    if (openTasks.isEmpty()) {
                        HintCard(stringResource(R.string.connection_no_entities))
                    } else {
                        Card {
                            openTasks.forEachIndexed { index, task ->
                                EntityRow(
                                    title = task.title,
                                    subtitle = null,
                                    selected = task.id == entityId,
                                    onClick = {
                                        entityId = task.id
                                        requiredCount = 1
                                    },
                                )
                                if (index < openTasks.lastIndex) Divider()
                            }
                        }
                    }
                }

                EntityKind.Subtask -> {
                    if (openSubtasks.isEmpty()) {
                        HintCard(stringResource(R.string.connection_no_entities))
                    } else {
                        Card {
                            openSubtasks.forEachIndexed { index, subtask ->
                                EntityRow(
                                    title = subtask.title,
                                    subtitle = tasks.firstOrNull { it.id == subtask.taskId }?.title,
                                    selected = subtask.id == entityId,
                                    onClick = {
                                        entityId = subtask.id
                                        requiredCount = 1
                                    },
                                )
                                if (index < openSubtasks.lastIndex) Divider()
                            }
                        }
                    }
                }

                EntityKind.Habit -> {
                    if (habits.isEmpty()) {
                        HintCard(stringResource(R.string.connection_no_entities))
                    } else {
                        Card {
                            habits.forEachIndexed { index, habit ->
                                EntityRow(
                                    title = habit.name,
                                    subtitle = null,
                                    selected = habit.id == entityId,
                                    onClick = {
                                        entityId = habit.id
                                        requiredCount = suggestedGoalFor(habit, weekly)
                                    },
                                )
                                if (index < habits.lastIndex) Divider()
                            }
                        }
                    }
                }
            }

            // Aviso: a mesma entidade já está ligada a outro bloco.
            val otherBlock = entityId?.let { id ->
                existing.firstOrNull { link ->
                    link.blockId != blockId && when (kind) {
                        EntityKind.Task -> link.taskId == id
                        EntityKind.Subtask -> link.subtaskId == id
                        EntityKind.Habit -> link.habitId == id
                    }
                }?.blockId
            }?.let { otherId -> blocks.firstOrNull { it.second.id == otherId }?.second?.title }

            if (otherBlock != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connection_other_block, otherBlock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SectionLabel(stringResource(R.string.connection_step_3))

            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.connection_goal_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.connection_goal_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    StepperButton(
                        icon = R.drawable.ic_remove,
                        enabled = requiredCount > Connection.REQUIRED_MIN,
                        onClick = { requiredCount -= 1 },
                    )
                    Text(
                        text = requiredCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(36.dp),
                    )
                    StepperButton(
                        icon = R.drawable.ic_add,
                        enabled = requiredCount < Connection.REQUIRED_MAX && dateIso == null,
                        onClick = { requiredCount += 1 },
                    )
                }
            }

            if (dateIso != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.connection_date_cap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            SectionLabel(stringResource(R.string.connection_step_4))

            // FlowRow: com 9 opções, a linha horizontal esconderia "Uma data".
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DayChip(
                    label = stringResource(R.string.connection_when_all),
                    selected = weekday == null,
                    onClick = { weekday = null },
                )
                (0..6).forEach { day ->
                    DayChip(
                        label = stringResource(weekdayShortRes(day)),
                        selected = weekday == day,
                        onClick = {
                            weekday = day
                            dateIso = null
                        },
                    )
                }
                DayChip(
                    label = dateIso?.let { formatShortDate(it) }
                        ?: stringResource(R.string.connection_when_pick_date),
                    selected = dateIso != null,
                    onClick = { showDatePicker = true },
                )
            }

            val summaryBlock = blocks.firstOrNull { it.second.id == blockId }?.second?.title
            val summaryEntity = when (kind) {
                EntityKind.Task -> openTasks.firstOrNull { it.id == entityId }?.title
                EntityKind.Subtask -> openSubtasks.firstOrNull { it.id == entityId }?.title
                EntityKind.Habit -> habits.firstOrNull { it.id == entityId }?.name
            }
            if (summaryBlock != null && summaryEntity != null) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = if (requiredCount == 1) {
                            stringResource(
                                R.string.connection_summary_one,
                                summaryBlock,
                                stringResource(articleRes(kind)),
                                summaryEntity,
                            )
                        } else {
                            stringResource(
                                R.string.connection_summary_many,
                                summaryBlock,
                                requiredCount,
                                stringResource(articleRes(kind)),
                                summaryEntity,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        requiredCount <= 1 -> stringResource(R.string.connection_estimate_first)
                        weekly -> stringResource(R.string.connection_estimate_weeks, requiredCount)
                        else -> stringResource(R.string.connection_estimate_days, requiredCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (duplicate) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.connection_duplicate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val routineId = blocks.firstOrNull { it.second.id == blockId }?.first?.id
                        ?: return@Button
                    onSave(
                        Connection(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            createdOn = initial?.createdOn ?: LocalDate.now().toString(),
                            routineId = routineId,
                            blockId = blockId ?: return@Button,
                            taskId = if (kind == EntityKind.Task) entityId else null,
                            subtaskId = if (kind == EntityKind.Subtask) entityId else null,
                            habitId = if (kind == EntityKind.Habit) entityId else null,
                            requiredCount = requiredCount,
                            dayFilter = when {
                                dateIso != null -> "date:$dateIso"
                                weekday != null -> "weekday:$weekday"
                                else -> "all"
                            },
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (initial == null) R.string.connection_save else R.string.connection_save_edit,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DatePickerSheet(
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** "2026-11-21" -> "21/11/2026". */
private fun formatShortDate(iso: String): String =
    runCatching {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(iso)

@Composable
private fun BlockRow(
    block: RoutineBlock,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(block.color.toEventColor().accent()),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${block.startTime} – ${block.endTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SelectionMark(selected)
    }
}

@Composable
private fun EntityRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SelectionMark(selected)
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun StepperButton(icon: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun HintCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

private fun weekdayShortRes(weekday: Int): Int = when (weekday) {
    0 -> R.string.weekday_short_0
    1 -> R.string.weekday_short_1
    2 -> R.string.weekday_short_2
    3 -> R.string.weekday_short_3
    4 -> R.string.weekday_short_4
    5 -> R.string.weekday_short_5
    else -> R.string.weekday_short_6
}

private fun articleRes(kind: EntityKind): Int = when (kind) {
    EntityKind.Task -> R.string.connection_entity_task_article
    EntityKind.Subtask -> R.string.connection_entity_subtask_article
    EntityKind.Habit -> R.string.connection_entity_habit_article
}

/** Meta do hábito, respeitando a limitação de rotina semanal. */
private fun suggestedGoalFor(habit: Habit, weekly: Boolean): Int =
    if (weekly) 1 else (habit.targetCount ?: 1)
