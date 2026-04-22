package io.github.kitectlab.winrt.runtime

// ---------------------------------------------------------------------------
// Low-level COM/WinRT initialization (internal).
// ---------------------------------------------------------------------------

internal object PlatformRuntimeInitialization {
    fun initializeCom(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.S_OK
        return HResult(WinRtPlatformApi.coInitializeExRaw(apartmentType))
    }

    fun uninitializeCom() {
        if (PlatformRuntime.isWindows) WinRtPlatformApi.coUninitializeRaw()
    }

    fun initializeWinRt(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.S_OK
        return HResult(WinRtPlatformApi.roInitializeRaw(apartmentType))
    }

    fun uninitializeWinRt() {
        if (!PlatformRuntime.isWindows) return
        ActivationFactory.clearRuntimeCache()
        WinRtPlatformApi.roUninitializeRaw()
    }
}

// ---------------------------------------------------------------------------
// Public API — COM and WinRT apartment management.
// ---------------------------------------------------------------------------

object ComRuntime {
    fun initializeSingleThreaded(): HResult =
        PlatformRuntimeInitialization.initializeCom(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        PlatformRuntimeInitialization.initializeCom(ApartmentType.MultiThreaded)

    fun uninitialize() = PlatformRuntimeInitialization.uninitializeCom()
}

object WinRtRuntime {
    fun initializeSingleThreaded(): HResult =
        PlatformRuntimeInitialization.initializeWinRt(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        PlatformRuntimeInitialization.initializeWinRt(ApartmentType.MultiThreaded)

    fun getActivationFactory(runtimeClassName: String, interfaceId: Guid = IID.IActivationFactory): Result<IUnknownReference> =
        runCatching {
            ActivationFactory.get(runtimeClassName, interfaceId)
        }

    fun activateInstance(runtimeClassName: String): Result<IInspectableReference> =
        runCatching {
            ActivationFactory.activateInstance(runtimeClassName)
        }

    fun uninitialize() = PlatformRuntimeInitialization.uninitializeWinRt()
}
