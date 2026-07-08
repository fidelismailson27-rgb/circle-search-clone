// Root build file — plugin versions are declared once here (via the version
// catalog, gradle/libs.versions.toml) and applied with `apply false`, so each
// module applies only the plugins it actually needs without re-resolving versions.
//
// No `org.jetbrains.kotlin.android` plugin here: AGP 9.0+ has built-in Kotlin
// support and that plugin is no longer required (or accepted) — verified against
// the CI build failure this produced and Android's migrate-to-built-in-kotlin guide.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// T004: static analysis, applied uniformly to every module (currently just :app).
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        ignoreFailures.set(false)
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        autoCorrect = false
    }
}
