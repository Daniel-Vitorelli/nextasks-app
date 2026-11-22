package com.daniel.nextasks.data

import kotlinx.serialization.Serializable

/**
 * Conclusão de um bloco de horário num período.
 *
 * Espelha o `TimeBlockCompletion` do app web: a chave é (bloco, início do período),
 * onde o período é o **dia** em rotinas diárias e a **semana** em rotinas semanais.
 * `value` guarda "true"/"false" (checklist) ou a nota de 1 a 5 (score).
 */
@Serializable
data class Completion(
    val blockId: String,
    val periodStart: String,
    val value: String,
)
