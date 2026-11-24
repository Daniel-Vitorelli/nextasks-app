package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compatibilidade da chave de conclusão: a v3.48 gravava a rotina semanal no domingo
 * da semana; a v3.49 grava no dia da ocorrência.
 *
 * O que importa aqui: migrar o que é antigo, **não** tocar no que já está certo, e
 * poder rodar a migração quantas vezes for preciso (ela roda a cada abertura do app).
 */
class CompletionKeysTest {

    private fun block(
        id: String = "b1",
        weekday: Int = 1,
        weekdays: List<Int> = emptyList(),
    ) = RoutineBlock(id = id, title = "academia", weekday = weekday, weekdays = weekdays)

    private fun routine(
        blocks: List<RoutineBlock>,
        frequency: RoutineFrequency = RoutineFrequency.Weekly,
        id: String = "r1",
    ) = Routine(id = id, name = "rotina", frequency = frequency, isActive = true, blocks = blocks)

    private fun completion(blockId: String = "b1", period: String) =
        Completion(blockId = blockId, periodStart = period, value = "true")

    @Test
    fun `conclusao semanal antiga vai para o dia do bloco`() {
        // 2026-11-22 é domingo; o bloco é de quarta (3) → 25/11.
        val migrated = migratedCompletions(
            completions = listOf(completion(period = "2026-11-22")),
            routines = listOf(routine(listOf(block(weekday = 3)))),
            extras = emptyList(),
        )

        assertEquals("2026-11-25", migrated.single().periodStart)
    }

    @Test
    fun `bloco multi-dia antigo vai para o primeiro dia`() {
        val migrated = migratedCompletions(
            completions = listOf(completion(period = "2026-11-22")),
            routines = listOf(routine(listOf(block(weekday = 1, weekdays = listOf(1, 3, 5))))),
            extras = emptyList(),
        )

        assertEquals("2026-11-23", migrated.single().periodStart)
    }

    @Test
    fun `migracao e idempotente`() {
        val routines = listOf(routine(listOf(block(weekday = 3))))
        val once = migratedCompletions(listOf(completion(period = "2026-11-22")), routines, emptyList())
        val twice = migratedCompletions(once, routines, emptyList())

        assertEquals(once, twice)
    }

    @Test
    fun `conclusao que ja esta no dia da ocorrencia nao muda`() {
        // Quarta 25/11 já é o dia do bloco: nada a fazer.
        val original = listOf(completion(period = "2026-11-25"))
        val migrated = migratedCompletions(
            original,
            listOf(routine(listOf(block(weekday = 3)))),
            emptyList(),
        )

        assertEquals(original, migrated)
    }

    @Test
    fun `bloco que acontece no domingo fica como esta`() {
        // Domingo é dia de ocorrência e início de semana ao mesmo tempo: a chave é a
        // mesma nas duas versões e a migração não pode mexer.
        val original = listOf(completion(period = "2026-11-22"))
        val migrated = migratedCompletions(
            original,
            listOf(routine(listOf(block(weekday = 0)))),
            emptyList(),
        )

        assertEquals(original, migrated)
    }

    @Test
    fun `rotina diaria nunca e tocada`() {
        // Domingo em rotina diária é só mais um dia; migrar seria trocar de dia.
        val original = listOf(completion(period = "2026-11-22"))
        val migrated = migratedCompletions(
            original,
            listOf(routine(listOf(block(weekday = 1)), frequency = RoutineFrequency.Daily)),
            emptyList(),
        )

        assertEquals(original, migrated)
    }

    @Test
    fun `bloco avulso de rotina semanal vai para o dia dele`() {
        val extra = ExtraBlock(id = "e1", date = "2026-11-25", routineId = "r1", title = "avulso")
        val migrated = migratedCompletions(
            completions = listOf(completion(blockId = "e1", period = "2026-11-22")),
            routines = listOf(routine(listOf(block(weekday = 3)))),
            extras = listOf(extra),
        )

        assertEquals("2026-11-25", migrated.single().periodStart)
    }

    @Test
    fun `bloco desconhecido e periodo ilegivel ficam como estao`() {
        val completions = listOf(
            completion(blockId = "sumiu", period = "2026-11-22"),
            completion(period = "ontem"),
        )

        val migrated = migratedCompletions(
            completions,
            listOf(routine(listOf(block(weekday = 3)))),
            emptyList(),
        )

        assertEquals(completions, migrated)
    }

    @Test
    fun `migrar duas chaves para o mesmo dia nao duplica a linha`() {
        val migrated = migratedCompletions(
            completions = listOf(
                completion(period = "2026-11-22"),
                completion(period = "2026-11-25"),
            ),
            routines = listOf(routine(listOf(block(weekday = 3)))),
            extras = emptyList(),
        )

        assertEquals(listOf("2026-11-25"), migrated.map { it.periodStart })
    }
}
