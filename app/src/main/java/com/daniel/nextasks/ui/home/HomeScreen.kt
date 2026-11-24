package com.daniel.nextasks.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.BlockOverrideRepository
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.EventKind
import com.daniel.nextasks.data.EventRepository
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.HabitFrequency
import com.daniel.nextasks.data.HabitKind
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.habitProgress
import com.daniel.nextasks.data.habitsForDay
import com.daniel.nextasks.data.habitPeriodCount
import com.daniel.nextasks.data.habitStreak
import com.daniel.nextasks.data.ExtraBlockRepository
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.RoutineUnit
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.TaskCompletion
import com.daniel.nextasks.data.TaskSortSettings
import com.daniel.nextasks.data.SuspensionMode
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.parseIsoDate
import com.daniel.nextasks.ui.calendar.CalendarEvent
import com.daniel.nextasks.ui.calendar.toEventColor
import com.daniel.nextasks.ui.habits.habitIconRes
import com.daniel.nextasks.ui.habits.habitTodayLabel
import com.daniel.nextasks.ui.calendar.fullDateLabel
import com.daniel.nextasks.ui.calendar.hourMinute
import com.daniel.nextasks.ui.calendar.routineOccurrences
import com.daniel.nextasks.ui.components.ScoreDialog
import com.daniel.nextasks.ui.components.dashedCard
import com.daniel.nextasks.ui.tasks.TaskBadge
import com.daniel.nextasks.ui.tasks.TaskFormDialog
import com.daniel.nextasks.ui.tasks.formatShortDate
import com.daniel.nextasks.ui.tasks.priorityColor
import com.daniel.nextasks.ui.tasks.priorityLabel
import com.daniel.nextasks.data.SubtaskNode
import com.daniel.nextasks.data.flattenSubtaskTree
import com.daniel.nextasks.ui.tasks.sortPendingTasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private const val HOME_TASK_LIMIT = 4

/**
 * Início: o dia de hoje, não o histórico.
 *
 * Adaptação da home do app web para mobile — lá a tela é um gráfico de 30 dias
 * com heatmap anual, que não cabe nem faz sentido no celular. Aqui a ordem é:
 * o bloco que está acontecendo agora, o progresso de hoje e a sequência.
 */
@Composable
fun HomeScreen(
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    taskRepository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    overrideRepository: BlockOverrideRepository,
    extraRepository: ExtraBlockRepository,
    eventRepository: EventRepository,
    habitRepository: HabitRepository,
    /** Conclusão de tarefa com a cascata das sub-tarefas. */
    taskCompletion: TaskCompletion,
    /** Nome configurado em Ajustes; vazio usa a saudação genérica. */
    userName: String = "",
    /** Ordem das tarefas escolhida em Ajustes. */
    taskSort: TaskSortSettings = TaskSortSettings(),
    /** Chamado depois de confirmar um bloco — as conexões reagem a isso. */
    onBlockConfirmed: (String, LocalDate) -> Unit = { _, _ -> },
    /** Desmarcar a confirmação: a conexão desfaz o que ela tinha concluído. */
    onBlockUnconfirmed: (String, LocalDate) -> Unit = { _, _ -> },
    /** Chamado depois de qualquer mudança de tarefa/sub-tarefa/hábito (XP). */
    onTaskChanged: (String) -> Unit = {},
    onSubtaskChanged: (String) -> Unit = {},
    onHabitChanged: (String, LocalDate) -> Unit = { _, _ -> },
    /** Chamado quando uma tarefa é concluída — idem. */
    onTaskDone: (String) -> Unit = {},
    onSubtaskDone: (String) -> Unit = {},
    onHabitDone: (String) -> Unit = {},
    onOpenRoutines: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenCalendar: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val routines by routineRepository.routines.collectAsState(initial = emptyList())
    val completions by completionRepository.completions.collectAsState(initial = emptyList())
    val tasks by taskRepository.tasks.collectAsState(initial = emptyList())
    val subtasks by subtaskRepository.subtasks.collectAsState(initial = emptyList())
    val overrides by overrideRepository.overrides.collectAsState(initial = emptyList())
    val extras by extraRepository.blocks.collectAsState(initial = emptyList())
    val agendaEvents by eventRepository.events.collectAsState(initial = emptyList())
    val habits by habitRepository.habitList.collectAsState(initial = emptyList())
    val habitCompletions by habitRepository.completionList.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val now by rememberNow()
    val today = now.toLocalDate()
    val routine = routines.firstOrNull { it.isActive }

    val stats = remember(routine, completions, today, overrides, extras, agendaEvents) {
        routineStats(routine, completions, today, overrides, extras, agendaEvents)
    }

    // Só hoje e amanhã: o suficiente para "agora" e para o "a seguir".
    // Blocos avulsos aparecem mesmo sem rotina ativa.
    val events = remember(routine, today, overrides, extras, agendaEvents) {
        routineOccurrences(
            listOfNotNull(routine),
            listOf(today, today.plusDays(1)),
            overrides,
            extras,
            agendaEvents,
        )
    }

    // Evento de dia inteiro é contexto do dia (já aparece na faixa acima), não
    // ocupa o lugar de "acontecendo agora" — esse fica para o que tem horário.
    val current = remember(events, now) {
        events.firstOrNull {
            !now.isBefore(it.start) && now.isBefore(it.end) && !(it.isEvent && it.isAllDay)
        }
    }
    // "A seguir" é o próximo bloco com horário ainda hoje — um bloco de dia
    // inteiro (hoje ou amanhã) não é "a seguir", é o dia todo.
    val next = remember(events, now, today) {
        events.firstOrNull {
            it.start.isAfter(now) && !it.isAllDay && it.start.toLocalDate() == today
        }
    }

    // Pendentes por urgência (prazo + prioridade), como na home do web.
    val pendingSubtasks = remember(subtasks) {
        subtasks.filterNot { it.done }.groupingBy { it.taskId }.eachCount()
    }
    val pendingTasks = remember(tasks, today, taskSort, pendingSubtasks) {
        sortPendingTasks(tasks, today, taskSort, pendingSubtasks)
    }

    val todayEvents = remember(agendaEvents, today) {
        agendaEvents
            .filter { event ->
                // Data ilegível (backup remendado) não pode derrubar a Home.
                val start = event.start ?: return@filter false
                val end = event.end ?: start
                !today.isBefore(start) && !today.isAfter(end)
            }
            .sortedBy { it.startTime ?: "" }
    }

    val byKey = remember(completions) { completionMap(completions) }

    // Um mapa por hábito, montado uma vez por mudança nas marcações. Antes cada
    // linha filtrava e remontava a lista inteira dentro da composição — e o relógio
    // de 30 s recompõe a tela toda, então isso rodava o tempo inteiro.
    val habitCountsById = remember(habitCompletions) {
        habitCompletions
            .groupBy { it.habitId }
            .mapValues { (_, list) ->
                list.mapNotNull { completion ->
                    parseIsoDate(completion.date)?.let { it to completion.count }
                }.toMap()
            }
    }

    val todayHabits = remember(habits, today) { habitsForDay(habits, today) }

    // Tarefas que pedem atenção hoje: prazo de hoje ou já vencido. O resumo é um
    // retrato do dia, não a lista (a lista completa fica mais abaixo, com "ver todas").
    val tasksDueToday = remember(tasks, today) {
        tasks.count { task ->
            if (task.done) return@count false
            val due = task.dueDate?.let { parseIsoDate(it) } ?: return@count false
            !due.isAfter(today)
        }
    }

    // Hábito marcado = pelo menos uma marcação hoje (meta é assunto da linha do hábito).
    val habitsMarked = remember(todayHabits, habitCountsById, today) {
        todayHabits.count { habit -> (habitCountsById[habit.id]?.get(today) ?: 0) > 0 }
    }

    // Sequência sobre o histórico inteiro (limitado a 5 anos), uma vez por mudança.
    // A janela de 60 dias fazia uma sequência de 200 dias aparecer como 60.
    val habitStreaks = remember(todayHabits, habitCountsById, today) {
        todayHabits.associate { habit ->
            val counts = habitCountsById[habit.id].orEmpty()
            val from = today.minusDays(1825)
            habit.id to habitStreak(habitProgress(habit, counts, from, today), today).current
        }
    }

    // Árvore de sub-tarefas das tarefas que a Home mostra, montada uma vez só.
    // Antes era `flattenSubtaskTree` dentro da composição, a cada recomposição.
    val subtaskTrees = remember(subtasks, pendingTasks) {
        pendingTasks.take(HOME_TASK_LIMIT).associate { task ->
            task.id to flattenSubtaskTree(subtasks, task.id)
        }
    }

    fun valueOf(event: CalendarEvent): String? {
        val blockId = event.blockId ?: return null
        val period = event.completionPeriodStart?.toString() ?: return null
        return byKey[blockId to period]?.value
    }

    fun write(event: CalendarEvent, value: String?) {
        val blockId = event.blockId ?: return
        val period = event.completionPeriodStart ?: return
        scope.launch {
            completionRepository.set(blockId, period, value)
            if (value != null) {
                onBlockConfirmed(blockId, period)
            } else {
                onBlockUnconfirmed(blockId, period)
            }
        }
    }

    var scoring by remember { mutableStateOf<CalendarEvent?>(null) }
    var showChart by rememberSaveable { mutableStateOf(false) }
    var showHeatmap by rememberSaveable { mutableStateOf(false) }
    var showTaskForm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        HomeHeader(now = now, userName = userName)

        Spacer(Modifier.height(16.dp))

        // O retrato do dia vem antes de tudo: três números e um atalho para criar
        // tarefa. Cada número leva para a seção que o explica.
        DaySummaryCard(
            blocksDone = stats.todayDone,
            blocksTotal = stats.todayConfirmable,
            tasksToday = tasksDueToday,
            habitsDone = habitsMarked,
            habitsTotal = todayHabits.size,
            onOpenCalendar = onOpenCalendar,
            onOpenTasks = onOpenTasks,
            onOpenHabits = onOpenHabits,
            onNewTask = { showTaskForm = true },
        )

        Spacer(Modifier.height(24.dp))

        if (todayEvents.isNotEmpty()) {
            SectionLabel(stringResource(R.string.home_events))
            todayEvents.forEach { event ->
                HomeEventRow(event = event, today = today)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        val hasBlocksToday = events.any { it.start.toLocalDate() == today }

        when {
            routine == null && !hasBlocksToday -> HomeEmptyCard(
                icon = R.drawable.ic_repeat,
                title = stringResource(R.string.home_no_routine_title),
                body = stringResource(R.string.home_no_routine_body),
                action = stringResource(R.string.home_no_routine_action),
                onAction = onOpenRoutines,
            )

            routine != null && routine.blocks.isEmpty() && !hasBlocksToday -> HomeEmptyCard(
                icon = R.drawable.ic_schedule,
                title = stringResource(R.string.home_no_blocks_title),
                body = stringResource(R.string.home_no_blocks_body),
                action = stringResource(R.string.home_no_blocks_action),
                onAction = onOpenRoutines,
            )

            else -> {
                SectionLabel(stringResource(R.string.home_now))
                if (current != null) {
                    CurrentBlockCard(
                        event = current,
                        value = valueOf(current),
                        onChecklist = { checked ->
                            write(current, if (checked) "true" else null)
                        },
                        onScore = { scoring = current },
                    )
                    if (next != null) {
                        Spacer(Modifier.height(10.dp))
                        NextBlockRow(event = next)
                    }
                } else {
                    NothingNowCard(next = next, onClick = onOpenCalendar)
                }

                if (routine != null) {
                    Spacer(Modifier.height(24.dp))
                    DayStatsCard(
                        stats = stats,
                        onOpenChart = { showChart = true },
                        onOpenHeatmap = { showHeatmap = true },
                    )
                }

            }
        }

        if (todayHabits.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.home_habits))
            todayHabits.forEach { habit ->
                val habitCounts = habitCountsById[habit.id].orEmpty()
                val todayCount = habitCounts[today] ?: 0
                // Meta semanal se mede na semana; só hábito ruim conta o dia.
                val count = if (habit.kind == HabitKind.Good) {
                    habitPeriodCount(habit, habitCounts, today)
                } else {
                    todayCount
                }
                val streak = habitStreaks[habit.id] ?: 0

                // Marca e, se bateu a meta do período, avisa as conexões.
                suspend fun markHabit() {
                    val total = habitRepository.complete(habit, today)
                    onHabitChanged(habit.id, today)
                    if (habit.kind != HabitKind.Good || !habit.hasGoal) return
                    val target = habit.targetCount ?: return
                    val period = habitPeriodCount(
                        habit,
                        habitCounts + (today to total),
                        today,
                    )
                    if (period >= target) onHabitDone(habit.id)
                }

                HomeHabitRow(
                    habit = habit,
                    count = count,
                    streak = streak,
                    onToggle = {
                        scope.launch {
                            if (habit.kind == HabitKind.Bad) {
                                if (todayCount > 0) {
                                    habitRepository.undo(habit.id, today)
                                    onHabitChanged(habit.id, today)
                                } else {
                                    markHabit()
                                }
                            } else {
                                markHabit()
                            }
                        }
                    },
                    onLongPress = {
                        scope.launch {
                            habitRepository.undo(habit.id, today)
                            onHabitChanged(habit.id, today)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (habits.isNotEmpty() && todayHabits.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.home_habits))
            Text(
                text = stringResource(R.string.home_habits_none_today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Tarefas aparecem mesmo sem rotina ativa — são independentes dela.
        if (pendingTasks.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_tasks),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenTasks) {
                    Text(stringResource(R.string.home_tasks_all))
                }
            }
            Spacer(Modifier.height(4.dp))
            pendingTasks.take(HOME_TASK_LIMIT).forEach { task ->
                HomeTaskRow(
                    task = task,
                    today = today,
                    onToggle = {
                        scope.launch {
                            val becomingDone = !task.done
                            // Pelo TaskCompletion: concluir aqui também conclui as sub-tarefas.
                            taskCompletion.setDone(task.id, becomingDone)
                            if (becomingDone) onTaskDone(task.id)
                            onTaskChanged(task.id)
                        }
                    },
                    onClick = onOpenTasks,
                )
                // Árvore simplificada: dá para marcar sem abrir o detalhe.
                subtaskTrees[task.id].orEmpty().forEach { node ->
                    HomeSubtaskRow(
                        node = node,
                        onToggle = {
                            scope.launch {
                                val becomingDone = !node.subtask.done
                                val shouldBeDone = subtaskRepository.toggleDone(node.subtask.id)
                                if (shouldBeDone != null) {
                                    // Pelo TaskCompletion: reabrir a sub-tarefa reabre a tarefa.
                                    taskCompletion.setDone(task.id, shouldBeDone)
                                    if (becomingDone) {
                                        // A conexão da sub-tarefa reage mesmo quando a tarefa
                                        // ainda não fechou; a da tarefa, só quando ela fecha.
                                        onSubtaskDone(node.subtask.id)
                                        if (shouldBeDone && !task.done) onTaskDone(task.id)
                                    }
                                    onSubtaskChanged(node.subtask.id)
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showHeatmap) {
        HeatmapDialog(
            routine = routine,
            completions = completions,
            today = today,
            overrides = overrides,
            extras = extras,
            agendaEvents = agendaEvents,
            onDismiss = { showHeatmap = false },
        )
    }

    if (showChart) {
        ProgressChartDialog(
            routine = routine,
            completions = completions,
            today = today,
            overrides = overrides,
            extras = extras,
            agendaEvents = agendaEvents,
            onDismiss = { showChart = false },
        )
    }

    if (showTaskForm) {
        TaskFormDialog(
            initial = null,
            onDismiss = { showTaskForm = false },
            onSave = { task ->
                scope.launch { taskRepository.save(task) }
                showTaskForm = false
            },
        )
    }

    scoring?.let { event ->
        val max = event.scoreMax.coerceAtLeast(2)
        val stored = valueOf(event)?.toIntOrNull() ?: 0
        ScoreDialog(
            initial = stored.coerceIn(0, max),
            max = max,
            color = event.color.accent(),
            onDismiss = { scoring = null },
            onConfirm = { value ->
                write(event, value.toString())
                scoring = null
            },
            onClear = {
                write(event, null)
                scoring = null
            },
        )
    }
}

/** Relógio que atualiza a cada 30s: o bloco "agora" muda sozinho com o tempo. */
@Composable
private fun rememberNow(): androidx.compose.runtime.State<LocalDateTime> =
    produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(30_000)
        }
    }

@Composable
private fun HomeHeader(now: LocalDateTime, userName: String) {
    // "Bom dia" / "Boa tarde" / "Boa noite" — com o primeiro nome quando existe.
    val period = stringResource(
        when {
            now.hour < 12 -> R.string.home_greeting_morning
            now.hour < 18 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    val firstName = userName.trim().substringBefore(' ').trim()
    val greeting = if (firstName.isEmpty()) {
        period
    } else {
        stringResource(R.string.home_greeting_named, period, firstName)
    }

    Text(
        text = stringResource(R.string.home_eyebrow).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = greeting,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = now.toLocalDate().fullDateLabel(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun CardBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/**
 * Retrato do dia: blocos, tarefas e hábitos em três números.
 *
 * Veio no lugar de abrir a tela já no meio de uma lista: em um toque dá para ver
 * quanto falta e pular direto para a seção que interessa. O botão "+ tarefa" evita
 * a viagem até Tarefas só para criar uma.
 */
@Composable
private fun DaySummaryCard(
    blocksDone: Int,
    blocksTotal: Int,
    tasksToday: Int,
    habitsDone: Int,
    habitsTotal: Int,
    onOpenCalendar: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHabits: () -> Unit,
    onNewTask: () -> Unit,
) {
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(
                value = if (blocksTotal > 0) "$blocksDone/$blocksTotal" else "–",
                label = stringResource(R.string.home_summary_blocks),
                onClick = onOpenCalendar,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                value = tasksToday.toString(),
                label = stringResource(R.string.home_summary_tasks),
                onClick = onOpenTasks,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                value = if (habitsTotal > 0) "$habitsDone/$habitsTotal" else "–",
                label = stringResource(R.string.home_summary_habits),
                onClick = onOpenHabits,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        TextButton(
            onClick = onNewTask,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_quick_task),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SummaryTile(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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

/** Texto que fica legível sobre a cor do bloco. */
private fun onColor(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White

@Composable
private fun CurrentBlockCard(
    event: CalendarEvent,
    value: String?,
    onChecklist: (Boolean) -> Unit,
    onScore: () -> Unit,
) {
    val color = event.color.accent()
    val timeLabel = if (event.isAllDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        "${event.start.toLocalTime().hourMinute()} – ${event.end.toLocalTime().hourMinute()}"
    }

    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = event.location.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = event.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!event.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (event.confirmation != EventConfirmation.None) {
            Spacer(Modifier.height(16.dp))
            when (event.confirmation) {
                EventConfirmation.Checklist -> ChecklistAction(
                    done = value == "true",
                    color = color,
                    onCheck = { onChecklist(true) },
                    onUndo = { onChecklist(false) },
                )

                EventConfirmation.Score -> ScoreAction(
                    value = value?.toIntOrNull(),
                    max = event.scoreMax.coerceAtLeast(2),
                    color = color,
                    onClick = onScore,
                )

                EventConfirmation.None -> Unit
            }
        }
    }
}

@Composable
private fun ChecklistAction(
    done: Boolean,
    color: Color,
    onCheck: () -> Unit,
    onUndo: () -> Unit,
) {
    if (done) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_done),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.home_undo), color = color)
            }
        }
    } else {
        Button(
            onClick = onCheck,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = onColor(color),
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_confirm),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ScoreAction(
    value: Int?,
    max: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (value != null) {
                stringResource(R.string.home_score_value, value, max)
            } else {
                stringResource(R.string.home_score_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = onColor(color),
            ),
        ) {
            Text(
                text = stringResource(
                    if (value != null) R.string.home_score_change else R.string.home_score_give,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NextBlockRow(event: CalendarEvent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_next),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (event.isAllDay) {
                stringResource(R.string.calendar_all_day)
            } else {
                event.start.toLocalTime().hourMinute()
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NothingNowCard(next: CalendarEvent?, onClick: () -> Unit) {
    // Uma linha só: quando não há nada acontecendo, a tela não pode gastar meia
    // dobra de rolagem dizendo isso.
    CardBox {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_nothing_now),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (next == null) {
                        stringResource(R.string.home_no_more_today)
                    } else {
                        stringResource(
                            R.string.home_next_at,
                            next.title,
                            next.start.toLocalTime().hourMinute(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DayStatsCard(
    stats: RoutineStats,
    onOpenChart: () -> Unit,
    onOpenHeatmap: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fire = stats.currentStreak > 0

    CardBox {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                if (stats.todayConfirmable == 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.home_progress_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                } else {
                    val percent = stats.todayPercent ?: 0
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$percent",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.home_progress_count,
                                stats.todayDone,
                                stats.todayConfirmable,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            IconButton(onClick = onOpenChart, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chart),
                    contentDescription = stringResource(R.string.home_chart_open),
                    modifier = Modifier.size(18.dp),
                    tint = muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (fire) Color(0xFFF97316) else muted,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_streak),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
                if (stats.longestStreak > 0) {
                    Text(
                        text = stringResource(
                            R.string.home_streak_best,
                            pluralStringResource(
                                if (stats.streakUnit == RoutineUnit.Week) {
                                    R.plurals.home_weeks
                                } else {
                                    R.plurals.home_days
                                },
                                stats.longestStreak,
                                stats.longestStreak,
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = pluralStringResource(
                    if (stats.streakUnit == RoutineUnit.Week) {
                        R.plurals.home_weeks
                    } else {
                        R.plurals.home_days
                    },
                    stats.currentStreak,
                    stats.currentStreak,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (fire) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    muted
                },
            )
            IconButton(onClick = onOpenHeatmap, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_heatmap),
                    contentDescription = stringResource(R.string.home_heatmap_open),
                    modifier = Modifier.size(18.dp),
                    tint = muted,
                )
            }
        }

        // Sequência vazia ganha a linha inteira: no espaço ao lado do número a
        // mensagem sempre saía cortada ("Nenhuma semana completa a…").
        if (stats.longestStreak == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    if (stats.streakUnit == RoutineUnit.Week) {
                        R.string.home_streak_none_week
                    } else {
                        R.string.home_streak_none
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun HomeEmptyCard(
    icon: Int,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dashedCard(MaterialTheme.colorScheme.outline, 16.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) {
            Text(action)
        }
    }
}

/** Linha compacta de tarefa na Home: concluir sem sair da tela. */
@Composable
private fun HomeTaskRow(
    task: Task,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val due = formatShortDate(task.dueDate)
    val start = formatShortDate(task.startDate)
    val startDay = task.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // Ainda não começou: o que importa é quando começa, não o prazo.
    val notStarted = startDay != null && startDay.isAfter(today)
    val overdue = task.dueDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.isBefore(today) == true
    val color = priorityColor(task.priority)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskBadge(
                        text = stringResource(priorityLabel(task.priority)),
                        color = color,
                        container = color.copy(alpha = 0.14f),
                    )
                    if (notStarted && start != null) {
                        TaskBadge(
                            text = stringResource(R.string.task_start_badge, start),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            icon = R.drawable.ic_calendar,
                        )
                    } else if (due != null) {
                        TaskBadge(
                            text = stringResource(R.string.task_due_badge, due),
                            color = if (overdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            container = if (overdue) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            icon = R.drawable.ic_schedule,
                        )
                    }
                }
            }
        }
    }
}

/** Sub-tarefa na Home: só checkbox e título, indentada por nível. */
@Composable
private fun HomeSubtaskRow(node: SubtaskNode, onToggle: () -> Unit) {
    val subtask = node.subtask
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + node.depth * 18).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
            Icon(
                painter = painterResource(
                    if (subtask.done) R.drawable.ic_check_circle else R.drawable.ic_circle_outline,
                ),
                contentDescription = stringResource(R.string.task_toggle_done),
                modifier = Modifier.size(17.dp),
                tint = if (subtask.done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (subtask.done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Evento do dia na Home.
 *
 * Existe porque um evento de dia inteiro nunca seria o "bloco atual" — ele é
 * contexto do dia, não uma atividade com começo e fim dentro dele.
 */
@Composable
private fun HomeEventRow(event: AgendaEvent, today: LocalDate) {
    val accent = event.color.toEventColor().accent()

    val timeLabel = when {
        event.isSingleDay && event.startTime == null ->
            stringResource(R.string.home_event_all_day)

        event.isSingleDay -> stringResource(
            R.string.home_event_range,
            event.startTime.orEmpty(),
            event.endTime.orEmpty(),
        )

        else -> stringResource(
            R.string.home_event_range,
            formatShortDate(event.startDate).orEmpty(),
            formatShortDate(event.endDate).orEmpty(),
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.kind == EventKind.Unforeseen) {
                        Icon(
                            painter = painterResource(R.drawable.ic_unforeseen),
                            contentDescription = stringResource(R.string.unforeseen_label),
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val suspensionLabel = when {
                // Imprevisto suspende a rotina inteira no período: o aviso não fala
                // em "horário" para não sugerir que dá para fazer o bloco.
                event.kind == EventKind.Unforeseen -> R.string.home_event_suspended
                event.effectiveSuspension == SuspensionMode.WholeDays -> R.string.home_event_suspended
                event.effectiveSuspension == SuspensionMode.WithinHours ->
                    R.string.home_event_suspended_hours

                else -> null
            }
            if (suspensionLabel != null) {
                Text(
                    text = stringResource(suspensionLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Hábito do dia na Home: marca direto, sem abrir outra tela. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeHabitRow(
    habit: Habit,
    count: Int,
    streak: Int,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = habit.color.toEventColor().accent()
    val isBad = habit.kind == HabitKind.Bad
    val done = when {
        isBad -> count > 0
        !habit.hasGoal -> count > 0
        else -> count >= (habit.targetCount ?: 0)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(habitIconRes(habit.icon)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (streak > 0) {
                    Text(
                        text = if (streak == 1) {
                            stringResource(R.string.habit_streak_one)
                        } else {
                            stringResource(R.string.habit_streak, streak)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Pílula de ação: bons contam até a meta; ruins alternam a recaída.
            Surface(
                shape = RoundedCornerShape(50),
                color = if (done) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.combinedClickable(
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (done) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_circle),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = habitTodayLabel(habit, count),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isBad && count > 0 -> MaterialTheme.colorScheme.error
                            done -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
