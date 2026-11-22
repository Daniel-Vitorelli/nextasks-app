package com.daniel.nextasks.data

import kotlinx.serialization.Serializable

/**
 * Página opcional do app.
 *
 * Addons nascem **desligados**: quem não usa não vê a seção no menu. Ligar é uma
 * escolha em Configurações, e o estado fica gravado como qualquer preferência.
 */
@Serializable
enum class Addon {
    /** Assistente de IA. */
    Ai,

    /** Gamificação: pontos, sequências e conquistas. */
    Gamification,

    /** Conexões: liga entidades a blocos de tempo (confirmar o bloco conclui a entidade). */
    Connections,
}

/**
 * O addon está ligado nestas preferências?
 *
 * Serve para checar **comportamento**, não só menu: quando um addon está
 * desligado, o que ele faz também precisa parar.
 */
fun AppSettings.isAddonEnabled(addon: Addon): Boolean = addon in enabledAddons
