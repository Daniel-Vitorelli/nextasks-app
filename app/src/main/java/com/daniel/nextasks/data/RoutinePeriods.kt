package com.daniel.nextasks.data

import java.time.LocalDate

/**
 * A unidade em que a rotina é avaliada: **o dia** na rotina diária, **a semana**
 * (começando no domingo) na semanal.
 *
 * A confirmação do bloco é sempre gravada no **dia da ocorrência** ([RoutineOccurrences]),
 * mas o "dia 100%" da gamificação continua sendo por unidade — senão um bloco de
 * Seg/Qua/Sex daria três semanas completas por semana. Fica aqui, em `data/`, porque
 * a gamificação e o XP precisam da **mesma** resposta.
 */
fun Routine.periodStartOf(day: LocalDate): LocalDate =
    if (frequency == RoutineFrequency.Weekly) day.weekStartSunday() else day

/**
 * As ocorrências confirmáveis da unidade que começa em [unitStart].
 *
 * Cada item é `(bloco, dia)` — um bloco de Seg/Qua/Sex rende três itens na semana,
 * e todos precisam ser confirmados para a unidade valer. Bloco sem confirmação
 * ([EventConfirmation.None]) fica de fora: não há o que confirmar.
 */
fun Routine.occurrencesIn(unitStart: LocalDate): List<Pair<String, LocalDate>> {
    val weekly = frequency == RoutineFrequency.Weekly
    val days = if (weekly) List(7) { unitStart.plusDays(it.toLong()) } else listOf(unitStart)
    return blocks
        .filter { it.confirmation != EventConfirmation.None }
        .flatMap { block ->
            val blockDays = if (weekly) days.filter { RoutineBlockDays.happensOn(block, it) } else days
            blockDays.map { block.id to it }
        }
}

/**
 * As unidades (dias ou semanas) em que **todas** as ocorrências foram confirmadas.
 *
 * Recebe as conclusões já validadas pelo chamador (bloco com confirmação e valor que
 * conta), porque "nota 0" e "checklist desmarcado" não podem fechar a unidade.
 * Unidade sem ocorrência nenhuma nunca volta — dia vazio não é dia completo.
 */
fun Routine.completePeriodStarts(validCompletions: List<Completion>): List<LocalDate> {
    val ownBlocks = blocks.map { it.id }.toSet()
    val values = validCompletions
        .filter { it.blockId in ownBlocks }
        .associateBy { it.blockId to it.periodStart }

    return validCompletions
        .asSequence()
        .filter { it.blockId in ownBlocks }
        .mapNotNull { parseIsoDate(it.periodStart) }
        .map { periodStartOf(it) }
        .distinct()
        .filter { unit ->
            val expected = occurrencesIn(unit)
            expected.isNotEmpty() && expected.all { (blockId, day) ->
                values[blockId to day.toString()] != null
            }
        }
        .sorted()
        .toList()
}
