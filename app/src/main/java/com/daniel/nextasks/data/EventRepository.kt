package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow

internal val Context.eventStore: DataStore<List<AgendaEvent>> by dataStore(
    fileName = "agenda_events.json",
    serializer = JsonListSerializer(AgendaEvent.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/** Repositório dos eventos (podem durar vários dias e suspender a rotina). */
class EventRepository(context: Context) {
    private val store = context.applicationContext.eventStore

    val events: Flow<List<AgendaEvent>> = store.data

    suspend fun save(event: AgendaEvent) {
        store.updateData { current ->
            if (current.any { it.id == event.id }) {
                current.map { if (it.id == event.id) event else it }
            } else {
                current + event
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }
}
