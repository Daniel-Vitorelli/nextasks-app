package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras da árvore de sub-tarefas — as mesmas do `subtask-cascade.ts` do web.
 *
 * É a regra que mais dói se quebrar: concluir a tarefa errada, ou deixar a
 * tarefa aberta com tudo embaixo concluído.
 */
class SubtaskCascadeTest {

    private fun sub(
        id: String,
        parentId: String? = null,
        done: Boolean = false,
        taskId: String = "t1",
        createdAt: Long = 0L,
    ) = Subtask(
        id = id,
        taskId = taskId,
        parentId = parentId,
        title = id,
        done = done,
        createdAt = createdAt,
    )

    @Test
    fun `concluir o unico filho conclui o pai e a tarefa`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"))

        val result = completeSubtask(list, "b")

        assertTrue(result.subtasks.first { it.id == "b" }.done)
        assertTrue("pai com todos os filhos concluídos fecha", result.subtasks.first { it.id == "a" }.done)
        assertTrue(result.taskDone)
    }

    @Test
    fun `concluir um filho nao conclui o pai enquanto o irmao estiver aberto`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"), sub("c", parentId = "a"))

        val result = completeSubtask(list, "b")

        assertTrue(result.subtasks.first { it.id == "b" }.done)
        assertFalse(result.subtasks.first { it.id == "a" }.done)
        assertFalse(result.taskDone)
    }

    @Test
    fun `concluir o ultimo filho fecha o pai e a tarefa`() {
        val list = listOf(
            sub("a"),
            sub("b", parentId = "a", done = true),
            sub("c", parentId = "a"),
        )

        val result = completeSubtask(list, "c")

        assertTrue(result.subtasks.first { it.id == "a" }.done)
        assertTrue(result.taskDone)
    }

    @Test
    fun `concluir um no conclui toda a sub-arvore abaixo`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"), sub("c", parentId = "b"))

        val result = completeSubtask(list, "a")

        assertTrue(result.subtasks.all { it.done })
        assertTrue(result.taskDone)
    }

    @Test
    fun `reabrir um filho reabre os ancestrais e a tarefa`() {
        val list = listOf(
            sub("a", done = true),
            sub("b", parentId = "a", done = true),
        )

        val result = reopenSubtask(list, "b")

        assertFalse(result.subtasks.first { it.id == "b" }.done)
        assertFalse("ancestral volta a abrir", result.subtasks.first { it.id == "a" }.done)
        assertFalse(result.taskDone)
    }

    @Test
    fun `reabrir um no nao mexe nos irmaos`() {
        val list = listOf(
            sub("a", done = true),
            sub("b", parentId = "a", done = true),
            sub("c", parentId = "a", done = true),
        )

        val result = reopenSubtask(list, "b")

        assertTrue("o irmão c continua concluído", result.subtasks.first { it.id == "c" }.done)
        assertFalse(result.subtasks.first { it.id == "a" }.done)
    }

    @Test
    fun `excluir um filho recalcula o pai - sem filhos conta como concluido`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"))

        val result = deleteSubtask(list, "b")

        assertEquals(listOf("a"), result.subtasks.map { it.id })
        assertTrue("pai sem filhos conta como concluído, como no web", result.subtasks.single().done)
        assertTrue(result.taskDone)
    }

    @Test
    fun `excluir um no leva a sub-arvore junto`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"), sub("c", parentId = "b"))

        val result = deleteSubtask(list, "a")

        assertTrue(result.subtasks.isEmpty())
    }

    @Test
    fun `tarefa com duas raizes so fecha quando as duas fecham`() {
        val list = listOf(sub("a", done = true), sub("b"))

        val result = completeSubtask(list, "b")

        assertTrue(result.taskDone)

        val parcial = completeSubtask(listOf(sub("a"), sub("b")), "a")
        assertFalse("ainda falta a raiz b", parcial.taskDone)
    }

    @Test
    fun `no inexistente nao altera nada`() {
        val list = listOf(sub("a"))

        val result = completeSubtask(list, "nao-existe")

        assertEquals(list, result.subtasks)
        assertFalse(result.taskDone)
    }

    @Test
    fun `descendantIds devolve a sub-arvore sem o proprio no`() {
        val list = listOf(sub("a"), sub("b", parentId = "a"), sub("c", parentId = "b"))

        assertEquals(setOf("b", "c"), list.descendantIds("a"))
    }
}
