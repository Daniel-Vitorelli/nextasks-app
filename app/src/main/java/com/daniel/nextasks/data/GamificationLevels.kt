package com.daniel.nextasks.data

import androidx.annotation.StringRes
import com.daniel.nextasks.R

/**
 * Curva de níveis: XP acumulado para alcançar o nível L é `75 · (L−1) · L / 2`.
 *
 * Quadrática suave, igual à do web — nível 2 em 75, 5 em 600, 10 em 3.375,
 * 20 em 14.250. Constante num lugar só para o balanceamento não se espalhar.
 */
private const val CURVE_COEFFICIENT = 75

/** Teto de segurança: ninguém chega perto, mas evita laço infinito com XP corrompido. */
const val MAX_LEVEL = 999

/** XP acumulado total necessário para **estar** no nível informado. */
fun cumulativeXpForLevel(level: Int): Int {
    val l = level.coerceIn(1, MAX_LEVEL)
    return CURVE_COEFFICIENT * (l - 1) * l / 2
}

/** Nível atual a partir do XP total. */
fun levelFromXp(totalXp: Int): Int {
    val xp = totalXp.coerceAtLeast(0)
    var level = 1
    while (level < MAX_LEVEL && cumulativeXpForLevel(level + 1) <= xp) level += 1
    return level
}

/** Progresso dentro do nível atual. */
data class LevelProgress(
    val level: Int,
    /** 0 a 100 dentro do nível. */
    val percent: Int,
    /** XP que falta para o próximo nível. */
    val xpToNextLevel: Int,
    /** XP já conquistado dentro do nível atual. */
    val xpIntoLevel: Int,
    /** Quanto o nível atual pede no total. */
    val xpForLevel: Int,
)

fun levelProgress(totalXp: Int): LevelProgress {
    val xp = totalXp.coerceAtLeast(0)
    val level = levelFromXp(xp)
    val floor = cumulativeXpForLevel(level)
    val ceiling = cumulativeXpForLevel(level + 1)
    val span = (ceiling - floor).coerceAtLeast(1)
    return LevelProgress(
        level = level,
        percent = (((xp - floor).toFloat() / span) * 100).toInt().coerceIn(0, 100),
        xpToNextLevel = (ceiling - xp).coerceAtLeast(0),
        xpIntoLevel = xp - floor,
        xpForLevel = span,
    )
}

/**
 * Faixas de rank, derivadas do nível **e** do número de conquistas.
 *
 * `minLevel` e `minAchievements` são cumulativos: o rank é o último cujo par foi
 * alcançado. A curva é alta de propósito — subir de rank é evento raro — e o
 * segundo requisito impede que farmar XP sozinho pule a escada.
 */
enum class Rank(
    @StringRes val label: Int,
    val minLevel: Int,
    val minAchievements: Int,
) {
    Beginner(R.string.rank_beginner, 1, 0),
    Apprentice(R.string.rank_apprentice, 8, 5),
    Bronze(R.string.rank_bronze, 16, 12),
    Silver(R.string.rank_silver, 26, 24),
    Gold(R.string.rank_gold, 38, 36),
    Platinum(R.string.rank_platinum, 52, 48),
    Diamond(R.string.rank_diamond, 66, 58),
    Legendary(R.string.rank_legendary, 84, 68),
}

/** Rank atual para o par (nível, conquistas desbloqueadas). */
fun rankFor(level: Int, achievements: Int): Rank {
    val l = level.coerceAtLeast(1)
    val a = achievements.coerceAtLeast(0)
    return Rank.entries.lastOrNull { l >= it.minLevel && a >= it.minAchievements }
        ?: Rank.Beginner
}

/** Próximo rank, ou nulo quando já está no último. */
fun nextRank(level: Int, achievements: Int): Rank? {
    val l = level.coerceAtLeast(1)
    val a = achievements.coerceAtLeast(0)
    return Rank.entries.firstOrNull { l < it.minLevel || a < it.minAchievements }
}
