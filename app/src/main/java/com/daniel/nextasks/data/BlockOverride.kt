package com.daniel.nextasks.data

import kotlinx.serialization.Serializable

/**
 * Exceção de **um dia específico** para um bloco de rotina.
 *
 * Editar ou excluir um bloco no calendário não altera a rotina: cria uma
 * exceção com a chave (rotina, bloco, dia). A rotina continua igual na tela de
 * Rotinas; o calendário e a Home passam a usar esta versão.
 *
 * `deleted = true` esconde a ocorrência naquele dia. Caso contrário, [block]
 * traz a versão substituta (mantendo o mesmo `id`, para as conclusões não se
 * perderem).
 */
@Serializable
data class BlockOverride(
    val routineId: String,
    val blockId: String,
    /** Dia da ocorrência, em ISO (yyyy-MM-dd). */
    val date: String,
    val deleted: Boolean = false,
    val block: RoutineBlock? = null,
) {
    /** Chave de unicidade da exceção. */
    val key: Triple<String, String, String> get() = Triple(routineId, blockId, date)
}
