package com.daniel.nextasks.data

import java.text.Normalizer
import java.util.Locale

/**
 * Uma conexão que o app sugere por semelhança de nome.
 *
 * Exemplo: bloco "Meditar" da rotina da manhã e hábito "Meditar" — provavelmente
 * foram feitos um para o outro.
 */
data class ConnectionSuggestion(
    val routineId: String,
    val routineName: String,
    val blockId: String,
    val blockTitle: String,
    val taskId: String? = null,
    val subtaskId: String? = null,
    val habitId: String? = null,
    val entityName: String,
    /** Rotina semanal: a meta fica em 1 porque só há uma confirmação por semana. */
    val weekly: Boolean = false,
    /** Meta sugerida para esta conexão. */
    val suggestedGoal: Int = 1,
)

/** Minúsculas, sem acento e com espaços normalizados. */
private fun normalize(value: String): String {
    val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Nota de semelhança entre o nome do bloco e o da entidade.
 *
 * 2 = nomes iguais, 1 = um contém o outro (com pelo menos 4 letras), 0 = nada.
 * O limite de 4 letras evita que "ir" case com "dormir".
 */
private fun similarity(blockTitle: String, entityName: String): Int {
    val block = normalize(blockTitle)
    val entity = normalize(entityName)
    if (block.isEmpty() || entity.isEmpty()) return 0
    if (block == entity) return 2
    if (entity.length >= 4 && block.contains(entity)) return 1
    if (block.length >= 4 && entity.contains(block)) return 1
    return 0
}

/**
 * Sugere conexões entre blocos e entidades de nome parecido que ainda não estão
 * ligados. Ordena por força da semelhança e corta em [limit].
 */
fun suggestConnections(
    routines: List<Routine>,
    tasks: List<Task>,
    subtasks: List<Subtask>,
    habits: List<Habit>,
    existing: List<Connection>,
    limit: Int = 3,
): List<ConnectionSuggestion> {
    val taken = existing.mapNotNull { link ->
        link.entityId?.let { link.blockId to it }
    }.toSet()

    val candidates = mutableListOf<Pair<Int, ConnectionSuggestion>>()

    // Hábito com meta 3x por dia pede meta 3; o resto começa em 1.
    fun goalFor(habitId: String?): Int =
        habits.firstOrNull { it.id == habitId }?.targetCount ?: 1

    routines.forEach { routine ->
        routine.blocks.forEach { block ->
            fun add(
                score: Int,
                taskId: String? = null,
                subtaskId: String? = null,
                habitId: String? = null,
                name: String,
            ) {
                if (score == 0) return
                val entityId = taskId ?: subtaskId ?: habitId ?: return
                if (block.id to entityId in taken) return
                val weekly = routine.frequency == RoutineFrequency.Weekly
                candidates += score to ConnectionSuggestion(
                    routineId = routine.id,
                    routineName = routine.name,
                    blockId = block.id,
                    blockTitle = block.title,
                    taskId = taskId,
                    subtaskId = subtaskId,
                    habitId = habitId,
                    entityName = name,
                    weekly = weekly,
                    suggestedGoal = if (weekly) 1 else goalFor(habitId),
                )
            }

            tasks.filterNot { it.done }.forEach { task ->
                add(similarity(block.title, task.title), taskId = task.id, name = task.title)
            }
            subtasks.filterNot { it.done }.forEach { subtask ->
                add(similarity(block.title, subtask.title), subtaskId = subtask.id, name = subtask.title)
            }
            habits.forEach { habit ->
                add(similarity(block.title, habit.name), habitId = habit.id, name = habit.name)
            }
        }
    }

    return candidates
        .sortedByDescending { it.first }
        .distinctBy { it.second.blockId to (it.second.taskId ?: it.second.subtaskId ?: it.second.habitId) }
        .take(limit)
        .map { it.second }
}
