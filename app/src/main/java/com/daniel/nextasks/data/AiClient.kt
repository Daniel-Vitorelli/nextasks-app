package com.daniel.nextasks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

/** Uma mensagem no formato que a API de chat espera. */
data class ChatTurn(val role: String, val content: String)

/** Resultado de um pedido ao modelo, já com quem respondeu. */
sealed interface AiChatResult {
    data class Ok(val text: String, val provider: AiProvider) : AiChatResult

    data class Failure(val message: String) : AiChatResult
}

/**
 * Cliente HTTP do assistente — **a única parte do app que usa internet**.
 *
 * Fala a API OpenAI-compatível (`POST {baseUrl}/chat/completions`), que cobre OpenAI,
 * OpenRouter, Groq, DeepSeek, Gemini no modo compatível, Ollama e LM Studio. A fila de
 * fallback é a ordem da lista: se o primeiro falhar (rede, chave, limite), tenta o
 * seguinte e só desiste quando todos falham.
 */
class AiClient {

    suspend fun complete(
        providers: List<AiProvider>,
        turns: List<ChatTurn>,
        maxTokens: Int = 2048,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val queue = providers.usableProviders()
        if (queue.isEmpty()) {
            return@withContext AiChatResult.Failure(
                "Nenhum provedor de IA habilitado. Configure um em Provedores.",
            )
        }

        val errors = mutableListOf<String>()
        for (provider in queue) {
            val attempt = runCatching { request(provider, turns, maxTokens) }
            attempt.getOrNull()?.let { return@withContext AiChatResult.Ok(it, provider) }
            val reason = attempt.exceptionOrNull()?.message ?: "falhou"
            errors += "${provider.name}: $reason"
        }
        AiChatResult.Failure(
            "Não consegui falar com nenhum provedor.\n" + errors.joinToString("\n") { "• $it" },
        )
    }

    /**
     * Um pedido só. Erros viram exceção para o fallback trocar de provedor.
     *
     * Pede `response_format: json_object` — é o que mantém o modelo no protocolo. Nem
     * todo provedor aceita, então um erro que mencione o parâmetro repete sem ele; os
     * outros erros (chave, modelo, rede) sobem para a fila de fallback.
     */
    private fun request(provider: AiProvider, turns: List<ChatTurn>, maxTokens: Int): String {
        return try {
            post(provider, turns, maxTokens, jsonMode = true)
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            val semSuporte = message.contains("response_format", ignoreCase = true) ||
                message.contains("response format", ignoreCase = true)
            if (semSuporte) post(provider, turns, maxTokens, jsonMode = false) else throw error
        }
    }

    private fun post(
        provider: AiProvider,
        turns: List<ChatTurn>,
        maxTokens: Int,
        jsonMode: Boolean,
    ): String {
        val body = buildJsonObject {
            put("model", provider.model)
            put("temperature", 0.2)
            put("max_tokens", maxTokens)
            if (jsonMode) {
                putJsonObject("response_format") { put("type", "json_object") }
            }
            putJsonArray("messages") {
                turns.forEach { turn ->
                    addJsonObject {
                        put("role", turn.role)
                        put("content", turn.content)
                    }
                }
            }
        }

        val connection = (URL(endpoint(provider.baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (provider.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${provider.apiKey.trim()}")
            }
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code — ${text.take(300).ifBlank { "sem resposta" }}")
            }
            return contentOf(text)
        } finally {
            connection.disconnect()
        }
    }

    /** Endereço final: respeita URLs que já apontam para `/chat/completions`. */
    internal fun endpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "URL base vazia" }
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    /** Extrai o texto do primeiro `choice`, aceitando `content` como texto ou lista. */
    private fun contentOf(raw: String): String {
        val root = AppJson.parseToJsonElement(raw).jsonObject
        val message = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: throw IllegalStateException("resposta sem 'choices'")

        val content = message["content"]
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.mapNotNull { part ->
                (part as? JsonPrimitive)?.contentOrNull
                    ?: part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.joinToString("")
            else -> null
        }
        return text?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalStateException("resposta sem conteúdo")
    }
}
