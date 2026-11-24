package com.daniel.nextasks.data

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Resultado de uma ferramenta: se deu certo e o que dizer (para o modelo e o chat). */
data class AiToolResult(val ok: Boolean, val summary: String)

/** Erro de argumento — vira `ok = false` com a explicação, nunca exceção solta. */
private class ToolFailure(message: String) : Exception(message)

/**
 * Executa as ferramentas do agente sobre os mesmos repositórios e motores que as
 * telas usam.
 *
 * Isso é deliberado: a IA não tem caminho paralelo. Concluir tarefa passa pelo
 * [TaskCompletion] (cascata de sub-tarefas), confirmar bloco avisa as conexões e a
 * gamificação, e toda exclusão limpa o que apontava para o registro — igual ao botão
 * da tela.
 */
class AiToolbox(
    private val routineRepository: RoutineRepository,
    private val completionRepository: CompletionRepository,
    private val taskRepository: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
    private val taskCompletion: TaskCompletion,
    private val habitRepository: HabitRepository,
    private val eventRepository: EventRepository,
    private val extraRepository: ExtraBlockRepository,
    private val overrideRepository: BlockOverrideRepository,
    private val connectionRepository: ConnectionRepository,
    private val connectionEngine: ConnectionEngine,
    private val settingsRepository: SettingsRepository,
    private val gamificationEngine: GamificationEngine,
    private val xpRepository: XpRepository,
    private val memoryRepository: AiMemoryRepository,
) {
    /** Nomes aceitos pelo executor — comparado com [AI_TOOLS] no teste. */
    val supportedTools: Set<String> get() = AI_EXECUTED_TOOLS

    suspend fun execute(tool: String, argsJson: String): AiToolResult = try {
        val args = ToolArgs(parseArgs(argsJson))
        val summary = when (tool) {
            "routine.list" -> listRoutines()
            "routine.create" -> createRoutine(args)
            "routine.update" -> updateRoutine(args)
            "routine.activate" -> activateRoutine(args)
            "routine.duplicate" -> duplicateRoutine(args)
            "routine.delete" -> deleteRoutine(args)

            "block.create" -> createBlock(args)
            "block.update" -> updateBlock(args)
            "block.delete" -> deleteBlock(args)
            "block.confirm" -> confirmBlock(args, unconfirm = false)
            "block.unconfirm" -> confirmBlock(args, unconfirm = true)
            "block.skipDay" -> skipDay(args, restore = false)
            "block.restoreDay" -> skipDay(args, restore = true)

            "task.list" -> listTasks(args)
            "task.create" -> createTask(args)
            "task.update" -> updateTask(args)
            "task.complete" -> completeTask(args)
            "task.delete" -> deleteTask(args)
            "subtask.list" -> listSubtasks(args)
            "subtask.create" -> createSubtask(args)
            "subtask.update" -> updateSubtask(args)
            "subtask.complete" -> completeSubtask(args)
            "subtask.delete" -> deleteSubtask(args)

            "habit.list" -> listHabits()
            "habit.create" -> createHabit(args)
            "habit.update" -> updateHabit(args)
            "habit.delete" -> deleteHabit(args)
            "habit.mark" -> markHabit(args, unmark = false)
            "habit.unmark" -> markHabit(args, unmark = true)

            "event.list" -> listEvents(args)
            "event.create" -> createEvent(args)
            "event.update" -> updateEvent(args)
            "event.delete" -> deleteEvent(args)
            "extra.create" -> createExtra(args)
            "extra.update" -> updateExtra(args)
            "extra.delete" -> deleteExtra(args)

            "connection.list" -> listConnections()
            "connection.create" -> createConnection(args)
            "connection.update" -> updateConnection(args)
            "connection.delete" -> deleteConnection(args)
            "connection.apply" -> applyConnection(args)

            "settings.get" -> getSettings()
            "settings.update" -> updateSettings(args)
            "addon.set" -> setAddon(args)
            "gamification.stats" -> stats()

            "memory.list" -> listMemory()
            "memory.add" -> addMemory(args)
            "memory.update" -> updateMemory(args)
            "memory.delete" -> deleteMemory(args)

            else -> throw ToolFailure("Ferramenta desconhecida: $tool")
        }
        AiToolResult(ok = true, summary = summary)
    } catch (error: ToolFailure) {
        AiToolResult(ok = false, summary = error.message ?: "argumentos inválidos")
    } catch (error: Exception) {
        AiToolResult(ok = false, summary = "não consegui executar: ${error.message ?: error.javaClass.simpleName}")
    }

    // ---------------- Rotinas ----------------

    private suspend fun listRoutines(): String {
        val routines = routineRepository.routines.first()
        if (routines.isEmpty()) return "Nenhuma rotina cadastrada."
        val completions = completionRepository.completions.first()
        val today = LocalDate.now()
        return buildString {
            routines.forEach { routine ->
                appendLine(
                    "- ${routine.id} | ${routine.name} | ${routine.frequency.name.lowercase()} | " +
                        "${if (routine.isActive) "ativa" else "inativa"} | ${routine.blocks.size} blocos" +
                        (routine.description?.let { " | $it" } ?: ""),
                )
                routine.blocks.forEach { block ->
                    val done = completions.any {
                        it.blockId == block.id && it.periodStart == today.toString() &&
                            completionCounts(block.confirmation, it.value) &&
                            it.value != "false"
                    }
                    appendLine(
                        "    · ${block.id} | ${block.title} | ${block.startTime}-${block.endTime}" +
                            " | ${formatWeekdays(block)} | ${block.confirmation.name.lowercase()}" +
                            (if (done) " | hoje: confirmado" else " | hoje: aberto") +
                            (block.description?.let { " | $it" } ?: ""),
                    )
                }
            }
        }.trim()
    }

    private suspend fun createRoutine(args: ToolArgs): String {
        val name = args.string("name")
        val routine = Routine(
            name = name,
            description = args.optionalString("description"),
            frequency = args.frequency() ?: RoutineFrequency.Daily,
            duration = args.duration() ?: RoutineDuration.Indefinite,
            endDate = args.optionalDate("endDate")?.toString(),
            isActive = args.bool("active", false),
        )
        routineRepository.save(routine)
        return "Rotina '$name' criada (id ${routine.id})."
    }

    private suspend fun updateRoutine(args: ToolArgs): String {
        val routine = findRoutine(args.string("id"))
        val updated = routine.copy(
            name = args.optionalString("name") ?: routine.name,
            description = if (args.has("description")) args.optionalString("description") else routine.description,
            frequency = args.frequency() ?: routine.frequency,
            duration = args.duration() ?: routine.duration,
            endDate = if (args.has("endDate")) args.optionalDate("endDate")?.toString() else routine.endDate,
            isActive = args.optBool("active") ?: routine.isActive,
        )
        routineRepository.save(updated)
        return "Rotina '${updated.name}' atualizada."
    }

    private suspend fun activateRoutine(args: ToolArgs): String {
        val routine = findRoutine(args.string("id"))
        routineRepository.toggleActive(routine.id)
        return "Rotina '${routine.name}' agora está ${if (!routine.isActive) "ativa" else "inativa"}."
    }

    private suspend fun duplicateRoutine(args: ToolArgs): String {
        val routine = findRoutine(args.string("id"))
        routineRepository.duplicate(routine.id)
        return "Rotina '${routine.name}' duplicada (a cópia nasce inativa)."
    }

    private suspend fun deleteRoutine(args: ToolArgs): String {
        val routine = findRoutine(args.string("id"))
        routine.blocks.forEach { block ->
            completionRepository.clearForBlock(block.id)
            overrideRepository.clearForBlock(block.id)
        }
        overrideRepository.clearForRoutine(routine.id)
        connectionRepository.clearForRoutine(routine.id)
        routineRepository.delete(routine.id)
        gamificationEngine.reconcile()
        return "Rotina '${routine.name}' excluída com histórico, exceções e conexões."
    }

    private suspend fun createBlock(args: ToolArgs): String {
        val routine = findRoutine(args.string("routineId"))
        val block = RoutineBlock(
            title = args.string("title"),
            description = args.optionalString("description"),
            startTime = args.time("startTime") ?: "08:00",
            endTime = args.time("endTime") ?: "09:00",
            isAllDay = args.bool("allDay", false),
            color = args.color() ?: EventColorKey.Green,
            weekdays = args.intList("weekdays") ?: emptyList(),
            confirmation = args.confirmation() ?: EventConfirmation.None,
            scoreMax = (args.optInt("scoreMax") ?: 5).coerceIn(2, 10),
        )
        routineRepository.saveBlock(routine.id, block)
        return "Bloco '${block.title}' criado na rotina '${routine.name}' (${block.startTime}-${block.endTime})."
    }

    private suspend fun updateBlock(args: ToolArgs): String {
        val routine = findRoutine(args.string("routineId"))
        val block = findBlock(routine, args.string("blockId"))
        val updated = block.copy(
            title = args.optionalString("title") ?: block.title,
            description = if (args.has("description")) args.optionalString("description") else block.description,
            startTime = args.time("startTime") ?: block.startTime,
            endTime = args.time("endTime") ?: block.endTime,
            isAllDay = args.optBool("allDay") ?: block.isAllDay,
            color = args.color() ?: block.color,
            weekdays = args.intList("weekdays") ?: block.weekdays,
            confirmation = args.confirmation() ?: block.confirmation,
            scoreMax = args.optInt("scoreMax")?.coerceIn(2, 10) ?: block.scoreMax,
        )
        routineRepository.saveBlock(routine.id, updated)
        return "Bloco '${updated.title}' atualizado."
    }

    private suspend fun deleteBlock(args: ToolArgs): String {
        val routine = findRoutine(args.string("routineId"))
        val block = findBlock(routine, args.string("blockId"))
        routineRepository.deleteBlock(routine.id, block.id)
        completionRepository.clearForBlock(block.id)
        overrideRepository.clearForBlock(block.id)
        connectionRepository.clearForBlock(block.id)
        gamificationEngine.reconcile()
        return "Bloco '${block.title}' excluído com histórico, exceção e conexões."
    }

    private suspend fun confirmBlock(args: ToolArgs, unconfirm: Boolean): String {
        val blockId = args.string("blockId")
        val day = args.optionalDate("day") ?: LocalDate.now()
        val raw = if (unconfirm) null else (args.optionalString("value") ?: "true")
        val stored = when {
            raw == null || raw.equals("false", true) -> null
            raw.equals("true", true) -> "true"
            raw.toIntOrNull() != null -> raw
            else -> throw ToolFailure("valor inválido: use true, false ou uma nota")
        }
        completionRepository.set(blockId, day, stored)
        if (stored == null) {
            connectionEngine.onBlockUnconfirmed(blockId, day)
        } else {
            connectionEngine.onBlockConfirmed(blockId, day)
        }
        gamificationEngine.reconcile()
        return if (stored == null) {
            "Bloco desmarcado em $day."
        } else {
            "Bloco confirmado em $day (valor $stored)."
        }
    }

    private suspend fun skipDay(args: ToolArgs, restore: Boolean): String {
        val routine = findRoutine(args.string("routineId"))
        val block = findBlock(routine, args.string("blockId"))
        val date = args.date("date")
        if (restore) {
            overrideRepository.restore(routine.id, block.id, date.toString())
            return "Bloco '${block.title}' volta a acontecer em $date."
        }
        overrideRepository.put(
            BlockOverride(routineId = routine.id, blockId = block.id, date = date.toString(), deleted = true),
        )
        return "Bloco '${block.title}' removido do dia $date."
    }

    // ---------------- Tarefas ----------------

    private suspend fun listTasks(args: ToolArgs): String {
        val status = (args.optionalString("status") ?: "open").lowercase()
        val subtasks = subtaskRepository.subtasks.first()
        val tasks = taskRepository.tasks.first()
            .filter {
                when (status) {
                    "done" -> it.done
                    "all" -> true
                    else -> !it.done
                }
            }
            .sortedWith(compareBy<Task>({ it.done }, { it.dueDate ?: "9999" }, { -it.priority }))
        if (tasks.isEmpty()) return "Nenhuma tarefa $status."
        return tasks.joinToString("\n") { task ->
            val pending = subtasks.count { it.taskId == task.id && !it.done }
            buildString {
                append("- ${task.id} | ${if (task.done) "[x]" else "[ ]"} ${task.title}")
                append(" | prioridade ${task.priority}")
                task.startDate?.let { append(" | começa $it") }
                task.dueDate?.let { append(" | prazo $it") }
                if (pending > 0) append(" | $pending sub-tarefas abertas")
                task.description?.let { append(" | $it") }
            }
        }
    }

    private suspend fun createTask(args: ToolArgs): String {
        val task = Task(
            title = args.string("title"),
            description = args.optionalString("description"),
            startDate = args.optionalDate("startDate")?.toString(),
            dueDate = args.optionalDate("dueDate")?.toString(),
            priority = (args.optInt("priority") ?: Task.DEFAULT_PRIORITY).coerceIn(1, 6),
        )
        taskRepository.save(task)
        return "Tarefa '${task.title}' criada (id ${task.id})."
    }

    private suspend fun updateTask(args: ToolArgs): String {
        val task = findTask(args.string("id"))
        val done = args.optBool("done")
        val updated = task.copy(
            title = args.optionalString("title") ?: task.title,
            description = if (args.has("description")) args.optionalString("description") else task.description,
            startDate = if (args.has("startDate")) args.optionalDate("startDate")?.toString() else task.startDate,
            dueDate = if (args.has("dueDate")) args.optionalDate("dueDate")?.toString() else task.dueDate,
            priority = (args.optInt("priority") ?: task.priority).coerceIn(1, 6),
        )
        taskRepository.save(updated)
        if (done != null && done != task.done) {
            // Pelo TaskCompletion: concluir a tarefa conclui a árvore de sub-tarefas.
            taskCompletion.setDone(task.id, done)
            notifyTask(task.id, done)
            return "Tarefa '${updated.title}' atualizada e ${if (done) "concluída" else "reaberta"}."
        }
        return "Tarefa '${updated.title}' atualizada."
    }

    private suspend fun completeTask(args: ToolArgs): String {
        val task = findTask(args.string("id"))
        val done = args.bool("done", true)
        taskCompletion.setDone(task.id, done)
        notifyTask(task.id, done)
        return "Tarefa '${task.title}' ${if (done) "concluída" else "reaberta"}."
    }

    private suspend fun deleteTask(args: ToolArgs): String {
        val task = findTask(args.string("id"))
        val subtaskIds = subtaskRepository.subtasks.first()
            .filter { it.taskId == task.id }
            .map { it.id }
        taskRepository.delete(task.id)
        subtaskRepository.deleteForTask(task.id)
        connectionRepository.clearForTask(task.id, subtaskIds)
        gamificationEngine.reconcile()
        return "Tarefa '${task.title}' excluída com ${subtaskIds.size} sub-tarefas."
    }

    private suspend fun listSubtasks(args: ToolArgs): String {
        val taskId = args.string("taskId")
        val list = subtaskRepository.subtasks.first().filter { it.taskId == taskId }
        if (list.isEmpty()) return "Nenhuma sub-tarefa nessa tarefa."
        return list.joinToString("\n") {
            "- ${it.id} | ${if (it.done) "[x]" else "[ ]"} ${it.title}" +
                (it.parentId?.let { parent -> " | mãe $parent" } ?: "") +
                (it.description?.let { text -> " | $text" } ?: "")
        }
    }

    private suspend fun createSubtask(args: ToolArgs): String {
        val task = findTask(args.string("taskId"))
        val subtask = Subtask(
            taskId = task.id,
            parentId = args.optionalString("parentId"),
            title = args.string("title"),
            description = args.optionalString("description"),
        )
        subtaskRepository.save(subtask)
        return "Sub-tarefa '${subtask.title}' criada em '${task.title}' (id ${subtask.id})."
    }

    private suspend fun updateSubtask(args: ToolArgs): String {
        val subtask = findSubtask(args.string("id"))
        val updated = subtask.copy(
            title = args.optionalString("title") ?: subtask.title,
            description = if (args.has("description")) args.optionalString("description") else subtask.description,
        )
        subtaskRepository.save(updated)
        return "Sub-tarefa '${updated.title}' atualizada."
    }

    private suspend fun completeSubtask(args: ToolArgs): String {
        val subtask = findSubtask(args.string("id"))
        val done = args.bool("done", true)
        if (subtask.done == done) return "Sub-tarefa '${subtask.title}' já estava ${if (done) "concluída" else "aberta"}."
        val taskDone = subtaskRepository.toggleDone(subtask.id)
        connectionEngine.onSubtaskDone(subtask.id)
        gamificationEngine.onSubtaskChanged(subtask.id)
        if (taskDone == true) {
            connectionEngine.onTaskDone(subtask.taskId)
            gamificationEngine.onTaskChanged(subtask.taskId)
        }
        return "Sub-tarefa '${subtask.title}' ${if (done) "concluída" else "reaberta"}."
    }

    private suspend fun deleteSubtask(args: ToolArgs): String {
        val subtask = findSubtask(args.string("id"))
        val taskDone = subtaskRepository.delete(subtask.id)
        connectionRepository.clearForSubtasks(listOf(subtask.id))
        gamificationEngine.reconcile()
        if (taskDone == false) taskCompletion.setDone(subtask.taskId, false)
        return "Sub-tarefa '${subtask.title}' excluída."
    }

    // ---------------- Hábitos ----------------

    private suspend fun listHabits(): String {
        val habits = habitRepository.habitList.first()
        if (habits.isEmpty()) return "Nenhum hábito cadastrado."
        val completions = habitRepository.completionList.first()
        val today = LocalDate.now().toString()
        return habits.joinToString("\n") { habit ->
            val count = completions.firstOrNull { it.habitId == habit.id && it.date == today }?.count ?: 0
            "- ${habit.id} | ${habit.name} | ${habit.kind.name.lowercase()} | " +
                "${habit.frequency.name.lowercase()} | meta ${habit.targetCount ?: "sem meta"} | " +
                "hoje: $count" + (habit.description?.let { " | $it" } ?: "")
        }
    }

    private suspend fun createHabit(args: ToolArgs): String {
        val habit = Habit(
            name = args.string("name"),
            description = args.optionalString("description"),
            kind = args.habitKind() ?: HabitKind.Good,
            frequency = args.habitFrequency() ?: HabitFrequency.Daily,
            targetCount = if (args.has("targetCount")) args.optInt("targetCount") else 1,
            daysOfWeek = args.intList("daysOfWeek") ?: (0..6).toList(),
            icon = args.optionalString("icon") ?: "target",
            color = args.color() ?: EventColorKey.Green,
            allowExceed = args.bool("allowExceed", false),
        )
        habitRepository.save(habit)
        return "Hábito '${habit.name}' criado (id ${habit.id})."
    }

    private suspend fun updateHabit(args: ToolArgs): String {
        val habit = findHabit(args.string("id"))
        val updated = habit.copy(
            name = args.optionalString("name") ?: habit.name,
            description = if (args.has("description")) args.optionalString("description") else habit.description,
            kind = args.habitKind() ?: habit.kind,
            frequency = args.habitFrequency() ?: habit.frequency,
            targetCount = if (args.has("targetCount")) args.optInt("targetCount") else habit.targetCount,
            daysOfWeek = args.intList("daysOfWeek") ?: habit.daysOfWeek,
            icon = args.optionalString("icon") ?: habit.icon,
            color = args.color() ?: habit.color,
            allowExceed = args.optBool("allowExceed") ?: habit.allowExceed,
        )
        habitRepository.save(updated)
        return "Hábito '${updated.name}' atualizado."
    }

    private suspend fun deleteHabit(args: ToolArgs): String {
        val habit = findHabit(args.string("id"))
        habitRepository.delete(habit.id)
        connectionRepository.clearForHabit(habit.id)
        gamificationEngine.reconcile()
        return "Hábito '${habit.name}' excluído com as marcações e conexões."
    }

    private suspend fun markHabit(args: ToolArgs, unmark: Boolean): String {
        val habit = findHabit(args.string("id"))
        val date = args.optionalDate("date") ?: LocalDate.now()
        if (unmark) {
            habitRepository.undo(habit.id, date)
            gamificationEngine.onHabitChanged(habit.id, date)
            return "Marcação de '${habit.name}' removida em $date."
        }
        val delta = (args.optInt("delta") ?: 1).let { if (it == 0) 1 else kotlin.math.abs(it) }
        habitRepository.complete(habit, date, delta)
        connectionEngine.onHabitDone(habit.id, date)
        gamificationEngine.onHabitChanged(habit.id, date)
        return "Hábito '${habit.name}' marcado em $date (+$delta)."
    }

    // ---------------- Agenda ----------------

    private suspend fun listEvents(args: ToolArgs): String {
        val from = args.optionalDate("from") ?: LocalDate.now()
        val to = args.optionalDate("to") ?: from.plusDays(30)
        val events = eventRepository.events.first()
            .filter { event ->
                val start = event.start ?: return@filter false
                val end = event.end ?: start
                !end.isBefore(from) && !start.isAfter(to)
            }
            .sortedBy { it.startDate }
        if (events.isEmpty()) return "Nenhum evento entre $from e $to."
        return events.joinToString("\n") { event ->
            "- ${event.id} | ${event.title} | ${event.startDate}" +
                (if (event.endDate != event.startDate) " a ${event.endDate}" else "") +
                (event.startTime?.let { " | $it" } ?: " | dia inteiro") +
                (event.endTime?.let { "-$it" } ?: "") +
                " | ${event.kind.name.lowercase()} | suspensão ${event.suspension.name.lowercase()}"
        }
    }

    private suspend fun createEvent(args: ToolArgs): String {
        val start = args.date("startDate")
        val kind = args.eventKind() ?: EventKind.Planned
        val explicitSuspension = args.suspension() ?: SuspensionMode.None
        val event = AgendaEvent(
            title = args.string("title"),
            description = args.optionalString("description"),
            startDate = start.toString(),
            endDate = (args.optionalDate("endDate") ?: start).toString(),
            startTime = args.time("startTime"),
            endTime = args.time("endTime"),
            color = args.color() ?: if (kind == EventKind.Unforeseen) EventColorKey.Orange else EventColorKey.Blue,
            // Imprevisto suspende sempre, no horário dele (mesma regra do formulário).
            suspension = if (kind == EventKind.Unforeseen) SuspensionMode.WithinHours else explicitSuspension,
            kind = kind,
        )
        eventRepository.save(event)
        return "Evento '${event.title}' criado (id ${event.id})."
    }

    private suspend fun updateEvent(args: ToolArgs): String {
        val event = findEvent(args.string("id"))
        val kind = args.eventKind() ?: event.kind
        val updated = event.copy(
            title = args.optionalString("title") ?: event.title,
            description = if (args.has("description")) args.optionalString("description") else event.description,
            startDate = args.optionalDate("startDate")?.toString() ?: event.startDate,
            endDate = args.optionalDate("endDate")?.toString() ?: event.endDate,
            startTime = if (args.has("startTime")) args.time("startTime") else event.startTime,
            endTime = if (args.has("endTime")) args.time("endTime") else event.endTime,
            color = args.color() ?: event.color,
            suspension = if (kind == EventKind.Unforeseen) {
                SuspensionMode.WithinHours
            } else {
                args.suspension() ?: event.suspension
            },
            kind = kind,
        )
        eventRepository.save(updated)
        return "Evento '${updated.title}' atualizado."
    }

    private suspend fun deleteEvent(args: ToolArgs): String {
        val event = findEvent(args.string("id"))
        eventRepository.delete(event.id)
        return "Evento '${event.title}' excluído."
    }

    private suspend fun createExtra(args: ToolArgs): String {
        val date = args.date("date")
        val extra = ExtraBlock(
            date = date.toString(),
            routineId = args.optionalString("routineId"),
            title = args.string("title"),
            description = args.optionalString("description"),
            startTime = args.time("startTime") ?: "08:00",
            endTime = args.time("endTime") ?: "09:00",
            isAllDay = args.bool("allDay", false),
            color = args.color() ?: EventColorKey.Blue,
            confirmation = args.confirmation() ?: EventConfirmation.None,
            scoreMax = (args.optInt("scoreMax") ?: 5).coerceIn(2, 10),
        )
        extraRepository.save(extra)
        return "Bloco avulso '${extra.title}' criado em $date (id ${extra.id})."
    }

    private suspend fun updateExtra(args: ToolArgs): String {
        val extra = findExtra(args.string("id"))
        val updated = extra.copy(
            title = args.optionalString("title") ?: extra.title,
            description = if (args.has("description")) args.optionalString("description") else extra.description,
            startTime = args.time("startTime") ?: extra.startTime,
            endTime = args.time("endTime") ?: extra.endTime,
            isAllDay = args.optBool("allDay") ?: extra.isAllDay,
            color = args.color() ?: extra.color,
            confirmation = args.confirmation() ?: extra.confirmation,
            scoreMax = (args.optInt("scoreMax") ?: extra.scoreMax).coerceIn(2, 10),
        )
        extraRepository.save(updated)
        return "Bloco avulso '${updated.title}' atualizado."
    }

    private suspend fun deleteExtra(args: ToolArgs): String {
        val extra = findExtra(args.string("id"))
        extraRepository.delete(extra.id)
        completionRepository.clearForBlock(extra.id)
        return "Bloco avulso '${extra.title}' excluído."
    }

    // ---------------- Conexões ----------------

    private suspend fun listConnections(): String {
        val connections = connectionRepository.connections.first()
        if (connections.isEmpty()) return "Nenhuma conexão cadastrada."
        val routines = routineRepository.routines.first()
        val tasks = taskRepository.tasks.first()
        val subtasks = subtaskRepository.subtasks.first()
        val habits = habitRepository.habitList.first()
        return connections.joinToString("\n") { link ->
            val routine = routines.firstOrNull { it.id == link.routineId }
            val block = routine?.blocks?.firstOrNull { it.id == link.blockId }
            val entity = when {
                link.taskId != null -> "tarefa " + (tasks.firstOrNull { it.id == link.taskId }?.title ?: link.taskId)
                link.subtaskId != null -> "sub-tarefa " +
                    (subtasks.firstOrNull { it.id == link.subtaskId }?.title ?: link.subtaskId)
                link.habitId != null -> "hábito " + (habits.firstOrNull { it.id == link.habitId }?.name ?: link.habitId)
                else -> "sem entidade"
            }
            "- ${link.id} | ${routine?.name ?: "?"} / ${block?.title ?: link.blockId} → $entity | " +
                "meta ${link.requiredCount} | filtro ${link.dayFilter}"
        }
    }

    private suspend fun createConnection(args: ToolArgs): String {
        val routine = findRoutine(args.string("routineId"))
        val block = findBlock(routine, args.string("blockId"))
        val taskId = args.optionalString("taskId")
        val subtaskId = args.optionalString("subtaskId")
        val habitId = args.optionalString("habitId")
        val chosen = listOfNotNull(taskId, subtaskId, habitId)
        if (chosen.size != 1) {
            throw ToolFailure("informe exatamente uma entidade: taskId, subtaskId ou habitId")
        }
        taskId?.let { findTask(it) }
        subtaskId?.let { findSubtask(it) }
        habitId?.let { findHabit(it) }
        val link = Connection(
            routineId = routine.id,
            blockId = block.id,
            taskId = taskId,
            subtaskId = subtaskId,
            habitId = habitId,
            requiredCount = (args.optInt("requiredCount") ?: 1).coerceIn(1, 99),
            dayFilter = args.optionalString("dayFilter") ?: Connection.ALL_DAYS,
        )
        connectionRepository.save(link)
        return "Conexão criada entre '${block.title}' e a entidade (id ${link.id})."
    }

    private suspend fun updateConnection(args: ToolArgs): String {
        val link = findConnection(args.string("id"))
        val updated = link.copy(
            requiredCount = (args.optInt("requiredCount") ?: link.requiredCount).coerceIn(1, 99),
            dayFilter = args.optionalString("dayFilter") ?: link.dayFilter,
        )
        connectionRepository.save(updated)
        return "Conexão atualizada (meta ${updated.requiredCount})."
    }

    private suspend fun deleteConnection(args: ToolArgs): String {
        val link = findConnection(args.string("id"))
        connectionRepository.delete(link.id)
        return "Conexão excluída."
    }

    private suspend fun applyConnection(args: ToolArgs): String {
        val link = findConnection(args.string("id"))
        connectionEngine.applyNow(link)
        gamificationEngine.reconcile()
        return "Conexão aplicada agora."
    }

    // ---------------- Configurações e memória ----------------

    private suspend fun getSettings(): String {
        val settings = settingsRepository.settings.first()
        val sort = settings.taskSort
        return buildString {
            appendLine("Nome do usuário: ${settings.userName.ifBlank { "(vazio)" }}")
            appendLine("Tema: ${settings.themeMode.name.lowercase()} | Cor: ${settings.accent.name.lowercase()}")
            appendLine("Addons ligados: ${settings.enabledAddons.joinToString { it.name.lowercase() }.ifBlank { "nenhum" }}")
            append("Ordenação de tarefas: ${if (sort.groupByUrgency) "agrupada por urgência" else "lista única"}")
        }
    }

    private suspend fun updateSettings(args: ToolArgs): String {
        args.optionalString("userName")?.let { settingsRepository.setUserName(it) }
        args.theme()?.let { settingsRepository.setThemeMode(it) }
        args.accent()?.let { settingsRepository.setAccent(it) }
        return "Configurações atualizadas."
    }

    private suspend fun setAddon(args: ToolArgs): String {
        val raw = args.string("addon").lowercase()
        val addon = when (raw) {
            "connections", "conexoes", "conexões" -> Addon.Connections
            "ai", "ia" -> Addon.Ai
            "gamification", "gamificacao", "gamificação" -> Addon.Gamification
            else -> throw ToolFailure("addon inválido: use connections, ai ou gamification")
        }
        val enabled = args.bool("enabled", true)
        settingsRepository.setAddonEnabled(addon, enabled)
        return "Addon ${addon.name.lowercase()} ${if (enabled) "ligado" else "desligado"}."
    }

    private suspend fun stats(): String {
        val stats = gamificationStats(
            routines = routineRepository.routines.first(),
            completions = completionRepository.completions.first(),
            tasks = taskRepository.tasks.first(),
            subtasks = subtaskRepository.subtasks.first(),
            habits = habitRepository.habitList.first(),
            habitCompletions = habitRepository.completionList.first(),
            connections = connectionRepository.connections.first(),
            xpEvents = xpRepository.all(),
            today = LocalDate.now(),
            agendaEvents = eventRepository.events.first(),
        )
        return "Nível ${stats.level} | ${stats.totalXp} XP | ${stats.days100} dias 100% | " +
            "sequência atual ${stats.currentRoutineStreak} (recorde ${stats.bestRoutineStreak}) | " +
            "${stats.tasksDone} tarefas concluídas | ${stats.blocksConfirmed} blocos confirmados | " +
            "${stats.goodHabitsActive} hábitos bons"
    }

    private suspend fun listMemory(): String {
        val entries = memoryRepository.all()
        if (entries.isEmpty()) return "A memória está vazia."
        return entries.joinToString("\n") { "- ${it.id} | ${it.text}" }
    }

    private suspend fun addMemory(args: ToolArgs): String {
        val entry = memoryRepository.add(args.string("text"))
        return "Guardei na memória (id ${entry.id})."
    }

    private suspend fun updateMemory(args: ToolArgs): String {
        val text = args.string("text")
        val id = args.string("id")
        if (memoryRepository.all().none { it.id == id }) throw ToolFailure("memória não encontrada: $id")
        memoryRepository.update(id, text)
        return "Memória atualizada."
    }

    private suspend fun deleteMemory(args: ToolArgs): String {
        val id = args.string("id")
        if (memoryRepository.all().none { it.id == id }) throw ToolFailure("memória não encontrada: $id")
        memoryRepository.delete(id)
        return "Memória apagada."
    }

    // ---------------- Buscas e avisos ----------------

    private suspend fun findRoutine(id: String): Routine =
        routineRepository.routines.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("rotina não encontrada: $id")

    private fun findBlock(routine: Routine, blockId: String): RoutineBlock =
        routine.blocks.firstOrNull { it.id == blockId }
            ?: throw ToolFailure("bloco não encontrado na rotina '${routine.name}': $blockId")

    private suspend fun findTask(id: String): Task =
        taskRepository.tasks.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("tarefa não encontrada: $id")

    private suspend fun findSubtask(id: String): Subtask =
        subtaskRepository.subtasks.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("sub-tarefa não encontrada: $id")

    private suspend fun findHabit(id: String): Habit =
        habitRepository.habitList.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("hábito não encontrado: $id")

    private suspend fun findEvent(id: String): AgendaEvent =
        eventRepository.events.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("evento não encontrado: $id")

    private suspend fun findExtra(id: String): ExtraBlock =
        extraRepository.blocks.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("bloco avulso não encontrado: $id")

    private suspend fun findConnection(id: String): Connection =
        connectionRepository.connections.first().firstOrNull { it.id == id }
            ?: throw ToolFailure("conexão não encontrada: $id")

    /** Mesmos avisos que a tela dá ao concluir/reabrir uma tarefa. */
    private suspend fun notifyTask(taskId: String, done: Boolean) {
        if (done) connectionEngine.onTaskDone(taskId)
        gamificationEngine.onTaskChanged(taskId)
    }

    private fun formatWeekdays(block: RoutineBlock): String {
        val days = block.scheduledWeekdays
        return if (days.size == 7) "todos os dias" else days.joinToString(",")
    }
}

/**
 * Ferramentas que o executor sabe rodar.
 *
 * Tem que ser **igual** ao catálogo [AI_TOOLS]: o teste compara as duas listas para
 * uma ferramenta nova no prompt não existir só no papel.
 */
val AI_EXECUTED_TOOLS: Set<String> = setOf(
    "routine.list", "routine.create", "routine.update", "routine.activate",
    "routine.duplicate", "routine.delete",
    "block.create", "block.update", "block.delete", "block.confirm", "block.unconfirm",
    "block.skipDay", "block.restoreDay",
    "task.list", "task.create", "task.update", "task.complete", "task.delete",
    "subtask.list", "subtask.create", "subtask.update", "subtask.complete", "subtask.delete",
    "habit.list", "habit.create", "habit.update", "habit.delete", "habit.mark", "habit.unmark",
    "event.list", "event.create", "event.update", "event.delete",
    "extra.create", "extra.update", "extra.delete",
    "connection.list", "connection.create", "connection.update", "connection.delete",
    "connection.apply",
    "settings.get", "settings.update", "addon.set", "gamification.stats",
    "memory.list", "memory.add", "memory.update", "memory.delete",
)

/**
 * Argumentos da ação com conversão tolerante.
 *
 * Modelo pequeno manda `"priority": "3"`, `"allDay": "sim"`, `"weekdays": [1,2]` ou
 * `"weekdays": "1,2"`. Aqui tudo isso vira o tipo certo; o que não dá, vira erro
 * legível em vez de exceção.
 */
private class ToolArgs(private val obj: JsonObject) {
    fun has(key: String): Boolean = obj.containsKey(key)

    fun optionalString(key: String): String? = when (val value = obj[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeUnless { it.isEmpty() || it == "null" }
        is JsonArray -> value.joinToString(",") {
            (it as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.takeUnless { it.isBlank() }
        else -> null
    }

    fun string(key: String): String = optionalString(key) ?: throw ToolFailure("faltou '$key'")

    fun optInt(key: String): Int? = when (val value = obj[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.trim()?.toIntOrNull()
            ?: throw ToolFailure("'$key' precisa ser número")
        else -> null
    }

    fun optBool(key: String): Boolean? = when ((obj[key] as? JsonPrimitive)?.contentOrNull?.lowercase()) {
        null, "null" -> null
        "true", "1", "sim", "yes" -> true
        "false", "0", "nao", "não", "no" -> false
        else -> throw ToolFailure("'$key' precisa ser true ou false")
    }

    fun bool(key: String, default: Boolean): Boolean = optBool(key) ?: default

    fun optionalDate(key: String): LocalDate? = optionalString(key)?.let { raw ->
        parseIsoDate(raw) ?: throw ToolFailure("'$key' precisa ser uma data AAAA-MM-DD (recebi '$raw')")
    }

    fun date(key: String): LocalDate = optionalDate(key) ?: throw ToolFailure("faltou '$key'")

    /** Aceita "HH:mm" ou "H:mm" e devolve sempre "HH:mm". */
    fun time(key: String): String? = optionalString(key)?.let { raw ->
        val parsed = parseIsoTime(raw)
            ?: runCatching { LocalTime.parse(raw, HmmFormatter) }.getOrNull()
            ?: throw ToolFailure("'$key' precisa ser um horário HH:mm")
        parsed.format(HHmmFormatter)
    }

    fun intList(key: String): List<Int>? = when (val value = obj[key]) {
        null, JsonNull -> null
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
        is JsonPrimitive -> value.contentOrNull
            ?.split(',', ' ', ';')
            ?.mapNotNull { it.trim().toIntOrNull() }
        else -> null
    }

    fun color(): EventColorKey? = optionalString("color")?.let { raw ->
        EventColorKey.entries.firstOrNull { it.name.equals(raw, true) }
            ?: throw ToolFailure("cor inválida: use ${EventColorKey.entries.joinToString { it.name.lowercase() }}")
    }

    fun confirmation(): EventConfirmation? = optionalString("confirmation")?.let { raw ->
        when (raw.lowercase()) {
            "none", "nenhuma" -> EventConfirmation.None
            "checklist" -> EventConfirmation.Checklist
            "score", "nota" -> EventConfirmation.Score
            else -> throw ToolFailure("confirmação inválida: use none, checklist ou score")
        }
    }

    fun frequency(): RoutineFrequency? = optionalString("frequency")?.let { raw ->
        when (raw.lowercase()) {
            "daily", "diaria", "diária" -> RoutineFrequency.Daily
            "weekly", "semanal" -> RoutineFrequency.Weekly
            else -> throw ToolFailure("frequência inválida: use daily ou weekly")
        }
    }

    fun duration(): RoutineDuration? = optionalString("duration")?.let { raw ->
        when (raw.lowercase()) {
            "indefinite", "indefinida" -> RoutineDuration.Indefinite
            "until", "ate", "até" -> RoutineDuration.Until
            else -> throw ToolFailure("duração inválida: use indefinite ou until")
        }
    }

    fun habitKind(): HabitKind? = optionalString("kind")?.let { raw ->
        when (raw.lowercase()) {
            "good", "bom", "boa" -> HabitKind.Good
            "bad", "ruim", "mau" -> HabitKind.Bad
            else -> throw ToolFailure("tipo de hábito inválido: use good ou bad")
        }
    }

    fun habitFrequency(): HabitFrequency? = optionalString("frequency")?.let { raw ->
        when (raw.lowercase()) {
            "daily", "diaria", "diária" -> HabitFrequency.Daily
            "weekly", "semanal" -> HabitFrequency.Weekly
            else -> throw ToolFailure("frequência inválida: use daily ou weekly")
        }
    }

    fun eventKind(): EventKind? = optionalString("kind")?.let { raw ->
        when (raw.lowercase()) {
            "event", "evento", "planned" -> EventKind.Planned
            "unforeseen", "imprevisto" -> EventKind.Unforeseen
            else -> throw ToolFailure("tipo de evento inválido: use event ou unforeseen")
        }
    }

    fun suspension(): SuspensionMode? = optionalString("suspension")?.let { raw ->
        when (raw.lowercase().replace("_", "")) {
            "none", "nenhuma", "nada" -> SuspensionMode.None
            "days", "dias", "wholedays" -> SuspensionMode.WholeDays
            "hours", "horario", "horário", "withinhours" -> SuspensionMode.WithinHours
            else -> throw ToolFailure("suspensão inválida: use none, days ou hours")
        }
    }

    fun theme(): ThemeMode? = optionalString("theme")?.let { raw ->
        when (raw.lowercase()) {
            "system", "sistema" -> ThemeMode.System
            "light", "claro" -> ThemeMode.Light
            "dark", "escuro" -> ThemeMode.Dark
            else -> throw ToolFailure("tema inválido: use system, light ou dark")
        }
    }

    fun accent(): AccentColor? = optionalString("accent")?.let { raw ->
        AccentColor.entries.firstOrNull { it.name.equals(raw, true) }
            ?: throw ToolFailure("cor inválida: use ${AccentColor.entries.joinToString { it.name.lowercase() }}")
    }
}

private val HHmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** Argumentos crus do modelo; JSON quebrado vira objeto vazio e o erro aparece no uso. */
private fun parseArgs(raw: String): JsonObject =
    runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
