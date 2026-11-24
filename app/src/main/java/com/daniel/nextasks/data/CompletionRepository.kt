package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

internal val Context.completionStore: DataStore<List<Completion>> by dataStore(
    fileName = "completions.json",
    serializer = JsonListSerializer(Completion.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/** Repositório local das conclusões dos blocos de horário. */
class CompletionRepository(context: Context) {
    private val store = context.applicationContext.completionStore

    val completions: Flow<List<Completion>> = store.data

    /** Grava (ou remove, com [value] nulo) a conclusão do bloco no período. */
    suspend fun set(blockId: String, periodStart: LocalDate, value: String?) {
        store.updateData { current ->
            val key = periodStart.toString()
            val remaining = current.filterNot {
                it.blockId == blockId && it.periodStart == key
            }
            if (value == null) {
                remaining
            } else {
                remaining + Completion(blockId = blockId, periodStart = key, value = value)
            }
        }
    }

    /** Remove as conclusões de um bloco excluído. */
    suspend fun clearForBlock(blockId: String) {
        store.updateData { current -> current.filterNot { it.blockId == blockId } }
    }

    /**
     * Traduz as conclusões antigas de rotina semanal (chave = domingo da semana) para
     * o **dia da ocorrência**, que passou a ser a chave na v3.49.
     *
     * Roda ao abrir o app e depois de restaurar um backup. É idempotente: sem nada
     * para migrar, o arquivo não é reescrito. Ver [migratedCompletions].
     */
    suspend fun migrateWeeklyKeys(routines: List<Routine>, extras: List<ExtraBlock>) {
        store.updateData { current ->
            val migrated = migratedCompletions(current, routines, extras)
            if (migrated == current) current else migrated
        }
    }
}
