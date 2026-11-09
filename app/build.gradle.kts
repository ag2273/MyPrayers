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
 * Adhan playback (#22), two build switches:
 * - -Pmawaqeet.devAssets=<dir>: where the uncleared dev clip lives (default: <repo>/dev-assets, git-ignored).
 *   Debug builds bundle it as res/raw/adhan_placeholder when present; release builds and checkouts without it
 *   get a generated silent clip. Nothing from dev-assets is ever tracked in git.
 * - -Pmawaqeet.adhanChannelSoundFallback=true: the Play fallback. The adhan plays as the notification channel's
 *   sound and the mediaPlayback foreground service is removed from the manifest.
 */
val devAssetsDir: File = providers.gradleProperty("mawaqeet.devAssets").orNull?.let(::file) ?: rootProject.file("dev-assets")
val adhanChannelSoundFallback: Boolean =
    providers.gradleProperty("mawaqeet.adhanChannelSoundFallback").orNull?.toBoolean() ?: false

/** Writes res/raw/adhan_placeholder: the dev clip (short-adhan-dev.*) if [devAudio] holds it, else silence. */
abstract class AdhanPlaceholderTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val devAudio: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val raw = outputDir.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        val clip = devAudio.asFileTree.files.firstOrNull { it.isFile && it.name.startsWith("short-adhan-dev.") }
        if (clip != null) {
            clip.copyTo(File(raw, "adhan_placeholder.${clip.extension}"), overwrite = true)
        } else {
            File(raw, "adhan_placeholder.wav").writeBytes(silentWav(seconds = 1))
        }
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

android {
    namespace = "app.mawaqeet"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mawaqeet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "ADHAN_CHANNEL_SOUND_FALLBACK", adhanChannelSoundFallback.toString())
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
