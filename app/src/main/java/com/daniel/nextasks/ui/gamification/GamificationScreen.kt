package com.daniel.nextasks.ui.gamification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.ACHIEVEMENTS
import com.daniel.nextasks.data.Achievement
import com.daniel.nextasks.data.AchievementTier
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.GamificationEngine
import com.daniel.nextasks.data.GamificationStats
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.Rank
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.XpAmounts
import com.daniel.nextasks.data.XpEvent
import com.daniel.nextasks.data.XpKind
import com.daniel.nextasks.data.AchievementRepository
import com.daniel.nextasks.data.XpRepository
import com.daniel.nextasks.data.gamificationStats
import com.daniel.nextasks.data.levelProgress
import com.daniel.nextasks.data.nextRank
import com.daniel.nextasks.data.progress
import com.daniel.nextasks.data.rankFromLevel
import com.daniel.nextasks.ui.components.rememberToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Tab { Achievements, History, How }
private enum class AchievementFilter { All, Unlocked, Locked }

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
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )

            LevelCard(stats = stats)
            Spacer(Modifier.height(16.dp))
            StatsCard(stats = stats)

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
                Tab.Achievements -> {
                    val unlockedIds = unlocks.map { it.achievementId }.toSet()
                    val visible = when (filter) {
                        AchievementFilter.All -> ACHIEVEMENTS
                        AchievementFilter.Unlocked -> ACHIEVEMENTS.filter { it.id in unlockedIds }
                        AchievementFilter.Locked -> ACHIEVEMENTS.filterNot { it.id in unlockedIds }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabChip(
                            label = stringResource(R.string.gamification_filter_all),
                            selected = filter == AchievementFilter.All,
                            onClick = { filter = AchievementFilter.All },
                        )
                        TabChip(
                            label = stringResource(
                                R.string.gamification_unlocked_count,
                                unlockedIds.size,
                                ACHIEVEMENTS.size,
                            ),
                            selected = filter == AchievementFilter.Unlocked,
                            onClick = { filter = AchievementFilter.Unlocked },
                        )
                        TabChip(
                            label = stringResource(R.string.gamification_filter_locked),
                            selected = filter == AchievementFilter.Locked,
                            onClick = { filter = AchievementFilter.Locked },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

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

                Tab.History -> HistoryList(events = events)

                Tab.How -> HowToEarn()
            }
        }
    }
}

/** Nível, rank e progresso até o próximo. */
@Composable
private fun LevelCard(stats: GamificationStats) {
    val progress = levelProgress(stats.totalXp)
    val rank = rankFromLevel(progress.level)
    val next = nextRank(progress.level)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = progress.level.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.gamification_level_word),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gamification_rank_label, stringResource(rank.label)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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

            Spacer(Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (next == null) {
                    stringResource(R.string.gamification_max_level)
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
        }
    }
}

/** Números do retrato. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsCard(stats: GamificationStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
            Spacer(Modifier.height(10.dp))

            val rows = listOf(
                stringResource(R.string.gamification_stat_blocks) to stats.blocksConfirmed,
                stringResource(R.string.gamification_stat_tasks) to stats.tasksDone,
                stringResource(R.string.gamification_stat_subtasks) to stats.subtasksDone,
                stringResource(R.string.gamification_stat_perfect_days) to stats.days100,
                stringResource(R.string.gamification_stat_streak) to stats.bestRoutineStreak,
                stringResource(R.string.gamification_stat_habit_targets) to stats.habitTargetDays,
                stringResource(R.string.gamification_stat_clean) to stats.bestCleanStreak,
                stringResource(R.string.gamification_stat_connections) to stats.activeConnections,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 4,
            ) {
                rows.forEach { (label, value) ->
                    Column(Modifier.width(74.dp)) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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

/** Uma conquista: desbloqueada mostra a data, bloqueada mostra o progresso. */
@Composable
private fun AchievementRow(
    achievement: Achievement,
    stats: GamificationStats,
    unlockedOn: String?,
) {
    val unlocked = unlockedOn != null
    val color = tierColor(achievement.tier)

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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (unlocked) color else MaterialTheme.colorScheme.outlineVariant),
            )
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
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(achievement.tier.label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
                Text(
                    text = stringResource(achievement.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (unlocked) {
                    Text(
                        text = stringResource(
                            R.string.gamification_unlocked_on,
                            formatDay(unlockedOn),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { achievement.progress(stats) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
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

/** Histórico recente de XP. */
@Composable
private fun HistoryList(events: List<XpEvent>) {
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

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        events.sortedByDescending { it.createdAt }.take(60).forEach { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = event.label?.let { stringResource(it.label) }
                            ?: event.kind,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatMoment(event.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (event.amount >= 0) "+${event.amount}" else event.amount.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (event.amount >= 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

/** Tabela "como ganhar", lida direto de [XpAmounts]. */
@Composable
private fun HowToEarn() {
    val rows = listOf(
        stringResource(R.string.gamification_how_block_checklist, XpAmounts.BLOCK_CHECKLIST),
        stringResource(R.string.gamification_how_block_score, XpAmounts.BLOCK_SCORE_PER_POINT),
        stringResource(R.string.gamification_how_task, XpAmounts.TASK_DONE),
        stringResource(R.string.gamification_how_subtask, XpAmounts.SUBTASK_DONE),
        stringResource(R.string.gamification_how_habit, XpAmounts.HABIT_CONFIRM),
        stringResource(R.string.gamification_how_habit_target, XpAmounts.HABIT_TARGET_BONUS),
        stringResource(R.string.gamification_how_habit_slip, XpAmounts.HABIT_SLIP),
        stringResource(R.string.gamification_how_day_full, XpAmounts.ROUTINE_DAY_FULL),
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = row,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.gamification_how_achievement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.gamification_how_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun tierColor(tier: AchievementTier): Color = when (tier) {
    AchievementTier.Bronze -> Color(0xFFCD7F32)
    AchievementTier.Silver -> Color(0xFFB0BEC5)
    AchievementTier.Gold -> Color(0xFFFFC107)
    AchievementTier.Platinum -> Color(0xFF64B5F6)
}

private fun formatDay(raw: String?): String {
    val date = raw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return raw ?: ""
    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun formatMoment(millis: Long): String {
    val moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return moment.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
}
