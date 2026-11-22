package com.daniel.nextasks.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.daniel.nextasks.data.AccentColor

/**
 * Mantém habilitado só o alias de launcher da cor escolhida.
 *
 * O Android não deixa trocar o ícone de um componente em tempo de execução, mas
 * deixa escolher **qual** componente é o ponto de entrada. Cada cor tem o seu
 * `activity-alias` no manifesto, e aqui só um fica habilitado — o sistema usa o
 * ícone desse alias na gaveta e na splash.
 */
internal object AppIcon {

    fun sync(context: Context, accent: AccentColor) {
        val manager = context.packageManager
        val pkg = context.packageName

        // Habilita o alvo antes de desabilitar os outros: se algo falhar no meio
        // do caminho, o app fica com dois ícones (recuperável) em vez de nenhum.
        enable(manager, ComponentName(pkg, "$pkg.Launcher${accent.name}"), true)

        AccentColor.entries.forEach { option ->
            if (option == accent) return@forEach
            enable(manager, ComponentName(pkg, "$pkg.Launcher${option.name}"), false)
        }
    }

    private fun enable(manager: PackageManager, component: ComponentName, enabled: Boolean) {
        val desired = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        if (manager.getComponentEnabledSetting(component) != desired) {
            // DONT_KILL_APP: trocar o ícone não pode derrubar a sessão atual.
            manager.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
        }
    }
}
