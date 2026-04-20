package io.github.kitectlab.winrt.runtime

/**
 * JVM-side equivalent of the `.cswinrt/src/WinRT.Runtime/Module.cs` WinRT module owner.
 *
 * The runtime keeps one process-wide MTA usage cookie alive so activation and interop helpers do not
 * each invent their own initialization lifetime.
 */
internal object WinRtModule {
    private val mtaCookie: NativePointer by lazy {
        if (!PlatformRuntime.isWindows) {
            NativeInterop.nullPointer
        } else {
            val result = WinRtPlatformApi.coIncrementMtaUsageRaw()
            HResult(result.hResultValue).requireSuccess("CoIncrementMTAUsage")
            if (!NativeInterop.isNull(result.pointer)) {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        runCatching {
                            WinRtPlatformApi.coDecrementMtaUsageRaw(result.pointer)
                        }
                    },
                )
            }
            result.pointer
        }
    }

    fun ensureInitialized() {
        @Suppress("UNUSED_VARIABLE")
        val ignored = mtaCookie
    }
}
