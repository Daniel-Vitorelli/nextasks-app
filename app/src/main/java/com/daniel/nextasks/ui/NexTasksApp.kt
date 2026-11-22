package com.daniel.nextasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Addon
import com.daniel.nextasks.data.AppSettings
import com.daniel.nextasks.data.isAddonEnabled
import com.daniel.nextasks.data.BackupService
import com.daniel.nextasks.data.CompletionRepository
import com.daniel.nextasks.data.AchievementRepository
import com.daniel.nextasks.data.ConnectionEngine
import com.daniel.nextasks.data.ConnectionRepository
import com.daniel.nextasks.data.BlockOverrideRepository
import com.daniel.nextasks.data.EventRepository
import com.daniel.nextasks.data.GamificationEngine
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.ExtraBlockRepository
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.SettingsRepository
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.TaskCompletion
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.TaskSortSettings
import com.daniel.nextasks.data.XpRepository
import com.daniel.nextasks.ui.habits.HabitsScreen
import com.daniel.nextasks.ui.home.HomeScreen
import com.daniel.nextasks.ui.tasks.TasksScreen
import com.daniel.nextasks.ui.calendar.CalendarScreen
import com.daniel.nextasks.ui.connections.ConnectionsScreen
import com.daniel.nextasks.ui.gamification.GamificationScreen
import com.daniel.nextasks.ui.routines.RoutinesScreen
import com.daniel.nextasks.ui.settings.SettingsScreen
import com.daniel.nextasks.ui.theme.NexTasksTheme
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Estrutura principal: barra superior com o botão de menu + gaveta de navegação.
 *
 * A gaveta é a navegação primária recomendada pelo Material 3 quando existem 5 ou mais
 * destinos de topo (aqui são 9). Uma barra inferior ficaria sobrecarregada e uma
 * navigation rail só faz sentido em telas largas (tablet/paisagem).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexTasksApp(settingsRepository: SettingsRepository) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var current by rememberSaveable { mutableStateOf(AppDestination.Home) }

    // Repositórios locais (DataStore) — vivem enquanto o app estiver aberto.
    val context = LocalContext.current.applicationContext
    val routineRepository = remember(context) { RoutineRepository(context) }
    val completionRepository = remember(context) { CompletionRepository(context) }
    val taskRepository = remember(context) { TaskRepository(context) }
    val overrideRepository = remember(context) { BlockOverrideRepository(context) }
    val extraRepository = remember(context) { ExtraBlockRepository(context) }
    val subtaskRepository = remember(context) { SubtaskRepository(context) }
    val eventRepository = remember(context) { EventRepository(context) }
    val habitRepository = remember(context) { HabitRepository(context) }
    val backupService = remember(context) { BackupService(context) }
    val connectionRepository = remember(context) { ConnectionRepository(context) }
    // Conclusão de tarefa com a cascata das sub-tarefas, num ponto só.
    val taskCompletion = remember(context) { TaskCompletion(taskRepository, subtaskRepository) }
    val connectionEngine = remember(context) {
        ConnectionEngine(
            connectionRepository = connectionRepository,
            completionRepository = completionRepository,
            routineRepository = routineRepository,
            taskCompletion = taskCompletion,
            subtaskRepository = subtaskRepository,
            habitRepository = habitRepository,
            settingsRepository = settingsRepository,
        )
    }
    val xpRepository = remember(context) { XpRepository(context) }
    val achievementRepository = remember(context) { AchievementRepository(context) }
    val gamificationEngine = remember(context) {
        GamificationEngine(
            xpRepository = xpRepository,
            achievementRepository = achievementRepository,
            routineRepository = routineRepository,
            completionRepository = completionRepository,
            taskRepository = taskRepository,
            subtaskRepository = subtaskRepository,
            habitRepository = habitRepository,
            connectionRepository = connectionRepository,
            settingsRepository = settingsRepository,
        )
    }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

    // Conexões órfãs (bloco ou entidade apagados) somem ao abrir o app.
    //
    // Lê direto dos fluxos com `first()`: `collectAsState(initial = emptyList())`
    // entrega uma lista vazia antes do primeiro valor real, e podar nesse instante
    // apagaria **todas** as conexões.
    // Chaveado nas preferências: no primeiro frame elas ainda são o padrão (sem
    // addons), então rodar uma vez com `Unit` nunca podaria nada.
    LaunchedEffect(settings.enabledAddons) {
        if (!settings.isAddonEnabled(Addon.Connections)) return@LaunchedEffect
        connectionRepository.prune(
            blockIds = routineRepository.routines.first()
                .flatMap { routine -> routine.blocks.map { it.id } }
                .toSet(),
            taskIds = taskRepository.tasks.first().map { it.id }.toSet(),
            subtaskIds = subtaskRepository.subtasks.first().map { it.id }.toSet(),
            habitIds = habitRepository.habitList.first().map { it.id }.toSet(),
        )
    }

    // Addons desligados não aparecem na gaveta.
    val visibleDestinations = remember(settings.enabledAddons) {
        AppDestination.entries.filter { destination ->
            val addon = destination.addon
            addon == null || addon in settings.enabledAddons
        }
    }

    // Se o addon da seção atual for desligado, volta para a Home.
    LaunchedEffect(settings.enabledAddons) {
        val addon = current.addon
        if (addon != null && addon !in settings.enabledAddons) {
            current = AppDestination.Home
        }
    }

    // Botão "voltar" fecha a gaveta em vez de sair do app.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NexTasksDrawer(
                current = current,
                destinations = visibleDestinations,
                onSelect = { destination ->
                    current = destination
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        // Calendário e Rotinas têm barra superior própria.
        when (current) {
            AppDestination.Calendar -> CalendarScreen(
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onBlockConfirmed = { blockId, period ->
                    scope.launch {
                        connectionEngine.onBlockConfirmed(blockId)
                        gamificationEngine.reconcile()
                    }
                },
                onBlockUnconfirmed = { blockId, period ->
                    scope.launch {
                        connectionEngine.onBlockUnconfirmed(blockId)
                        gamificationEngine.reconcile()
                    }
                },
                routineRepository = routineRepository,
                completionRepository = completionRepository,
                overrideRepository = overrideRepository,
                extraRepository = extraRepository,
                eventRepository = eventRepository,
            )

            AppDestination.Connections -> ConnectionsScreen(
                connectionRepository = connectionRepository,
                routineRepository = routineRepository,
                completionRepository = completionRepository,
                taskRepository = taskRepository,
                subtaskRepository = subtaskRepository,
                habitRepository = habitRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onApplyNow = { connection ->
                    scope.launch { connectionEngine.applyNow(connection) }
                },
            )

            AppDestination.Gamification -> GamificationScreen(
                gamification = gamificationEngine,
                xpRepository = xpRepository,
                achievementRepository = achievementRepository,
                routineRepository = routineRepository,
                completionRepository = completionRepository,
                taskRepository = taskRepository,
                subtaskRepository = subtaskRepository,
                habitRepository = habitRepository,
                connectionRepository = connectionRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            AppDestination.Routines -> RoutinesScreen(
                repository = routineRepository,
                completionRepository = completionRepository,
                connectionRepository = connectionRepository,
                overrideRepository = overrideRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            AppDestination.Tasks -> TasksScreen(
                repository = taskRepository,
                subtaskRepository = subtaskRepository,
                taskCompletion = taskCompletion,
                connectionRepository = connectionRepository,
                taskSort = settings.taskSort,
                onTaskDone = { taskId -> scope.launch { connectionEngine.onTaskDone(taskId) } },
                onSubtaskDone = { id -> scope.launch { connectionEngine.onSubtaskDone(id) } },
                onTaskChanged = { id -> scope.launch { gamificationEngine.onTaskChanged(id) } },
                onSubtaskChanged = { id -> scope.launch { gamificationEngine.onSubtaskChanged(id) } },
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            AppDestination.Habits -> HabitsScreen(
                repository = habitRepository,
                connectionRepository = connectionRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            AppDestination.Settings -> SettingsScreen(
                settingsRepository = settingsRepository,
                backupService = backupService,
                routineRepository = routineRepository,
                taskRepository = taskRepository,
                habitRepository = habitRepository,
                eventRepository = eventRepository,
                subtaskRepository = subtaskRepository,
                connectionRepository = connectionRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )

            else -> SectionScaffold(
                destination = current,
                userName = settings.userName,
                taskSort = settings.taskSort,
                onBlockConfirmed = { blockId, period ->
                    scope.launch {
                        connectionEngine.onBlockConfirmed(blockId)
                        gamificationEngine.reconcile()
                    }
                },
                onBlockUnconfirmed = { blockId, period ->
                    scope.launch {
                        connectionEngine.onBlockUnconfirmed(blockId)
                        gamificationEngine.reconcile()
                    }
                },
                onTaskDone = { taskId -> scope.launch { connectionEngine.onTaskDone(taskId) } },
                onSubtaskDone = { id -> scope.launch { connectionEngine.onSubtaskDone(id) } },
                onHabitDone = { id -> scope.launch { connectionEngine.onHabitDone(id) } },
                onTaskChanged = { id -> scope.launch { gamificationEngine.onTaskChanged(id) } },
                onSubtaskChanged = { id -> scope.launch { gamificationEngine.onSubtaskChanged(id) } },
                onHabitChanged = { id, date ->
                    scope.launch { gamificationEngine.onHabitChanged(id, date) }
                },
                xpRepository = xpRepository,
                achievementRepository = achievementRepository,
                gamificationEngine = gamificationEngine,
                taskCompletion = taskCompletion,
                routineRepository = routineRepository,
                completionRepository = completionRepository,
                taskRepository = taskRepository,
                subtaskRepository = subtaskRepository,
                overrideRepository = overrideRepository,
                extraRepository = extraRepository,
                eventRepository = eventRepository,
                habitRepository = habitRepository,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onNavigate = { destination -> current = destination },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionScaffold(
    destination: AppDestination,
    userName: String,
    taskSort: TaskSortSettings,
    onBlockConfirmed: (String, LocalDate) -> Unit,
    onBlockUnconfirmed: (String, LocalDate) -> Unit,
    onTaskDone: (String) -> Unit,
    onSubtaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onTaskChanged: (String) -> Unit,
    onSubtaskChanged: (String) -> Unit,
    onHabitChanged: (String, LocalDate) -> Unit,
    xpRepository: XpRepository,
    achievementRepository: AchievementRepository,
    gamificationEngine: GamificationEngine,
    taskCompletion: TaskCompletion,
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    taskRepository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    overrideRepository: BlockOverrideRepository,
    extraRepository: ExtraBlockRepository,
    eventRepository: EventRepository,
    habitRepository: HabitRepository,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(destination.label),
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
        SectionContent(
            destination = destination,
            taskSort = taskSort,
            onBlockConfirmed = onBlockConfirmed,
            onBlockUnconfirmed = onBlockUnconfirmed,
            onTaskDone = onTaskDone,
            onSubtaskDone = onSubtaskDone,
            onHabitDone = onHabitDone,
            onTaskChanged = onTaskChanged,
            onSubtaskChanged = onSubtaskChanged,
            onHabitChanged = onHabitChanged,
            xpRepository = xpRepository,
            achievementRepository = achievementRepository,
            gamificationEngine = gamificationEngine,
            taskCompletion = taskCompletion,
            routineRepository = routineRepository,
            completionRepository = completionRepository,
            taskRepository = taskRepository,
            subtaskRepository = subtaskRepository,
            overrideRepository = overrideRepository,
            extraRepository = extraRepository,
            eventRepository = eventRepository,
            habitRepository = habitRepository,
            userName = userName,
            onNavigate = onNavigate,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun NexTasksDrawer(
    current: AppDestination,
    destinations: List<AppDestination>,
    onSelect: (AppDestination) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(300.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        destinations.forEach { destination ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = stringResource(destination.label),
                        fontWeight = if (destination == current) {
                            FontWeight.Medium
                        } else {
                            FontWeight.Normal
                        },
                    )
                },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                selected = destination == current,
                onClick = { onSelect(destination) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                shape = RoundedCornerShape(10.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.drawer_version, appVersionName()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun SectionContent(
    destination: AppDestination,
    routineRepository: RoutineRepository,
    completionRepository: CompletionRepository,
    taskRepository: TaskRepository,
    subtaskRepository: SubtaskRepository,
    overrideRepository: BlockOverrideRepository,
    extraRepository: ExtraBlockRepository,
    eventRepository: EventRepository,
    habitRepository: HabitRepository,
    userName: String,
    taskSort: TaskSortSettings,
    onBlockConfirmed: (String, LocalDate) -> Unit,
    onBlockUnconfirmed: (String, LocalDate) -> Unit,
    onTaskDone: (String) -> Unit,
    onSubtaskDone: (String) -> Unit,
    onHabitDone: (String) -> Unit,
    onTaskChanged: (String) -> Unit,
    onSubtaskChanged: (String) -> Unit,
    onHabitChanged: (String, LocalDate) -> Unit,
    xpRepository: XpRepository,
    achievementRepository: AchievementRepository,
    gamificationEngine: GamificationEngine,
    taskCompletion: TaskCompletion,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        AppDestination.Home -> HomeScreen(
            routineRepository = routineRepository,
            completionRepository = completionRepository,
            taskRepository = taskRepository,
            subtaskRepository = subtaskRepository,
            overrideRepository = overrideRepository,
            extraRepository = extraRepository,
            eventRepository = eventRepository,
            habitRepository = habitRepository,
            taskCompletion = taskCompletion,
            userName = userName,
            taskSort = taskSort,
            onBlockConfirmed = onBlockConfirmed,
            onBlockUnconfirmed = onBlockUnconfirmed,
            onTaskDone = onTaskDone,
            onSubtaskDone = onSubtaskDone,
            onHabitDone = onHabitDone,
            onTaskChanged = onTaskChanged,
            onSubtaskChanged = onSubtaskChanged,
            onHabitChanged = onHabitChanged,
            onOpenRoutines = { onNavigate(AppDestination.Routines) },
            onOpenTasks = { onNavigate(AppDestination.Tasks) },
            modifier = modifier,
        )

        else -> PlaceholderSection(destination = destination, modifier = modifier)
    }
}

@Composable
private fun PlaceholderSection(
    destination: AppDestination,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(destination.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.section_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PlaceholderSectionPreview() {
    NexTasksTheme {
        PlaceholderSection(destination = AppDestination.Calendar, modifier = Modifier.fillMaxSize())
    }
}

/** Lê a versão instalada do pacote, para a gaveta não mentir sobre o build. */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
}
