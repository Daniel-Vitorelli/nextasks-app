package com.daniel.nextasks.ui.calendar

import com.daniel.nextasks.data.EventConfirmation
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * A chave da conclusão é o **dia da ocorrência**, inclusive em rotina semanal.
 *
 * Era aqui que um bloco de Seg/Qua/Sex acabava com uma confirmação só para os três
 * dias: a chave era o domingo da semana.
 */
class RoutineOccurrencesTest {

    private val dias = listOf(23, 24, 25, 26, 27).map { LocalDate.of(2026, 11, it) }

    private fun block(
        id: String = "b1",
        weekdays: List<Int> = listOf(1, 3, 5),
        confirmation: EventConfirmation = EventConfirmation.Checklist,
    ) = RoutineBlock(
        id = id,
        title = "academia",
        startTime = "08:00",
        endTime = "09:00",
        weekday = weekdays.firstOrNull() ?: 1,
        weekdays = weekdays,
        confirmation = confirmation,
    )

    private fun routine(
        id: String = "r1",
        blocks: List<RoutineBlock>,
        frequency: RoutineFrequency = RoutineFrequency.Weekly,
    ) = Routine(
        id = id,
        name = "rotina",
        frequency = frequency,
        isActive = true,
        createdAt = 0L,
        blocks = blocks,
    )

    @Test
    fun `cada dia do bloco semanal tem a propria chave`() {
        val events = routineOccurrences(listOf(routine(blocks = listOf(block()))), dias)

        // Seg, Qua e Sex aparecem, cada um com a chave do seu dia.
        assertEquals(
            listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 27)),
            events.map { it.completionPeriodStart },
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 27)),
            events.map { it.start.toLocalDate() },
        )
    }

    @Test
    fun `as tres ocorrencias da semana nao compartilham a chave`() {
        val events = routineOccurrences(listOf(routine(blocks = listOf(block()))), dias)
        val keys = events.map { it.completionPeriodStart }

        // O bug da v3.48: as três apontavam para o domingo da semana.
        assertEquals(3, keys.size)
        assertEquals(3, keys.distinct().size)
    }

    @Test
    fun `bloco avulso de rotina semanal usa o proprio dia`() {
        val extra = ExtraBlock(
            id = "e1",
            date = "2026-11-25",
            routineId = "r1",
            title = "avulso",
            confirmation = EventConfirmation.Checklist,
        )

        val events = routineOccurrences(
            routines = listOf(routine(blocks = listOf(block(weekdays = listOf(3))))),
            days = dias,
            extras = listOf(extra),
        )

        val avulso = events.single { it.isExtra }
        assertEquals(LocalDate.of(2026, 11, 25), avulso.completionPeriodStart)
    }

    @Test
    fun `rotina diaria continua com a chave do dia`() {
        val events = routineOccurrences(
            routines = listOf(routine(blocks = listOf(block(weekdays = emptyList())), frequency = RoutineFrequency.Daily)),
            days = dias,
        )

        assertEquals(dias, events.map { it.completionPeriodStart })
    }
}
