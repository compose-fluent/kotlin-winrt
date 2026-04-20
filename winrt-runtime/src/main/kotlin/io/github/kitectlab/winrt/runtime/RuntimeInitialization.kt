package io.github.kitectlab.winrt.runtime

object JvmComRuntime {
    fun initializeSingleThreaded(): HResult =
        initialize(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        initialize(ApartmentType.MultiThreaded)

    fun uninitialize() {
        if (PlatformRuntime.isWindows) {
            WindowsRuntimePlatform.coUninitialize()
        }
    }

    private fun initialize(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return WindowsRuntimePlatform.coInitializeEx(apartmentType)
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
            WindowsRuntimePlatform.roUninitialize()
        }
    }

    private fun initialize(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return WindowsRuntimePlatform.roInitialize(apartmentType)
    }
}
