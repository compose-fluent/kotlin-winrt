package io.github.composefluent.winrt.runtime

// ---------------------------------------------------------------------------
// Low-level COM/WinRT initialization (internal).
// ---------------------------------------------------------------------------

internal object PlatformRuntimeInitialization {
    fun initializeCom(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.S_OK
        return HResult(WinRTPlatformApi.coInitializeExRaw(apartmentType))
    }

    fun uninitializeCom() {
        if (!PlatformRuntime.isWindows) return
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
        WinRTComposableObjectReference.closeRuntimeReferences()
        ComWrappersSupport.clearRuntimeCache()
        PlatformFinalization.drain()
        uninitializeComApartment()
    }

    fun uninitializeComApartment() {
        if (!PlatformRuntime.isWindows) return
        try {
            WinRTPlatformApi.coUninitializeRaw()
        } finally {
            PlatformFinalization.drain()
        }
    }

    fun initializeWinRT(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.S_OK
        return HResult(WinRTPlatformApi.roInitializeRaw(apartmentType))
    }

    fun uninitializeWinRT() {
        if (!PlatformRuntime.isWindows) return
        WinRTPlatformApi.roUninitializeRaw()
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

object WinRTRuntime {
    fun initializeSingleThreaded(): HResult =
        PlatformRuntimeInitialization.initializeWinRT(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        PlatformRuntimeInitialization.initializeWinRT(ApartmentType.MultiThreaded)

    fun getActivationFactory(runtimeClassName: String, interfaceId: Guid = IID.IActivationFactory): Result<IUnknownReference> =
        runCatching {
            ActivationFactory.get(runtimeClassName, interfaceId)
        }

    fun activateInstance(runtimeClassName: String): Result<IInspectableReference> =
        runCatching {
            ActivationFactory.activateInstance(runtimeClassName)
        }

    fun uninitialize() = PlatformRuntimeInitialization.uninitializeWinRT()
}
