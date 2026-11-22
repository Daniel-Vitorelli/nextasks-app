package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regras de conexão: quantas confirmações contam e quais dias entram.
 *
 * Aqui mora o bug histórico de "confirmação anterior à criação não conta" —
 * se `createdOn` deixar de ser respeitado, a conexão nasce já satisfeita.
 */
class ConnectionRulesTest {

    private fun link(
        requiredCount: Int = 1,
        dayFilter: String = Connection.ALL_DAYS,
        createdOn: String = "2026-03-10",
        taskId: String? = "t1",
    ) = Connection(
        routineId = "r1",
        blockId = "b1",
        taskId = taskId,
        requiredCount = requiredCount,
        dayFilter = dayFilter,
        createdOn = createdOn,
    )

    private fun completion(period: String, value: String = "true", blockId: String = "b1") =
        Completion(blockId = blockId, periodStart = period, value = value)

    // ---------- countsFor ----------

    @Test
    fun `confirmacao antes da criacao da conexao nao conta`() {
        val connection = link(createdOn = "2026-03-10")

        assertFalse(connection.countsFor(completion("2026-03-09")))
        assertTrue(connection.countsFor(completion("2026-03-10")))
    }

    @Test
    fun `checklist desmarcado nao conta`() {
        val connection = link()

        assertFalse(connection.countsFor(completion("2026-03-10", "false")))
    }

    @Test
    fun `nota zero nao conta e nota positiva conta`() {
        val connection = link()

        assertFalse(connection.countsFor(completion("2026-03-10", "0")))
        assertTrue(connection.countsFor(completion("2026-03-10", "1")))
    }

    @Test
    fun `confirmacao de outro bloco nao conta`() {
        val connection = link()

        assertFalse(connection.countsFor(completion("2026-03-10", blockId = "outro")))
    }

    @Test
    fun `data ilegivel no periodo nao conta`() {
        val connection = link()

        assertFalse(connection.countsFor(completion("ontem")))
    }

    @Test
    fun `filtro de dia da semana so conta no dia certo`() {
        // 2026-03-16 é segunda (weekday 1).
        val connection = link(dayFilter = "weekday:1")

        assertTrue(connection.countsFor(completion("2026-03-16")))
        assertFalse(connection.countsFor(completion("2026-03-17")))
    }

    @Test
    fun `domingo e weekday zero`() {
        // 2026-03-15 é domingo.
        val connection = link(dayFilter = "weekday:0")

        assertTrue(connection.countsFor(completion("2026-03-15")))
        assertFalse(connection.countsFor(completion("2026-03-16")))
    }

    @Test
    fun `filtro de data unica so conta naquele dia`() {
        val connection = link(dayFilter = "date:2026-03-20")

        assertTrue(connection.countsFor(completion("2026-03-20")))
        assertFalse(connection.countsFor(completion("2026-03-21")))
    }

    // ---------- DayFilter ----------

    @Test
    fun `dayFilter faz ida e volta`() {
        assertEquals(DayFilter.All, DayFilter.parse("all"))
        assertEquals(DayFilter.Weekday(3), DayFilter.parse("weekday:3"))
        assertEquals(
            DayFilter.Date(LocalDate.of(2026, 3, 20)),
            DayFilter.parse("date:2026-03-20"),
        )

        assertEquals("all", DayFilter.All.raw())
        assertEquals("weekday:3", DayFilter.Weekday(3).raw())
        assertEquals("date:2026-03-20", DayFilter.Date(LocalDate.of(2026, 3, 20)).raw())
    }

    @Test
    fun `filtro invalido cai para todos os dias em vez de quebrar`() {
        assertEquals(DayFilter.All, DayFilter.parse("weekday:9"))
        assertEquals(DayFilter.All, DayFilter.parse("weekday:abc"))
        assertEquals(DayFilter.All, DayFilter.parse("date:32/13/2026"))
        assertEquals(DayFilter.All, DayFilter.parse("qualquer coisa"))
    }

    // ---------- dayFilterFor ----------

    @Test
    fun `rotina semanal nao aceita filtro de dia`() {
        // A confirmação semanal é gravada no início da semana; filtrar por dia da
        // semana compararia sempre com o domingo e a conexão nunca contaria.
        assertEquals("all", dayFilterFor(weekly = true, weekday = 1, dateIso = null))
        assertEquals("all", dayFilterFor(weekly = true, weekday = null, dateIso = "2026-11-20"))
    }

    @Test
    fun `rotina diaria respeita o filtro escolhido`() {
        assertEquals("weekday:3", dayFilterFor(weekly = false, weekday = 3, dateIso = null))
        assertEquals("date:2026-11-20", dayFilterFor(weekly = false, weekday = null, dateIso = "2026-11-20"))
        assertEquals("all", dayFilterFor(weekly = false, weekday = null, dateIso = null))
    }

    @Test
    fun `a data escolhida ganha do dia da semana`() {
        assertEquals(
            "date:2026-11-20",
            dayFilterFor(weekly = false, weekday = 1, dateIso = "2026-11-20"),
        )
    }

    @Test
    fun `periodo semanal e o inicio da semana, nao o dia do bloco`() {
        // 2026-11-15 é domingo: início da semana de uma rotina semanal.
        val connection = link(dayFilter = "weekday:1")

        assertFalse(connection.countsFor(completion("2026-11-15")))
    }

    // ---------- Connection ----------

    @Test
    fun `entityId devolve a entidade preenchida`() {
        assertEquals("t1", link(taskId = "t1").entityId)
        assertEquals(
            "s1",
            Connection(routineId = "r1", blockId = "b1", subtaskId = "s1").entityId,
        )
        assertEquals(
            "h1",
            Connection(routineId = "r1", blockId = "b1", habitId = "h1").entityId,
        )
    }
}
