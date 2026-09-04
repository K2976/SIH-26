import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
}

// No plugins{} marker artifact exists for this plugin — see the classpath
// comment in the root build.gradle.kts. Its real id is "org.openapi.generator"
// (not the org.openapitools.* group/artifact coordinate it's published under).
apply(plugin = "org.openapi.generator")

android {
    namespace = "com.fitforge.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.fitforge.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        // Matches the backend's default MIN_SUPPORTED_VERSION (base.py) so a
        // fresh checkout is ungated by default; raising MIN_SUPPORTED_VERSION
        // above this is what demonstrates the update gate.
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // localhost + `adb reverse tcp:8000 tcp:8000` works for both the
            // emulator and a USB-connected physical device; 10.0.2.2 only
            // works for the emulator. No /api/v1/ suffix here — the
            // generated API interfaces already carry it in each @GET/@POST path.
            buildConfigField("String", "BASE_URL", "\"http://localhost:8000/\"")
        }
        release {
            isMinifyEnabled = false
            // ponytail: placeholder until a real deployment exists.
            buildConfigField("String", "BASE_URL", "\"https://api.fitforge.app/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // AGP 9's sourceSets API rejects a lazy Provider<Directory> here
            // ("You cannot add Provider instances to the Android SourceSet
            // API") — resolve it to a plain File eagerly instead.
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin").get().asFile)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi").get().asFile

// Retrofit client generated from the backend's OpenAPI schema (see
// CLAUDE.md "API" convention) — never hand-write DTOs that mirror
// serializers, the schema is the single source of truth.
tasks.named<GenerateTask>("openApiGenerate") {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set("$rootDir/openapi/schema.yaml")
    outputDir.set(openApiGeneratedDir.path)
    packageName.set("com.fitforge.app.data.remote.generated")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "kotlinx-datetime"
        )
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// Both the Kotlin compiler and KSP (Hilt/Room) consume the generated
// client, so both task families must wait for it — relying on just one
// is the classic "clean build fails, incremental build passes" trap.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("openApiGenerate")
}
tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("openApiGenerate")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    // Required by the generated client's own ApiClient.kt infrastructure
    // file, even though we never use that class directly.
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.datetime)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
