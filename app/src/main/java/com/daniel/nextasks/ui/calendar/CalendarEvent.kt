package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.daniel.nextasks.data.EventConfirmation
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Cores de evento do NexTasks — mesmos tokens `--event-*` do app web
 * (frontend/app/[locale]/globals.css), com variante para tema escuro.
 */
enum class EventColor(
    private val light: Color,
    private val dark: Color,
    private val lightContainer: Color,
    private val darkContainer: Color,
) {
    Red(Color(0xFFB23E31), Color(0xFFD85140), Color(0xFFFFE1D4), Color(0xFF6C2920)),
    Orange(Color(0xFFAD4517), Color(0xFFE07040), Color(0xFFFFE3C8), Color(0xFF5F3524)),
    Yellow(Color(0xFF855100), Color(0xFFD4A83A), Color(0xFFFFF0BE), Color(0xFF4E3F1B)),
    Green(Color(0xFF005939), Color(0xFF59B291), Color(0xFFD0FAE7), Color(0xFF002819)),
    Blue(Color(0xFF006CBF), Color(0xFF4895C8), Color(0xFFE2F9FF), Color(0xFF2F4656)),
    Purple(Color(0xFF7733D2), Color(0xFF8A5AD6), Color(0xFFFEECFF), Color(0xFF483A63)),
    Gray(Color(0xFF5F6061), Color(0xFFA0A0A0), Color(0xFFF6F4F3), Color(0xFF424242)),
    ;

    /** Cor do marcador do evento. */
    @Composable
    fun accent(): Color = if (isSystemInDarkTheme()) dark else light

    /** Cor de fundo (chips de dia inteiro). */
    @Composable
    fun container(): Color = if (isSystemInDarkTheme()) darkContainer else lightContainer
}

/**
 * Evento exibido no calendário.
 *
 * Espelha o `TimeBlock` do app web (title, start, end, isAllDay, color);
 * `location` é um extra de exibição. Os campos de [blockId] em diante ligam o
 * evento ao bloco de origem, permitindo registrar a conclusão.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val location: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val isAllDay: Boolean = false,
    val color: EventColor = EventColor.Blue,
    /** Descrição do bloco de origem, quando houver. */
    val description: String? = null,
    val blockId: String? = null,
    val routineId: String? = null,
    val confirmation: EventConfirmation = EventConfirmation.None,
    /** Escala da nota quando a confirmação é Score. */
    val scoreMax: Int = 5,
    /** Dia (rotina diária) ou domingo da semana (rotina semanal) da conclusão. */
    val completionPeriodStart: LocalDate? = null,
    /** A ocorrência foi alterada/excluída só neste dia (exceção da rotina). */
    val isOverridden: Boolean = false,
    /** Bloco avulso criado no calendário, fora da rotina. */
    val isExtra: Boolean = false,
    /** Fatia de um evento (pode durar vários dias). */
    val isEvent: Boolean = false,
    /** Id do evento de origem, quando [isEvent]. */
    val eventId: String? = null,
)
