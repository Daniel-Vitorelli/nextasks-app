package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Tarefa avulsa do usuário — espelha o `model Task` do app web, com um campo
 * a mais: [startDate].
 *
 * `startDate` e `dueDate` ficam como data ISO (yyyy-MM-dd), sem hora, porque no
 * app o prazo é sempre um dia inteiro. `priority` vai de 1 (muito baixa) a 6 (urgente).
 */
@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    /** Quando a tarefa passa a valer. Nulo = sem data de início. */
    val startDate: String? = null,
    val dueDate: String? = null,
    val priority: Int = DEFAULT_PRIORITY,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_PRIORITY = 3
        val PRIORITIES = 1..6
    }
}
