package com.daniel.nextasks.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.HabitFrequency
import com.daniel.nextasks.data.HabitKind
import com.daniel.nextasks.data.HabitDay
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.HabitStreak
import com.daniel.nextasks.data.habitProgress
import com.daniel.nextasks.data.habitStreak
import com.daniel.nextasks.ui.calendar.toEventColor
import com.daniel.nextasks.ui.components.dashedCard
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Seção de hábitos: criar, editar e excluir. A marcação do dia fica na Home,
 * como no app web — aqui a tela é de gestão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    repository: HabitRepository,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val habits by repository.habitList.collectAsState(initial = emptyList())
    val completions by repository.completionList.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var formVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Habit?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    val editing = habits.firstOrNull { it.id == editingId }
    val countsToday = remember(completions, today) {
        completions.filter { it.date == today.toString() }
            .associate { it.habitId to it.count }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_habits),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingId = null
                    formVisible = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.habit_new),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { innerPadding ->
        if (habits.isEmpty()) {
            EmptyHabits(
                onCreate = {
                    editingId = null
                    formVisible = true
                },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(habits, key = { it.id }) { habit ->
                    val counts = remember(completions, habit.id) {
                        completions
                            .filter { it.habitId == habit.id }
                            .associate { java.time.LocalDate.parse(it.date) to it.count }
                    }
                    val progress = remember(counts, habit, today) {
                        habitProgress(habit, counts, today.minusDays(83), today)
                    }
                    val streak = remember(progress, today) { habitStreak(progress, today) }

                    HabitCard(
                        habit = habit,
                        countToday = countsToday[habit.id] ?: 0,
                        progress = progress,
                        streak = streak,
                        expanded = expandedId == habit.id,
                        onToggleExpand = {
                            expandedId = if (expandedId == habit.id) null else habit.id
                        },
                        onEdit = {
                            editingId = habit.id
                            formVisible = true
                        },
                        onDelete = { deleteTarget = habit },
                    )
                }
            }
        }
    }

    if (formVisible) {
        HabitFormDialog(
            initial = editing,
            onDismiss = { formVisible = false },
            onSave = { habit ->
                scope.launch { repository.save(habit) }
                formVisible = false
            },
        )
    }

    deleteTarget?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.habit_delete_title)) },
            text = { Text(stringResource(R.string.habit_delete_body, habit.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.delete(habit.id) }
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.task_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        )
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    countToday: Int,
    progress: List<HabitDay>,
    streak: HabitStreak,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = habit.color.toEventColor().accent()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(habitIconRes(habit.icon)),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accent,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = habitGoalLabel(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = habitTodayLabel(habit, countToday),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.habit_actions),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.subtask_edit)) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.subtask_delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            if (expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        HabitStat(
                            label = stringResource(R.string.habit_stat_current),
                            value = streak.current.toString(),
                            highlight = true,
                            accent = accent,
                        )
                        HabitStat(
                            label = stringResource(R.string.habit_stat_longest),
                            value = streak.longest.toString(),
                        )
                        streak.lastFullDay?.let { day ->
                            HabitStat(
                                label = stringResource(R.string.habit_stat_last),
                                value = day.format(
                                    java.time.format.DateTimeFormatter.ofPattern("dd/MM"),
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (progress.isEmpty()) {
                        Text(
                            text = stringResource(R.string.habit_no_history),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        HabitHeatmap(
                            habit = habit,
                            progress = progress,
                            accent = accent,
                            emptyColor = MaterialTheme.colorScheme.surfaceVariant,
                            slipColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            outlineColor = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.habit_heatmap_caption),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            HabitHeatmapLegend(
                                accent = accent,
                                emptyColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitStat(
    label: String,
    value: String,
    highlight: Boolean = false,
    accent: Color = Color.Unspecified,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHabits(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dashedCard(MaterialTheme.colorScheme.outline, 16.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_habit_target),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.habits_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.habits_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCreate) {
                Text(stringResource(R.string.habit_new))
            }
        }
    }
}
