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
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
