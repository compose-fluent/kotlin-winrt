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
    fun resource_manager_prefers_microsoft_ui_pri_then_controls_pri() {
        val root = Files.createTempDirectory("kotlin-winrt-winui-pri-")
        val controlsPri = root.resolve("Microsoft.UI.Xaml.Controls.pri")
        val microsoftUiPri = root.resolve("Microsoft.UI.pri")
        Files.writeString(controlsPri, "controls")

        assertEquals(controlsPri, WinUiResourceManagerSupport.preferredPriPath(root))

        Files.writeString(microsoftUiPri, "ui")

        assertEquals(microsoftUiPri, WinUiResourceManagerSupport.preferredPriPath(root))
    }

    @Test
    fun resource_manager_returns_null_when_no_pri_exists() {
        val root = Files.createTempDirectory("kotlin-winrt-winui-no-pri-")

        assertNull(WinUiResourceManagerSupport.preferredPriPath(root))
    }
}
