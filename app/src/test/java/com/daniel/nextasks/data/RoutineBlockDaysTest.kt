package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Bloco de rotina semanal em vários dias da semana.
 *
 * Antes o bloco cabia em um dia só (`weekday`). Agora `weekdays` guarda a lista, e
 * `weekday` fica apenas como compatibilidade com dados e versões antigas.
 */
class RoutineBlockDaysTest {

    /** 2026-11-24 é uma terça-feira. */
    private val terca = LocalDate.of(2026, 11, 24)

    private fun block(days: List<Int> = emptyList(), weekday: Int = 1) =
        RoutineBlock(title = "academia", weekday = weekday, weekdays = days)

    @Test
    fun `bloco sem weekdays cai no weekday antigo`() {
        // Dado gravado antes da v3.49: só tinha `weekday`.
        val b = block(days = emptyList(), weekday = 1)

        assertEquals(listOf(1), b.scheduledWeekdays)
        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 23))) // segunda
        assertFalse(RoutineBlockDays.happensOn(b, terca))
    }

    @Test
    fun `bloco em varios dias acontece em todos eles`() {
        val b = block(days = listOf(1, 3, 5))

        // Terça não está na lista: o bloco é Seg/Qua/Sex.
        assertFalse(RoutineBlockDays.happensOn(b, terca))
        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 23))) // seg
        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 25))) // qua
        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 27))) // sex
        assertFalse(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 28))) // sáb
    }

    @Test
    fun `domingo e o dia zero`() {
        val b = block(days = listOf(0))

        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 22))) // domingo
        assertEquals(0, RoutineBlockDays.indexOf(LocalDate.of(2026, 11, 22)))
        assertEquals(6, RoutineBlockDays.indexOf(LocalDate.of(2026, 11, 28))) // sábado
    }

    @Test
    fun `selecao invalida e limpa e vazia vira todos os dias`() {
        assertEquals(listOf(1, 2, 3), RoutineBlockDays.sanitize(listOf(3, 1, 2, 2, 9, -1)))
        assertEquals(RoutineBlockDays.All, RoutineBlockDays.sanitize(emptyList()))
    }

    @Test
    fun `weekdays so com lixo cai no weekday em vez de todos os dias`() {
        // Backup remendado com [9]: "todos os dias" esconderia o problema no calendário.
        val b = RoutineBlock(title = "academia", weekday = 4, weekdays = listOf(9, -2))

        assertEquals(listOf(4), b.scheduledWeekdays)
        assertTrue(RoutineBlockDays.happensOn(b, LocalDate.of(2026, 11, 26))) // quinta
    }

    @Test
    fun `weekday fora da faixa nao quebra o bloco`() {
        val b = RoutineBlock(title = "academia", weekday = 42, weekdays = emptyList())

        assertEquals(listOf(6), b.scheduledWeekdays)
    }

    @Test
    fun `weekdays com lixo no meio usa so os dias validos`() {
        val b = RoutineBlock(title = "academia", weekday = 1, weekdays = listOf(9, 3, 3, 5))

        assertEquals(listOf(3, 5), b.scheduledWeekdays)
    }

    @Test
    fun `desligar o ultimo dia nao deixa o bloco sem dia`() {
        val um = listOf(3)
        assertEquals(listOf(3), RoutineBlockDays.toggle(um, 3))

        val dois = listOf(1, 3)
        assertEquals(listOf(1), RoutineBlockDays.toggle(dois, 3))
        assertEquals(listOf(1, 3, 5), RoutineBlockDays.toggle(dois, 5))
    }

    @Test
    fun `semana comeca no domingo`() {
        assertEquals(LocalDate.of(2026, 11, 22), terca.weekStartSunday())
        assertEquals(LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 22).weekStartSunday())
    }

    @Test
    fun `sincronizar grava weekdays e espelha weekday no primeiro dia`() {
        val b = RoutineBlock(title = "estudo", weekday = 4, weekdays = listOf(3, 1, 5))
        val synced = b.syncedDays()

        assertEquals(listOf(1, 3, 5), synced.weekdays)
        assertEquals(1, synced.weekday)
    }

    @Test
    fun `normalizar rotina antiga preenche weekdays de todos os blocos`() {
        val antiga = Routine(
            name = "rotina",
            frequency = RoutineFrequency.Weekly,
            blocks = listOf(block(days = emptyList(), weekday = 2)),
        )

        val normal = antiga.normalized()

        assertEquals(listOf(2), normal.blocks.first().weekdays)
        assertEquals(2, normal.blocks.first().weekday)
    }
}
