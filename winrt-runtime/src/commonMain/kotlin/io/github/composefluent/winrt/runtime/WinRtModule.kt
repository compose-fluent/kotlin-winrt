package io.github.composefluent.winrt.runtime

/**
 * Shared equivalent of the `.cswinrt/src/WinRT.Runtime/Module.cs` WinRT module owner.
 *
 * The runtime keeps one process-wide MTA usage cookie alive so activation and interop helpers do not
 * each invent their own initialization lifetime.
 */
internal object WinRtModule {
    private data class State(
        val mtaCookie: RawAddress,
        val shutdownHook: AutoCloseable?,
    )

    private val state: State by lazy {
        if (!PlatformRuntime.isWindows) {
            State(PlatformAbi.nullPointer, null)
        } else {
            val result = WinRtPlatformApi.coIncrementMtaUsageRaw()
            HResult(result.hResultValue).requireSuccess("CoIncrementMTAUsage")
            val shutdownHook =
                if (PlatformAbi.isNull(result.pointer)) {
                    null
                } else {
                    PlatformProcessHooks.registerShutdownHook {
                        runCatching {
                            WinRtPlatformApi.coDecrementMtaUsageRaw(result.pointer)
                        }
                    }
                }
            State(result.pointer, shutdownHook)
        }
    }

    fun ensureInitialized() {
        @Suppress("UNUSED_VARIABLE")
        val ignored = state.mtaCookie
    }
}
