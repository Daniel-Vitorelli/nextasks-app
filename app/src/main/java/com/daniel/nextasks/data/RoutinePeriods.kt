package com.daniel.nextasks.data

import java.time.LocalDate

/**
 * A unidade em que a rotina é avaliada: **o dia** na rotina diária, **a semana**
 * (começando no domingo) na semanal.
 *
 * A confirmação do bloco é sempre gravada no **dia da ocorrência** (`RoutineOccurrences`),
 * mas o "dia 100%" da gamificação continua sendo por unidade — senão um bloco de
 * Seg/Qua/Sex daria três semanas completas por semana. Fica aqui, em `data/`, porque
 * a gamificação e o XP precisam da **mesma** resposta.
 */
fun Routine.periodStartOf(day: LocalDate): LocalDate =
    if (frequency == RoutineFrequency.Weekly) day.weekStartSunday() else day

/**
 * As ocorrências confirmáveis da unidade que começa em [unitStart], já descontando
 * o que um evento suspendeu.
 *
 * Cada item é `(bloco, dia)` — um bloco de Seg/Qua/Sex rende três itens na semana,
 * e todos precisam ser confirmados para a unidade valer. Bloco sem confirmação
 * ([EventConfirmation.None]) fica de fora: não há o que confirmar. Ocorrência
 * suspensa por imprevisto também fica de fora: ela não pode ser cumprida nem exigida.
 */
fun Routine.occurrencesIn(
    unitStart: LocalDate,
    suspensions: Suspensions = Suspensions.None,
): List<Pair<String, LocalDate>> {
    val weekly = frequency == RoutineFrequency.Weekly
    val days = if (weekly) List(7) { unitStart.plusDays(it.toLong()) } else listOf(unitStart)
    return blocks
        .filter { it.confirmation != EventConfirmation.None }
        .flatMap { block ->
            val blockDays = if (weekly) days.filter { RoutineBlockDays.happensOn(block, it) } else days
            blockDays
                .filterNot { block.isSuspendedOn(it, suspensions) }
                .map { block.id to it }
        }
}

/**
 * As unidades (dias ou semanas) em que **todas** as ocorrências foram confirmadas.
 *
 * Recebe as conclusões já validadas pelo chamador (bloco com confirmação e valor que
 * conta), porque "nota 0" e "checklist desmarcado" não podem fechar a unidade.
 * Unidade sem ocorrência nenhuma nunca volta — dia vazio não é dia completo.
 *
 * Com [suspensions], a unidade é julgada pelo que dava para fazer: se o imprevisto
 * levou uma das ocorrências, basta confirmar as outras. Se **tudo** foi suspenso, a
 * unidade só conta quando o usuário já tinha confirmado tudo antes (o imprevisto não
 * pode apagar o que foi feito); sem confirmação nenhuma não há prêmio por não fazer nada.
 */
fun Routine.completePeriodStarts(
    validCompletions: List<Completion>,
    suspensions: Suspensions = Suspensions.None,
): List<LocalDate> {
    val ownBlocks = blocks.map { it.id }.toSet()
    val values = validCompletions
        .filter { it.blockId in ownBlocks }
        .associateBy { it.blockId to it.periodStart }

    fun confirmed(occurrences: List<Pair<String, LocalDate>>): Boolean =
        occurrences.isNotEmpty() && occurrences.all { (blockId, day) ->
            values[blockId to day.toString()] != null
        }

    return validCompletions
        .asSequence()
        .filter { it.blockId in ownBlocks }
        .mapNotNull { parseIsoDate(it.periodStart) }
        .map { periodStartOf(it) }
        .distinct()
        .filter { unit ->
            val required = occurrencesIn(unit, suspensions)
            if (required.isNotEmpty()) {
                confirmed(required)
            } else {
                confirmed(occurrencesIn(unit))
            }
        }
        .sorted()
        .toList()
}

/**
 * Os blocos que a rotina prevê para este dia, ignorando suspensão.
 *
 * Diferente de [occurrencesIn], que trabalha na unidade (dia ou semana) e já tira o
 * que foi suspenso. Aqui a pergunta é "o que estava previsto", que é a base para
 * decidir se o dia foi suspenso por inteiro.
 */
fun Routine.blocksDueOn(day: LocalDate): List<RoutineBlock> = blocks.filter { block ->
    block.confirmation != EventConfirmation.None &&
        (frequency == RoutineFrequency.Daily || RoutineBlockDays.happensOn(block, day))
}

/**
 * O dia tinha rotina e **tudo** o que estava previsto foi suspenso.
 *
 * É o caso do imprevisto que toma o dia. A sequência pausa em vez de quebrar: o
 * usuário não deixou de cumprir nada, o dia é que não deu.
 */
fun Routine.dayIsFullySuspended(day: LocalDate, suspensions: Suspensions): Boolean {
    if (suspensions.isEmpty) return false
    val due = blocksDueOn(day)
    return due.isNotEmpty() && due.all { it.isSuspendedOn(day, suspensions) }
}

/**
 * Unidades que existiriam mas foram suspensas por inteiro.
 *
 * Serve para a sequência **pausar** em vez de quebrar: um dia de imprevisto não é um
 * dia perdido, então ele sai da contagem em vez de zerar a corrida. As unidades
 * candidatas saem das próprias suspensões, e não de varrer o histórico inteiro.
 */
fun Routine.suspendedPeriodStarts(suspensions: Suspensions): List<LocalDate> {
    if (suspensions.isEmpty) return emptyList()

    val candidates = suspensions.wholeDays + suspensions.windows.map { it.start.toLocalDate() }
    return candidates
        .asSequence()
        .map { periodStartOf(it) }
        .distinct()
        .filter { unit -> occurrencesIn(unit).isNotEmpty() && occurrencesIn(unit, suspensions).isEmpty() }
        .sorted()
        .toList()
}


/** Unidade em que a sequência da rotina anda: o dia na diária, a semana na semanal. */
enum class RoutineUnit { Day, Week }

/**
 * Sequência da rotina em **unidades completas** (`RoutinePeriods.completePeriodStarts`).
 *
 * A unidade suspensa por imprevisto **pausa** a corrida: sai do calendário da conta,
 * então não soma nem quebra. É a mesma sequência que a gamificação usa — antes a Home
 * contava dias corridos e uma rotina semanal (que só acontece em alguns dias) ficava
 * eternamente em 0 ou 1.
 */
data class RoutineStreak(
    val current: Int = 0,
    val longest: Int = 0,
    val unit: RoutineUnit = RoutineUnit.Day,
    /** Quantas unidades fecharam (100%) — o "dias 100%" da gamificação. */
    val completedUnits: Int = 0,
)

/**
 * Calcula a sequência da rotina sobre o histórico inteiro.
 *
 * Recebe as conclusões já validadas (bloco com confirmação e valor que conta) e as
 * suspensões; [today] só define onde a corrida atual termina — a unidade de hoje pode
 * estar incompleta, que a contagem começa na anterior.
 */
fun routineStreak(
    routine: Routine,
    validCompletions: List<Completion>,
    suspensions: Suspensions = Suspensions.None,
    today: LocalDate,
): RoutineStreak {
    val unit = if (routine.frequency == RoutineFrequency.Weekly) {
        RoutineUnit.Week
    } else {
        RoutineUnit.Day
    }
    val unitDays = if (unit == RoutineUnit.Week) 7L else 1L
    fun unitOf(day: LocalDate): Int = (day.toEpochDay() / unitDays).toInt()

    // Sem data de criação legível, a conta vale desde sempre: melhor um número
    // conservador do que esconder a sequência inteira.
    val createdUnit = routine.createdOnDate()?.let { unitOf(it) } ?: Int.MIN_VALUE

    val complete = routine.completePeriodStarts(validCompletions, suspensions)
        .map { unitOf(it) }
        .filter { it >= createdUnit }
        .distinct()
    val suspended = routine.suspendedPeriodStarts(suspensions)
        .map { unitOf(it) }
        .filter { it >= createdUnit }
        .distinct()

    // A corrida anda em unidades **ativas**: a suspensa sai do índice.
    fun activeIndex(unitIndex: Int): Int = unitIndex - suspended.count { it < unitIndex }
    val summary = streakSummary(
        complete.map { activeIndex(it) }.distinct(),
        activeIndex(unitOf(today)),
    )

    return RoutineStreak(
        current = summary.current,
        longest = summary.best,
        unit = unit,
        completedUnits = complete.size,
    )
}