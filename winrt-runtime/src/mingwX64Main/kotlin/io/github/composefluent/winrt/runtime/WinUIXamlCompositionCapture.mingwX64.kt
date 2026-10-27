@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.*

internal actual fun platformCreateXamlD3DDevice(procedure: RawAddress, driver: Int, output: RawAddress): Int =
    requireNotNull(procedure.value.toCPointer<CFunction<(COpaquePointer?, Int, COpaquePointer?, UInt,
        COpaquePointer?, UInt, UInt, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>>())
        .invoke(null, driver, null, 0x20u, null, 0u, 7u, output.value.toCPointer<COpaque>(), null, null)

internal actual fun platformWrapXamlD3DDevice(procedure: RawAddress, device: RawAddress, output: RawAddress): Int =
    invokeCapturePointerPair(procedure, device, output)

private fun invokeCapturePointerPair(procedure: RawAddress, device: RawAddress, output: RawAddress): Int =
    requireNotNull(procedure.value.toCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>())
        .invoke(device.value.toCPointer<COpaque>(), output.value.toCPointer<COpaque>())

internal actual fun platformReadXamlCaptureWindow(procedure: RawAddress, window: RawAddress, output: RawAddress): Int =
    invokeCapturePointerPair(procedure, window, output)

internal actual fun platformXamlCaptureDwmAttribute(procedure: RawAddress, window: RawAddress, attribute: Int, output: RawAddress, bytes: Int): Int =
    requireNotNull(procedure.value.toCPointer<CFunction<(COpaquePointer?, UInt, COpaquePointer?, UInt) -> Int>>())
        .invoke(window.value.toCPointer<COpaque>(), attribute.toUInt(), output.value.toCPointer<COpaque>(), bytes.toUInt())
