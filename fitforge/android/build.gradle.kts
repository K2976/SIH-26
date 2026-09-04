buildscript {
    dependencies {
        // AGP 9's built-in Kotlin support depends on KGP at runtime; pin it
        // explicitly rather than accepting AGP's minimum-required default.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        // openapi-generator-gradle-plugin publishes no Gradle plugin marker
        // artifact (neither on Maven Central nor the Plugin Portal), so it
        // can't be applied via the `plugins {}` DSL — classpath + legacy
        // apply(plugin = ...) in app/build.gradle.kts is the only way in.
        classpath("org.openapitools:openapi-generator-gradle-plugin:${libs.versions.openapiGenerator.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}
