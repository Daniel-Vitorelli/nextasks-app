package com.daniel.nextasks.ui.gamification

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.ACHIEVEMENTS
import com.daniel.nextasks.data.Achievement
import com.daniel.nextasks.data.AchievementCategory
import com.daniel.nextasks.data.AchievementCondition
import com.daniel.nextasks.data.AchievementRepository
import com.daniel.nextasks.data.AchievementTier
import com.daniel.nextasks.data.AchievementUnlock
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.GamificationEngine
import com.daniel.nextasks.data.GamificationStat
import com.daniel.nextasks.data.GamificationStats
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.Rank
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.XpAmounts
import com.daniel.nextasks.data.XpEvent
import com.daniel.nextasks.data.XpKind
import com.daniel.nextasks.data.XpRepository
import com.daniel.nextasks.data.gamificationStats
import com.daniel.nextasks.data.levelProgress
import com.daniel.nextasks.data.nextRank
import com.daniel.nextasks.data.progress
import com.daniel.nextasks.data.progressDetail
import com.daniel.nextasks.data.rankFor
import com.daniel.nextasks.data.valueOf
import com.daniel.nextasks.ui.components.rememberToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class Tab { Achievements, History, How }
private enum class AchievementFilter { All, Unlocked, Locked }

/** Quantos lançamentos o histórico mostra antes do "mostrar mais". */
private const val HistoryPage = 30

/**
 * Página de gamificação: nível, rank, números, conquistas e histórico de XP.
 *
 * Todos os números vêm de [gamificationStats] sobre os dados reais — não existe
 * contador paralelo para sair do lugar. O XP em si mora no histórico
 * (`xp_events.json`), e é ele que define o nível.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GamificationScreen(
    gamification: GamificationEngine,
    xpRepository: XpRepository,
    achievementRepository: AchievementRepository,
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    taskRepository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    habitRepository: HabitRepository,
    connectionRepository: ConnectionRepository,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val events by xpRepository.events.collectAsState(initial = emptyList())
    val unlocks by achievementRepository.unlocks.collectAsState(initial = emptyList())
    val routines by routineRepository.routines.collectAsState(initial = emptyList())
    val completions by completionRepository.completions.collectAsState(initial = emptyList())
    val tasks by taskRepository.tasks.collectAsState(initial = emptyList())
    val subtasks by subtaskRepository.subtasks.collectAsState(initial = emptyList())
    val habits by habitRepository.habitList.collectAsState(initial = emptyList())
    val habitCompletions by habitRepository.completionList.collectAsState(initial = emptyList())
    val connections by connectionRepository.connections.collectAsState(initial = emptyList())

    val today = rememberToday()
    val stats = remember(routines, completions, tasks, subtasks, habits, habitCompletions, connections, events) {
        gamificationStats(
            routines = routines,
            completions = completions,
            tasks = tasks,
            subtasks = subtasks,
            habits = habits,
            habitCompletions = habitCompletions,
            connections = connections,
            xpEvents = events,
            today = today,
        )
    }

    // Abrir a tela também serve de conserto: se algo pontuou fora das telas
    // (conexão concluindo tarefa, backup restaurado), o XP se ajusta aqui.
    LaunchedEffect(Unit) { gamification.reconcile() }

    var tab by rememberSaveable { mutableStateOf(Tab.Achievements) }
    var filter by rememberSaveable { mutableStateOf(AchievementFilter.All) }
    var detail by remember { mutableStateOf<Achievement?>(null) }
    var showRanks by remember { mutableStateOf(false) }

    val unlockedIds = remember(unlocks) { unlocks.map { it.achievementId }.toSet() }
    val level = levelProgress(stats.totalXp).level
    // "Semana" aqui é os últimos 7 dias: mostra o ritmo recente, não o acumulado.
    val weekStart = remember(today) {
        today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val weekXp = remember(events, weekStart) {
        events.filter { it.createdAt >= weekStart }.sumOf { it.amount }
    }
    val almost = remember(stats, unlockedIds) {
        ACHIEVEMENTS
            .filterNot { it.id in unlockedIds }
            .maxByOrNull { it.progress(stats) }
            ?.takeIf { it.progress(stats) > 0f }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_gamification),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = stringResource(R.string.open_menu),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.gamification_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )

            LevelHero(
                stats = stats,
                weekXp = weekXp,
                unlocked = unlockedIds.size,
                totalAchievements = ACHIEVEMENTS.size,
            )

            Spacer(Modifier.height(12.dp))
            RankCard(
                level = level,
                achievements = unlockedIds.size,
                onOpen = { showRanks = true },
            )

            Spacer(Modifier.height(12.dp))
            StatsGrid(stats = stats)

            if (events.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                FirstStepsCard()
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(
                    label = stringResource(R.string.gamification_tab_achievements),
                    selected = tab == Tab.Achievements,
                    onClick = { tab = Tab.Achievements },
                )
                TabChip(
                    label = stringResource(R.string.gamification_tab_history),
                    selected = tab == Tab.History,
                    onClick = { tab = Tab.History },
                )
                TabChip(
                    label = stringResource(R.string.gamification_tab_how),
                    selected = tab == Tab.How,
                    onClick = { tab = Tab.How },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (tab) {
                Tab.Achievements -> AchievementsTab(
                    stats = stats,
                    unlocks = unlocks,
                    unlockedIds = unlockedIds,
                    almost = almost,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onOpenDetail = { detail = it },
                )

                Tab.History -> HistoryList(events = events, today = today)

                Tab.How -> HowToEarn()
            }
        }
    }

    detail?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            stats = stats,
            unlockedOn = unlocks.firstOrNull { it.achievementId == achievement.id }?.unlockedOn,
            onDismiss = { detail = null },
        )
    }

    if (showRanks) {
        RankLadderDialog(
            level = level,
            achievements = unlockedIds.size,
            onDismiss = { showRanks = false },
        )
    }
}

/**
 * Cartão principal: anel de progresso do nível, rank e três números de contexto.
 *
 * O anel substituiu o círculo chapado + barra solta: o progresso do nível fica no
 * mesmo lugar do número, e a barra continua embaixo para leitura rápida.
 */
@Composable
private fun LevelHero(
    stats: GamificationStats,
    weekXp: Int,
    unlocked: Int,
    totalAchievements: Int,
) {
    val progress = levelProgress(stats.totalXp)
    val rank = rankFor(progress.level, unlocked)
    val next = nextRank(progress.level, unlocked)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelRing(level = progress.level, percent = progress.percent)

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gamification_eyebrow),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(rank.label),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.gamification_xp_total, stats.totalXp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gamification_level, progress.level),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${progress.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.gamification_to_next,
                    progress.xpToNextLevel,
                    progress.level + 1,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))
            Text(
                text = if (next == null) {
                    stringResource(R.string.gamification_last_rank)
                } else {
                    stringResource(
                        R.string.gamification_next_rank_detail,
                        stringResource(next.label),
                        next.minLevel,
                        next.minAchievements,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                MiniStat(
                    icon = R.drawable.ic_flame,
                    value = stats.currentRoutineStreak.toString(),
                    label = stringResource(R.string.gamification_stat_streak),
                )
                MiniStat(
                    icon = R.drawable.ic_habit_emoji_events,
                    value = "$unlocked/$totalAchievements",
                    label = stringResource(R.string.gamification_tab_achievements),
                )
                MiniStat(
                    icon = R.drawable.ic_habit_trending_up,
                    value = signed(weekXp),
                    label = stringResource(R.string.gamification_week_xp),
                    valueColor = when {
                        weekXp > 0 -> MaterialTheme.colorScheme.primary
                        weekXp < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** Anel do nível: trilho + arco do progresso, com o número no centro. */
@Composable
private fun LevelRing(level: Int, percent: Int) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * (percent.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.gamification_level_word),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.MiniStat(
    icon: Int,
    value: String,
    label: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class RankState { Done, Current, Locked }

/**
 * Escada de ranks: brasões hexagonais ligados por um trilho.
 *
 * O rank passou a pedir nível **e** conquistas; a escada mostra onde você está e
 * o que falta para o próximo. Tocar abre o quadro completo.
 */
@Composable
private fun RankCard(level: Int, achievements: Int, onOpen: () -> Unit) {
    val current = rankFor(level, achievements)
    val next = nextRank(level, achievements)
    val currentIndex = Rank.entries.indexOf(current)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.gamification_rank_see_all),
                onClick = onOpen,
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gamification_ranks_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.gamification_ranks_count,
                        currentIndex + 1,
                        Rank.entries.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Rank.entries.forEachIndexed { index, rank ->
                    RankBadge(
                        rank = rank,
                        state = when {
                            index < currentIndex -> RankState.Done
                            index == currentIndex -> RankState.Current
                            else -> RankState.Locked
                        },
                        size = if (index == currentIndex) 38.dp else 30.dp,
                    )
                    if (index < Rank.entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    if (index < currentIndex) {
                                        rankColor(rank).copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(current.label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (next == null) {
                    stringResource(R.string.gamification_last_rank)
                } else {
                    stringResource(
                        R.string.gamification_next_rank_detail,
                        stringResource(next.label),
                        next.minLevel,
                        next.minAchievements,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Brasão hexagonal do rank, com o ícone da faixa no centro. */
@Composable
private fun RankBadge(rank: Rank, state: RankState, size: Dp) {
    val color = rankColor(rank)
    val alpha = when (state) {
        RankState.Done -> 1f
        RankState.Current -> 1f
        RankState.Locked -> 0.32f
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f
            val path = Path()
            for (i in 0 until 6) {
                val angle = (PI / 3.0 * i) - PI / 2.0
                val x = this.size.width / 2f + (radius * cos(angle)).toFloat()
                val y = this.size.height / 2f + (radius * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                color = color.copy(alpha = if (state == RankState.Locked) 0.05f else 0.14f),
            )
            drawPath(
                path = path,
                color = color.copy(alpha = if (state == RankState.Locked) 0.35f else 0.9f),
                style = Stroke(stroke),
            )
        }
        Icon(
            painter = painterResource(rankIcon(rank)),
            contentDescription = null,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** Quadro completo dos ranks, com requisito e estado de cada faixa. */
@Composable
private fun RankLadderDialog(level: Int, achievements: Int, onDismiss: () -> Unit) {
    val current = rankFor(level, achievements)
    val currentIndex = Rank.entries.indexOf(current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gamification_ranks_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Rank.entries.forEachIndexed { index, rank ->
                    val state = when {
                        index < currentIndex -> RankState.Done
                        index == currentIndex -> RankState.Current
                        else -> RankState.Locked
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (state == RankState.Current) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RankBadge(rank = rank, state = state, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(rank.label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (state == RankState.Locked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = stringResource(
                                    R.string.gamification_rank_requirements,
                                    rank.minLevel,
                                    rank.minAchievements,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(
                                when (state) {
                                    RankState.Done -> R.string.gamification_rank_done
                                    RankState.Current -> R.string.gamification_rank_current
                                    RankState.Locked -> R.string.gamification_rank_locked
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (state == RankState.Current) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (state == RankState.Locked) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.gamification_rank_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.gamification_close))
            }
        },
    )
}

private fun rankColor(rank: Rank): Color = when (rank) {
    Rank.Beginner -> Color(0xFF888780)
    Rank.Apprentice -> Color(0xFF639922)
    Rank.Bronze -> Color(0xFFCD7F32)
    Rank.Silver -> Color(0xFFB0BEC5)
    Rank.Gold -> Color(0xFFFFC107)
    Rank.Platinum -> Color(0xFF64B5F6)
    Rank.Diamond -> Color(0xFF7F77DD)
    Rank.Legendary -> Color(0xFFE24B4A)
}

private fun rankIcon(rank: Rank): Int = when (rank) {
    Rank.Beginner -> R.drawable.ic_habit_flag
    Rank.Apprentice -> R.drawable.ic_habit_school
    Rank.Bronze -> R.drawable.ic_habit_emoji_events
    Rank.Silver -> R.drawable.ic_habit_grade
    Rank.Gold -> R.drawable.ic_habit_stars
    Rank.Platinum -> R.drawable.ic_habit_bolt
    Rank.Diamond -> R.drawable.ic_gamification
    Rank.Legendary -> R.drawable.ic_flame
}

private data class StatTile(val icon: Int, val value: Int, val label: Int)

/**
 * Números do retrato em grade de 4 colunas.
 *
 * Antes eram colunas fixas de 74 dp com rótulo de uma linha: "Sub-tarefas" e
 * "Dias limpos" saíam cortados. Agora cada célula tem ícone, valor e até duas
 * linhas de rótulo.
 */
@Composable
private fun StatsGrid(stats: GamificationStats) {
    val tiles = listOf(
        StatTile(R.drawable.ic_check_circle, stats.blocksConfirmed, R.string.gamification_stat_blocks),
        StatTile(R.drawable.ic_habit_assignment, stats.tasksDone, R.string.gamification_stat_tasks),
        StatTile(R.drawable.ic_habit_checklist, stats.subtasksDone, R.string.gamification_stat_subtasks),
        StatTile(R.drawable.ic_heatmap, stats.days100, R.string.gamification_stat_perfect_days),
        StatTile(R.drawable.ic_flame, stats.bestRoutineStreak, R.string.gamification_stat_best_streak),
        StatTile(R.drawable.ic_habit_target, stats.habitTargetDays, R.string.gamification_stat_habit_targets),
        StatTile(R.drawable.ic_habit_eco, stats.bestCleanStreak, R.string.gamification_stat_clean),
        StatTile(R.drawable.ic_link, stats.activeConnections, R.string.gamification_stat_connections),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.gamification_stats_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            tiles.chunked(4).let { rows ->
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { tile ->
                            StatCell(tile = tile, modifier = Modifier.weight(1f))
                        }
                    }
                    if (index < rows.lastIndex) {
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(tile: StatTile, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tile.value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(tile.label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Primeiro contato: explica o que gera XP sem mandar o usuário para outra tela. */
@Composable
private fun FirstStepsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_gamification),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.gamification_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.gamification_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsTab(
    stats: GamificationStats,
    unlocks: List<AchievementUnlock>,
    unlockedIds: Set<String>,
    almost: Achievement?,
    filter: AchievementFilter,
    onFilterChange: (AchievementFilter) -> Unit,
    onOpenDetail: (Achievement) -> Unit,
) {
    AchievementsSummary(
        unlocked = unlockedIds.size,
        total = ACHIEVEMENTS.size,
        almost = almost,
        stats = stats,
    )

    Spacer(Modifier.height(12.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabChip(
            label = stringResource(R.string.gamification_filter_all),
            selected = filter == AchievementFilter.All,
            onClick = { onFilterChange(AchievementFilter.All) },
        )
        TabChip(
            label = stringResource(R.string.gamification_filter_unlocked),
            selected = filter == AchievementFilter.Unlocked,
            onClick = { onFilterChange(AchievementFilter.Unlocked) },
        )
        TabChip(
            label = stringResource(R.string.gamification_filter_locked),
            selected = filter == AchievementFilter.Locked,
            onClick = { onFilterChange(AchievementFilter.Locked) },
        )
    }

    Spacer(Modifier.height(12.dp))

    val visible = when (filter) {
        AchievementFilter.All -> ACHIEVEMENTS
        // Conquistadas: as mais recentes primeiro.
        AchievementFilter.Unlocked -> ACHIEVEMENTS
            .filter { it.id in unlockedIds }
            .sortedByDescending { achievement ->
                unlocks.firstOrNull { it.achievementId == achievement.id }?.unlockedOn ?: ""
            }
        // Faltando: o que está mais perto de cair primeiro.
        AchievementFilter.Locked -> ACHIEVEMENTS
            .filterNot { it.id in unlockedIds }
            .sortedByDescending { it.progress(stats) }
    }

    if (visible.isEmpty()) {
        Text(
            text = stringResource(R.string.gamification_filter_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        )
        return
    }

    // Seções por família: com 76 conquistas, a lista plana virava um paredão.
    val grouped = AchievementCategory.entries.mapNotNull { category ->
        val items = visible.filter { it.category == category }
        if (items.isEmpty()) null else category to items
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        grouped.forEach { (category, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryHeader(
                    category = category,
                    unlocked = items.count { it.id in unlockedIds },
                    total = items.size,
                )
                items.forEach { achievement ->
                    AchievementRow(
                        achievement = achievement,
                        stats = stats,
                        unlockedOn = unlocks
                            .firstOrNull { it.achievementId == achievement.id }
                            ?.unlockedOn,
                        onClick = { onOpenDetail(achievement) },
                    )
                }
            }
        }
    }
}

/** Cabeçalho de família: ícone, nome e quantas já caíram. */
@Composable
private fun CategoryHeader(category: AchievementCategory, unlocked: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(category.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(category.label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.gamification_unlocked_count, unlocked, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Cabeçalho da aba: quanto já caiu e qual é a próxima conquista. */
@Composable
private fun AchievementsSummary(
    unlocked: Int,
    total: Int,
    almost: Achievement?,
    stats: GamificationStats,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gamification_tab_achievements),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.gamification_unlocked_count, unlocked, total),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { if (total == 0) 0f else unlocked.toFloat() / total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            if (almost != null) {
                val detail = almost.progressDetail(stats)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medal(tier = almost.tier, category = almost.category, unlocked = false)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gamification_next_achievement),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(almost.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail != null) {
                            Text(
                                text = stringResource(
                                    R.string.gamification_progress_detail,
                                    detail.first,
                                    detail.second,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = "+${almost.tier.xp}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = tierColor(almost.tier),
                    )
                }
            }
        }
    }
}

/** Uma conquista: desbloqueada mostra a data, bloqueada mostra o progresso. */
@Composable
private fun AchievementRow(
    achievement: Achievement,
    stats: GamificationStats,
    unlockedOn: String?,
    onClick: () -> Unit,
) {
    val unlocked = unlockedOn != null
    val color = tierColor(achievement.tier)
    val detail = if (unlocked) null else achievement.progressDetail(stats)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (unlocked) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Medal(tier = achievement.tier, category = achievement.category, unlocked = unlocked)
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(achievement.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (unlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TierTag(tier = achievement.tier)
                }
                Text(
                    text = stringResource(achievement.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (unlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.gamification_unlocked_on,
                                formatDay(unlockedOn),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { achievement.progress(stats) },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        if (detail != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.gamification_progress_detail,
                                    detail.first,
                                    detail.second,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))
            Text(
                text = "+${achievement.tier.xp}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Detalhe da conquista: todas as condições, não só a mais atrasada.
 *
 * A linha da lista mostra a condição que falta e corta a descrição em duas linhas;
 * aqui o usuário vê o que a conquista pede por inteiro e quanto já tem em cada item.
 */
@Composable
private fun AchievementDetailDialog(
    achievement: Achievement,
    stats: GamificationStats,
    unlockedOn: String?,
    onDismiss: () -> Unit,
) {
    val unlocked = unlockedOn != null
    val color = tierColor(achievement.tier)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Medal(tier = achievement.tier, category = achievement.category, unlocked = unlocked)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(achievement.category.label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(achievement.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    TierTag(tier = achievement.tier)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(achievement.description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.gamification_detail_reward, achievement.tier.xp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    text = if (unlocked) {
                        stringResource(R.string.gamification_unlocked_on, formatDay(unlockedOn))
                    } else {
                        stringResource(R.string.gamification_detail_locked)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                if (achievement.conditions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.gamification_detail_conditions).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    achievement.conditions.forEach { condition ->
                        ConditionRow(
                            condition = condition,
                            stats = stats,
                            color = color,
                            unlocked = unlocked,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.gamification_close))
            }
        },
    )
}

@Composable
private fun ConditionRow(
    condition: AchievementCondition,
    stats: GamificationStats,
    color: Color,
    unlocked: Boolean,
) {
    val value = stats.valueOf(condition.stat)
    // Conquista já desbloqueada é permanente: a condição foi cumprida no passado,
    // mesmo que a estatística atual tenha mudado (rotina apagada, contador zerado).
    val met = unlocked || value >= condition.target
    val ratio = when {
        unlocked -> 1f
        condition.target <= 0 -> 1f
        else -> (value.toFloat() / condition.target).coerceIn(0f, 1f)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(statLabel(condition.stat)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (unlocked) {
                    stringResource(R.string.gamification_condition_met)
                } else {
                    stringResource(
                        R.string.gamification_progress_detail,
                        value.coerceAtMost(condition.target),
                        condition.target,
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (met) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (met) MaterialTheme.colorScheme.primary else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/** Medalha do tier: ícone da família, tingido, apagado enquanto está bloqueada. */
@Composable
private fun Medal(tier: AchievementTier, category: AchievementCategory, unlocked: Boolean) {
    val color = tierColor(tier)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (unlocked) 0.18f else 0.07f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = if (unlocked) 0.55f else 0.20f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.icon),
            contentDescription = null,
            tint = if (unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun TierTag(tier: AchievementTier) {
    val color = tierColor(tier)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(tier.label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/**
 * Histórico agrupado por dia.
 *
 * A lista plana de 60 itens não deixava ver "quanto rendeu hoje" nem separar
 * dias; agora cada grupo tem o saldo do dia e o cabeçalho usa Hoje/Ontem.
 */
@Composable
private fun HistoryList(events: List<XpEvent>, today: LocalDate) {
    if (events.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Text(
                text = stringResource(R.string.gamification_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    var shown by rememberSaveable { mutableStateOf(HistoryPage) }
    val sorted = remember(events) { events.sortedByDescending { it.createdAt } }
    val visible = remember(sorted, shown) { sorted.take(shown) }
    val groups = remember(visible) {
        visible.groupBy { event ->
            Instant.ofEpochMilli(event.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        XpBreakdown(events = events)

        groups.forEach { (day, dayEvents) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dayLabel(day, today),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = signed(dayEvents.sumOf { it.amount }),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = amountColor(dayEvents.sumOf { it.amount }),
                    )
                }
                dayEvents.forEach { event -> HistoryRow(event = event) }
            }
        }

        if (sorted.size > shown) {
            TextButton(
                onClick = { shown += HistoryPage },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gamification_history_more))
            }
        }
    }
}

/**
 * De onde vem o XP: soma por tipo de lançamento, da maior para a menor.
 *
 * A barra é relativa ao maior valor absoluto do conjunto — dá para comparar
 * "conquistas" com "blocos" sem precisar de escala.
 */
@Composable
private fun XpBreakdown(events: List<XpEvent>) {
    val totals = remember(events) {
        events.groupBy { it.kind }
            .map { (kind, list) -> kind to list.sumOf { it.amount } }
            .filter { it.second != 0 }
            .sortedByDescending { abs(it.second) }
            .take(6)
    }
    if (totals.isEmpty()) return

    val max = totals.maxOf { abs(it.second) }.coerceAtLeast(1)
    val total = events.sumOf { it.amount }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gamification_history_breakdown).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.gamification_history_breakdown_total, total),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor(total),
                )
            }

            Spacer(Modifier.height(10.dp))

            totals.forEachIndexed { index, (kind, amount) ->
                val known = XpKind.fromRaw(kind)
                val label = if (known != null) stringResource(known.label) else kind
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(kindIcon(kind)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = signed(amount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor(amount),
                    )
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { abs(amount).toFloat() / max },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (amount < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (index < totals.lastIndex) Spacer(Modifier.height(9.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.gamification_history_breakdown_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryRow(event: XpEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(kindIcon(event.kind)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.label?.let { stringResource(it.label) } ?: event.kind,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoment(event.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = signed(event.amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = amountColor(event.amount),
        )
    }
}

private data class HowRow(val label: Int, val amount: String, val negative: Boolean = false)

/** Tabela "como ganhar", lida direto de [XpAmounts]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HowToEarn() {
    val rows = listOf(
        HowRow(R.string.xp_kind_block, "+${XpAmounts.BLOCK_CHECKLIST}"),
        HowRow(
            R.string.gamification_how_score_label,
            stringResource(R.string.gamification_how_per_point, XpAmounts.BLOCK_SCORE_PER_POINT),
        ),
        HowRow(R.string.xp_kind_task, "+${XpAmounts.TASK_DONE}"),
        HowRow(R.string.xp_kind_subtask, "+${XpAmounts.SUBTASK_DONE}"),
        HowRow(R.string.xp_kind_habit, "+${XpAmounts.HABIT_CONFIRM}"),
        HowRow(R.string.xp_kind_habit_target, "+${XpAmounts.HABIT_TARGET_BONUS}"),
        HowRow(R.string.xp_kind_habit_slip, XpAmounts.HABIT_SLIP.toString(), negative = true),
        HowRow(R.string.xp_kind_day_full, "+${XpAmounts.ROUTINE_DAY_FULL}"),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(row.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    AmountChip(text = row.amount, negative = row.negative)
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.gamification_how_achievement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AchievementTier.entries.forEach { tier ->
                    TierTag(tier = tier)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.gamification_how_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AmountChip(text: String, negative: Boolean = false) {
    val color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** Rótulo de cada estatística usada como condição de conquista. */
private fun statLabel(stat: GamificationStat): Int = when (stat) {
    GamificationStat.BlocksConfirmed -> R.string.gamification_stat_blocks
    GamificationStat.PerfectBlocks -> R.string.gamification_stat_perfect_blocks
    GamificationStat.Days100 -> R.string.gamification_stat_perfect_days
    GamificationStat.BestRoutineStreak -> R.string.gamification_stat_streak
    GamificationStat.TasksDone -> R.string.gamification_stat_tasks
    GamificationStat.SubtasksDone -> R.string.gamification_stat_subtasks
    GamificationStat.ActiveConnections -> R.string.gamification_stat_connections
    GamificationStat.GoodHabitsActive -> R.string.gamification_stat_good_habits
    GamificationStat.BadHabitsActive -> R.string.gamification_stat_bad_habits
    GamificationStat.HabitTargetDays -> R.string.gamification_stat_habit_targets
    GamificationStat.RelapsesLogged -> R.string.gamification_stat_relapses
    GamificationStat.BestCleanStreak -> R.string.gamification_stat_clean
    GamificationStat.TotalXp -> R.string.gamification_stat_total_xp
    GamificationStat.Level -> R.string.gamification_stat_level
}

private fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.Bronze -> Color(0xFFCD7F32)
    AchievementTier.Silver -> Color(0xFFB0BEC5)
    AchievementTier.Gold -> Color(0xFFFFC107)
    AchievementTier.Platinum -> Color(0xFF64B5F6)
}

@Composable
private fun amountColor(amount: Int): Color = when {
    amount > 0 -> MaterialTheme.colorScheme.primary
    amount < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> value.toString()
    else -> "0"
}

private fun kindIcon(raw: String): Int = when (XpKind.fromRaw(raw)) {
    XpKind.BlockConfirm -> R.drawable.ic_check_circle
    XpKind.TaskDone -> R.drawable.ic_tasks
    XpKind.SubtaskDone -> R.drawable.ic_habit_checklist
    XpKind.HabitConfirm -> R.drawable.ic_habit_target
    XpKind.HabitTarget -> R.drawable.ic_habit_grade
    XpKind.HabitSlip -> R.drawable.ic_habit_trending_down
    XpKind.RoutineDayFull -> R.drawable.ic_heatmap
    XpKind.Achievement -> R.drawable.ic_habit_emoji_events
    null -> R.drawable.ic_info
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.gamification_history_today)
    today.minusDays(1) -> stringResource(R.string.gamification_history_yesterday)
    else -> day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun formatDay(raw: String?): String {
    val date = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return raw ?: ""
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun formatMoment(millis: Long): String {
    val moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return moment.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}
