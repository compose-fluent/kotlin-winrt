package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinUiAuthoredTypeMetadataTest {
    private class DerivedControl

    @Test
    fun closed_types_expose_key_item_and_boxed_types_and_typed_operations_through_the_abi() {
        // XamlCompiler CSharpTypeInfoPass2.tt: XamlUserType.KeyType/ItemType/DictionaryAdd.
        val name = "Windows.Foundation.Collections.IMap`2<String,String>"
        val values = mutableMapOf<String, String>()
        WinUiAuthoredTypeMetadata.registerDefinition(WinRTXamlTypeDefinition(
            DerivedControl::class, "Test.DictionaryOwner", "System.Object",
            members = listOf(WinRTXamlMemberDefinition("Values", name, get = { values },
                dictionary = WinRTXamlDictionaryDefinition(MutableMap::class, "String", String::class,
                    "String", String::class, add = { instance, key, item ->
                        @Suppress("UNCHECKED_CAST")
                        (instance as MutableMap<String, String>)[key as String] = item as String
                    })),
                WinRTXamlMemberDefinition("Optional", "Windows.Foundation.IReference`1<Int32>", Int::class, get = { null },
                    valueTypes = listOf(WinRTXamlValueTypeDefinition("Windows.Foundation.IReference`1<Int32>", Int::class,
                        boxedTypeName = "Int32", boxedType = Int::class))),
                WinRTXamlMemberDefinition("Labels", "String[]", Array::class, get = { arrayOf("label") },
                    valueTypes = listOf(WinRTXamlValueTypeDefinition("String[]", Array::class, isArray = true,
                        itemTypeName = "String", itemType = String::class))),
            ),
        ))
        try {
            val pointer = WinUiAuthoredTypeMetadata.tryCreate(name) { PlatformAbi.nullPointer }
            IUnknownReference(pointer.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 10, result)).requireSuccess()
                    assertEquals(0, PlatformAbi.readInt8(result).toInt())
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 12, result)).requireSuccess()
                    assertEquals(1, PlatformAbi.readInt8(result).toInt())
                    for (slot in listOf(15, 16)) {
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, slot, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { element ->
                            HResult(ComVtableInvoker.invokeArgs(element.pointer, 8, result)).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use {
                                assertEquals("String", it.toKString())
                            }
                        }
                    }
                    WinRTObjectMarshaller.createMarshaler(values).use { instance ->
                        WinRTObjectMarshaller.createMarshaler("key").use { key ->
                            WinRTObjectMarshaller.createMarshaler("value").use { item ->
                                HResult(ComVtableInvoker.invokeArgs(type.pointer, 23, instance.abi, key.abi, item.abi)).requireSuccess()
                            }
                        }
                    }
                    assertEquals(mapOf("key" to "value"), values)
                }
            }
            for ((closedName, slot, elementName) in listOf(Triple("String[]", 15, "String"),
                Triple("Windows.Foundation.IReference`1<Int32>", 17, "Int32"))) {
                val pointer = WinUiAuthoredTypeMetadata.tryCreate(closedName) { PlatformAbi.nullPointer }
                IUnknownReference(pointer.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { type ->
                    PlatformAbi.confinedScope().use { scope ->
                        val result = PlatformAbi.allocatePointerSlot(scope)
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, slot, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { element ->
                            HResult(ComVtableInvoker.invokeArgs(element.pointer, 8, result)).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use { assertEquals(elementName, it.toKString()) }
                        }
                        if (slot == 17) HString.create("42").use { input ->
                            // CSharp XamlSystemBaseType leaves primitive parsing to the SDK provider.
                            assertEquals(KnownHResults.E_NOTIMPL.value,
                                ComVtableInvoker.invokeArgs(type.pointer, 20, input.handle, result))
                            assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                        } else {
                            HResult(ComVtableInvoker.invokeArgs(type.pointer, 9, result)).requireSuccess()
                            assertEquals(1, PlatformAbi.readInt8(result).toInt())
                        }
                    }
                }
            }
        } finally {
            WinUiAuthoredTypeMetadata.clearForTests()
        }
    }

    @Test
    fun authored_control_exposes_its_identity_and_native_base_without_activating_the_base() {
        // CsWinRT CCW identity + WinUI IXamlType contract used by MetadataAPI's style checks.
        val base = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                interfaceId = WinUiXamlInterfaceIds.IXamlType,
                methods = emptyList(),
            )),
            defaultInterfaceId = WinUiXamlInterfaceIds.IXamlType,
        )
        Projections.registerAuthoredRuntimeClassType(DerivedControl::class, "Test.DerivedControl", "Test.NativeControl")
        try {
            val pointer = WinUiAuthoredTypeMetadata.tryCreate("Test.DerivedControl") { name ->
                assertEquals("Test.NativeControl", name)
                base.acquireReference(WinUiXamlInterfaceIds.IXamlType)
            }
            IUnknownReference(pointer.asRawComPtr(), WinUiXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 8, result)).requireSuccess()
                    HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use {
                        assertEquals("Test.DerivedControl", it.toKString())
                    }
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 6, result)).requireSuccess()
                    IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { actualBase ->
                        base.createReference(WinUiXamlInterfaceIds.IXamlType).use { expectedBase ->
                            assertEquals(expectedBase.pointer, actualBase.pointer)
                        }
                    }
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 11, result)).requireSuccess()
                    assertEquals(0, PlatformAbi.readInt8(result).toInt())
                    assertEquals(KnownHResults.E_NOTIMPL.value, ComVtableInvoker.invokeArgs(type.pointer, 19, result))
                    assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                }
            }
            assertTrue(PlatformAbi.isNull(WinUiAuthoredTypeMetadata.tryCreate("Test.Unregistered") {
                error("Unknown types must be left to the SDK provider")
            }))
            assertTrue(PlatformAbi.isNull(WinUiAuthoredTypeMetadata.tryCreate("Test.DerivedControl") {
                PlatformAbi.nullPointer
            }))
        } finally {
            base.releaseManagedReference()
            WinUiAuthoredTypeMetadata.clearForTests()
        }
    }
}
