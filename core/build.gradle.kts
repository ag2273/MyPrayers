plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Pure Kotlin/JVM: no Android dependencies allowed in this module.
    // adhan-kotlin is an implementation detail of the core: its types never appear in the public interface.
    implementation(libs.adhan)
    implementation(libs.kotlinx.datetime)
    // The Backup File's JSON, through the JsonElement tree only (no serialization compiler plugin).
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    // CityDirectoryTest reads the bundled city list the app ships.
    inputs.file(rootProject.file("app/src/main/assets/cities.tsv"))
    // AdhkarCatalogTest reads the bundled adhkar.
    inputs.file(rootProject.file("app/src/main/assets/adhkar.tsv"))
}
