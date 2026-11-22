package com.daniel.nextasks.ui.calendar

import com.daniel.nextasks.data.RoutineBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Horário do bloco no dia — incluindo a virada de meia-noite e o dia inteiro. */
class ResolveTimesTest {

    private val day = LocalDate.of(2026, 11, 22)

    private fun block(
        start: String = "08:00",
        end: String = "09:00",
        allDay: Boolean = false,
    ) = RoutineBlock(title = "bloco", startTime = start, endTime = end, isAllDay = allDay)

    @Test
    fun `bloco normal comeca e termina no mesmo dia`() {
        val (start, end) = resolveTimes(day, block())!!

        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 0), end)
    }

    @Test
    fun `bloco que termina antes de comecar atravessa a meia-noite`() {
        val (start, end) = resolveTimes(day, block(start = "23:00", end = "01:00"))!!

        assertEquals(LocalDateTime.of(2026, 11, 22, 23, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 1, 0), end)
    }

    @Test
    fun `dia inteiro ocupa do inicio ao fim do dia`() {
        val (start, end) = resolveTimes(day, block(allDay = true))!!

        assertEquals(LocalDateTime.of(2026, 11, 22, 0, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), end)
    }

    @Test
    fun `horario invalido devolve nulo em vez de quebrar`() {
        assertNull(resolveTimes(day, block(start = "25:00")))
        assertNull(resolveTimes(day, block(end = "abc")))
        assertNull(resolveTimes(day, block(start = "")))
    }

    @Test
    fun `inicio igual ao fim vale como bloco de 24 horas`() {
        val (start, end) = resolveTimes(day, block(start = "08:00", end = "08:00"))!!

        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 0), end)
    }
}
