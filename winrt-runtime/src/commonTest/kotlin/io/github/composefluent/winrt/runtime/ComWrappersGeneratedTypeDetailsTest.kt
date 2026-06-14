package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComWrappersGeneratedTypeDetailsTest {
    @Test
    fun create_ccw_uses_registered_generated_type_details() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRT_GeneratedDetailsComponent_TypeDetails.register()
        val managed = GeneratedDetailsComponent("payload")

        ComWrappersSupport.createCCWForObject(managed, GENERATED_DETAILS_INTERFACE_ID).use { ccw ->
            val pointer = PlatformAbi.fromRawComPtr(ccw.pointer)
            val found = ComWrappersSupport.findObject(pointer, GeneratedDetailsComponent::class)
            val info = ComWrappersSupport.getInspectableInfo(pointer)

            assertSame(managed, found)
            assertEquals("test.GeneratedDetailsComponent", info?.runtimeClassName)
            assertTrue(info?.interfaceIds?.contains(GENERATED_DETAILS_INTERFACE_ID) == true)
        }
    }
}

private val GENERATED_DETAILS_INTERFACE_ID = Guid("12121212-1212-1212-1212-121212121212")

private data class GeneratedDetailsComponent(val name: String)

private object WinRT_GeneratedDetailsComponent_TypeDetails {
    fun register() {
        ComWrappersSupport.registerAuthoringTypeDetailsFactory(
            GeneratedDetailsComponent::class,
            ::createCcwDefinition,
        )
    }

    fun createCcwDefinition(value: Any): WinRtCcwDefinition {
        require(value is GeneratedDetailsComponent)
        return WinRtCcwDefinition(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = GENERATED_DETAILS_INTERFACE_ID,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = GENERATED_DETAILS_INTERFACE_ID,
            runtimeClassName = "test.GeneratedDetailsComponent",
        )
    }
}
