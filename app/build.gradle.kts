import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin: do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/*
 * Adhan playback (#22), these build switches:
 * - -Pmawaqeet.adhanAudio=<file>: the Short Adhan every build (release too) bundles as res/raw/adhan_placeholder.
 * - -Pmawaqeet.adhanFullAudio=<file>: the Full Adhan (all Prayers but Fajr), bundled as res/raw/adhan_full.
 * - -Pmawaqeet.adhanFajrAudio=<file>: Fajr's own Full Adhan («الصلاة خير من النوم»), bundled as res/raw/adhan_fajr_full.
 *   These recordings stay outside the repo, since the repo is public and they aren't ours to publish. Each wins over
 *   devAssets; a missing Full or Fajr recording falls back to the Short Adhan's clip.
 * - -Pmawaqeet.devAssets=<dir>: where the uncleared dev clip lives (default: <repo>/dev-assets, git-ignored).
 *   Debug builds bundle it as res/raw/adhan_placeholder when present; release builds and checkouts without it
 *   get a generated silent clip. Nothing from dev-assets is ever tracked in git.
 * - -Pmawaqeet.adhanChannelSoundFallback=true: the Play fallback. The adhan plays as the notification channel's
 *   sound and the mediaPlayback foreground service is removed from the manifest.
 */
val devAssetsDir: File = providers.gradleProperty("mawaqeet.devAssets").orNull?.let(::file) ?: rootProject.file("dev-assets")
val adhanAudioFile: File? = providers.gradleProperty("mawaqeet.adhanAudio").orNull?.let(::file)
val adhanFullAudioFile: File? = providers.gradleProperty("mawaqeet.adhanFullAudio").orNull?.let(::file)
val adhanFajrAudioFile: File? = providers.gradleProperty("mawaqeet.adhanFajrAudio").orNull?.let(::file)
val adhanChannelSoundFallback: Boolean =
    providers.gradleProperty("mawaqeet.adhanChannelSoundFallback").orNull?.toBoolean() ?: false

/**
 * Writes the bundled adhans to res/raw. adhan_placeholder (the Short Adhan): the [adhanAudio] recording if given, else
 * the dev clip (short-adhan-dev.*) if [devAudio] holds it, else silence. adhan_full and adhan_fajr_full: [fullAudio]
 * and [fajrAudio] if given, else the same clip as adhan_placeholder.
 */
abstract class AdhanPlaceholderTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val adhanAudio: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val devAudio: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fullAudio: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val fajrAudio: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val raw = outputDir.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        val clip = adhanAudio.files.firstOrNull { it.isFile }
            ?: devAudio.asFileTree.files.firstOrNull { it.isFile && it.name.startsWith("short-adhan-dev.") }
        fun write(name: String, recording: File?) = when {
            recording != null -> recording.copyTo(File(raw, "$name.${recording.extension}"), overwrite = true)
            else -> File(raw, "$name.wav").also { it.writeBytes(silentWav(seconds = 1)) }
        }
        write("adhan_placeholder", clip)
        write("adhan_full", fullAudio.files.firstOrNull { it.isFile } ?: clip)
        write("adhan_fajr_full", fajrAudio.files.firstOrNull { it.isFile } ?: clip)
    }

    /** 8 kHz, 8-bit mono PCM WAV of silence (8-bit PCM silence is 0x80). */
    private fun silentWav(seconds: Int): ByteArray {
        val rate = 8000
        val data = rate * seconds
        val out = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate).putShort(1).putShort(8)
        out.put("data".toByteArray()).putInt(data)
        repeat(data) { out.put(0x80.toByte()) }
        return out.array()
    }
}

/*
 * Release signing. The keystore and its passwords never live in the repo: they come from Gradle properties
 * (~/.gradle/gradle.properties or -P) or the environment, as below. Without them the release APK is left unsigned.
 *   mawaqeet.signing.storeFile / MAWAQEET_SIGNING_STORE_FILE        path to the .jks keystore
 *   mawaqeet.signing.storePassword / MAWAQEET_SIGNING_STORE_PASSWORD
 *   mawaqeet.signing.keyAlias / MAWAQEET_SIGNING_KEY_ALIAS
 *   mawaqeet.signing.keyPassword / MAWAQEET_SIGNING_KEY_PASSWORD
 */
fun signingValue(name: String): String? =
    providers.gradleProperty("mawaqeet.signing.$name").orNull
        ?: providers.environmentVariable("MAWAQEET_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()).orNull
val releaseStoreFile: String? = signingValue("storeFile")

android {
    namespace = "app.mawaqeet"
    compileSdk = 37

    defaultConfig {
        // The Play package name (fixed by the first upload). The code namespace stays app.mawaqeet.
        applicationId = "app.nileorbit.mawaqeet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "ADHAN_CHANNEL_SOUND_FALLBACK", adhanChannelSoundFallback.toString())
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Arabic is the unqualified default locale (res/resources.properties, ADR 0002).
        generateLocaleConfig = true
        localeFilters += listOf("ar", "en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric on SDK 36 + JDK 21 needs this, else "Failed to interact with raw FileDescriptor internals".
            it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            it.maxHeapSize = "1g"
        }
    }
}

if (adhanChannelSoundFallback) {
    android.sourceSets.getByName("debug").manifest.srcFile("src/channelSoundFallback/AndroidManifest.xml")
    android.sourceSets.getByName("release").manifest.srcFile("src/channelSoundFallback/AndroidManifest.xml")
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val placeholder = tasks.register<AdhanPlaceholderTask>("generate${name}AdhanPlaceholder") {
            adhanAudioFile?.let { adhanAudio.from(it) }
            adhanFullAudioFile?.let { fullAudio.from(it) }
            adhanFajrAudioFile?.let { fajrAudio.from(it) }
            if (variant.buildType == "debug") devAudio.from(devAssetsDir)
        }
        variant.sources.res?.addGeneratedSourceDirectory(placeholder, AdhanPlaceholderTask::outputDir)
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    // Exported schemas are committed so later migrations can be tested against them.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.glance.appwidget)

    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
