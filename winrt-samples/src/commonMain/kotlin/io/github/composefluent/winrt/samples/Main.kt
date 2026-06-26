package io.github.composefluent.winrt.samples

import io.github.composefluent.winrt.runtime.PlatformRuntime

fun main() {
    println("kotlin-winrt samples bootstrap")
    println("host-os=${PlatformRuntime.osName}")
    val hasExplicitSmokeSelection = hasExplicitSmokeSelection()
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
    if (PlatformRuntime.isWindows && (shouldRunWinUiSmoke() || !hasExplicitSmokeSelection)) {
        runWinUiSample()
    }
}

fun shouldRunNativeSmoke(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runNativeSmoke")

fun shouldRunComponentSmoke(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runComponentSmoke")

fun shouldRunWinUiSmoke(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runWinUiSmoke")

private fun hasExplicitSmokeSelection(): Boolean =
    winRTSampleOptionConfigured("kotlin.winrt.samples.runNativeSmoke") ||
        winRTSampleOptionConfigured("kotlin.winrt.samples.runComponentSmoke") ||
        winRTSampleOptionConfigured("kotlin.winrt.samples.runWinUiSmoke")

internal expect fun runWinUiSample()
