package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ParameterizedInterfaceIdTest {
    @Test
    fun renders_parameterized_runtime_class_signature() {
        val argsSignature = WinRtTypeSignature.runtimeClass(
            "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs",
            WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
        )
        val signature = WinRtTypeSignature.parameterizedInterface(
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
        val signature = WinRtTypeSignature.parameterizedInterface(
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
        val left = ParameterizedInterfaceId.createFromSignature(
            WinRtTypeSignature.parameterizedInterface(
                "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                WinRtTypeSignature.object_(),
                WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
            ),
        )
        val right = ParameterizedInterfaceId.createFromSignature(
            WinRtTypeSignature.parameterizedInterface(
                "9de1c534-6ae1-11e0-84e1-18a905bcc53f",
                WinRtTypeSignature.object_(),
                WinRtTypeSignature.guid("469e6d36-2e11-5b06-9e0a-c5eef0cf8f12"),
            ),
        )

        assertNotEquals(left, right)
    }
}
