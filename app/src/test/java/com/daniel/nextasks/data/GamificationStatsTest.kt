package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Retrato de estatísticas: o que conta como bloco feito, dia 100%, meta de hábito
 * e dias limpos. É daqui que saem as conquistas, então erro aqui vira conquista
 * dada de graça.
 */
class GamificationStatsTest {

    private val today = LocalDate.of(2026, 11, 22)

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun routine(
        blocks: List<RoutineBlock>,
        created: LocalDate = LocalDate.of(2026, 11, 1),
    ) = Routine(name = "rotina", isActive = true, createdAt = millis(created), blocks = blocks)

    private fun block(
        id: String,
        mode: EventConfirmation,
        scoreMax: Int = 5,
    ) = RoutineBlock(id = id, title = id, confirmation = mode, scoreMax = scoreMax)

    private fun stats(
        routines: List<Routine> = emptyList(),
        completions: List<Completion> = emptyList(),
        tasks: List<Task> = emptyList(),
        subtasks: List<Subtask> = emptyList(),
        habits: List<Habit> = emptyList(),
        habitCompletions: List<HabitCompletion> = emptyList(),
        connections: List<Connection> = emptyList(),
        xpEvents: List<XpEvent> = emptyList(),
        agendaEvents: List<AgendaEvent> = emptyList(),
    ) = gamificationStats(
        routines = routines,
        completions = completions,
        tasks = tasks,
        subtasks = subtasks,
        habits = habits,
        habitCompletions = habitCompletions,
        connections = connections,
        xpEvents = xpEvents,
        today = today,
        agendaEvents = agendaEvents,
    )

    @Test
    fun `conta blocos confirmados e nota cheia`() {
        val r = routine(
            listOf(
                block("b1", EventConfirmation.Checklist),
                block("b2", EventConfirmation.Score, scoreMax = 5),
            ),
        )
        val completions = listOf(
            Completion("b1", "2026-11-22", "true"),
            Completion("b2", "2026-11-22", "5"),
            Completion("b2", "2026-11-21", "3"),
        )

        val s = stats(routines = listOf(r), completions = completions)

        assertEquals(3, s.blocksConfirmed)
        assertEquals(1, s.perfectBlocks)
        assertEquals(1, s.days100)
    }

    @Test
    fun `dia cem por cento exige todos os blocos confirmaveis`() {
        val r = routine(
            listOf(
                block("b1", EventConfirmation.Checklist),
                block("b2", EventConfirmation.Checklist),
            ),
        )
        val completions = listOf(Completion("b1", "2026-11-22", "true"))

        val s = stats(routines = listOf(r), completions = completions)

        assertEquals(0, s.days100)
        assertEquals(0, s.bestRoutineStreak)
    }

    @Test
    fun `bloco sem confirmacao nao entra na conta`() {
        val r = routine(listOf(block("b1", EventConfirmation.None)))
        val completions = listOf(Completion("b1", "2026-11-22", "true"))

        assertEquals(0, stats(routines = listOf(r), completions = completions).blocksConfirmed)
    }

    @Test
    fun `imprevisto em um dia do bloco semanal nao impede a semana`() {
        // Semana de 15/11: bloco na segunda (16) e na quarta (18). Quarta teve
        // imprevisto, então confirmar a segunda fecha a semana.
        val r = routine(
            listOf(
                block("b1", EventConfirmation.Checklist).copy(weekday = 1, weekdays = listOf(1, 3)),
            ),
        ).copy(frequency = RoutineFrequency.Weekly)
        val imprevisto = AgendaEvent(
            title = "imprevisto",
            startDate = "2026-11-18",
            endDate = "2026-11-18",
            kind = EventKind.Unforeseen,
        )

        val s = stats(
            routines = listOf(r),
            completions = listOf(Completion("b1", "2026-11-16", "true")),
            agendaEvents = listOf(imprevisto),
        )

        assertEquals(1, s.days100)
    }

    @Test
    fun `sem o imprevisto a semana multi-dia nao fecha pela metade`() {
        val r = routine(
            listOf(
                block("b1", EventConfirmation.Checklist).copy(weekday = 1, weekdays = listOf(1, 3)),
            ),
        ).copy(frequency = RoutineFrequency.Weekly)

        val s = stats(
            routines = listOf(r),
            completions = listOf(Completion("b1", "2026-11-16", "true")),
        )

        assertEquals(0, s.days100)
    }

    @Test
    fun `nota zero nao conta como bloco feito`() {
        val r = routine(listOf(block("b1", EventConfirmation.Score)))
        val completions = listOf(Completion("b1", "2026-11-22", "0"))

        assertEquals(0, stats(routines = listOf(r), completions = completions).blocksConfirmed)
    }

    @Test
    fun `tarefas e subtarefas concluidas`() {
        val tasks = listOf(Task(title = "a", done = true), Task(title = "b", done = false))
        val subtasks = listOf(
            Subtask(taskId = "a", title = "s1", done = true),
            Subtask(taskId = "a", title = "s2", done = true),
        )

        val s = stats(tasks = tasks, subtasks = subtasks)

        assertEquals(1, s.tasksDone)
        assertEquals(2, s.subtasksDone)
    }

    @Test
    fun `meta de habito bom conta por dia`() {
        val habit = Habit(
            id = "h1",
            name = "Meditar",
            kind = HabitKind.Good,
            targetCount = 3,
            createdAt = millis(today.minusDays(5)),
        )
        val completions = listOf(
            HabitCompletion("h1", "2026-11-22", 3),
            HabitCompletion("h1", "2026-11-21", 1),
        )

        assertEquals(1, stats(habits = listOf(habit), habitCompletions = completions).habitTargetDays)
    }

    @Test
    fun `meta de habito semanal conta a semana`() {
        val habit = Habit(
            id = "h1",
            name = "Correr",
            kind = HabitKind.Good,
            frequency = HabitFrequency.Weekly,
            targetCount = 3,
            createdAt = millis(today.minusDays(20)),
        )
        // 2026-11-15 é domingo; 16, 17 e 18 caem na mesma semana.
        val completions = listOf(
            HabitCompletion("h1", "2026-11-16", 1),
            HabitCompletion("h1", "2026-11-17", 1),
            HabitCompletion("h1", "2026-11-18", 1),
        )

        assertEquals(
            1,
            stats(habits = listOf(habit), habitCompletions = completions).habitTargetDays,
        )
    }

    @Test
    fun `dias limpos de habito ruim descontam recaidas`() {
        val habit = Habit(
            id = "h1",
            name = "Fumar",
            kind = HabitKind.Bad,
            createdAt = millis(today.minusDays(9)),
        )
        val completions = listOf(HabitCompletion("h1", "2026-11-18", 1))

        val s = stats(habits = listOf(habit), habitCompletions = completions)

        // 13 a 22 menos o 18: corrida 13-17 (5) e 19-22 (4).
        assertEquals(5, s.bestCleanStreak)
        assertEquals(4, s.currentCleanStreak)
        assertEquals(1, s.relapsesLogged)
    }

    @Test
    fun `xp total soma o historico e vira nivel`() {
        val events = listOf(
            XpEvent(kind = "task.done", refKey = "a", amount = 20),
            XpEvent(kind = "habit.slip", refKey = "b", amount = -15),
            XpEvent(kind = "block.confirm", refKey = "c", amount = 70),
        )

        val s = stats(xpEvents = events)

        assertEquals(75, s.totalXp)
        assertEquals(2, s.level)
    }
}
