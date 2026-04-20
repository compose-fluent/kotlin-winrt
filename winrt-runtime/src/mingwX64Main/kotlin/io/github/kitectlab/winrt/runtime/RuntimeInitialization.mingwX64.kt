package io.github.kitectlab.winrt.runtime

internal actual object PlatformRuntimeInitialization {
    actual fun initializeCom(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return HResult(WinRtPlatformApi.coInitializeExRaw(apartmentType))
    }

    actual fun uninitializeCom() {
        if (PlatformRuntime.isWindows) {
            WinRtPlatformApi.coUninitializeRaw()
        }
    }

    actual fun initializeWinRt(apartmentType: ApartmentType): HResult {
        if (!PlatformRuntime.isWindows) {
            return KnownHResults.S_OK
        }
        return HResult(WinRtPlatformApi.roInitializeRaw(apartmentType))
    }

    actual fun uninitializeWinRt() {
        if (PlatformRuntime.isWindows) {
            WinRtPlatformApi.roUninitializeRaw()
        }
    }
}
