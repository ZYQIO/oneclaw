import com.android.build.api.variant.impl.VariantOutputImpl
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

val dnsjavaInetAddressResolverService = "META-INF/services/java.net.spi.InetAddressResolverProvider"
val openclawRepoRootDir = rootProject.projectDir.resolve("../..").canonicalFile

abstract class PrepareEmbeddedRuntimePodAssetsTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val repoRootPath: Property<String>

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:InputFile
    abstract val scriptFile: RegularFileProperty

    @get:OutputDirectory
    abstract val artifactDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val clean: Property<Boolean>

    init {
        clean.convention(true)
    }

    @TaskAction
    fun generate() {
        val repoRoot = File(repoRootPath.get())
        val command =
            mutableListOf(
                "pnpm",
                "exec",
                "tsx",
                scriptFile.get().asFile.absolutePath,
                "--repo-root",
                repoRoot.absolutePath,
                "--source-dir",
                sourceDir.get().asFile.absolutePath,
                "--artifact-dir",
                artifactDir.get().asFile.absolutePath,
                "--target-dir",
                outputDir.get().asFile.absolutePath,
            )
        if (!clean.get()) {
            command += "--no-clean"
        }

        execOperations.exec {
            workingDir = repoRoot
            commandLine(command)
        }
    }
}

val androidStoreFile = providers.gradleProperty("OPENCLAW_ANDROID_STORE_FILE").orNull?.takeIf { it.isNotBlank() }
val androidStorePassword = providers.gradleProperty("OPENCLAW_ANDROID_STORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val androidKeyAlias = providers.gradleProperty("OPENCLAW_ANDROID_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
val androidKeyPassword = providers.gradleProperty("OPENCLAW_ANDROID_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val resolvedAndroidStoreFile =
    androidStoreFile?.let { storeFilePath ->
        if (storeFilePath.startsWith("~/")) {
            "${System.getProperty("user.home")}/${storeFilePath.removePrefix("~/")}"
        } else {
            storeFilePath
        }
    }

val hasAndroidReleaseSigning =
    listOf(resolvedAndroidStoreFile, androidStorePassword, androidKeyAlias, androidKeyPassword).all { it != null }

val wantsAndroidReleaseBuild =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true) ||
            Regex("""(^|:)(bundle|assemble)$""").containsMatchIn(taskName)
    }

if (wantsAndroidReleaseBuild && !hasAndroidReleaseSigning) {
    error(
        "Missing Android release signing properties. Set OPENCLAW_ANDROID_STORE_FILE, " +
            "OPENCLAW_ANDROID_STORE_PASSWORD, OPENCLAW_ANDROID_KEY_ALIAS, and " +
            "OPENCLAW_ANDROID_KEY_PASSWORD in ~/.gradle/gradle.properties.",
    )
}

plugins {
    id("com.android.application")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ai.openclaw.app"
    compileSdk = 36

    // Release signing is local-only; keep the keystore path and passwords out of the repo.
    signingConfigs {
        if (hasAndroidReleaseSigning) {
            create("release") {
                storeFile = project.file(checkNotNull(resolvedAndroidStoreFile))
                storePassword = checkNotNull(androidStorePassword)
                keyAlias = checkNotNull(androidKeyAlias)
                keyPassword = checkNotNull(androidKeyPassword)
            }
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("../../shared/OpenClawKit/Sources/OpenClawKit/Resources")
        }
    }

    defaultConfig {
        applicationId = "ai.openclaw.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 2026031400
        versionName = "2026.3.14"
        ndk {
            // Support all major ABIs — native libs are tiny (~47 KB per ABI)
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            if (hasAndroidReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/*.version",
                    "/META-INF/LICENSE*.txt",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
                    "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
                    "org/bouncycastle/x509/CertPathReviewerMessages*.properties",
                )
        }
    }

    lint {
        disable +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "IconLauncherShape",
                "NewerVersionAvailable",
            )
        warningsAsErrors = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        val variantName =
            variant.name.replaceFirstChar { firstChar ->
                if (firstChar.isLowerCase()) {
                    firstChar.titlecase()
                } else {
                    firstChar.toString()
                }
            }
        val prepareEmbeddedRuntimePodAssets =
            tasks.register<PrepareEmbeddedRuntimePodAssetsTask>("prepare${variantName}EmbeddedRuntimePodAssets") {
                repoRootPath.set(openclawRepoRootDir.absolutePath)
                sourceDir.set(openclawRepoRootDir.resolve("apps/android/runtime-pod"))
                scriptFile.set(openclawRepoRootDir.resolve("apps/android/scripts/local-host-embedded-runtime-pod-sync-assets.ts"))
                artifactDir.set(layout.buildDirectory.dir("intermediates/embedded-runtime-pod/${variant.name}/prepared"))
                outputDir.set(layout.buildDirectory.dir("generated/embedded-runtime-pod-assets/${variant.name}"))
            }
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareEmbeddedRuntimePodAssets,
            PrepareEmbeddedRuntimePodAssetsTask::outputDir,
        )
        variant.outputs
            .filterIsInstance<VariantOutputImpl>()
            .forEach { output ->
                val versionName = output.versionName.orNull ?: "0"
                val buildType = variant.buildType

                val outputFileName = "openclaw-$versionName-$buildType.apk"
                output.outputFileName = outputFileName
            }
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.webkit:webkit:1.15.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // material-icons-extended pulled in full icon set (~20 MB DEX). Only ~18 icons used.
    // R8 will tree-shake unused icons when minify is enabled on release builds.
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material Components (XML theme + resources)
    implementation("com.google.android.material:material:1.13.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-autolink:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.27.1")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.27.1")

    // CameraX (for node.invoke camera.* parity)
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-video:1.5.2")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Unicast DNS-SD (Wide-Area Bonjour) for tailnet discovery domains.
    implementation("dnsjava:dnsjava:3.6.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.1.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val stripReleaseDnsjavaServiceDescriptor =
    tasks.register("stripReleaseDnsjavaServiceDescriptor") {
        val mergedJar =
            layout.buildDirectory.file(
                "intermediates/merged_java_res/release/mergeReleaseJavaResource/base.jar",
            )

        inputs.file(mergedJar)
        outputs.file(mergedJar)

        doLast {
            val jarFile = mergedJar.get().asFile
            if (!jarFile.exists()) {
                return@doLast
            }

            val unpackDir = temporaryDir.resolve("merged-java-res")
            delete(unpackDir)
            copy {
                from(zipTree(jarFile))
                into(unpackDir)
                exclude(dnsjavaInetAddressResolverService)
            }
            delete(jarFile)
            ant.invokeMethod(
                "zip",
                mapOf(
                    "destfile" to jarFile.absolutePath,
                    "basedir" to unpackDir.absolutePath,
                ),
            )
        }
    }

tasks.matching { it.name == "stripReleaseDnsjavaServiceDescriptor" }.configureEach {
    dependsOn("mergeReleaseJavaResource")
}

tasks.matching { it.name == "minifyReleaseWithR8" }.configureEach {
    dependsOn(stripReleaseDnsjavaServiceDescriptor)
}
