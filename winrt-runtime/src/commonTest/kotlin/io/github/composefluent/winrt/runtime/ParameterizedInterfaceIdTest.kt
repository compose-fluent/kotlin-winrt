package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParameterizedInterfaceIdTest {
    @Test
    fun creates_winrt_pinterface_guid_from_known_cswinrt_signature() {
        assertEquals(
            Guid("98B9ACC1-4B56-532E-AC73-03D5291CCA90"),
            ParameterizedInterfaceId.createFromSignature(
                "pinterface({913337e9-11a1-4345-a3a2-4e7f956e222d};string)",
            ),
        )
    }

    @Test
    fun creates_winrt_runtime_class_signature_guid_from_known_cswinrt_signature() {
        assertEquals(
            Guid("B9D890EA-0397-53EA-A1AC-96653135C3D4"),
            ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.runtimeClass(
                    "Microsoft.UI.Xaml.Controls.UIElementCollection",
                    WinRtTypeSignature.guid("23050cb1-db88-54ed-9083-5ecfb12512fd"),
                ),
            ),
        )
    }

    @Test
    fun renders_parameterized_runtime_class_signature() {
        val argsSignature =
            WinRtTypeSignature.runtimeClass(
                "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs",
                WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
            )
        val signature =
            WinRtTypeSignature.parameterizedInterface(
                "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                WinRtTypeSignature.object_(),
                argsSignature,
            )

        assertEquals(
            "pinterface({9de1c534-6ae1-11e0-84e1-18a905bcc53f};cinterface(IInspectable);rc(Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs;{c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a}))",
            signature.render(),
        )
    }

    @Test
    fun parameterized_interface_id_is_deterministic() {
        val signature =
            WinRtTypeSignature.parameterizedInterface(
                "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                WinRtTypeSignature.object_(),
                WinRtTypeSignature.runtimeClass(
                    "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs",
                    WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
                ),
            )

        val first = ParameterizedInterfaceId.createFromSignature(signature)
        val second = ParameterizedInterfaceId.createFromSignature(signature.render())

        assertEquals(first, second)
    }

    @Test
    fun parameterized_interface_id_changes_when_signature_changes() {
        val left =
            ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                    WinRtTypeSignature.object_(),
                    WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
                ),
            )
        val right =
            ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                    WinRtTypeSignature.object_(),
                    WinRtTypeSignature.guid("469e6d36-2e11-5b06-9e0a-c5eef0cf8f12"),
                ),
            )

        assertNotEquals(left, right)
    }

    @Test
    fun convenience_parameterized_interface_helper_matches_signature_path() {
        val fromConvenience =
            ParameterizedInterfaceId.createFromParameterizedInterface(
                WinRtCollectionInterfaceIds.iVector,
                WinRtTypeSignature.string(),
            )
        val fromSignature =
            ParameterizedInterfaceId.createFromSignature(
                "pinterface({913337e9-11a1-4345-a3a2-4e7f956e222d};string)",
            )

        assertEquals(fromSignature, fromConvenience)
    }

    @Test
    fun creates_i_reference_string_guid_from_known_winrt_signature() {
        assertEquals(
            IID.NullableString,
            ParameterizedInterfaceId.createFromSignature(
                "pinterface({61c17706-2d65-11e0-9ae8-d48564015472};string)",
            ),
        )
    }

    @Test
    fun creates_i_reference_delegate_guid_from_known_winui_add_handler_probe() {
        assertEquals(
            Guid("DEA1E123-12EA-5CB3-B923-ABE74E426D9E"),
            ParameterizedInterfaceId.createFromSignature(
                "pinterface({61c17706-2d65-11e0-9ae8-d48564015472};delegate({b60074f3-125b-534e-8f9c-9769bd3f0f64}))",
            ),
        )
    }
}
