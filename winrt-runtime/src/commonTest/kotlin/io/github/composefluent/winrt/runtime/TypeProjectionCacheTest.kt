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

    @Test
    fun nested_type_names_and_string_scopes_preserve_their_abi_storage() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT TypeReference's HSTRING and TypeName share a call-scoped lifetime.
        // Exercise both embedded and overflow frames, including reuse after null input.
        ComWrappersSupport.clearRegistriesForTests()
        try {
            Projections.registerCustomAbiTypeMapping(CacheProbe::class, CacheProbe::class, "Test.ScratchProbe")
            fun verify(depth: Int) {
                if (depth == 0) return
                val isNull = depth % 2 == 0
                val name = if (isNull) "" else "Test.ScratchProbe"
                val kind = if (isNull) WinRTTypeKind.Primitive else WinRTTypeKind.Metadata
                TypeProjection.createInputMarshaler(if (isNull) null else CacheProbe::class).use { type ->
                    withNativeHStringReferenceAbi("string-$depth") { string, output ->
                        PlatformAbi.writePointer(output, string)
                        verify(depth - 1)
                        assertEquals("string-$depth", PlatformAbi.readHString(string))
                        assertEquals(string, PlatformAbi.readPointer(output))
                        assertEquals(name, PlatformAbi.readHString(PlatformAbi.readPointer(type.abi)))
                        assertEquals(kind.ordinal, PlatformAbi.readInt32(TypeProjection.LAYOUT.slice(type.abi, "kind")))
                    }
                }
            }
            repeat(2) { verify(8) }
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
