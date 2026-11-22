package com.daniel.nextasks.ui.tasks

import com.daniel.nextasks.data.SortDirection
import com.daniel.nextasks.data.Task
import com.daniel.nextasks.data.TaskSortCriterion
import com.daniel.nextasks.data.TaskSortField
import com.daniel.nextasks.data.TaskSortSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Ordenação das tarefas: grupos de urgência + critérios escolhidos. */
class TaskSortingTest {

    private val today = LocalDate.of(2026, 11, 22)

    private fun task(
        id: String,
        dueDate: String? = null,
        startDate: String? = null,
        priority: Int = Task.DEFAULT_PRIORITY,
        done: Boolean = false,
        createdAt: Long = 0L,
    ) = Task(
        id = id,
        title = id,
        dueDate = dueDate,
        startDate = startDate,
        priority = priority,
        done = done,
        createdAt = createdAt,
    )

    @Test
    fun `grupos de urgencia vem na ordem atrasadas - do dia - sem data - nao comecadas`() {
        val tasks = listOf(
            task("nao-comecou", startDate = "2026-11-25"),
            task("sem-data"),
            task("do-dia", dueDate = "2026-11-22"),
            task("atrasada", dueDate = "2026-11-20"),
        )

        val sorted = sortPendingTasks(tasks, today)

        assertEquals(
            listOf("atrasada", "do-dia", "sem-data", "nao-comecou"),
            sorted.map { it.id },
        )
    }

    @Test
    fun `prioridade maior vem antes dentro do grupo`() {
        val tasks = listOf(
            task("baixa", dueDate = "2026-11-22", priority = 1),
            task("urgente", dueDate = "2026-11-22", priority = 6),
        )

        val sorted = sortPendingTasks(tasks, today)

        assertEquals(listOf("urgente", "baixa"), sorted.map { it.id })
    }

    @Test
    fun `sem agrupar, o criterio vale para a lista inteira`() {
        val settings = TaskSortSettings(groupByUrgency = false)
        val tasks = listOf(
            task("atrasada-baixa", dueDate = "2026-11-20", priority = 1),
            task("sem-data-urgente", priority = 6),
        )

        val sorted = sortPendingTasks(tasks, today, settings)

        assertEquals(listOf("sem-data-urgente", "atrasada-baixa"), sorted.map { it.id })
    }

    @Test
    fun `tarefa sem prazo fica no fim mesmo na ordem decrescente de prazo`() {
        val settings = TaskSortSettings(
            groupByUrgency = false,
            criteria = listOf(TaskSortCriterion(TaskSortField.DueDate, SortDirection.Descending)),
        )
        val tasks = listOf(
            task("sem-prazo"),
            task("com-prazo", dueDate = "2026-12-01"),
        )

        val sorted = sortPendingTasks(tasks, today, settings)

        assertEquals(listOf("com-prazo", "sem-prazo"), sorted.map { it.id })
    }

    @Test
    fun `concluidas ficam no fim, da mais recente para a mais antiga`() {
        val tasks = listOf(
            task("aberta"),
            task("feita-antiga", done = true, createdAt = 100),
            task("feita-nova", done = true, createdAt = 200),
        )

        val sorted = sortTasks(tasks, today)

        assertEquals(listOf("aberta", "feita-nova", "feita-antiga"), sorted.map { it.id })
    }

    @Test
    fun `criterio de subtarefas pendentes sobe quem esta mais perto de acabar`() {
        val settings = TaskSortSettings(
            groupByUrgency = false,
            criteria = listOf(TaskSortCriterion(TaskSortField.Subtasks, SortDirection.Ascending)),
        )
        val tasks = listOf(task("muitas"), task("poucas"), task("nenhuma"))
        val pending = mapOf("muitas" to 5, "poucas" to 1, "nenhuma" to 0)

        val sorted = sortPendingTasks(tasks, today, settings, pending)

        assertEquals(listOf("nenhuma", "poucas", "muitas"), sorted.map { it.id })
    }

    @Test
    fun `desempate final pela criacao evita ordem aleatoria`() {
        val settings = TaskSortSettings(groupByUrgency = false)
        val tasks = listOf(
            task("segunda", priority = 3, createdAt = 2),
            task("primeira", priority = 3, createdAt = 1),
        )

        val sorted = sortPendingTasks(tasks, today, settings)

        assertEquals(listOf("primeira", "segunda"), sorted.map { it.id })
    }

    @Test
    fun `bucketOf classifica cada caso`() {
        assertEquals(TaskBucket.Overdue, bucketOf(task("x", dueDate = "2026-11-21"), today))
        assertEquals(TaskBucket.Due, bucketOf(task("x", dueDate = "2026-11-22"), today))
        assertEquals(TaskBucket.NoDue, bucketOf(task("x"), today))
        assertEquals(
            TaskBucket.NotStarted,
            bucketOf(task("x", startDate = "2026-11-23"), today),
        )
    }

    @Test
    fun `data invalida nao quebra a classificacao`() {
        assertEquals(TaskBucket.NoDue, bucketOf(task("x", dueDate = "22/11/2026"), today))
    }
}
