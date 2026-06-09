package io.github.composefluent.winrt.runtime

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
}
