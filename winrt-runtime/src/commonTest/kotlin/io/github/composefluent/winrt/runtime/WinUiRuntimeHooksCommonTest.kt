package io.github.composefluent.winrt.runtime

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun Path.writeText(value: String) {
        SystemFileSystem.sink(this).buffered().use { sink ->
            sink.writeString(value)
        }
    }
}
