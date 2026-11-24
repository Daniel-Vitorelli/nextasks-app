package com.daniel.nextasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regras do assistente que dá para testar sem rede: catálogo x executor, prompt,
 * leitura da resposta do modelo, fila de provedores e leitura de argumentos.
 *
 * O que depende de HTTP de verdade fica para o teste manual no emulador.
 */
class AiRulesTest {

    private val today = LocalDate.of(2026, 11, 24)

    // ---------------- catálogo ----------------

    @Test
    fun `catalogo e executor tem exatamente as mesmas ferramentas`() {
        assertEquals(AI_TOOLS.map { it.name }.toSet(), AI_EXECUTED_TOOLS)
    }

    @Test
    fun `catalogo nao repete nome e toda assinatura comeca pelo nome`() {
        assertEquals(AI_TOOLS.size, AI_TOOLS.map { it.name }.distinct().size)
        AI_TOOLS.forEach { spec ->
            assertTrue("rótulo vazio em ${spec.name}", spec.label.isNotBlank())
            assertTrue(
                "assinatura de ${spec.name} não começa pelo nome: ${spec.signature}",
                spec.signature.startsWith("${spec.name}("),
            )
        }
    }

    @Test
    fun `busca por nome devolve a spec`() {
        assertEquals("Criar tarefa", aiToolSpec("task.create")?.label)
        assertEquals(null, aiToolSpec("nao.existe"))
    }

    // ---------------- prompt ----------------

    @Test
    fun `prompt traz data, ferramentas, memoria e estado do app`() {
        val prompt = buildSystemPrompt(
            today = today,
            snapshot = AiSnapshot(
                userName = "Daniel",
                routines = 1,
                activeRoutine = "Treino",
                openTasks = 2,
                habits = 3,
            ),
            memory = listOf(AiMemoryEntry(text = "prefere treinar de manhã")),
        )

        assertTrue(prompt.contains("2026-11-24"))
        assertTrue(prompt.contains("Daniel"))
        assertTrue(prompt.contains("Treino"))
        assertTrue(prompt.contains("prefere treinar de manhã"))
        assertTrue(prompt.contains("2 abertas"))
        AI_TOOLS.forEach { spec ->
            assertTrue("o prompt não descreve ${spec.name}", prompt.contains(spec.name))
        }
    }

    @Test
    fun `prompt com memoria vazia avisa que esta vazia`() {
        val prompt = buildSystemPrompt(today, AiSnapshot(), emptyList())
        assertTrue(prompt.contains("(vazia)"))
    }

    // ---------------- resposta ----------------

    @Test
    fun `json com fala e acao e lido`() {
        val reply = parseAiReply(
            """{"say": "vou criar a tarefa", "actions": [{"tool": "task.create", "args": {"title": "Comprar pão"}}]}""",
        )

        assertEquals("vou criar a tarefa", reply.say)
        assertEquals(1, reply.actions.size)
        assertEquals("task.create", reply.actions.first().tool)
        assertTrue(reply.actions.first().args.contains("Comprar"))
    }

    @Test
    fun `resposta cercada de codigo e lida igual`() {
        val raw = "Claro:\n```json\n{\"say\":\"feito\",\"actions\":[]}\n```"
        val reply = parseAiReply(raw)

        assertEquals("feito", reply.say)
        assertTrue(reply.actions.isEmpty())
    }

    @Test
    fun `texto puro vira so fala`() {
        val reply = parseAiReply("Não entendi, pode repetir?")

        assertEquals("Não entendi, pode repetir?", reply.say)
        assertTrue(reply.actions.isEmpty())
    }

    @Test
    fun `acao sem tool e ignorada e o resto sobrevive`() {
        val reply = parseAiReply(
            """{"say":"", "actions":[{"args":{"x":1}},{"tool":"habit.list"}]}""",
        )

        assertEquals(1, reply.actions.size)
        assertEquals("habit.list", reply.actions.first().tool)
    }

    @Test
    fun `no maximo seis acoes por resposta`() {
        val actions = (1..9).joinToString(",") { """{"tool":"task.list","args":{}}""" }
        val reply = parseAiReply("""{"say":"", "actions":[$actions]}""")

        assertEquals(6, reply.actions.size)
    }

    @Test
    fun `json quebrado nao derruba a leitura`() {
        val reply = parseAiReply("""{"say": "oi", "actions": [""")

        // Sem JSON válido, o texto inteiro vira fala — nada de exceção.
        assertTrue(reply.say.isNotEmpty())
        assertTrue(reply.actions.isEmpty())
    }

    // ---------------- fila de provedores ----------------

    @Test
    fun `fila mantem a ordem e tira quem esta desligado`() {
        val a = AiProvider(name = "a")
        val off = AiProvider(name = "b", enabled = false)
        val semModelo = AiProvider(name = "c", model = "")
        val d = AiProvider(name = "d")

        assertEquals(listOf("a", "d"), listOf(a, off, semModelo, d).usableProviders().map { it.name })
    }

    @Test
    fun `endpoint nao duplica o caminho`() {
        val client = AiClient()

        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            client.endpoint("https://api.openai.com/v1"),
        )
        assertEquals(
            "http://10.0.2.2:11434/v1/chat/completions",
            client.endpoint("http://10.0.2.2:11434/v1/"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            client.endpoint("https://openrouter.ai/api/v1/chat/completions"),
        )
    }

    // ---------------- argumentos ----------------

    @Test
    fun `argumentos aparecem com rotulo em portugues`() {
        val args = formatActionArgs("""{"title":"Dentista","dueDate":"2026-11-25","weekdays":[1,2]}""")

        assertEquals(
            listOf("título" to "Dentista", "prazo" to "2026-11-25", "dias da semana" to "1, 2"),
            args,
        )
    }

    @Test
    fun `argumento desconhecido mantem a chave`() {
        assertEquals(listOf("foo" to "bar"), formatActionArgs("""{"foo":"bar"}"""))
    }
}
