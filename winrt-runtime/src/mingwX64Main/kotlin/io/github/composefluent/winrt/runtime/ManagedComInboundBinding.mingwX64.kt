@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.native.internal.InternalForKotlinNative::class,
)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.toCPointer
import kotlin.native.internal.NativePtr
import kotlin.native.internal.ref.createUnretainedExternalRCRef
import kotlin.native.internal.ref.dereferenceExternalRCRefOrNull
import kotlin.native.internal.ref.disposeExternalRCRef

@PublishedApi
internal actual class PlatformManagedComInboundWeakReference actual constructor(value: Any) : AutoCloseable {
    @PublishedApi
    internal var reference = createUnretainedExternalRCRef(value)

    @PublishedApi
    internal actual inline fun get(): Any? = dereferenceExternalRCRefOrNull(reference)

    actual override fun close() {
        val current = reference
        reference = NativePtr.NULL
        disposeExternalRCRef(current)
    }
}

internal actual fun platformCreateManagedComInboundBindingHandle(
    binding: ManagedComInboundBinding,
    @Suppress("UNUSED_PARAMETER") canonicalObjectMemory: RawAddress,
): RawAddress = RawAddress(
    StableRef.create(binding).asCPointer().rawValue.toLong(),
)

internal actual fun platformDisposeManagedComInboundBindingHandle(
    @Suppress("UNUSED_PARAMETER") binding: ManagedComInboundBinding,
    platformHandle: RawAddress,
) {
    platformHandle.value
        .toCPointer<COpaque>()
        ?.asStableRef<ManagedComInboundBinding>()
        ?.dispose()
}

internal actual fun platformCreateInspectableQueryInterfaceCallback(): NativeCallbackHandle =
    NativeCallbackHandle(
        pointer = Win64ManagedComIUnknownCallbacks.queryInterface,
        onClose = {},
    )

internal actual fun platformCreateInspectableAddRefCallback(): NativeCallbackHandle =
    NativeCallbackHandle(
        pointer = Win64ManagedComIUnknownCallbacks.addRef,
        onClose = {},
    )

internal actual fun platformCreateInspectableReleaseCallback(): NativeCallbackHandle =
    NativeCallbackHandle(
        pointer = Win64ManagedComIUnknownCallbacks.release,
        onClose = {},
    )

@PublishedApi
internal actual inline fun platformWinRTProjectionInboundBinding(
    thisWord: Long,
): ManagedComInboundBinding? {
    if (thisWord == 0L) return null
    val bindingReference =
        (thisWord + managedComInboundBindingSlot * Long.SIZE_BYTES.toLong())
            .toCPointer<LongVar>()
            ?.get(0)
            ?: 0L
    if (bindingReference == 0L) return null
    return bindingReference
        .toCPointer<COpaque>()
        ?.asStableRef<ManagedComInboundBinding>()
        ?.get()
}

internal actual fun platformTryWinRTProjectionInboundBinding(thisWord: Long): ManagedComInboundBinding? {
    if (thisWord == 0L) return null
    val vtable = readPointerWord(thisWord)
    if (vtable == 0L) return null
    if (
        readPointerWord(vtable + IUnknownVftblSlots.QueryInterface * Long.SIZE_BYTES) !=
            Win64ManagedComIUnknownCallbacks.queryInterface.value ||
        readPointerWord(vtable + IUnknownVftblSlots.AddRef * Long.SIZE_BYTES) !=
            Win64ManagedComIUnknownCallbacks.addRef.value ||
        readPointerWord(vtable + IUnknownVftblSlots.Release * Long.SIZE_BYTES) !=
            Win64ManagedComIUnknownCallbacks.release.value
    ) {
        return null
    }
    return platformWinRTProjectionInboundBinding(thisWord)
}

private fun readPointerWord(address: Long): Long =
    address.toCPointer<LongVar>()?.get(0) ?: 0L
