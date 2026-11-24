package com.daniel.nextasks.ui.home

import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.BlockOverride
import com.daniel.nextasks.data.Completion
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.dayIsFullySuspended
import com.daniel.nextasks.data.dueBlocksConfirmed
import com.daniel.nextasks.data.suspensions
import com.daniel.nextasks.ui.calendar.CalendarEvent
import com.daniel.nextasks.ui.calendar.routineOccurrences
import java.time.LocalDate
import kotlin.math.roundToInt

/** Janela usada para calcular a sequência. Cobre mais de um ano de histórico. */
private const val STREAK_DAYS = 365

/**
 * Números da Home, com a mesma semântica de `/api/routines/progress` do app web:
 *
 * - só a rotina **ativa** conta;
 * - checklist vale 1; nota vale `valor / máximo` (nota cheia = 1);
 * - o percentual do dia é a média dos blocos confirmáveis daquele dia;
 * - **dia completo** (100%) é o que alimenta a sequência.
 */
data class RoutineStats(
    val todayPercent: Int? = null,
    val todayConfirmable: Int = 0,
    val todayDone: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

/** Valor de 0 a 1 que um bloco entrega no dia (checklist) ou no período (nota). */
internal fun completionWeight(
    event: CalendarEvent,
    completions: Map<Pair<String, String>, Completion>,
): Double {
    val blockId = event.blockId ?: return 0.0
    val period = event.completionPeriodStart?.toString() ?: return 0.0
    val raw = completions[blockId to period]?.value ?: return 0.0

    return when (event.confirmation) {
        EventConfirmation.Checklist -> if (raw == "true") 1.0 else 0.0
        EventConfirmation.Score -> {
            val score = raw.toIntOrNull() ?: 0
            val max = event.scoreMax.coerceAtLeast(2)
            (score.toDouble() / max).coerceIn(0.0, 1.0)
        }

        EventConfirmation.None -> 0.0
    }
}

internal fun completionMap(completions: List<Completion>): Map<Pair<String, String>, Completion> =
    completions.associateBy { it.blockId to it.periodStart }

/** Um dia da série do gráfico. `percent` é nulo quando não havia bloco confirmável. */
data class DayProgress(
    val date: LocalDate,
    val percent: Int?,
    val confirmable: Int,
    /** Quantos blocos do dia foram concluídos por inteiro (nota cheia conta). */
    val done: Int = 0,
)

/**
 * Série diária de progresso (0–100) para os dias pedidos.
 *
 * Mesma conta de `/api/routines/progress`: média dos blocos confirmáveis do dia,
 * com checklist valendo 1 e nota valendo `valor / máximo`.
 */
fun progressSeries(
    routine: Routine?,
    completions: List<Completion>,
    days: List<LocalDate>,
    overrides: List<BlockOverride> = emptyList(),
    extras: List<ExtraBlock> = emptyList(),
    agendaEvents: List<AgendaEvent> = emptyList(),
): List<DayProgress> {
    if (routine == null) return days.map { DayProgress(it, null, 0) }

    val events = routineOccurrences(listOf(routine), days, overrides, extras, agendaEvents)
        .filter { it.confirmation != EventConfirmation.None }
    if (events.isEmpty()) return days.map { DayProgress(it, null, 0) }

    val byKey = completionMap(completions)
    val byDay = events.groupBy { it.start.toLocalDate() }

    return days.map { day ->
        val dayEvents = byDay[day].orEmpty()
        if (dayEvents.isEmpty()) {
            DayProgress(day, null, 0)
        } else {
            val weights = dayEvents.map { completionWeight(it, byKey) }
            DayProgress(
                date = day,
                percent = ((weights.sum() / dayEvents.size) * 100).roundToInt(),
                confirmable = dayEvents.size,
                done = weights.count { it >= 0.999 },
            )
        }
    }
}

fun routineStats(
    routine: Routine?,
    completions: List<Completion>,
    today: LocalDate,
    overrides: List<BlockOverride> = emptyList(),
    extras: List<ExtraBlock> = emptyList(),
    agendaEvents: List<AgendaEvent> = emptyList(),
): RoutineStats {
    if (routine == null) return RoutineStats()

    val suspensions = agendaEvents.suspensions()
    val days = (STREAK_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val series = progressSeries(routine, completions, days, overrides, extras, agendaEvents)
    if (series.none { it.confirmable > 0 }) return RoutineStats()

    var currentStreak = 0
    var longestStreak = 0
    var run = 0
    val fullDays = mutableSetOf<LocalDate>()
    val suspendedDays = mutableSetOf<LocalDate>()

    var todayPercent: Int? = null
    var todayConfirmable = 0
    var todayDone = 0

    for (point in series) {
        val day = point.date
        if (day == today) {
            todayConfirmable = point.confirmable
            todayPercent = point.percent
            todayDone = point.done
        }

        // Imprevisto que tomou o dia: a rotina foi suspensa, então o dia **pausa** a
        // sequência em vez de quebrá-la. Não é dia completo (não houve o que cumprir),
        // mas também não é dia perdido.
        if (point.confirmable == 0 && routine.dayIsFullySuspended(day, suspensions)) {
            suspendedDays += day
            // Imprevisto registrado depois: o que já estava cumprido continua valendo.
            if (routine.dueBlocksConfirmed(day, completions)) {
                fullDays += day
                run += 1
                if (run > longestStreak) longestStreak = run
            }
            continue
        }

        // A corrida conta dias **ativos** consecutivos: o dia suspenso não soma nem
        // zera, então ontem suspenso + hoje completo continua a sequência.
        if (point.percent == 100) {
            fullDays += day
            run += 1
            if (run > longestStreak) longestStreak = run
        } else {
            run = 0
        }
    }

    // A sequência atual conta de trás para frente a partir de hoje: dia suspenso é
    // pulado (pausa), dia completo conta e qualquer outro dia encerra a corrida. Hoje
    // ainda incompleto não quebra — a contagem começa em ontem.
    var cursor = if (today in fullDays) today else today.minusDays(1)
    while (cursor in fullDays || cursor in suspendedDays) {
        if (cursor in fullDays) currentStreak += 1
        cursor = cursor.minusDays(1)
    }

    return RoutineStats(
        todayPercent = todayPercent,
        todayConfirmable = todayConfirmable,
        todayDone = todayDone,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
    )
}
