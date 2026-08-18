package io.github.composefluent.winrt.benchmarks

internal actual object BenchmarkPlatform {
    @Volatile
    private var resultSink: Any? = null

    actual val runner: String = "kotlin-jvm"
    actual val runtime: String = buildString {
        append("Kotlin/JVM ")
        append(KotlinVersion.CURRENT)
        append("; Java ")
        append(System.getProperty("java.runtime.version"))
        append("; ")
        append(System.getProperty("java.vm.name"))
    }

    actual fun consumeResult(value: Any?) {
        resultSink = value
    }
}
