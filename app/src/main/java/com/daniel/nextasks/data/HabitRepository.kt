package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

internal val Context.habitStore: DataStore<List<Habit>> by dataStore(
    fileName = "habits.json",
    serializer = JsonListSerializer(Habit.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

internal val Context.habitCompletionStore: DataStore<List<HabitCompletion>> by dataStore(
    fileName = "habit_completions.json",
    serializer = JsonListSerializer(HabitCompletion.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/** Repositório dos hábitos e das marcações diárias. */
class HabitRepository(context: Context) {
    private val habits = context.applicationContext.habitStore
    private val completions = context.applicationContext.habitCompletionStore

    val habitList: Flow<List<Habit>> = habits.data
    val completionList: Flow<List<HabitCompletion>> = completions.data

    suspend fun save(habit: Habit) {
        habits.updateData { current ->
            if (current.any { it.id == habit.id }) {
                current.map { if (it.id == habit.id) habit else it }
            } else {
                current + habit
            }
        }
    }

    suspend fun delete(habitId: String) {
        habits.updateData { current -> current.filterNot { it.id == habitId } }
        completions.updateData { current -> current.filterNot { it.habitId == habitId } }
    }

    /**
     * Cópia do hábito **sem** o histórico.
     *
     * Copiar as marcações inflaria a sequência e as estatísticas dos dois hábitos —
     * a cópia nasce zerada, pronta para ser ajustada.
     */
    suspend fun duplicate(habitId: String) {
        habits.updateData { current ->
            val original = current.firstOrNull { it.id == habitId } ?: return@updateData current
            current + original.copy(
                id = UUID.randomUUID().toString(),
                name = "${original.name} (cópia)",
                createdAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Marca o dia. Hábito bom satura na meta; ruim acumula recaídas.
     * Devolve a contagem do dia depois da marcação.
     */
    suspend fun complete(habit: Habit, date: LocalDate, increment: Int = 1): Int {
        var result = 0
        val key = date.toString()
        completions.updateData { current ->
            val existing = current.firstOrNull { it.habitId == habit.id && it.date == key }
            val nextCount = applyCompletion(habit, existing?.count ?: 0, increment)
            result = nextCount
            if (existing == null) {
                current + HabitCompletion(habitId = habit.id, date = key, count = nextCount)
            } else {
                current.map {
                    if (it.habitId == habit.id && it.date == key) {
                        it.copy(count = nextCount)
                    } else {
                        it
                    }
                }
            }
        }
        return result
    }

    /**
     * Garante **pelo menos** [minimum] marcações no dia, sem somar em cima do que já existe.
     *
     * Usado pelo motor de conexões: confirmar o mesmo bloco de novo, ou desmarcar e
     * remarcar, não pode inflar a contagem. Antes o motor chamava `complete` com
     * `increment = targetCount` a cada confirmação e a meta estourava.
     */
    suspend fun ensureCount(
        habit: Habit,
        date: LocalDate,
        minimum: Int = habit.targetCount ?: 1,
    ): Int {
        var result = 0
        val key = date.toString()
        completions.updateData { current ->
            val existing = current.firstOrNull { it.habitId == habit.id && it.date == key }
            val nextCount = maxOf(existing?.count ?: 0, minimum.coerceAtLeast(1))
            result = nextCount
            when {
                existing == null ->
                    current + HabitCompletion(habitId = habit.id, date = key, count = nextCount)

                existing.count == nextCount -> current
                else -> current.map {
                    if (it.habitId == habit.id && it.date == key) {
                        it.copy(count = nextCount)
                    } else {
                        it
                    }
                }
            }
        }
        return result
    }

    /** Desfaz uma marcação do dia. */
    suspend fun undo(habitId: String, date: LocalDate) {
        val key = date.toString()
        completions.updateData { current ->
            val existing = current.firstOrNull { it.habitId == habitId && it.date == key }
                ?: return@updateData current
            if (existing.count <= 1) {
                current.filterNot { it.habitId == habitId && it.date == key }
            } else {
                current.map {
                    if (it.habitId == habitId && it.date == key) {
                        it.copy(count = it.count - 1)
                    } else {
                        it
                    }
                }
            }
        }
    }
}
