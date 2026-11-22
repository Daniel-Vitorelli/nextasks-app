package com.daniel.nextasks.ui.calendar

import androidx.annotation.StringRes
import com.daniel.nextasks.R

/**
 * Visualizações do Google Calendar para Android:
 * Programação, Dia, 3 dias, Semana e Mês.
 *
 * A troca é feita pelo menu de três pontos (overflow) da barra superior.
 */
enum class CalendarViewMode(@StringRes val label: Int) {
    Schedule(R.string.view_schedule),
    Day(R.string.view_day),
    ThreeDays(R.string.view_three_days),
    Week(R.string.view_week),
    Month(R.string.view_month),
}
