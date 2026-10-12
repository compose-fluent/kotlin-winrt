package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Independent ABI fixtures for CsWinRT IListMethods<T,TAbi> and IDictionaryMethods<K,KAbi,V,VAbi>. */
class WinRTCollectionValueAbiTest {
    private data class TextRecord(val text: String)

    @Test
    fun registered_struct_elements_share_layout_and_owned_field_cleanup_with_arrays() {
        var disposed = 0
        val struct = object : NativeStructAdapter<TextRecord> {
            override val layout = NativeStructLayout.sequential(NativeScalarFieldSpec("Text", NativeStructScalarKind.ADDRESS))
            override fun read(source: RawAddress) = TextRecord(NativeStringMarshaller.fromAbi(PlatformAbi.readPointer(source)))
            override fun write(value: TextRecord, destination: RawAddress) {
                PlatformAbi.writePointer(destination, NativeStringMarshaller.getAbi(NativeStringMarshaller.fromManaged(value.text)))
            }
            override fun disposeAbi(source: RawAddress) {
                disposed++
                NativeStringMarshaller.disposeAbi(PlatformAbi.readPointer(source))
            }
        }
        val signature = WinRTTypeSignature.struct("Test.CollectionTextRecord", WinRTTypeSignature.string())
        WinRTValueBoxingRegistration.registerStruct(TextRecord::class, "Test.CollectionTextRecord", signature.render(), struct)
        val adapter = WinRTReferenceValueAdapters.valueType(TextRecord::class, "Test.CollectionTextRecord", signature)
        assertNativeVector(adapter, TextRecord("first"), TextRecord("second"), ComAbiValueKind.Struct(NativeAbiLayout.ADDRESS),
            { address, value -> struct.write(value, address) }, { struct.read(it as RawAddress) })
        assertEquals(2, disposed)
        val marshaler = Marshaler.referenceValueAdapter(adapter)
        marshaler.createMarshalerArray(arrayOf(TextRecord("a"), TextRecord("b")))!!.use { array ->
            assertEquals(listOf(TextRecord("a"), TextRecord("b")), marshaler.fromAbiArray(array.length, array.data))
        }
        assertEquals(4, disposed)
    }

    @Test
    fun native_vector_values_use_actual_scalar_and_struct_carriers() {
        assertNativeVector(
            WinRTReferenceValueAdapters.valueType(Double::class, "Double", WinRTTypeSignature.float64()),
            0.0, 2.5, ComAbiValueKind.Double, PlatformAbi::writeDouble, { it as Double },
        )
        assertNativeVector(
            WinRTReferenceValueAdapters.valueType(Int::class, "Int32", WinRTTypeSignature.int32()),
            -7, 42, ComAbiValueKind.Int32, PlatformAbi::writeInt32, { it as Int },
        )
        val first = Guid("01234567-89AB-CDEF-0123-456789ABCDEF")
        val second = Guid("FEDCBA98-7654-3210-FEDC-BA9876543210")
        assertNativeVector(
            WinRTReferenceValueAdapters.valueType(Guid::class, "Guid", WinRTTypeSignature.guidValue()),
            first, second, ComAbiValueKind.Struct(NativeAbiLayout.GUID),
            { address, value -> PlatformAbi.writeGuid(address, value) }, { PlatformAbi.readGuid(it as RawAddress) },
        )
    }

    private fun <T> assertNativeVector(
        adapter: WinRTReferenceValueAdapter<T>, first: T, second: T, nativeKind: ComAbiValueKind,
        write: (RawAddress, T) -> Unit, readArgument: (Any?) -> T,
    ) {
        // Vtable fixture writes actual native bytes, never invoking the Kotlin CCW marshaler.
        PlatformAbi.confinedScope().use { scope ->
            var appended: T? = null
            ComAbiInteropBridge.createRawInt32Callback(listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Int32, ComAbiValueKind.Pointer)) { args ->
                write(args[2] as RawAddress, first)
                KnownHResults.S_OK.value
            }.use { getAt ->
                ComAbiInteropBridge.createRawInt32Callback(listOf(ComAbiValueKind.Pointer, nativeKind)) { args ->
                    appended = readArgument(args[1])
                    KnownHResults.S_OK.value
                }.use { append ->
                    val vtable = PlatformAbi.allocatePointerArray(scope, 14)
                    PlatformAbi.writePointerAt(vtable, 6, getAt.pointer)
                    PlatformAbi.writePointerAt(vtable, 13, append.pointer)
                    val instance = PlatformAbi.allocatePointerSlot(scope)
                    PlatformAbi.writePointer(instance, vtable)
                    val vector = WinRTVectorReference(instance, WinRTCollectionInterfaceIds.vector(adapter.typeSignature), true)
                    WinRTVectorListAdapter(vector, adapter, adapter::createInputMarshaler).use { list ->
                        assertEquals(first, list[0])
                        list.add(second)
                        assertEquals(second, appended)
                    }
                }
            }
        }
    }

    @Test
    fun ccw_get_many_and_replace_all_use_int32_array_stride() {
        val adapter = WinRTReferenceValueAdapters.valueType(Int::class, "Int32", WinRTTypeSignature.int32())
        val values = mutableListOf(11, 22, 33)
        val iid = WinRTCollectionInterfaceIds.vector(adapter.typeSignature)
        IUnknownReference(WinRTListProjection.fromManaged(values, adapter).asRawComPtr(), iid).use { reference ->
            PlatformAbi.confinedScope().use { scope ->
                val data = PlatformAbi.allocateBytes(scope, 12, 4)
                val count = PlatformAbi.allocateInt32Slot(scope)
                HResult(ComVtableInvoker.invokeArgs(reference.pointer, 16, 0u, 3, data, count)).requireSuccess()
                assertEquals(3, PlatformAbi.readInt32(count))
                assertEquals(listOf(11, 22, 33), (0..2).map { PlatformAbi.readInt32(PlatformAbi.slice(data, it * 4L, 4)) })
                PlatformAbi.writeInt32(data, 99)
                HResult(ComVtableInvoker.invokeArgs(reference.pointer, 17, 1, data)).requireSuccess()
                assertEquals(listOf(99), values)
            }
        }
    }

    @Test
    fun value_key_and_value_dictionary_calls_use_typed_arguments() {
        val key = WinRTReferenceValueAdapters.valueType(Int::class, "Int32", WinRTTypeSignature.int32())
        val value = WinRTReferenceValueAdapters.valueType(Double::class, "Double", WinRTTypeSignature.float64())
        val managed = linkedMapOf(1 to 1.5)
        val iid = WinRTCollectionInterfaceIds.map(key.typeSignature, value.typeSignature)
        IUnknownReference(WinRTDictionaryProjection.fromManaged(managed, key, value).asRawComPtr(), iid).use { reference ->
            val projected = WinRTDictionaryProjection.fromAbi(reference.getRefPointer().asRawAddress(), key, value)!!
            (projected as AutoCloseable).use {
                assertEquals(1.5, projected[1])
                projected[2] = 2.5
                assertTrue(projected.containsKey(2))
                assertEquals(2.5, projected.remove(2))
                assertFalse(projected.containsKey(2))
                assertEquals(mapOf(1 to 1.5), managed)
            }
        }
    }

    @Test
    fun dictionary_size_is_constant_and_entry_views_write_through() {
        val adapter = WinRTReferenceValueAdapters.string
        val managed = linkedMapOf("one" to "before", "two" to "second")
        val iid = WinRTCollectionInterfaceIds.map(adapter.typeSignature, adapter.typeSignature)
        IUnknownReference(WinRTDictionaryProjection.fromManaged(managed, adapter, adapter).asRawComPtr(), iid).use { owner ->
            var sizeCalls = 0
            val reference = object : WinRTMapReference(owner.pointer.asRawAddress(), iid, true) {
                override fun size(): UInt { sizeCalls++; return super.size() }
            }
            WinRTDictionaryProjection.FromAbiHelper(reference, adapter, adapter, WinRTTypeHandle("Test.Map", iid)).use { map ->
                val entries = map.entries
                assertEquals(2, map.size)
                assertEquals(1, sizeCalls)
                val iterator = entries.iterator()
                assertEquals("before", iterator.next().setValue("after"))
                assertEquals("after", managed["one"])
                iterator.remove()
                assertFalse(managed.containsKey("one"))
                assertEquals("two", iterator.next().key)
                assertFalse(iterator.hasNext())
                assertEquals(1, entries.size)
                entries.clear()
                assertTrue(managed.isEmpty())
                assertTrue(entries.isEmpty())
            }
        }
    }
}
