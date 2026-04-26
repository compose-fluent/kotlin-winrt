package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class WinUiRuntimeHooksTest {
    @Test
    fun xaml_metadata_provider_iids_and_runtime_class_are_stable() {
        assertEquals("Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider", WinUiXamlMetadataProvider.providerRuntimeClassName)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUiXamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF"), WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
    }

}
