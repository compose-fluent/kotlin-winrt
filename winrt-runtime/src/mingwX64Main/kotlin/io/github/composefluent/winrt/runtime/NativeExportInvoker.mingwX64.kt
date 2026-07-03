@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke
import kotlinx.cinterop.toCPointer

actual object WinRTNativeExportInvoker {
    actual fun invokeHResultAddressAddress(
        function: RawAddress,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int =
        function.asHResultAddressAddress().invoke(arg0.asOpaquePointer(), arg1.asOpaquePointer())

    actual fun invokeHResultStruct8Address(
        function: RawAddress,
        structValue: RawAddress,
        arg1: RawAddress,
    ): Int =
        function.asHResultStruct8Address().invoke(
            PlatformAbi.readInt64(structValue).toULong(),
            arg1.asOpaquePointer(),
        )

    private fun RawAddress.asHResultAddressAddress(): CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>> =
        value.toCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>()
            ?: error("Cannot call a null native export function pointer.")

    private fun RawAddress.asHResultStruct8Address(): CPointer<CFunction<(ULong, COpaquePointer?) -> Int>> =
        value.toCPointer<CFunction<(ULong, COpaquePointer?) -> Int>>()
            ?: error("Cannot call a null native export function pointer.")

    private fun RawAddress.asOpaquePointer(): COpaquePointer? =
        value.toCPointer()
}
