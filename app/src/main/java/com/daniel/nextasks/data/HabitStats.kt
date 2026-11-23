package com.daniel.nextasks.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.roundToInt

/** Um dia do histórico do hábito. `value` nulo = dia fora da agenda. */
data class HabitDay(
    val date: LocalDate,
    val value: Int?,
    val count: Int,
)

data class HabitStreak(
    val current: Int = 0,
    val longest: Int = 0,
    val lastFullDay: LocalDate? = null,
)

/**
 * Progresso diário do hábito, igual a `buildHabitProgress` do app web.
 *
 * - **Bom diário**: dias agendados valem `count/meta`; os outros ficam nulos.
 * - **Bom semanal**: a meta é da semana, e cada dia mostra o preenchimento da
 *   semana corrente.
 * - **Ruim**: lógica invertida — dia sem marcação é dia limpo (100), qualquer
 *   recaída zera (0). Na versão semanal, uma recaída zera a semana inteira.
 */
fun habitProgress(
    habit: Habit,
    countsByDay: Map<LocalDate, Int>,
    from: LocalDate,
    to: LocalDate,
): List<HabitDay> {
    val created = java.time.Instant.ofEpochMilli(habit.createdAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val start = maxOf(from, created)
    if (start.isAfter(to)) return emptyList()

    val target = habit.targetCount?.coerceAtLeast(1)
    val isBad = habit.kind == HabitKind.Bad
    val schedule = if (habit.usesSchedule) habit.daysOfWeek.toSet() else null

    val result = mutableListOf<HabitDay>()
    var day = start
    while (!day.isAfter(to)) {
        val weekday = day.dayOfWeek.value % 7
        if (schedule != null && weekday !in schedule) {
            result += HabitDay(day, null, 0)
            day = day.plusDays(1)
            continue
        }

        val count = if (habit.frequency == HabitFrequency.Weekly) {
            val weekStart = day.minusDays(weekday.toLong())
            var sum = 0
            var cursor = weekStart
            while (!cursor.isAfter(day)) {
                sum += countsByDay[cursor] ?: 0
                cursor = cursor.plusDays(1)
            }
            sum
        } else {
            countsByDay[day] ?: 0
        }

        val value = when {
            isBad -> if (count > 0) 0 else 100
            // Sem meta, qualquer marcação fecha o dia.
            target == null -> if (count > 0) 100 else 0
            else -> ((min(count, target).toFloat() / target) * 100).roundToInt()
        }

        result += HabitDay(day, value, count)
        day = day.plusDays(1)
    }
    return result
}

/**
 * Quantas marcações o hábito tem no período da data.
 *
 * Diário = o próprio dia; semanal = a semana inteira (domingo a sábado). É a
 * resposta certa para "bati a meta?": a Home comparava a meta **semanal** com a
 * contagem de um único dia, então "3× por semana" só fechava se as três
 * marcações caíssem no mesmo dia.
 */
fun habitPeriodCount(
    habit: Habit,
    countsByDay: Map<LocalDate, Int>,
    date: LocalDate,
): Int = when (habit.frequency) {
    HabitFrequency.Daily -> countsByDay[date] ?: 0
    HabitFrequency.Weekly -> {
        val weekStart = date.minusDays((date.dayOfWeek.value % 7).toLong())
        (0..6).sumOf { countsByDay[weekStart.plusDays(it.toLong())] ?: 0 }
    }
}

/**
 * Sequência de dias cumpridos. Dias fora da agenda não contam nem quebram.
 *
 * A conta é feita **sobre os dias agendados**, não sobre o calendário: um hábito
 * que só vale às segundas precisa poder ter sequência de várias semanas — antes
 * ele nunca passava de 1, porque o domingo no meio "quebrava" a contagem.
 *
 * A sequência atual só vale se o último dia agendado foi cumprido (ou é hoje e
 * ainda está em aberto): hoje em aberto não quebra, mas um dia agendado perdido
 * quebra.
 */
fun habitStreak(progress: List<HabitDay>, today: LocalDate): HabitStreak {
    // `value` nulo = dia fora da agenda: sai da conta por completo.
    val scheduled = progress.filter { it.value != null }
    if (scheduled.isEmpty()) return HabitStreak()

    var longest = 0
    var run = 0
    for (day in scheduled) {
        run = if (day.value == 100) run + 1 else 0
        if (run > longest) longest = run
    }

    val last = scheduled.last()
    val closed = if (last.date == today && last.value != 100) {
        scheduled.dropLast(1)
    } else {
        scheduled
    }
    val current = closed.takeLastWhile { it.value == 100 }.size
    val lastFull = scheduled.lastOrNull { it.value == 100 }?.date

    return HabitStreak(current = current, longest = longest, lastFullDay = lastFull)
}

/**
 * Aplica a marcação do dia.
 *
 * Ruim, sem meta e "pode passar da meta" acumulam livremente; bom com meta
 * satura nela.
 */
fun applyCompletion(habit: Habit, current: Int, increment: Int = 1): Int {
    val next = current + increment
    val target = habit.targetCount
    return when {
        habit.kind == HabitKind.Bad -> next
        target == null -> next
        habit.allowExceed -> next
        else -> min(next, target)
    }
}

/** Quantos dias o hábito existe — usado para limitar a janela de histórico. */
fun Habit.daysSinceCreation(today: LocalDate): Int {
    val created = java.time.Instant.ofEpochMilli(createdAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return (ChronoUnit.DAYS.between(created, today).toInt() + 1).coerceAtLeast(1)
}
