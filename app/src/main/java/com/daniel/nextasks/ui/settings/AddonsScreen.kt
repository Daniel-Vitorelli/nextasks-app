package com.daniel.nextasks.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.nextasks.R
import com.daniel.nextasks.data.Addon
import com.daniel.nextasks.ui.AppDestination

/**
 * Tela de addons: liga e desliga páginas opcionais.
 *
 * Separada da lista de Configurações pelo mesmo motivo da ordem das tarefas —
 * a lista cresce e cada addon ocupa duas linhas com descrição.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddonsScreen(
    enabledAddons: Set<Addon>,
    onBack: () -> Unit,
    onToggle: (Addon, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_addons),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
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
                text = stringResource(R.string.settings_addons_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
            )

            SettingsCard(contentPadding = 0.dp) {
                Addon.entries.forEachIndexed { index, addon ->
                    AddonRow(
                        addon = addon,
                        enabled = addon in enabledAddons,
                        onToggle = { enabled -> onToggle(addon, enabled) },
                    )
                    if (index < Addon.entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Linha de Configurações que abre esta tela, com os addons ativos no subtítulo. */
@Composable
internal fun AddonsEntryRow(enabledAddons: Set<Addon>, onClick: () -> Unit) {
    val names = enabledAddons.sortedBy { it.ordinal }
        .map { stringResource(it.destination().label) }

    val subtitle = when {
        names.isEmpty() -> stringResource(R.string.settings_addons_none)
        names.size <= 2 -> names.joinToString(" · ")
        else -> stringResource(R.string.settings_addons_count, names.size)
    }

    ActionRow(
        icon = R.drawable.ic_extension,
        title = stringResource(R.string.settings_addons),
        subtitle = subtitle,
        onClick = onClick,
    )
}

/** Linha de addon: ícone, nome, o que faz e o interruptor. */
@Composable
private fun AddonRow(
    addon: Addon,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val destination = addon.destination()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(destination.label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(addon.descriptionRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

private fun Addon.destination(): AppDestination =
    AppDestination.entries.first { it.addon == this }

private fun Addon.descriptionRes(): Int = when (this) {
    Addon.Ai -> R.string.addon_ai_desc
    Addon.Gamification -> R.string.addon_gamification_desc
    Addon.Connections -> R.string.addon_connections_desc
}
