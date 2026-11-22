package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import java.util.UUID

internal val Context.subtaskStore: DataStore<List<Subtask>> by dataStore(
    fileName = "subtasks.json",
    serializer = JsonListSerializer(Subtask.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Repositório das sub-tarefas.
 *
 * As operações de conclusão/exclusão aplicam a cascata da árvore e devolvem o
 * estado que a **tarefa** deve assumir, para quem chamou sincronizar a tarefa.
 */
class SubtaskRepository(context: Context) {
    private val store = context.applicationContext.subtaskStore

    val subtasks: Flow<List<Subtask>> = store.data

    suspend fun save(subtask: Subtask) {
        store.updateData { current ->
            if (current.any { it.id == subtask.id }) {
                current.map { if (it.id == subtask.id) subtask else it }
            } else {
                current + subtask
            }
        }
    }

    /** Conclui ou reabre o nó com cascata. Devolve o estado desejado da tarefa. */
    suspend fun toggleDone(id: String): Boolean? {
        var taskDone: Boolean? = null
        store.updateData { current ->
            val node = current.firstOrNull { it.id == id } ?: return@updateData current
            val result = if (node.done) {
                reopenSubtask(current, id)
            } else {
                completeSubtask(current, id)
            }
            taskDone = result.taskDone
            result.subtasks
        }
        return taskDone
    }

    /** Exclui o nó e a sub-árvore. Devolve o estado desejado da tarefa. */
    suspend fun delete(id: String): Boolean? {
        var taskDone: Boolean? = null
        store.updateData { current ->
            val result = deleteSubtask(current, id)
            taskDone = result.taskDone
            result.subtasks
        }
        return taskDone
    }

    /** Concluir a tarefa conclui todas as sub-tarefas dela, como no app web. */
    suspend fun completeAllForTask(taskId: String) {
        store.updateData { current ->
            current.map { if (it.taskId == taskId) it.copy(done = true) else it }
        }
    }

    /**
     * Copia a árvore de sub-tarefas para outra tarefa, preservando a hierarquia
     * e renascendo tudo como pendente (a cópia da tarefa também nasce pendente).
     */
    suspend fun duplicateForTask(sourceTaskId: String, targetTaskId: String) {
        store.updateData { current ->
            val source = current.filter { it.taskId == sourceTaskId }
            if (source.isEmpty()) return@updateData current

            val idMap = source.associate { it.id to UUID.randomUUID().toString() }
            val copies = source.map { subtask ->
                subtask.copy(
                    id = idMap.getValue(subtask.id),
                    taskId = targetTaskId,
                    parentId = subtask.parentId?.let { idMap[it] },
                    done = false,
                    createdAt = System.currentTimeMillis(),
                )
            }
            current + copies
        }
    }

    suspend fun deleteForTask(taskId: String) {
        store.updateData { current -> current.filterNot { it.taskId == taskId } }
    }
}
