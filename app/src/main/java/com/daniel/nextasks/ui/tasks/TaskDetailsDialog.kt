package com.daniel.nextasks.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Subtask
import com.daniel.nextasks.data.SubtaskNode
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.flattenSubtaskTree

/**
 * Detalhe da tarefa com a árvore de sub-tarefas.
 *
 * Tocar no card da tarefa abre esta tela — no app web o clique no card também
 * leva ao detalhe, e a edição fica no menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsDialog(
    task: Task,
    subtasks: List<Subtask>,
    onDismiss: () -> Unit,
    onToggleSubtask: (Subtask) -> Unit,
    onAddSubtask: (parentId: String?) -> Unit,
    onEditSubtask: (Subtask) -> Unit,
    onDeleteSubtask: (Subtask) -> Unit,
) {
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val nodes = remember(subtasks, task.id, collapsed) {
        flattenSubtaskTree(subtasks, task.id, collapsed)
    }
    val doneCount = subtasks.count { it.done }

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
                            text = stringResource(R.string.task_details_title),
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
                        IconButton(onClick = { onAddSubtask(null) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = stringResource(R.string.subtask_add),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                ) {
                    item {
                        TaskSummaryBlock(task = task, doneCount = doneCount, total = subtasks.size)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.subtasks_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (nodes.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.subtasks_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(nodes, key = { it.subtask.id }) { node ->
                            SubtaskRow(
                                node = node,
                                collapsed = node.subtask.id in collapsed,
                                onToggleCollapse = {
                                    collapsed = if (node.subtask.id in collapsed) {
                                        collapsed - node.subtask.id
                                    } else {
                                        collapsed + node.subtask.id
                                    }
                                },
                                onToggle = { onToggleSubtask(node.subtask) },
                                onEdit = { onEditSubtask(node.subtask) },
                                onAddChild = { onAddSubtask(node.subtask.id) },
                                onDelete = { onDeleteSubtask(node.subtask) },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryBlock(task: Task, doneCount: Int, total: Int) {
    Column {
        Text(
            text = task.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (task.done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = priorityColor(task.priority)
            TaskBadge(
                text = stringResource(priorityLabel(task.priority)),
                color = color,
                container = color.copy(alpha = 0.14f),
            )
            formatShortDate(task.dueDate)?.let { due ->
                TaskBadge(
                    text = stringResource(R.string.task_due_badge, due),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    icon = R.drawable.ic_schedule,
                )
            }
            if (total > 0) {
                TaskBadge(
                    text = stringResource(R.string.subtasks_count, doneCount, total),
                    color = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    icon = R.drawable.ic_tasks,
                )
            }
        }

        if (!task.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubtaskRow(
    node: SubtaskNode,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    val subtask = node.subtask
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * 20).dp)
            .clip(RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.hasChildren) {
                IconButton(onClick = onToggleCollapse, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = stringResource(R.string.subtask_collapse),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (collapsed) -90f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(30.dp))
            }
            Checkbox(
                checked = subtask.done,
                onCheckedChange = { onToggle() },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = subtask.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (subtask.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtask.description.isNullOrBlank()) {
                    Text(
                        text = subtask.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.subtask_actions),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.subtask_add_child)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAddChild()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.subtask_edit)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.subtask_delete)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
