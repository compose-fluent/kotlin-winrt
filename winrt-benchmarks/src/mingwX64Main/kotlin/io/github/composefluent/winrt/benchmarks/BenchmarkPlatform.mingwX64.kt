@file:OptIn(ExperimentalNativeApi::class)

package io.github.composefluent.winrt.benchmarks

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual object BenchmarkPlatform {
    private var resultSink: Any? = null

    actual val runner: String = "kotlin-native"
    actual val runtime: String = "Kotlin/Native ${KotlinVersion.CURRENT}; mingwX64"

    actual fun consumeResult(value: Any?) {
        resultSink = value
    }
}
