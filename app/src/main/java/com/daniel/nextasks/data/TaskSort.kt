package com.daniel.nextasks.data

import kotlinx.serialization.Serializable

/** Campo pelo qual as tarefas podem ser ordenadas. */
@Serializable
enum class TaskSortField {
    Priority,
    DueDate,
    StartDate,
    CreatedAt,
    Title,

    /** Quantas sub-tarefas ainda faltam — "o que está quase pronto" primeiro. */
    Subtasks,
}

/** Sentido da ordenação de um critério. */
@Serializable
enum class SortDirection {
    Ascending,
    Descending,
}

/** Um critério: campo + sentido. A ordem dos critérios é a ordem de desempate. */
@Serializable
data class TaskSortCriterion(
    val field: TaskSortField,
    val direction: SortDirection,
)

/**
 * Como as tarefas são ordenadas na Home e na lista de Tarefas.
 *
 * [groupByUrgency] mantém a separação inteligente (atrasadas → do dia → sem data →
 * ainda não começou) e aplica os critérios **dentro** de cada grupo. Desligado, a
 * ordenação é uma só, aplicada à lista inteira.
 */
@Serializable
data class TaskSortSettings(
    val groupByUrgency: Boolean = true,
    val criteria: List<TaskSortCriterion> = DEFAULT_CRITERIA,
) {
    companion object {
        /** Prioridade manda, prazo desempata e a mais antiga vem antes. */
        val DEFAULT_CRITERIA: List<TaskSortCriterion> = listOf(
            TaskSortCriterion(TaskSortField.Priority, SortDirection.Descending),
            TaskSortCriterion(TaskSortField.DueDate, SortDirection.Ascending),
            TaskSortCriterion(TaskSortField.CreatedAt, SortDirection.Ascending),
        )
    }
}

/** Combinações prontas — um atalho para não começar do zero. */
enum class TaskSortPreset(val settings: TaskSortSettings) {
    /** O padrão do app: prioridade, prazo e depois a mais antiga. */
    Balanced(TaskSortSettings()),

    /** O prazo manda; prioridade só desempata. */
    Deadline(
        TaskSortSettings(
            criteria = listOf(
                TaskSortCriterion(TaskSortField.DueDate, SortDirection.Ascending),
                TaskSortCriterion(TaskSortField.Priority, SortDirection.Descending),
            ),
        ),
    ),

    /** Só a prioridade, sem agrupar. */
    Priority(
        TaskSortSettings(
            groupByUrgency = false,
            criteria = listOf(
                TaskSortCriterion(TaskSortField.Priority, SortDirection.Descending),
                TaskSortCriterion(TaskSortField.DueDate, SortDirection.Ascending),
            ),
        ),
    ),

    /** O que foi criado por último primeiro. */
    Newest(
        TaskSortSettings(
            groupByUrgency = false,
            criteria = listOf(
                TaskSortCriterion(TaskSortField.CreatedAt, SortDirection.Descending),
            ),
        ),
    ),

    /** Ordem alfabética pura. */
    Alphabetical(
        TaskSortSettings(
            groupByUrgency = false,
            criteria = listOf(
                TaskSortCriterion(TaskSortField.Title, SortDirection.Ascending),
            ),
        ),
    ),
}
