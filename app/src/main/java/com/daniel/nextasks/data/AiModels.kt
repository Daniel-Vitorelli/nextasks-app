package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Provedor de IA — sempre a mesma API (OpenAI-compatível) para o app falar uma
 * língua só: OpenAI, OpenRouter, Groq, DeepSeek, Gemini (endpoint compatível),
 * Ollama e LM Studio entram todos por aqui.
 *
 * A **ordem da lista é a fila de fallback**: o primeiro provedor habilitado que
 * responder, responde.
 */
@Serializable
data class AiProvider(
    val id: String = UUID.randomUUID().toString(),
    /** Nome que o usuário vê ("OpenAI", "Ollama do PC"). */
    val name: String = "OpenAI",
    /** Raiz da API. O app acrescenta `/chat/completions` (ou usa como está, se já vier). */
    val baseUrl: String = "https://api.openai.com/v1",
    /** Chave enviada como `Authorization: Bearer`. Vazio serve para servidor local. */
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    /** Provedor desligado não entra na fila. */
    val enabled: Boolean = true,
)

/** Quem escreveu a mensagem. */
@Serializable
enum class AiRole {
    User,
    Assistant,

    /** Resultado de uma ação proposta (confirmação ou recusa). */
    Action,
}

/** Situação de uma ação proposta pela IA. */
@Serializable
enum class AiActionStatus {
    /** Esperando o usuário confirmar. Nada foi executado. */
    Pending,
    Confirmed,
    Rejected,

    /** Confirmada, mas a execução falhou (argumento inválido, id inexistente…). */
    Failed,
}

/**
 * Ação proposta pela IA, guardada junto da mensagem.
 *
 * Os argumentos ficam como **JSON cru**: o modelo devolve JSON livre e só na hora de
 * executar é que vira objeto. Assim o histórico não depende de tipos que mudam.
 */
@Serializable
data class AiAction(
    val id: String = UUID.randomUUID().toString(),
    val tool: String,
    val args: String = "{}",
    val status: AiActionStatus = AiActionStatus.Pending,
    /** Resumo do que aconteceu na execução (ou do erro). */
    val result: String? = null,
)

/** Uma mensagem da conversa. */
@Serializable
data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: AiRole,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Ações propostas (só faz sentido em [AiRole.Assistant]). */
    val actions: List<AiAction> = emptyList(),
)

/** Uma conversa. O título nasce da primeira mensagem do usuário. */
@Serializable
data class AiSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Título para exibir: o da conversa ou um rótulo neutro. */
    val displayTitle: String get() = title.ifBlank { "Nova conversa" }
}

/** Um fato/preferência que a IA deve lembrar em todas as conversas. */
@Serializable
data class AiMemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
