package com.daniel.nextasks.data

/**
 * Conclusão de tarefa com a cascata das sub-tarefas.
 *
 * A regra "concluir a tarefa conclui todas as sub-tarefas" mora aqui, num ponto só,
 * porque antes cada tela repetia a cascata por conta própria — e duas esqueciam:
 * o checkbox da Home e o motor de Conexões. O resultado era uma tarefa concluída
 * com sub-tarefas pendentes, dependendo de onde o usuário tocou.
 *
 * Reabrir a tarefa reabre só ela; as sub-tarefas ficam como estavam (mesma
 * semântica do app web, onde reabrir um nó é que puxa os ancestrais).
 */
class TaskCompletion(
    private val taskRepository: TaskRepository,
    private val subtaskRepository: SubtaskRepository,
) {
    /** Marca/desmarca a tarefa e, ao concluir, conclui a árvore de sub-tarefas. */
    suspend fun setDone(taskId: String, done: Boolean) {
        taskRepository.setDone(taskId, done)
        if (done) subtaskRepository.completeAllForTask(taskId)
    }
}
