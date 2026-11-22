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
 * Faixas de rank, derivadas do nível. `minLevel` é cumulativo: o rank é o último
 * alcançado. Curva alta de propósito — subir de rank é evento raro.
 */
enum class Rank(@StringRes val label: Int, val minLevel: Int) {
    Beginner(R.string.rank_beginner, 1),
    Apprentice(R.string.rank_apprentice, 6),
    Bronze(R.string.rank_bronze, 12),
    Silver(R.string.rank_silver, 20),
    Gold(R.string.rank_gold, 30),
    Platinum(R.string.rank_platinum, 42),
    Diamond(R.string.rank_diamond, 56),
    Legendary(R.string.rank_legendary, 72),
}

fun rankFromLevel(level: Int): Rank {
    val l = level.coerceAtLeast(1)
    return Rank.entries.lastOrNull { l >= it.minLevel } ?: Rank.Beginner
}

/** Próximo rank, ou nulo quando já está no último. */
fun nextRank(level: Int): Rank? {
    val l = level.coerceAtLeast(1)
    return Rank.entries.firstOrNull { it.minLevel > l }
}
