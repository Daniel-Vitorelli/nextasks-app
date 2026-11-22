package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Sub-tarefa de uma tarefa, em árvore — espelha o `model Subtask` do app web.
 *
 * `parentId` nulo significa raiz da tarefa; qualquer outro valor aponta para a
 * sub-tarefa pai, permitindo níveis arbitrários.
 */
@Serializable
data class Subtask(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val parentId: String? = null,
    val title: String,
    val description: String? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
