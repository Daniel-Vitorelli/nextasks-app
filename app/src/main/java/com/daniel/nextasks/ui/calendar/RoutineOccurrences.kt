package com.daniel.nextasks.ui.calendar

import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.BlockOverride
import com.daniel.nextasks.data.EventColorKey
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineDuration
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.SuspensionMode
import com.daniel.nextasks.data.RoutineFrequency
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Converte a chave de cor guardada no banco para a cor de exibição. */
fun EventColorKey.toEventColor(): EventColor = when (this) {
    EventColorKey.Red -> EventColor.Red
    EventColorKey.Orange -> EventColor.Orange
    EventColorKey.Yellow -> EventColor.Yellow
    EventColorKey.Green -> EventColor.Green
    EventColorKey.Blue -> EventColor.Blue
    EventColorKey.Purple -> EventColor.Purple
    EventColorKey.Gray -> EventColor.Gray
}

private fun parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value) }.getOrNull()

/**
 * Materializa os blocos das rotinas ativas nos dias informados, gerando os
 * eventos da agenda. Mesma regra do app web (`materializeSchedule`):
 *
 * - rotina inativa não gera nada;
 * - duração "até" corta os dias depois da data final;
 * - rotina semanal só ocorre no dia da semana do bloco;
 * - bloco que termina antes de começar atravessa a meia-noite;
 * - exceção do dia ([BlockOverride]) substitui ou esconde a ocorrência daquele dia,
 *   sem tocar na rotina.
 */
fun routineOccurrences(
    routines: List<Routine>,
    days: List<LocalDate>,
    overrides: List<BlockOverride> = emptyList(),
    extras: List<ExtraBlock> = emptyList(),
    agendaEvents: List<AgendaEvent> = emptyList(),
): List<CalendarEvent> {
    val events = mutableListOf<CalendarEvent>()
    val overrideByKey = overrides.associateBy { it.key }

    // Suspensão da rotina: dias inteiros e janelas de horário dos eventos.
    val suspendedDays = mutableSetOf<LocalDate>()
    val suspendedWindows = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    for (event in agendaEvents) {
        when (event.suspension) {
            SuspensionMode.None -> Unit
            SuspensionMode.WholeDays -> suspendedDays += event.days()
            SuspensionMode.WithinHours -> suspendedWindows += event.segments()
                .map { it.start to it.end }
        }
    }

    for (routine in routines) {
        if (!routine.isActive) continue
        val endDate = routine.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        for (day in days) {
            if (routine.duration == RoutineDuration.Until && endDate != null && day.isAfter(endDate)) {
                continue
            }

            for (original in routine.blocks) {
                if (routine.frequency == RoutineFrequency.Weekly &&
                    day.dayOfWeek.value % 7 != original.weekday
                ) {
                    continue
                }

                // Exceção do dia: pode esconder a ocorrência ou trocar o bloco.
                val override = overrideByKey[Triple(routine.id, original.id, day.toString())]
                if (override?.deleted == true) continue
                val block = override?.block ?: original

                val (start, end) = resolveTimes(day, block) ?: continue

                // Rotina suspensa pelo evento: some do calendário e da Home.
                if (day in suspendedDays) continue
                if (suspendedWindows.any { it.first.isBefore(end) && start.isBefore(it.second) }) {
                    continue
                }

                events += CalendarEvent(
                    id = "${block.id}:$day",
                    title = block.title,
                    location = routine.name,
                    start = start,
                    end = end,
                    isAllDay = block.isAllDay,
                    color = block.color.toEventColor(),
                    description = block.description,
                    blockId = block.id,
                    routineId = routine.id,
                    confirmation = block.confirmation,
                    scoreMax = block.scoreMax,
                    isOverridden = override != null,
                    completionPeriodStart = if (routine.frequency == RoutineFrequency.Weekly) {
                        day.startOfWeek()
                    } else {
                        day
                    },
                )
            }
        }
    }

    // Blocos avulsos: existem só no dia deles, com ou sem vínculo com rotina.
    val daySet = days.toSet()
    for (extra in extras) {
        val day = runCatching { LocalDate.parse(extra.date) }.getOrNull() ?: continue
        if (day !in daySet) continue

        val block = extra.toRoutineBlock()
        val (start, end) = resolveTimes(day, block) ?: continue
        val routine = routines.firstOrNull { it.id == extra.routineId }

        events += CalendarEvent(
            id = "extra:${extra.id}:$day",
            title = block.title,
            location = routine?.name,
            start = start,
            end = end,
            isAllDay = block.isAllDay,
            color = block.color.toEventColor(),
            description = block.description,
            blockId = extra.id,
            routineId = extra.routineId,
            confirmation = block.confirmation,
            scoreMax = block.scoreMax,
            isExtra = true,
            completionPeriodStart = if (routine?.frequency == RoutineFrequency.Weekly) {
                day.startOfWeek()
            } else {
                day
            },
        )
    }

    // Eventos: uma fatia por dia, para aproveitar as visualizações existentes.
    for (event in agendaEvents) {
        for (segment in event.segments()) {
            if (segment.date !in daySet) continue
            events += CalendarEvent(
                id = "event:${event.id}:${segment.date}",
                title = event.title,
                location = null,
                start = segment.start,
                end = segment.end,
                isAllDay = segment.isAllDay,
                color = event.color.toEventColor(),
                description = event.description,
                isEvent = true,
                eventId = event.id,
            )
        }
    }

    return events.sortedBy { it.start }
}

/** Horário de início/fim do bloco no dia, tratando dia inteiro e virada de meia-noite. */
internal fun resolveTimes(day: LocalDate, block: RoutineBlock): Pair<LocalDateTime, LocalDateTime>? {
    if (block.isAllDay) {
        return day.atStartOfDay() to day.plusDays(1).atStartOfDay()
    }
    val startTime = parseTime(block.startTime) ?: return null
    val endTime = parseTime(block.endTime) ?: return null
    val start = LocalDateTime.of(day, startTime)
    val sameDayEnd = LocalDateTime.of(day, endTime)
    val end = if (sameDayEnd.isAfter(start)) sameDayEnd else sameDayEnd.plusDays(1)
    return start to end
}

private val HhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Caminho inverso de [toEventColor], para reabrir o formulário preenchido. */
fun EventColor.toColorKey(): EventColorKey = when (this) {
    EventColor.Red -> EventColorKey.Red
    EventColor.Orange -> EventColorKey.Orange
    EventColor.Yellow -> EventColorKey.Yellow
    EventColor.Green -> EventColorKey.Green
    EventColor.Blue -> EventColorKey.Blue
    EventColor.Purple -> EventColorKey.Purple
    EventColor.Gray -> EventColorKey.Gray
}

/**
 * Reconstrói o bloco a partir do evento exibido, para abrir o formulário já
 * preenchido ao editar a ocorrência de um dia.
 */
fun CalendarEvent.toEditableBlock(): RoutineBlock? = blockId?.let { id ->
    RoutineBlock(
        id = id,
        title = title,
        description = description,
        startTime = start.toLocalTime().format(HhMm),
        endTime = end.toLocalTime().format(HhMm),
        isAllDay = isAllDay,
        color = color.toColorKey(),
        weekday = start.dayOfWeek.value % 7,
        confirmation = confirmation,
        scoreMax = scoreMax,
    )
}
