package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.PlatformRuntime

fun main() {
    println("kotlin-winrt samples bootstrap")
    println("host-os=${PlatformRuntime.osName}")
    if (PlatformRuntime.isWindows && shouldRunNativeSmoke()) {
        val result = JsonApiCompatSample.run()
        println("json-id=${result.id}")
        println("json-null-type=${result.nullValueType}")
        println("json-verified=${result.verified}")
        println("json-first-education-type=${result.firstEducationType}")
    }
    if (PlatformRuntime.isWindows && shouldRunComponentSmoke()) {
        val result = NetProjectionSample.add()
        println("simple-math=${result.expression} = ${result.value}")
    }
    if (PlatformRuntime.isWindows && shouldRunWinUiSmoke()) {
        runWinUiSample()
    }
}

fun shouldRunNativeSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runNativeSmoke")

fun shouldRunComponentSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runComponentSmoke")

fun shouldRunWinUiSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runWinUiSmoke")

private fun runWinUiSample() {
    val sampleClass = Class.forName("io.github.composefluent.winrt.samples.WinUiControlsSample")
    val instance = sampleClass.getField("INSTANCE").get(null)
    sampleClass.getMethod("start").invoke(instance)
}
