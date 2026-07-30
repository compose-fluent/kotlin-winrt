package io.github.composefluent.winrt.benchmarks

internal actual object BenchmarkPlatform {
    actual val runner: String = "kotlin-native"
    actual val runtime: String = "Kotlin/Native ${KotlinVersion.CURRENT}; mingwX64"
}
