package com.daniel.nextasks.data

import java.time.LocalDate

/**
 * Compatibilidade da chave de conclusão entre a v3.48 e a v3.49.
 *
 * Até a v3.48 a rotina semanal gravava a conclusão no **início da semana** (domingo),
 * porque o bloco só acontecia em um dia. Agora a chave é o **dia da ocorrência**, e o
 * mesmo bloco pode acontecer em vários dias da semana. Sem migrar, as confirmações
 * antigas ficariam órfãs: sumiriam do calendário, da Home e do XP.
 *
 * A regra é conservadora — só mexe no que **com certeza** é chave antiga:
 * - rotina diária nunca mudou de chave;
 * - rotina semanal cujo bloco acontece no domingo já está na chave nova (o domingo é
 *   a ocorrência e o início da semana ao mesmo tempo);
 * - qualquer outro dia que não seja domingo também não é chave antiga.
 *
 * Isso torna a migração idempotente: rodar de novo não muda nada.
 */
fun migratedCompletions(
    completions: List<Completion>,
    routines: List<Routine>,
    extras: List<ExtraBlock>,
): List<Completion> {
    if (completions.isEmpty()) return completions

    val blockById = routines.flatMap { it.blocks }.associateBy { it.id }
    val routineByBlockId = buildMap {
        routines.forEach { routine -> routine.blocks.forEach { put(it.id, routine) } }
    }
    val routineById = routines.associateBy { it.id }
    val extraById = extras.associateBy { it.id }

    val migrated = completions.map { completion ->
        val period = parseIsoDate(completion.periodStart) ?: return@map completion
        val target = migratedDay(
            blockId = completion.blockId,
            period = period,
            blockById = blockById,
            routineByBlockId = routineByBlockId,
            routineById = routineById,
            extraById = extraById,
        ) ?: return@map completion
        completion.copy(periodStart = target.toString())
    }

    // Migrar duas chaves antigas para o mesmo dia não pode duplicar a linha.
    return migrated.distinctBy { it.blockId to it.periodStart }
}

/** Dia da ocorrência que substitui [period], ou nulo quando não há o que migrar. */
private fun migratedDay(
    blockId: String,
    period: LocalDate,
    blockById: Map<String, RoutineBlock>,
    routineByBlockId: Map<String, Routine>,
    routineById: Map<String, Routine>,
    extraById: Map<String, ExtraBlock>,
): LocalDate? {
    val routine = routineByBlockId[blockId]
    if (routine != null) {
        if (routine.frequency != RoutineFrequency.Weekly) return null

        val block = blockById[blockId] ?: return null
        val days = block.scheduledWeekdays
        // Já é dia de ocorrência: ou é a chave nova, ou o bloco acontece no domingo.
        if (RoutineBlockDays.indexOf(period) in days) return null
        if (RoutineBlockDays.indexOf(period) != 0) return null
        return period.plusDays(days.first().toLong())
    }

    // Bloco avulso: a chave antiga era o domingo da semana do próprio dia dele.
    val extra = extraById[blockId] ?: return null
    val extraDay = parseIsoDate(extra.date) ?: return null
    if (period == extraDay) return null
    val linked = extra.routineId?.let { routineById[it] }
    if (linked?.frequency != RoutineFrequency.Weekly) return null
    if (period != extraDay.weekStartSunday()) return null
    return extraDay
}
