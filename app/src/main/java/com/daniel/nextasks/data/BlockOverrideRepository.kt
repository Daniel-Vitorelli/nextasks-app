package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow

internal val Context.overrideStore: DataStore<List<BlockOverride>> by dataStore(
    fileName = "block_overrides.json",
    serializer = JsonListSerializer(BlockOverride.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Repositório das exceções por dia.
 *
 * Cada linha é (rotina, bloco, dia). Gravar a mesma chave de novo substitui a
 * anterior, então editar duas vezes o mesmo dia não acumula lixo.
 */
class BlockOverrideRepository(context: Context) {
    private val store = context.applicationContext.overrideStore

    val overrides: Flow<List<BlockOverride>> = store.data

    /** Grava (ou substitui) a exceção do dia. */
    suspend fun put(override: BlockOverride) {
        store.updateData { current ->
            current.filterNot { it.key == override.key } + override
        }
    }

    /** Volta a ocorrência ao que a rotina define. */
    suspend fun restore(routineId: String, blockId: String, date: String) {
        store.updateData { current ->
            current.filterNot {
                it.routineId == routineId && it.blockId == blockId && it.date == date
            }
        }
    }

    /** Limpa as exceções de um bloco que deixou de existir na rotina. */
    suspend fun clearForBlock(blockId: String) {
        store.updateData { current -> current.filterNot { it.blockId == blockId } }
    }

    /** Limpa tudo de uma rotina excluída. */
    suspend fun clearForRoutine(routineId: String) {
        store.updateData { current -> current.filterNot { it.routineId == routineId } }
    }
}
