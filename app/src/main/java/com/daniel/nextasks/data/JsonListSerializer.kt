package com.daniel.nextasks.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Serializa uma lista de objetos como JSON dentro de um arquivo do DataStore.
 * Usado tanto pelas rotinas quanto pelas conclusões.
 */
class JsonListSerializer<T>(
    private val elementSerializer: KSerializer<T>,
) : Serializer<List<T>> {
    override val defaultValue: List<T> = emptyList()

    override suspend fun readFrom(input: InputStream): List<T> {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            AppJson.decodeFromString(ListSerializer(elementSerializer), bytes.decodeToString())
        } catch (error: Exception) {
            throw CorruptionException("Não foi possível ler os dados salvos", error)
        }
    }

    override suspend fun writeTo(t: List<T>, output: OutputStream) {
        val encoded = AppJson.encodeToString(ListSerializer(elementSerializer), t)
        output.write(encoded.encodeToByteArray())
    }
}
