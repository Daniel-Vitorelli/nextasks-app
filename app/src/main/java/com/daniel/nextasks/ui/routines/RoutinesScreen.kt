package com.daniel.nextasks.ui.routines

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.RoutineRepository
import kotlinx.coroutines.launch

/**
 * Seção de rotinas: lista, detalhe com os blocos de horário, e os formulários.
 * Tudo lido e gravado no repositório local (DataStore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    repository: RoutineRepository,
    completionRepository: CompletionRepository,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by repository.routines.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Builds antigas tinham um switch independente por rotina; garante "uma ativa por vez".
    LaunchedEffect(Unit) { repository.normalizeActive() }

    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var formVisible by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var blockFormVisible by rememberSaveable { mutableStateOf(false) }
    var editingBlockId by rememberSaveable { mutableStateOf<String?>(null) }

    val opened = routines.firstOrNull { it.id == openedId }
    val editing = routines.firstOrNull { it.id == editingId }

    BackHandler(enabled = opened != null) { openedId = null }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = opened?.name ?: stringResource(R.string.section_routines),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (opened == null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = stringResource(R.string.open_menu),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else {
                        IconButton(onClick = { openedId = null }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                actions = {
                    if (opened != null) {
                        IconButton(
                            onClick = {
                                editingId = opened.id
                                formVisible = true
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.routine_edit),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (opened == null) {
                        editingId = null
                        formVisible = true
                    } else {
                        editingBlockId = null
                        blockFormVisible = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(
                        if (opened == null) R.string.routine_create else R.string.block_create,
                    ),
                )
            }
        },
    ) { innerPadding ->
        if (opened == null) {
            RoutineList(
                routines = routines,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                onOpen = { routine -> openedId = routine.id },
                onEdit = { routine ->
                    editingId = routine.id
                    formVisible = true
                },
                onToggle = { routine -> scope.launch { repository.toggleActive(routine.id) } },
                onDuplicate = { routine -> scope.launch { repository.duplicate(routine.id) } },
                onDelete = { routine -> scope.launch { repository.delete(routine.id) } },
                onCreate = {
                    editingId = null
                    formVisible = true
                },
            )
        } else {
            RoutineDetail(
                routine = opened,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                onEditBlock = { block ->
                    editingBlockId = block.id
                    blockFormVisible = true
                },
                onDeleteBlock = { block ->
                    scope.launch {
                        repository.deleteBlock(opened.id, block.id)
                        // Limpa o histórico de conclusões do bloco removido.
                        completionRepository.clearForBlock(block.id)
                    }
                },
            )
        }
    }

    if (formVisible) {
        RoutineFormDialog(
            initial = editing,
            onDismiss = { formVisible = false },
            onSave = { routine ->
                scope.launch { repository.save(routine) }
                formVisible = false
            },
        )
    }

    if (blockFormVisible && opened != null) {
        BlockFormDialog(
            initial = opened.blocks.firstOrNull { it.id == editingBlockId },
            routine = opened,
            onDismiss = { blockFormVisible = false },
            onSave = { block ->
                scope.launch { repository.saveBlock(opened.id, block) }
                blockFormVisible = false
            },
        )
    }
}

@Composable
private fun RoutineList(
    routines: List<Routine>,
    modifier: Modifier = Modifier,
    onOpen: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onToggle: (Routine) -> Unit,
    onDuplicate: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onCreate: () -> Unit,
) {
    val sorted = remember(routines) {
        routines.sortedWith(
            compareByDescending<Routine> { it.isActive }.thenByDescending { it.createdAt },
        )
    }

    if (sorted.isEmpty()) {
        EmptyRoutines(onCreate = onCreate, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sorted, key = { it.id }) { routine ->
            RoutineCard(
                routine = routine,
                onOpen = { onOpen(routine) },
                onEdit = { onEdit(routine) },
                onToggle = { onToggle(routine) },
                onDuplicate = { onDuplicate(routine) },
                onDelete = { onDelete(routine) },
            )
        }
    }
}

@Composable
private fun RoutineDetail(
    routine: Routine,
    modifier: Modifier = Modifier,
    onEditBlock: (com.daniel.nextasks.data.RoutineBlock) -> Unit,
    onDeleteBlock: (com.daniel.nextasks.data.RoutineBlock) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        RoutineSummary(routine)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.blocks_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        if (routine.blocks.isEmpty()) {
            EmptyBlock()
        } else {
            routine.blocks.forEach { block ->
                BlockRow(
                    block = block,
                    showWeekday = routine.frequency == RoutineFrequency.Weekly,
                    onEdit = { onEditBlock(block) },
                    onDelete = { onDeleteBlock(block) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
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
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Só uma rotina fica ativa: ligar esta desliga as outras.
                Switch(checked = routine.isActive, onCheckedChange = { onToggle() })
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.routine_actions),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routine_edit)) },
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
                            text = { Text(stringResource(R.string.routine_duplicate)) },
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
                            text = { Text(stringResource(R.string.routine_delete)) },
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

            if (!routine.description.isNullOrBlank()) {
                Text(
                    text = routine.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    stringResource(
                        if (routine.frequency == RoutineFrequency.Daily) {
                            R.string.routine_daily
                        } else {
                            R.string.routine_weekly
                        },
                    ),
                )
                Pill(pluralStringResource(R.plurals.blocks_count, routine.blocks.size, routine.blocks.size))
                if (!routine.isActive) {
                    Pill(stringResource(R.string.routine_inactive))
                }
            }
        }
    }
}

@Composable
private fun EmptyRoutines(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_repeat),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.routines_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.routines_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) {
            Text(stringResource(R.string.routine_create))
        }
    }
}
