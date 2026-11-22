package com.daniel.nextasks.data

/**
 * Regras da árvore de sub-tarefas, iguais às do app web
 * (`lib/server/subtask-cascade.ts`):
 *
 * - concluir um nó conclui toda a sub-árvore abaixo e sobe enquanto **todos**
 *   os filhos estiverem concluídos;
 * - reabrir um nó reabre a cadeia de ancestrais e a tarefa;
 * - excluir remove a sub-árvore e recalcula os ancestrais (um pai sem filhos
 *   conta como concluído, como no original);
 * - a tarefa só fica concluída quando todas as raízes estiverem concluídas.
 */
data class SubtaskCascade(
    val subtasks: List<Subtask>,
    /** Estado que a tarefa deve assumir depois da operação. */
    val taskDone: Boolean,
)

private fun List<Subtask>.childrenOf(parentId: String?): List<Subtask> =
    filter { it.parentId == parentId }

private fun List<Subtask>.descendantIds(id: String): Set<String> {
    val result = mutableSetOf<String>()
    val stack = childrenOf(id).map { it.id }.toMutableList()
    while (stack.isNotEmpty()) {
        val current = stack.removeAt(stack.lastIndex)
        if (result.add(current)) {
            stack += childrenOf(current).map { it.id }
        }
    }
    return result
}

private fun List<Subtask>.ancestorIds(id: String): List<String> {
    val result = mutableListOf<String>()
    var parent = firstOrNull { it.id == id }?.parentId
    while (parent != null) {
        result += parent
        parent = firstOrNull { it.id == parent }?.parentId
    }
    return result
}

private fun List<Subtask>.taskDoneFor(taskId: String): Boolean {
    val roots = filter { it.taskId == taskId && it.parentId == null }
    return roots.isNotEmpty() && roots.all { it.done }
}

fun completeSubtask(list: List<Subtask>, id: String): SubtaskCascade {
    val node = list.firstOrNull { it.id == id } ?: return SubtaskCascade(list, false)
    val doneIds = mutableSetOf(id)
    doneIds += list.descendantIds(id)

    var parent = node.parentId
    while (parent != null) {
        val children = list.childrenOf(parent)
        // Precisa contar o que já estava concluído antes desta operação.
        if (children.isNotEmpty() && children.all { it.id in doneIds || it.done }) {
            doneIds += parent
            parent = list.firstOrNull { it.id == parent }?.parentId
        } else {
            break
        }
    }

    val updated = list.map { if (it.id in doneIds) it.copy(done = true) else it }
    return SubtaskCascade(updated, updated.taskDoneFor(node.taskId))
}

fun reopenSubtask(list: List<Subtask>, id: String): SubtaskCascade {
    val node = list.firstOrNull { it.id == id } ?: return SubtaskCascade(list, false)
    val undoIds = setOf(id) + list.ancestorIds(id)
    val updated = list.map { if (it.id in undoIds) it.copy(done = false) else it }
    return SubtaskCascade(updated, taskDone = false)
}

fun deleteSubtask(list: List<Subtask>, id: String): SubtaskCascade {
    val node = list.firstOrNull { it.id == id } ?: return SubtaskCascade(list, false)
    val removeIds = setOf(id) + list.descendantIds(id)
    var remaining = list.filterNot { it.id in removeIds }

    // Recalcula a cadeia acima: pai sem filhos conta como concluído.
    var parent = node.parentId
    while (parent != null && remaining.any { it.id == parent }) {
        val children = remaining.childrenOf(parent)
        if (children.isEmpty() || children.all { it.done }) {
            remaining = remaining.map {
                if (it.id == parent) it.copy(done = true) else it
            }
            parent = remaining.firstOrNull { it.id == parent }?.parentId
        } else {
            break
        }
    }

    return SubtaskCascade(remaining, remaining.taskDoneFor(node.taskId))
}

/** Nó + profundidade, na ordem em que a árvore deve ser exibida. */
data class SubtaskNode(
    val subtask: Subtask,
    val depth: Int,
    val hasChildren: Boolean = false,
)

/**
 * Achata a árvore para exibição, respeitando os nós recolhidos: os filhos de um
 * nó recolhido não entram na lista.
 */
fun flattenSubtaskTree(
    list: List<Subtask>,
    taskId: String,
    collapsed: Set<String> = emptySet(),
): List<SubtaskNode> {
    val result = mutableListOf<SubtaskNode>()

    fun visit(parentId: String?, depth: Int) {
        list.filter { it.taskId == taskId && it.parentId == parentId }
            .sortedBy { it.createdAt }
            .forEach { node ->
                val hasChildren = list.any { it.parentId == node.id }
                result += SubtaskNode(node, depth, hasChildren)
                if (node.id !in collapsed) visit(node.id, depth + 1)
            }
    }

    visit(null, 0)
    return result
}
