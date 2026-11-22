package com.daniel.nextasks.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.daniel.nextasks.data.SortDirection
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.TaskSortCriterion
import com.daniel.nextasks.data.TaskSortField
import com.daniel.nextasks.data.TaskSortPreset
import com.daniel.nextasks.data.TaskSortSettings
import com.daniel.nextasks.ui.tasks.TaskBucket
import com.daniel.nextasks.ui.tasks.bucketOf
import com.daniel.nextasks.ui.tasks.formatShortDate
import com.daniel.nextasks.ui.tasks.sortPendingTasks
import java.time.LocalDate

/** Quantas tarefas a prévia mostra por grupo. */
private const val PREVIEW_PER_GROUP = 2

/**
 * Tela de ordem das tarefas.
 *
 * Vive fora da lista de Configurações porque a lista de critérios cresce e
 * precisa de espaço — e porque a prévia é o que dá sentido à escolha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskOrderScreen(
    sort: TaskSortSettings,
    tasks: List<Task>,
    pendingSubtasks: Map<String, Int>,
    today: LocalDate,
    onBack: () -> Unit,
    onChange: (TaskSortSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_task_order),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.sort_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            SectionTitle(stringResource(R.string.sort_section_presets))
            PresetChips(sort = sort, onChange = onChange)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QuickAction(
                    icon = R.drawable.ic_swap_vert,
                    label = stringResource(R.string.sort_flip_all),
                    enabled = sort.criteria.isNotEmpty(),
                    onClick = {
                        onChange(sort.copy(criteria = sort.criteria.map { it.toggled() }))
                    },
                )
                QuickAction(
                    icon = R.drawable.ic_restart,
                    label = stringResource(R.string.sort_reset),
                    enabled = sort != TaskSortSettings(),
                    onClick = { onChange(TaskSortSettings()) },
                )
            }

            SectionTitle(stringResource(R.string.sort_section_grouping))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sort_group_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.sort_group_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = sort.groupByUrgency,
                        onCheckedChange = { onChange(sort.copy(groupByUrgency = it)) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.sort_section_criteria))
            SettingsCard(contentPadding = 0.dp) {
                sort.criteria.forEachIndexed { index, criterion ->
                    CriterionRow(
                        position = index + 1,
                        criterion = criterion,
                        canMoveUp = index > 0,
                        canMoveDown = index < sort.criteria.lastIndex,
                        canRemove = sort.criteria.size > 1,
                        onMove = { delta ->
                            onChange(sort.copy(criteria = sort.criteria.moved(index, delta)))
                        },
                        onToggleDirection = {
                            onChange(
                                sort.copy(
                                    criteria = sort.criteria.mapIndexed { i, item ->
                                        if (i == index) item.toggled() else item
                                    },
                                ),
                            )
                        },
                        onRemove = {
                            onChange(
                                sort.copy(
                                    criteria = sort.criteria.filterIndexed { i, _ -> i != index },
                                ),
                            )
                        },
                    )
                    if (index < sort.criteria.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (sort.criteria.size < TaskSortField.entries.size) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    AddCriterionRow(
                        used = sort.criteria.map { it.field }.toSet(),
                        onAdd = { field ->
                            onChange(
                                sort.copy(
                                    criteria = sort.criteria + TaskSortCriterion(
                                        field = field,
                                        direction = field.defaultDirection(),
                                    ),
                                ),
                            )
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.sort_section_summary))
            SortSummary(sort = sort)

            SectionTitle(stringResource(R.string.sort_preview_title))
            TaskOrderPreview(
                sort = sort,
                tasks = tasks,
                pendingSubtasks = pendingSubtasks,
                today = today,
            )
        }
    }
}

/** Linha de Configurações que abre esta tela, com um resumo do que está escolhido. */
@Composable
internal fun TaskOrderEntryRow(sort: TaskSortSettings, onClick: () -> Unit) {
    val preset = TaskSortPreset.entries.firstOrNull { it.settings == sort }
    val fields = sort.criteria.map { stringResource(it.field.labelRes()) }
    val subtitle = when {
        preset != null -> stringResource(preset.labelRes())
        fields.isEmpty() -> stringResource(R.string.sort_summary_none)
        else -> fields.joinToString(" → ")
    }

    ActionRow(
        icon = R.drawable.ic_sort,
        title = stringResource(R.string.settings_task_order),
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
private fun PresetChips(sort: TaskSortSettings, onChange: (TaskSortSettings) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskSortPreset.entries.forEach { preset ->
            FilterChip(
                selected = sort == preset.settings,
                onClick = { onChange(preset.settings) },
                label = { Text(stringResource(preset.labelRes())) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Uma linha de critério: posição, nome, sentido, inverter e menu. */
@Composable
private fun CriterionRow(
    position: Int,
    criterion: TaskSortCriterion,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMove: (Int) -> Unit,
    onToggleDirection: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(criterion.field.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(criterion.directionLabelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onToggleDirection) {
            Icon(
                painter = painterResource(
                    if (criterion.direction == SortDirection.Descending) {
                        R.drawable.ic_arrow_down
                    } else {
                        R.drawable.ic_arrow_up
                    },
                ),
                contentDescription = stringResource(R.string.sort_toggle_direction),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.sort_criterion_actions),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_move_up)) },
                    enabled = canMoveUp,
                    onClick = {
                        menuOpen = false
                        onMove(-1)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_move_down)) },
                    enabled = canMoveDown,
                    onClick = {
                        menuOpen = false
                        onMove(1)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.sort_remove),
                            color = if (canRemove) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    enabled = canRemove,
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

/** "Adicionar critério" com a lista dos campos ainda não usados. */
@Composable
private fun AddCriterionRow(
    used: Set<TaskSortField>,
    onAdd: (TaskSortField) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val available = TaskSortField.entries.filterNot { it in used }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Box {
            Text(
                text = stringResource(R.string.sort_add_criterion),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuOpen = true }
                    .padding(vertical = 4.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                available.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(stringResource(field.labelRes())) },
                        onClick = {
                            menuOpen = false
                            onAdd(field)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A configuração escrita em português.
 *
 * Serve para conferir a leitura do que foi montado: uma pilha de critérios é
 * fácil de montar errado e difícil de reler.
 */
@Composable
private fun SortSummary(sort: TaskSortSettings) {
    // `joinToString` não é inline: as strings são resolvidas antes, em `map`.
    val parts = sort.criteria.map { criterion ->
        val field = stringResource(criterion.field.labelRes())
        val direction = stringResource(criterion.directionLabelRes())
        "$field ($direction)"
    }
    val chain = parts.joinToString(" → ")

    val text = when {
        sort.criteria.isEmpty() -> stringResource(R.string.sort_summary_none)
        sort.groupByUrgency -> stringResource(R.string.sort_summary_grouped, chain)
        else -> stringResource(R.string.sort_summary_flat, chain)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/** Prévia com as tarefas reais, agrupadas como na Home. */
@Composable
private fun TaskOrderPreview(
    sort: TaskSortSettings,
    tasks: List<Task>,
    pendingSubtasks: Map<String, Int>,
    today: LocalDate,
) {
    val ordered = remember(tasks, sort, today, pendingSubtasks) {
        sortPendingTasks(tasks, today, sort, pendingSubtasks)
    }

    val groups: List<Pair<TaskBucket?, List<Task>>> = if (sort.groupByUrgency) {
        TaskBucket.entries.mapNotNull { bucket ->
            ordered.filter { bucketOf(it, today) == bucket }
                .takeIf { it.isNotEmpty() }
                ?.let { bucket to it }
        }
    } else {
        listOf(null to ordered)
    }

    val shown = groups.sumOf { it.second.take(PREVIEW_PER_GROUP).size }
    val hidden = ordered.size - shown

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (ordered.isEmpty()) {
                Text(
                    text = stringResource(R.string.sort_preview_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                groups.forEach { (bucket, items) ->
                    if (bucket != null) {
                        Text(
                            text = stringResource(bucket.labelRes()).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items.take(PREVIEW_PER_GROUP).forEach { task ->
                        PreviewRow(task = task)
                    }
                }
                if (hidden > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (hidden == 1) {
                            stringResource(R.string.sort_preview_more_one)
                        } else {
                            stringResource(R.string.sort_preview_more, hidden)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(task: Task) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        task.dueDate?.let { due ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatShortDate(due).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Move o item uma posição, devolvendo a mesma lista quando o movimento é inválido. */
private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

private fun TaskSortCriterion.toggled(): TaskSortCriterion = copy(
    direction = if (direction == SortDirection.Descending) {
        SortDirection.Ascending
    } else {
        SortDirection.Descending
    },
)

/** Sentido que faz mais sentido ao acrescentar o campo. */
private fun TaskSortField.defaultDirection(): SortDirection = when (this) {
    TaskSortField.Priority -> SortDirection.Descending
    TaskSortField.CreatedAt -> SortDirection.Descending
    else -> SortDirection.Ascending
}

private fun TaskSortField.labelRes(): Int = when (this) {
    TaskSortField.Priority -> R.string.sort_field_priority
    TaskSortField.DueDate -> R.string.sort_field_due
    TaskSortField.StartDate -> R.string.sort_field_start
    TaskSortField.CreatedAt -> R.string.sort_field_created
    TaskSortField.Title -> R.string.sort_field_title
    TaskSortField.Subtasks -> R.string.sort_field_subtasks
}

private fun TaskSortCriterion.directionLabelRes(): Int = when (field) {
    TaskSortField.Priority -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_priority_desc
    } else {
        R.string.sort_dir_priority_asc
    }

    TaskSortField.DueDate -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_due_desc
    } else {
        R.string.sort_dir_due_asc
    }

    TaskSortField.StartDate -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_start_desc
    } else {
        R.string.sort_dir_start_asc
    }

    TaskSortField.CreatedAt -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_created_desc
    } else {
        R.string.sort_dir_created_asc
    }

    TaskSortField.Title -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_title_desc
    } else {
        R.string.sort_dir_title_asc
    }

    TaskSortField.Subtasks -> if (direction == SortDirection.Descending) {
        R.string.sort_dir_subtasks_desc
    } else {
        R.string.sort_dir_subtasks_asc
    }
}

private fun TaskBucket.labelRes(): Int = when (this) {
    TaskBucket.Overdue -> R.string.sort_bucket_overdue
    TaskBucket.Due -> R.string.sort_bucket_due
    TaskBucket.NoDue -> R.string.sort_bucket_nodue
    TaskBucket.NotStarted -> R.string.sort_bucket_notstarted
}

private fun TaskSortPreset.labelRes(): Int = when (this) {
    TaskSortPreset.Balanced -> R.string.sort_preset_balanced
    TaskSortPreset.Deadline -> R.string.sort_preset_deadline
    TaskSortPreset.Priority -> R.string.sort_preset_priority
    TaskSortPreset.Newest -> R.string.sort_preset_newest
    TaskSortPreset.Alphabetical -> R.string.sort_preset_alpha
}
