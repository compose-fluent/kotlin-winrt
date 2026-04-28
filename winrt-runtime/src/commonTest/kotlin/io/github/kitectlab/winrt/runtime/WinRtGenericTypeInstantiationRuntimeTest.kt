package io.github.kitectlab.winrt.runtime

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WinRtGenericTypeInstantiationRuntimeTest {
    @AfterTest
    fun tearDown() {
        WinRtGenericTypeInstantiationRuntime.clearForTests()
    }

    @Test
    fun runtime_records_generic_instantiation_binding_families_by_class_and_kind() {
        WinRtGenericTypeInstantiationRuntime.bindRcwHelpers(
            className = "Windows_Foundation_IReference_Int",
            sourceType = "Windows.Foundation.IReference<Int>",
            isDelegate = false,
            functions = listOf("get_Value", "get_Value"),
        )
        WinRtGenericTypeInstantiationRuntime.bindVtableFunctions(
            className = "Windows_Foundation_IReference_Int",
            sourceType = "Windows.Foundation.IReference<Int>",
            isDelegate = false,
            functions = listOf("get_Value"),
        )
        WinRtGenericTypeInstantiationRuntime.bindPropertyAccessors(
            className = "Windows_Foundation_IReference_Int",
            sourceType = "Windows.Foundation.IReference<Int>",
            isDelegate = false,
            functions = listOf("get_Value"),
        )
        WinRtGenericTypeInstantiationRuntime.bindGenericReturnOnlyRcwHelpers(
            className = "Windows_Foundation_IReference_Int",
            sourceType = "Windows.Foundation.IReference<Int>",
            isDelegate = false,
            functions = listOf("get_Value", "get_Value"),
        )
        WinRtGenericTypeInstantiationRuntime.bindProjectedGenericFallbacks(
            className = "Windows_Foundation_IReference_Int",
            sourceType = "Windows.Foundation.IReference<Int>",
            isDelegate = false,
            functions = listOf("get_Value"),
        )

        val rcw = assertNotNull(
            WinRtGenericTypeInstantiationRuntime.bindingFor(
                "Windows_Foundation_IReference_Int",
                WinRtGenericTypeInstantiationBindingKind.RcwHelpers,
            ),
        )

        assertEquals("Windows.Foundation.IReference<Int>", rcw.entry.sourceType)
        assertEquals(listOf("get_Value"), rcw.functions)
        assertEquals(
            listOf(
                WinRtGenericTypeInstantiationBindingKind.RcwHelpers,
                WinRtGenericTypeInstantiationBindingKind.VtableFunctions,
                WinRtGenericTypeInstantiationBindingKind.PropertyAccessors,
                WinRtGenericTypeInstantiationBindingKind.GenericReturnOnlyRcwHelpers,
                WinRtGenericTypeInstantiationBindingKind.ProjectedGenericFallbacks,
            ),
            WinRtGenericTypeInstantiationRuntime.bindingsForClass("Windows_Foundation_IReference_Int").map { it.kind },
        )
        assertEquals(
            listOf("get_Value"),
            WinRtGenericTypeInstantiationRuntime.bindingFor(
                "Windows_Foundation_IReference_Int",
                WinRtGenericTypeInstantiationBindingKind.GenericReturnOnlyRcwHelpers,
            )?.functions,
        )
        assertEquals(
            listOf("get_Value"),
            WinRtGenericTypeInstantiationRuntime.bindingFor(
                "Windows_Foundation_IReference_Int",
                WinRtGenericTypeInstantiationBindingKind.ProjectedGenericFallbacks,
            )?.functions,
        )
    }

    @Test
    fun runtime_records_delegate_ccw_invoke_binding_without_function_list() {
        WinRtGenericTypeInstantiationRuntime.bindDelegateCcwInvoke(
            className = "Windows_Foundation_EventHandler_Int",
            sourceType = "Windows.Foundation.EventHandler<Int>",
            isDelegate = true,
        )

        val binding = assertNotNull(
            WinRtGenericTypeInstantiationRuntime.bindingFor(
                "Windows_Foundation_EventHandler_Int",
                WinRtGenericTypeInstantiationBindingKind.DelegateCcwInvoke,
            ),
        )

        assertEquals(true, binding.entry.isDelegate)
        assertEquals(emptyList(), binding.functions)
    }
}
