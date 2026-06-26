package io.github.composefluent.winrt.runtime

class RuntimeScope private constructor(
    private val comInitialization: HResult,
    private val winRTInitialization: HResult,
) : AutoCloseable {
    private var closed = false

    val comInitialized: Boolean
        get() = comInitialization.isSuccess

    val winRTInitialized: Boolean
        get() = winRTInitialization.isSuccess

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        if (winRTInitialized) {
            RuntimeScopeThreadInitialization.recordWinRTUninitialize()
            PlatformRuntimeInitialization.uninitializeWinRT()
        }
        if (comInitialized && winRTInitialization != KnownHResults.RPC_E_CHANGED_MODE) {
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
            val winRTResult = PlatformRuntimeInitialization.initializeWinRT(apartmentType)
            RuntimeScopeThreadInitialization.recordScopeInitialize(comResult, winRTResult)
            return RuntimeScope(comResult, winRTResult)
        }
    }
}

internal object RuntimeScopeThreadInitialization {
    private val activeScopes = PlatformThreadLocalInt()
    private val comInitializations = PlatformThreadLocalInt()
    private val winRTInitializations = PlatformThreadLocalInt()

    fun recordScopeInitialize(comResult: HResult, winRTResult: HResult) {
        activeScopes.set(activeScopes.get() + 1)
        if (winRTResult.isSuccess) {
            winRTInitializations.set(winRTInitializations.get() + 1)
        }
        if (comResult.isSuccess && winRTResult != KnownHResults.RPC_E_CHANGED_MODE) {
            comInitializations.set(comInitializations.get() + 1)
        }
    }

    fun recordScopeClose() {
        activeScopes.set((activeScopes.get() - 1).coerceAtLeast(0))
    }

    fun recordWinRTUninitialize() {
        winRTInitializations.set((winRTInitializations.get() - 1).coerceAtLeast(0))
    }

    fun recordComUninitialize() {
        comInitializations.set((comInitializations.get() - 1).coerceAtLeast(0))
    }
}
