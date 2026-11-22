package com.daniel.nextasks.data

import java.time.LocalDate

/**
 * Quando uma rotina acontece — regra pura, sem Compose.
 *
 * Fica fora do motor de ocorrências para poder ser testada em JVM e para que
 * calendário, Home, gráfico e heatmap usem **a mesma** resposta. Antes cada tela
 * recortava o histórico do seu jeito (o gráfico cortava pelo `createdAt`, o
 * calendário não), e o calendário mostrava blocos em meses anteriores à criação
 * da rotina.
 */
fun Routine.createdOnDate(): LocalDate? =
    runCatching { epochMillisToDate(createdAt) }.getOrNull()

/**
 * A rotina cobre este dia?
 *
 * - inativa não gera nada;
 * - antes da criação não gera nada (não existe histórico para trás);
 * - duração "até" para de gerar depois da data final;
 * - `endDate` ilegível é ignorado em vez de esconder a rotina inteira.
 *
 * O dia da semana do bloco **não** entra aqui: isso é por bloco, não por rotina.
 */
fun Routine.coversDay(day: LocalDate): Boolean {
    if (!isActive) return false

    val created = createdOnDate()
    if (created != null && day.isBefore(created)) return false

    if (duration == RoutineDuration.Until) {
        val end = parseIsoDate(endDate) ?: return true
        if (day.isAfter(end)) return false
    }

    return true
}
