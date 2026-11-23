package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Regras da gamificação: curva de nível, ranks, sequências e avaliação de
 * conquistas. Tudo puro, sem Android.
 */
class GamificationRulesTest {

    // ---------- níveis ----------

    @Test
    fun `curva de nivel bate com a tabela do web`() {
        assertEquals(0, cumulativeXpForLevel(1))
        assertEquals(75, cumulativeXpForLevel(2))
        assertEquals(225, cumulativeXpForLevel(3))
        // O comentário do web diz 600 para o nível 5, mas a fórmula dá 750 —
        // 75·4·5/2. A fórmula é a que roda lá, então é ela que vale aqui.
        assertEquals(750, cumulativeXpForLevel(5))
        assertEquals(3375, cumulativeXpForLevel(10))
        assertEquals(14250, cumulativeXpForLevel(20))
    }

    @Test
    fun `nivel a partir do xp respeita os limites exatos`() {
        assertEquals(1, levelFromXp(0))
        assertEquals(1, levelFromXp(74))
        assertEquals(2, levelFromXp(75))
        assertEquals(2, levelFromXp(224))
        assertEquals(3, levelFromXp(225))
        assertEquals(10, levelFromXp(3375))
        assertEquals(9, levelFromXp(3374))
    }

    @Test
    fun `xp negativo nao quebra`() {
        assertEquals(1, levelFromXp(-500))
    }

    @Test
    fun `progresso dentro do nivel`() {
        // Nível 2 vai de 75 a 225: 150 de span.
        val progress = levelProgress(150)

        assertEquals(2, progress.level)
        assertEquals(75, progress.xpIntoLevel)
        assertEquals(75, progress.xpToNextLevel)
        assertEquals(50, progress.percent)
    }

    @Test
    fun `progresso no inicio do nivel e zero`() {
        val progress = levelProgress(75)

        assertEquals(2, progress.level)
        assertEquals(0, progress.percent)
        assertEquals(150, progress.xpForLevel)
    }

    // ---------- ranks ----------

    @Test
    fun `rank sobe nos limites certos`() {
        assertEquals(Rank.Beginner, rankFromLevel(1))
        assertEquals(Rank.Beginner, rankFromLevel(5))
        assertEquals(Rank.Apprentice, rankFromLevel(6))
        assertEquals(Rank.Bronze, rankFromLevel(12))
        assertEquals(Rank.Legendary, rankFromLevel(72))
        assertEquals(Rank.Legendary, rankFromLevel(200))
    }

    @Test
    fun `proximo rank para no ultimo`() {
        assertEquals(Rank.Apprentice, nextRank(5))
        assertNull(nextRank(72))
    }

    // ---------- sequências ----------

    @Test
    fun `sequencia conta corridas consecutivas`() {
        val summary = streakSummary(listOf(1, 2, 3, 7, 8), reference = 8)

        assertEquals(3, summary.best)
        assertEquals(2, summary.current)
    }

    @Test
    fun `hoje vazio nao quebra a sequencia de ontem`() {
        val summary = streakSummary(listOf(5, 6, 7), reference = 8)

        assertEquals(3, summary.current)
    }

    @Test
    fun `buraco de dois dias zera a sequencia`() {
        val summary = streakSummary(listOf(5, 6), reference = 9)

        assertEquals(0, summary.current)
        assertEquals(2, summary.best)
    }

    @Test
    fun `unidades repetidas contam uma vez`() {
        val summary = streakSummary(listOf(3, 3, 4), reference = 4)

        assertEquals(2, summary.current)
    }

    // ---------- conquistas ----------

    private fun stats(
        blocks: Int = 0,
        tasks: Int = 0,
        xp: Int = 0,
        level: Int = 1,
    ) = GamificationStats(
        totalXp = xp,
        level = level,
        blocksConfirmed = blocks,
        perfectBlocks = 0,
        days100 = 0,
        bestRoutineStreak = 0,
        currentRoutineStreak = 0,
        tasksDone = tasks,
        subtasksDone = 0,
        activeConnections = 0,
        goodHabitsActive = 0,
        badHabitsActive = 0,
        habitTargetDays = 0,
        relapsesLogged = 0,
        bestCleanStreak = 0,
        currentCleanStreak = 0,
    )

    @Test
    fun `conquista de um bloco ja cai com um bloco`() {
        val achievement = achievementById("first-block")!!

        assertTrue(achievement.isSatisfiedBy(stats(blocks = 1)))
        assertFalse(achievement.isSatisfiedBy(stats(blocks = 0)))
    }

    @Test
    fun `todas as condicoes precisam valer`() {
        val resilience = achievementById("resilience")!!

        assertFalse(resilience.isSatisfiedBy(stats()))
        assertFalse(resilience.isSatisfiedBy(stats().copy(relapsesLogged = 1)))
        assertTrue(
            resilience.isSatisfiedBy(stats().copy(relapsesLogged = 1, bestCleanStreak = 7)),
        )
    }

    @Test
    fun `progresso usa a condicao mais atrasada`() {
        val block10 = achievementById("block-10")!!

        assertEquals(0.5f, block10.progress(stats(blocks = 5)), 0.001f)
        assertEquals(1f, block10.progress(stats(blocks = 40)), 0.001f)
    }

    @Test
    fun `detalhe do progresso mostra a condicao mais atrasada`() {
        val resilience = achievementById("resilience")!!
        // Recaída 1 de 1 (feita), sequência limpa 3 de 7 (atrasada): o detalhe é 3 de 7.
        val s = stats().copy(relapsesLogged = 1, bestCleanStreak = 3)

        assertEquals(3 to 7, resilience.progressDetail(s))
    }

    @Test
    fun `catalogo nao tem id repetido`() {
        val ids = ACHIEVEMENTS.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }
}
