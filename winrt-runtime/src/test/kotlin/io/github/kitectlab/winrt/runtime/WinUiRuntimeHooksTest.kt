package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class WinUiRuntimeHooksTest {
    @Test
    fun xaml_metadata_provider_iids_and_runtime_class_are_stable() {
        assertEquals("Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider", WinUiXamlMetadataProvider.providerRuntimeClassName)
        assertEquals(Guid("A96251F0-2214-5D53-8746-CE99A2593CD7"), WinUiXamlInterfaceIds.IXamlMetadataProvider)
        assertEquals(Guid("2D7EB3FD-ECDB-5084-B7E0-12F9598381EF"), WinUiXamlInterfaceIds.IXamlControlsXamlMetaDataProviderStatics)
    }

    @Test
    fun xaml_metadata_provider_can_activate_when_windows_app_sdk_is_configured() {
        assumeTrue(PlatformRuntime.isWindows)
        if (WindowsAppSdkBootstrap.discoverBootstrapLibrary() == null) {
            return
        }

        WindowsAppSdkBootstrap.initialize().getOrThrow().let { library ->
            try {
                RuntimeScope.initializeMultithreaded().use {
                    WinUiXamlMetadataProvider.tryCreate()?.use { provider ->
                        assertNotNull(provider.getXamlTypeByFullName("Microsoft.UI.Xaml.Controls.Button"))
                    }
                }
            } finally {
                WindowsAppSdkBootstrap.shutdown(library).getOrThrow()
            }
        }
    }
}
