package com.daniel.nextasks.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Como o tema é escolhido: seguindo o sistema ou fixo. */
@Serializable
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * Cor de destaque do app. Os valores ficam em `ui/theme/Accents.kt` — aqui só a
 * escolha, para as preferências não dependerem de Compose.
 */
@Serializable
enum class AccentColor {
    Green,
    Blue,
    Purple,
    Pink,
    Orange,
    Amber,
    Cyan,
}

/**
 * Preferências do app.
 *
 * Todos os campos têm default, então um arquivo ausente ou antigo continua válido —
 * não é preciso migração para acrescentar uma preferência nova.
 */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Nome do usuário; vazio = não definido. A Home usa só o primeiro nome. */
    val userName: String = "",
    /** Como as tarefas são ordenadas na Home e na lista de Tarefas. */
    val taskSort: TaskSortSettings = TaskSortSettings(),
    /** Cor de destaque (o verde da marca é o padrão). */
    val accent: AccentColor = AccentColor.Green,
    /** Addons ligados pelo usuário. Vazio = só as seções fixas aparecem. */
    val enabledAddons: Set<Addon> = emptySet(),
) {
    /** Primeiro nome, como o web faz com `name.split(" ")[0]`. */
    val firstName: String get() = userName.trim().substringBefore(' ').trim()
}

/** Serializa um único objeto (não uma lista) em um arquivo do DataStore. */
internal class JsonValueSerializer<T>(
    private val elementSerializer: KSerializer<T>,
    private val fallback: T,
) : Serializer<T> {
    override val defaultValue: T = fallback

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            AppJson.decodeFromString(elementSerializer, bytes.decodeToString())
        } catch (error: Exception) {
            throw CorruptionException("Não foi possível ler as preferências", error)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(AppJson.encodeToString(elementSerializer, t).encodeToByteArray())
    }
}

private val Context.settingsStore: DataStore<AppSettings> by dataStore(
    fileName = "settings.json",
    serializer = JsonValueSerializer(AppSettings.serializer(), AppSettings()),
    corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
)

/** Preferências do app. Instancie uma vez e compartilhe: o DataStore não aceita duas. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data

    suspend fun setThemeMode(mode: ThemeMode) {
        store.updateData { it.copy(themeMode = mode) }
    }

    suspend fun setUserName(name: String) {
        store.updateData { it.copy(userName = name.trim()) }
    }

    suspend fun setTaskSort(sort: TaskSortSettings) {
        store.updateData { it.copy(taskSort = sort) }
    }

    suspend fun setAccent(accent: AccentColor) {
        store.updateData { it.copy(accent = accent) }
    }

    suspend fun setAddonEnabled(addon: Addon, enabled: Boolean) {
        store.updateData { current ->
            current.copy(
                enabledAddons = if (enabled) {
                    current.enabledAddons + addon
                } else {
                    current.enabledAddons - addon
                },
            )
        }
    }
}

/** JSON indentado, para o arquivo exportado ficar legível fora do app. */
internal val PrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
}
