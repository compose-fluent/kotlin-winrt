package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MarshalersTest {
    @Test
    fun blittable_marshaler_round_trips_boolean_and_guid_values() {
        val booleanMarshaler = Marshaler.boolean()
        assertEquals(WinRtAbiCategory.BLITTABLE, booleanMarshaler.abiCategory)
        assertTrue(booleanMarshaler.fromAbi(booleanMarshaler.getAbi(booleanMarshaler.createMarshaler(true))) == true)

        Arena.ofConfined().use { arena ->
            val guidMemory = arena.allocate(NativeLayoutsJvmCompat.GUID)
            val guid = Guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
            Marshaler.guid().copyManaged(guid, guidMemory.asNativePointer())
            assertEquals(guid, Marshaler.guid().fromAbi(guidMemory.asNativePointer()))
        }
    }

    @Test
    fun string_marshaler_round_trips_owned_hstrings_and_arrays_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        val stringMarshaler = Marshaler.string()
        val abi = stringMarshaler.fromManaged("projection-runtime") as NativePointer
        try {
            assertEquals("projection-runtime", stringMarshaler.fromAbi(abi))
        } finally {
            stringMarshaler.disposeAbi(abi)
        }

        stringMarshaler.fromManagedArray(arrayOf("one", "two")).use { abiArray ->
            assertNotNull(abiArray)
            assertEquals(listOf("one", "two"), stringMarshaler.fromAbiArray(abiArray!!.length, abiArray.data))
        }
    }

    @Test
    fun interface_marshaler_reuses_unwrapped_projected_objects() {
        ComWrappersSupport.clearRegistriesForTests()
        val typeHandle = WinRtTypeHandle("test.IFoo", Guid("66666666-6666-6666-6666-666666666666"))
        val marshaler = Marshaler.interfaceType(typeHandle, TestProjectedWrapper::class) { it as TestProjectedWrapper }
        val host = WinRtInspectableComObject.inspectableBox("payload", "test.RuntimeClass")
        val projected = TestProjectedWrapper(
            primaryTypeHandle = typeHandle,
            inspectable = IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr(), IID.IInspectable),
        )

        val reference = marshaler.createMarshaler(projected) as ComObjectReference
        try {
            assertEquals(projected.nativeObject.pointer.asRawAddress(), marshaler.getAbi(reference))
        } finally {
            marshaler.disposeMarshaler(reference)
            projected.nativeObject.close()
        }
    }

    @Test
    fun interface_marshaler_from_abi_returns_original_managed_instance_for_ccw() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("77777777-7777-7777-7777-777777777777")
        val typeHandle = WinRtTypeHandle("test.IManaged", interfaceId)
        ComWrappersSupport.registerCcwFactory(TestManagedInterfaceImpl::class) {
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = interfaceId,
                runtimeClassName = "test.ManagedInterfaceImpl",
            )
        }

        val marshaler = Marshaler.interfaceType(typeHandle, TestManagedInterfaceImpl::class)
        val managed = TestManagedInterfaceImpl("payload")
        val abi = marshaler.fromManaged(managed) as NativePointer
        try {
            assertSame(managed, marshaler.fromAbi(abi))
        } finally {
            marshaler.disposeAbi(abi)
        }
    }

    @Test
    fun inspectable_marshaler_round_trips_managed_values_and_external_inspectables() {
        val marshaler = Marshaler.inspectableAny()

        val managedAbi = marshaler.fromManaged(42) as NativePointer
        try {
            assertEquals(42, marshaler.fromAbi(managedAbi))
        } finally {
            marshaler.disposeAbi(managedAbi)
        }

        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = Guid("88888888-8888-8888-8888-888888888888"),
                    methods = emptyList(),
                ),
            ),
            runtimeClassName = "test.RuntimeClass",
        )
        val inspectablePointer = host.createReference(Guid("88888888-8888-8888-8888-888888888888")).use { reference ->
            reference.asInspectable().use { inspectable ->
                inspectable.getRefPointer()
            }
        }

        try {
            val projected = marshaler.fromAbi(inspectablePointer)
            assertTrue(projected is IWinRTObject)
            val runtimeClassName = ((projected as IWinRTObject).nativeObject.asInspectable()).use { it.getRuntimeClassName() }
            assertEquals("test.RuntimeClass", runtimeClassName)
            (projected as? AutoCloseable)?.close()
        } finally {
            marshaler.disposeAbi(inspectablePointer)
            host.releaseManagedReference()
        }
    }

    @Test
    fun delegate_marshaler_round_trips_handle_to_reference_and_invokes() {
        var callCount = 0
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = Guid("99999999-9999-9999-9999-999999999999"),
            parameterKinds = emptyList(),
            returnKind = WinRtDelegateValueKind.UNIT,
        )
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = descriptor.interfaceId,
            parameterKinds = emptyList(),
        ) {
            callCount += 1
        }

        val abi = MarshalDelegate.fromManaged(handle)
        try {
            val projected = MarshalDelegate.fromAbi(abi, descriptor)
                ?: error("Delegate reference should not be null.")
            projected.invoke(emptyList())
            assertEquals(1, callCount)
        } finally {
            try {
                MarshalDelegate.disposeAbi(abi, descriptor)
            } finally {
                handle.close()
            }
        }
    }

    private class TestProjectedWrapper(
        override val primaryTypeHandle: WinRtTypeHandle,
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private data class TestManagedInterfaceImpl(val value: String)
}
