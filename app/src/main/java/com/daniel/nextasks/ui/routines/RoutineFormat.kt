package com.daniel.nextasks.ui.routines

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrazilianFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

/** Converte a data ISO (yyyy-MM-dd) guardada no banco para dd/MM/yyyy. */
internal fun formatIsoDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(BrazilianFormat) }.getOrDefault(iso)

/** Letras dos dias da semana, domingo primeiro (índice 0). */
internal val WeekdayLetters = listOf("D", "S", "T", "Q", "Q", "S", "S")

/** Nomes dos dias da semana, domingo primeiro (índice 0). */
internal val WeekdayNames = listOf(
    "Domingo",
    "Segunda",
    "Terça",
    "Quarta",
    "Quinta",
    "Sexta",
    "Sábado",
)
