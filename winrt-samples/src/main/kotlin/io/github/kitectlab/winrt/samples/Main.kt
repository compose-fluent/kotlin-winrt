package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.PlatformRuntime

fun main() {
    println("kotlin-winrt samples bootstrap")
    println("host-os=${PlatformRuntime.osName}")
    if (PlatformRuntime.isWindows) {
        val result = WinRtJsonSmoke.run()
        println("winrt-json-runtime-class=${result.runtimeClass}")
        println("winrt-json-name=${result.parsedName}")
    }
}
