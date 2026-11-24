package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Suspensão da rotina: um imprevisto suspende o que cai no período dele.
 *
 * O evento planejado só suspende se o usuário pedir; o imprevisto suspende sempre, e
 * é essa diferença que o calendário, a Home e a gamificação passam a ler.
 */
class SuspensionsTest {

    /** 2026-11-24 é terça-feira. */
    private val dia = LocalDate.of(2026, 11, 24)

    private fun event(
        kind: EventKind = EventKind.Planned,
        suspension: SuspensionMode = SuspensionMode.None,
        startTime: String? = null,
        endTime: String? = null,
        days: Int = 1,
    ) = AgendaEvent(
        title = "evento",
        startDate = dia.toString(),
        endDate = dia.plusDays((days - 1).toLong()).toString(),
        startTime = startTime,
        endTime = endTime,
        suspension = suspension,
        kind = kind,
    )

    private fun block(start: String = "08:00", end: String = "09:00") = RoutineBlock(
        title = "academia",
        startTime = start,
        endTime = end,
        confirmation = EventConfirmation.Checklist,
    )

    @Test
    fun `evento planejado sem suspensao nao suspende nada`() {
        assertTrue(listOf(event()).suspensions().isEmpty)
    }

    @Test
    fun `imprevisto suspende mesmo sem o usuario pedir`() {
        val imprevisto = event(kind = EventKind.Unforeseen)

        // Guardado como "só no horário" para o app antigo suspender igual.
        assertEquals(SuspensionMode.WithinHours, imprevisto.effectiveSuspension)
        assertTrue(block().isSuspendedOn(dia, listOf(imprevisto).suspensions()))
    }

    @Test
    fun `imprevisto de dia inteiro suspende o dia`() {
        val suspensions = listOf(event(kind = EventKind.Unforeseen)).suspensions()

        // Qualquer horário do dia cai dentro de uma suspensão de dia inteiro.
        assertTrue(block("20:00", "21:00").isSuspendedOn(dia, suspensions))
    }

    @Test
    fun `imprevisto com horario suspende so o bloco da janela`() {
        val suspensions = listOf(
            event(kind = EventKind.Unforeseen, startTime = "08:00", endTime = "09:30"),
        ).suspensions()

        assertTrue(block("08:00", "09:00").isSuspendedOn(dia, suspensions))
        assertFalse(block("20:00", "21:00").isSuspendedOn(dia, suspensions))
    }

    @Test
    fun `imprevisto multi-dia suspende os dias do meio`() {
        val suspensions = listOf(
            event(kind = EventKind.Unforeseen, startTime = "22:00", endTime = "02:00", days = 3),
        ).suspensions()

        // Noite do primeiro dia e um bloco no meio do dia seguinte.
        assertTrue(block("23:00", "23:30").isSuspendedOn(dia, suspensions))
        assertTrue(block("12:00", "13:00").isSuspendedOn(dia.plusDays(1), suspensions))
        // Depois do término não suspende.
        assertFalse(block("12:00", "13:00").isSuspendedOn(dia.plusDays(2), suspensions))
    }

    @Test
    fun `dia todo suspenso deixa o dia sem ocorrencia exigivel`() {
        val suspensions = listOf(event(kind = EventKind.Unforeseen)).suspensions()
        val routine = Routine(
            name = "rotina",
            frequency = RoutineFrequency.Daily,
            isActive = true,
            createdAt = 0L,
            blocks = listOf(block()),
        )

        assertTrue(routine.dayIsFullySuspended(dia, suspensions))
        assertTrue(routine.occurrencesIn(dia, suspensions).isEmpty())
        assertFalse(routine.occurrencesIn(dia).isEmpty())
    }

    @Test
    fun `bloco que atravessa a meia-noite tem janela no dia certo`() {
        val times = block("22:00", "01:00").timesOn(dia)

        assertEquals(dia.atTime(22, 0), times?.first)
        assertEquals(dia.plusDays(1).atTime(1, 0), times?.second)
    }

    @Test
    fun `bloco com horario ilegivel e ignorado pela suspensao`() {
        val quebrado = RoutineBlock(title = "x", startTime = "ontem", endTime = "09:00")
        val janela = listOf(
            event(kind = EventKind.Unforeseen, startTime = "08:00", endTime = "09:00"),
        ).suspensions()

        // Sem horário legível não dá para afirmar overlap — e o motor de ocorrências
        // também descarta esse bloco, então ele não precisa ser suspenso.
        assertFalse(quebrado.isSuspendedOn(dia, janela))
    }

    @Test
    fun `sem suspensions o bloco nao e suspenso`() {
        assertFalse(block().isSuspendedOn(dia, Suspensions.None))
    }
}
