@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.get
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction

/**
 * Process-lifetime Windows x64 entry points for the fixed IUnknown prefix.
 * Common code defines the immutable table lookup and reference-count program;
 * Native only exports fallback callbacks and materializes executable memory.
 */
internal object Win64ManagedComIUnknownCallbacks {
    val queryInterface: RawAddress by lazy {
        allocateWin64ExecutableCode(
            code = buildManagedComQueryInterfaceStub(
                fallbackAddress = staticCFunction(::nativeInspectableQueryInterfaceFallback).rawValue.toLong(),
            ),
            description = "the mingw managed COM QueryInterface stub",
        )
    }

    val addRef: RawAddress by lazy {
        allocateWin64ExecutableCode(
            code = buildManagedComReferenceCountStub(
                minimumCurrentCount = managedComAddRefFastPathMinimumCount,
                delta = 1,
                fallbackAddress = staticCFunction(::nativeInspectableAddRefFallback).rawValue.toLong(),
            ),
            description = "the mingw managed COM AddRef stub",
        )
    }

    val release: RawAddress by lazy {
        allocateWin64ExecutableCode(
            code = buildManagedComReferenceCountStub(
                minimumCurrentCount = managedComReleaseFastPathMinimumCount,
                delta = -1,
                fallbackAddress = staticCFunction(::nativeInspectableReleaseFallback).rawValue.toLong(),
            ),
            description = "the mingw managed COM Release stub",
        )
    }
}

private fun nativeInspectableQueryInterfaceFallback(
    thisPointer: COpaquePointer?,
    interfaceIdPointer: COpaquePointer?,
    resultPointer: COpaquePointer?,
): Int {
    if (interfaceIdPointer == null) {
        return WinRTInspectableComObject.invokeQueryInterfaceCallback(
            thisPointer = thisPointer.asManagedCallbackRawAddress(),
            interfaceIdPointer = RawAddress.Null,
            resultPointer = resultPointer.asManagedCallbackRawAddress(),
        )
    }
    val interfaceIdWords = interfaceIdPointer.reinterpret<LongVar>()
    return WinRTInspectableComObject.invokeQueryInterfaceCallbackByAbiWords(
        thisPointer = thisPointer.asManagedCallbackRawAddress(),
        interfaceIdLowBits = interfaceIdWords[0],
        interfaceIdHighBits = interfaceIdWords[1],
        resultPointer = resultPointer.asManagedCallbackRawAddress(),
    )
}

private fun nativeInspectableAddRefFallback(thisPointer: COpaquePointer?): Int =
    WinRTInspectableComObject.invokeAddRefCallback(thisPointer.asManagedCallbackRawAddress())

private fun nativeInspectableReleaseFallback(thisPointer: COpaquePointer?): Int =
    WinRTInspectableComObject.invokeReleaseCallback(thisPointer.asManagedCallbackRawAddress())

private fun COpaquePointer?.asManagedCallbackRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)
