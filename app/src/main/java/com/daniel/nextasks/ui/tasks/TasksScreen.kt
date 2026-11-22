package com.daniel.nextasks.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.Subtask
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.TaskCompletion
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.TaskSortSettings
import com.daniel.nextasks.data.descendantIds
import com.daniel.nextasks.ui.components.dashedCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Seção de tarefas: lista ordenada por urgência, criação, edição, duplicação e
 * exclusão. Tudo persistido localmente (DataStore), funcionando offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    repository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    /** Conclusão de tarefa com a cascata das sub-tarefas. */
    taskCompletion: TaskCompletion,
    /** Conexões: limpar as órfãs ao excluir tarefa/sub-tarefa. */
    connectionRepository: ConnectionRepository,
    /** Mesma ordem configurada para a Home. */
    taskSort: TaskSortSettings = TaskSortSettings(),
    /** Avisa as conexões que a tarefa foi concluída. */
    onTaskDone: (String) -> Unit = {},
    onSubtaskDone: (String) -> Unit = {},
    /** Chamado depois de qualquer mudança, para o XP refletir o estado atual. */
    onTaskChanged: (String) -> Unit = {},
    onSubtaskChanged: (String) -> Unit = {},
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasks by repository.tasks.collectAsState(initial = emptyList())
    val subtasks by subtaskRepository.subtasks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var formVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Task?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var subtaskForm by remember { mutableStateOf<SubtaskForm?>(null) }
    var subtaskDeleteTarget by remember { mutableStateOf<Subtask?>(null) }

    val today = remember { LocalDate.now() }
    val pendingSubtasks = remember(subtasks) {
        subtasks.filterNot { it.done }.groupingBy { it.taskId }.eachCount()
    }
    val sorted = remember(tasks, taskSort, today, pendingSubtasks) {
        sortTasks(tasks, today, taskSort, pendingSubtasks)
    }
    val editing = tasks.firstOrNull { it.id == editingId }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_tasks),
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
                    contentDescription = stringResource(R.string.task_create),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    ) { innerPadding ->
        if (sorted.isEmpty()) {
            EmptyTasks(
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
                items(sorted, key = { it.id }) { task ->
                    val mine = subtasks.filter { it.taskId == task.id }
                    TaskCard(
                        task = task,
                        subtaskDone = mine.count { it.done },
                        subtaskTotal = mine.size,
                        onOpen = { detailsId = task.id },
                        onToggle = {
                            scope.launch {
                                // Concluir a tarefa conclui as sub-tarefas (como no web).
                                taskCompletion.setDone(task.id, !task.done)
                                if (!task.done) onTaskDone(task.id)
                                onTaskChanged(task.id)
                            }
                        },
                        onEdit = {
                            editingId = task.id
                            formVisible = true
                        },
                        onDuplicate = {
                            scope.launch {
                                val copy = repository.duplicate(task.id)
                                if (copy != null) {
                                    subtaskRepository.duplicateForTask(task.id, copy.id)
                                }
                            }
                        },
                        onDelete = { deleteTarget = task },
                    )
                }
            }
        }
    }

    if (formVisible) {
        TaskFormDialog(
            initial = editing,
            onDismiss = { formVisible = false },
            onSave = { task ->
                scope.launch { repository.save(task) }
                formVisible = false
            },
        )
    }

    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.task_delete_title)) },
            text = { Text(stringResource(R.string.task_delete_body, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val subtaskIds = subtasks.filter { it.taskId == task.id }.map { it.id }
                        scope.launch {
                            repository.delete(task.id)
                            subtaskRepository.deleteForTask(task.id)
                            // A árvore foi junto: as conexões não têm mais dono.
                            connectionRepository.clearForTask(task.id, subtaskIds)
                        }
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

    detailsId?.let { id ->
        val task = tasks.firstOrNull { it.id == id }
        if (task != null) {
            TaskDetailsDialog(
                task = task,
                subtasks = subtasks.filter { it.taskId == id },
                onDismiss = { detailsId = null },
                onToggleSubtask = { sub ->
                    scope.launch {
                        val shouldBeDone = subtaskRepository.toggleDone(sub.id)
                        if (shouldBeDone != null) {
                            repository.setDone(sub.taskId, shouldBeDone)
                            if (shouldBeDone) onSubtaskDone(sub.id)
                            onSubtaskChanged(sub.id)
                            onTaskChanged(sub.taskId)
                        }
                    }
                },
                onAddSubtask = { parentId -> subtaskForm = SubtaskForm(parentId, null) },
                onEditSubtask = { sub -> subtaskForm = SubtaskForm(sub.parentId, sub) },
                onDeleteSubtask = { sub -> subtaskDeleteTarget = sub },
            )
        }
    }

    subtaskDeleteTarget?.let { sub ->
        AlertDialog(
            onDismissRequest = { subtaskDeleteTarget = null },
            title = { Text(stringResource(R.string.subtask_delete_title)) },
            text = { Text(stringResource(R.string.subtask_delete_body, sub.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        subtaskDeleteTarget = null
                        val removed = subtasks.descendantIds(sub.id) + sub.id
                        scope.launch {
                            val shouldBeDone = subtaskRepository.delete(sub.id)
                            if (shouldBeDone != null) {
                                repository.setDone(sub.taskId, shouldBeDone)
                            }
                            connectionRepository.clearForSubtasks(removed)
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.task_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { subtaskDeleteTarget = null }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        )
    }

    subtaskForm?.let { form ->
        val taskId = detailsId
        if (taskId != null) {
            val parentTitle = form.parentId
                ?.let { pid -> subtasks.firstOrNull { it.id == pid }?.title }
            SubtaskFormDialog(
                initial = form.editing,
                parentTitle = parentTitle,
                onDismiss = { subtaskForm = null },
                onSave = { title, description ->
                    scope.launch {
                        val editing = form.editing
                        subtaskRepository.save(
                            Subtask(
                                id = editing?.id ?: UUID.randomUUID().toString(),
                                taskId = taskId,
                                parentId = form.parentId,
                                title = title,
                                description = description,
                                done = editing?.done ?: false,
                                createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                    subtaskForm = null
                },
            )
        }
    }
}

/** Estado do formulário de sub-tarefa: onde ela entra e o que está sendo editado. */
private data class SubtaskForm(val parentId: String?, val editing: Subtask?)

@Composable
private fun TaskCard(
    task: Task,
    subtaskDone: Int,
    subtaskTotal: Int,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))
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
                    formatShortDate(task.startDate)?.let { start ->
                        TaskBadge(
                            text = stringResource(R.string.task_start_badge, start),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            icon = R.drawable.ic_calendar,
                        )
                    }
                    formatShortDate(task.dueDate)?.let { due ->
                        TaskBadge(
                            text = stringResource(R.string.task_due_badge, due),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            icon = R.drawable.ic_schedule,
                        )
                    }
                    if (subtaskTotal > 0) {
                        TaskBadge(
                            text = stringResource(
                                R.string.subtasks_count,
                                subtaskDone,
                                subtaskTotal,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            icon = R.drawable.ic_tasks,
                        )
                    }
                    if (task.done) {
                        TaskBadge(
                            text = stringResource(R.string.task_done),
                            color = MaterialTheme.colorScheme.primary,
                            container = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            icon = R.drawable.ic_check_circle,
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.task_actions),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_edit)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_duplicate)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_copy),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_delete)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
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

@Composable
private fun EmptyTasks(onCreate: () -> Unit, modifier: Modifier = Modifier) {
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
                    painter = painterResource(R.drawable.ic_tasks),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.tasks_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tasks_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            CreateTaskButton(onClick = onCreate)
        }
    }
}
