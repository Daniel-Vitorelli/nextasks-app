package com.daniel.nextasks.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.daniel.nextasks.R

/** Nível de raridade da conquista; define a recompensa em XP. */
enum class AchievementTier(@StringRes val label: Int, val xp: Int) {
    Bronze(R.string.achievement_tier_bronze, 50),
    Silver(R.string.achievement_tier_silver, 100),
    Gold(R.string.achievement_tier_gold, 250),
    Platinum(R.string.achievement_tier_platinum, 500),
}

/**
 * Família da conquista. Organiza a lista em seções e dá um ícone próprio a cada
 * linha — antes todas usavam o mesmo troféu.
 */
enum class AchievementCategory(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Blocks(R.string.ach_category_blocks, R.drawable.ic_check_circle),
    Tasks(R.string.ach_category_tasks, R.drawable.ic_habit_assignment),
    Habits(R.string.ach_category_habits, R.drawable.ic_habit_target),
    Clean(R.string.ach_category_clean, R.drawable.ic_habit_eco),
    Connections(R.string.ach_category_connections, R.drawable.ic_link),
    Progress(R.string.ach_category_progress, R.drawable.ic_habit_bolt),
    Special(R.string.ach_category_special, R.drawable.ic_habit_stars),
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
    val category: AchievementCategory,
    val tier: AchievementTier,
    @StringRes val name: Int,
    @StringRes val description: Int,
    val conditions: List<AchievementCondition>,
)

private fun a(
    id: String,
    category: AchievementCategory,
    tier: AchievementTier,
    @StringRes name: Int,
    @StringRes description: Int,
    vararg conditions: Pair<GamificationStat, Int>,
): Achievement = Achievement(
    id = id,
    category = category,
    tier = tier,
    name = name,
    description = description,
    conditions = conditions.map { AchievementCondition(it.first, it.second) },
)

/**
 * Catálogo v2: 76 conquistas em sete famílias.
 *
 * Os 43 ids originais foram preservados (mesmas condições) para não invalidar
 * desbloqueios já gravados; as demais são degraus novos entre eles ou no topo.
 *
 * Divergência consciente: o web fala em nota **10** (`perfect10`), mas no Android
 * a nota é `1..scoreMax` por bloco. Aqui "nota cheia" é `valor == scoreMax`.
 */
val ACHIEVEMENTS: List<Achievement> = listOf(
    // ---------- Blocos de tempo ----------
    a("first-block", AchievementCategory.Blocks, AchievementTier.Bronze, R.string.ach_first_block_name, R.string.ach_first_block_desc, GamificationStat.BlocksConfirmed to 1),
    a("block-10", AchievementCategory.Blocks, AchievementTier.Bronze, R.string.ach_block_10_name, R.string.ach_block_10_desc, GamificationStat.BlocksConfirmed to 10),
    a("block-25", AchievementCategory.Blocks, AchievementTier.Bronze, R.string.ach_block_25_name, R.string.ach_block_25_desc, GamificationStat.BlocksConfirmed to 25),
    a("block-50", AchievementCategory.Blocks, AchievementTier.Silver, R.string.ach_block_50_name, R.string.ach_block_50_desc, GamificationStat.BlocksConfirmed to 50),
    a("block-100", AchievementCategory.Blocks, AchievementTier.Silver, R.string.ach_block_100_name, R.string.ach_block_100_desc, GamificationStat.BlocksConfirmed to 100),
    a("block-250", AchievementCategory.Blocks, AchievementTier.Gold, R.string.ach_block_250_name, R.string.ach_block_250_desc, GamificationStat.BlocksConfirmed to 250),
    a("block-500", AchievementCategory.Blocks, AchievementTier.Platinum, R.string.ach_block_500_name, R.string.ach_block_500_desc, GamificationStat.BlocksConfirmed to 500),
    a("block-1000", AchievementCategory.Blocks, AchievementTier.Platinum, R.string.ach_block_1000_name, R.string.ach_block_1000_desc, GamificationStat.BlocksConfirmed to 1000),
    a("perfect-day", AchievementCategory.Blocks, AchievementTier.Silver, R.string.ach_perfect_day_name, R.string.ach_perfect_day_desc, GamificationStat.Days100 to 1),
    a("perfect-days-10", AchievementCategory.Blocks, AchievementTier.Silver, R.string.ach_perfect_days_10_name, R.string.ach_perfect_days_10_desc, GamificationStat.Days100 to 10),
    a("perfect-days-50", AchievementCategory.Blocks, AchievementTier.Gold, R.string.ach_perfect_days_50_name, R.string.ach_perfect_days_50_desc, GamificationStat.Days100 to 50),
    a("perfect-days-100", AchievementCategory.Blocks, AchievementTier.Platinum, R.string.ach_perfect_days_100_name, R.string.ach_perfect_days_100_desc, GamificationStat.Days100 to 100),
    a("streak-week-full", AchievementCategory.Blocks, AchievementTier.Gold, R.string.ach_streak_week_name, R.string.ach_streak_week_desc, GamificationStat.BestRoutineStreak to 7),
    a("streak-14", AchievementCategory.Blocks, AchievementTier.Gold, R.string.ach_streak_14_name, R.string.ach_streak_14_desc, GamificationStat.BestRoutineStreak to 14),
    a("streak-month-full", AchievementCategory.Blocks, AchievementTier.Platinum, R.string.ach_streak_month_name, R.string.ach_streak_month_desc, GamificationStat.BestRoutineStreak to 30),
    a("streak-60", AchievementCategory.Blocks, AchievementTier.Platinum, R.string.ach_streak_60_name, R.string.ach_streak_60_desc, GamificationStat.BestRoutineStreak to 60),

    // ---------- Tarefas e sub-tarefas ----------
    a("first-task", AchievementCategory.Tasks, AchievementTier.Bronze, R.string.ach_first_task_name, R.string.ach_first_task_desc, GamificationStat.TasksDone to 1),
    a("task-5", AchievementCategory.Tasks, AchievementTier.Bronze, R.string.ach_task_5_name, R.string.ach_task_5_desc, GamificationStat.TasksDone to 5),
    a("task-10", AchievementCategory.Tasks, AchievementTier.Bronze, R.string.ach_task_10_name, R.string.ach_task_10_desc, GamificationStat.TasksDone to 10),
    a("task-25", AchievementCategory.Tasks, AchievementTier.Silver, R.string.ach_task_25_name, R.string.ach_task_25_desc, GamificationStat.TasksDone to 25),
    a("task-50", AchievementCategory.Tasks, AchievementTier.Silver, R.string.ach_task_50_name, R.string.ach_task_50_desc, GamificationStat.TasksDone to 50),
    a("task-100", AchievementCategory.Tasks, AchievementTier.Platinum, R.string.ach_task_100_name, R.string.ach_task_100_desc, GamificationStat.TasksDone to 100),
    a("task-250", AchievementCategory.Tasks, AchievementTier.Platinum, R.string.ach_task_250_name, R.string.ach_task_250_desc, GamificationStat.TasksDone to 250),
    a("subtask-10", AchievementCategory.Tasks, AchievementTier.Bronze, R.string.ach_subtask_10_name, R.string.ach_subtask_10_desc, GamificationStat.SubtasksDone to 10),
    a("subtask-25", AchievementCategory.Tasks, AchievementTier.Bronze, R.string.ach_subtask_25_name, R.string.ach_subtask_25_desc, GamificationStat.SubtasksDone to 25),
    a("subtask-50", AchievementCategory.Tasks, AchievementTier.Silver, R.string.ach_subtask_50_name, R.string.ach_subtask_50_desc, GamificationStat.SubtasksDone to 50),
    a("subtask-100", AchievementCategory.Tasks, AchievementTier.Gold, R.string.ach_subtask_100_name, R.string.ach_subtask_100_desc, GamificationStat.SubtasksDone to 100),

    // ---------- Hábitos bons ----------
    a("first-checkin", AchievementCategory.Habits, AchievementTier.Bronze, R.string.ach_first_checkin_name, R.string.ach_first_checkin_desc, GamificationStat.HabitTargetDays to 1),
    a("checkin-3", AchievementCategory.Habits, AchievementTier.Bronze, R.string.ach_checkin_3_name, R.string.ach_checkin_3_desc, GamificationStat.HabitTargetDays to 3),
    a("checkin-7", AchievementCategory.Habits, AchievementTier.Bronze, R.string.ach_checkin_7_name, R.string.ach_checkin_7_desc, GamificationStat.HabitTargetDays to 7),
    a("checkin-14", AchievementCategory.Habits, AchievementTier.Silver, R.string.ach_checkin_14_name, R.string.ach_checkin_14_desc, GamificationStat.HabitTargetDays to 14),
    a("checkin-30", AchievementCategory.Habits, AchievementTier.Silver, R.string.ach_checkin_30_name, R.string.ach_checkin_30_desc, GamificationStat.HabitTargetDays to 30),
    a("checkin-50", AchievementCategory.Habits, AchievementTier.Silver, R.string.ach_checkin_50_name, R.string.ach_checkin_50_desc, GamificationStat.HabitTargetDays to 50),
    a("checkin-100", AchievementCategory.Habits, AchievementTier.Platinum, R.string.ach_checkin_100_name, R.string.ach_checkin_100_desc, GamificationStat.HabitTargetDays to 100),
    a("checkin-250", AchievementCategory.Habits, AchievementTier.Platinum, R.string.ach_checkin_250_name, R.string.ach_checkin_250_desc, GamificationStat.HabitTargetDays to 250),
    a("checkin-500", AchievementCategory.Habits, AchievementTier.Platinum, R.string.ach_checkin_500_name, R.string.ach_checkin_500_desc, GamificationStat.HabitTargetDays to 500),
    a("three-good-habits", AchievementCategory.Habits, AchievementTier.Bronze, R.string.ach_three_good_name, R.string.ach_three_good_desc, GamificationStat.GoodHabitsActive to 3),
    a("five-good-habits", AchievementCategory.Habits, AchievementTier.Silver, R.string.ach_five_good_name, R.string.ach_five_good_desc, GamificationStat.GoodHabitsActive to 5),
    a("ten-good-habits", AchievementCategory.Habits, AchievementTier.Gold, R.string.ach_ten_good_name, R.string.ach_ten_good_desc, GamificationStat.GoodHabitsActive to 10),

    // ---------- Hábitos ruins e dias limpos ----------
    a("bad-habits-1", AchievementCategory.Clean, AchievementTier.Bronze, R.string.ach_bad_habits_1_name, R.string.ach_bad_habits_1_desc, GamificationStat.BadHabitsActive to 1),
    a("battling-two", AchievementCategory.Clean, AchievementTier.Bronze, R.string.ach_battling_two_name, R.string.ach_battling_two_desc, GamificationStat.BadHabitsActive to 2),
    a("bad-habits-3", AchievementCategory.Clean, AchievementTier.Silver, R.string.ach_bad_habits_3_name, R.string.ach_bad_habits_3_desc, GamificationStat.BadHabitsActive to 3),
    a("clean-week", AchievementCategory.Clean, AchievementTier.Silver, R.string.ach_clean_week_name, R.string.ach_clean_week_desc, GamificationStat.BestCleanStreak to 7),
    a("clean-14", AchievementCategory.Clean, AchievementTier.Silver, R.string.ach_clean_14_name, R.string.ach_clean_14_desc, GamificationStat.BestCleanStreak to 14),
    a("clean-month", AchievementCategory.Clean, AchievementTier.Gold, R.string.ach_clean_month_name, R.string.ach_clean_month_desc, GamificationStat.BestCleanStreak to 30),
    a("clean-60", AchievementCategory.Clean, AchievementTier.Gold, R.string.ach_clean_60_name, R.string.ach_clean_60_desc, GamificationStat.BestCleanStreak to 60),
    a("clean-90", AchievementCategory.Clean, AchievementTier.Platinum, R.string.ach_clean_90_name, R.string.ach_clean_90_desc, GamificationStat.BestCleanStreak to 90),
    a("clean-180", AchievementCategory.Clean, AchievementTier.Platinum, R.string.ach_clean_180_name, R.string.ach_clean_180_desc, GamificationStat.BestCleanStreak to 180),
    a("resilience", AchievementCategory.Clean, AchievementTier.Gold, R.string.ach_resilience_name, R.string.ach_resilience_desc, GamificationStat.RelapsesLogged to 1, GamificationStat.BestCleanStreak to 7),

    // ---------- Conexões ----------
    a("first-link", AchievementCategory.Connections, AchievementTier.Bronze, R.string.ach_first_link_name, R.string.ach_first_link_desc, GamificationStat.ActiveConnections to 1),
    a("link-3", AchievementCategory.Connections, AchievementTier.Bronze, R.string.ach_link_3_name, R.string.ach_link_3_desc, GamificationStat.ActiveConnections to 3),
    a("link-10", AchievementCategory.Connections, AchievementTier.Silver, R.string.ach_link_10_name, R.string.ach_link_10_desc, GamificationStat.ActiveConnections to 10),
    a("link-25", AchievementCategory.Connections, AchievementTier.Gold, R.string.ach_link_25_name, R.string.ach_link_25_desc, GamificationStat.ActiveConnections to 25),
    a("link-50", AchievementCategory.Connections, AchievementTier.Platinum, R.string.ach_link_50_name, R.string.ach_link_50_desc, GamificationStat.ActiveConnections to 50),
    a("architect", AchievementCategory.Connections, AchievementTier.Silver, R.string.ach_architect_name, R.string.ach_architect_desc, GamificationStat.ActiveConnections to 5),
    a("architect-10", AchievementCategory.Connections, AchievementTier.Gold, R.string.ach_architect_10_name, R.string.ach_architect_10_desc, GamificationStat.ActiveConnections to 10),

    // ---------- Nota cheia, XP e níveis ----------
    a("perfect-10", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_perfect_10_name, R.string.ach_perfect_10_desc, GamificationStat.PerfectBlocks to 10),
    a("perfect-50", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_perfect_50_name, R.string.ach_perfect_50_desc, GamificationStat.PerfectBlocks to 50),
    a("xp-500", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_xp_500_name, R.string.ach_xp_500_desc, GamificationStat.TotalXp to 500),
    a("xp-1000", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_xp_1000_name, R.string.ach_xp_1000_desc, GamificationStat.TotalXp to 1000),
    a("xp-2500", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_xp_2500_name, R.string.ach_xp_2500_desc, GamificationStat.TotalXp to 2500),
    a("xp-10000", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_xp_10000_name, R.string.ach_xp_10000_desc, GamificationStat.TotalXp to 10000),
    a("xp-25000", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_xp_25000_name, R.string.ach_xp_25000_desc, GamificationStat.TotalXp to 25000),
    a("xp-50000", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_xp_50000_name, R.string.ach_xp_50000_desc, GamificationStat.TotalXp to 50000),
    a("xp-100000", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_xp_100000_name, R.string.ach_xp_100000_desc, GamificationStat.TotalXp to 100000),
    a("level-3", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_level_3_name, R.string.ach_level_3_desc, GamificationStat.Level to 3),
    a("level-5", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_level_5_name, R.string.ach_level_5_desc, GamificationStat.Level to 5),
    a("level-7", AchievementCategory.Progress, AchievementTier.Bronze, R.string.ach_level_7_name, R.string.ach_level_7_desc, GamificationStat.Level to 7),
    a("level-10", AchievementCategory.Progress, AchievementTier.Silver, R.string.ach_level_10_name, R.string.ach_level_10_desc, GamificationStat.Level to 10),
    a("level-15", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_level_15_name, R.string.ach_level_15_desc, GamificationStat.Level to 15),
    a("level-20", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_level_20_name, R.string.ach_level_20_desc, GamificationStat.Level to 20),
    a("level-25", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_level_25_name, R.string.ach_level_25_desc, GamificationStat.Level to 25),
    a("level-30", AchievementCategory.Progress, AchievementTier.Gold, R.string.ach_level_30_name, R.string.ach_level_30_desc, GamificationStat.Level to 30),
    a("level-40", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_level_40_name, R.string.ach_level_40_desc, GamificationStat.Level to 40),
    a("level-50", AchievementCategory.Progress, AchievementTier.Platinum, R.string.ach_level_50_name, R.string.ach_level_50_desc, GamificationStat.Level to 50),

    // ---------- Combinações ----------
    a("balance", AchievementCategory.Special, AchievementTier.Gold, R.string.ach_balance_name, R.string.ach_balance_desc, GamificationStat.BlocksConfirmed to 100, GamificationStat.TasksDone to 50, GamificationStat.HabitTargetDays to 30),
    a("machine", AchievementCategory.Special, AchievementTier.Gold, R.string.ach_machine_name, R.string.ach_machine_desc, GamificationStat.Days100 to 10, GamificationStat.BestRoutineStreak to 14),
    a("veteran", AchievementCategory.Special, AchievementTier.Platinum, R.string.ach_veteran_name, R.string.ach_veteran_desc, GamificationStat.TotalXp to 25000, GamificationStat.Level to 30),
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

/**
 * A condição mais atrasada, como (valor atual, alvo) — o "3 de 10" da conquista.
 *
 * Usa a mesma escolha de [progress]: a de menor razão valor/alvo. Condição com
 * alvo zero conta como satisfeita e não vira detalhe.
 */
fun Achievement.progressDetail(stats: GamificationStats): Pair<Int, Int>? {
    if (conditions.isEmpty()) return null
    val behind = conditions
        .filter { it.target > 0 }
        .minByOrNull { condition ->
            stats.valueOf(condition.stat).toFloat() / condition.target
        }
        ?: return null
    return stats.valueOf(behind.stat) to behind.target
}

/** Conquista por id (nulo se o catálogo mudou e o desbloqueio ficou órfão). */
fun achievementById(id: String): Achievement? = ACHIEVEMENTS.firstOrNull { it.id == id }
