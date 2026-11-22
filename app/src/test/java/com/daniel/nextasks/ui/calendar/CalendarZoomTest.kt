package com.daniel.nextasks.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ancoragem do zoom: o horário que estava sob os dedos não pode escapar.
 *
 * É a conta que faz a pinça parecer natural — sem ela, o conteúdo "foge" do dedo
 * e o usuário precisa rolar depois de cada zoom.
 */
class CalendarZoomTest {

    @Test
    fun `zoom no meio da tela mantem o horario sob o dedo`() {
        // 100 px por hora, topo da lista, dedo a 250 px => 2h30 no conteúdo.
        // Dobrando a altura, essas 2h30 passam a ocupar 500 px; menos os 250 px
        // do dedo, a rolagem precisa ir para 250.
        assertEquals(250, zoomedScrollOffset(0, 100f, 200f, 250f))
    }

    @Test
    fun `dedo no topo nao rola nada`() {
        assertEquals(0, zoomedScrollOffset(0, 100f, 200f, 0f))
    }

    @Test
    fun `reduzir o zoom recalcula a rolagem para menos`() {
        // 200 px/h, rolagem 1000, dedo a 300 => 6h30 no conteúdo.
        // Metade da altura: 6h30 valem 650 px; menos os 300 do dedo => 350.
        assertEquals(350, zoomedScrollOffset(1000, 200f, 100f, 300f))
    }

    @Test
    fun `resultado nunca e negativo`() {
        // Conteúdo curto e dedo baixo: a conta daria -250, mas rolagem não existe negativa.
        assertEquals(0, zoomedScrollOffset(0, 100f, 50f, 500f))
    }

    @Test
    fun `altura invalida nao mexe na rolagem`() {
        assertEquals(42, zoomedScrollOffset(42, 0f, 100f, 10f))
    }

    @Test
    fun `zoom sem mudanca de altura devolve a mesma posicao`() {
        assertEquals(700, zoomedScrollOffset(700, 120f, 120f, 400f))
    }
}
