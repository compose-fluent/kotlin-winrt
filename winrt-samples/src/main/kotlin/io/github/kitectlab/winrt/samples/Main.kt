package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.PlatformRuntime

fun main() {
    println("kotlin-winrt samples bootstrap")
    println("host-os=${PlatformRuntime.osName}")
    if (PlatformRuntime.isWindows && shouldRunNativeSmoke()) {
        val result = JsonApiCompatSample.run()
        println("json-id=${result.id}")
        println("json-null-type=${result.nullValueType}")
        println("json-verified=${result.verified}")
    }
}

fun shouldRunNativeSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runNativeSmoke")
