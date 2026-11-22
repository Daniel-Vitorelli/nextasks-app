package com.daniel.nextasks.ui.habits

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Habit
import com.daniel.nextasks.data.HabitFrequency
import com.daniel.nextasks.data.HabitKind

/** Texto do status de hoje: "2 de 3", "4× hoje", "Dia limpo" ou "Recaída hoje". */
@Composable
fun habitTodayLabel(habit: Habit, count: Int): String = when {
    habit.kind == HabitKind.Bad && count > 0 -> stringResource(R.string.habit_slip_today)
    habit.kind == HabitKind.Bad -> stringResource(R.string.habit_clean_today)
    !habit.hasGoal -> stringResource(R.string.habit_count_today, count)
    else -> stringResource(R.string.habit_today_count, count, habit.targetCount ?: 0)
}

/** Linha de apoio: "Diário · 3×", "Semanal · sem meta" ou "Quero largar". */
@Composable
fun habitGoalLabel(habit: Habit): String {
    if (habit.kind == HabitKind.Bad) return stringResource(R.string.habit_kind_bad)

    val frequency = if (habit.frequency == HabitFrequency.Weekly) {
        stringResource(R.string.habit_weekly)
    } else {
        stringResource(R.string.habit_daily)
    }
    val goal = habit.targetCount?.let { "${it}×" } ?: stringResource(R.string.habit_no_goal)
    return "$frequency · $goal"
}
