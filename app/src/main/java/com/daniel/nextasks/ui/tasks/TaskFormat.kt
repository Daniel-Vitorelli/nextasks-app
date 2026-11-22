package com.daniel.nextasks.ui.tasks

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.daniel.nextasks.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PtBr = Locale.forLanguageTag("pt-BR")
private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
private val ShortDateFormatter = DateTimeFormatter.ofPattern("dd/MM", PtBr)

@StringRes
internal fun priorityLabel(priority: Int): Int = when (priority) {
    1 -> R.string.priority_1
    2 -> R.string.priority_2
    3 -> R.string.priority_3
    4 -> R.string.priority_4
    5 -> R.string.priority_5
    else -> R.string.priority_6
}

/**
 * Cor da prioridade — mesmos tons do `priorityBadgeStyles` do app web
 * (cinza, azul, verde, amarelo, laranja e vermelho), em valores que funcionam
 * tanto no tema claro quanto no escuro.
 */
internal fun priorityColor(priority: Int): Color = when (priority) {
    1 -> Color(0xFF9CA3AF)
    2 -> Color(0xFF3B82F6)
    3 -> Color(0xFF22C55E)
    4 -> Color(0xFFEAB308)
    5 -> Color(0xFFF97316)
    else -> Color(0xFFEF4444)
}

/** "2026-11-20" -> "20/11/2026". Devolve null se a data for inválida. */
internal fun formatDueDate(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it).format(DateFormatter) }.getOrNull()
}

/** Versão curta para os badges: "2026-11-20" -> "20/11". */
internal fun formatShortDate(iso: String?): String? = iso?.let {
    runCatching { LocalDate.parse(it).format(ShortDateFormatter) }.getOrNull()
}
