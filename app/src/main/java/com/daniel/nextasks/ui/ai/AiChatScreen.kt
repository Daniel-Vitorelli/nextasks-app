package com.daniel.nextasks.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AiAction
import com.daniel.nextasks.data.AiActionStatus
import com.daniel.nextasks.data.AiConversation
import com.daniel.nextasks.data.AiMemoryRepository
import com.daniel.nextasks.data.AiMessage
import com.daniel.nextasks.data.AiMessageRepository
import com.daniel.nextasks.data.AiProviderRepository
import com.daniel.nextasks.data.AiRole
import com.daniel.nextasks.data.AiSendResult
import com.daniel.nextasks.data.AiSessionRepository
import com.daniel.nextasks.data.aiToolSpec
import com.daniel.nextasks.data.formatActionArgs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Página do assistente: chat com a IA.
 *
 * Só esta página usa internet (o pedido ao provedor); sessões, memória e provedores
 * ficam no aparelho. A IA **propõe** ações e cada uma espera confirmação — nada é
 * executado sem passar pela tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    conversation: AiConversation,
    providerRepository: AiProviderRepository,
    sessionRepository: AiSessionRepository,
    messageRepository: AiMessageRepository,
    memoryRepository: AiMemoryRepository,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by sessionRepository.sessions.collectAsState(initial = emptyList())
    val allMessages by messageRepository.messages.collectAsState(initial = emptyList())
    val providers by providerRepository.providers.collectAsState(initial = emptyList())

    var sessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var showProviders by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val hasProvider = providers.any { it.enabled }
    val current = sessions.firstOrNull { it.id == sessionId }
    val messages = remember(allMessages, sessionId) {
        allMessages.filter { it.sessionId == sessionId }.sortedBy { it.createdAt }
    }

    // Abre a conversa mais recente (ou cria a primeira) na entrada da página.
    LaunchedEffect(Unit) {
        if (sessionId == null) sessionId = conversation.ensureSession().id
    }

    // Conversa apagada com a página aberta: cai para a mais recente.
    LaunchedEffect(sessions, sessionId) {
        if (sessions.isNotEmpty() && sessions.none { it.id == sessionId }) {
            sessionId = sessions.first().id
        }
    }

    fun report(result: AiSendResult) {
        if (result is AiSendResult.Error) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.ai_error, result.message)) }
        }
    }

    fun submit(text: String) {
        val id = sessionId ?: return
        val content = text.trim()
        if (content.isEmpty() || sending) return
        input = ""
        scope.launch {
            sending = true
            val result = conversation.send(id, content)
            sending = false
            report(result)
        }
    }

    fun confirm(actionId: String) {
        val id = sessionId ?: return
        scope.launch {
            sending = true
            val result = conversation.confirm(id, actionId)
            sending = false
            report(result)
        }
    }

    fun reject(actionId: String) {
        val id = sessionId ?: return
        scope.launch {
            sending = true
            val result = conversation.reject(id, actionId)
            sending = false
            report(result)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = current?.displayTitle ?: stringResource(R.string.ai_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (sending) {
                            Text(
                                text = stringResource(R.string.ai_thinking),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                actions = {
                    IconButton(onClick = { showSessions = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_habit_chat),
                            contentDescription = stringResource(R.string.ai_sessions),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { showMemory = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_habit_description),
                            contentDescription = stringResource(R.string.ai_memory),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { showProviders = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.ai_providers),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            if (!hasProvider) {
                NoProviderCard(onConfigure = { showProviders = true })
            }

            val listState = rememberLazyListState()
            val total = messages.size + if (sending) 1 else 0

            // Rola para o fim quando chega mensagem nova ou o "pensando" aparece.
            LaunchedEffect(total) {
                if (total > 0) listState.animateScrollToItem(total - 1)
            }

            Box(Modifier.weight(1f)) {
                when {
                    messages.isEmpty() && !sending -> EmptyChat(onSuggestion = ::submit)
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItem(
                                message = message,
                                busy = sending,
                                onConfirm = ::confirm,
                                onReject = ::reject,
                            )
                        }
                        if (sending) item(key = "thinking") { ThinkingRow() }
                    }
                }
            }

            Composer(
                value = input,
                onValueChange = { input = it },
                onSend = { submit(input) },
                enabled = hasProvider,
                sending = sending,
            )
        }
    }

    if (showSessions) {
        AiSessionsSheet(
            sessions = sessions,
            currentId = sessionId,
            onSelect = {
                sessionId = it
                showSessions = false
            },
            onNew = {
                scope.launch {
                    sessionId = sessionRepository.create().id
                    showSessions = false
                }
            },
            onDelete = { session ->
                scope.launch {
                    sessionRepository.delete(session.id)
                    messageRepository.deleteForSession(session.id)
                    // Apagar a última conversa não pode deixar o chat sem sessão.
                    val remaining = sessionRepository.sessions.first()
                    sessionId = when {
                        remaining.isEmpty() -> conversation.ensureSession().id
                        session.id == sessionId -> remaining.first().id
                        else -> sessionId
                    }
                }
            },
            onDismiss = { showSessions = false },
        )
    }

    if (showMemory) {
        AiMemoryDialog(
            memoryRepository = memoryRepository,
            onDismiss = { showMemory = false },
        )
    }

    if (showProviders) {
        AiProvidersScreen(
            providerRepository = providerRepository,
            onDismiss = { showProviders = false },
        )
    }
}

// ---------------- pedaços da tela ----------------

@Composable
private fun NoProviderCard(onConfigure: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ai_no_provider_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_no_provider_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onConfigure) {
                Text(stringResource(R.string.ai_no_provider_action))
            }
        }
    }
}

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ai),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ai_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.ai_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        listOf(
            R.string.ai_suggestion_today,
            R.string.ai_suggestion_task,
            R.string.ai_suggestion_routine,
            R.string.ai_suggestion_week,
        ).forEach { hint ->
            val label = stringResource(hint)
            TextButton(onClick = { onSuggestion(label) }) {
                Text(label)
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: AiMessage,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    when (message.role) {
        AiRole.User -> Column(Modifier.fillMaxWidth()) {
            Bubble(text = message.text, mine = true)
        }

        AiRole.Assistant -> Column(Modifier.fillMaxWidth()) {
            if (message.text.isNotBlank()) Bubble(text = message.text, mine = false)
            message.actions.forEach { action ->
                Spacer(Modifier.height(6.dp))
                ActionCard(
                    action = action,
                    enabled = !busy,
                    onConfirm = { onConfirm(action.id) },
                    onReject = { onReject(action.id) },
                )
            }
        }

        AiRole.Action -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (mine) 14.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 14.dp,
            ),
            color = if (mine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (mine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Cartão de uma ação proposta: o rótulo humano, os argumentos e os dois botões.
 *
 * Enquanto está pendente, nada foi executado — é só uma proposta.
 */
@Composable
private fun ActionCard(
    action: AiAction,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val spec = aiToolSpec(action.tool)
    val pending = action.status == AiActionStatus.Pending
    val accent = when (action.status) {
        AiActionStatus.Pending -> MaterialTheme.colorScheme.primary
        AiActionStatus.Confirmed -> MaterialTheme.colorScheme.primary
        AiActionStatus.Rejected -> MaterialTheme.colorScheme.onSurfaceVariant
        AiActionStatus.Failed -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_habit_bolt),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = spec?.label ?: action.tool,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when (action.status) {
                        AiActionStatus.Pending -> stringResource(R.string.ai_action_pending)
                        AiActionStatus.Confirmed -> stringResource(R.string.ai_action_confirmed)
                        AiActionStatus.Rejected -> stringResource(R.string.ai_action_rejected)
                        AiActionStatus.Failed -> stringResource(R.string.ai_action_failed)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }

            val args = formatActionArgs(action.args)
            if (args.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                args.forEach { (label, value) ->
                    Text(
                        text = "$label: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!action.result.isNullOrBlank() && !pending) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = action.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (pending) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReject, enabled = enabled) {
                        Text(stringResource(R.string.ai_action_reject))
                    }
                    Button(onClick = onConfirm, enabled = enabled) {
                        Text(stringResource(R.string.ai_action_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ai_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    sending: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                maxLines = 5,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank() && !sending,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.ai_send),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
