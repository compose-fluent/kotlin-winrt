package io.github.composefluent.winrt.runtime

class RuntimeScope private constructor(
    private val comInitialization: HResult,
    private val winRtInitialization: HResult,
) : AutoCloseable {
    private var closed = false

    val comInitialized: Boolean
        get() = comInitialization.isSuccess

    val winRtInitialized: Boolean
        get() = winRtInitialization.isSuccess

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        if (winRtInitialized) {
            RuntimeScopeThreadInitialization.recordWinRtUninitialize()
            PlatformRuntimeInitialization.uninitializeWinRt()
        }
        if (comInitialized && winRtInitialization != KnownHResults.RPC_E_CHANGED_MODE) {
            RuntimeScopeThreadInitialization.recordComUninitialize()
            PlatformRuntimeInitialization.uninitializeCom()
        }
        RuntimeScopeThreadInitialization.recordScopeClose()
    }

    companion object {
        fun initializeSingleThreaded(): RuntimeScope =
            initialize(ApartmentType.SingleThreaded)

        fun initializeMultithreaded(): RuntimeScope =
            initialize(ApartmentType.MultiThreaded)

        private fun initialize(apartmentType: ApartmentType): RuntimeScope {
            val comResult = PlatformRuntimeInitialization.initializeCom(apartmentType)
            val winRtResult = PlatformRuntimeInitialization.initializeWinRt(apartmentType)
            RuntimeScopeThreadInitialization.recordScopeInitialize(comResult, winRtResult)
            return RuntimeScope(comResult, winRtResult)
        }
    }
}

internal object RuntimeScopeThreadInitialization {
    private val activeScopes = PlatformThreadLocalInt()
    private val comInitializations = PlatformThreadLocalInt()
    private val winRtInitializations = PlatformThreadLocalInt()

    fun recordScopeInitialize(comResult: HResult, winRtResult: HResult) {
        activeScopes.set(activeScopes.get() + 1)
        if (winRtResult.isSuccess) {
            winRtInitializations.set(winRtInitializations.get() + 1)
        }
        if (comResult.isSuccess && winRtResult != KnownHResults.RPC_E_CHANGED_MODE) {
            comInitializations.set(comInitializations.get() + 1)
        }
    }

    fun recordScopeClose() {
        activeScopes.set((activeScopes.get() - 1).coerceAtLeast(0))
    }

    fun recordWinRtUninitialize() {
        winRtInitializations.set((winRtInitializations.get() - 1).coerceAtLeast(0))
    }

    fun recordComUninitialize() {
        comInitializations.set((comInitializations.get() - 1).coerceAtLeast(0))
    }
}
