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

    /** Remove as conexões de uma rotina excluída (todos os blocos dela). */
    suspend fun clearForRoutine(routineId: String) {
        store.updateData { current -> current.filterNot { it.routineId == routineId } }
    }

    /**
     * Remove as conexões de uma tarefa e das sub-tarefas dela.
     *
     * Excluir a tarefa leva a árvore inteira junto, então as conexões das
     * sub-tarefas ficariam apontando para ids que não existem mais.
     */
    suspend fun clearForTask(taskId: String, subtaskIds: Collection<String> = emptyList()) {
        store.updateData { current ->
            current.filterNot { link ->
                link.taskId == taskId ||
                    (link.subtaskId != null && link.subtaskId in subtaskIds)
            }
        }
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

    /** Remove as conexões de sub-tarefas excluídas (a sub-árvore inteira). */
    suspend fun clearForSubtasks(subtaskIds: Collection<String>) {
        if (subtaskIds.isEmpty()) return
        store.updateData { current ->
            current.filterNot { link -> link.subtaskId != null && link.subtaskId in subtaskIds }
        }
    }

    /** Remove as conexões que apontam para um hábito excluído. */
    suspend fun clearForHabit(habitId: String) {
        store.updateData { current -> current.filterNot { it.habitId == habitId } }
    }
}

/**
 * Efeitos de uma conexão depois que o usuário age — nos dois sentidos.
 *
 * - **Bloco → entidade** ([onBlockConfirmed]): confirmar o bloco o suficiente
 *   conclui a tarefa/sub-tarefa ou fecha a meta do hábito.
 * - **Entidade → bloco** ([onTaskDone], [onSubtaskDone], [onHabitDone]):
 *   concluir a entidade confirma o bloco no período atual.
 *
 * A conclusão de tarefa passa por [TaskCompletion] para não perder a cascata das
 * sub-tarefas — era exatamente o que faltava aqui.
 */
class ConnectionEngine(
    private val connectionRepository: ConnectionRepository,
    private val completionRepository: CompletionRepository,
    private val routineRepository: RoutineRepository,
    private val taskCompletion: TaskCompletion,
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
    /**
     * Sentido bloco → entidade.
     *
     * [period] é o período da confirmação (o dia, ou o início da semana em rotina
     * semanal). Precisa vir da tela: usar `LocalDate.now()` aqui fazia confirmar o
     * bloco de ontem fechar a meta do hábito **hoje**.
     */
    suspend fun onBlockConfirmed(blockId: String, period: LocalDate = LocalDate.now()) {
        if (!enabled()) return

        val links = connectionRepository.connections.first().filter { it.blockId == blockId }
        if (links.isEmpty()) return

        val completions = completionRepository.completions.first()

        links.forEach { link ->
            val count = completions.count { link.countsFor(it) }
            if (count >= link.requiredCount) advance(link, period)
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

    suspend fun onHabitDone(habitId: String, today: LocalDate = LocalDate.now()) {
        // Recaída de hábito ruim não "conclui" nada: só hábito bom confirma bloco.
        val habit = runCatching {
            habitRepository.habitList.first().firstOrNull { it.id == habitId }
        }.getOrNull()
        if (habit == null || habit.kind != HabitKind.Good) return

        confirmBlocks({ it.habitId == habitId }, today)
    }

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

    /**
     * Desmarcar a confirmação desfaz o que ela concluiu.
     *
     * Só vale para tarefa e sub-tarefa: hábito não volta atrás, porque a marcação é
     * do usuário e a conexão não consegue separar o que foi dela do que foi manual.
     * Se outra conexão da mesma entidade continua satisfeita, nada é desfeito.
     */
    suspend fun onBlockUnconfirmed(blockId: String, today: LocalDate = LocalDate.now()) {
        if (!enabled()) return

        val links = connectionRepository.connections.first().filter { it.blockId == blockId }
        if (links.isEmpty()) return

        val completions = completionRepository.completions.first()
        links.forEach { link ->
            val count = completions.count { link.countsFor(it) }
            if (count < link.requiredCount) revert(link, completions)
        }
    }

    /** Aplica o efeito agora, sem esperar as confirmações — ação manual da tela. */
    suspend fun applyNow(connection: Connection, today: LocalDate = LocalDate.now()) {
        // Mesmo portão do resto do motor: addon desligado não executa nada.
        if (!enabled()) return
        advance(connection, today)
    }

    private suspend fun revert(link: Connection, completions: List<Completion>) {
        val entityId = link.entityId ?: return

        // Outra conexão da mesma entidade pode continuar batendo a meta.
        val others = connectionRepository.connections.first()
            .filter { it.id != link.id && it.entityId == entityId }
        if (others.any { other -> completions.count { other.countsFor(it) } >= other.requiredCount }) {
            return
        }

        link.taskId?.let { id ->
            taskCompletion.setDone(id, false)
            return
        }

        link.subtaskId?.let { id ->
            val subtask = subtaskRepository.subtasks.first().firstOrNull { it.id == id }
            if (subtask != null && subtask.done) {
                // `toggleDone` reabre o nó e os ancestrais e devolve o estado que a
                // tarefa deve assumir. Ignorar o retorno deixava a tarefa concluída
                // com uma sub-tarefa aberta.
                val taskDone = subtaskRepository.toggleDone(id)
                if (taskDone == false) taskCompletion.setDone(subtask.taskId, false)
            }
        }
    }

    private suspend fun advance(link: Connection, period: LocalDate) {
        link.taskId?.let { id ->
            // Pelo TaskCompletion: concluir a tarefa tem que concluir as sub-tarefas.
            taskCompletion.setDone(id, true)
            return
        }

        link.subtaskId?.let { id ->
            val subtask = subtaskRepository.subtasks.first().firstOrNull { it.id == id }
            if (subtask != null && !subtask.done) subtaskRepository.toggleDone(id)
            return
        }

        link.habitId?.let { id ->
            val habit = habitRepository.habitList.first().firstOrNull { it.id == id } ?: return
            // Garante a meta do período sem somar de novo: confirmar o mesmo bloco
            // duas vezes (ou desmarcar e remarcar) não infla a contagem.
            habitRepository.ensureCount(habit, habitDay(link, period))
        }
    }

    /**
     * Dia em que o hábito recebe a marcação da conexão.
     *
     * Em rotina diária é o próprio período. Em semanal o período é o domingo e o
     * bloco acontece `weekday` dias depois — a marcação vai para o dia do bloco,
     * não para "hoje". Antes o motor usava `LocalDate.now()`, então confirmar o
     * bloco de ontem fechava a meta do hábito hoje.
     */
    private suspend fun habitDay(link: Connection, period: LocalDate): LocalDate {
        val routine = routineRepository.routines.first()
            .firstOrNull { it.id == link.routineId }
            ?: return period
        if (routine.frequency != RoutineFrequency.Weekly) return period
        val block = routine.blocks.firstOrNull { it.id == link.blockId } ?: return period
        return period.plusDays(block.weekday.toLong())
    }
}
