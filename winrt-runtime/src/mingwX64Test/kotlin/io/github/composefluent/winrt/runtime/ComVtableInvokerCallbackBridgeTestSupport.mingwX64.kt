@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer

internal actual fun invokeInt32PointerCallbackForTest(
    callbackPointer: RawAddress,
    argument: RawAddress,
): Int {
    val callback = (callbackPointer.toTestOpaquePointer() ?: error("Callback pointer must not be null."))
        .reinterpret<CFunction<(COpaquePointer?) -> Int>>()
    return callback.invoke(argument.toTestOpaquePointer())
}

private fun RawAddress.toTestOpaquePointer(): COpaquePointer? =
    value.toCPointer<COpaque>()
