package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class WinRtTypeSignatureTest {
    @Test
    fun builds_primitive_signatures_using_reference_tokens() {
        assertEquals("i1", WinRtTypeSignature.int8().render())
        assertEquals("u1", WinRtTypeSignature.uint8().render())
        assertEquals("i2", WinRtTypeSignature.int16().render())
        assertEquals("u2", WinRtTypeSignature.uint16().render())
        assertEquals("i4", WinRtTypeSignature.int32().render())
        assertEquals("u4", WinRtTypeSignature.uint32().render())
        assertEquals("i8", WinRtTypeSignature.int64().render())
        assertEquals("u8", WinRtTypeSignature.uint64().render())
        assertEquals("f4", WinRtTypeSignature.float32().render())
        assertEquals("f8", WinRtTypeSignature.float64().render())
        assertEquals("b1", WinRtTypeSignature.boolean().render())
        assertEquals("c2", WinRtTypeSignature.char16().render())
        assertEquals("g16", WinRtTypeSignature.guidValue().render())
    }

    @Test
    fun builds_enum_signature_using_default_underlying_type() {
        assertEquals(
            "enum(Windows.Foundation.AsyncStatus;i4)",
            WinRtTypeSignature.enum("Windows.Foundation.AsyncStatus").render(),
        )
    }

    @Test
    fun builds_struct_signature_using_field_signatures() {
        assertEquals(
            "struct(Windows.Foundation.Point;f8;f8)",
            WinRtTypeSignature.struct(
                "Windows.Foundation.Point",
                WinRtTypeSignature.float64(),
                WinRtTypeSignature.float64(),
            ).render(),
        )
    }

    @Test
    fun builds_delegate_signature_using_interface_guid() {
        assertEquals(
            "delegate({9de1c534-6ae1-11e0-84e1-18a905bcc53f})",
            WinRtTypeSignature.delegate("9de1c534-6ae1-11e0-84e1-18a905bcc53f").render(),
        )
    }
}
