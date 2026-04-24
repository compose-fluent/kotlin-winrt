package io.github.kitectlab.winrt.runtime

internal object ActivationFactoryReferenceSupport {
    fun <T> activateInstance(
        invokeActivate: (RawAddress) -> Int,
        wrapInspectable: (RawAddress) -> T,
        initializeReferenceTracker: (T) -> Unit,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val instanceOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invokeActivate(instanceOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return wrapInspectable(PlatformAbi.readPointer(instanceOut)).also(initializeReferenceTracker)
        }
}

internal object InspectableReferenceSupport {
    fun getRuntimeClassName(
        noThrow: Boolean,
        invokeGetRuntimeClassName: (RawAddress) -> Int,
    ): String? =
        PlatformAbi.confinedScope().use { scope ->
            val hStringOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invokeGetRuntimeClassName(hStringOut)
            if (HResult(hResult).isFailure) {
                if (noThrow) {
                    return null
                }
                WinRtPlatformApi.checkSucceededRaw(hResult)
            }

            val handle = PlatformAbi.readPointer(hStringOut)
            if (PlatformAbi.isNull(handle)) {
                return null
            }
            return HString.fromHandle(handle, owner = true).use(HString::toKString)
        }
}
