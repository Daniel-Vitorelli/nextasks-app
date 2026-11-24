package com.daniel.nextasks.ui.calendar

import com.daniel.nextasks.data.AgendaEvent
import com.daniel.nextasks.data.BlockOverride
import com.daniel.nextasks.data.EventColorKey
import com.daniel.nextasks.data.EventKind
import com.daniel.nextasks.data.ExtraBlock
import com.daniel.nextasks.data.Routine
import com.daniel.nextasks.data.RoutineBlock
import com.daniel.nextasks.data.RoutineBlockDays
import com.daniel.nextasks.data.RoutineFrequency
import com.daniel.nextasks.data.coversDay
import com.daniel.nextasks.data.isSuspendedOn
import com.daniel.nextasks.data.parseIsoDate
import com.daniel.nextasks.data.scheduledWeekdays
import com.daniel.nextasks.data.suspensions
import com.daniel.nextasks.data.timesOn
import java.time.LocalDate
import java.time.LocalDateTime
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

/**
 * Materializa os blocos das rotinas ativas nos dias informados, gerando os
 * eventos da agenda. Mesma regra do app web (`materializeSchedule`):
 *
 * - rotina inativa não gera nada;
 * - **antes da criação da rotina** não gera nada ([coversDay]);
 * - duração "até" corta os dias depois da data final;
 * - rotina semanal só ocorre nos dias da semana do bloco ([RoutineBlockDays]);
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

    // Suspensão da rotina: um imprevisto suspende sempre; o evento planejado, só se
    // o usuário pediu. A conta fica em `data/` porque a Home e a gamificação usam a
    // mesma resposta.
    val suspensions = agendaEvents.suspensions()

    for (routine in routines) {
        if (!routine.isActive) continue

        for (day in days) {
            // Inativa, antes da criação ou depois do fim: o dia não existe.
            if (!routine.coversDay(day)) continue

            for (original in routine.blocks) {
                val weekly = routine.frequency == RoutineFrequency.Weekly
                if (weekly && !RoutineBlockDays.happensOn(original, day)) {
                    continue
                }

                // Exceção do dia: pode esconder a ocorrência ou trocar o bloco.
                val override = overrideByKey[Triple(routine.id, original.id, day.toString())]
                if (override?.deleted == true) continue
                val block = override?.block ?: original

                val (start, end) = resolveTimes(day, block) ?: continue

                // Suspensa por um evento: some do calendário e da Home, e o dia não
                // passa a contar como perdido (ver `RoutinePeriods`).
                if (block.isSuspendedOn(day, suspensions)) continue

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
                    // A chave da conclusão é (bloco, **dia da ocorrência**) — vale para
                    // rotina diária e semanal. Antes a semanal gravava no domingo da
                    // semana, o que fazia um bloco de Seg/Qua/Sex ter uma confirmação só
                    // para os três dias.
                    completionPeriodStart = day,
                )
            }
        }
    }

    // Blocos avulsos: existem só no dia deles, com ou sem vínculo com rotina.
    val daySet = days.toSet()
    for (extra in extras) {
        val day = parseIsoDate(extra.date) ?: continue
        if (day !in daySet) continue

        val block = extra.toRoutineBlock()
        val (start, end) = resolveTimes(day, block) ?: continue

        // Evento ou imprevisto no período esconde o bloco avulso também: ele faz parte
        // do dia que foi suspenso, não só a rotina. Como ele some dos eventos, também
        // deixa de pesar no progresso do dia — suspender não é perder.
        if (block.isSuspendedOn(day, suspensions)) continue

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
            // Bloco avulso também confirma no dia dele, mesmo preso a rotina semanal.
            completionPeriodStart = day,
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
                // O imprevisto aparece marcado e sabe que suspendeu a rotina.
                isUnforeseen = event.kind == EventKind.Unforeseen,
            )
        }
    }

    return events.sortedBy { it.start }
}

/**
 * Horário de início/fim do bloco no dia, tratando dia inteiro e virada de meia-noite.
 *
 * A conta mora em `data/` ([RoutineBlock.timesOn]) porque a suspensão precisa dela.
 */
internal fun resolveTimes(day: LocalDate, block: RoutineBlock): Pair<LocalDateTime, LocalDateTime>? =
    block.timesOn(day)

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
 *
 * Os dias da semana vêm do próprio bloco ([origin]), não da data do evento: editar
 * a ocorrência de quarta não pode transformar um bloco de Seg/Qua/Sex em "só quarta".
 */
fun CalendarEvent.toEditableBlock(origin: RoutineBlock? = null): RoutineBlock? {
    val id = blockId ?: return null
    val days = origin?.scheduledWeekdays ?: listOf(RoutineBlockDays.indexOf(start.toLocalDate()))
    return RoutineBlock(
        id = id,
        title = title,
        description = description,
        startTime = start.toLocalTime().format(HhMm),
        endTime = end.toLocalTime().format(HhMm),
        isAllDay = isAllDay,
        color = color.toColorKey(),
        weekday = days.first(),
        weekdays = days,
        confirmation = confirmation,
        scoreMax = scoreMax,
    )
}
