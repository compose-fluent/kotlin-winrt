package io.github.kitectlab.winrt.runtime

internal object ActivationFactoryReferenceSupport {
    fun <T> activateInstance(
        invokeActivate: (NativePointer) -> Int,
        wrapInspectable: (NativePointer) -> T,
        initializeReferenceTracker: (T) -> Unit,
    ): T =
        NativeInterop.confinedScope().use { scope ->
            val instanceOut = NativeInterop.allocatePointerSlot(scope)
            val hResult = invokeActivate(instanceOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return wrapInspectable(NativeInterop.readPointer(instanceOut)).also(initializeReferenceTracker)
        }
}

internal object InspectableReferenceSupport {
    fun getRuntimeClassName(
        noThrow: Boolean,
        invokeGetRuntimeClassName: (NativePointer) -> Int,
    ): String? =
        NativeInterop.confinedScope().use { scope ->
            val hStringOut = NativeInterop.allocatePointerSlot(scope)
            val hResult = invokeGetRuntimeClassName(hStringOut)
            if (HResult(hResult).isFailure) {
                if (noThrow) {
                    return null
                }
                WinRtPlatformApi.checkSucceededRaw(hResult)
            }

            val handle = NativeInterop.readPointer(hStringOut)
            if (NativeInterop.isNull(handle)) {
                return null
            }
            return HString.fromHandle(handle, owner = true).use(HString::toKString)
        }
}
