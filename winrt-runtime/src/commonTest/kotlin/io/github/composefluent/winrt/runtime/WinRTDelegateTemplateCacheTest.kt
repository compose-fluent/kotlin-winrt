package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinRTDelegateTemplateCacheTest {
    @Test
    fun one_descriptor_can_alternate_between_distinct_static_entries() {
        // CsWinRT 3 EventHandlerInterfaceEntries is type-level, while each ABI entry
        // still defines its own dispatch. A descriptor memo must include that entry.
        val descriptor = WinRTDelegateDescriptor(
            Guid("ce718f5c-5b2f-4b45-b16d-7390535827e1"),
            listOf(WinRTDelegateValueKind.INT32),
        )
        var observed = 0
        ComAbiInteropBridge.createRawWordComMethodCallback(descriptor.functionSignature) { _, value, _, _, _, _, _ ->
            observed = value.toInt()
            KnownHResults.S_OK.value
        }.use { first ->
            ComAbiInteropBridge.createRawWordComMethodCallback(descriptor.functionSignature) { _, value, _, _, _, _, _ ->
                observed = value.toInt() * 2
                KnownHResults.S_OK.value
            }.use { second ->
                try {
                    for ((entry, expected) in listOf(first.pointer to 9, second.pointer to 18, first.pointer to 9)) {
                        WinRTDelegateBridge.createUnitDelegateStatic(descriptor, Any(), entry).use { handle ->
                            handle.createReference().use { reference -> reference.invokeAbi(listOf(9)).requireSuccess() }
                        }
                        assertEquals(expected, observed)
                    }
                } finally {
                    WinRTDelegateCcwTemplates.clearForTests()
                }
            }
        }
    }

    @Test
    fun resetting_templates_rebuilds_the_same_descriptor_for_new_configuration() {
        val descriptor = WinRTDelegateDescriptor(
            Guid("ce718f5c-5b2f-4b45-b16d-7390535827e2"),
            emptyList(),
        )
        fun hasCustomPropertyProvider(): Boolean =
            WinRTDelegateBridge.createDelegate(descriptor) { Unit }.use { handle ->
                handle.createReference().use { reference ->
                    reference.tryQueryInterface(IID.ICustomPropertyProvider)?.use { true } ?: false
                }
            }
        try {
            FeatureSwitches.overrideForTests(FeatureSwitches.EnableDefaultCustomTypeMappingsPropertyName, true)
            FeatureSwitches.overrideForTests(FeatureSwitches.EnableICustomPropertyProviderSupportPropertyName, true)
            WinRTDelegateCcwTemplates.clearForTests()
            assertTrue(hasCustomPropertyProvider())
            FeatureSwitches.overrideForTests(FeatureSwitches.EnableICustomPropertyProviderSupportPropertyName, false)
            WinRTDelegateCcwTemplates.clearForTests()
            assertFalse(hasCustomPropertyProvider())
        } finally {
            FeatureSwitches.overrideForTests(FeatureSwitches.EnableDefaultCustomTypeMappingsPropertyName, null)
            FeatureSwitches.overrideForTests(FeatureSwitches.EnableICustomPropertyProviderSupportPropertyName, null)
            WinRTDelegateCcwTemplates.clearForTests()
        }
    }
}
