package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class WinUIRuntimeHooksTest {
    @Test
    fun xaml_metadata_provider_registry_loads_runtime_asset_manifest() {
        val previousRoot = System.getProperty(WinRTRuntimeAssets.runtimeAssetsRootPropertyName)
        val root = Files.createTempDirectory("kotlin-winrt-xaml-provider-assets-")
        Files.writeString(
            root.resolve(WinUIRuntimeAssetManifests.xamlMetadataProvidersFileName),
            """
            WinUI3Package.XamlMetaDataProvider
            WinUI3Package.XamlMetaDataProvider
            # comment
            Sample.Sample_XamlTypeInfo.XamlMetaDataProvider
            """.trimIndent(),
        )

        try {
            System.setProperty(WinRTRuntimeAssets.runtimeAssetsRootPropertyName, root.toString())
            WinUIXamlMetadataProviderRegistry.clearForTests()

            assertEquals(
                listOf(
                    "WinUI3Package.XamlMetaDataProvider",
                    "Sample.Sample_XamlTypeInfo.XamlMetaDataProvider",
                ),
                WinUIXamlMetadataProviderRegistry.registeredRuntimeClassNames(),
            )
        } finally {
            WinUIXamlMetadataProviderRegistry.clearForTests()
            if (previousRoot == null) {
                System.clearProperty(WinRTRuntimeAssets.runtimeAssetsRootPropertyName)
            } else {
                System.setProperty(WinRTRuntimeAssets.runtimeAssetsRootPropertyName, previousRoot)
            }
        }
    }

}
