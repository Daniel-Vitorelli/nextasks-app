package com.daniel.nextasks.ui.ai

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AiMemoryEntry
import com.daniel.nextasks.data.AiMemoryRepository
import kotlinx.coroutines.launch

/**
 * Memória do assistente: o que vale para todas as conversas.
 *
 * Editável à mão aqui e pela própria IA (ferramentas `memory.*`, sempre com
 * confirmação). Fica no aparelho, junto do resto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMemoryDialog(
    memoryRepository: AiMemoryRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val memories by memoryRepository.memories.collectAsState(initial = emptyList())
    var editor by remember { mutableStateOf<AiMemoryEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiMemoryEntry?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.ai_memory_title),
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
                        if (memories.isNotEmpty()) {
                            TextButton(onClick = { clearConfirm = true }) {
                                Text(
                                    text = stringResource(R.string.ai_memory_clear),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.ai_memory_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            draft = ""
                            creating = true
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_memory_add))
                    }
                }

                if (memories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_memory_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(memories, key = { it.id }) { entry ->
                            MemoryCard(
                                entry = entry,
                                onEdit = {
                                    draft = entry.text
                                    editor = entry
                                },
                                onDelete = { deleteTarget = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        val target = editor
        AlertDialog(
            onDismissRequest = {
                creating = false
                editor = null
            },
            title = {
                Text(
                    stringResource(
                        if (target == null) R.string.ai_memory_new else R.string.ai_memory_edit,
                    ),
                )
            },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.ai_memory_title)) },
                    placeholder = { Text(stringResource(R.string.ai_memory_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val text = draft.trim()
                        scope.launch {
                            if (target == null) {
                                memoryRepository.add(text)
                            } else {
                                memoryRepository.update(target.id, text)
                            }
                        }
                        creating = false
                        editor = null
                    },
                ) {
                    Text(stringResource(R.string.task_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creating = false
                        editor = null
                    },
                ) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.ai_memory_delete_title)) },
            text = { Text(stringResource(R.string.ai_memory_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { memoryRepository.delete(entry.id) }
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

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text(stringResource(R.string.ai_memory_clear_title)) },
            text = { Text(stringResource(R.string.ai_memory_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { memoryRepository.clear() }
                        clearConfirm = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.ai_memory_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryCard(
    entry: AiMemoryEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.ai_memory_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.task_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
