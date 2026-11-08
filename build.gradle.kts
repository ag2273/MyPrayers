plugins {
    alias(libs.plugins.android.application) apply false
    // Declaring KGP here pins the Kotlin version AGP 9's built-in Kotlin uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
