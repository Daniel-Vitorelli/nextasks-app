package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.aiProviderStore: DataStore<List<AiProvider>> by dataStore(
    fileName = "ai_providers.json",
    serializer = JsonListSerializer(AiProvider.serializer()),
    // Provedor ilegível vira lista vazia em vez de derrubar a página de IA.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

internal val Context.aiSessionStore: DataStore<List<AiSession>> by dataStore(
    fileName = "ai_sessions.json",
    serializer = JsonListSerializer(AiSession.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

internal val Context.aiMessageStore: DataStore<List<AiMessage>> by dataStore(
    fileName = "ai_messages.json",
    serializer = JsonListSerializer(AiMessage.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

internal val Context.aiMemoryStore: DataStore<List<AiMemoryEntry>> by dataStore(
    fileName = "ai_memory.json",
    serializer = JsonListSerializer(AiMemoryEntry.serializer()),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Provedores na ordem da fila de fallback.
 *
 * A lista gravada **é** a fila: mover para cima é aumentar a prioridade.
 */
class AiProviderRepository(context: Context) {
    private val store = context.applicationContext.aiProviderStore

    val providers: Flow<List<AiProvider>> = store.data

    suspend fun save(provider: AiProvider) {
        store.updateData { current ->
            if (current.any { it.id == provider.id }) {
                current.map { if (it.id == provider.id) provider else it }
            } else {
                current + provider
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    /** Sobe um degrau na fila (aparece antes, é tentado antes). */
    suspend fun moveUp(id: String) = move(id, -1)

    /** Desce um degrau na fila. */
    suspend fun moveDown(id: String) = move(id, 1)

    private suspend fun move(id: String, delta: Int) {
        store.updateData { current ->
            val index = current.indexOfFirst { it.id == id }
            val target = index + delta
            if (index < 0 || target !in current.indices) {
                current
            } else {
                current.toMutableList().apply {
                    val item = removeAt(index)
                    add(target, item)
                }
            }
        }
    }
}

/** Conversas do assistente, da mais recente para a mais antiga. */
class AiSessionRepository(context: Context) {
    private val store = context.applicationContext.aiSessionStore

    val sessions: Flow<List<AiSession>> =
        store.data.map { list -> list.sortedByDescending { it.updatedAt } }

    suspend fun create(title: String = ""): AiSession {
        val session = AiSession(title = title)
        store.updateData { current -> current + session }
        return session
    }

    suspend fun rename(id: String, title: String) {
        store.updateData { current ->
            current.map { if (it.id == id) it.copy(title = title.trim()) else it }
        }
    }

    /** Marca atividade: a conversa sobe na lista. */
    suspend fun touch(id: String, now: Long = System.currentTimeMillis()) {
        store.updateData { current ->
            current.map { if (it.id == id) it.copy(updatedAt = now) else it }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    suspend fun latest(): AiSession? = sessions.first().firstOrNull()
}

/** Mensagens de todas as conversas. A tela filtra pela sessão aberta. */
class AiMessageRepository(context: Context) {
    private val store = context.applicationContext.aiMessageStore

    val messages: Flow<List<AiMessage>> = store.data

    val hasAny: Flow<Boolean> = store.data.map { it.isNotEmpty() }

    suspend fun append(message: AiMessage) {
        store.updateData { current -> current + message }
    }

    /** Grava o desfecho de uma ação (confirmada, recusada, falhou). */
    suspend fun updateAction(sessionId: String, actionId: String, update: (AiAction) -> AiAction) {
        store.updateData { current ->
            current.map { message ->
                if (message.sessionId != sessionId || message.actions.none { it.id == actionId }) {
                    message
                } else {
                    message.copy(
                        actions = message.actions.map { action ->
                            if (action.id == actionId) update(action) else action
                        },
                    )
                }
            }
        }
    }

    suspend fun deleteForSession(sessionId: String) {
        store.updateData { current -> current.filterNot { it.sessionId == sessionId } }
    }

    suspend fun clearAll() {
        store.updateData { emptyList() }
    }
}

/**
 * Memória do assistente: fatos e preferências que valem para **todas** as conversas.
 *
 * É editável à mão (tela de memória) e pela própria IA (ferramentas `memory.*`), e
 * entra no começo de todo pedido.
 */
class AiMemoryRepository(context: Context) {
    private val store = context.applicationContext.aiMemoryStore

    val memories: Flow<List<AiMemoryEntry>> = store.data

    suspend fun save(entry: AiMemoryEntry) {
        store.updateData { current ->
            if (current.any { it.id == entry.id }) {
                current.map { if (it.id == entry.id) entry else it }
            } else {
                current + entry
            }
        }
    }

    suspend fun add(text: String): AiMemoryEntry {
        val entry = AiMemoryEntry(text = text.trim())
        store.updateData { current -> current + entry }
        return entry
    }

    suspend fun update(id: String, text: String) {
        store.updateData { current ->
            current.map {
                if (it.id == id) it.copy(text = text.trim(), updatedAt = System.currentTimeMillis()) else it
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    suspend fun clear() {
        store.updateData { emptyList() }
    }

    suspend fun all(): List<AiMemoryEntry> = memories.first()
}

/**
 * Provedores que podem ser tentados, na ordem da fila.
 *
 * Função pura para dar para testar sem rede: tira os desligados e os que não têm
 * modelo — e mantém a ordem, que é o fallback.
 */
fun List<AiProvider>.usableProviders(): List<AiProvider> =
    filter { it.enabled && it.model.isNotBlank() }
