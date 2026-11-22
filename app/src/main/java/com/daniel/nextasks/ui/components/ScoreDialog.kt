package com.daniel.nextasks.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import kotlin.math.roundToInt

/**
 * Diálogo de nota de um bloco: valor em destaque (tocável para digitar), slider na
 * cor do bloco e opção de remover o registro.
 *
 * Compartilhado entre a agenda do calendário e a Home.
 */
@Composable
internal fun ScoreDialog(
    initial: Int,
    max: Int,
    color: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember { mutableFloatStateOf(if (initial > 0) initial.toFloat() else 1f) }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val current = value.roundToInt()

    fun commitTyped() {
        val parsed = text.toIntOrNull()
        if (parsed != null) {
            value = parsed.coerceIn(1, max).toFloat()
        }
        editing = false
    }

    LaunchedEffect(editing) {
        if (editing) {
            // Se o campo ainda não estiver posicionado, o usuário toca nele.
            runCatching { focusRequester.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.completion_score_title), color = color) },
        text = {
            Column {
                if (editing) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input ->
                            text = input.filter { it.isDigit() }.take(4)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                        modifier = Modifier
                            .width(150.dp)
                            .focusRequester(focusRequester),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.completion_score_value, current, max),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                text = current.toString()
                                editing = true
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = value,
                    onValueChange = { newValue ->
                        editing = false
                        value = newValue.roundToInt().toFloat()
                    },
                    valueRange = 1f..max.toFloat(),
                    // Até 10 a escala mostra os traços; acima disso o slider é
                    // contínuo e arredondamos para inteiro, senão as marcas viram ruído.
                    steps = if (max <= 10) (max - 2).coerceAtLeast(0) else 0,
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        activeTickColor = if (color.luminance() > 0.55f) Color.Black else Color.White,
                    ),
                )
                if (initial > 0) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.completion_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editing) commitTyped()
                    onConfirm(value.roundToInt())
                },
            ) {
                Text(stringResource(R.string.routine_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routine_cancel))
            }
        },
    )
}
