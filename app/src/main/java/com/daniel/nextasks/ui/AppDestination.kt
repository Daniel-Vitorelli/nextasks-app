package com.daniel.nextasks.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Addon

/**
 * Seções de nível superior do NexTasks.
 *
 * São 9 destinos — acima do limite de 5 de uma barra inferior — por isso a navegação
 * principal é um [androidx.compose.material3.ModalNavigationDrawer]. Três deles
 * (Conexões, IA e Gamificação) são addons e só aparecem depois de ligados.
 */
enum class AppDestination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Home(R.string.section_home, R.drawable.ic_home),
    Calendar(R.string.section_calendar, R.drawable.ic_calendar),
    Routines(R.string.section_routines, R.drawable.ic_repeat),
    Tasks(R.string.section_tasks, R.drawable.ic_tasks),
    Habits(R.string.section_habits, R.drawable.ic_habit_target),
    Connections(R.string.section_connections, R.drawable.ic_link),
    Ai(R.string.section_ai, R.drawable.ic_ai),
    Gamification(R.string.section_gamification, R.drawable.ic_gamification),
    Settings(R.string.section_settings, R.drawable.ic_settings);

    /**
     * Addon dono desta seção, ou nulo se ela é fixa.
     *
     * Seções fixas aparecem sempre na gaveta; as com addon só quando ligadas.
     */
    val addon: Addon?
        get() = when (this) {
            Ai -> Addon.Ai
            Gamification -> Addon.Gamification
            Connections -> Addon.Connections
            else -> null
        }
}
