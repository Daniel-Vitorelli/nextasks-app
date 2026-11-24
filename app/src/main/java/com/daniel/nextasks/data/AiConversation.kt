package com.daniel.nextasks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** O que aconteceu num envio: ok ou o motivo da falha (para a tela mostrar). */
sealed interface AiSendResult {
    data object Ok : AiSendResult

    data class Error(val message: String) : AiSendResult
}

/**
 * Motor da conversa: monta o pedido, chama o modelo e guarda o que ele propôs.
 *
 * O laço tem uma parada dura de propósito: o modelo **não executa nada**. Ele devolve
 * ações, que ficam pendentes até o usuário confirmar uma a uma ([confirm]/[reject]).
 * Toda ação confirmada passa pelo [AiToolbox] — os mesmos repositórios e motores das
 * telas.
 */
class AiConversation(
    private val providerRepository: AiProviderRepository,
    private val sessionRepository: AiSessionRepository,
    private val messageRepository: AiMessageRepository,
    private val memoryRepository: AiMemoryRepository,
    private val toolbox: AiToolbox,
    private val client: AiClient,
    private val routineRepository: RoutineRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val connectionRepository: ConnectionRepository,
    private val eventRepository: EventRepository,
    private val extraRepository: ExtraBlockRepository,
    private val xpRepository: XpRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Abre a conversa mais recente ou cria uma nova. */
    suspend fun ensureSession(): AiSession =
        sessionRepository.latest() ?: sessionRepository.create()

    /** Envia a mensagem do usuário e busca a resposta do modelo (sem executar nada). */
    suspend fun send(sessionId: String, text: String): AiSendResult {
        val content = text.trim()
        if (content.isEmpty()) return AiSendResult.Ok

        val session = sessionRepository.sessions.first().firstOrNull { it.id == sessionId }
        messageRepository.append(
            AiMessage(sessionId = sessionId, role = AiRole.User, text = content),
        )
        sessionRepository.touch(sessionId)
        if (session != null && session.title.isBlank()) {
            sessionRepository.rename(sessionId, titleFor(content))
        }
        return respond(sessionId)
    }

    /** Confirma uma ação: executa e devolve o resultado ao modelo. */
    suspend fun confirm(sessionId: String, actionId: String): AiSendResult {
        val action = findAction(sessionId, actionId) ?: return AiSendResult.Ok
        if (action.status != AiActionStatus.Pending) return AiSendResult.Ok

        val outcome = withContext(Dispatchers.IO) { toolbox.execute(action.tool, action.args) }
        val status = if (outcome.ok) AiActionStatus.Confirmed else AiActionStatus.Failed
        messageRepository.updateAction(sessionId, actionId) {
            it.copy(status = status, result = outcome.summary)
        }
        messageRepository.append(
            AiMessage(
                sessionId = sessionId,
                role = AiRole.Action,
                text = "${action.tool}: ${outcome.summary}",
            ),
        )
        sessionRepository.touch(sessionId)
        return followUpIfIdle(sessionId)
    }

    /** Recusa uma ação: nada é executado, e o modelo fica sabendo. */
    suspend fun reject(sessionId: String, actionId: String): AiSendResult {
        val action = findAction(sessionId, actionId) ?: return AiSendResult.Ok
        if (action.status != AiActionStatus.Pending) return AiSendResult.Ok

        messageRepository.updateAction(sessionId, actionId) {
            it.copy(status = AiActionStatus.Rejected, result = "Recusada pelo usuário")
        }
        messageRepository.append(
            AiMessage(
                sessionId = sessionId,
                role = AiRole.Action,
                text = "${action.tool}: recusada pelo usuário",
            ),
        )
        sessionRepository.touch(sessionId)
        return followUpIfIdle(sessionId)
    }

    // ---------------- interno ----------------

    private suspend fun findAction(sessionId: String, actionId: String): AiAction? =
        messageRepository.messages.first()
            .filter { it.sessionId == sessionId }
            .flatMap { it.actions }
            .firstOrNull { it.id == actionId }

    /**
     * Só chama o modelo de novo quando não sobrou ação pendente: aí ele recebe os
     * resultados e pode continuar ou encerrar.
     */
    private suspend fun followUpIfIdle(sessionId: String): AiSendResult {
        val messages = messageRepository.messages.first().filter { it.sessionId == sessionId }
        val pending = messages.flatMap { it.actions }.any { it.status == AiActionStatus.Pending }
        if (pending) return AiSendResult.Ok
        return respond(sessionId)
    }

    private suspend fun respond(sessionId: String): AiSendResult {
        val providers = providerRepository.providers.first()
        if (providers.usableProviders().isEmpty()) {
            return AiSendResult.Error(
                "Nenhum provedor de IA habilitado. Abra Provedores e configure um.",
            )
        }

        val history = messageRepository.messages.first().filter { it.sessionId == sessionId }
        val system = buildSystemPrompt(
            today = LocalDate.now(),
            snapshot = snapshot(),
            memory = memoryRepository.all(),
        )
        val turns = buildTurns(system, history)

        return when (val result = client.complete(providers, turns)) {
            is AiChatResult.Failure -> AiSendResult.Error(result.message)
            is AiChatResult.Ok -> {
                val reply = parseAiReply(result.text)
                val actions = reply.actions.map { proposed ->
                    val known = toolbox.supportedTools.contains(proposed.tool)
                    AiAction(
                        tool = proposed.tool,
                        args = proposed.args,
                        status = if (known) AiActionStatus.Pending else AiActionStatus.Failed,
                        result = if (known) null else "ferramenta desconhecida: ${proposed.tool}",
                    )
                }
                messageRepository.append(
                    AiMessage(
                        sessionId = sessionId,
                        role = AiRole.Assistant,
                        text = reply.say,
                        actions = actions,
                    ),
                )
                sessionRepository.touch(sessionId)
                AiSendResult.Ok
            }
        }
    }

    /**
     * Converte o histórico no formato da API.
     *
     * Junta mensagens seguidas do mesmo papel (alguns provedores exigem alternância) e
     * corta por tamanho: mandar a conversa inteira estoura a janela do modelo.
     */
    private fun buildTurns(system: String, history: List<AiMessage>): List<ChatTurn> {
        val turns = mutableListOf(ChatTurn("system", system))
        var budget = MaxHistoryChars

        fun add(role: String, content: String) {
            if (content.isBlank()) return
            val last = turns.lastOrNull()
            if (last != null && last.role == role && role != "system") {
                turns[turns.lastIndex] = last.copy(content = last.content + "\n\n" + content)
            } else {
                turns += ChatTurn(role, content)
            }
        }

        for (message in history.asReversed()) {
            val (role, content) = when (message.role) {
                AiRole.User -> "user" to message.text.trim()
                AiRole.Assistant -> "assistant" to assistantContent(message)
                AiRole.Action -> "user" to "[resultado da ação] ${message.text}"
            }
            if (content.length > budget) break
            budget -= content.length
            turns.add(1, ChatTurn(role, content))
        }
        return turns
    }

    private fun assistantContent(message: AiMessage): String = buildString {
        append(message.text.trim())
        if (message.actions.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("[ações propostas] ")
            append(
                message.actions.joinToString("; ") {
                    "${it.tool} ${it.args} → ${it.status.name.lowercase()}"
                },
            )
        }
    }

    /** Retrato do app que vai no prompt (evita uma chamada só para ler o estado). */
    private suspend fun snapshot(): AiSnapshot {
        val routines = routineRepository.routines.first()
        val active = routines.firstOrNull { it.isActive }
        val tasks = taskRepository.tasks.first()
        val habits = habitRepository.habitList.first()
        val settings = settingsRepository.settings.first()
        val xp = xpRepository.all().sumOf { it.amount }
        return AiSnapshot(
            userName = settings.userName,
            routines = routines.size,
            activeRoutine = active?.name,
            blocks = routines.sumOf { it.blocks.size },
            openTasks = tasks.count { !it.done },
            doneTasks = tasks.count { it.done },
            habits = habits.size,
            connections = connectionRepository.connections.first().size,
            events = eventRepository.events.first().size,
            extras = extraRepository.blocks.first().size,
            addons = settings.enabledAddons,
            totalXp = xp,
            level = levelFromXp(xp),
        )
    }

    private fun titleFor(text: String): String {
        val flat = text.replace('\n', ' ').trim()
        return if (flat.length <= TitleChars) flat else flat.take(TitleChars).trimEnd() + "…"
    }

    private companion object {
        const val MaxHistoryChars = 24_000
        const val TitleChars = 48
    }
}
