package com.daniel.nextasks.ui.habits

import com.daniel.nextasks.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integridade do catálogo de ícones de hábito.
 *
 * A chave é a identidade do item na grade do seletor: chave repetida derrubava o
 * app com "Key was already used" quando a grade compunha as duas entradas. Estes
 * testes seguram a regressão sem precisar de emulador.
 */
class HabitIconsTest {

    @Test
    fun `chaves do seletor sao unicas`() {
        val duplicadas = HabitIcons.map { it.first }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }

        assertTrue("chaves repetidas: ${duplicadas.keys}", duplicadas.isEmpty())
        assertEquals(HabitIcons.size, HabitIcons.map { it.first }.distinct().size)
    }

    @Test
    fun `seletor tem os icones e nenhum recurso zerado`() {
        // 295 entradas hoje; o piso só existe para pegar uma remoção acidental.
        assertTrue("achei só ${HabitIcons.size} ícones", HabitIcons.size >= 290)
        HabitIcons.forEach { (key, resource) ->
            assertTrue("chave vazia no seletor", key.isNotBlank())
            assertTrue("recurso zerado em '$key'", resource != 0)
        }
    }

    @Test
    fun `chaves originais continuam resolvendo`() {
        assertEquals(R.drawable.ic_habit_target, habitIconRes("target"))
        assertEquals(R.drawable.ic_habit_water, habitIconRes("water"))
        // Apelido da v3.13, que não aparece mais no seletor.
        assertEquals(R.drawable.ic_habit_yoga, habitIconRes("yoga"))
    }

    @Test
    fun `chave desconhecida cai no icone padrao`() {
        assertEquals(R.drawable.ic_habit_target, habitIconRes("nao_existe"))
    }
}
