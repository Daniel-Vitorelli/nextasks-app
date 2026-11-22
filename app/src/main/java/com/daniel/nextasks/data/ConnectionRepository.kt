package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal val Context.connectionStore: DataStore<List<Connection>> by dataStore(
    fileName = "connections.json",
    serializer = JsonListSerializer(Connection.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/** Repositório local das conexões entre entidades e blocos de horário. */
class ConnectionRepository(context: Context) {
    private val store = context.applicationContext.connectionStore

    val connections: Flow<List<Connection>> = store.data

    suspend fun save(connection: Connection) {
        store.updateData { current ->
            val exists = current.any { it.id == connection.id }
            if (exists) {
                current.map { if (it.id == connection.id) connection else it }
            } else {
                current + connection
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    /** Remove as conexões de um bloco excluído (a rotina ou o bloco sumiu). */
    suspend fun clearForBlock(blockId: String) {
        store.updateData { current -> current.filterNot { it.blockId == blockId } }
    }

    /**
     * Remove conexões cujo bloco ou entidade não existe mais.
     *
     * Chamado quando os dados mudam: é mais simples (e mais seguro) varrer tudo
     * do que lembrar de limpar em cada exclusão.
     */
    suspend fun prune(
        blockIds: Set<String>,
        taskIds: Set<String>,
        subtaskIds: Set<String>,
        habitIds: Set<String>,
    ) {
        store.updateData { current ->
            current.filterNot { link ->
                link.blockId !in blockIds ||
                    (link.taskId != null && link.taskId !in taskIds) ||
                    (link.subtaskId != null && link.subtaskId !in subtaskIds) ||
                    (link.habitId != null && link.habitId !in habitIds)
            }
        }
    }

    /** Remove as conexões que apontam para uma entidade excluída. */
    suspend fun clearForEntity(taskId: String? = null, subtaskId: String? = null, habitId: String? = null) {
        store.updateData { current ->
            current.filterNot {
                (taskId != null && it.taskId == taskId) ||
                    (subtaskId != null && it.subtaskId == subtaskId) ||
                    (habitId != null && it.habitId == habitId)
            }
        }
    }
}

/**
 * Efeitos de uma conexão depois que o usuário age.
 *
 * Só o sentido **bloco → entidade**: confirmar o bloco o suficiente conclui a
 * tarefa/sub-tarefa ou fecha a meta do hábito. O sentido inverso (concluir a
 * entidade confirmar o bloco) ainda não existe no app.
 */
class ConnectionEngine(
    private val connectionRepository: ConnectionRepository,
    private val completionRepository: CompletionRepository,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val habitRepository: HabitRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Addon desligado = comportamento desligado.
     *
     * Lido a cada chamada (e não no construtor) para valer no instante em que o
     * usuário desliga, sem precisar reiniciar o app.
     */
    private suspend fun enabled(): Boolean = runCatching {
        settingsRepository.settings.first().isAddonEnabled(Addon.Connections)
    }.getOrDefault(false)
    suspend fun onBlockConfirmed(blockId: String, today: LocalDate = LocalDate.now()) {
        if (!enabled()) return

        val links = connectionRepository.connections.first().filter { it.blockId == blockId }
        if (links.isEmpty()) return

        val completions = completionRepository.completions.first()

        links.forEach { link ->
            val count = completions.count { link.countsFor(it) }
            if (count >= link.requiredCount) advance(link, today)
        }
    }

    /**
     * Sentido inverso: concluir a entidade confirma o bloco no período atual.
     *
     * Cada entidade tem sua função porque o "concluído" é diferente em cada uma
     * (tarefa tem flag, sub-tarefa também, hábito bate meta de contagem).
     */
    suspend fun onTaskDone(taskId: String, today: LocalDate = LocalDate.now()) =
        confirmBlocks({ it.taskId == taskId }, today)

    suspend fun onSubtaskDone(subtaskId: String, today: LocalDate = LocalDate.now()) =
        confirmBlocks({ it.subtaskId == subtaskId }, today)

    suspend fun onHabitDone(habitId: String, today: LocalDate = LocalDate.now()) =
        confirmBlocks({ it.habitId == habitId }, today)

    private suspend fun confirmBlocks(
        match: (Connection) -> Boolean,
        today: LocalDate,
    ) {
        if (!enabled()) return

        val links = connectionRepository.connections.first().filter(match)
        if (links.isEmpty()) return

        val routines = routineRepository.routines.first()
        val completions = completionRepository.completions.first()

        links.forEach { link ->
            val routine = routines.firstOrNull { it.id == link.routineId } ?: return@forEach
            // Mesma regra do calendário: semanal conta a semana inteira.
            val period = if (routine.frequency == RoutineFrequency.Weekly) {
                today.minusDays((today.dayOfWeek.value % 7).toLong())
            } else {
                today
            }
            val key = period.toString()
            val already = completions.any {
                it.blockId == link.blockId && it.periodStart == key && it.value != "false"
            }
            if (!already) completionRepository.set(link.blockId, period, "true")
        }
    }

    private suspend fun advance(link: Connection, today: LocalDate) {
        link.taskId?.let { id ->
            taskRepository.setDone(id, true)
            return
        }

        link.subtaskId?.let { id ->
            val subtask = subtaskRepository.subtasks.first().firstOrNull { it.id == id }
            if (subtask != null && !subtask.done) subtaskRepository.toggleDone(id)
            return
        }

        link.habitId?.let { id ->
            val habit = habitRepository.habitList.first().firstOrNull { it.id == id } ?: return
            // Fecha a meta do dia de uma vez, sem depender de quantas faltavam.
            habitRepository.complete(habit, today, increment = habit.targetCount ?: 1)
        }
    }
}
