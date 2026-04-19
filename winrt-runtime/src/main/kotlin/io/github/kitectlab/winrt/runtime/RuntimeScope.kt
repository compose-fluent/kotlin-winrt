package io.github.kitectlab.winrt.runtime

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
            JvmWinRtRuntime.uninitialize()
        }
        if (comInitialized && winRtInitialization != KnownHResults.RPC_E_CHANGED_MODE) {
            JvmComRuntime.uninitialize()
        }
    }

    companion object {
        fun initializeSingleThreaded(): RuntimeScope =
            initialize(ApartmentType.SingleThreaded)

        fun initializeMultithreaded(): RuntimeScope =
            initialize(ApartmentType.MultiThreaded)

        private fun initialize(apartmentType: ApartmentType): RuntimeScope {
            val comResult = when (apartmentType) {
                ApartmentType.SingleThreaded -> JvmComRuntime.initializeSingleThreaded()
                ApartmentType.MultiThreaded -> JvmComRuntime.initializeMultithreaded()
            }
            val winRtResult = when (apartmentType) {
                ApartmentType.SingleThreaded -> JvmWinRtRuntime.initializeSingleThreaded()
                ApartmentType.MultiThreaded -> JvmWinRtRuntime.initializeMultithreaded()
            }
            return RuntimeScope(comResult, winRtResult)
        }
    }
}
