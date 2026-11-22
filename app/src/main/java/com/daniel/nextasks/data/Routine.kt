package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Com que frequência a rotina se repete. Espelha o `Frequency` do app web. */
@Serializable
enum class RoutineFrequency {
    Daily,
    Weekly,
}

/** Por quanto tempo a rotina fica ativa. Espelha o `Duration` do app web. */
@Serializable
enum class RoutineDuration {
    Indefinite,
    Until,
}

/** Cores de evento do NexTasks (mesmos tokens `--event-*` do app web). */
@Serializable
enum class EventColorKey {
    Red,
    Orange,
    Yellow,
    Green,
    Blue,
    Purple,
    Gray,
}

/** Como o bloco é confirmado. Espelha o `EventConfirmation` do app web. */
@Serializable
enum class EventConfirmation {
    None,
    Checklist,
    Score,
}

/**
 * Bloco de horário dentro de uma rotina. Espelha o `TimeBlock` do app web.
 *
 * `startTime`/`endTime` ficam como "HH:mm" (horário de parede). Em rotinas
 * semanais, `weekday` diz em que dia da semana o bloco acontece (0 = domingo).
 */
@Serializable
data class RoutineBlock(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val isAllDay: Boolean = false,
    val color: EventColorKey = EventColorKey.Green,
    val weekday: Int = 1,
    val confirmation: EventConfirmation = EventConfirmation.None,
    /** Nota máxima quando a confirmação é [EventConfirmation.Score] (escala de 1 a N). */
    val scoreMax: Int = 5,
)

/**
 * Rotina do NexTasks.
 *
 * Mesmos campos do `Routine` do app web (name, description, frequency, duration,
 * endDate, isActive), mais os blocos de horário que geram os eventos da agenda.
 * `endDate` fica como texto ISO (yyyy-MM-dd) para não depender de fuso.
 */
@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val frequency: RoutineFrequency = RoutineFrequency.Daily,
    val duration: RoutineDuration = RoutineDuration.Indefinite,
    val endDate: String? = null,
    // Igual ao app web (`isActive @default(false)`): rotina nova nasce inativa.
    // Quem ativa é o botão de power — e só uma pode ficar ativa por vez.
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val blocks: List<RoutineBlock> = emptyList(),
)
