package io.github.composefluent.winrt.runtime

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinUiRuntimeHooksCommonTest {
    @Test
    fun xaml_metadata_provider_iids_and_runtime_class_are_stable() {
        assertEquals("Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider", WinUiXamlMetadataProvider.providerRuntimeClassName)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUiXamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF"), WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
    }

    @Test
    fun xaml_metadata_provider_registry_preserves_registration_order_without_duplicates() {
        WinUiXamlMetadataProviderRegistry.clearForTests()

        WinUiXamlMetadataProviderRegistry.register("WinUI3Package.XamlMetaDataProvider")
        WinUiXamlMetadataProviderRegistry.register("WinUI3Package.XamlMetaDataProvider")
        WinUiXamlMetadataProviderRegistry.register("Sample.Sample_XamlTypeInfo.XamlMetaDataProvider")

        assertEquals(
            listOf(
                "WinUI3Package.XamlMetaDataProvider",
                "Sample.Sample_XamlTypeInfo.XamlMetaDataProvider",
            ),
            WinUiXamlMetadataProviderRegistry.registeredRuntimeClassNames(),
        )

        WinUiXamlMetadataProviderRegistry.clearForTests()
    }

    @Test
    fun xaml_metadata_provider_registry_loads_runtime_asset_manifest() {
        val runtimeAssetsRoot = Path("build/kotlin-winrt/runtime-assets")
        val manifest = Path("build/kotlin-winrt/runtime-assets/${WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName}")
        val previousContents = manifest.takeIf { it.isRegularFile() }?.readText()
        SystemFileSystem.createDirectories(runtimeAssetsRoot)
        manifest.writeText(
            """
            WinUI3Package.XamlMetaDataProvider
            WinUI3Package.XamlMetaDataProvider
            # comment
            Sample.Sample_XamlTypeInfo.XamlMetaDataProvider
            """.trimIndent(),
        )

        try {
            WinUiXamlMetadataProviderRegistry.clearForTests()

            assertEquals(
                listOf(
                    "WinUI3Package.XamlMetaDataProvider",
                    "Sample.Sample_XamlTypeInfo.XamlMetaDataProvider",
                ),
                WinUiXamlMetadataProviderRegistry.registeredRuntimeClassNames(),
            )
        } finally {
            WinUiXamlMetadataProviderRegistry.clearForTests()
            if (previousContents == null) {
                runCatching { SystemFileSystem.delete(manifest) }
            } else {
                manifest.writeText(previousContents)
            }
        }
    }

    @Test
    fun resource_manager_requested_handler_iid_matches_parameterized_signature() {
        val expected = ParameterizedInterfaceId.createFromSignature(
            WinRtTypeSignature.parameterizedInterface(
                "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                WinRtTypeSignature.object_(),
                WinRtTypeSignature.runtimeClass(
                    "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs",
                    WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
                ),
            ),
        )

        assertEquals(expected, WinUiResourceManagerRuntime.resourceManagerRequestedHandlerIid())
    }

    @Test
    fun resource_manager_prefers_application_pri_then_microsoft_ui_pri_then_controls_pri() {
        val root = Path("build/kotlin-winrt/common-test-winui-pri")
        runCatching { SystemFileSystem.delete(root / "resources.pri") }
        runCatching { SystemFileSystem.delete(root / "Microsoft.UI.pri") }
        runCatching { SystemFileSystem.delete(root / "Microsoft.UI.Xaml.Controls.pri") }
        SystemFileSystem.createDirectories(root)
        val applicationPri = root / "resources.pri"
        val controlsPri = root / "Microsoft.UI.Xaml.Controls.pri"
        val microsoftUiPri = root / "Microsoft.UI.pri"
        controlsPri.writeText("controls")

        assertEquals(controlsPri, WinUiResourceManagerSupport.preferredPriPath(root))

        microsoftUiPri.writeText("ui")

        assertEquals(microsoftUiPri, WinUiResourceManagerSupport.preferredPriPath(root))

        applicationPri.writeText("application")

        assertEquals(applicationPri, WinUiResourceManagerSupport.preferredPriPath(root))
    }

    @Test
    fun resource_manager_returns_null_when_no_pri_exists() {
        val root = Path("build/kotlin-winrt/common-test-winui-no-pri")
        runCatching { SystemFileSystem.delete(root / "resources.pri") }
        runCatching { SystemFileSystem.delete(root / "Microsoft.UI.pri") }
        runCatching { SystemFileSystem.delete(root / "Microsoft.UI.Xaml.Controls.pri") }
        SystemFileSystem.createDirectories(root)

        assertNull(WinUiResourceManagerSupport.preferredPriPath(root))
    }

    private fun Path.writeText(value: String) {
        SystemFileSystem.sink(this).buffered().use { sink ->
            sink.writeString(value)
        }
    }

    private operator fun Path.div(child: String): Path = Path(this, child)
}
