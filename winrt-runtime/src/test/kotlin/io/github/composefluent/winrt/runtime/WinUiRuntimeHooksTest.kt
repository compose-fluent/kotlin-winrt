package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class WinUiRuntimeHooksTest {
    @Test
    fun xaml_metadata_provider_iids_and_runtime_class_are_stable() {
        assertEquals("Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider", WinUiXamlMetadataProvider.providerRuntimeClassName)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUiXamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF"), WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
    }

    @Test
    fun resource_manager_requested_delegate_iid_matches_staged_winui_signature() {
        assertEquals(
            ParameterizedInterfaceId.createFromSignature(
                "pinterface({9de1c534-6ae1-11e0-84e1-18a905bcc53f};cinterface(IInspectable);rc(Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs;{c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a}))",
            ),
            WinRtWinUiResourceManagerBootstrap.resourceManagerRequestedHandlerIid(),
        )
    }

    @Test
    fun winui_resource_manager_bootstrap_prefers_packaged_resources_pri_alias() {
        val root = Files.createTempDirectory("kotlin-winrt-pri")
        try {
            val resourcesPri = Files.createFile(root.resolve("resources.pri"))
            Files.createFile(root.resolve("Microsoft.UI.pri"))
            Files.createFile(root.resolve("Microsoft.UI.Xaml.Controls.pri"))

            assertEquals(resourcesPri, WinRtWinUiResourceManagerBootstrap.preferredPriPath(root))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun winui_resource_manager_bootstrap_skips_missing_pri_assets() {
        val root = Files.createTempDirectory("kotlin-winrt-pri")
        try {
            assertNull(WinRtWinUiResourceManagerBootstrap.preferredPriPath(root))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
