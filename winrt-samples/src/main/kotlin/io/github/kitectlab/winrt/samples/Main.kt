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
        println("json-first-education-type=${result.firstEducationType}")
    }
    if (PlatformRuntime.isWindows && shouldRunComponentSmoke()) {
        val result = NetProjectionSample.add()
        println("simple-math=${result.expression} = ${result.value}")
    }
    if (shouldRunWinUiDesktopHeadlessSmoke()) {
        val result = WinUiDesktopSample.runHeadlessFlow()
        println("winui-window-activated=${result.windowActivated}")
        println("winui-content=${result.finalWindowContent}")
        println("winui-tapped-handler=${result.tappedHandlerRegistered}")
    }
}

fun shouldRunNativeSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runNativeSmoke")

fun shouldRunComponentSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runComponentSmoke")

fun shouldRunWinUiDesktopHeadlessSmoke(): Boolean =
    java.lang.Boolean.getBoolean("kotlin.winrt.samples.runWinUiDesktopHeadlessSmoke")
