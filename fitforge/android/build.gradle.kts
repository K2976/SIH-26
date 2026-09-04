buildscript {
    dependencies {
        // AGP 9's built-in Kotlin support depends on KGP at runtime; pin it
        // explicitly rather than accepting AGP's minimum-required default.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.openapi.generator) apply false
}
