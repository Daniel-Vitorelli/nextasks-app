package com.daniel.nextasks.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Bloco avulso: existe em **um dia só** e não faz parte de nenhuma rotina.
 *
 * Pode ficar solto ([routineId] nulo) ou vinculado a uma rotina — o vínculo serve
 * para exibir o nome dela e para o bloco entrar no progresso do dia. Em nenhum
 * dos casos a rotina é alterada: a tela de Rotinas continua igual.
 */
@Serializable
data class ExtraBlock(
    val id: String = UUID.randomUUID().toString(),
    /** Dia do bloco, em ISO (yyyy-MM-dd). */
    val date: String,
    val routineId: String? = null,
    val title: String,
    val description: String? = null,
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val isAllDay: Boolean = false,
    val color: EventColorKey = EventColorKey.Blue,
    val confirmation: EventConfirmation = EventConfirmation.None,
    val scoreMax: Int = 5,
) {
    /**
     * Vira um [RoutineBlock] para reaproveitar o formulário e o cálculo de horários.
     * O `id` é o mesmo do bloco avulso, então as conclusões ficam ligadas a ele.
     */
    fun toRoutineBlock(): RoutineBlock = RoutineBlock(
        id = id,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        color = color,
        weekday = runCatching { java.time.LocalDate.parse(date).dayOfWeek.value % 7 }.getOrDefault(1),
        // Bloco avulso só existe no dia dele, então basta o `weekday`
        // (`scheduledWeekdays` cai nele). Gravar os 7 dias aqui mentiria sobre o modelo.
        weekdays = emptyList(),
        confirmation = confirmation,
        scoreMax = scoreMax,
    )

    companion object {
        fun from(block: RoutineBlock, date: String, routineId: String?): ExtraBlock = ExtraBlock(
            id = block.id,
            date = date,
            routineId = routineId,
            title = block.title,
            description = block.description,
            startTime = block.startTime,
            endTime = block.endTime,
            isAllDay = block.isAllDay,
            color = block.color,
            confirmation = block.confirmation,
            scoreMax = block.scoreMax,
        )
    }
}
