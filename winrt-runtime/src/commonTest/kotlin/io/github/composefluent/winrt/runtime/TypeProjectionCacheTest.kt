package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeProjectionCacheTest {
    @Test
    fun cached_name_and_kind_follow_metadata_registration_and_reset() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT 3 ABI.System.Type.TypeNameCache caches ManagedTypeReference.
        // Kotlin additionally permits runtime metadata registration and test resets.
        ComWrappersSupport.clearRegistriesForTests()
        try {
            val custom = readType(CacheProbe::class)
            assertEquals(WinRTTypeKind.Custom.ordinal, custom.second)
            Projections.registerCustomAbiTypeMapping(CacheProbe::class, CacheProbe::class, "Test.CacheProbe")
            repeat(2) {
                assertEquals("Test.CacheProbe" to WinRTTypeKind.Metadata.ordinal, readType(CacheProbe::class))
            }
            WinRTTypeRegistry.register(WinRTTypeId(CacheProbe::class, "Test.RenamedProbe", isWindowsRuntimeType = true))
            assertEquals("Test.RenamedProbe" to WinRTTypeKind.Metadata.ordinal, readType(CacheProbe::class))
            ComWrappersSupport.clearRegistriesForTests()
            assertEquals(custom, readType(CacheProbe::class))
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun invalidation_does_not_change_an_active_borrowed_type_name() {
        if (!PlatformRuntime.isWindows) return
        ComWrappersSupport.clearRegistriesForTests()
        try {
            Projections.registerCustomAbiTypeMapping(CacheProbe::class, CacheProbe::class, "Test.OriginalProbe")
            TypeProjection.createInputMarshaler(CacheProbe::class).use { outer ->
                WinRTTypeRegistry.register(WinRTTypeId(CacheProbe::class, "Test.ReplacementProbe", isWindowsRuntimeType = true))
                assertEquals("Test.ReplacementProbe" to WinRTTypeKind.Metadata.ordinal, readType(CacheProbe::class))
                assertEquals("Test.OriginalProbe", PlatformAbi.readHString(PlatformAbi.readPointer(outer.abi)))
            }
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    private fun readType(type: KClass<*>): Pair<String, Int> =
        TypeProjection.createInputMarshaler(type).use { marshaler ->
            PlatformAbi.readHString(PlatformAbi.readPointer(marshaler.abi)) to
                PlatformAbi.readInt32(TypeProjection.LAYOUT.slice(marshaler.abi, "kind"))
        }

    private class CacheProbe
}
