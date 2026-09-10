package io.github.composefluent.winrt.runtime

/**
 * Owns one successful COM apartment initialization on the creating thread.
 *
 * This mirrors C++/WinRT `init_apartment`: a scope is created only after
 * `CoInitializeEx` succeeds and closing it performs exactly one matching
 * `CoUninitialize`.
 */
class RuntimeScope private constructor(
    private val ownerThread: Long,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        check(platformCurrentThreadToken() == ownerThread) {
            "RuntimeScope must be closed on its creating thread."
        }
        if (closed) {
            return
        }
        closed = true
        PlatformRuntimeInitialization.uninitializeCom()
    }

    companion object {
        fun initializeSingleThreaded(): RuntimeScope =
            initialize(ApartmentType.SingleThreaded)

        fun initializeMultithreaded(): RuntimeScope =
            initialize(ApartmentType.MultiThreaded)

        private fun initialize(apartmentType: ApartmentType): RuntimeScope {
            check(!platformCurrentThreadIsVirtual()) {
                "RuntimeScope cannot initialize a COM apartment on a JVM virtual thread."
            }
            val comResult = PlatformRuntimeInitialization.initializeCom(apartmentType)
            comResult.requireSuccess("CoInitializeEx")
            return RuntimeScope(platformCurrentThreadToken())
        }
    }
}
