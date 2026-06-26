package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class WinRTTypeSignatureTest {
    @Test
    fun builds_primitive_signatures_using_reference_tokens() {
        assertEquals("i1", WinRTTypeSignature.int8().render())
        assertEquals("u1", WinRTTypeSignature.uint8().render())
        assertEquals("i2", WinRTTypeSignature.int16().render())
        assertEquals("u2", WinRTTypeSignature.uint16().render())
        assertEquals("i4", WinRTTypeSignature.int32().render())
        assertEquals("u4", WinRTTypeSignature.uint32().render())
        assertEquals("i8", WinRTTypeSignature.int64().render())
        assertEquals("u8", WinRTTypeSignature.uint64().render())
        assertEquals("f4", WinRTTypeSignature.float32().render())
        assertEquals("f8", WinRTTypeSignature.float64().render())
        assertEquals("b1", WinRTTypeSignature.boolean().render())
        assertEquals("c2", WinRTTypeSignature.char16().render())
        assertEquals("g16", WinRTTypeSignature.guidValue().render())
    }

    @Test
    fun builds_enum_signature_using_default_underlying_type() {
        assertEquals(
            "enum(Windows.Foundation.AsyncStatus;i4)",
            WinRTTypeSignature.enum("Windows.Foundation.AsyncStatus").render(),
        )
    }

    @Test
    fun builds_struct_signature_using_field_signatures() {
        assertEquals(
            "struct(Windows.Foundation.Point;f8;f8)",
            WinRTTypeSignature.struct(
                "Windows.Foundation.Point",
                WinRTTypeSignature.float64(),
                WinRTTypeSignature.float64(),
            ).render(),
        )
    }

    @Test
    fun builds_delegate_signature_using_interface_guid() {
        assertEquals(
            "delegate({9de1c534-6ae1-11e0-84e1-18a905bcc53f})",
            WinRTTypeSignature.delegate("9de1c534-6ae1-11e0-84e1-18a905bcc53f").render(),
        )
    }
}
