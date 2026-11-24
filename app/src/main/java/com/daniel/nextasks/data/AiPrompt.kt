package com.daniel.nextasks.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Uma ferramenta do agente: nome, rótulo humano, grupo e assinatura. */
data class AiToolSpec(
    val name: String,
    /** Rótulo curto em português, usado no cartão de confirmação. */
    val label: String,
    val group: String,
    /** Assinatura compacta que vai no prompt ("task.create(title, dueDate?)"). */
    val signature: String,
)

/**
 * Catálogo de **tudo** que a IA pode fazer no app.
 *
 * É a mesma lista que descreve as ferramentas no prompt e alimenta o cartão de
 * confirmação; o executor ([AiToolbox]) tem que cobrir todos estes nomes — há teste
 * comparando as duas listas.
 */
val AI_TOOLS: List<AiToolSpec> = listOf(
    // Rotinas e blocos
    AiToolSpec("routine.list", "Listar rotinas", "Rotinas", "routine.list()"),
    AiToolSpec(
        "routine.create", "Criar rotina", "Rotinas",
        "routine.create(name, description?, frequency? daily|weekly, duration? indefinite|until, endDate? AAAA-MM-DD, active?)",
    ),
    AiToolSpec(
        "routine.update", "Editar rotina", "Rotinas",
        "routine.update(id, name?, description?, frequency?, duration?, endDate?, active?)",
    ),
    AiToolSpec("routine.activate", "Ativar rotina", "Rotinas", "routine.activate(id)"),
    AiToolSpec("routine.duplicate", "Duplicar rotina", "Rotinas", "routine.duplicate(id)"),
    AiToolSpec("routine.delete", "Excluir rotina", "Rotinas", "routine.delete(id)"),
    AiToolSpec(
        "block.create", "Criar bloco", "Rotinas",
        "block.create(routineId, title, startTime? HH:mm, endTime? HH:mm, allDay?, weekdays? [0..6], color?, confirmation? none|checklist|score, scoreMax?, description?)",
    ),
    AiToolSpec(
        "block.update", "Editar bloco", "Rotinas",
        "block.update(routineId, blockId, title?, startTime?, endTime?, allDay?, weekdays?, color?, confirmation?, scoreMax?, description?)",
    ),
    AiToolSpec("block.delete", "Excluir bloco", "Rotinas", "block.delete(routineId, blockId)"),
    AiToolSpec(
        "block.confirm", "Confirmar bloco", "Rotinas",
        "block.confirm(blockId, day? AAAA-MM-DD, value? true|false|nota)",
    ),
    AiToolSpec(
        "block.unconfirm", "Desmarcar bloco", "Rotinas",
        "block.unconfirm(blockId, day? AAAA-MM-DD)",
    ),
    AiToolSpec(
        "block.skipDay", "Pular um dia", "Rotinas",
        "block.skipDay(routineId, blockId, date AAAA-MM-DD)",
    ),
    AiToolSpec(
        "block.restoreDay", "Restaurar um dia", "Rotinas",
        "block.restoreDay(routineId, blockId, date AAAA-MM-DD)",
    ),

    // Tarefas e sub-tarefas
    AiToolSpec("task.list", "Listar tarefas", "Tarefas", "task.list(status? open|done|all)"),
    AiToolSpec(
        "task.create", "Criar tarefa", "Tarefas",
        "task.create(title, description?, startDate? AAAA-MM-DD, dueDate? AAAA-MM-DD, priority? 1..6)",
    ),
    AiToolSpec(
        "task.update", "Editar tarefa", "Tarefas",
        "task.update(id, title?, description?, startDate?, dueDate?, priority?, done?)",
    ),
    AiToolSpec("task.complete", "Concluir tarefa", "Tarefas", "task.complete(id, done? true)"),
    AiToolSpec("task.delete", "Excluir tarefa", "Tarefas", "task.delete(id)"),
    AiToolSpec("subtask.list", "Listar sub-tarefas", "Tarefas", "subtask.list(taskId)"),
    AiToolSpec(
        "subtask.create", "Criar sub-tarefa", "Tarefas",
        "subtask.create(taskId, title, description?, parentId?)",
    ),
    AiToolSpec(
        "subtask.update", "Editar sub-tarefa", "Tarefas",
        "subtask.update(id, title?, description?)",
    ),
    AiToolSpec(
        "subtask.complete", "Concluir sub-tarefa", "Tarefas",
        "subtask.complete(id, done? true)",
    ),
    AiToolSpec("subtask.delete", "Excluir sub-tarefa", "Tarefas", "subtask.delete(id)"),

    // Hábitos
    AiToolSpec("habit.list", "Listar hábitos", "Hábitos", "habit.list()"),
    AiToolSpec(
        "habit.create", "Criar hábito", "Hábitos",
        "habit.create(name, description?, kind? good|bad, frequency? daily|weekly, targetCount?, daysOfWeek? [0..6], icon?, color?, allowExceed?)",
    ),
    AiToolSpec(
        "habit.update", "Editar hábito", "Hábitos",
        "habit.update(id, name?, description?, kind?, frequency?, targetCount?, daysOfWeek?, icon?, color?, allowExceed?)",
    ),
    AiToolSpec("habit.delete", "Excluir hábito", "Hábitos", "habit.delete(id)"),
    AiToolSpec(
        "habit.mark", "Marcar hábito", "Hábitos",
        "habit.mark(id, date? AAAA-MM-DD, delta?)",
    ),
    AiToolSpec("habit.unmark", "Desmarcar hábito", "Hábitos", "habit.unmark(id, date? AAAA-MM-DD)"),

    // Agenda
    AiToolSpec(
        "event.list", "Listar eventos", "Agenda",
        "event.list(from? AAAA-MM-DD, to? AAAA-MM-DD)",
    ),
    AiToolSpec(
        "event.create", "Criar evento", "Agenda",
        "event.create(title, startDate AAAA-MM-DD, endDate?, startTime? HH:mm, endTime? HH:mm, color?, kind? event|unforeseen, suspension? none|days|hours)",
    ),
    AiToolSpec(
        "event.update", "Editar evento", "Agenda",
        "event.update(id, title?, startDate?, endDate?, startTime?, endTime?, color?, kind?, suspension?)",
    ),
    AiToolSpec("event.delete", "Excluir evento", "Agenda", "event.delete(id)"),
    AiToolSpec(
        "extra.create", "Criar bloco avulso", "Agenda",
        "extra.create(date AAAA-MM-DD, title, startTime? HH:mm, endTime? HH:mm, allDay?, color?, confirmation? none|checklist|score, scoreMax?, description?, routineId?)",
    ),
    AiToolSpec(
        "extra.update", "Editar bloco avulso", "Agenda",
        "extra.update(id, title?, startTime?, endTime?, allDay?, color?, confirmation?, scoreMax?, description?)",
    ),
    AiToolSpec("extra.delete", "Excluir bloco avulso", "Agenda", "extra.delete(id)"),

    // Conexões
    AiToolSpec("connection.list", "Listar conexões", "Conexões", "connection.list()"),
    AiToolSpec(
        "connection.create", "Criar conexão", "Conexões",
        "connection.create(routineId, blockId, taskId? | subtaskId? | habitId?, requiredCount? 1..99, dayFilter? all|weekday:0..6|date:AAAA-MM-DD)",
    ),
    AiToolSpec(
        "connection.update", "Editar conexão", "Conexões",
        "connection.update(id, requiredCount?, dayFilter?)",
    ),
    AiToolSpec("connection.delete", "Excluir conexão", "Conexões", "connection.delete(id)"),
    AiToolSpec("connection.apply", "Aplicar conexão agora", "Conexões", "connection.apply(id)"),

    // Configurações e gamificação
    AiToolSpec("settings.get", "Ler configurações", "Configurações", "settings.get()"),
    AiToolSpec(
        "settings.update", "Alterar configurações", "Configurações",
        "settings.update(userName?, theme? system|light|dark, accent? green|blue|purple|pink|orange|amber|cyan)",
    ),
    AiToolSpec(
        "addon.set", "Ligar/desligar addon", "Configurações",
        "addon.set(addon connections|ai|gamification, enabled)",
    ),
    AiToolSpec("gamification.stats", "Ver estatísticas", "Configurações", "gamification.stats()"),

    // Memória
    AiToolSpec("memory.list", "Listar memória", "Memória", "memory.list()"),
    AiToolSpec("memory.add", "Lembrar", "Memória", "memory.add(text)"),
    AiToolSpec("memory.update", "Editar memória", "Memória", "memory.update(id, text)"),
    AiToolSpec("memory.delete", "Esquecer", "Memória", "memory.delete(id)"),
)

/** Busca a especificação pelo nome da ferramenta. */
fun aiToolSpec(name: String): AiToolSpec? = AI_TOOLS.firstOrNull { it.name == name }

/** Retrato do app para o prompt: evita uma ferramenta só para "ler o app". */
data class AiSnapshot(
    val userName: String = "",
    val routines: Int = 0,
    val activeRoutine: String? = null,
    val blocks: Int = 0,
    val openTasks: Int = 0,
    val doneTasks: Int = 0,
    val habits: Int = 0,
    val connections: Int = 0,
    val events: Int = 0,
    val extras: Int = 0,
    val addons: Set<Addon> = emptySet(),
    val totalXp: Int = 0,
    val level: Int = 0,
)

private val IsoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Monta o pedido de sistema: quem é o assistente, como responder (JSON), o catálogo
 * de ferramentas, a memória e o estado atual do app.
 */
fun buildSystemPrompt(today: LocalDate, snapshot: AiSnapshot, memory: List<AiMemoryEntry>): String {
    val weekday = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("pt", "BR"))
    val tools = AI_TOOLS.groupBy { it.group }
        .entries
        .joinToString("\n") { (group, list) ->
            "$group:\n" + list.joinToString("\n") { "  - ${it.signature} — ${it.label}" }
        }

    val memoryBlock = if (memory.isEmpty()) {
        "(vazia)"
    } else {
        memory.joinToString("\n") { "- ${it.text}" }
    }

    val addons = if (snapshot.addons.isEmpty()) "nenhum" else snapshot.addons.joinToString { it.name.lowercase() }
    val greeting = snapshot.userName.takeIf { it.isNotBlank() }?.let { " O nome do usuário é $it." }.orEmpty()

    return buildString {
        appendLine("Você é o assistente do NexTasks, um app de rotinas, calendário, tarefas e hábitos.")
        appendLine("Responda em português do Brasil, direto e sem enrolação.$greeting")
        appendLine("Hoje é $weekday, ${today.format(IsoDate)}.")
        appendLine()
        appendLine("## Formato da resposta")
        appendLine("Responda SEMPRE com um único objeto JSON, sem nada antes ou depois:")
        appendLine("""{"say": "texto para o usuário", "actions": [{"tool": "nome", "args": {}}]}""")
        appendLine("- \"say\" é o que aparece no chat: explique, confirme ou pergunte. Nunca use markdown pesado.")
        appendLine("- \"actions\" traz as ferramentas a executar; use [] quando basta conversar. Máximo 6 por resposta.")
        appendLine("- TODA ação passa por confirmação do usuário antes de rodar. Nunca diga que já fez: proponha.")
        appendLine("- Use só as ferramentas listadas abaixo e ids que vieram de uma listagem. Na dúvida, liste antes.")
        appendLine("- Datas em AAAA-MM-DD e horas em HH:mm. Não invente dados: o resumo abaixo é o estado atual.")
        appendLine("- Não existe ferramenta para exportar/importar backup nem para abrir telas do sistema.")
        appendLine()
        appendLine("## Ferramentas")
        appendLine(tools)
        appendLine()
        appendLine("## Memória do usuário (editável por ele)")
        appendLine(memoryBlock)
        appendLine()
        appendLine("## Estado atual do app")
        appendLine("- Rotinas: ${snapshot.routines} (ativa: ${snapshot.activeRoutine ?: "nenhuma"}), ${snapshot.blocks} blocos")
        appendLine("- Tarefas: ${snapshot.openTasks} abertas, ${snapshot.doneTasks} concluídas")
        appendLine("- Hábitos: ${snapshot.habits} · Conexões: ${snapshot.connections} · Eventos: ${snapshot.events} · Blocos avulsos: ${snapshot.extras}")
        appendLine("- Addons ligados: $addons")
        appendLine("- Gamificação: nível ${snapshot.level}, ${snapshot.totalXp} XP")
    }
}

/** Ação que o modelo pediu (ainda não executada). */
data class AiProposedAction(val tool: String, val args: String)

/** Resposta do modelo separada em fala + ações. */
data class AiReply(val say: String, val actions: List<AiProposedAction>)

/**
 * Lê a resposta do modelo.
 *
 * Modelo pequeno não é confiável com JSON: tira cerca de código, aceita texto puro
 * (vira só `say`) e ignora ação sem `tool`. Nunca lança — conversa não pode quebrar
 * por formato.
 */
fun parseAiReply(raw: String): AiReply {
    val text = raw.trim()
    val candidate = extractJsonObject(text)
    if (candidate != null) {
        val parsed = runCatching { AppJson.parseToJsonElement(candidate).jsonObject }.getOrNull()
        if (parsed != null) {
            val say = (parsed["say"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            val actions = (parsed["actions"] as? JsonArray)
                ?.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val tool = (obj["tool"] as? JsonPrimitive)?.contentOrNull?.trim()
                    if (tool.isNullOrBlank()) return@mapNotNull null
                    val args = obj["args"] ?: JsonObject(emptyMap())
                    AiProposedAction(tool = tool, args = args.toString())
                }
                .orEmpty()
                .take(6)
            if (say.isNotBlank() || actions.isNotEmpty()) return AiReply(say, actions)
        }
    }
    return AiReply(say = text, actions = emptyList())
}

/** Pega o primeiro `{` até o último `}`, ignorando cerca de código em volta. */
private fun extractJsonObject(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return text.substring(start, end + 1)
}

/** Rótulos dos argumentos no cartão de confirmação. */
private val ArgLabels = mapOf(
    "id" to "id",
    "routineId" to "rotina",
    "blockId" to "bloco",
    "taskId" to "tarefa",
    "subtaskId" to "sub-tarefa",
    "habitId" to "hábito",
    "parentId" to "sub-tarefa mãe",
    "name" to "nome",
    "title" to "título",
    "description" to "descrição",
    "text" to "texto",
    "startTime" to "início",
    "endTime" to "término",
    "startDate" to "início",
    "endDate" to "término",
    "dueDate" to "prazo",
    "date" to "data",
    "day" to "dia",
    "value" to "valor",
    "count" to "quantidade",
    "delta" to "incremento",
    "priority" to "prioridade",
    "frequency" to "frequência",
    "duration" to "duração",
    "weekdays" to "dias da semana",
    "color" to "cor",
    "confirmation" to "confirmação",
    "scoreMax" to "nota máxima",
    "allDay" to "dia inteiro",
    "kind" to "tipo",
    "suspension" to "suspensão",
    "active" to "ativa",
    "enabled" to "ligado",
    "icon" to "ícone",
    "targetCount" to "meta",
    "allowExceed" to "passar da meta",
    "daysOfWeek" to "dias",
    "requiredCount" to "meta de confirmações",
    "dayFilter" to "filtro de dia",
    "status" to "situação",
    "from" to "de",
    "to" to "até",
    "addon" to "addon",
    "theme" to "tema",
    "accent" to "cor de destaque",
    "userName" to "nome do usuário",
)

/** Argumentos prontos para exibir: pares (rótulo, valor). */
fun formatActionArgs(argsJson: String): List<Pair<String, String>> {
    val obj = runCatching { AppJson.parseToJsonElement(argsJson).jsonObject }.getOrNull() ?: return emptyList()
    return obj.entries.map { (key, value) ->
        val label = ArgLabels[key] ?: key
        val text = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            is JsonArray -> value.joinToString(", ") {
                (it as? JsonPrimitive)?.contentOrNull ?: it.toString()
            }
            else -> value.toString()
        }
        label to text
    }
}
