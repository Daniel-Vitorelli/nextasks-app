package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Bom = quer manter; ruim = quer largar (a marcação vira recaída). */
@Serializable
enum class HabitKind { Good, Bad }

@Serializable
enum class HabitFrequency { Daily, Weekly }

/**
 * Hábito — espelha o `model Habit` do app web.
 *
 * `daysOfWeek` só vale para hábito bom diário (0 = domingo). Bom semanal e
 * ruim são acompanhados todos os dias. `targetCount` é quantas confirmações
 * fecham o período (dia ou semana).
 */
@Serializable
data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    /** Chave do ícone em [habitIconRes]. */
    val icon: String = "target",
    val color: EventColorKey = EventColorKey.Green,
    val kind: HabitKind = HabitKind.Good,
    val frequency: HabitFrequency = HabitFrequency.Daily,
    val daysOfWeek: List<Int> = (0..6).toList(),
    /** Nulo = hábito sem meta: só conta ocorrências, sem alvo a atingir. */
    val targetCount: Int? = 1,
    /** Permite continuar marcando depois de bater a meta. */
    val allowExceed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Existe meta a bater? */
    val hasGoal: Boolean get() = targetCount != null && targetCount > 0

    /** Só hábito bom diário restringe dias; os demais valem todo dia. */
    val usesSchedule: Boolean
        get() = kind == HabitKind.Good && frequency == HabitFrequency.Daily
}

/** Uma confirmação (ou recaída) do hábito em um dia. Chave: (habitId, date). */
@Serializable
data class HabitCompletion(
    val habitId: String,
    val date: String,
    val count: Int = 1,
)
