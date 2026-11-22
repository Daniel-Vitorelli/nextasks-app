package com.daniel.nextasks.data

import androidx.annotation.StringRes
import com.daniel.nextasks.R
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Um lançamento de XP.
 *
 * `refKey` é a chave de idempotência: o mesmo evento (bloco X no período Y, tarefa
 * Z concluída) nunca pontua duas vezes, mesmo que a ação seja repetida ou o app
 * reabra. É o mesmo desenho do `XpEvent` do app web, onde a unicidade
 * (usuário, kind, refKey) mora no banco.
 */
@Serializable
data class XpEvent(
    val id: String = UUID.randomUUID().toString(),
    /** Valor cru de [XpKind.raw]; string para não quebrar com kind desconhecido. */
    val kind: String,
    val refKey: String,
    val amount: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val label: XpKind? get() = XpKind.fromRaw(kind)
}

/** Conquista desbloqueada. Guarda o dia para a lista mostrar "desde quando". */
@Serializable
data class AchievementUnlock(
    val achievementId: String,
    val unlockedOn: String,
)

/**
 * Tabela declarativa de XP — fonte única, espelhando `XP_AMOUNTS` do web.
 *
 * Mexer aqui muda o jogo inteiro: a aba "Como ganhar" lê estes valores.
 */
object XpAmounts {
    const val BLOCK_CHECKLIST = 10
    const val BLOCK_SCORE_PER_POINT = 1
    const val TASK_DONE = 20
    const val SUBTASK_DONE = 10
    const val HABIT_CONFIRM = 5
    const val HABIT_TARGET_BONUS = 10
    const val HABIT_SLIP = -15
    const val ROUTINE_DAY_FULL = 25
}

/** Tipos de evento de XP, com rótulo para o histórico. */
enum class XpKind(val raw: String, @StringRes val label: Int) {
    BlockConfirm("block.confirm", R.string.xp_kind_block),
    TaskDone("task.done", R.string.xp_kind_task),
    SubtaskDone("subtask.done", R.string.xp_kind_subtask),
    HabitConfirm("habit.confirm", R.string.xp_kind_habit),
    HabitTarget("habit.target", R.string.xp_kind_habit_target),
    HabitSlip("habit.slip", R.string.xp_kind_habit_slip),
    RoutineDayFull("routine.dayFull", R.string.xp_kind_day_full),
    Achievement("achievement", R.string.xp_kind_achievement),
    ;

    companion object {
        fun fromRaw(raw: String): XpKind? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * XP de um bloco confirmado, conforme o modo de confirmação.
 *
 * Checklist vale o valor cheio; nota vale por ponto; bloco sem confirmação não
 * pontua (não há o que confirmar).
 */
fun blockXp(confirmation: EventConfirmation, value: String): Int = when (confirmation) {
    EventConfirmation.Checklist -> if (value == "true") XpAmounts.BLOCK_CHECKLIST else 0
    EventConfirmation.Score -> (value.toIntOrNull() ?: 0).coerceAtLeast(0) *
        XpAmounts.BLOCK_SCORE_PER_POINT

    EventConfirmation.None -> 0
}

/** Uma confirmação de bloco conta como feita? (mesma regra do calendário) */
fun completionCounts(confirmation: EventConfirmation, value: String): Boolean = when (confirmation) {
    EventConfirmation.Checklist -> value == "true"
    EventConfirmation.Score -> (value.toIntOrNull() ?: 0) >= 1
    EventConfirmation.None -> false
}
