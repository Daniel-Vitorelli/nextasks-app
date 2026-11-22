package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * Ligação entre uma entidade (tarefa, sub-tarefa ou hábito) e um bloco de horário.
 *
 * Espelha o `TaskBlockConnection` do app web: confirmar o bloco conta para a
 * entidade, e atingir [requiredCount] confirmações conclui a entidade.
 *
 * Exatamente um dos três ids de entidade é preenchido — o mesmo contrato que o
 * servidor do web valida.
 */
@Serializable
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    /** Rotina dona do bloco; guardada para exibir o nome e a frequência. */
    val routineId: String,
    val blockId: String,
    val taskId: String? = null,
    val subtaskId: String? = null,
    val habitId: String? = null,
    /** Quantas confirmações do bloco concluem a entidade (1 a 99). */
    val requiredCount: Int = 1,
    /** "all", "weekday:0..6" ou "date:YYYY-MM-DD". */
    val dayFilter: String = ALL_DAYS,
    /** Dia em que a conexão foi criada — confirmações anteriores não contam. */
    val createdOn: String = LocalDate.now().toString(),
) {
    val entityId: String?
        get() = taskId ?: subtaskId ?: habitId

    companion object {
        const val ALL_DAYS = "all"
        const val REQUIRED_MIN = 1
        const val REQUIRED_MAX = 99
    }
}

/** Filtro de dia da conexão, já interpretado. */
sealed interface DayFilter {
    data object All : DayFilter

    data class Weekday(val weekday: Int) : DayFilter

    data class Date(val date: LocalDate) : DayFilter

    companion object {
        fun parse(raw: String): DayFilter = when {
            raw.startsWith("weekday:") ->
                raw.substringAfter("weekday:").toIntOrNull()
                    ?.takeIf { it in 0..6 }
                    ?.let { Weekday(it) }
                    ?: All

            raw.startsWith("date:") ->
                runCatching { LocalDate.parse(raw.substringAfter("date:")) }
                    .getOrNull()
                    ?.let { Date(it) }
                    ?: All

            else -> All
        }
    }

    /** Volta para o formato gravado. */
    fun raw(): String = when (this) {
        All -> Connection.ALL_DAYS
        is Weekday -> "weekday:$weekday"
        is Date -> "date:$date"
    }
}

/** Uma confirmação do bloco entra na conta desta conexão? */
fun Connection.countsFor(completion: Completion): Boolean {
    if (completion.blockId != blockId) return false
    if (completion.value == "false" || completion.value.toIntOrNull() == 0) return false

    val period = runCatching { LocalDate.parse(completion.periodStart) }.getOrNull() ?: return false
    val created = runCatching { LocalDate.parse(createdOn) }.getOrNull() ?: return true
    if (period.isBefore(created)) return false

    return when (val filter = DayFilter.parse(dayFilter)) {
        DayFilter.All -> true
        is DayFilter.Weekday -> period.dayOfWeek.value % 7 == filter.weekday
        is DayFilter.Date -> period == filter.date
    }
}
