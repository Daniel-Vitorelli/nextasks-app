package com.daniel.nextasks.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AppSettings
import com.daniel.nextasks.data.AccentColor
import com.daniel.nextasks.data.Addon
import com.daniel.nextasks.data.BackupService
import com.daniel.nextasks.data.BackupSummary
import com.daniel.nextasks.data.EventRepository
import com.daniel.nextasks.data.HabitRepository
import com.daniel.nextasks.data.RestoreResult
import com.daniel.nextasks.data.RoutineRepository
import com.daniel.nextasks.data.SettingsRepository
import com.daniel.nextasks.data.SubtaskRepository
import com.daniel.nextasks.data.TaskRepository
import com.daniel.nextasks.data.ThemeMode
import com.daniel.nextasks.ui.AppDestination
import com.daniel.nextasks.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Configurações: aparência, backup dos dados locais e informações do app.
 *
 * A seção de dados existe porque tudo vive em DataStore no aparelho — sem backup,
 * desinstalar apaga meses de histórico sem aviso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    backupService: BackupService,
    routineRepository: RoutineRepository,
    taskRepository: TaskRepository,
    habitRepository: HabitRepository,
    eventRepository: EventRepository,
    subtaskRepository: SubtaskRepository,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val today = remember { LocalDate.now() }
    val routines by routineRepository.routines.collectAsState(initial = emptyList())
    val tasks by taskRepository.tasks.collectAsState(initial = emptyList())
    val habits by habitRepository.habitList.collectAsState(initial = emptyList())
    val events by eventRepository.events.collectAsState(initial = emptyList())
    val subtasks by subtaskRepository.subtasks.collectAsState(initial = emptyList())
    val pendingSubtasks = remember(subtasks) {
        subtasks.filterNot { it.done }.groupingBy { it.taskId }.eachCount()
    }

    // Backup lido e aguardando confirmação; nulo = nenhum diálogo aberto.
    var pendingRestore by remember { mutableStateOf<Pair<String, BackupSummary>?>(null) }
    var wipeConfirm by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showTaskOrder by rememberSaveable { mutableStateOf(false) }
    var showAddons by rememberSaveable { mutableStateOf(false) }

    val exportDone = stringResource(R.string.settings_export_done)
    val exportFailed = stringResource(R.string.settings_export_failed)
    val importInvalid = stringResource(R.string.settings_import_invalid)
    val importDoneTemplate = stringResource(R.string.settings_import_done)
    val wipeDone = stringResource(R.string.settings_wipe_done)
    val noPicker = stringResource(R.string.settings_no_picker)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val text = runCatching { backupService.export() }.getOrNull()
                val saved = text != null && withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray())
                            out.flush()
                        } != null
                    }.getOrDefault(false)
                }
                busy = false
                snackbarHostState.showSnackbar(if (saved) exportDone else exportFailed)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()
                            ?.use { it.readText() }
                    }.getOrNull()
                }
                busy = false
                val summary = text?.let { backupService.inspect(it) }
                if (text == null || summary == null) {
                    snackbarHostState.showSnackbar(importInvalid)
                } else {
                    pendingRestore = text to summary
                }
            }
        }
    }

    // A ordem das tarefas tem tela própria: a lista de critérios cresce e a
    // prévia precisa de espaço.
    if (showTaskOrder) {
        TaskOrderScreen(
            sort = settings.taskSort,
            tasks = tasks,
            pendingSubtasks = pendingSubtasks,
            today = today,
            onBack = { showTaskOrder = false },
            onChange = { sort -> scope.launch { settingsRepository.setTaskSort(sort) } },
        )
        return
    }

    if (showAddons) {
        AddonsScreen(
            enabledAddons = settings.enabledAddons,
            onBack = { showAddons = false },
            onToggle = { addon, enabled ->
                scope.launch { settingsRepository.setAddonEnabled(addon, enabled) }
            },
        )
        return
    }

    val summaryText = storageSummary(routines.size, tasks.size, habits.size, events.size)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.section_settings),
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
            SectionTitle(stringResource(R.string.settings_profile))

            SettingsCard(contentPadding = if (editingName) 16.dp else 0.dp) {
                if (!editingName) {
                    ActionRow(
                        icon = R.drawable.ic_person,
                        title = stringResource(R.string.settings_name),
                        subtitle = settings.userName.ifBlank {
                            stringResource(R.string.settings_name_empty)
                        },
                        onClick = {
                            nameDraft = settings.userName
                            editingName = true
                        },
                    )
                } else {
                    // Edição no próprio card: o campo fica junto do valor que
                    // altera, sem abrir um modal por cima da tela.
                    Text(
                        text = stringResource(R.string.settings_name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { if (it.length <= 40) nameDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_name_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { editingName = false }) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                editingName = false
                                scope.launch { settingsRepository.setUserName(nameDraft) }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.settings_save),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_appearance))

            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeOption(
                            mode = mode,
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_accent),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(settings.accent.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentColor.entries.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings.accent == accent,
                            onClick = {
                                scope.launch { settingsRepository.setAccent(accent) }
                            },
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_task_order))

            SettingsCard(contentPadding = 0.dp) {
                TaskOrderEntryRow(
                    sort = settings.taskSort,
                    onClick = { showTaskOrder = true },
                )
            }

            SectionTitle(stringResource(R.string.settings_addons))

            SettingsCard(contentPadding = 0.dp) {
                AddonsEntryRow(
                    enabledAddons = settings.enabledAddons,
                    onClick = { showAddons = true },
                )
            }

            SectionTitle(stringResource(R.string.settings_data))

            // Confirmação embutida: o arquivo escolhido e o que ele contém ficam
            // à vista antes de aplicar, sem modal.
            pendingRestore?.let { (json, summary) ->
                RestoreCard(
                    summary = summary,
                    onCancel = { pendingRestore = null },
                    onConfirm = {
                        pendingRestore = null
                        scope.launch {
                            busy = true
                            val result = runCatching { backupService.restore(json) }.getOrNull()
                            busy = false
                            snackbarHostState.showSnackbar(
                                when (result) {
                                    is RestoreResult.Restored ->
                                        importDoneTemplate.format(result.records)
                                    else -> importInvalid
                                },
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (summaryText != null) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }

            SettingsCard(contentPadding = 0.dp) {
                ActionRow(
                    icon = R.drawable.ic_download,
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_hint),
                    enabled = !busy,
                    onClick = {
                        val name = "nextasks-backup-${LocalDate.now()}.json"
                        // Sem seletor de arquivos (aparelho sem DocumentsUI) o
                        // launch lança — avisar em vez de não fazer nada.
                        runCatching { exportLauncher.launch(name) }
                            .onFailure {
                                scope.launch { snackbarHostState.showSnackbar(noPicker) }
                            }
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ActionRow(
                    icon = R.drawable.ic_upload,
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_hint),
                    enabled = !busy,
                    onClick = {
                        runCatching { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                            .onFailure { scope.launch { snackbarHostState.showSnackbar(noPicker) } }
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                ActionRow(
                    icon = R.drawable.ic_delete,
                    title = stringResource(R.string.settings_wipe),
                    subtitle = stringResource(R.string.settings_wipe_hint),
                    enabled = !busy,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { wipeConfirm = true },
                )
            }

            SectionTitle(stringResource(R.string.settings_about))

            SettingsCard {
                InfoRow(
                    label = stringResource(R.string.settings_version),
                    value = appVersionName(),
                )
                Spacer(Modifier.height(10.dp))
                InfoRow(
                    label = stringResource(R.string.settings_storage),
                    value = stringResource(R.string.settings_storage_value),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_local_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (wipeConfirm) {
        AlertDialog(
            onDismissRequest = { wipeConfirm = false },
            title = { Text(stringResource(R.string.settings_wipe_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_wipe_body,
                        summaryText ?: stringResource(R.string.settings_nothing),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        wipeConfirm = false
                        scope.launch {
                            busy = true
                            backupService.clearAll()
                            busy = false
                            snackbarHostState.showSnackbar(wipeDone)
                        }
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_wipe_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

/** Uma das três opções de tema, com ícone e rótulo. */
@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (mode) {
        ThemeMode.System -> R.drawable.ic_theme_auto
        ThemeMode.Light -> R.drawable.ic_theme_light
        ThemeMode.Dark -> R.drawable.ic_theme_dark
    }
    val label = when (mode) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Linha de ação: ícone, título, explicação e a seta implícita no toque. */
@Composable
internal fun ActionRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Rótulo à esquerda, valor à direita. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsCard(
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Com padding zero (lista de ações) cada linha cuida do próprio espaçamento.
        Column(
            modifier = Modifier.padding(
                horizontal = if (contentPadding == 0.dp) 0.dp else 16.dp,
                vertical = contentPadding,
            ),
            content = content,
        )
    }
}

/** "3 rotinas · 12 tarefas · 4 hábitos", omitindo o que está zerado. */
@Composable
private fun storageSummary(
    routines: Int,
    tasks: Int,
    habits: Int,
    events: Int,
): String? {
    val parts = buildList {
        if (routines > 0) {
            add(
                if (routines == 1) {
                    stringResource(R.string.count_routine_one)
                } else {
                    stringResource(R.string.count_routine, routines)
                },
            )
        }
        if (tasks > 0) {
            add(
                if (tasks == 1) {
                    stringResource(R.string.count_task_one)
                } else {
                    stringResource(R.string.count_task, tasks)
                },
            )
        }
        if (habits > 0) {
            add(
                if (habits == 1) {
                    stringResource(R.string.count_habit_one)
                } else {
                    stringResource(R.string.count_habit, habits)
                },
            )
        }
        if (events > 0) {
            add(
                if (events == 1) {
                    stringResource(R.string.count_event_one)
                } else {
                    stringResource(R.string.count_event, events)
                },
            )
        }
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** "Exportado em 18/11/2026 14:46 · 42 registros" — a data pode faltar. */
@Composable
private fun restoreMeta(summary: BackupSummary): String {
    val whenLabel = summary.exportedAt?.let { raw ->
        runCatching {
            LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
        }.getOrDefault(raw)
    }
    return if (whenLabel != null) {
        stringResource(R.string.settings_import_meta, whenLabel, summary.records)
    } else {
        stringResource(R.string.settings_import_meta_plain, summary.records)
    }
}

/** Lê a versão instalada do pacote. */
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

/** Confirmação de restauração, dentro da própria tela. */
@Composable
private fun RestoreCard(
    summary: BackupSummary,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_import_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = restoreMeta(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_import_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.settings_cancel))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(R.string.settings_restore),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Círculo de cor de destaque; o escolhido ganha anel e check. */
@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = accent.colors()

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.swatch)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSwatch,
            )
        }
    }
}

private fun AccentColor.labelRes(): Int = when (this) {
    AccentColor.Green -> R.string.accent_green
    AccentColor.Blue -> R.string.accent_blue
    AccentColor.Purple -> R.string.accent_purple
    AccentColor.Pink -> R.string.accent_pink
    AccentColor.Orange -> R.string.accent_orange
    AccentColor.Amber -> R.string.accent_amber
    AccentColor.Cyan -> R.string.accent_cyan
}

