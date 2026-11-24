package com.daniel.nextasks.ui.home

import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.Completion
import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.EventKind
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.RoutineUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Números da Home. O que importa aqui é o imprevisto: ele **não** pode ser tratado
 * como dia perdido — a sequência pausa e retoma.
 */
class RoutineStatsTest {

    private val today = LocalDate.of(2026, 11, 24)

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun routine(created: LocalDate) = Routine(
        name = "rotina",
        frequency = RoutineFrequency.Daily,
        isActive = true,
        createdAt = millis(created),
        blocks = listOf(
            RoutineBlock(
                id = "b1",
                title = "academia",
                startTime = "08:00",
                endTime = "09:00",
                confirmation = EventConfirmation.Checklist,
            ),
        ),
    )

    private fun done(day: LocalDate) = Completion("b1", day.toString(), "true")

    private fun unforeseen(day: LocalDate) = AgendaEvent(
        title = "imprevisto",
        startDate = day.toString(),
        endDate = day.toString(),
        kind = EventKind.Unforeseen,
    )

    @Test
    fun `imprevisto no meio pausa a sequencia em vez de zerar`() {
        val stats = routineStats(
            routine = routine(today.minusDays(10)),
            completions = listOf(done(today.minusDays(2)), done(today)),
            today = today,
            agendaEvents = listOf(unforeseen(today.minusDays(1))),
        )

        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(1, stats.todayDone)
    }

    @Test
    fun `sem imprevisto o dia vazio quebra a sequencia`() {
        val stats = routineStats(
            routine = routine(today.minusDays(10)),
            completions = listOf(done(today.minusDays(2)), done(today)),
            today = today,
        )

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun `imprevisto registrado depois nao apaga o dia cumprido`() {
        val stats = routineStats(
            routine = routine(today.minusDays(10)),
            completions = listOf(done(today)),
            today = today,
            agendaEvents = listOf(unforeseen(today)),
        )

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun `imprevisto em dia sem rotina nao vira pausa`() {
        // A rotina só acontece na segunda (semanal): a terça de imprevisto não é um
        // dia de rotina, então não há pausa a fazer — e também não há o que quebrar.
        val semanal = Routine(
            name = "rotina",
            frequency = RoutineFrequency.Weekly,
            isActive = true,
            createdAt = millis(today.minusDays(30)),
            blocks = listOf(
                RoutineBlock(
                    id = "b1",
                    title = "academia",
                    startTime = "08:00",
                    endTime = "09:00",
                    weekday = 1,
                    weekdays = listOf(1),
                    confirmation = EventConfirmation.Checklist,
                ),
            ),
        )
        val segunda = today.minusDays(1) // 2026-11-23, segunda
        val stats = routineStats(
            routine = semanal,
            completions = listOf(done(segunda)),
            today = today,
            agendaEvents = listOf(unforeseen(today)),
        )

        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun `rotina semanal conta semanas e nao zera no dia sem bloco`() {
        // A rotina só acontece na segunda. Na terça não há bloco nenhum — e era aí
        // que a Home zerava a sequência, mesmo com as três últimas semanas fechadas.
        val semanal = Routine(
            name = "rotina",
            frequency = RoutineFrequency.Weekly,
            isActive = true,
            createdAt = millis(today.minusDays(60)),
            blocks = listOf(
                RoutineBlock(
                    id = "b1",
                    title = "academia",
                    startTime = "08:00",
                    endTime = "09:00",
                    weekday = 1,
                    weekdays = listOf(1),
                    confirmation = EventConfirmation.Checklist,
                ),
            ),
        )
        val stats = routineStats(
            routine = semanal,
            completions = listOf(
                done(today.minusDays(1)), // segunda desta semana
                done(today.minusDays(8)),
                done(today.minusDays(15)),
            ),
            today = today,
        )

        assertEquals(RoutineUnit.Week, stats.streakUnit)
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        // Hoje é terça: não há bloco para confirmar, mas a semana segue de pé.
        assertEquals(0, stats.todayConfirmable)
    }

    @Test
    fun `semana sem confirmacao quebra a sequencia semanal`() {
        val semanal = Routine(
            name = "rotina",
            frequency = RoutineFrequency.Weekly,
            isActive = true,
            createdAt = millis(today.minusDays(60)),
            blocks = listOf(
                RoutineBlock(
                    id = "b1",
                    title = "academia",
                    startTime = "08:00",
                    endTime = "09:00",
                    weekday = 1,
                    weekdays = listOf(1),
                    confirmation = EventConfirmation.Checklist,
                ),
            ),
        )
        val stats = routineStats(
            routine = semanal,
            completions = listOf(
                done(today.minusDays(1)), // esta semana
                done(today.minusDays(15)), // duas semanas atrás
            ),
            today = today,
        )

        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.longestStreak)
    }

    @Test
    fun `rotina diaria mantem a unidade em dias`() {
        val stats = routineStats(
            routine = routine(today.minusDays(10)),
            completions = listOf(done(today.minusDays(1)), done(today)),
            today = today,
        )

        assertEquals(RoutineUnit.Day, stats.streakUnit)
        assertEquals(2, stats.currentStreak)
    }
}
