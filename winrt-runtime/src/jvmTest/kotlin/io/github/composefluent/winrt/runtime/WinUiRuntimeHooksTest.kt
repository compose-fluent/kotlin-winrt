package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
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

}
