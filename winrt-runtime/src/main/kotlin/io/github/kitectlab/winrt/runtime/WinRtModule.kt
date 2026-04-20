package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

/**
 * JVM-side equivalent of the `.cswinrt/src/WinRT.Runtime/Module.cs` WinRT module owner.
 *
 * The runtime keeps one process-wide MTA usage cookie alive so activation and interop helpers do not
 * each invent their own initialization lifetime.
 */
internal object WinRtModule {
    private val mtaCookie: MemorySegment by lazy {
        if (!PlatformRuntime.isWindows) {
            MemorySegment.NULL
        } else {
            val result = WindowsRuntimePlatform.coIncrementMtaUsage()
            result.hResult.requireSuccess("CoIncrementMTAUsage")
            if (result.pointer != MemorySegment.NULL) {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        runCatching {
                            WindowsRuntimePlatform.coDecrementMtaUsage(result.pointer)
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
