package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Resultado de uma restauração. */
sealed interface RestoreResult {
    /** Nada foi reconhecido como backup do NexTasks. */
    data object Invalid : RestoreResult

    /** Quantas tabelas foram restauradas. */
    data class Restored(val tables: Int, val records: Int) : RestoreResult
}

/** O que um arquivo de backup contém, para confirmar antes de aplicar. */
data class BackupSummary(
    val exportedAt: String?,
    val counts: Map<String, Int>,
) {
    val records: Int get() = counts.values.sum()
}

/**
 * Backup e restauração dos dados locais.
 *
 * Lê e escreve **através dos DataStore**, e não por fora: escrever no arquivo pelas costas
 * deixaria o cache em memória desatualizado e o app mostraria dados velhos até reiniciar.
 */
class BackupService(context: Context) {
    private val app = context.applicationContext

    /** Uma tabela do app: arquivo, leitura e escrita. */
    private class Table(
        val fileName: String,
        val read: suspend () -> String,
        val write: suspend (String) -> Unit,
        val clear: suspend () -> Unit,
    )

    private fun <T> table(
        fileName: String,
        store: DataStore<T>,
        serializer: KSerializer<T>,
        empty: T,
    ): Table = Table(
        fileName = fileName,
        read = { AppJson.encodeToString(serializer, store.data.first()) },
        write = { json -> store.updateData { AppJson.decodeFromString(serializer, json) } },
        clear = { store.updateData { empty } },
    )

    private fun tables(): List<Table> = listOf(
        table("routines.json", app.routineStore, ListSerializer(Routine.serializer()), emptyList()),
        table(
            "completions.json",
            app.completionStore,
            ListSerializer(Completion.serializer()),
            emptyList(),
        ),
        table("tasks.json", app.taskStore, ListSerializer(Task.serializer()), emptyList()),
        table(
            "connections.json",
            app.connectionStore,
            ListSerializer(Connection.serializer()),
            emptyList(),
        ),
        table("subtasks.json", app.subtaskStore, ListSerializer(Subtask.serializer()), emptyList()),
        table(
            "habits.json",
            app.habitStore,
            ListSerializer(Habit.serializer()),
            emptyList(),
        ),
        table(
            "habit_completions.json",
            app.habitCompletionStore,
            ListSerializer(HabitCompletion.serializer()),
            emptyList(),
        ),
        table(
            "agenda_events.json",
            app.eventStore,
            ListSerializer(AgendaEvent.serializer()),
            emptyList(),
        ),
        table(
            "extra_blocks.json",
            app.extraBlockStore,
            ListSerializer(ExtraBlock.serializer()),
            emptyList(),
        ),
        table(
            "block_overrides.json",
            app.overrideStore,
            ListSerializer(BlockOverride.serializer()),
            emptyList(),
        ),
        // Gamificação: sem estas duas, restaurar um backup perderia nível, XP e
        // conquistas — e o XP não dá para recalcular, porque o valor depende de
        // quando cada ação aconteceu.
        table("xp_events.json", app.xpStore, ListSerializer(XpEvent.serializer()), emptyList()),
        table(
            "achievements.json",
            app.achievementStore,
            ListSerializer(AchievementUnlock.serializer()),
            emptyList(),
        ),
        // Assistente de IA: conversas e memória. Os provedores ficam de fora de
        // propósito — a chave da API não pode viajar num arquivo de backup.
        table(
            "ai_sessions.json",
            app.aiSessionStore,
            ListSerializer(AiSession.serializer()),
            emptyList(),
        ),
        table(
            "ai_messages.json",
            app.aiMessageStore,
            ListSerializer(AiMessage.serializer()),
            emptyList(),
        ),
        table(
            "ai_memory.json",
            app.aiMemoryStore,
            ListSerializer(AiMemoryEntry.serializer()),
            emptyList(),
        ),
    )

    /** Quantas tabelas o backup cobre — para avisar quando a restauração é parcial. */
    val tableCount: Int get() = tables().size

    /** Gera o backup completo como JSON indentado. */
    suspend fun export(): String {
        val files = buildJsonObject {
            tables().forEach { table ->
                // Uma tabela ilegível não pode derrubar o backup inteiro: sai vazia
                // e as outras são salvas normalmente.
                val raw = runCatching { table.read() }.getOrDefault("[]")
                put(table.fileName, runCatching { AppJson.parseToJsonElement(raw) }
                    .getOrDefault(JsonArray(emptyList())))
            }
        }
        val root = buildJsonObject {
            put("app", "NexTasks")
            put("version", BACKUP_VERSION)
            put("exportedAt", LocalDateTime.now().format(ISO_SECONDS))
            put("files", files)
        }
        return PrettyJson.encodeToString(JsonObject.serializer(), root)
    }

    /** Lê o cabeçalho do backup sem aplicar nada. */
    fun inspect(json: String): BackupSummary? {
        val root = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val files = root["files"]?.jsonObject ?: return null
        val counts = files.mapValues { (_, element) ->
            (element as? JsonArray)?.size ?: 0
        }
        val exportedAt = root["exportedAt"]?.jsonPrimitive?.content
        return BackupSummary(exportedAt = exportedAt, counts = counts)
    }

    /**
     * Substitui os dados pelas tabelas do backup. Tabelas ausentes no arquivo ficam
     * como estão; tabelas desconhecidas são ignoradas (backup de versão mais nova).
     */
    suspend fun restore(json: String): RestoreResult {
        val root = runCatching { AppJson.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return RestoreResult.Invalid
        val files = root["files"]?.jsonObject ?: return RestoreResult.Invalid

        val known = tables().associateBy { it.fileName }
        var restored = 0
        var records = 0
        for ((name, element) in files) {
            val table = known[name] ?: continue
            val array = element as? JsonArray ?: continue
            val ok = runCatching { table.write(element.toString()) }.isSuccess
            if (ok) {
                restored += 1
                records += array.size
            }
        }
        return if (restored == 0) RestoreResult.Invalid else RestoreResult.Restored(restored, records)
    }

    /** Apaga tudo: rotinas, tarefas, hábitos, eventos e histórico. */
    suspend fun clearAll() {
        tables().forEach { it.clear() }
    }

    private companion object {
        const val BACKUP_VERSION = 1
        val ISO_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
