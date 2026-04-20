package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComWrappersSupportTest {
    @Test
    fun create_rcw_uses_runtime_class_factory_and_caches_wrapper_identity() {
        ComWrappersSupport.clearRegistriesForTests()
        val created = mutableListOf<TestRuntimeClassWrapper>()
        ComWrappersSupport.registerRuntimeClassFactory("test.RuntimeClass") { inspectable ->
            TestRuntimeClassWrapper(inspectable).also(created::add)
        }

        val host = WinRtInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = "test.RuntimeClass",
        )
        val pointer = host.detachReference(IID.IInspectable).asNativePointer()

        val first = ComWrappersSupport.createRcwForComObject(pointer) as TestRuntimeClassWrapper
        val second = ComWrappersSupport.createRcwForComObject(pointer) as TestRuntimeClassWrapper

        assertSame(first, second)
        assertEquals(1, created.size)
        assertEquals("test.RuntimeClass", first.nativeObject.asInspectable().use { it.getRuntimeClassName() })
        first.nativeObject.close()
    }

    @Test
    fun create_rcw_uses_static_type_and_helper_type_registration() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceType = WinRtTypeHandle("test.IFoo", Guid("11111111-1111-1111-1111-111111111111"))
        val helperType = WinRtTypeHandle("test.IFoo.Helper", Guid("22222222-2222-2222-2222-222222222222"))
        ComWrappersSupport.registerHelperType(interfaceType, helperType)
        ComWrappersSupport.registerTypedRcwFactory(helperType) { inspectable ->
            TestTypedWrapper(helperType, inspectable)
        }

        val host = WinRtInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = "test.RuntimeClass",
        )
        val pointer = host.detachReference(IID.IInspectable).asNativePointer()
        val wrapper = ComWrappersSupport.createRcwForComObject(pointer, interfaceType) as TestTypedWrapper

        assertEquals(helperType, wrapper.primaryTypeHandle)
        assertEquals("test.RuntimeClass", wrapper.nativeObject.asInspectable().use { it.getRuntimeClassName() })
        wrapper.nativeObject.close()
    }

    @Test
    fun create_rcw_falls_back_to_single_interface_optimized_object_for_inspectable_ptr_without_registered_factory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("34343434-3434-3434-3434-343434343434")
        val typeHandle = WinRtTypeHandle("test.IFallback", interfaceId)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = emptyList(),
                ),
            ),
            runtimeClassName = "test.Fallback",
        )

        val pointer = host.detachReference(interfaceId).asNativePointer()
        val wrapper = ComWrappersSupport.createRcwForComObject(pointer, typeHandle) as SingleInterfaceOptimizedObject

        assertEquals(typeHandle, wrapper.primaryTypeHandle)
        assertEquals(interfaceId, wrapper.nativeObject.interfaceId)
        assertFalse(wrapper.hasUnwrappableNativeObject)
        assertSame(wrapper.nativeObject, wrapper.getObjectReferenceForType(typeHandle))
        wrapper.nativeObject.close()
    }

    @Test
    fun create_ccw_and_find_object_use_registered_factory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("44444444-4444-4444-4444-444444444444")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) { value ->
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = interfaceId,
                runtimeClassName = "test.ManagedType",
            )
        }

        val managed = TestManagedType("payload")
        ComWrappersSupport.createCCWForObject(managed, interfaceId).use { ccw ->
            val found = ComWrappersSupport.findObject(ccw.pointer, TestManagedType::class)
            val info = ComWrappersSupport.getInspectableInfo(ccw.pointer)

            assertSame(managed, found)
            assertNotNull(info)
            assertEquals("test.ManagedType", info?.runtimeClassName)
            assertTrue(info?.interfaceIds?.contains(interfaceId) == true)
        }
    }

    @Test
    fun cast_extension_rehydrates_registered_typed_wrapper() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceType = WinRtTypeHandle("test.IFoo", Guid("55555555-5555-5555-5555-555555555555"))
        ComWrappersSupport.registerTypedRcwFactory(interfaceType) { inspectable ->
            TestTypedWrapper(interfaceType, inspectable)
        }

        val projected = ProjectedInspectableObject(
            pointer = WinRtInspectableComObject.inspectableBox("payload", "test.RuntimeClass")
                .detachReference(IID.IInspectable)
                .asNativePointer(),
        )

        val cast = projected.winrtAs(interfaceType) as TestTypedWrapper
        assertEquals(interfaceType, cast.primaryTypeHandle)
        cast.nativeObject.close()
        projected.nativeObject.close()
    }

    private data class TestManagedType(val name: String)

    private class ProjectedInspectableObject(
        pointer: NativePointer,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer, IID.IInspectable)
    }

    private class TestRuntimeClassWrapper(
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private class TestTypedWrapper(
        override val primaryTypeHandle: WinRtTypeHandle,
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }
}
