package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class WinRtTypeIdTest {
    @Test
    fun registry_registers_and_resolves_by_kclass_and_name() {
        WinRtTypeRegistry.clearForTests()

        val registered =
            WinRtTypeRegistry.register<SampleRuntimeClass>(
                projectedTypeName = "Contoso.SampleRuntimeClass",
                interfaceId = Guid("11111111-1111-1111-1111-111111111111"),
                boxedName = "Windows.Foundation.IReference`1<Contoso.SampleRuntimeClass>",
                runtimeClassName = "Contoso.SampleRuntimeClass",
            )

        assertEquals(registered, WinRtTypeRegistry.find<SampleRuntimeClass>())
        assertEquals(registered, WinRtTypeRegistry.findByProjectedName("Contoso.SampleRuntimeClass"))
    }

    private class SampleRuntimeClass
}
