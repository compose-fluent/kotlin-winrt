package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class WinUiRuntimeHooksTest {
    @Test
    fun xaml_metadata_provider_registry_loads_runtime_asset_manifest() {
        val previousRoot = System.getProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName)
        val root = Files.createTempDirectory("kotlin-winrt-xaml-provider-assets-")
        Files.writeString(
            root.resolve(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName),
            """
            WinUI3Package.XamlMetaDataProvider
            WinUI3Package.XamlMetaDataProvider
            # comment
            Sample.Sample_XamlTypeInfo.XamlMetaDataProvider
            """.trimIndent(),
        )

        try {
            System.setProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName, root.toString())
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
            if (previousRoot == null) {
                System.clearProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName)
            } else {
                System.setProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName, previousRoot)
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

        assertEquals(expected, WinUiResourceManagerSupport.resourceManagerRequestedHandlerIid())
    }

    @Test
    fun resource_manager_prefers_application_pri_then_microsoft_ui_pri_then_controls_pri() {
        val root = Files.createTempDirectory("kotlin-winrt-winui-pri-")
        val applicationPri = root.resolve("resources.pri")
        val controlsPri = root.resolve("Microsoft.UI.Xaml.Controls.pri")
        val microsoftUiPri = root.resolve("Microsoft.UI.pri")
        Files.writeString(controlsPri, "controls")

        assertEquals(controlsPri, WinUiResourceManagerSupport.preferredPriPath(root))

        Files.writeString(microsoftUiPri, "ui")

        assertEquals(microsoftUiPri, WinUiResourceManagerSupport.preferredPriPath(root))

        Files.writeString(applicationPri, "application")

        assertEquals(applicationPri, WinUiResourceManagerSupport.preferredPriPath(root))
    }

    @Test
    fun resource_manager_returns_null_when_no_pri_exists() {
        val root = Files.createTempDirectory("kotlin-winrt-winui-no-pri-")

        assertNull(WinUiResourceManagerSupport.preferredPriPath(root))
    }
}
