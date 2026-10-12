package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WinUiXamlValueConversionTest {
    private data class Parsed(val value: Int)
    private enum class Choice { Zero, One }

    @Test
    fun application_literal_factories_and_enum_parsers_precede_sdk_conversion() {
        // XamlTypeExtensions.GetStringToThing uses CreateFromString and enum
        // conversion directly. Reuse the IXamlType registrar's same factory.
        ComWrappersSupport.clearRegistriesForTests()
        registerWinUiXamlTypeDefinition(WinUiXamlTypeDefinition(Parsed::class, "probe.Parsed", "System.Object",
            isWinRTComponent = false, createFromString = { Parsed(it.toInt()) }))
        registerWinUiXamlEnumType(Choice::class, "probe.Choice", Choice.entries.toTypedArray())
        var sdkCalled = false
        fun convert(type: kotlin.reflect.KClass<*>, text: String) = convertWinUiXamlLiteral(type, text) { _, _ ->
            sdkCalled = true; error("Application parser was skipped")
        }
        assertEquals(Parsed(12), convert(Parsed::class, "12"))
        assertEquals(Choice.One, convert(Choice::class, "one"))
        assertFalse(sdkCalled)
        assertEquals(3, convertWinUiXamlLiteral(Int::class, "3") { _, text -> text.toInt() })
    }
}
