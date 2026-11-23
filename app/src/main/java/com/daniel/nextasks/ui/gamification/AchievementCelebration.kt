package com.daniel.nextasks.ui.gamification

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Achievement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Celebração de conquista: cartão com mola, medalha crescendo e uma explosão de
 * confete atrás. Aparece em qualquer tela porque o motor emite num canal lido
 * pelo `NexTasksApp`.
 *
 * Sem som de propósito: o app não tem assets de áudio e um efeito genérico soaria
 * pior que o silêncio. O haptic faz o "peso" do momento.
 */
@Composable
fun AchievementCelebration(achievement: Achievement, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cardScale = remember { Animatable(0.72f) }
    val iconScale = remember { Animatable(0.35f) }
    val burst = remember { Animatable(0f) }
    val color = tierColor(achievement.tier)
    // O toque que abriu a tela pode cair no diálogo recém-criado e fechá-lo no
    // mesmo frame. Uma janela curta de carência evita esse dismiss acidental.
    var dismissible by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "celebrationPulse")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )

    var xpStarted by remember { mutableStateOf(false) }
    LaunchedEffect(achievement.id) {
        delay(280)
        xpStarted = true
    }
    val animatedXp by animateIntAsState(
        targetValue = if (xpStarted) achievement.tier.xp else 0,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "celebrationXp",
    )

    LaunchedEffect(achievement.id) {
        delay(450)
        dismissible = true
    }

    LaunchedEffect(achievement.id) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
            )
        }
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 240f),
            )
        }
        burst.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
        )
    }

    LaunchedEffect(achievement.id) {
        delay(4200)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (dismissible) onDismiss() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Confetti(
                progress = burst.value,
                accent = color,
                seed = achievement.id.hashCode(),
            )

            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        rotationZ = (1f - cardScale.value) * -14f
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, color.copy(alpha = 0.65f)),
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.celebration_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )

                    Spacer(Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .graphicsLayer {
                                scaleX = haloScale
                                scaleY = haloScale
                            }
                            .border(1.dp, color.copy(alpha = 0.28f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .graphicsLayer {
                                    scaleX = iconScale.value
                                    scaleY = iconScale.value
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.16f))
                                    .border(2.dp, color, CircleShape),
                            )
                            Icon(
                                painter = painterResource(achievement.category.icon),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = stringResource(achievement.name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = stringResource(achievement.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.celebration_reward, animatedXp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.celebration_tap),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val originX: Float,
    val originY: Float,
    val vx: Float,
    val vy: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
)

/**
 * Dois canhões nas laterais de baixo do cartão, atirando para cima e para fora.
 *
 * A primeira versão explodia do centro: metade da animação ficava escondida atrás
 * do próprio cartão. Saindo das bordas, o confete aparece desde o primeiro frame.
 */
@Composable
private fun Confetti(progress: Float, accent: Color, seed: Int) {
    val particles = remember(seed, accent) {
        val random = Random(seed)
        val palette = listOf(
            accent,
            Color(0xFFFFC107),
            Color(0xFF64B5F6),
            Color(0xFFE24B4A),
            Color(0xFF9FE1CB),
        )
        List(44) {
            val toLeft = random.nextBoolean()
            ConfettiParticle(
                originX = if (toLeft) -0.39f else 0.39f,
                originY = 0.46f,
                vx = (if (toLeft) -1f else 1f) * (0.35f + random.nextFloat() * 0.65f),
                vy = -(0.9f + random.nextFloat() * 0.7f),
                speed = 0.45f + random.nextFloat() * 0.55f,
                size = 5f + random.nextFloat() * 7f,
                color = palette[random.nextInt(palette.size)],
                spin = random.nextFloat() * 360f,
            )
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension
        val reach = unit * 0.9f

        particles.forEach { particle ->
            val origin = Offset(
                x = center.x + particle.originX * unit,
                y = center.y + particle.originY * unit,
            )
            val travel = reach * particle.speed * progress
            val x = origin.x + particle.vx * travel
            val y = origin.y + particle.vy * travel + 300f * progress * progress
            val alpha = (1f - progress).coerceIn(0f, 1f)
            val side = particle.size * (1f - progress * 0.35f)

            rotate(degrees = particle.spin + progress * 540f, pivot = Offset(x, y)) {
                drawRect(
                    color = particle.color.copy(alpha = alpha),
                    topLeft = Offset(x - side / 2f, y - side / 2f),
                    size = Size(side, side * 0.55f),
                )
            }
        }
    }
}
