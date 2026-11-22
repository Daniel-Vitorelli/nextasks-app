package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Estado de uma conexão: por que ela não dispara e quanto falta no ritmo atual.
 */
class ConnectionStatusTest {

    private val today = LocalDate.of(2026, 11, 22)

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun routine(
        active: Boolean = true,
        duration: RoutineDuration = RoutineDuration.Indefinite,
        endDate: String? = null,
        created: LocalDate = LocalDate.of(2026, 11, 1),
    ) = Routine(
        name = "rotina",
        isActive = active,
        duration = duration,
        endDate = endDate,
        createdAt = millis(created),
    )

    private fun block(start: String = "07:00", end: String = "07:30") =
        RoutineBlock(title = "bloco", startTime = start, endTime = end)

    // ---------- blockBlocker ----------

    @Test
    fun `rotina inativa nunca dispara`() {
        assertEquals(
            BlockBlocker.RoutineInactive,
            blockBlocker(routine(active = false), block(), today),
        )
    }

    @Test
    fun `rotina que terminou nunca dispara`() {
        val r = routine(duration = RoutineDuration.Until, endDate = "2026-11-20")

        assertEquals(BlockBlocker.RoutineEnded, blockBlocker(r, block(), today))
    }

    @Test
    fun `rotina com data final hoje ainda vale`() {
        val r = routine(duration = RoutineDuration.Until, endDate = "2026-11-22")

        assertNull(blockBlocker(r, block(), today))
    }

    @Test
    fun `rotina que ainda vai comecar nao dispara`() {
        val r = routine(created = LocalDate.of(2026, 12, 1))

        assertEquals(BlockBlocker.RoutineNotStarted, blockBlocker(r, block(), today))
    }

    @Test
    fun `horario do bloco invalido nao dispara`() {
        assertEquals(
            BlockBlocker.InvalidTime,
            blockBlocker(routine(), block(start = "25:00"), today),
        )
    }

    @Test
    fun `rotina saudavel nao tem bloqueio`() {
        assertNull(blockBlocker(routine(), block(), today))
    }

    // ---------- paceEstimate ----------

    private fun link(required: Int = 3, createdOn: String = "2026-11-12") = Connection(
        routineId = "r1",
        blockId = "b1",
        taskId = "t1",
        requiredCount = required,
        createdOn = createdOn,
    )

    private fun done(date: LocalDate) =
        Completion(blockId = "b1", periodStart = date.toString(), value = "true")

    @Test
    fun `estimativa segue o ritmo observado`() {
        // 11 dias de janela, 10 confirmações, meta 12 e 10 já feitas:
        // faltam 2 no ritmo de ~0,91/dia => 3 dias.
        val completions = (1..10).map { done(today.minusDays(it.toLong())) }

        assertEquals(3, paceEstimate(link(required = 12), completions, today))
    }

    @Test
    fun `ritmo semanal estima em dias`() {
        // 14 dias de janela, 2 confirmações, falta 1 => 7 dias.
        val connection = link(required = 3, createdOn = today.minusDays(13).toString())
        val completions = listOf(done(today.minusDays(7)), done(today))

        assertEquals(7, paceEstimate(connection, completions, today))
    }

    @Test
    fun `sem confirmacao nao da para estimar`() {
        assertNull(paceEstimate(link(), emptyList(), today))
    }

    @Test
    fun `meta batida nao tem estimativa`() {
        val completions = (0..3).map { done(today.minusDays(it.toLong())) }

        assertNull(paceEstimate(link(required = 3), completions, today))
    }

    @Test
    fun `conexao nova demais nao estima`() {
        assertNull(
            paceEstimate(
                link(required = 3, createdOn = today.toString()),
                listOf(done(today)),
                today,
            ),
        )
    }

    @Test
    fun `confirmacao anterior a criacao fica fora do ritmo`() {
        // Só a confirmação de ontem conta; a de antes da criação não.
        val connection = link(required = 3, createdOn = today.minusDays(5).toString())
        val completions = listOf(done(today.minusDays(20)), done(today.minusDays(1)))

        // 6 dias de janela com 1 confirmação, faltam 2 => 12 dias.
        assertEquals(12, paceEstimate(connection, completions, today))
    }
}
