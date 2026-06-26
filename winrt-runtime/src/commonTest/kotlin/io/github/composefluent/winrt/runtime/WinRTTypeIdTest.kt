package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinRTTypeIdTest {
    @Test
    fun registry_registers_and_resolves_by_kclass_and_name() {
        WinRTTypeRegistry.clearForTests()
        TypeNameSupport.clearRegistriesForTests()

        val registered =
            WinRTTypeRegistry.register<SampleRuntimeClass>(
                projectedTypeName = "Contoso.SampleRuntimeClass",
                guid = Guid("11111111-1111-1111-1111-111111111111"),
                iid = Guid("11111111-1111-1111-1111-111111111111"),
                boxedName = "Windows.Foundation.IReference`1<Contoso.SampleRuntimeClass>",
                runtimeClassName = "Contoso.SampleRuntimeClass",
            )

        assertEquals(registered, WinRTTypeRegistry.find<SampleRuntimeClass>())
        assertEquals(registered, WinRTTypeRegistry.findByProjectedName("Contoso.SampleRuntimeClass"))
        assertNull(WinRTTypeRegistry.findByName("SampleRuntimeClass"))
        assertNull(WinRTTypeRegistry.findByName(SampleRuntimeClass::class.qualifiedName ?: ""))
    }

    @Test
    fun registry_does_not_resolve_projection_types_by_ambiguous_simple_name() {
        ComWrappersSupport.clearRegistriesForTests()

        WinRTTypeRegistry.register<SampleRuntimeClass>(
            projectedTypeName = "Contoso.SampleRuntimeClass",
            runtimeClassName = "Contoso.SampleRuntimeClass",
            aliases = setOf("Contoso.SampleAlias"),
        )

        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findKClassByNameCached("Contoso.SampleRuntimeClass"))
        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findKClassByNameCached("Contoso.SampleAlias"))
        assertNull(TypeNameSupport.findKClassByNameCached("SampleRuntimeClass"))
        assertNull(TypeNameSupport.findKClassByNameCached(SampleRuntimeClass::class.qualifiedName ?: ""))
    }

    @Test
    fun projection_registration_does_not_add_kotlin_qualified_name_alias() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRTTypeRegistry.clearForTests()

        WinRTTypeRegistry.register<SampleRuntimeClass>(
            projectedTypeName = "Contoso.SampleRuntimeClass",
            runtimeClassName = "Contoso.SampleRuntimeClass",
        )
        TypeNameSupport.registerProjectionType(
            type = SampleRuntimeClass::class,
            runtimeClassName = "Contoso.SampleRuntimeClass",
        )

        assertEquals(SampleRuntimeClass::class, TypeNameSupport.findKClassByNameCached("Contoso.SampleRuntimeClass"))
        assertNull(TypeNameSupport.findKClassByNameCached(SampleRuntimeClass::class.qualifiedName ?: ""))
    }

    private class SampleRuntimeClass
}
