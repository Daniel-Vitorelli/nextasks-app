package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.LocalDate

internal val Context.xpStore: DataStore<List<XpEvent>> by dataStore(
    fileName = "xp_events.json",
    serializer = JsonListSerializer(XpEvent.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

internal val Context.achievementStore: DataStore<List<AchievementUnlock>> by dataStore(
    fileName = "achievements.json",
    serializer = JsonListSerializer(AchievementUnlock.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Histórico de XP.
 *
 * É a fonte do total: o XP nunca é somado em outro lugar. `set` é idempotente por
 * `refKey` — repetir a mesma ação não duplica, mudar a nota do bloco atualiza o
 * valor, e desfazer remove o lançamento.
 *
 * Valor **negativo é lançamento de verdade** (recaída de hábito ruim): só `0`
 * significa "não pontua / desfazer". Antes o filtro `amount <= 0` engolia a
 * penalidade e a recaída não tirava XP nenhum.
 */
class XpRepository(context: Context) {
    private val store = context.applicationContext.xpStore

    val events: Flow<List<XpEvent>> = store.data

    suspend fun all(): List<XpEvent> = events.first()

    /**
     * Garante o lançamento de [refKey] com [amount].
     *
     * `amount == 0` remove o lançamento (desfez a ação ou ela não pontua).
     * Negativo é valor válido — é a penalidade da recaída.
     */
    suspend fun set(kind: XpKind, amount: Int, refKey: String) {
        store.updateData { current ->
            val existing = current.firstOrNull { it.refKey == refKey }
            when {
                amount == 0 -> if (existing == null) current else current - existing
                existing == null -> current + XpEvent(kind = kind.raw, refKey = refKey, amount = amount)
                existing.amount == amount && existing.kind == kind.raw -> current
                else -> current.map {
                    if (it.refKey == refKey) it.copy(kind = kind.raw, amount = amount) else it
                }
            }
        }
    }

    /**
     * Substitui **todo** o XP de domínio pelo conjunto desejado, numa escrita só.
     *
     * Preserva os lançamentos de conquista e o `createdAt` dos que continuam
     * existindo, para o histórico não perder a data. É a base do reconcile.
     */
    suspend fun replaceDomain(desired: List<XpEntry>) {
        store.updateData { current ->
            val previous = current
                .filter { isDomainRef(it.refKey) }
                .associateBy { it.refKey }
            val rebuilt = desired
                .filter { it.amount != 0 }
                .map { entry ->
                    previous[entry.refKey]?.copy(kind = entry.kind.raw, amount = entry.amount)
                        ?: XpEvent(kind = entry.kind.raw, refKey = entry.refKey, amount = entry.amount)
                }
            current.filterNot { isDomainRef(it.refKey) } + rebuilt
        }
    }

}

/** Lançamento desejado de XP (kind, valor e chave). */
data class XpEntry(val kind: XpKind, val amount: Int, val refKey: String)

/** Prefixos que o reconcile controla; conquistas ficam de fora. */
private val DOMAIN_PREFIXES = listOf("block:", "task:", "subtask:", "habit:", "routine:")

internal fun isDomainRef(refKey: String): Boolean =
    DOMAIN_PREFIXES.any { refKey.startsWith(it) }

/** Conquistas desbloqueadas, com o dia em que caíram. */
class AchievementRepository(context: Context) {
    private val store = context.applicationContext.achievementStore

    val unlocks: Flow<List<AchievementUnlock>> = store.data

    suspend fun all(): List<AchievementUnlock> = unlocks.first()

    suspend fun unlock(achievementId: String, on: LocalDate = LocalDate.now()) {
        store.updateData { current ->
            if (current.any { it.achievementId == achievementId }) {
                current
            } else {
                current + AchievementUnlock(achievementId, on.toString())
            }
        }
    }
}

/**
 * Liga as ações do app ao XP e às conquistas.
 *
 * O desenho é **reconciliar, não somar**: cada `onXChanged` olha o estado atual
 * daquele registro e garante que o lançamento de XP corresponda a ele. Assim,
 * desfazer, refazer, editar a nota ou reabrir a tarefa caem todos no mesmo lugar —
 * e nenhum caminho novo precisa lembrar de "tirar o XP".
 */
class GamificationEngine(
    private val xpRepository: XpRepository,
    private val achievementRepository: AchievementRepository,
    private val routineRepository: RoutineRepository,
    private val completionRepository: CompletionRepository,
    private val taskRepository: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val habitRepository: HabitRepository,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Fila de conquistas recém-desbloqueadas.
     *
     * Canal, e não `SharedFlow`: o app pode reconciliar antes de a tela começar a
     * coletar, e um `SharedFlow` sem assinante descartaria justamente a primeira
     * conquista. O canal guarda até alguém consumir.
     */
    private val celebrationChannel = Channel<String>(Channel.BUFFERED)

    /** Conquistas para celebrar, na ordem em que caíram. */
    val celebrations: Flow<String> = celebrationChannel.receiveAsFlow()

    /** Addon desligado = nada pontua (mesma regra de Conexões). */
    private suspend fun enabled(): Boolean = runCatching {
        settingsRepository.settings.first().isAddonEnabled(Addon.Gamification)
    }.getOrDefault(false)

    /** Retrato atual, usado para avaliar as conquistas. */
    private suspend fun stats(today: LocalDate): GamificationStats = gamificationStats(
        routines = routineRepository.routines.first(),
        completions = completionRepository.completions.first(),
        tasks = taskRepository.tasks.first(),
        subtasks = subtaskRepository.subtasks.first(),
        habits = habitRepository.habitList.first(),
        habitCompletions = habitRepository.completionList.first(),
        connections = connectionRepository.connections.first(),
        xpEvents = xpRepository.all(),
        today = today,
    )

    /**
     * Refaz todo o XP de domínio a partir dos dados atuais.
     *
     * O caminho normal pontua por evento (`onTaskChanged` etc.), mas há ações que
     * concluem entidades sem passar por essas telas — uma conexão que conclui a
     * tarefa, uma restauração de backup. Em vez de lembrar de chamar o XP em cada
     * canto, esta varredura garante que o histórico corresponda aos dados.
     */
    suspend fun reconcile(today: LocalDate = LocalDate.now()) {
        if (!enabled()) return

        val routines = routineRepository.routines.first()
        val completions = completionRepository.completions.first()
        val blocks = routines.flatMap { it.blocks }.associateBy { it.id }
        val desired = mutableListOf<XpEntry>()

        completions.forEach { completion ->
            val block = blocks[completion.blockId] ?: return@forEach
            desired += XpEntry(
                kind = XpKind.BlockConfirm,
                amount = blockXp(block.confirmation, completion.value),
                refKey = "block:${completion.blockId}:${completion.periodStart}",
            )
        }

        taskRepository.tasks.first().filter { it.done }.forEach { task ->
            desired += XpEntry(XpKind.TaskDone, XpAmounts.TASK_DONE, "task:${task.id}:done")
        }
        subtaskRepository.subtasks.first().filter { it.done }.forEach { subtask ->
            desired += XpEntry(XpKind.SubtaskDone, XpAmounts.SUBTASK_DONE, "subtask:${subtask.id}:done")
        }

        val habits = habitRepository.habitList.first()
        habitRepository.completionList.first().forEach { completion ->
            val habit = habits.firstOrNull { it.id == completion.habitId } ?: return@forEach
            val day = "habit:${completion.habitId}:${completion.date}"
            when (habit.kind) {
                HabitKind.Bad -> if (completion.count > 0) {
                    desired += XpEntry(XpKind.HabitSlip, XpAmounts.HABIT_SLIP, "$day:slip")
                }

                HabitKind.Good -> {
                    if (completion.count > 0) {
                        desired += XpEntry(XpKind.HabitConfirm, XpAmounts.HABIT_CONFIRM, "$day:confirm")
                    }
                    if (completion.count >= (habit.targetCount ?: 1)) {
                        desired += XpEntry(XpKind.HabitTarget, XpAmounts.HABIT_TARGET_BONUS, "$day:target")
                    }
                }
            }
        }

        // Dia 100%: um bônus por período em que todos os blocos confirmáveis fecharam.
        val active = routines.firstOrNull { it.isActive }
        if (active != null) {
            val confirmable = active.blocks.filter { it.confirmation != EventConfirmation.None }
            val confirmableIds = confirmable.map { it.id }.toSet()
            completions
                .filter { it.blockId in confirmableIds }
                .groupBy { it.periodStart }
                .forEach { (periodStart, list) ->
                    val done = list
                        .filter { completion ->
                            val block = confirmable.first { it.id == completion.blockId }
                            completionCounts(block.confirmation, completion.value)
                        }
                        .map { it.blockId }
                        .distinct()
                    if (done.size >= confirmable.size && confirmable.isNotEmpty()) {
                        desired += XpEntry(
                            XpKind.RoutineDayFull,
                            XpAmounts.ROUTINE_DAY_FULL,
                            "routine:dayFull:$periodStart",
                        )
                    }
                }
        }

        xpRepository.replaceDomain(desired)
        refreshAchievements(today)
    }

    suspend fun onTaskChanged(taskId: String) {
        if (!enabled()) return
        val task = taskRepository.tasks.first().firstOrNull { it.id == taskId } ?: return
        xpRepository.set(
            kind = XpKind.TaskDone,
            amount = if (task.done) XpAmounts.TASK_DONE else 0,
            refKey = "task:$taskId:done",
        )
        // Concluir a tarefa conclui a árvore: as sub-tarefas pontuam junto.
        subtaskRepository.subtasks.first()
            .filter { it.taskId == taskId }
            .forEach { subtask ->
                xpRepository.set(
                    kind = XpKind.SubtaskDone,
                    amount = if (subtask.done) XpAmounts.SUBTASK_DONE else 0,
                    refKey = "subtask:${subtask.id}:done",
                )
            }
        refreshAchievements()
    }

    suspend fun onSubtaskChanged(subtaskId: String) {
        if (!enabled()) return
        val subtask = subtaskRepository.subtasks.first().firstOrNull { it.id == subtaskId } ?: return
        xpRepository.set(
            kind = XpKind.SubtaskDone,
            amount = if (subtask.done) XpAmounts.SUBTASK_DONE else 0,
            refKey = "subtask:$subtaskId:done",
        )
        refreshAchievements()
    }

    /**
     * Marcação de hábito mudou.
     *
     * Bom: 5 por dia marcado (uma vez) + 10 quando a meta do dia é batida. Ruim: -15
     * por recaída. Desmarcar remove os lançamentos do dia.
     */
    suspend fun onHabitChanged(habitId: String, date: LocalDate) {
        if (!enabled()) return

        val habit = habitRepository.habitList.first().firstOrNull { it.id == habitId } ?: return
        val count = habitRepository.completionList.first()
            .firstOrNull { it.habitId == habitId && it.date == date.toString() }
            ?.count ?: 0

        val day = "habit:$habitId:$date"
        when (habit.kind) {
            HabitKind.Bad -> {
                xpRepository.set(XpKind.HabitSlip, if (count > 0) XpAmounts.HABIT_SLIP else 0, "$day:slip")
            }

            HabitKind.Good -> {
                val target = habit.targetCount ?: 1
                xpRepository.set(
                    XpKind.HabitConfirm,
                    if (count > 0) XpAmounts.HABIT_CONFIRM else 0,
                    "$day:confirm",
                )
                xpRepository.set(
                    XpKind.HabitTarget,
                    if (count >= target) XpAmounts.HABIT_TARGET_BONUS else 0,
                    "$day:target",
                )
            }
        }
        refreshAchievements()
    }

    /**
     * Desbloqueia conquistas cujas condições o retrato atual já satisfaz.
     *
     * Roda em laço porque o XP da conquista pode levar de nível e destravar outra
     * conquista de nível na mesma chamada.
     */
    suspend fun refreshAchievements(today: LocalDate = LocalDate.now()): List<String> {
        if (!enabled()) return emptyList()

        val unlocked = mutableListOf<String>()
        repeat(3) {
            val owned = achievementRepository.all().map { it.achievementId }.toSet()
            val stats = stats(today)
            val newOnes = ACHIEVEMENTS.filter { it.id !in owned && it.isSatisfiedBy(stats) }
            if (newOnes.isEmpty()) return unlocked

            newOnes.forEach { achievement ->
                achievementRepository.unlock(achievement.id, today)
                xpRepository.set(
                    kind = XpKind.Achievement,
                    amount = achievement.tier.xp,
                    refKey = "achievement:${achievement.id}",
                )
                unlocked += achievement.id
                celebrationChannel.trySend(achievement.id)
            }
        }
        return unlocked
    }
}
