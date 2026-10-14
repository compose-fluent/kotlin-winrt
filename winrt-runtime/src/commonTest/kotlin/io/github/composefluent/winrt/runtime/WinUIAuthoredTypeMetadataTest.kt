package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIAuthoredTypeMetadataTest {
    private class DerivedControl
    private open class Model(var title: String = "initial") { val readOnly: String get() = "fixed" }
    private class DerivedModel : Model()
    private enum class Choice { First }

    @Test
    fun projected_metadata_is_a_fallback_and_preserves_projection_identity() {
        // CSharpTypeInfoPass2 keeps referenced type activators separate from
        // component authoring and consults the library's metadata provider.
        var constructed = 0
        val wasWinRT = Projections.isTypeWindowsRuntimeType(Model::class)
        registerWinUIXamlProjectedTypeDefinition(WinUIXamlTypeDefinition(
            Model::class, "Test.ProjectedXamlModel", "System.Object", baseType = Any::class,
            activate = { constructed++; Model() }, isBindable = false,
        ))
        try {
            assertEquals(wasWinRT, Projections.isTypeWindowsRuntimeType(Model::class))
            assertEquals(PlatformAbi.nullPointer,
                WinUIAuthoredTypeMetadata.tryCreateAuthored("Test.ProjectedXamlModel") { PlatformAbi.nullPointer })
            val pointer = WinUIAuthoredTypeMetadata.tryCreate("Test.ProjectedXamlModel") { PlatformAbi.nullPointer }
            IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 11, result)).requireSuccess()
                    assertEquals(1, PlatformAbi.readInt8(result).toInt())
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 19, result)).requireSuccess()
                    IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { instance ->
                        assertTrue(WinRTObjectMarshaller.fromAbi(instance.pointer.asRawAddress()) is Model)
                    }
                    assertEquals(1, constructed)
                }
            }
        } finally {
            WinUIAuthoredTypeMetadata.clearForTests()
        }
    }

    @Test
    fun ordinary_models_expose_inherited_members_content_and_factories_without_component_authoring() {
        // XamlCompiler XamlUserType/XamlMember delegates and CsWinRT's ICustomPropertyProvider.
        registerWinUIXamlTypeDefinition(WinUIXamlTypeDefinition(
            Model::class, "Test.XamlModel", "System.Object", baseType = Any::class,
            contentProperty = "Title", isWinRTComponent = false,
            members = listOf(
                WinUIXamlMemberDefinition("Title", "String", String::class,
                    get = { (it as Model).title }, set = { instance, value -> (instance as Model).title = value as String }),
                WinUIXamlMemberDefinition("ReadOnly", "String", String::class, get = { (it as Model).readOnly }),
            ),
        ))
        var initialized = 0
        registerWinUIXamlTypeDefinition(WinUIXamlTypeDefinition(
            DerivedModel::class, "Test.DerivedXamlModel", "Test.XamlModel", baseType = Model::class,
            activate = { DerivedModel() }, createFromString = { text -> DerivedModel().apply { title = text } },
            initializer = { initialized++ }, isWinRTComponent = false,
        ))
        try {
            assertFalse(Projections.isTypeWindowsRuntimeType(DerivedModel::class))
            val pointer = WinUIAuthoredTypeMetadata.tryCreate("Test.DerivedXamlModel") { PlatformAbi.nullPointer }
            IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    for (slot in listOf(11, 14)) {
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, slot, result)).requireSuccess()
                        assertEquals(1, PlatformAbi.readInt8(result).toInt())
                    }
                    HResult(ComVtableInvoker.invoke(type.pointer, 24)).requireSuccess()
                    assertEquals(1, initialized)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 19, result)).requireSuccess()
                    IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { instance ->
                        val model = WinRTObjectMarshaller.fromAbi(instance.pointer.asRawAddress()) as DerivedModel
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, 7, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUIXamlInterfaceIds.IXamlMember).use { member ->
                            HResult(ComVtableInvoker.invokeArgs(member.pointer, 9, result)).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use { assertEquals("Title", it.toKString()) }
                            for ((slot, expected) in listOf(10 to "Test.XamlModel", 11 to "String")) {
                                HResult(ComVtableInvoker.invokeArgs(member.pointer, slot, result)).requireSuccess()
                                IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { memberType ->
                                    HResult(ComVtableInvoker.invokeArgs(memberType.pointer, 8, result)).requireSuccess()
                                    HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use { assertEquals(expected, it.toKString()) }
                                }
                            }
                            HResult(ComVtableInvoker.invokeArgs(member.pointer, 12, instance.pointer.asRawAddress(), result)).requireSuccess()
                            IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { value ->
                                assertEquals("initial", WinRTObjectMarshaller.fromAbi(value.pointer.asRawAddress()))
                            }
                            WinRTObjectMarshaller.createMarshaler("updated").use { value ->
                                HResult(ComVtableInvoker.invokeArgs(member.pointer, 13, instance.pointer.asRawAddress(), value.abi)).requireSuccess()
                            }
                            assertEquals("updated", model.title)
                        }
                        // Binding uses the same inherited accessors as IXamlMember.
                        val custom = requireNotNull(WinUIAuthoredTypeMetadata.customProperty(model, "Title"))
                        assertEquals("updated", custom.getValue(model))
                        custom.setValue(model, "bindable")
                        assertEquals("bindable", model.title)
                        HString.create("ReadOnly").use { name ->
                            HResult(ComVtableInvoker.invokeArgs(type.pointer, 21, name.handle, result)).requireSuccess()
                            IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUIXamlInterfaceIds.IXamlMember).use { member ->
                                HResult(ComVtableInvoker.invokeArgs(member.pointer, 8, result)).requireSuccess()
                                assertEquals(1, PlatformAbi.readInt8(result).toInt())
                                assertEquals(KnownHResults.E_NOTIMPL.value,
                                    ComVtableInvoker.invokeArgs(member.pointer, 13, instance.pointer.asRawAddress(), PlatformAbi.nullPointer))
                            }
                        }
                        HString.create("Missing").use { name ->
                            HResult(ComVtableInvoker.invokeArgs(type.pointer, 21, name.handle, result)).requireSuccess()
                            assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                        }
                    }
                    HString.create("parsed").use { input ->
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, 20, input.handle, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { value ->
                            assertEquals("parsed", (WinRTObjectMarshaller.fromAbi(value.pointer.asRawAddress()) as DerivedModel).title)
                        }
                    }
                }
            }
        } finally {
            WinUIAuthoredTypeMetadata.clearForTests()
        }
    }

    @Test
    fun system_type_failures_clear_abi_outputs_even_when_the_parser_throws() {
        // CsWinRT write_out_initialize initializes the result before managed dispatch.
        WinUIAuthoredTypeMetadata.registerEnum(Choice::class, "Test.XamlChoice") { throw IllegalArgumentException("parse") }
        try {
            val pointer = WinUIAuthoredTypeMetadata.tryCreate("Test.XamlChoice") { PlatformAbi.nullPointer }
            IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                    assertEquals(KnownHResults.E_NOTIMPL.value, ComVtableInvoker.invokeArgs(type.pointer, 19, result))
                    assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    HString.create("unused").use { input ->
                        PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                        assertEquals(KnownHResults.E_NOTIMPL.value,
                            ComVtableInvoker.invokeArgs(type.pointer, 21, input.handle, result))
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                        PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                        assertFalse(HResult(ComVtableInvoker.invokeArgs(type.pointer, 20, input.handle, result)).isSuccess)
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    }
                }
            }
        } finally {
            WinUIAuthoredTypeMetadata.clearForTests()
        }
    }

    @Test
    fun closed_types_expose_key_item_and_boxed_types_and_typed_operations_through_the_abi() {
        // XamlCompiler CSharpTypeInfoPass2.tt: XamlUserType.KeyType/ItemType/DictionaryAdd.
        val name = "Windows.Foundation.Collections.IMap`2<String,String>"
        val values = mutableMapOf<String, String>()
        WinUIAuthoredTypeMetadata.registerDefinition(WinUIXamlTypeDefinition(
            DerivedControl::class, "Test.DictionaryOwner", "System.Object",
            members = listOf(WinUIXamlMemberDefinition("Values", name, get = { values },
                dictionary = WinUIXamlDictionaryDefinition(MutableMap::class, "String", String::class,
                    "String", String::class, add = { instance, key, item ->
                        @Suppress("UNCHECKED_CAST")
                        (instance as MutableMap<String, String>)[key as String] = item as String
                    })),
                WinUIXamlMemberDefinition("Optional", "Windows.Foundation.IReference`1<Int32>", Int::class, get = { null },
                    valueTypes = listOf(WinUIXamlValueTypeDefinition("Windows.Foundation.IReference`1<Int32>", Int::class,
                        boxedTypeName = "Int32", boxedType = Int::class))),
                WinUIXamlMemberDefinition("Labels", "String[]", Array::class, get = { arrayOf("label") },
                    valueTypes = listOf(WinUIXamlValueTypeDefinition("String[]", Array::class, isArray = true,
                        itemTypeName = "String", itemType = String::class))),
            ),
        ))
        try {
            val pointer = WinUIAuthoredTypeMetadata.tryCreate(name) { PlatformAbi.nullPointer }
            IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 10, result)).requireSuccess()
                    assertEquals(0, PlatformAbi.readInt8(result).toInt())
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 12, result)).requireSuccess()
                    assertEquals(1, PlatformAbi.readInt8(result).toInt())
                    for (slot in listOf(15, 16)) {
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, slot, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { element ->
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
                val pointer = WinUIAuthoredTypeMetadata.tryCreate(closedName) { PlatformAbi.nullPointer }
                IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                    PlatformAbi.confinedScope().use { scope ->
                        val result = PlatformAbi.allocatePointerSlot(scope)
                        HResult(ComVtableInvoker.invokeArgs(type.pointer, slot, result)).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { element ->
                            HResult(ComVtableInvoker.invokeArgs(element.pointer, 8, result)).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use { assertEquals(elementName, it.toKString()) }
                        }
                        if (slot == 17) HString.create("42").use { input ->
                            // CSharp XamlSystemBaseType leaves primitive parsing to the SDK provider.
                            PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                            assertEquals(KnownHResults.E_NOTIMPL.value,
                                ComVtableInvoker.invokeArgs(type.pointer, 20, input.handle, result))
                            assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                        } else {
                            HResult(ComVtableInvoker.invokeArgs(type.pointer, 9, result)).requireSuccess()
                            assertEquals(1, PlatformAbi.readInt8(result).toInt())
                            HString.create("unused").use { input ->
                                PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                                assertEquals(KnownHResults.E_NOTIMPL.value,
                                    ComVtableInvoker.invokeArgs(type.pointer, 20, input.handle, result))
                                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                            }
                        }
                        PlatformAbi.writePointer(result, type.pointer.asRawAddress())
                        assertEquals(KnownHResults.E_NOTIMPL.value, ComVtableInvoker.invokeArgs(type.pointer, 19, result))
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    }
                }
            }
        } finally {
            WinUIAuthoredTypeMetadata.clearForTests()
        }
    }

    @Test
    fun authored_control_exposes_its_identity_and_native_base_without_activating_the_base() {
        // CsWinRT CCW identity + WinUI IXamlType contract used by MetadataAPI's style checks.
        val base = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                interfaceId = WinUIXamlInterfaceIds.IXamlType,
                methods = emptyList(),
            )),
            defaultInterfaceId = WinUIXamlInterfaceIds.IXamlType,
        )
        Projections.registerAuthoredRuntimeClassType(DerivedControl::class, "Test.DerivedControl", "Test.NativeControl")
        try {
            val pointer = WinUIAuthoredTypeMetadata.tryCreate("Test.DerivedControl") { name ->
                assertEquals("Test.NativeControl", name)
                base.acquireReference(WinUIXamlInterfaceIds.IXamlType)
            }
            IUnknownReference(pointer.asRawComPtr(), WinUIXamlInterfaceIds.IXamlType).use { type ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 8, result)).requireSuccess()
                    HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use {
                        assertEquals("Test.DerivedControl", it.toKString())
                    }
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 6, result)).requireSuccess()
                    IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { actualBase ->
                        base.createReference(WinUIXamlInterfaceIds.IXamlType).use { expectedBase ->
                            assertEquals(expectedBase.pointer, actualBase.pointer)
                        }
                    }
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 11, result)).requireSuccess()
                    assertEquals(0, PlatformAbi.readInt8(result).toInt())
                    assertEquals(KnownHResults.E_NOTIMPL.value, ComVtableInvoker.invokeArgs(type.pointer, 19, result))
                    assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                }
            }
            assertTrue(PlatformAbi.isNull(WinUIAuthoredTypeMetadata.tryCreate("Test.Unregistered") {
                error("Unknown types must be left to the SDK provider")
            }))
            assertTrue(PlatformAbi.isNull(WinUIAuthoredTypeMetadata.tryCreate("Test.DerivedControl") {
                PlatformAbi.nullPointer
            }))
        } finally {
            base.releaseManagedReference()
            WinUIAuthoredTypeMetadata.clearForTests()
        }
    }
}
