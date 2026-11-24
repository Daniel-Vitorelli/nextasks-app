package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Regras dos hábitos: meta, saturação, recaída e sequência. */
class HabitStatsTest {

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun habit(
        kind: HabitKind = HabitKind.Good,
        frequency: HabitFrequency = HabitFrequency.Daily,
        target: Int? = 1,
        allowExceed: Boolean = false,
        daysOfWeek: List<Int> = (0..6).toList(),
        created: LocalDate = LocalDate.of(2026, 1, 1),
    ) = Habit(
        name = "hábito",
        kind = kind,
        frequency = frequency,
        targetCount = target,
        allowExceed = allowExceed,
        daysOfWeek = daysOfWeek,
        createdAt = millis(created),
    )

    // ---------- applyCompletion ----------

    @Test
    fun `bom com meta satura na meta`() {
        val h = habit(target = 3)

        assertEquals(3, applyCompletion(h, current = 2))
        assertEquals("já na meta, continua na meta", 3, applyCompletion(h, current = 3))
    }

    @Test
    fun `allowExceed deixa passar da meta`() {
        val h = habit(target = 3, allowExceed = true)

        assertEquals(4, applyCompletion(h, current = 3))
    }

    @Test
    fun `sem meta acumula livremente`() {
        val h = habit(target = null)

        assertEquals(7, applyCompletion(h, current = 6))
    }

    @Test
    fun `ruim acumula mesmo com meta`() {
        val h = habit(kind = HabitKind.Bad, target = 2)

        assertEquals(3, applyCompletion(h, current = 2))
    }

    // ---------- habitProgress ----------

    @Test
    fun `bom diario mede o dia pela meta`() {
        val h = habit(target = 2)
        val day = LocalDate.of(2026, 3, 10)

        val progress = habitProgress(h, mapOf(day to 1), day, day)

        assertEquals(50, progress.single().value)
        assertEquals(1, progress.single().count)
    }

    @Test
    fun `bom diario satura em 100 mesmo passando da meta`() {
        val h = habit(target = 2)
        val day = LocalDate.of(2026, 3, 10)

        val progress = habitProgress(h, mapOf(day to 9), day, day)

        assertEquals(100, progress.single().value)
    }

    @Test
    fun `ruim sem marcacao e dia limpo e com marcacao zera`() {
        val h = habit(kind = HabitKind.Bad)
        val limpo = LocalDate.of(2026, 3, 10)
        val recaida = LocalDate.of(2026, 3, 11)

        val progress = habitProgress(h, mapOf(recaida to 1), limpo, recaida)

        assertEquals(100, progress.first { it.date == limpo }.value)
        assertEquals(0, progress.first { it.date == recaida }.value)
    }

    @Test
    fun `dia fora da agenda fica nulo e nao conta`() {
        // Só segunda-feira (weekday 1).
        val h = habit(daysOfWeek = listOf(1))
        val segunda = LocalDate.of(2026, 11, 16)
        val terca = LocalDate.of(2026, 11, 17)

        val progress = habitProgress(h, emptyMap(), segunda, terca)

        assertEquals(0, progress.first { it.date == segunda }.value)
        assertNull(progress.first { it.date == terca }.value)
    }

    @Test
    fun `nao inventa historico antes da criacao do habito`() {
        val h = habit(created = LocalDate.of(2026, 3, 10))

        val progress = habitProgress(
            h,
            emptyMap(),
            from = LocalDate.of(2026, 3, 1),
            to = LocalDate.of(2026, 3, 12),
        )

        assertEquals(LocalDate.of(2026, 3, 10), progress.first().date)
        assertEquals(3, progress.size)
    }

    @Test
    fun `bom semanal acumula a semana ate o dia`() {
        val h = habit(frequency = HabitFrequency.Weekly, target = 3)
        val segunda = LocalDate.of(2026, 11, 16)
        val terca = LocalDate.of(2026, 11, 17)
        val counts = mapOf(segunda to 2, terca to 1)

        val progress = habitProgress(h, counts, segunda, terca)

        assertEquals(67, progress.first { it.date == segunda }.value)
        assertEquals("2 + 1 bate a meta de 3", 100, progress.first { it.date == terca }.value)
    }

    // ---------- habitStreak ----------

    @Test
    fun `sequencia conta dias seguidos ate hoje`() {
        val h = habit()
        val today = LocalDate.of(2026, 11, 22)
        val counts = (0..4).associate { today.minusDays(it.toLong()) to 1 }

        val progress = habitProgress(h, counts, today.minusDays(4), today)
        val streak = habitStreak(progress, today)

        assertEquals(5, streak.current)
        assertEquals(5, streak.longest)
    }

    @Test
    fun `dois dias sem cumprir quebram a sequencia atual`() {
        val h = habit()
        val today = LocalDate.of(2026, 11, 22)
        // Cumpriu só até anteontem.
        val counts = mapOf(
            today.minusDays(4) to 1,
            today.minusDays(3) to 1,
            today.minusDays(2) to 1,
        )

        val progress = habitProgress(h, counts, today.minusDays(4), today)
        val streak = habitStreak(progress, today)

        assertEquals(0, streak.current)
        assertEquals("o recorde continua registrado", 3, streak.longest)
    }

    @Test
    fun `hoje em aberto nao quebra a sequencia`() {
        val h = habit()
        val today = LocalDate.of(2026, 11, 22)
        val counts = mapOf(today.minusDays(2) to 1, today.minusDays(1) to 1)

        val progress = habitProgress(h, counts, today.minusDays(2), today)
        val streak = habitStreak(progress, today)

        assertEquals(2, streak.current)
    }

    @Test
    fun `dia fora da agenda nao quebra a sequencia`() {
        // Só segunda (1). 16/11 e 23/11 são segundas.
        val h = habit(daysOfWeek = listOf(1))
        val today = LocalDate.of(2026, 11, 23)
        val counts = mapOf(LocalDate.of(2026, 11, 16) to 1, today to 1)

        val progress = habitProgress(h, counts, LocalDate.of(2026, 11, 16), today)
        val streak = habitStreak(progress, today)

        assertEquals("o domingo entre as duas segundas é nulo, não quebra", 2, streak.current)
    }

    @Test
    fun `habito sem meta fecha o dia com qualquer marcacao`() {
        val h = habit(target = null)
        val day = LocalDate.of(2026, 3, 10)

        val progress = habitProgress(h, mapOf(day to 1), day, day)

        assertEquals(100, progress.single().value)
        assertTrue(h.hasGoal.not())
    }

    @Test
    fun `dias desde a criacao conta o dia de hoje`() {
        val h = habit(created = LocalDate.of(2026, 11, 20))

        assertEquals(3, h.daysSinceCreation(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `sequencia vazia quando nada foi cumprido`() {
        val h = habit()
        val today = LocalDate.of(2026, 11, 22)

        val progress = habitProgress(h, emptyMap(), today.minusDays(2), today)
        val streak = habitStreak(progress, today)

        assertEquals(0, streak.current)
        assertEquals(0, streak.longest)
        assertFalse(progress.any { it.value == 100 })
    }

    // ---------- habitPeriodCount ----------

    @Test
    fun `meta semanal soma a semana inteira`() {
        val h = habit(frequency = HabitFrequency.Weekly, target = 3)
        // 2026-03-08 é domingo: a semana vai de domingo a sábado.
        val sunday = LocalDate.of(2026, 3, 8)
        val counts = mapOf(
            sunday.plusDays(1) to 1,
            sunday.plusDays(2) to 1,
            sunday.plusDays(3) to 1,
        )

        assertEquals(3, habitPeriodCount(h, counts, sunday.plusDays(3)))
    }

    @Test
    fun `meta diaria nao soma os outros dias da semana`() {
        val h = habit(frequency = HabitFrequency.Daily, target = 3)
        val day = LocalDate.of(2026, 3, 9)
        val counts = mapOf(day to 1, day.plusDays(1) to 2)

        assertEquals(1, habitPeriodCount(h, counts, day))
    }

    @Test
    fun `semana semanal comeca no domingo`() {
        val h = habit(frequency = HabitFrequency.Weekly, target = 2)
        val sunday = LocalDate.of(2026, 3, 8)
        val saturday = sunday.plusDays(6)
        val counts = mapOf(
            // Marcação do sábado anterior não entra nesta semana.
            sunday.minusDays(1) to 5,
            saturday to 2,
        )

        assertEquals(2, habitPeriodCount(h, counts, saturday))
    }

    // ---------- agenda do dia ----------

    @Test
    fun `bom diario com dias escolhidos nao acontece fora deles`() {
        val terca = LocalDate.of(2026, 11, 24)
        val h = habit(daysOfWeek = listOf(1, 3, 5))

        assertFalse(h.happensOn(terca))
        assertTrue(h.happensOn(terca.plusDays(1))) // quarta
    }

    @Test
    fun `lista de dias vazia vale como todo dia`() {
        // Dado antigo ou escrita incompleta não pode sumir com o hábito da Home.
        val h = habit(daysOfWeek = emptyList())

        assertTrue(h.happensOn(LocalDate.of(2026, 11, 24)))
    }

    @Test
    fun `habito semanal ou ruim acontece todo dia`() {
        val terca = LocalDate.of(2026, 11, 24)

        assertTrue(habit(frequency = HabitFrequency.Weekly, daysOfWeek = listOf(1)).happensOn(terca))
        assertTrue(habit(kind = HabitKind.Bad, daysOfWeek = listOf(1)).happensOn(terca))
    }

    @Test
    fun `habitsForDay filtra pela agenda e mantem a ordem`() {
        val terca = LocalDate.of(2026, 11, 24)
        val foraDoDia = habit(daysOfWeek = listOf(1, 3)) // sem terça
        val todoDia = habit(daysOfWeek = emptyList())
        val ruim = habit(kind = HabitKind.Bad)

        val hoje = habitsForDay(listOf(foraDoDia, todoDia, ruim), terca).map { it.id }

        assertEquals(listOf(todoDia.id, ruim.id), hoje)
    }
}
