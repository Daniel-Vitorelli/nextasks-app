package com.daniel.nextasks.data

import java.time.LocalDate

/**
 * Em quais dias da semana um bloco de rotina semanal acontece.
 *
 * Um bloco pode estar em **vários** dias (Seg/Qua/Sex, por exemplo). O campo antigo
 * `weekday` guardava um só dia; o modelo atual é `weekdays` e `weekday` continua sendo
 * lido apenas como compatibilidade (ver [RoutineBlock.scheduledWeekdays]).
 *
 * Regra pura, sem Android: calendário, Home, prévia de suspensão e o motor de conexões
 * usam **a mesma** resposta. Antes cada tela comparava `dayOfWeek` do seu jeito.
 */
object RoutineBlockDays {

    /** Todos os dias, domingo primeiro (0 = domingo … 6 = sábado). */
    val All: List<Int> = (0..6).toList()

    /** Índice do dia da semana de [day], com domingo valendo 0. */
    fun indexOf(day: LocalDate): Int = day.dayOfWeek.value % 7

    /** Este bloco acontece em [day]? Vale só para rotinas semanais. */
    fun happensOn(block: RoutineBlock, day: LocalDate): Boolean =
        indexOf(day) in block.scheduledWeekdays

    /**
     * Limpa uma seleção vinda da tela: descarta valores fora de 0..6, remove repetidos
     * e devolve em ordem. Seleção vazia vira [All] — bloco que não acontece em dia
     * nenhum sumiria do calendário sem aviso, então o vazio nunca é gravado.
     */
    fun sanitize(days: Collection<Int>): List<Int> {
        val valid = valid(days)
        return valid.ifEmpty { All }
    }

    /** Só os dias válidos de [days], em ordem e sem repetição — vazio se não sobrar nada. */
    fun valid(days: Collection<Int>): List<Int> = days.filter { it in 0..6 }.distinct().sorted()

    /**
     * Dias que sobraram depois de desligar [day].
     *
     * O último dia ligado não pode ser desligado: é o que mantém a invariante de
     * "pelo menos um dia".
     */
    fun toggle(days: Collection<Int>, day: Int): List<Int> {
        val current = sanitize(days)
        return if (day in current) {
            if (current.size == 1) current else current - day
        } else {
            sanitize(current + day)
        }
    }
}

/**
 * Os dias da semana em que este bloco acontece, sempre com pelo menos um.
 *
 * `weekdays` é a verdade; `weekday` só entra quando ele está vazio (bloco gravado
 * antes da v3.49) **ou** quando a lista não tem nenhum dia válido — backup remendado
 * com `[9]` não pode virar "todos os dias", que esconderia o problema no calendário.
 * Por isso o valor padrão de `weekdays` no modelo é vazio: se fosse `All`, um bloco
 * criado na versão anterior apareceria em todos os dias.
 */
val RoutineBlock.scheduledWeekdays: List<Int>
    get() = RoutineBlockDays.valid(weekdays).ifEmpty { listOf(weekday.coerceIn(0, 6)) }

/** Verdadeiro quando o bloco é de todos os dias da semana. */
val RoutineBlock.coversWholeWeek: Boolean
    get() = scheduledWeekdays.size == 7

/**
 * Primeiro dia da semana de [this]: domingo, como no app web.
 *
 * Existe aqui (e não só no calendário) porque o motor de conexões também precisa
 * comparar semanas, e `data/` não pode depender de `ui/`.
 */
fun LocalDate.weekStartSunday(): LocalDate = minusDays((dayOfWeek.value % 7).toLong())
