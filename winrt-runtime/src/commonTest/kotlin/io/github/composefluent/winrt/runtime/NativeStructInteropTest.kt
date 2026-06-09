package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeStructInteropTest {
    @Test
    fun sequential_layout_aligns_fields_and_struct_size_like_native_abi() {
        val layout = NativeStructLayout.sequential(
            NativeScalarFieldSpec("name", NativeStructScalarKind.ADDRESS),
            NativeScalarFieldSpec("kind", NativeStructScalarKind.INT32),
        )

        assertEquals(0L, layout.field("name").offsetBytes)
        assertEquals(8L, layout.field("kind").offsetBytes)
        assertEquals(16L, layout.sizeBytes)
        assertEquals(8L, layout.alignmentBytes)
        assertEquals(NativeAbiLayout(byteSize = 16, byteAlignment = 8), layout.abiLayout)
        assertEquals(layout.abiLayout, NativeAbiLayout.TYPE_NAME)
    }

    @Test
    fun sequential_layout_inserts_member_padding() {
        val layout = NativeStructLayout.sequential(
            NativeScalarFieldSpec("flag", NativeStructScalarKind.INT8),
            NativeScalarFieldSpec("value", NativeStructScalarKind.INT64),
        )

        assertEquals(0L, layout.field("flag").offsetBytes)
        assertEquals(8L, layout.field("value").offsetBytes)
        assertEquals(16L, layout.sizeBytes)
        assertEquals(8L, layout.alignmentBytes)
    }
}
