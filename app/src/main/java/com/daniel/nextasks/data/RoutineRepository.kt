package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

internal val Context.routineStore: DataStore<List<Routine>> by dataStore(
    fileName = "routines.json",
    serializer = JsonListSerializer(Routine.serializer()),
    // Arquivo corrompido vira lista vazia em vez de derrubar o app.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
)

/**
 * Repositório local de rotinas e dos blocos de horário dentro delas.
 *
 * Leitura reativa via [routines] (Flow) e escrita transacional via DataStore,
 * então o app funciona offline e a UI atualiza sozinha a cada mudança.
 */
class RoutineRepository(context: Context) {
    private val store = context.applicationContext.routineStore

    val routines: Flow<List<Routine>> = store.data.map { current -> current.map { it.normalized() } }

    suspend fun save(routine: Routine) {
        store.updateData { current ->
            val isNew = current.none { it.id == routine.id }
            // Primeira rotina já nasce ativa — senão o usuário cria, abre o calendário
            // e não vê nada. Da segunda em diante, fica inativa até ele ativar.
            val incoming0 = if (isNew && !routine.isActive && current.none { it.isActive }) {
                routine.copy(isActive = true)
            } else {
                routine
            }
            val incoming = incoming0.normalized()
            val updated = if (isNew) current + incoming else current.map {
                if (it.id == incoming.id) incoming else it
            }

            // Invariante: no máximo uma ativa. O "só uma" vale sobre a lista final.
            updated.map { it.normalized() }.let { list ->
                if (!incoming.isActive) {
                    list
                } else {
                    list.map { other ->
                        if (other.id != incoming.id && other.isActive) {
                            other.copy(isActive = false)
                        } else {
                            other
                        }
                    }
                }
            }
        }
    }

    suspend fun delete(id: String) {
        store.updateData { current -> current.filterNot { it.id == id } }
    }

    /**
     * Ativa/desativa uma rotina.
     *
     * Como no app web (`POST /api/routines/[id]/activate`), **só uma rotina fica ativa
     * por vez**: ativar uma desativa todas as outras na mesma gravação. Desativar a
     * rotina atual é permitido — dá para ficar sem nenhuma ativa.
     */
    suspend fun toggleActive(id: String) {
        store.updateData { current ->
            val target = current.firstOrNull { it.id == id } ?: return@updateData current
            val activating = !target.isActive

            current.map { routine ->
                when {
                    routine.id == id -> routine.copy(isActive = activating)
                    activating && routine.isActive -> routine.copy(isActive = false)
                    else -> routine
                }
            }
        }
    }

    /**
     * Garante a regra de "uma ativa por vez" em dados antigos, quando cada rotina
     * tinha um switch independente. Mantém a ativa mais recente e desliga as demais.
     * Idempotente: se já existe 0 ou 1 ativa, não faz nada.
     */
    suspend fun normalizeActive() {
        store.updateData { current ->
            val active = current.filter { it.isActive }
            if (active.size <= 1) {
                current
            } else {
                val keep = active.maxByOrNull { it.createdAt }?.id
                current.map { routine ->
                    if (routine.isActive && routine.id != keep) {
                        routine.copy(isActive = false)
                    } else {
                        routine
                    }
                }
            }
        }
    }

    suspend fun duplicate(id: String) {
        store.updateData { current ->
            val original = current.firstOrNull { it.id == id }
            if (original == null) {
                current
            } else {
                current + original.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${original.name} (cópia)",
                    createdAt = System.currentTimeMillis(),
                    // A cópia nasce inativa (como em `/api/routines/[id]/duplicate`),
                    // senão duplicar a rotina ativa criaria uma segunda ativa.
                    isActive = false,
                    blocks = original.blocks.map { it.copy(id = UUID.randomUUID().toString()) },
                )
            }
        }
    }

    suspend fun saveBlock(routineId: String, block: RoutineBlock) {
        store.updateData { current ->
            current.map { routine ->
                if (routine.id != routineId) {
                    routine
                } else {
                    val exists = routine.blocks.any { it.id == block.id }
                    // Um bloco, um horário: sai do mesmo jeito em todo dia que pega.
                    val incoming = block.syncedDays()
                    val blocks = if (exists) {
                        routine.blocks.map { if (it.id == block.id) incoming else it }
                    } else {
                        routine.blocks + incoming
                    }
                    routine.copy(blocks = blocks.sortedWith(compareBy { it.startTime }))
                }
            }
        }
    }

    suspend fun deleteBlock(routineId: String, blockId: String) {
        store.updateData { current ->
            current.map { routine ->
                if (routine.id != routineId) {
                    routine
                } else {
                    routine.copy(blocks = routine.blocks.filterNot { it.id == blockId })
                }
            }
        }
    }

    /**
     * Cópia de um bloco **dentro da mesma rotina**, com horário, dias e confirmação.
     *
     * O histórico do bloco original não vem junto: a cópia nasce sem confirmações.
     * Útil para "academia" de manhã e de noite sem redigitar tudo.
     */
    suspend fun duplicateBlock(routineId: String, blockId: String) {
        store.updateData { current ->
            current.map { routine ->
                val original = routine.blocks.firstOrNull { it.id == blockId }
                if (routine.id != routineId || original == null) {
                    routine
                } else {
                    val copy = original.copy(
                        id = UUID.randomUUID().toString(),
                        title = "${original.title} (cópia)",
                    )
                    routine.copy(
                        blocks = (routine.blocks + copy).sortedWith(compareBy { it.startTime }),
                    )
                }
            }
        }
    }
}

/**
 * Deixa o bloco com os dois campos de dia coerentes.
 *
 * `weekdays` é a verdade; `weekday` passa a ser o primeiro dia dela, para que uma
 * versão anterior do app continue mostrando o bloco em algum dia certo. Bloco antigo
 * (sem `weekdays`) ganha a lista a partir do `weekday` que ele já tinha.
 */
internal fun RoutineBlock.syncedDays(): RoutineBlock {
    val days = scheduledWeekdays
    return if (weekdays == days && weekday == days.first()) {
        this
    } else {
        copy(weekdays = days, weekday = days.first())
    }
}

/** Aplica [syncedDays] a todos os blocos — usado na leitura e na escrita. */
internal fun Routine.normalized(): Routine {
    if (blocks.isEmpty()) return this
    val synced = blocks.map { it.syncedDays() }
    return if (synced == blocks) this else copy(blocks = synced)
}
