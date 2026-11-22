// Build script raiz: declara os plugins usados pelos módulos, sem aplicá-los aqui.
// O suporte a Kotlin é embutido no AGP 9 (built-in Kotlin), então o plugin
// "org.jetbrains.kotlin.android" não é mais aplicado.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
