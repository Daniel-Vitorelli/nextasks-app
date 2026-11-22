package com.daniel.nextasks.data

import androidx.annotation.StringRes
import com.daniel.nextasks.R

/** Nível de raridade da conquista; define a recompensa em XP. */
enum class AchievementTier(@StringRes val label: Int, val xp: Int) {
    Bronze(R.string.achievement_tier_bronze, 50),
    Silver(R.string.achievement_tier_silver, 100),
    Gold(R.string.achievement_tier_gold, 250),
    Platinum(R.string.achievement_tier_platinum, 500),
}

/** Estatística do retrato usada como condição de conquista. */
enum class GamificationStat {
    BlocksConfirmed,
    PerfectBlocks,
    Days100,
    BestRoutineStreak,
    TasksDone,
    SubtasksDone,
    ActiveConnections,
    GoodHabitsActive,
    BadHabitsActive,
    HabitTargetDays,
    RelapsesLogged,
    BestCleanStreak,
    TotalXp,
    Level,
}

data class AchievementCondition(val stat: GamificationStat, val target: Int)

/**
 * Uma conquista do catálogo.
 *
 * Todas as condições precisam ser satisfeitas. O catálogo é fixo (não é dado do
 * usuário): o que varia é o desbloqueio, guardado em `achievements.json`.
 */
data class Achievement(
    val id: String,
    val tier: AchievementTier,
    @StringRes val name: Int,
    @StringRes val description: Int,
    val conditions: List<AchievementCondition>,
)

private fun a(
    id: String,
    tier: AchievementTier,
    @StringRes name: Int,
    @StringRes description: Int,
    vararg conditions: Pair<GamificationStat, Int>,
): Achievement = Achievement(
    id = id,
    tier = tier,
    name = name,
    description = description,
    conditions = conditions.map { AchievementCondition(it.first, it.second) },
)

/**
 * Catálogo v1, portado do web (mesmos ids, para facilitar comparação).
 *
 * Divergência consciente: o web fala em nota **10** (`perfect10`), mas no Android
 * a nota é `1..scoreMax` por bloco. Aqui "nota cheia" é `valor == scoreMax`.
 */
val ACHIEVEMENTS: List<Achievement> = listOf(
    // Blocos de tempo
    a("first-block", AchievementTier.Bronze, R.string.ach_first_block_name, R.string.ach_first_block_desc, GamificationStat.BlocksConfirmed to 1),
    a("block-10", AchievementTier.Bronze, R.string.ach_block_10_name, R.string.ach_block_10_desc, GamificationStat.BlocksConfirmed to 10),
    a("block-50", AchievementTier.Silver, R.string.ach_block_50_name, R.string.ach_block_50_desc, GamificationStat.BlocksConfirmed to 50),
    a("block-250", AchievementTier.Gold, R.string.ach_block_250_name, R.string.ach_block_250_desc, GamificationStat.BlocksConfirmed to 250),
    a("block-500", AchievementTier.Platinum, R.string.ach_block_500_name, R.string.ach_block_500_desc, GamificationStat.BlocksConfirmed to 500),
    a("perfect-day", AchievementTier.Silver, R.string.ach_perfect_day_name, R.string.ach_perfect_day_desc, GamificationStat.Days100 to 1),
    a("perfect-days-50", AchievementTier.Gold, R.string.ach_perfect_days_50_name, R.string.ach_perfect_days_50_desc, GamificationStat.Days100 to 50),
    a("streak-week-full", AchievementTier.Gold, R.string.ach_streak_week_name, R.string.ach_streak_week_desc, GamificationStat.BestRoutineStreak to 7),
    a("streak-month-full", AchievementTier.Platinum, R.string.ach_streak_month_name, R.string.ach_streak_month_desc, GamificationStat.BestRoutineStreak to 30),

    // Tarefas e sub-tarefas
    a("first-task", AchievementTier.Bronze, R.string.ach_first_task_name, R.string.ach_first_task_desc, GamificationStat.TasksDone to 1),
    a("task-10", AchievementTier.Bronze, R.string.ach_task_10_name, R.string.ach_task_10_desc, GamificationStat.TasksDone to 10),
    a("task-50", AchievementTier.Silver, R.string.ach_task_50_name, R.string.ach_task_50_desc, GamificationStat.TasksDone to 50),
    a("task-100", AchievementTier.Platinum, R.string.ach_task_100_name, R.string.ach_task_100_desc, GamificationStat.TasksDone to 100),
    a("subtask-25", AchievementTier.Bronze, R.string.ach_subtask_25_name, R.string.ach_subtask_25_desc, GamificationStat.SubtasksDone to 25),
    a("subtask-100", AchievementTier.Gold, R.string.ach_subtask_100_name, R.string.ach_subtask_100_desc, GamificationStat.SubtasksDone to 100),

    // Hábitos bons
    a("first-checkin", AchievementTier.Bronze, R.string.ach_first_checkin_name, R.string.ach_first_checkin_desc, GamificationStat.HabitTargetDays to 1),
    a("checkin-7", AchievementTier.Bronze, R.string.ach_checkin_7_name, R.string.ach_checkin_7_desc, GamificationStat.HabitTargetDays to 7),
    a("checkin-30", AchievementTier.Silver, R.string.ach_checkin_30_name, R.string.ach_checkin_30_desc, GamificationStat.HabitTargetDays to 30),
    a("checkin-100", AchievementTier.Platinum, R.string.ach_checkin_100_name, R.string.ach_checkin_100_desc, GamificationStat.HabitTargetDays to 100),
    a("checkin-500", AchievementTier.Platinum, R.string.ach_checkin_500_name, R.string.ach_checkin_500_desc, GamificationStat.HabitTargetDays to 500),
    a("three-good-habits", AchievementTier.Bronze, R.string.ach_three_good_name, R.string.ach_three_good_desc, GamificationStat.GoodHabitsActive to 3),
    a("five-good-habits", AchievementTier.Silver, R.string.ach_five_good_name, R.string.ach_five_good_desc, GamificationStat.GoodHabitsActive to 5),

    // Hábitos ruins
    a("battling-two", AchievementTier.Bronze, R.string.ach_battling_two_name, R.string.ach_battling_two_desc, GamificationStat.BadHabitsActive to 2),
    a("clean-week", AchievementTier.Silver, R.string.ach_clean_week_name, R.string.ach_clean_week_desc, GamificationStat.BestCleanStreak to 7),
    a("clean-month", AchievementTier.Gold, R.string.ach_clean_month_name, R.string.ach_clean_month_desc, GamificationStat.BestCleanStreak to 30),
    a("clean-60", AchievementTier.Gold, R.string.ach_clean_60_name, R.string.ach_clean_60_desc, GamificationStat.BestCleanStreak to 60),
    a("clean-90", AchievementTier.Platinum, R.string.ach_clean_90_name, R.string.ach_clean_90_desc, GamificationStat.BestCleanStreak to 90),
    a("resilience", AchievementTier.Gold, R.string.ach_resilience_name, R.string.ach_resilience_desc, GamificationStat.RelapsesLogged to 1, GamificationStat.BestCleanStreak to 7),

    // Conexões
    a("first-link", AchievementTier.Bronze, R.string.ach_first_link_name, R.string.ach_first_link_desc, GamificationStat.ActiveConnections to 1),
    a("link-10", AchievementTier.Silver, R.string.ach_link_10_name, R.string.ach_link_10_desc, GamificationStat.ActiveConnections to 10),
    a("link-25", AchievementTier.Gold, R.string.ach_link_25_name, R.string.ach_link_25_desc, GamificationStat.ActiveConnections to 25),
    a("architect", AchievementTier.Silver, R.string.ach_architect_name, R.string.ach_architect_desc, GamificationStat.ActiveConnections to 5),

    // Nota cheia
    a("perfect-10", AchievementTier.Gold, R.string.ach_perfect_10_name, R.string.ach_perfect_10_desc, GamificationStat.PerfectBlocks to 10),
    a("perfect-50", AchievementTier.Platinum, R.string.ach_perfect_50_name, R.string.ach_perfect_50_desc, GamificationStat.PerfectBlocks to 50),

    // XP e níveis
    a("xp-1000", AchievementTier.Bronze, R.string.ach_xp_1000_name, R.string.ach_xp_1000_desc, GamificationStat.TotalXp to 1000),
    a("xp-10000", AchievementTier.Gold, R.string.ach_xp_10000_name, R.string.ach_xp_10000_desc, GamificationStat.TotalXp to 10000),
    a("xp-50000", AchievementTier.Platinum, R.string.ach_xp_50000_name, R.string.ach_xp_50000_desc, GamificationStat.TotalXp to 50000),
    a("level-5", AchievementTier.Bronze, R.string.ach_level_5_name, R.string.ach_level_5_desc, GamificationStat.Level to 5),
    a("level-10", AchievementTier.Silver, R.string.ach_level_10_name, R.string.ach_level_10_desc, GamificationStat.Level to 10),
    a("level-15", AchievementTier.Gold, R.string.ach_level_15_name, R.string.ach_level_15_desc, GamificationStat.Level to 15),
    a("level-20", AchievementTier.Gold, R.string.ach_level_20_name, R.string.ach_level_20_desc, GamificationStat.Level to 20),
    a("level-25", AchievementTier.Platinum, R.string.ach_level_25_name, R.string.ach_level_25_desc, GamificationStat.Level to 25),
    a("level-40", AchievementTier.Platinum, R.string.ach_level_40_name, R.string.ach_level_40_desc, GamificationStat.Level to 40),
)

/** Valor da estatística no retrato atual. */
fun GamificationStats.valueOf(stat: GamificationStat): Int = when (stat) {
    GamificationStat.BlocksConfirmed -> blocksConfirmed
    GamificationStat.PerfectBlocks -> perfectBlocks
    GamificationStat.Days100 -> days100
    GamificationStat.BestRoutineStreak -> bestRoutineStreak
    GamificationStat.TasksDone -> tasksDone
    GamificationStat.SubtasksDone -> subtasksDone
    GamificationStat.ActiveConnections -> activeConnections
    GamificationStat.GoodHabitsActive -> goodHabitsActive
    GamificationStat.BadHabitsActive -> badHabitsActive
    GamificationStat.HabitTargetDays -> habitTargetDays
    GamificationStat.RelapsesLogged -> relapsesLogged
    GamificationStat.BestCleanStreak -> bestCleanStreak
    GamificationStat.TotalXp -> totalXp
    GamificationStat.Level -> level
}

/** A conquista está satisfeita por este retrato? */
fun Achievement.isSatisfiedBy(stats: GamificationStats): Boolean =
    conditions.all { stats.valueOf(it.stat) >= it.target }

/** Progresso da condição mais atrasada (0..1), para a barra da conquista bloqueada. */
fun Achievement.progress(stats: GamificationStats): Float {
    if (conditions.isEmpty()) return 1f
    return conditions.minOf { condition ->
        val value = stats.valueOf(condition.stat)
        (value.toFloat() / condition.target).coerceIn(0f, 1f)
    }
}

/** Conquista por id (nulo se o catálogo mudou e o desbloqueio ficou órfão). */
fun achievementById(id: String): Achievement? = ACHIEVEMENTS.firstOrNull { it.id == id }
