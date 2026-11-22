package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** O que acontece com a rotina durante um evento. */
@Serializable
enum class SuspensionMode {
    /** A rotina segue normal. */
    None,

    /** Suspende a rotina nos dias inteiros em que o evento acontece. */
    WholeDays,

    /** Suspende só dentro do horário do evento. */
    WithinHours,
}

/**
 * Evento do calendário — pode durar vários dias.
 *
 * As pontas são independentes: o dia de início pode ser "dia inteiro" ou ter um
 * horário, e o dia de término também. Um evento de um dia só usa as duas pontas
 * no mesmo dia (dia inteiro ou início e fim com hora).
 */
@Serializable
data class AgendaEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    /** Nulo = o dia de início é dia inteiro. */
    val startTime: String? = null,
    /** Nulo = o dia de término é dia inteiro. */
    val endTime: String? = null,
    val color: EventColorKey = EventColorKey.Blue,
    val suspension: SuspensionMode = SuspensionMode.None,
) {
    val start: LocalDate get() = LocalDate.parse(startDate)
    val end: LocalDate get() = LocalDate.parse(endDate)
    val isSingleDay: Boolean get() = startDate == endDate

    /** Todos os dias cobertos pelo evento, inclusive as pontas. */
    fun days(): List<LocalDate> {
        val first = start
        val last = end
        if (last.isBefore(first)) return listOf(first)
        val result = mutableListOf<LocalDate>()
        var day = first
        while (!day.isAfter(last)) {
            result += day
            day = day.plusDays(1)
        }
        return result
    }

    /**
     * Fatias do evento, uma por dia. O calendário já sabe desenhar eventos de um
     * dia só, então um evento longo vira uma sequência de fatias contínuas:
     * no dia inicial vai do horário até o fim do dia, nos dias do meio ocupa o
     * dia inteiro e no dia final vai da meia-noite até o horário.
     */
    fun segments(): List<EventSegment> {
        val days = days()
        val startAt = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val endAt = endTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

        return days.mapIndexed { index, day ->
            val first = index == 0
            val last = index == days.lastIndex

            val from = if (first) startAt?.let { LocalDateTime.of(day, it) } else day.atStartOfDay()
            val to = if (last) {
                endAt?.let { LocalDateTime.of(day, it) } ?: day.plusDays(1).atStartOfDay()
            } else {
                day.plusDays(1).atStartOfDay()
            }

            EventSegment(
                date = day,
                start = from ?: day.atStartOfDay(),
                end = if (to.isAfter(from ?: day.atStartOfDay())) {
                    to
                } else {
                    (from ?: day.atStartOfDay()).plusHours(1)
                },
                // "Dia inteiro" só quando a fatia cobre o dia todo.
                isAllDay = (from ?: day.atStartOfDay()) == day.atStartOfDay() &&
                    to == day.plusDays(1).atStartOfDay(),
            )
        }
    }
}

/** Fatia de um evento em um dia específico. */
data class EventSegment(
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean,
)
