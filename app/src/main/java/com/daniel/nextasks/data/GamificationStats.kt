package com.daniel.nextasks.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Resumo de sequência sobre unidades consecutivas (dias ou semanas). */
data class StreakSummary(
    /** Maior corrida em qualquer ponto. */
    val best: Int,
    /**
     * Corrida que termina na referência ou imediatamente antes dela — o dia de hoje
     * ainda incompleto não quebra a sequência.
     */
    val current: Int,
)

/**
 * Resume `best`/`current` a partir de unidades ordenadas e únicas.
 *
 * Mesma conta do web: hoje vazio não zera a sequência, ontem completo mantém.
 */
fun streakSummary(units: List<Int>, reference: Int): StreakSummary {
    val sorted = units.distinct().sorted()

    var best = 0
    var run = 0
    var previous = Int.MIN_VALUE
    for (unit in sorted) {
        run = if (unit == previous + 1) run + 1 else 1
        if (run > best) best = run
        previous = unit
    }

    val present = sorted.toHashSet()
    val anchor = when {
        reference in present -> reference
        (reference - 1) in present -> reference - 1
        else -> null
    }

    var current = 0
    if (anchor != null) {
        var unit = anchor
        while (unit in present) {
            current += 1
            unit -= 1
        }
    }

    return StreakSummary(best = best, current = current)
}

/**
 * Retrato das estatísticas que alimentam conquistas e a tela de gamificação.
 *
 * Tudo é derivado dos dados que já existem (conclusões, tarefas, hábitos, conexões
 * e o próprio histórico de XP) — nada aqui é fonte de verdade separada, então
 * apagar uma tarefa não deixa estatística fantasma.
 */
data class GamificationStats(
    val totalXp: Int,
    val level: Int,
    val blocksConfirmed: Int,
    /** Confirmações de bloco com a nota máxima configurada nele. */
    val perfectBlocks: Int,
    val days100: Int,
    val bestRoutineStreak: Int,
    val currentRoutineStreak: Int,
    val tasksDone: Int,
    val subtasksDone: Int,
    val activeConnections: Int,
    val goodHabitsActive: Int,
    val badHabitsActive: Int,
    /** Dias (ou semanas, em hábito semanal) em que um hábito bom bateu a meta. */
    val habitTargetDays: Int,
    val relapsesLogged: Int,
    val bestCleanStreak: Int,
    val currentCleanStreak: Int,
)

fun gamificationStats(
    routines: List<Routine>,
    completions: List<Completion>,
    tasks: List<Task>,
    subtasks: List<Subtask>,
    habits: List<Habit>,
    habitCompletions: List<HabitCompletion>,
    connections: List<Connection>,
    xpEvents: List<XpEvent>,
    today: LocalDate,
): GamificationStats {
    val totalXp = xpEvents.sumOf { it.amount }
    val level = levelFromXp(totalXp)

    // Blocos: só conta o que o bloco permite confirmar.
    val blocksById = routines.flatMap { it.blocks }.associateBy { it.id }
    val valid = completions.filter { completion ->
        val block = blocksById[completion.blockId] ?: return@filter false
        completionCounts(block.confirmation, completion.value)
    }

    val perfectBlocks = valid.count { completion ->
        val block = blocksById[completion.blockId] ?: return@count false
        block.confirmation == EventConfirmation.Score &&
            completion.value.toIntOrNull() == block.scoreMax
    }

    // Dia/semana 100% da rotina ativa: período completo quando todos os blocos
    // confirmáveis têm confirmação válida.
    val active = routines.firstOrNull { it.isActive }
    val activeCreated = active?.createdOnDate()
    var days100 = 0
    var bestRoutineStreak = 0
    var currentRoutineStreak = 0
    if (active != null && activeCreated != null && active.blocks.isNotEmpty()) {
        val unitDays = if (active.frequency == RoutineFrequency.Weekly) 7L else 1L
        val createdUnit = activeCreated.toEpochDay() / unitDays
        val confirmable = active.blocks.count { it.confirmation != EventConfirmation.None }

        val fullUnits = valid
            .filter { completion -> blocksById[completion.blockId] in active.blocks }
            .groupBy { it.periodStart }
            .mapNotNull { (periodStart, list) ->
                if (list.map { it.blockId }.distinct().size < confirmable) return@mapNotNull null
                val day = parseIsoDate(periodStart) ?: return@mapNotNull null
                val unit = (day.toEpochDay() / unitDays).toInt()
                if (unit < createdUnit) null else unit
            }

        days100 = fullUnits.size
        val summary = streakSummary(fullUnits, (today.toEpochDay() / unitDays).toInt())
        bestRoutineStreak = summary.best
        currentRoutineStreak = summary.current
    }

    // Hábitos
    var habitTargetDays = 0
    var relapsesLogged = 0
    val cleanDaysByHabit = mutableMapOf<String, MutableSet<Long>>()

    habits.filter { it.kind == HabitKind.Bad }.forEach { habit ->
        val days = mutableSetOf<Long>()
        var day = epochMillisToDate(habit.createdAt)
        while (!day.isAfter(today)) {
            days += day.toEpochDay()
            day = day.plusDays(1)
        }
        cleanDaysByHabit[habit.id] = days
    }

    habitCompletions.forEach { completion ->
        val habit = habits.firstOrNull { it.id == completion.habitId } ?: return@forEach
        if (habit.kind == HabitKind.Bad) {
            relapsesLogged += 1
            parseIsoDate(completion.date)?.let { cleanDaysByHabit[habit.id]?.remove(it.toEpochDay()) }
        }
    }

    // Meta batida: diário conta o dia, semanal conta a semana inteira — a mesma
    // regra do `habitPeriodCount` que a Home usa.
    habits.filter { it.kind == HabitKind.Good }.forEach { habit ->
        val target = habit.targetCount ?: 1
        val mine = habitCompletions.filter { it.habitId == habit.id }
        habitTargetDays += when (habit.frequency) {
            HabitFrequency.Daily -> mine.count { it.count >= target }

            HabitFrequency.Weekly -> mine
                .mapNotNull { completion ->
                    parseIsoDate(completion.date)?.let { day ->
                        day.minusDays((day.dayOfWeek.value % 7).toLong()) to completion.count
                    }
                }
                .groupBy({ it.first }, { it.second })
                .count { (_, counts) -> counts.sum() >= target }
        }
    }

    var bestCleanStreak = 0
    var currentCleanStreak = 0
    cleanDaysByHabit.values.forEach { days ->
        val summary = streakSummary(days.map { it.toInt() }, today.toEpochDay().toInt())
        if (summary.best > bestCleanStreak) bestCleanStreak = summary.best
        if (summary.current > currentCleanStreak) currentCleanStreak = summary.current
    }

    return GamificationStats(
        totalXp = totalXp,
        level = level,
        blocksConfirmed = valid.size,
        perfectBlocks = perfectBlocks,
        days100 = days100,
        bestRoutineStreak = bestRoutineStreak,
        currentRoutineStreak = currentRoutineStreak,
        tasksDone = tasks.count { it.done },
        subtasksDone = subtasks.count { it.done },
        activeConnections = connections.size,
        goodHabitsActive = habits.count { it.kind == HabitKind.Good },
        badHabitsActive = habits.count { it.kind == HabitKind.Bad },
        habitTargetDays = habitTargetDays,
        relapsesLogged = relapsesLogged,
        bestCleanStreak = bestCleanStreak,
        currentCleanStreak = currentCleanStreak,
    )
}
