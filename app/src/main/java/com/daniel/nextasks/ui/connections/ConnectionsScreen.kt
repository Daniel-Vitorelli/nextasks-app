package com.daniel.nextasks.ui.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.daniel.nextasks.data.BlockBlocker
import com.daniel.nextasks.data.Completion
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.Connection
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.ConnectionSuggestion
import com.daniel.nextasks.data.suggestConnections
import com.daniel.nextasks.data.DayFilter
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.Subtask
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.blockBlocker
import com.daniel.nextasks.data.countsFor
import com.daniel.nextasks.data.paceEstimate
import com.daniel.nextasks.ui.calendar.toEventColor
import com.daniel.nextasks.ui.components.rememberToday
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Uma conexão já resolvida para exibição (bloco + entidade + progresso). */
private data class ConnectionView(
    val connection: Connection,
    val routineName: String,
    val block: RoutineBlock?,
    val entityName: String,
    val entityKindRes: Int,
    val confirmed: Int,
    /** Nulo para hábitos (o "feito" depende da meta do dia). */
    val entityDone: Boolean?,
    /** Dias desde a última confirmação que conta (nulo = nunca contou). */
    val staleDays: Int?,
    /** Motivo permanente para a conexão nunca disparar (rotina inativa, terminada…). */
    val blocker: BlockBlocker? = null,
    /** Outras entidades ligadas ao mesmo bloco: a confirmação alimenta todas. */
    val sharesWith: List<String> = emptyList(),
    /** Dias estimados para bater a meta no ritmo atual (nulo = sem dados). */
    val estimateDays: Int? = null,
) {
    val satisfied: Boolean get() = confirmed >= connection.requiredCount
    val remaining: Int get() = (connection.requiredCount - confirmed).coerceAtLeast(0)
    val sortKey: String get() = routineName + (block?.startTime ?: "")
}

/**
 * Página de conexões: liga entidades (tarefa, sub-tarefa, hábito) a blocos de
 * tempo. Confirmar o bloco o suficiente conclui a entidade.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    connectionRepository: ConnectionRepository,
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    taskRepository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    habitRepository: HabitRepository,
    onOpenDrawer: () -> Unit,
    /** "Aplicar agora": força o efeito da conexão sem esperar as confirmações. */
    onApplyNow: (Connection) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val connections by connectionRepository.connections.collectAsState(initial = emptyList())
    val routines by routineRepository.routines.collectAsState(initial = emptyList())
    val completions by completionRepository.completions.collectAsState(initial = emptyList())
    val tasks by taskRepository.tasks.collectAsState(initial = emptyList())
    val subtasks by subtaskRepository.subtasks.collectAsState(initial = emptyList())
    val habits by habitRepository.habitList.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    val today = rememberToday()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf(ListFilter.All) }
    var deleteTarget by remember { mutableStateOf<Connection?>(null) }

    val deletedMessage = stringResource(R.string.connections_deleted)
    val undoLabel = stringResource(R.string.occurrence_undo)

    if (editorOpen) {
        ConnectionEditorScreen(
            routines = routines,
            tasks = tasks,
            subtasks = subtasks,
            habits = habits,
            existing = connections,
            initial = connections.firstOrNull { it.id == editingId },
            onBack = { editorOpen = false },
            onSave = { connection ->
                scope.launch { connectionRepository.save(connection) }
                editorOpen = false
            },
            onApplyNow = if (editingId == null) {
                null
            } else {
                { connection ->
                    scope.launch {
                        connectionRepository.save(connection)
                        onApplyNow(connection)
                    }
                    editorOpen = false
                }
            },
        )
        return
    }

    val views = remember(connections, routines, tasks, subtasks, habits, completions) {
        buildViews(connections, routines, tasks, subtasks, habits, completions, today)
    }
    val suggestions = remember(routines, tasks, subtasks, habits, connections) {
        suggestConnections(routines, tasks, subtasks, habits, connections)
    }
    val satisfied = views.count { it.satisfied }
    val entities = views.mapNotNull { it.connection.entityId }.toSet().size
    val visible = when (filter) {
        ListFilter.All -> views
        ListFilter.Pending -> views.filterNot { it.satisfied }
        ListFilter.Satisfied -> views.filter { it.satisfied }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_connections),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = stringResource(R.string.open_menu),
                            modifier = Modifier.size(24.dp),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingId = null
                    editorOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.connections_new),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            Text(
                text = stringResource(R.string.connections_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )

            StatsCard(
                total = views.size,
                satisfied = satisfied,
                pending = views.size - satisfied,
                entities = entities,
            )

            Spacer(Modifier.height(16.dp))

            if (suggestions.isNotEmpty()) {
                SuggestionCard(
                    suggestions = suggestions,
                    onConnect = { suggestion ->
                        scope.launch {
                            connectionRepository.save(
                                Connection(
                                    routineId = suggestion.routineId,
                                    blockId = suggestion.blockId,
                                    taskId = suggestion.taskId,
                                    subtaskId = suggestion.subtaskId,
                                    habitId = suggestion.habitId,
                                    requiredCount = suggestion.suggestedGoal,
                                ),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            if (views.isEmpty()) {
                EmptyState(onCreate = { editorOpen = true })
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListFilter.entries.forEach { option ->
                        val label = when (option) {
                            ListFilter.All -> R.string.connections_filter_all
                            ListFilter.Pending -> R.string.connections_filter_pending
                            ListFilter.Satisfied -> R.string.connections_filter_done
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(stringResource(label)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
                if (visible.isEmpty()) {
                    HintBox(stringResource(R.string.connections_filter_empty))
                }
                visible.groupBy { it.routineName }.forEach { (routineName, items) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = routineName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.connections_group_count, items.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { view ->
                            ConnectionCard(
                                view = view,
                                onOpen = {
                                    editingId = view.connection.id
                                    editorOpen = true
                                },
                                onDelete = { deleteTarget = view.connection },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.connections_delete_title)) },
            text = { Text(stringResource(R.string.connections_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            connectionRepository.delete(target.id)
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short,
                            )
                            // Desfazer devolve o vínculo exatamente como era.
                            if (result == SnackbarResult.ActionPerformed) {
                                connectionRepository.save(target)
                            }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.connections_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatsCard(total: Int, satisfied: Int, pending: Int, entities: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatCell(total, stringResource(R.string.connections_stat_total), Modifier.weight(1f))
                StatDivider()
                StatCell(satisfied, stringResource(R.string.connections_stat_satisfied), Modifier.weight(1f))
                StatDivider()
                StatCell(pending, stringResource(R.string.connections_stat_pending), Modifier.weight(1f))
                StatDivider()
                StatCell(entities, stringResource(R.string.connections_stat_entities), Modifier.weight(1f))
            }
            if (total > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (satisfied > 0) {
                        Box(
                            modifier = Modifier
                                .weight(satisfied.toFloat())
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    if (pending > 0) {
                        Box(
                            modifier = Modifier
                                .weight(pending.toFloat())
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.connections_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.connections_empty_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.connections_how_1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.connections_how_2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.connections_how_3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onCreate) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.connections_new))
            }
        }
    }
}

@Composable
private fun ConnectionCard(view: ConnectionView, onOpen: () -> Unit, onDelete: () -> Unit) {
    val block = view.block
    val blockColor = block?.color?.toEventColor()?.accent() ?: MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(blockColor),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = view.routineName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = block?.title ?: stringResource(R.string.connections_missing),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (block != null) {
                        Text(
                            text = "${block.startTime} – ${block.endTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.connections_delete_title),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        when (view.entityKindRes) {
                            R.string.connections_entity_habit -> R.drawable.ic_flame
                            R.string.connections_entity_subtask -> R.drawable.ic_tasks
                            else -> R.drawable.ic_check_circle
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(view.entityKindRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = view.entityName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (view.entityDone == true) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.connections_entity_done),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        view.satisfied -> stringResource(R.string.connections_satisfied)
                        view.remaining == 1 -> stringResource(R.string.connections_remaining_one)
                        else -> stringResource(
                            R.string.connections_progress,
                            view.confirmed,
                            view.connection.requiredCount,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (view.satisfied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = {
                    (view.confirmed.toFloat() / view.connection.requiredCount.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (view.satisfied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            val filterLabel = when (val filter = DayFilter.parse(view.connection.dayFilter)) {
                DayFilter.All -> stringResource(R.string.connection_when_all)
                is DayFilter.Weekday -> stringResource(
                    R.string.connection_when_day,
                    stringResource(weekdayShortRes(filter.weekday)),
                )
                is DayFilter.Date -> filter.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
            val since = runCatching {
                LocalDate.parse(view.connection.createdOn).format(DateTimeFormatter.ofPattern("dd/MM"))
            }.getOrDefault(view.connection.createdOn)

            Text(
                text = "$filterLabel · ${stringResource(R.string.connections_since, since)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            view.staleDays?.let { days ->
                // Com um motivo permanente, "parada há N dias" só confunde.
                if (view.blocker == null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (days == 1) {
                            stringResource(R.string.connections_stale_one)
                        } else {
                            stringResource(R.string.connections_stale, days)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            view.blocker?.let { blocker ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = blockerLabel(blocker),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (view.sharesWith.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.connections_shares_with,
                        view.sharesWith.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (view.blocker == null) {
                view.estimateDays?.let { days ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.connections_estimate_pace, days),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Texto do motivo pelo qual a conexão não vai disparar. */
@Composable
private fun blockerLabel(blocker: BlockBlocker): String = when (blocker) {
    BlockBlocker.RoutineInactive -> stringResource(R.string.connections_blocker_inactive)
    BlockBlocker.RoutineEnded -> stringResource(R.string.connections_blocker_ended)
    BlockBlocker.RoutineNotStarted -> stringResource(R.string.connections_blocker_not_started)
    BlockBlocker.InvalidTime -> stringResource(R.string.connections_blocker_invalid_time)
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

/** Resolve as conexões em cartões prontos, ordenados por rotina e horário. */
private fun buildViews(
    connections: List<Connection>,
    routines: List<Routine>,
    tasks: List<Task>,
    subtasks: List<Subtask>,
    habits: List<Habit>,
    completions: List<Completion>,
    today: LocalDate,
): List<ConnectionView> {
    val blocks = routines.flatMap { routine -> routine.blocks.map { routine to it } }

    fun nameOf(connection: Connection): String? = when {
        connection.taskId != null -> tasks.firstOrNull { it.id == connection.taskId }?.title
        connection.subtaskId != null -> subtasks.firstOrNull { it.id == connection.subtaskId }?.title
        connection.habitId != null -> habits.firstOrNull { it.id == connection.habitId }?.name
        else -> null
    }

    return connections.mapNotNull { connection ->
        val block = blocks.firstOrNull { it.second.id == connection.blockId }
        if (block == null) return@mapNotNull null

        val entity = when {
            connection.taskId != null -> tasks.firstOrNull { it.id == connection.taskId }
                ?.let { Triple(it.title, R.string.connections_entity_task, it.done) }

            connection.subtaskId != null -> subtasks.firstOrNull { it.id == connection.subtaskId }
                ?.let { Triple(it.title, R.string.connections_entity_subtask, it.done) }

            connection.habitId != null -> habits.firstOrNull { it.id == connection.habitId }
                ?.let { Triple(it.name, R.string.connections_entity_habit, null) }

            else -> null
        } ?: return@mapNotNull null

        ConnectionView(
            connection = connection,
            routineName = block.first.name,
            block = block.second,
            entityName = entity.first,
            entityKindRes = entity.second,
            confirmed = completions.count { connection.countsFor(it) },
            entityDone = entity.third,
            staleDays = staleDays(connection, completions, today),
            blocker = blockBlocker(block.first, block.second, today),
            sharesWith = connections
                .filter { it.id != connection.id && it.blockId == connection.blockId }
                .mapNotNull { nameOf(it) },
            estimateDays = paceEstimate(connection, completions, today),
        )
    }.sortedBy { it.sortKey }
}

/** Filtro de status da lista. */
private enum class ListFilter { All, Pending, Satisfied }

@Composable
private fun HintBox(text: String) {
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

/** Cartões de sugestão: um toque cria a conexão. */
@Composable
private fun SuggestionCard(
    suggestions: List<ConnectionSuggestion>,
    onConnect: (ConnectionSuggestion) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.connections_suggestions).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.connections_suggestions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, bottom = 8.dp),
            )
            if (suggestions.size > 1) {
                TextButton(onClick = { suggestions.forEach(onConnect) }) {
                    Text(
                        text = stringResource(R.string.connections_connect_all),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_link),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${suggestion.blockTitle} → ${suggestion.entityName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (suggestion.suggestedGoal > 1) {
                                    stringResource(
                                        R.string.connections_suggestion_goal,
                                        suggestion.routineName,
                                        suggestion.suggestedGoal,
                                    )
                                } else {
                                    suggestion.routineName
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onConnect(suggestion) }) {
                            Text(stringResource(R.string.connections_connect))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dias sem confirmação que conta, ou nulo se a conexão está saudável.
 *
 * Só sinaliza depois de uma semana: antes disso, "parada" seria só o começo.
 */
private fun staleDays(
    connection: Connection,
    completions: List<Completion>,
    today: LocalDate,
): Int? {
    val periods = completions
        .filter { connection.countsFor(it) }
        .mapNotNull { runCatching { LocalDate.parse(it.periodStart) }.getOrNull() }

    if (periods.size >= connection.requiredCount) return null

    val reference = periods.maxOrNull()
        ?: runCatching { LocalDate.parse(connection.createdOn) }.getOrNull()
        ?: return null

    val days = ChronoUnit.DAYS.between(reference, today).toInt()
    return days.takeIf { it >= 7 }
}
