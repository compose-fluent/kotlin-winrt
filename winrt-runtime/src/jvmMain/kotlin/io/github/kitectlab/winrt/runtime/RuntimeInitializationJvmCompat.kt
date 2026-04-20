package io.github.kitectlab.winrt.runtime

object JvmComRuntime {
    fun initializeSingleThreaded(): HResult =
        PlatformRuntimeInitialization.initializeCom(ApartmentType.SingleThreaded)

    fun initializeMultithreaded(): HResult =
        PlatformRuntimeInitialization.initializeCom(ApartmentType.MultiThreaded)

    fun uninitialize() = PlatformRuntimeInitialization.uninitializeCom()
}

object JvmWinRtRuntime {
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
