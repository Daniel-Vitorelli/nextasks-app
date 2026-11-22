package com.daniel.nextasks.ui.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import com.daniel.nextasks.ui.theme.NexTasksTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Pinça na grade do calendário, com toque de verdade.
 *
 * O gesto é a parte que não dá para cobrir em teste JVM: depende do despacho de
 * ponteiros do Compose e da briga com a rolagem vertical.
 */
class CalendarPinchTest {

    @get:Rule
    val rule = createComposeRule()

    private val day: LocalDate = LocalDate.of(2026, 11, 22)

    /** Monta a grade com zoom controlado, guardando o último valor aplicado. */
    private fun setGrid(onZoomChanged: (Float) -> Unit = {}) {
        rule.setContent {
            var zoom by remember { mutableFloatStateOf(1f) }
            NexTasksTheme {
                CalendarTimeGrid(
                    days = listOf(day),
                    events = emptyList(),
                    today = day,
                    selectedDate = day,
                    onSelectDay = {},
                    zoom = zoom,
                    onZoom = {
                        zoom = it
                        onZoomChanged(it)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("grade"),
                )
            }
        }
    }

    @Test
    fun pincaParaForaAumentaOZoom() {
        var last = 1f
        setGrid { last = it }

        rule.onNodeWithTag("grade").performTouchInput {
            pinch(
                start0 = center + Offset(-80f, 0f),
                end0 = center + Offset(-320f, 0f),
                start1 = center + Offset(80f, 0f),
                end1 = center + Offset(320f, 0f),
            )
        }
        rule.waitForIdle()

        assertTrue("pinça para fora deveria aumentar o zoom, veio $last", last > 1f)
    }

    @Test
    fun pincaParaDentroDiminuiOZoom() {
        var last = 1f
        setGrid { last = it }

        rule.onNodeWithTag("grade").performTouchInput {
            pinch(
                start0 = center + Offset(-320f, 0f),
                end0 = center + Offset(-80f, 0f),
                start1 = center + Offset(320f, 0f),
                end1 = center + Offset(80f, 0f),
            )
        }
        rule.waitForIdle()

        assertTrue("pinça para dentro deveria diminuir o zoom, veio $last", last < 1f)
    }

    @Test
    fun arrastoDeUmDedoNaoMudaOZoom() {
        var last = 1f
        setGrid { last = it }

        rule.onNodeWithTag("grade").performTouchInput { swipeUp() }
        rule.waitForIdle()

        assertEquals("um dedo é rolagem, não zoom", 1f, last, 0.001f)
    }
}
