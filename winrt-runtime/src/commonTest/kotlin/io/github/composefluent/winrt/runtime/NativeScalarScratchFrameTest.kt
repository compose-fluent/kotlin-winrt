package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativeScalarScratchFrameTest {
    @Test
    fun reuses_and_clears_the_top_level_scalar_slot() {
        val firstPointer = acquireNativeScalarScratchFrame().use { frame ->
            PlatformAbi.writeInt64(frame.pointer, 0x1122334455667788L)
            PlatformAbi.pointerKey(frame.pointer)
        }

        acquireNativeScalarScratchFrame().use { frame ->
            assertEquals(firstPointer, PlatformAbi.pointerKey(frame.pointer))
            assertEquals(0L, PlatformAbi.readInt64(frame.pointer))
        }
    }

    @Test
    fun nested_scalar_frames_use_distinct_slots() {
        acquireNativeScalarScratchFrame().use { outer ->
            PlatformAbi.writeInt64(outer.pointer, 41L)

            acquireNativeScalarScratchFrame().use { inner ->
                assertNotEquals(
                    PlatformAbi.pointerKey(outer.pointer),
                    PlatformAbi.pointerKey(inner.pointer),
                )
                PlatformAbi.writeInt64(inner.pointer, 99L)
            }

            assertEquals(41L, PlatformAbi.readInt64(outer.pointer))
        }
    }
}
