@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeTypeProjectionLifetimeTest {
    @Test
    fun active_type_name_survives_cache_invalidation_and_collection() {
        // CsWinRT TypeReference keeps its Name pinned for the complete ABI call.
        // The Native fast-pass buffer must remain alive after its cache entry is replaced.
        ComWrappersSupport.clearRegistriesForTests()
        try {
            Projections.registerCustomAbiTypeMapping(Probe::class, Probe::class, "Test.OriginalProbe")
            TypeProjection.createInputMarshaler(Probe::class).use { outer ->
                WinRTTypeRegistry.register(WinRTTypeId(Probe::class, "Test.ReplacementProbe", isWindowsRuntimeType = true))
                TypeProjection.createInputMarshaler(Probe::class).use { inner ->
                    assertEquals("Test.ReplacementProbe", PlatformAbi.readHString(PlatformAbi.readPointer(inner.abi)))
                }
                GC.collect()
                assertEquals("Test.OriginalProbe", PlatformAbi.readHString(PlatformAbi.readPointer(outer.abi)))
            }
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    private class Probe
}
