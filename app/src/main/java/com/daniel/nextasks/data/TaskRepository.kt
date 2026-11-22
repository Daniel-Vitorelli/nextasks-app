package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import java.util.UUID

internal val Context.taskStore: DataStore<List<Task>> by dataStore(
    fileName = "tasks.json",
    serializer = JsonListSerializer(Task.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Repositório local de tarefas — mesmo esquema das rotinas: DataStore com JSON,
 * leitura reativa e escrita transacional, funcionando offline.
 */
class TaskRepository(context: Context) {
    private val store = context.applicationContext.taskStore

    val tasks: Flow<List<Task>> = store.data

    suspend fun save(task: Task) {
        store.updateData { current ->
            if (current.any { it.id == task.id }) {
                current.map { if (it.id == task.id) task else it }
            } else {
                current + task
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    suspend fun toggleDone(id: String) {
        store.updateData { current ->
            current.map { if (it.id == id) it.copy(done = !it.done) else it }
        }
    }

    /** Define o estado de conclusão (usado pela cascata das sub-tarefas). */
    suspend fun setDone(id: String, done: Boolean) {
        store.updateData { current ->
            current.map { if (it.id == id) it.copy(done = done) else it }
        }
    }

    /** Cópia da tarefa nasce pendente, como em `/api/tasks/[id]/duplicate`. */
    /** Duplica a tarefa e devolve a cópia (para copiar também as sub-tarefas). */
    suspend fun duplicate(id: String): Task? {
        var copy: Task? = null
        store.updateData { current ->
            val original = current.firstOrNull { it.id == id }
            if (original == null) {
                current
            } else {
                copy = original.copy(
                    id = UUID.randomUUID().toString(),
                    title = "${original.title} (cópia)",
                    done = false,
                    createdAt = System.currentTimeMillis(),
                )
                current + copy!!
            }
        }
        return copy
    }
}
