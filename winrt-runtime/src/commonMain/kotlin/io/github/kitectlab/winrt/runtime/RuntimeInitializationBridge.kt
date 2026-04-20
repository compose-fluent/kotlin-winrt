package io.github.kitectlab.winrt.runtime

internal expect object PlatformRuntimeInitialization {
    fun initializeCom(apartmentType: ApartmentType): HResult

    fun uninitializeCom()

    fun initializeWinRt(apartmentType: ApartmentType): HResult

    fun uninitializeWinRt()
}
