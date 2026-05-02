package io.github.composefluent.winrt.runtime

class RuntimeScope private constructor(
    private val comInitialization: HResult,
    private val winRtInitialization: HResult,
) : AutoCloseable {
    val comInitialized: Boolean
        get() = comInitialization.isSuccess

    val winRtInitialized: Boolean
        get() = winRtInitialization.isSuccess

    override fun close() {
        if (winRtInitialized) {
            PlatformRuntimeInitialization.uninitializeWinRt()
        }
        if (comInitialized && winRtInitialization != KnownHResults.RPC_E_CHANGED_MODE) {
            PlatformRuntimeInitialization.uninitializeCom()
        }
    }

    companion object {
        fun initializeSingleThreaded(): RuntimeScope =
            initialize(ApartmentType.SingleThreaded)

        fun initializeMultithreaded(): RuntimeScope =
            initialize(ApartmentType.MultiThreaded)

        private fun initialize(apartmentType: ApartmentType): RuntimeScope {
            val comResult = PlatformRuntimeInitialization.initializeCom(apartmentType)
            val winRtResult = PlatformRuntimeInitialization.initializeWinRt(apartmentType)
            return RuntimeScope(comResult, winRtResult)
        }
    }
}
