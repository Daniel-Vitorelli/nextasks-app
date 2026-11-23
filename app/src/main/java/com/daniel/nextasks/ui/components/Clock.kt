package com.daniel.nextasks.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Hoje" que vira o dia sozinho.
 *
 * `remember { LocalDate.now() }` congela a data na primeira composição: o app
 * aberto depois da meia-noite continua mostrando o dia anterior — e no calendário
 * isso muda a janela de eventos, os grupos de tarefa e o "hoje" dos hábitos.
 * O valor é reavaliado a cada minuto; é barato e suficiente para uma virada de dia.
 */
@Composable
fun rememberToday(): LocalDate {
    val state = produceState(initialValue = LocalDate.now()) {
        while (true) {
            delay(60_000)
            val current = LocalDate.now()
            if (current != value) value = current
        }
    }
    return state.value
}

/** Hora atual que anda, para a linha do "agora" na grade do calendário. */
@Composable
fun rememberClockTime(): LocalTime {
    val state = produceState(initialValue = LocalTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalTime.now()
        }
    }
    return state.value
}
