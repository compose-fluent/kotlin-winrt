package io.github.composefluent.winrt.runtime

import java.util.concurrent.ConcurrentHashMap

@PublishedApi
internal actual class PlatformManagedComInboundWeakReference actual constructor(value: Any) : AutoCloseable {
    @PublishedApi
    internal val reference = java.lang.ref.WeakReference(value)

    @PublishedApi
    internal actual inline fun get(): Any? = reference.get()

    actual override fun close() = Unit
}

internal actual class ManagedComInboundBindingHandle actual constructor(
    private val binding: ManagedComInboundBinding,
    private val canonicalObjectMemory: RawAddress,
) : AutoCloseable {
    init {
        managedComInboundBindings[canonicalObjectMemory.value] = binding
    }

    actual fun attach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView?,
        objectMemoryOffsetBytes: Long,
    ) {
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComInboundBindingSlot * Long.SIZE_BYTES.toLong(),
                canonicalObjectMemory,
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComInboundBindingSlot,
                canonicalObjectMemory,
            )
        }
    }

    actual fun detach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView?,
        objectMemoryOffsetBytes: Long,
    ) {
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComInboundBindingSlot * Long.SIZE_BYTES.toLong(),
                PlatformAbi.nullPointer,
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComInboundBindingSlot,
                PlatformAbi.nullPointer,
            )
        }
    }

    actual override fun close() {
        managedComInboundBindings.remove(canonicalObjectMemory.value, binding)
    }
}

internal actual fun platformCreateInspectableQueryInterfaceCallback(): NativeCallbackHandle =
    ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.QueryInterface) {
            thisWord, interfaceIdWord, resultWord, _, _, _, _ ->
        WinRTInspectableComObject.invokeQueryInterfaceCallback(
            thisPointer = RawAddress(thisWord),
            interfaceIdPointer = RawAddress(interfaceIdWord),
            resultPointer = RawAddress(resultWord),
        )
    }.withManagedComFastPath(
        description = "the JVM managed COM QueryInterface stub",
        buildCode = ::buildManagedComQueryInterfaceStub,
    )

internal actual fun platformCreateInspectableAddRefCallback(): NativeCallbackHandle =
    ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.AddRef) { thisWord, _, _, _, _, _, _ ->
        WinRTInspectableComObject.invokeAddRefCallback(RawAddress(thisWord))
    }.withManagedComFastPath(
        description = "the JVM managed COM AddRef stub",
        buildCode = { fallbackAddress ->
            buildManagedComReferenceCountStub(
                minimumCurrentCount = managedComAddRefFastPathMinimumCount,
                delta = 1,
                fallbackAddress = fallbackAddress,
            )
        },
    )

internal actual fun platformCreateInspectableReleaseCallback(): NativeCallbackHandle =
    ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.Release) { thisWord, _, _, _, _, _, _ ->
        WinRTInspectableComObject.invokeReleaseCallback(RawAddress(thisWord))
    }.withManagedComFastPath(
        description = "the JVM managed COM Release stub",
        buildCode = { fallbackAddress ->
            buildManagedComReferenceCountStub(
                minimumCurrentCount = managedComReleaseFastPathMinimumCount,
                delta = -1,
                fallbackAddress = fallbackAddress,
            )
        },
    )

@PublishedApi
internal actual inline fun platformWinRTProjectionInboundManagedValue(
    thisWord: Long,
): Any? =
    platformWinRTProjectionInboundBinding(thisWord)?.get()

@PublishedApi
internal actual inline fun platformWinRTProjectionInboundBinding(
    thisWord: Long,
): ManagedComInboundBinding? {
    if (thisWord == 0L) return null
    managedComInboundBindings[thisWord]?.let { return it }
    val canonicalObjectWord = PlatformAbi.readPointerAt(
        RawAddress(thisWord),
        managedComInboundBindingSlot,
    ).value
    return if (canonicalObjectWord == 0L) null else managedComInboundBindings[canonicalObjectWord]
}

internal actual fun platformTryWinRTProjectionInboundBinding(thisWord: Long): ManagedComInboundBinding? {
    if (thisWord == 0L) return null
    managedComInboundBindings[thisWord]?.let { return it }
    val objectMemory = RawAddress(thisWord)
    val vtable = PlatformAbi.readPointer(objectMemory)
    if (!WinRTInspectableComObject.isManagedVtablePointer(vtable)) {
        return null
    }
    val canonicalObjectWord = PlatformAbi.readPointerAt(
        objectMemory,
        managedComInboundBindingSlot,
    ).value
    return if (canonicalObjectWord == 0L) null else managedComInboundBindings[canonicalObjectWord]
}

@PublishedApi
internal val managedComInboundBindings = ConcurrentHashMap<Long, ManagedComInboundBinding>()

private fun NativeCallbackHandle.withManagedComFastPath(
    description: String,
    buildCode: (Long) -> ByteArray,
): NativeCallbackHandle =
    try {
        NativeCallbackHandle(
            pointer = allocateWin64ExecutableCode(
                code = buildCode(pointer.value),
                description = description,
            ),
            onClose = ::close,
        )
    } catch (failure: Throwable) {
        close()
        throw failure
    }
