package com.daniel.nextasks.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Uma fatia de tempo em que a rotina fica suspensa. */
data class SuspensionWindow(val start: LocalDateTime, val end: LocalDateTime)

/**
 * O que os eventos da agenda suspendem da rotina.
 *
 * Suspender significa **esconder a ocorrência**: o bloco não aparece, não pode ser
 * confirmado e não conta como perdido. É a mesma resposta usada pelo calendário, pela
 * Home, pelo motor de conexões e pela gamificação — antes cada um refazia a conta.
 */
data class Suspensions(
    val wholeDays: Set<LocalDate> = emptySet(),
    val windows: List<SuspensionWindow> = emptyList(),
) {
    val isEmpty: Boolean get() = wholeDays.isEmpty() && windows.isEmpty()

    /** O dia está suspenso por inteiro? */
    fun covers(day: LocalDate): Boolean = day in wholeDays

    /** A janela `[start, end)` encosta em alguma suspensão? */
    fun overlaps(start: LocalDateTime, end: LocalDateTime): Boolean =
        windows.any { window -> window.start.isBefore(end) && start.isBefore(window.end) }

    companion object {
        /** Sem nenhum evento suspendendo — o caso comum. */
        val None = Suspensions()
    }
}

/**
 * Lê os eventos da agenda e devolve as suspensões que eles impõem.
 *
 * Um imprevisto entra sempre, no horário dele ([AgendaEvent.effectiveSuspension]);
 * o evento planejado só entra se o usuário escolheu suspender.
 */
fun List<AgendaEvent>.suspensions(): Suspensions {
    if (isEmpty()) return Suspensions.None

    var days = emptySet<LocalDate>()
    val windows = mutableListOf<SuspensionWindow>()
    for (event in this) {
        when (event.effectiveSuspension) {
            SuspensionMode.None -> Unit
            SuspensionMode.WholeDays -> days = days + event.days()
            SuspensionMode.WithinHours -> event.segments().forEach { segment ->
                windows += SuspensionWindow(segment.start, segment.end)
            }
        }
    }
    return Suspensions(wholeDays = days, windows = windows)
}

/**
 * Horário do bloco no dia, já tratando dia inteiro e virada de meia-noite.
 *
 * Mora em `data/` porque a suspensão precisa da mesma conta: um bloco 22:00–01:00
 * atravessa o dia, e comparar com o evento pela hora solta erraria.
 */
fun RoutineBlock.timesOn(day: LocalDate): Pair<LocalDateTime, LocalDateTime>? {
    if (isAllDay) return day.atStartOfDay() to day.plusDays(1).atStartOfDay()

    val from = parseIsoTime(startTime) ?: return null
    val to = parseIsoTime(endTime) ?: return null
    val start = LocalDateTime.of(day, from)
    val sameDayEnd = LocalDateTime.of(day, to)
    val end = if (sameDayEnd.isAfter(start)) sameDayEnd else sameDayEnd.plusDays(1)
    return start to end
}

/** Esta ocorrência (bloco neste dia) some por causa de um evento? */
fun RoutineBlock.isSuspendedOn(day: LocalDate, suspensions: Suspensions): Boolean {
    if (suspensions.isEmpty) return false
    if (suspensions.covers(day)) return true
    val times = timesOn(day) ?: return false
    return suspensions.overlaps(times.first, times.second)
}
