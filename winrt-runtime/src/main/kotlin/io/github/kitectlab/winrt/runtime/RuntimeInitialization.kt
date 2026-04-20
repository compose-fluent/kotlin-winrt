package io.github.kitectlab.winrt.runtime

object JvmComRuntime {
    fun initializeSingleThreaded(): HResult =
        initialize(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        initialize(ApartmentType.MultiThreaded)

    fun uninitialize() {
        if (PlatformRuntime.isWindows) {
            WinRtPlatformApi.coUninitializeRaw()
        }
    }

    private fun initialize(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return HResult(WinRtPlatformApi.coInitializeExRaw(apartmentType))
    }
}

object JvmWinRtRuntime {
    fun initializeSingleThreaded(): HResult =
        initialize(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        initialize(ApartmentType.MultiThreaded)

    fun getActivationFactory(runtimeClassName: String, interfaceId: Guid = IID.IActivationFactory): Result<IUnknownReference> =
        runCatching {
            ActivationFactory.get(runtimeClassName, interfaceId)
        }

    fun activateInstance(runtimeClassName: String): Result<IInspectableReference> =
        runCatching {
            ActivationFactory.activateInstance(runtimeClassName)
        }

    fun uninitialize() {
        if (PlatformRuntime.isWindows) {
            ActivationFactory.clearRuntimeCache()
            WinRtPlatformApi.roUninitializeRaw()
        }
    }

    private fun initialize(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return HResult(WinRtPlatformApi.roInitializeRaw(apartmentType))
    }
}
