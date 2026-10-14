package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UWPXamlRuntimeTest {
    private class Model(var title: String = "initial")

    @Test
    fun explicit_framework_selection_is_idempotent_and_cannot_change_live_projections() = withFramework(XamlFramework.UWP) {
        // CsWinRT Configuration/FeatureSwitches.cs and Projections.cs share a process-wide choice.
        XamlRuntime.initialize(XamlFramework.UWP)
        assertEquals(XamlFramework.UWP, XamlRuntime.framework)
        assertEquals("Windows.UI.Xaml.Data.INotifyPropertyChanged",
            TypeNameSupport.getNameForType(microsoft.ui.xaml.data.INotifyPropertyChanged::class))
        assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.WinUI) }
        assertEquals(Guid("B3765D69-68A5-4B32-8861-FDB90C1F5836"), XamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUIXamlInterfaceIds.IXamlMetadataProvider)
        assertNull(XamlProjectionConfiguration.abi.defaultMetadataProviderRuntimeClassName)
    }

    @Test
    fun first_projection_read_freezes_the_feature_switch_default() {
        FeatureSwitches.overrideForTests(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, true)
        try {
            assertEquals(XamlFramework.UWP, XamlRuntime.framework)
            assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.WinUI) }
            FeatureSwitches.overrideForTests(FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName, false)
            assertEquals(XamlFramework.WinUI, XamlRuntime.framework)
            assertFailsWith<IllegalStateException> { XamlRuntime.initialize(XamlFramework.UWP) }
        } finally {
            FeatureSwitches.clearForTests()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun uwp_application_metadata_provider_uses_sdk_slots_and_owned_member_results() = withFramework(XamlFramework.UWP) {
        // Windows SDK windows.ui.xaml.markup.h and XamlCompiler XamlUserType/XamlMember;
        // CsWinRT ComWrappersSupport owns identity and IInspectable result ownership.
        var initialized = 0
        registerXamlTypeDefinition(XamlTypeDefinition(
            Model::class, "Test.UWP.Model", "System.Object", baseType = Any::class,
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
            assertNull(provider.tryQueryInterface(WinUIXamlInterfaceIds.IXamlMetadataProvider))
            assertNotNull(provider.getXamlTypeByFullName("Test.UWP.Model")).use { type ->
                assertEquals(Guid("7920EAB1-A2E5-479A-BD50-6CEF3C0B4970"), type.interfaceId)
                assertNull(type.tryQueryInterface(WinUIXamlInterfaceIds.IXamlType))
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
                            assertNull(member.tryQueryInterface(WinUIXamlInterfaceIds.IXamlMember))
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
                    HString.create("Test.UWP.Unknown").use { name ->
                        HResult(ComVtableInvoker.invokeArgs(provider.pointer, 7, name.handle, result)).requireSuccess()
                        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(result)))
                    }
                }
            }
            assertNull(provider.getXamlTypeByFullName("Test.UWP.Unknown"))
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
    fun framework_augmentation_does_not_attach_uwp_metadata_to_winui_application() = withFramework(XamlFramework.UWP) {
        val application = WinRTCcwDefinition(
            listOf(WinRTInspectableInterfaceDefinition(WinUIXamlInterfaceIds.IApplicationOverrides, emptyList())),
            WinUIXamlInterfaceIds.IApplicationOverrides,
        )
        assertSame(application, XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(application))
    }

    private fun withFramework(framework: XamlFramework, block: () -> Unit) {
        FeatureSwitches.clearForTests()
        XamlRuntime.initialize(framework)
        ComWrappersSupport.clearRegistriesForTests()
        WinUIXamlMetadataProviderRegistry.clearForTests()
        XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
        try {
            block()
        } finally {
            XamlSystemProjectionRuntimeHooks.closeRuntimeCaches()
            WinUIXamlMetadataProviderRegistry.clearForTests()
            FeatureSwitches.clearForTests()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }
}
