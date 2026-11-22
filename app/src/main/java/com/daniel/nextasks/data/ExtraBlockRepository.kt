package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow

internal val Context.extraBlockStore: DataStore<List<ExtraBlock>> by dataStore(
    fileName = "extra_blocks.json",
    serializer = JsonListSerializer(ExtraBlock.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/** Repositório dos blocos avulsos (um dia só, fora das rotinas). */
class ExtraBlockRepository(context: Context) {
    private val store = context.applicationContext.extraBlockStore

    val blocks: Flow<List<ExtraBlock>> = store.data

    suspend fun save(block: ExtraBlock) {
        store.updateData { current ->
            if (current.any { it.id == block.id }) {
                current.map { if (it.id == block.id) block else it }
            } else {
                current + block
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }
}
