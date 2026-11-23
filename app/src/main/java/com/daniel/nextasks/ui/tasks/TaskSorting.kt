package com.daniel.nextasks.ui.tasks

import com.daniel.nextasks.data.SortDirection
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.TaskSortCriterion
import com.daniel.nextasks.data.TaskSortField
import com.daniel.nextasks.data.TaskSortSettings
import com.daniel.nextasks.data.parseIsoDate
import java.time.LocalDate

/** Onde a tarefa cai na fila do dia. A ordem dos valores é a ordem de exibição. */
internal enum class TaskBucket {
    /** Prazo já passou — o que ficou para trás vem primeiro. */
    Overdue,

    /** Tem prazo (hoje ou à frente) e já pode ser feita. */
    Due,

    /** Sem prazo: pode ser feita a qualquer momento. */
    NoDue,

    /** Ainda não começou: não adianta cobrar antes da data de início. */
    NotStarted,
}

private fun String?.toLocalDateOrNull(): LocalDate? = parseIsoDate(this)

internal fun bucketOf(task: Task, today: LocalDate): TaskBucket {
    val due = task.dueDate.toLocalDateOrNull()
    val start = task.startDate.toLocalDateOrNull()

    return when {
        due != null && due.isBefore(today) -> TaskBucket.Overdue
        start != null && start.isAfter(today) -> TaskBucket.NotStarted
        due != null -> TaskBucket.Due
        else -> TaskBucket.NoDue
    }
}

/**
 * Tarefas pendentes na ordem escolhida pelo usuário.
 *
 * Com [TaskSortSettings.groupByUrgency] ligado, os grupos continuam mandando
 * (atrasadas → do dia → sem data → ainda não começou) e os critérios ordenam
 * dentro de cada grupo. Desligado, os critérios valem para a lista inteira.
 */
internal fun sortPendingTasks(
    tasks: List<Task>,
    today: LocalDate,
    settings: TaskSortSettings = TaskSortSettings(),
    /** Sub-tarefas pendentes por tarefa; ausente = nenhuma. */
    pendingSubtasks: Map<String, Int> = emptyMap(),
): List<Task> {
    val pending = tasks.filterNot { it.done }

    if (!settings.groupByUrgency) {
        return orderGroup(pending, bucket = null, settings = settings, pendingSubtasks)
    }

    return TaskBucket.entries.flatMap { bucket ->
        orderGroup(
            tasks = pending.filter { bucketOf(it, today) == bucket },
            bucket = bucket,
            settings = settings,
            pendingSubtasks = pendingSubtasks,
        )
    }
}

/** Lista completa: pendentes na ordem escolhida e concluídas no fim. */
internal fun sortTasks(
    tasks: List<Task>,
    today: LocalDate,
    settings: TaskSortSettings = TaskSortSettings(),
    pendingSubtasks: Map<String, Int> = emptyMap(),
): List<Task> = sortPendingTasks(tasks, today, settings, pendingSubtasks) +
    tasks.filter { it.done }.sortedByDescending { it.createdAt }

/**
 * Aplica os critérios dentro de um grupo.
 *
 * Duas regras entram sem o usuário pedir: tarefas que ainda não começaram são
 * ordenadas por quando começam (é o que define o grupo) e a data de criação
 * fecha o desempate, para a ordem nunca depender do acaso.
 */
private fun orderGroup(
    tasks: List<Task>,
    bucket: TaskBucket?,
    settings: TaskSortSettings,
    pendingSubtasks: Map<String, Int>,
): List<Task> {
    if (tasks.size < 2) return tasks

    val comparators = buildList {
        if (bucket == TaskBucket.NotStarted &&
            settings.criteria.none { it.field == TaskSortField.StartDate }
        ) {
            add(
                comparatorFor(
                    TaskSortCriterion(TaskSortField.StartDate, SortDirection.Ascending),
                    pendingSubtasks,
                ),
            )
        }
        settings.criteria.forEach { add(comparatorFor(it, pendingSubtasks)) }
        if (settings.criteria.none { it.field == TaskSortField.CreatedAt }) {
            add(
                comparatorFor(
                    TaskSortCriterion(TaskSortField.CreatedAt, SortDirection.Ascending),
                    pendingSubtasks,
                ),
            )
        }
    }

    return tasks.sortedWith(comparators.reduce { acc, comparator -> acc.thenComparing(comparator) })
}

private fun comparatorFor(
    criterion: TaskSortCriterion,
    pendingSubtasks: Map<String, Int>,
): Comparator<Task> {
    val direction = if (criterion.direction == SortDirection.Descending) -1 else 1

    return Comparator { a, b ->
        when (criterion.field) {
            TaskSortField.Priority -> a.priority.compareTo(b.priority) * direction
            TaskSortField.DueDate -> compareDates(a.dueDate, b.dueDate, direction)
            TaskSortField.StartDate -> compareDates(a.startDate, b.startDate, direction)
            TaskSortField.CreatedAt -> a.createdAt.compareTo(b.createdAt) * direction
            TaskSortField.Title -> a.title.compareTo(b.title, ignoreCase = true) * direction
            TaskSortField.Subtasks -> {
                val left = pendingSubtasks[a.id] ?: 0
                val right = pendingSubtasks[b.id] ?: 0
                left.compareTo(right) * direction
            }
        }
    }
}

/**
 * Datas ausentes ficam **sempre no fim**, inclusive na ordem decrescente — uma
 * tarefa sem prazo não pode virar "a mais distante" e liderar a lista.
 */
private fun compareDates(a: String?, b: String?, direction: Int): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    else -> a.compareTo(b) * direction
}
