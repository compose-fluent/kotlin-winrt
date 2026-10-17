package io.github.composefluent.winrt.benchmarks

// The CLI rejects Native-specific diagnostics on JVM; ordinary JVM runs never call this.
internal actual fun benchmarkNativeGcSnapshot(): BenchmarkNativeGcSnapshot? = null
