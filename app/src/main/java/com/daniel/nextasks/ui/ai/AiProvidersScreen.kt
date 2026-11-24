package com.daniel.nextasks.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.nextasks.R
import com.daniel.nextasks.data.AiChatResult
import com.daniel.nextasks.data.AiClient
import com.daniel.nextasks.data.AiProvider
import com.daniel.nextasks.data.AiProviderRepository
import com.daniel.nextasks.data.ChatTurn
import kotlinx.coroutines.launch

/**
 * Configuração dos provedores de IA, em fila.
 *
 * A ordem da lista **é** o fallback: se o primeiro falhar (rede, chave, limite), o
 * próximo responde. A chave fica no aparelho e não entra no backup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProvidersScreen(
    providerRepository: AiProviderRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by providerRepository.providers.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiProvider?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.ai_providers_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.task_cancel),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.ai_providers_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            creating = true
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_provider_add))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (providers.size > 1) {
                        Text(
                            text = stringResource(R.string.ai_provider_order),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (providers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_provider_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(providers, key = { _, item -> item.id }) { index, provider ->
                            ProviderCard(
                                provider = provider,
                                position = index + 1,
                                canMoveUp = index > 0,
                                canMoveDown = index < providers.lastIndex,
                                onUp = { scope.launch { providerRepository.moveUp(provider.id) } },
                                onDown = { scope.launch { providerRepository.moveDown(provider.id) } },
                                onToggle = {
                                    scope.launch {
                                        providerRepository.save(provider.copy(enabled = !provider.enabled))
                                    }
                                },
                                onEdit = { editing = provider },
                                onDelete = { deleteTarget = provider },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        AiProviderEditor(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { provider ->
                scope.launch { providerRepository.save(provider) }
                creating = false
                editing = null
            },
        )
    }

    deleteTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.ai_provider_delete_title)) },
            text = { Text(stringResource(R.string.ai_provider_delete_body, provider.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { providerRepository.delete(provider.id) }
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.task_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.task_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$position",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${provider.model} · ${provider.baseUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = provider.enabled, onCheckedChange = { onToggle() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (provider.apiKey.isBlank()) {
                        stringResource(R.string.ai_provider_key_empty)
                    } else {
                        "•••• " + provider.apiKey.takeLast(4)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onUp, enabled = canMoveUp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up),
                        contentDescription = stringResource(R.string.ai_provider_up),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDown, enabled = canMoveDown) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = stringResource(R.string.ai_provider_down),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.ai_provider_edit),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.task_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Editor do provedor, com teste de conexão antes de salvar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderEditor(
    initial: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { AiClient() }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by rememberSaveable { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by rememberSaveable { mutableStateOf(initial?.apiKey.orEmpty()) }
    var model by rememberSaveable { mutableStateOf(initial?.model ?: "gpt-4o-mini") }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val complete = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(
                                if (initial == null) R.string.ai_provider_new else R.string.ai_provider_edit,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.task_cancel),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = complete,
                            onClick = {
                                val provider = (initial ?: AiProvider()).copy(
                                    name = name.trim(),
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    enabled = enabled,
                                )
                                onSave(provider)
                            },
                        ) {
                            Text(stringResource(R.string.task_save))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.ai_provider_name)) },
                        placeholder = { Text(stringResource(R.string.ai_provider_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.ai_provider_url)) },
                        placeholder = { Text(stringResource(R.string.ai_provider_url_hint)) },
                        singleLine = true,
                        // URL e modelo são técnicos: teclado sem maiúscula automática,
                        // sem corretor e sem dica. A NVIDIA, por exemplo, recusa o modelo
                        // "Nvidia/..." com 404 — a maiúscula que o teclado colocava sozinho
                        // quebrava a conexão.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.ai_provider_key)) },
                        placeholder = { Text(stringResource(R.string.ai_provider_key_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.ai_provider_model)) },
                        placeholder = { Text(stringResource(R.string.ai_provider_model_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.ai_provider_enabled),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = complete && !testing,
                            onClick = {
                                scope.launch {
                                    testing = true
                                    testResult = null
                                    val probe = (initial ?: AiProvider()).copy(
                                        name = name.trim(),
                                        baseUrl = baseUrl.trim(),
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                        enabled = true,
                                    )
                                    val result = client.complete(
                                        providers = listOf(probe),
                                        turns = listOf(
                                            ChatTurn("user", "Responda apenas com a palavra OK."),
                                        ),
                                        maxTokens = 16,
                                    )
                                    testResult = when (result) {
                                        is AiChatResult.Ok -> true to result.text.take(80)
                                        is AiChatResult.Failure -> false to result.message.take(160)
                                    }
                                    testing = false
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (testing) R.string.ai_provider_testing else R.string.ai_provider_test,
                                ),
                            )
                        }
                        testResult?.let { (ok, message) ->
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(
                                    if (ok) R.string.ai_provider_ok else R.string.ai_provider_fail,
                                    message,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
