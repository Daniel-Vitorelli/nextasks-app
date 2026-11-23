package com.daniel.nextasks.ui.gamification

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.ACHIEVEMENTS
import com.daniel.nextasks.data.Achievement
import com.daniel.nextasks.data.AchievementRepository
import com.daniel.nextasks.data.AchievementTier
import com.daniel.nextasks.data.AchievementUnlock
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.GamificationEngine
import com.daniel.nextasks.data.GamificationStats
import com.daniel.nextasks.data.HabitRepository
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
import com.daniel.nextasks.data.rankFromLevel
import com.daniel.nextasks.ui.components.rememberToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    val unlockedIds = remember(unlocks) { unlocks.map { it.achievementId }.toSet() }
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
                )

                Tab.History -> HistoryList(events = events, today = today)

                Tab.How -> HowToEarn()
            }
        }
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
    val rank = rankFromLevel(progress.level)
    val next = nextRank(progress.level)

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
                text = if (next == null) {
                    stringResource(R.string.gamification_last_rank)
                } else {
                    stringResource(
                        R.string.gamification_to_next,
                        progress.xpToNextLevel,
                        progress.level + 1,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (next != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.gamification_next_rank,
                        next.minLevel - progress.level,
                        stringResource(next.label),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
        AchievementFilter.Unlocked -> ACHIEVEMENTS.filter { it.id in unlockedIds }
        AchievementFilter.Locked -> ACHIEVEMENTS.filterNot { it.id in unlockedIds }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { achievement ->
            AchievementRow(
                achievement = achievement,
                stats = stats,
                unlockedOn = unlocks
                    .firstOrNull { it.achievementId == achievement.id }
                    ?.unlockedOn,
            )
        }
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
                    Medal(tier = almost.tier, unlocked = false)
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
) {
    val unlocked = unlockedOn != null
    val color = tierColor(achievement.tier)
    val detail = if (unlocked) null else achievement.progressDetail(stats)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (unlocked) color.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Medal(tier = achievement.tier, unlocked = unlocked)
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

/** Medalha do tier: troféu tingido, apagado enquanto a conquista está bloqueada. */
@Composable
private fun Medal(tier: AchievementTier, unlocked: Boolean) {
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
            painter = painterResource(R.drawable.ic_habit_emoji_events),
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
