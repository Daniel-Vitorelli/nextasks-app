package com.daniel.nextasks.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Por que uma conexão não pode disparar — mesmo que o bloco seja confirmado.
 *
 * Só entram motivos **permanentes**: "hoje não é o dia do bloco" é rotina normal de
 * uma rotina semanal, não defeito. A ideia é responder "por que isso nunca acontece?"
 * sem o usuário precisar abrir rotina por rotina.
 */
enum class BlockBlocker {
    /** A rotina está desligada; os blocos dela não ocorrem. */
    RoutineInactive,

    /** A rotina tem data final e já passou dela. */
    RoutineEnded,

    /** A rotina ainda não começou (data de criação no futuro). */
    RoutineNotStarted,

    /** O bloco tem horário que não dá para interpretar. */
    InvalidTime,
}

/** Motivo pelo qual este bloco não acontece mais, ou nulo se está tudo bem. */
fun blockBlocker(
    routine: Routine,
    block: RoutineBlock,
    today: LocalDate,
): BlockBlocker? = when {
    !routine.isActive -> BlockBlocker.RoutineInactive
    today.isBefore(routine.createdOnDate()) -> BlockBlocker.RoutineNotStarted
    // Inativa e antes da criação já saíram; se `coversDay` ainda diz não, é o fim.
    !routine.coversDay(today) -> BlockBlocker.RoutineEnded
    parseIsoTime(block.startTime) == null || parseIsoTime(block.endTime) == null ->
        BlockBlocker.InvalidTime

    else -> null
}

/** Janela usada para medir o ritmo de confirmações (dias). */
private const val PACE_WINDOW_DAYS = 60L

/**
 * Quantos dias faltam para bater a meta, no ritmo que o usuário vem confirmando.
 *
 * Diferente da estimativa do editor (que é só `requiredCount`), esta olha o histórico:
 * quem confirma o bloco 4 vezes por semana chega na meta antes de quem confirma 1.
 *
 * Devolve nulo quando não dá para estimar: já bateu a meta, a conexão é nova demais
 * (menos de 3 dias) ou ainda não teve nenhuma confirmação.
 */
fun paceEstimate(
    connection: Connection,
    completions: List<Completion>,
    today: LocalDate,
): Int? {
    val confirmed = completions.count { connection.countsFor(it) }
    val remaining = connection.requiredCount - confirmed
    if (remaining <= 0) return null

    val created = parseIsoDate(connection.createdOn) ?: return null
    val from = maxOf(created, today.minusDays(PACE_WINDOW_DAYS - 1))
    val days = ChronoUnit.DAYS.between(from, today).toInt() + 1
    if (days < 3) return null

    val inWindow = completions.count { completion ->
        if (!connection.countsFor(completion)) return@count false
        val day = parseIsoDate(completion.periodStart) ?: return@count false
        !day.isBefore(from) && !day.isAfter(today)
    }
    if (inWindow == 0) return null

    // Regra de três em vez de dividir pela taxa: evita o erro de ponto flutuante
    // que transformava "1 confirmação a cada 7 dias" em 8 dias.
    val daysNeeded = ceil(remaining.toDouble() * days / inWindow).toInt()
    return daysNeeded.coerceIn(1, 999)
}
