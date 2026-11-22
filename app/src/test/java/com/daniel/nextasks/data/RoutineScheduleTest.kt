package com.daniel.nextasks.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Quando a rotina existe.
 *
 * Antes desta regra o calendário mostrava os blocos da rotina ativa em qualquer
 * mês do passado — inclusive antes de ela ser criada.
 */
class RoutineScheduleTest {

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun routine(
        active: Boolean = true,
        duration: RoutineDuration = RoutineDuration.Indefinite,
        endDate: String? = null,
        created: LocalDate = LocalDate.of(2026, 3, 10),
    ) = Routine(
        name = "rotina",
        isActive = active,
        duration = duration,
        endDate = endDate,
        createdAt = millis(created),
    )

    @Test
    fun `rotina inativa nao cobre nenhum dia`() {
        assertFalse(routine(active = false).coversDay(LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `nao cobre dias anteriores a criacao`() {
        val r = routine(created = LocalDate.of(2026, 3, 10))

        assertFalse(r.coversDay(LocalDate.of(2026, 3, 9)))
        assertTrue("o dia da criação já vale", r.coversDay(LocalDate.of(2026, 3, 10)))
        assertTrue(r.coversDay(LocalDate.of(2026, 3, 11)))
    }

    @Test
    fun `duracao ate para depois da data final`() {
        val r = routine(
            duration = RoutineDuration.Until,
            endDate = "2026-03-20",
        )

        assertTrue(r.coversDay(LocalDate.of(2026, 3, 20)))
        assertFalse(r.coversDay(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun `duracao ate sem data final legivel nao esconde a rotina`() {
        val r = routine(
            duration = RoutineDuration.Until,
            endDate = "não é data",
        )

        assertTrue(r.coversDay(LocalDate.of(2026, 4, 1)))
    }

    @Test
    fun `data final e ignorada quando a duracao e indefinida`() {
        val r = routine(duration = RoutineDuration.Indefinite, endDate = "2026-03-11")

        assertTrue(r.coversDay(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `createdOnDate converte o epoch para data local`() {
        val r = routine(created = LocalDate.of(2026, 3, 10))

        assertTrue(r.createdOnDate() == LocalDate.of(2026, 3, 10))
    }
}
