package com.daniel.nextasks.ui.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PtBr = Locale.forLanguageTag("pt-BR")
private val MonthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", PtBr)
private val DayHeaderFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)
private val NarrowWeekdayFormatter = DateTimeFormatter.ofPattern("EEEEE", PtBr)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PtBr)

/** Primeiro dia da semana: domingo, como no app web (date-fns com locale pt-BR). */
fun LocalDate.startOfWeek(): LocalDate = minusDays((dayOfWeek.value % 7).toLong())

fun LocalDate.monthYearLabel(): String =
    format(MonthYearFormatter).replaceFirstChar { it.titlecase(PtBr) }

fun LocalDate.dayHeaderLabel(): String = format(DayHeaderFormatter).uppercase(PtBr)

/** "Segunda-feira, 16 de novembro" — usado no cabeçalho da Home. */
fun LocalDate.fullDateLabel(): String =
    format(DayHeaderFormatter).replaceFirstChar { it.titlecase(PtBr) }

fun LocalDate.weekdayLetter(): String = format(NarrowWeekdayFormatter)

fun LocalTime.hourMinute(): String = format(TimeFormatter)

/** Título do cabeçalho do dia: "Hoje", "Amanhã" ou o dia por extenso. */
fun dayHeaderLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    else -> date.dayHeaderLabel()
}
