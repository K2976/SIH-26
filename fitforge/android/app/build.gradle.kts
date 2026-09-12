import io.gitlab.arturbosch.detekt.Detekt
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
            // Defaults to the deployed Render backend so a debug build works
            // over Wi-Fi/mobile data with no cable and no local server. Pass
            // -PDEBUG_API_BASE_URL=http://<laptop-lan-ip>:8000/ (find it via
            // `ipconfig getifaddr en0`) to test against a local
            // `runserver 0.0.0.0:8000` instead — same-Wi-Fi only. No
            // /api/v1/ suffix here — the generated API interfaces already
            // carry it in each @GET/@POST path.
            val debugBaseUrl = (project.findProperty("DEBUG_API_BASE_URL") as String?)
                ?: System.getenv("DEBUG_API_BASE_URL")
                ?: "https://fitforge-backend-f7ow.onrender.com/"
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            // Configurable so CI/local release builds can point at the
            // deployed Render URL without editing this file — pass
            // -PRELEASE_API_BASE_URL=... or set the env var of the same
            // name. Falls back to the confirmed deployed URL — Render
            // appended "-f7ow" since the bare "fitforge-backend" name was
            // unavailable; don't assume it matches the service name.
            val releaseBaseUrl = (project.findProperty("RELEASE_API_BASE_URL") as String?)
                ?: System.getenv("RELEASE_API_BASE_URL")
                ?: "https://fitforge-backend-f7ow.onrender.com/"
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
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

    // MediaPipe's setModelAssetPath mmaps the .task file directly; AGP's default
    // asset compression breaks that. Keep these stored, not deflated.
    androidResources {
        noCompress += listOf("task", "tflite")
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

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}

val preparePhoneBackend = tasks.register<Exec>("preparePhoneBackend") {
    group = "installation"
    description = "Starts the local backend and configures ADB reverse for debug installs"
    workingDir(rootDir)
    commandLine(
        "bash",
        rootDir.resolve("../scripts/prepare_phone_backend.sh").absolutePath,
    )
}

// A command-line debug install targets a connected device, so prepare the
// localhost backend bridge first. assembleDebug remains side-effect free for CI.
tasks.matching { it.name == "installDebug" }.configureEach {
    dependsOn(preparePhoneBackend)
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

    // openapi-generator 7.14.0's Kotlin template unconditionally emits
    // NullEnum.kt as a zero-entry enum whenever the spec has ANY nullable
    // field, regardless of whether anything actually needs it — and a
    // zero-entry enum is itself invalid Kotlin syntax, which fails the
    // whole module's compile. NullEnum is never referenced by any other
    // generated file (verified: `grep -rl NullEnum` finds only the file
    // itself), so deleting it removes dead, broken code and nothing else.
    // No newer generator version exists yet to fix this upstream — this is
    // a targeted workaround for a confirmed generator bug, not a schema or
    // behavior change. Revisit when openapi-generator ships a fix.
    doLast {
        val nullEnumFile = file(
            "$openApiGeneratedDir/src/main/kotlin/com/fitforge/app/data/remote/generated/models/NullEnum.kt"
        )
        if (nullEnumFile.exists()) {
            nullEnumFile.delete()
        }
    }
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
    implementation(libs.androidx.health.connect)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
