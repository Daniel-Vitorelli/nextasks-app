package com.daniel.nextasks.ui.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.daniel.nextasks.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BrazilianFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

/** Converte a data ISO (yyyy-MM-dd) guardada no banco para dd/MM/yyyy. */
internal fun formatIsoDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(BrazilianFormat) }.getOrDefault(iso)

/** Recurso do nome curto do dia (0 = domingo … 6 = sábado). */
internal fun weekdayShortRes(day: Int): Int = when (day) {
    0 -> R.string.weekday_short_0
    1 -> R.string.weekday_short_1
    2 -> R.string.weekday_short_2
    3 -> R.string.weekday_short_3
    4 -> R.string.weekday_short_4
    5 -> R.string.weekday_short_5
    else -> R.string.weekday_short_6
}

/**
 * Os dias do bloco em texto curto: "Seg", "Seg, Qua, Sex" ou "Todos os dias".
 *
 * Usa os mesmos recursos dos chips do formulário — uma fonte só para os nomes dos
 * dias, sem lista paralela em Kotlin.
 */
@Composable
internal fun formatBlockDays(days: List<Int>): String = when {
    days.isEmpty() || days.size == 7 -> stringResource(R.string.block_days_all)
    // `map` é inline, então dá para resolver o recurso dentro do lambda.
    else -> days.map { stringResource(weekdayShortRes(it)) }.joinToString(", ")
}
