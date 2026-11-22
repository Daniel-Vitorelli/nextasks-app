package com.daniel.nextasks.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.ui.routines.BlockFormDialog
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.ExtraBlockRepository
import com.daniel.nextasks.data.EventRepository
import com.daniel.nextasks.data.BlockOverrideRepository
import com.daniel.nextasks.data.BlockOverride
import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.ui.theme.NexTasksTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private const val PAGE_COUNT = 1200
private const val BASE_PAGE = PAGE_COUNT / 2

/**
 * Tela de calendário no padrão do Google Calendar para Android.
 *
 * Visualizações: Programação (lista), Dia, 3 dias, Semana (grade de horários)
 * e Mês. A troca é feita pelo menu de três pontos da barra superior.
 *
 * A data selecionada é a única fonte de verdade; a tira de semana apenas
 * acompanha (e reporta) essa data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onOpenDrawer: () -> Unit,
    /** Chamado depois de confirmar um bloco — as conexões reagem a isso. */
    onBlockConfirmed: (String, LocalDate) -> Unit = { _, _ -> },
    /** Desmarcar a confirmação: a conexão desfaz o que ela tinha concluído. */
    onBlockUnconfirmed: (String, LocalDate) -> Unit = { _, _ -> },
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    overrideRepository: BlockOverrideRepository,
    extraRepository: ExtraBlockRepository,
    eventRepository: EventRepository,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val routines by routineRepository.routines.collectAsState(initial = emptyList())
    val completions by completionRepository.completions.collectAsState(initial = emptyList())
    val overrides by overrideRepository.overrides.collectAsState(initial = emptyList())
    val extras by extraRepository.blocks.collectAsState(initial = emptyList())
    val agendaEvents by eventRepository.events.collectAsState(initial = emptyList())
    val baseWeekStart = remember(today) { today.startOfWeek() }

    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    val pagerState = rememberPagerState(
        initialPage = BASE_PAGE +
            ChronoUnit.WEEKS.between(baseWeekStart, selectedDate.startOfWeek()).toInt(),
    ) { PAGE_COUNT }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf(CalendarViewMode.Schedule) }
    var lastNonMonthView by rememberSaveable { mutableStateOf(CalendarViewMode.Schedule) }
    // Zoom da grade (pinça de dois dedos). Mora na tela, e não na grade, para
    // sobreviver à troca de dia e à troca entre Dia / 3 dias / Semana.
    var gridZoom by rememberSaveable { mutableFloatStateOf(1f) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // Arrastar a tira troca de semana mantendo o dia da semana.
    val currentDate by rememberUpdatedState(selectedDate)
    LaunchedEffect(pagerState, baseWeekStart) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                val weekday = currentDate.dayOfWeek.value % 7
                val weekStart = baseWeekStart.plusWeeks((page - BASE_PAGE).toLong())
                selectedEpochDay = weekStart.plusDays(weekday.toLong()).toEpochDay()
            }
    }

    // Mudou a data por fora (seletor, arrasto, mês): a tira acompanha.
    LaunchedEffect(selectedEpochDay, baseWeekStart) {
        val target = BASE_PAGE +
            ChronoUnit.WEEKS.between(baseWeekStart, selectedDate.startOfWeek()).toInt()
        if (pagerState.settledPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // Eventos materializados a partir das rotinas, numa janela que cobre o mês
    // visível e os adjacentes (o mês pode ser arrastado).
    val events = remember(routines, selectedDate, overrides, extras, agendaEvents) {
        val firstOfMonth = selectedDate.withDayOfMonth(1)
        val from = firstOfMonth.minusMonths(1)
        val to = firstOfMonth.plusMonths(2).plusDays(7)
        val days = buildList {
            var day = from
            while (!day.isAfter(to)) {
                add(day)
                day = day.plusDays(1)
            }
        }
        routineOccurrences(routines, days, overrides, extras, agendaEvents)
    }

    val eventsByDay = remember(events) { events.groupBy { it.start.toLocalDate() } }
    var actionEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    // Bloco avulso: dia do formulário (nulo = fechado) e registro em edição.
    var extraFormDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingExtra by remember { mutableStateOf<ExtraBlock?>(null) }
    var linkToRoutine by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var eventFormDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingAgendaEvent by remember { mutableStateOf<AgendaEvent?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.occurrence_deleted)
    val undoLabel = stringResource(R.string.occurrence_undo)
    val extraDeletedMessage = stringResource(R.string.extra_deleted)

    val monthExpanded = viewMode == CalendarViewMode.Month ||
        (viewMode == CalendarViewMode.Schedule && expanded)

    val selectDate: (LocalDate) -> Unit = { date -> selectedEpochDay = date.toEpochDay() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            CalendarTopBar(
                monthLabel = selectedDate.monthYearLabel(),
                expanded = monthExpanded,
                viewMode = viewMode,
                onOpenDrawer = onOpenDrawer,
                onToggleMonth = {
                    when (viewMode) {
                        // Recolher volta para a visualização de onde veio.
                        CalendarViewMode.Month -> {
                            viewMode = lastNonMonthView
                            expanded = false
                        }

                        CalendarViewMode.Schedule -> expanded = !expanded

                        // Dia / 3 dias / Semana: abre o mês em tela cheia.
                        else -> {
                            lastNonMonthView = viewMode
                            viewMode = CalendarViewMode.Month
                        }
                    }
                },
                onSelectViewMode = { mode ->
                    viewMode = mode
                    expanded = false
                    if (mode != CalendarViewMode.Month) lastNonMonthView = mode
                },
                onGoToDate = { showDatePicker = true },
            )

            when (viewMode) {
                CalendarViewMode.Month -> {
                    CalendarMonthView(
                        month = selectedDate,
                        selectedDate = selectedDate,
                        today = today,
                        events = events,
                        onSelectDay = { date ->
                            selectDate(date)
                            // No Google Calendar, tocar um dia no mês abre o dia.
                            viewMode = CalendarViewMode.Day
                        },
                        onEventClick = { actionEvent = it },
                        onMonthChange = { newMonth ->
                            val day = selectedDate.dayOfMonth.coerceAtMost(newMonth.lengthOfMonth())
                            selectDate(newMonth.withDayOfMonth(day))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                CalendarViewMode.Schedule -> {
                    if (expanded) {
                        CalendarMonthGrid(
                            month = selectedDate,
                            selectedDate = selectedDate,
                            today = today,
                            eventDays = eventsByDay.keys,
                            onSelect = { date ->
                                selectDate(date)
                                expanded = false
                            },
                        )
                    } else {
                        CalendarWeekStrip(
                            pagerState = pagerState,
                            baseWeekStart = baseWeekStart,
                            basePage = BASE_PAGE,
                            selectedDate = selectedDate,
                            today = today,
                            onSelectDay = { date -> selectDate(date) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalSwipe(
                                onSwipeLeft = { selectDate(selectedDate.plusMonths(1)) },
                                onSwipeRight = { selectDate(selectedDate.plusMonths(-1)) },
                            ),
                    ) {
                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = { slideByDirection() },
                            label = "agenda",
                            modifier = Modifier.fillMaxSize(),
                        ) { date ->
                            CalendarAgenda(
                                selectedDate = date,
                                today = today,
                                events = events,
                                completions = completions,
                                onEventClick = { actionEvent = it },
                                onCompletionChange = { event, value ->
                                    val blockId = event.blockId
                                    val period = event.completionPeriodStart
                                    if (blockId != null && period != null) {
                                        scope.launch {
                                            completionRepository.set(blockId, period, value)
                                            if (value != null) {
                                                onBlockConfirmed(blockId, period)
                                            } else {
                                                onBlockUnconfirmed(blockId, period)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                else -> {
                    val periodDays = when (viewMode) {
                        CalendarViewMode.Day -> 1L
                        CalendarViewMode.ThreeDays -> 3L
                        else -> 7L
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalSwipe(
                                onSwipeLeft = { selectDate(selectedDate.plusDays(periodDays)) },
                                onSwipeRight = { selectDate(selectedDate.plusDays(-periodDays)) },
                            ),
                    ) {
                        AnimatedContent(
                            targetState = selectedDate,
                            transitionSpec = { slideByDirection() },
                            label = "grade",
                            modifier = Modifier.fillMaxSize(),
                        ) { date ->
                            val days = when (viewMode) {
                                CalendarViewMode.Day -> listOf(date)
                                CalendarViewMode.ThreeDays -> List(3) { date.plusDays(it.toLong()) }
                                else -> {
                                    val weekStart = date.startOfWeek()
                                    List(7) { weekStart.plusDays(it.toLong()) }
                                }
                            }
                            CalendarTimeGrid(
                                days = days,
                                events = events,
                                today = today,
                                selectedDate = date,
                                onSelectDay = { selectDate(it) },
                                onEventClick = { actionEvent = it },
                                zoom = gridZoom,
                                onZoom = { gridZoom = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = { fabMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.calendar_new_event),
                )
            }
            DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_block)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        fabMenu = false
                        editingExtra = null
                        linkToRoutine = routines.any { it.isActive }
                        extraFormDate = selectedDate
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_new_event)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        fabMenu = false
                        editingAgendaEvent = null
                        eventFormDate = selectedDate
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            selectDate(date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.calendar_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.calendar_cancel))
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
            )
        }
    }

    // Ações de uma ocorrência: valem só para aquele dia, nunca para a rotina.
    actionEvent?.let { event ->
        OccurrenceActionsDialog(
            event = event,
            isOverridden = event.isOverridden,
            onEdit = {
                if (event.isEvent) {
                    val stored = agendaEvents.firstOrNull { it.id == event.eventId }
                    if (stored != null) {
                        editingAgendaEvent = stored
                        eventFormDate = event.start.toLocalDate()
                    }
                } else if (event.isExtra) {
                    val extra = extras.firstOrNull { it.id == event.blockId }
                    if (extra != null) {
                        editingExtra = extra
                        linkToRoutine = extra.routineId != null
                        extraFormDate = event.start.toLocalDate()
                    }
                } else {
                    editingEvent = event
                }
                actionEvent = null
            },
            onDelete = {
                actionEvent = null
                val routineId = event.routineId
                val blockId = event.blockId
                if (event.isEvent) {
                    val id = event.eventId
                    if (id != null) {
                        scope.launch {
                            eventRepository.delete(id)
                            snackbarHostState.showSnackbar(extraDeletedMessage)
                        }
                    }
                } else if (event.isExtra) {
                    if (blockId != null) {
                        scope.launch {
                            extraRepository.delete(blockId)
                            snackbarHostState.showSnackbar(extraDeletedMessage)
                        }
                    }
                } else if (routineId != null && blockId != null) {
                    val date = event.start.toLocalDate().toString()
                    scope.launch {
                        overrideRepository.put(
                            BlockOverride(
                                routineId = routineId,
                                blockId = blockId,
                                date = date,
                                deleted = true,
                            ),
                        )
                        val result = snackbarHostState.showSnackbar(
                            message = removedMessage,
                            actionLabel = undoLabel,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            overrideRepository.restore(routineId, blockId, date)
                        }
                    }
                }
            },
            onRestore = {
                actionEvent = null
                val routineId = event.routineId
                val blockId = event.blockId
                if (routineId != null && blockId != null) {
                    scope.launch {
                        overrideRepository.restore(
                            routineId,
                            blockId,
                            event.start.toLocalDate().toString(),
                        )
                    }
                }
            },
            onDismiss = { actionEvent = null },
        )
    }

    eventFormDate?.let { date ->
        EventFormDialog(
            initial = editingAgendaEvent,
            defaultDate = date,
            routine = routines.firstOrNull { it.isActive },
            onDismiss = {
                eventFormDate = null
                editingAgendaEvent = null
            },
            onSave = { saved ->
                scope.launch { eventRepository.save(saved) }
                eventFormDate = null
                editingAgendaEvent = null
            },
        )
    }

    // Bloco avulso: criado no FAB (dia selecionado) ou editado a partir da agenda.
    extraFormDate?.let { date ->
        val active = routines.firstOrNull { it.isActive }
        BlockFormDialog(
            initial = editingExtra?.toRoutineBlock(),
            routine = null,
            onDismiss = {
                extraFormDate = null
                editingExtra = null
            },
            onSave = { block ->
                scope.launch {
                    extraRepository.save(
                        ExtraBlock.from(
                            block = block,
                            date = date.toString(),
                            routineId = if (linkToRoutine) active?.id else null,
                        ),
                    )
                }
                extraFormDate = null
                editingExtra = null
            },
            showWeekday = false,
            notice = stringResource(R.string.extra_notice, date.fullDateLabel()),
            extraContent = {
                if (active != null) {
                    Text(
                        text = stringResource(R.string.extra_link_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !linkToRoutine,
                            onClick = { linkToRoutine = false },
                            label = { Text(stringResource(R.string.extra_link_none)) },
                        )
                        FilterChip(
                            selected = linkToRoutine,
                            onClick = { linkToRoutine = true },
                            label = { Text(active.name, maxLines = 1) },
                        )
                    }
                }
            },
        )
    }

    editingEvent?.let { event ->
        val routine = routines.firstOrNull { it.id == event.routineId }
        val block = event.toEditableBlock()
        if (routine != null && block != null) {
            val date = event.start.toLocalDate()
            BlockFormDialog(
                initial = block,
                routine = routine,
                onDismiss = { editingEvent = null },
                onSave = { edited ->
                    scope.launch {
                        overrideRepository.put(
                            BlockOverride(
                                routineId = routine.id,
                                blockId = edited.id,
                                date = date.toString(),
                                block = edited,
                            ),
                        )
                    }
                    editingEvent = null
                },
                showWeekday = false,
                notice = stringResource(R.string.occurrence_notice, date.fullDateLabel()),
            )
        }
    }
}

/**
 * Transição de período: o conteúdo novo entra pelo lado do movimento e o antigo
 * sai pelo lado oposto, como uma página deslizando.
 */
private fun AnimatedContentTransitionScope<LocalDate>.slideByDirection(): ContentTransform {
    val forward = targetState > initialState
    return if (forward) {
        (slideInHorizontally(tween(280)) { it } + fadeIn(tween(200))) togetherWith
            (slideOutHorizontally(tween(280)) { -it } + fadeOut(tween(160)))
    } else {
        (slideInHorizontally(tween(280)) { -it } + fadeIn(tween(200))) togetherWith
            (slideOutHorizontally(tween(280)) { it } + fadeOut(tween(160)))
    }
}

/**
 * Detecta um arrasto horizontal e dispara no fim do gesto, se passar do limite.
 * Usado para trocar de período sem precisar de um pager por visualização.
 */
@Composable
private fun Modifier.horizontalSwipe(
    threshold: Dp = 64.dp,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var accumulated = 0f
            var dragging = false
            var aborted = false

            while (!aborted) {
                val event = awaitPointerEvent()
                // Dois dedos é pinça da grade: não pode virar troca de dia.
                if (event.changes.count { it.pressed } > 1) {
                    aborted = true
                    break
                }

                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dx = change.positionChange().x
                val dy = change.positionChange().y

                if (!dragging) {
                    // Rolagem vertical não é swipe horizontal.
                    if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                        aborted = true
                    } else if (abs(dx) > viewConfiguration.touchSlop) {
                        dragging = true
                    }
                }
                if (dragging) {
                    change.consume()
                    accumulated += dx
                }
            }

            if (dragging && !aborted) {
                val limit = threshold.toPx()
                when {
                    accumulated <= -limit -> left()
                    accumulated >= limit -> right()
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 780)
@Composable
private fun CalendarScreenPreview() {
    val context = LocalContext.current
    val repository = remember { RoutineRepository(context) }
    val completions = remember { CompletionRepository(context) }
    val overrides = remember { BlockOverrideRepository(context) }
    val extras = remember { ExtraBlockRepository(context) }
    val events = remember { EventRepository(context) }
    NexTasksTheme {
        CalendarScreen(
            onOpenDrawer = {},
            routineRepository = repository,
            completionRepository = completions,
            overrideRepository = overrides,
            extraRepository = extras,
            eventRepository = events,
        )
    }
}