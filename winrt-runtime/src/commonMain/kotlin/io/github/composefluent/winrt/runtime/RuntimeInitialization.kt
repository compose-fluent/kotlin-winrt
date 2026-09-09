package io.github.composefluent.winrt.runtime

// ---------------------------------------------------------------------------
// Low-level COM/WinRT initialization (internal).
// ---------------------------------------------------------------------------

internal object PlatformRuntimeInitialization {
    fun initializeCom(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.E_NOTSUPPORTED
        return HResult(WinRTPlatformApi.coInitializeExRaw(apartmentType))
    }

    fun uninitializeCom() {
        if (!PlatformRuntime.isWindows) return
        WinRTPlatformApi.coUninitializeRaw()
    }

    fun cleanupApplicationHostRuntime() {
        if (!PlatformRuntime.isWindows) return
        var failure: Throwable? = null
        fun attempt(cleanup: () -> Unit) {
            try {
                cleanup()
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        // Keep attempting every phase so one failing cache/release path cannot prevent the
        // remaining application-owned references from being drained.
        attempt { XamlSystemProjectionRuntimeHooks.closeRuntimeCaches() }
        attempt { WinRTComposableObjectReference.closeRuntimeReferences() }
        attempt { ComWrappersSupport.clearRuntimeCache() }
        attempt { drainDeferredComReleasesForCurrentContext() }
        attempt { PlatformFinalization.drain() }
        attempt { drainDeferredComReleasesForCurrentContext() }
        failure?.let { throw it }
    }

    fun initializeWinRT(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) return KnownHResults.E_NOTSUPPORTED
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
