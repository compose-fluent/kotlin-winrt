package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UwpXamlRuntimeTest {
    private class Model(var title: String = "initial")

    @Test
    fun explicit_framework_selection_is_idempotent_and_cannot_change_live_projections() = withFramework(XamlFramework.Uwp) {
        // CsWinRT Configuration/FeatureSwitches.cs and Projections.cs share a process-wide choice.
        XamlRuntime.initialize(XamlFramework.Uwp)
        assertEquals(XamlFramework.Uwp, XamlRuntime.framework)
        assertEquals("Windows.UI.Xaml.Data.INotifyPropertyChanged",
            TypeNameSupport.getNameForType(microsoft.ui.xaml.data.INotifyPropertyChanged::class))
        assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.WinUi) }
        assertEquals(Guid("B3765D69-68A5-4B32-8861-FDB90C1F5836"), XamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUiXamlInterfaceIds.IXamlMetadataProvider)
        assertNull(XamlProjectionConfiguration.abi.defaultMetadataProviderRuntimeClassName)
    }

    @Test
    fun first_projection_read_freezes_the_feature_switch_default() {
        FeatureSwitches.overrideForTests(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, true)
        try {
            assertEquals(XamlFramework.Uwp, XamlRuntime.framework)
            assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.WinUi) }
            FeatureSwitches.overrideForTests(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, false)
            assertEquals(XamlFramework.WinUi, XamlRuntime.framework)
            assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.Uwp) }
        } finally {
            FeatureSwitches.clearForTests()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun uwp_application_metadata_provider_uses_sdk_slots_and_owned_member_results() = withFramework(XamlFramework.Uwp) {
        // Windows SDK windows.ui.xaml.markup.h and XamlCompiler XamlUserType/XamlMember;
        // CsWinRT ComWrappersSupport owns identity and IInspectable result ownership.
        var initialized = 0
        registerXamlTypeDefinition(XamlTypeDefinition(
            Model::class, "Test.Uwp.Model", "System.Object", baseType = Any::class,
            activate = { Model() }, initializer = { initialized++ }, isWinRTComponent = false,
            members = listOf(XamlMemberDefinition("Title", "String", String::class,
                get = { (it as Model).title }, set = { target, value -> (target as Model).title = value as String })),
            valueTypes = listOf(XamlValueTypeDefinition(
                "Windows.Foundation.IReference`1<Int32>", Int::class, boxedTypeName = "Int32", boxedType = Int::class)),
        ))
        val application = WinRTCcwDefinition(
            listOf(WinRTInspectableInterfaceDefinition(XamlInterfaceIds.IApplicationOverrides, emptyList())),
            XamlInterfaceIds.IApplicationOverrides,
        )
        val augmented = XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(application)
        assertEquals(2, augmented.interfaceDefinitions.size)
        assertSame(augmented, XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(augmented))
        val host = WinRTInspectableComObject(
            interfaceDefinitions = augmented.interfaceDefinitions,
            defaultInterfaceId = augmented.defaultInterfaceId,
        )
        XamlMetadataProviderReference(host.detachReference(XamlInterfaceIds.IXamlMetadataProvider)).use { provider ->
            assertNull(provider.tryQueryInterface(WinUiXamlInterfaceIds.IXamlMetadataProvider))
            assertNotNull(provider.getXamlTypeByFullName("Test.Uwp.Model")).use { type ->
                assertEquals(Guid("7920EAB1-A2E5-479A-BD50-6CEF3C0B4970"), type.interfaceId)
                assertNull(type.tryQueryInterface(WinUiXamlInterfaceIds.IXamlType))
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocatePointerSlot(scope)
                    // Literal SDK slots deliberately verify the ABI, independently of XamlTypeAbi.
                    val underlying = PlatformAbi.allocateBytes(scope, TypeProjection.LAYOUT.sizeBytes, TypeProjection.LAYOUT.alignmentBytes)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 17, underlying)).requireSuccess()
                    try {
                        // CsWinRT Type.ToAbi uses the managed name for TypeKind.Custom.
                        // This fixture deliberately has a different XAML metadata alias.
                        assertEquals(Model::class.qualifiedName, PlatformAbi.readHString(PlatformAbi.readPointer(underlying)))
                        assertEquals(WinRTTypeKind.Custom.ordinal,
                            PlatformAbi.readInt32(TypeProjection.LAYOUT.slice(underlying, "kind")))
                    } finally {
                        TypeProjection.disposeAbi(underlying)
                    }
                    HResult(ComVtableInvoker.invoke(type.pointer, 23)).requireSuccess()
                    assertEquals(1, initialized)
                    HResult(ComVtableInvoker.invokeArgs(type.pointer, 18, result)).requireSuccess()
                    IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { instance ->
                        val model = WinRTObjectMarshaller.fromAbi(instance.pointer.asRawAddress()) as Model
                        HString.create("Title").use { name ->
                            HResult(ComVtableInvoker.invokeArgs(type.pointer, 20, name.handle, result)).requireSuccess()
                        }
                        IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr(), XamlInterfaceIds.IXamlMember).use { member ->
                            assertNotNull(member.tryQueryInterface(Guid("C541F58C-43A9-4216-B718-E0B11B14E93E"))).close()
                            assertNull(member.tryQueryInterface(WinUiXamlInterfaceIds.IXamlMember))
                            WinRTObjectMarshaller.createMarshaler("updated").use { value ->
                                HResult(ComVtableInvoker.invokeArgs(member.pointer, 13, instance.pointer.asRawAddress(), value.abi)).requireSuccess()
                            }
                            assertEquals("updated", model.title)
                            HResult(ComVtableInvoker.invokeArgs(member.pointer, 12, instance.pointer.asRawAddress(), result)).requireSuccess()
                            IUnknownReference(PlatformAbi.readPointer(result).asRawComPtr()).use { value ->
                                assertEquals("updated", WinRTObjectMarshaller.fromAbi(value.pointer.asRawAddress()))
                            }
                        }
                    }
                    assertNotNull(type.tryQueryInterface(requireNotNull(XamlInterfaceIds.IXamlType2))).use { extended ->
                        HResult(ComVtableInvoker.invokeArgs(extended.pointer, 6, result)).requireSuccess()
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    }
                    HString.create("Test.Uwp.Unknown").use { name ->
                        HResult(ComVtableInvoker.invokeArgs(provider.pointer, 7, name.handle, result)).requireSuccess()
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    }
                }
            }
            assertNull(provider.getXamlTypeByFullName("Test.Uwp.Unknown"))
            assertNotNull(provider.getXamlTypeByFullName("Windows.Foundation.IReference`1<Int32>")).use { nullable ->
                assertNotNull(nullable.tryQueryInterface(requireNotNull(XamlInterfaceIds.IXamlType2))).use { extended ->
                    PlatformAbi.confinedScope().use { scope ->
                        val result = PlatformAbi.allocatePointerSlot(scope)
                        HResult(ComVtableInvoker.invokeArgs(extended.pointer, 6, result)).requireSuccess()
                        XamlTypeReference(PlatformAbi.readPointer(result)).use { boxed ->
                            HResult(ComVtableInvoker.invokeArgs(boxed.pointer, 8, result)).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(result), owner = true).use { assertEquals("Int32", it.toKString()) }
                        }
                    }
                }
            }
            val definitions = provider.getXmlnsDefinitions()
            assertEquals(KnownHResults.S_OK.value, definitions.hResult)
            assertTrue(definitions.definitions.isEmpty())
        }
    }

    @Test
    fun framework_augmentation_does_not_attach_uwp_metadata_to_winui_application() = withFramework(XamlFramework.Uwp) {
        val application = WinRTCcwDefinition(
            listOf(WinRTInspectableInterfaceDefinition(WinUiXamlInterfaceIds.IApplicationOverrides, emptyList())),
            WinUiXamlInterfaceIds.IApplicationOverrides,
        )
        assertSame(application, XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(application))
    }

    private fun withFramework(framework: XamlFramework, block: () -> Unit) {
        FeatureSwitches.clearForTests()
        XamlRuntime.initialize(framework)
        ComWrappersSupport.clearRegistriesForTests()
        WinUiXamlMetadataProviderRegistry.clearForTests()
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
        try {
            block()
        } finally {
            XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
            WinUiXamlMetadataProviderRegistry.clearForTests()
            FeatureSwitches.clearForTests()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }
}
