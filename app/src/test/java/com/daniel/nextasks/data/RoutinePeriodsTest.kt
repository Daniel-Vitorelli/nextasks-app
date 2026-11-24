package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "Dia 100%" com a confirmação por dia.
 *
 * A chave da conclusão é o dia da ocorrência, mas a rotina é avaliada pela unidade
 * dela: o dia na diária, a semana (domingo) na semanal. Numa rotina semanal de
 * Seg/Qua/Sex, as três ocorrências precisam ser confirmadas para a semana fechar.
 */
class RoutinePeriodsTest {

    /** 2026-11-24 é terça-feira; a semana dela começa em 22/11 (domingo). */
    private val terca = LocalDate.of(2026, 11, 24)
    private val domingoDaSemana = LocalDate.of(2026, 11, 22)

    private fun block(
        id: String = "b1",
        weekdays: List<Int> = emptyList(),
        weekday: Int = 1,
        confirmation: EventConfirmation = EventConfirmation.Checklist,
    ) = RoutineBlock(
        id = id,
        title = id,
        weekday = weekday,
        weekdays = weekdays,
        confirmation = confirmation,
    )

    private fun routine(
        blocks: List<RoutineBlock>,
        frequency: RoutineFrequency = RoutineFrequency.Daily,
    ) = Routine(name = "rotina", frequency = frequency, isActive = true, createdAt = 0L, blocks = blocks)

    private fun done(blockId: String = "b1", day: LocalDate = terca, value: String = "true") =
        Completion(blockId = blockId, periodStart = day.toString(), value = value)

    @Test
    fun `rotina diaria fecha o dia quando todos os blocos confirmam`() {
        val routine = routine(listOf(block("b1"), block("b2")))

        val units = routine.completePeriodStarts(listOf(done("b1"), done("b2")))

        assertEquals(listOf(terca), units)
    }

    @Test
    fun `rotina diaria com bloco pendente nao fecha o dia`() {
        val routine = routine(listOf(block("b1"), block("b2")))

        assertTrue(routine.completePeriodStarts(listOf(done("b1"))).isEmpty())
    }

    @Test
    fun `bloco sem confirmacao nao impede fechar o dia`() {
        val routine = routine(listOf(block("b1"), block("b2", confirmation = EventConfirmation.None)))

        assertEquals(listOf(terca), routine.completePeriodStarts(listOf(done("b1"))))
    }

    @Test
    fun `rotina semanal de um dia fecha a semana com uma confirmacao`() {
        // Segunda-feira 23/11, bloco só na segunda: a chave é o dia, a unidade é a semana.
        val routine = routine(listOf(block(weekday = 1)), RoutineFrequency.Weekly)

        val units = routine.completePeriodStarts(listOf(done(day = LocalDate.of(2026, 11, 23))))

        assertEquals(listOf(domingoDaSemana), units)
    }

    @Test
    fun `rotina semanal multi-dia so fecha com todos os dias`() {
        val routine = routine(listOf(block(weekdays = listOf(1, 3))), RoutineFrequency.Weekly)
        val segunda = LocalDate.of(2026, 11, 23)
        val quarta = LocalDate.of(2026, 11, 25)

        assertTrue(routine.completePeriodStarts(listOf(done(day = segunda))).isEmpty())
        assertEquals(listOf(domingoDaSemana), routine.completePeriodStarts(listOf(done(day = segunda), done(day = quarta))))
    }

    @Test
    fun `ocorrencias da semana trazem um item por dia do bloco`() {
        val routine = routine(listOf(block(weekdays = listOf(1, 3, 5))), RoutineFrequency.Weekly)

        val occurrences = routine.occurrencesIn(domingoDaSemana)

        assertEquals(
            listOf(
                "b1" to LocalDate.of(2026, 11, 23),
                "b1" to LocalDate.of(2026, 11, 25),
                "b1" to LocalDate.of(2026, 11, 27),
            ),
            occurrences,
        )
    }

    @Test
    fun `dia sem ocorrencia nunca conta como completo`() {
        val routine = routine(listOf(block(weekday = 1)), RoutineFrequency.Weekly)
        // Conclusão numa terça que não é dia do bloco não fecha nada.
        assertTrue(routine.completePeriodStarts(listOf(done(day = terca))).isEmpty())
    }

    @Test
    fun `conclusao de outro bloco nao fecha a unidade`() {
        val routine = routine(listOf(block("b1")))

        assertTrue(routine.completePeriodStarts(listOf(done("outro"))).isEmpty())
    }
}
