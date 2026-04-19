package io.github.kitectlab.winrt.runtime

enum class ApartmentType {
    SingleThreaded,
    MultiThreaded,
}

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
            val activationResult = ActivationFactory.tryGet(runtimeClassName, interfaceId)
            if (!activationResult.isSuccess) {
                throw WinRtRuntimeException(
                    "Activation factory lookup failed for $runtimeClassName with ${activationResult.hResult}",
                    activationResult.hResult,
                )
            }
            if (interfaceId == IID.IActivationFactory) {
                ActivationFactoryReference(activationResult.pointer, interfaceId)
            } else {
                IUnknownReference(activationResult.pointer, interfaceId)
            }
        }

    fun uninitialize() {
        if (PlatformRuntime.isWindows) {
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
