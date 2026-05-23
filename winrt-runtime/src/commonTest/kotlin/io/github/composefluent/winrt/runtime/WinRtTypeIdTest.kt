package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinRtTypeIdTest {
    @Test
    fun registry_registers_and_resolves_by_kclass_and_name() {
        WinRtTypeRegistry.clearForTests()

        val registered =
            WinRtTypeRegistry.register<SampleRuntimeClass>(
                projectedTypeName = "Contoso.SampleRuntimeClass",
                guid = Guid("11111111-1111-1111-1111-111111111111"),
                iid = Guid("11111111-1111-1111-1111-111111111111"),
                boxedName = "Windows.Foundation.IReference`1<Contoso.SampleRuntimeClass>",
                runtimeClassName = "Contoso.SampleRuntimeClass",
            )

        assertEquals(registered, WinRtTypeRegistry.find<SampleRuntimeClass>())
        assertEquals(registered, WinRtTypeRegistry.findByProjectedName("Contoso.SampleRuntimeClass"))
        assertNull(WinRtTypeRegistry.findByName("SampleRuntimeClass"))
    }

    @Test
    fun registry_does_not_resolve_projection_types_by_ambiguous_simple_name() {
        ComWrappersSupport.clearRegistriesForTests()

        WinRtTypeRegistry.register<SampleRuntimeClass>(
            projectedTypeName = "Contoso.SampleRuntimeClass",
            runtimeClassName = "Contoso.SampleRuntimeClass",
            aliases = setOf("Contoso.SampleAlias"),
        )

        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findKClassByNameCached("Contoso.SampleRuntimeClass"))
        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findKClassByNameCached("Contoso.SampleAlias"))
        assertNull(TypeNameSupport.findKClassByNameCached("SampleRuntimeClass"))
    }

    private class SampleRuntimeClass
}
