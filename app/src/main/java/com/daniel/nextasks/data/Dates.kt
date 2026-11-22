package com.daniel.nextasks.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Conversões de data/hora tolerantes a dado ruim.
 *
 * O app guarda datas como texto ISO nos arquivos JSON. Um backup editado à mão,
 * uma restauração parcial ou um arquivo remendado pode deixar um valor inválido
 * no meio de uma lista — e um `LocalDate.parse` cru derrubaria a tela inteira.
 * Aqui o registro ruim vira `null` e a tela simplesmente o ignora, que é a mesma
 * política do `ReplaceFileCorruptionHandler` dos DataStores.
 */
fun parseIsoDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw) }.getOrNull()
}

fun parseIsoTime(raw: String?): LocalTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalTime.parse(raw) }.getOrNull()
}

/** Data local de um instante em epoch millis (campo `createdAt`). */
fun epochMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
