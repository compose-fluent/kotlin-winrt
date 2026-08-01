package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class NativeStructScratchFrameTest {
    @Test
    fun reuses_the_top_level_struct_storage() {
        val adapter = SmallValueAdapter()
        val firstPointer = acquire(adapter).use { frame ->
            frame.write(SmallValue(41), adapter)
            assertEquals(SmallValue(41), frame.read(adapter))
            PlatformAbi.pointerKey(frame.pointer)
        }

        acquire(adapter).use { frame ->
            assertEquals(firstPointer, PlatformAbi.pointerKey(frame.pointer))
            assertEquals(SmallValue(0), frame.read(adapter))
        }
    }

    @Test
    fun nested_struct_frames_use_distinct_storage() {
        val adapter = SmallValueAdapter()
        acquire(adapter).use { outer ->
            outer.write(SmallValue(41), adapter)

            acquire(adapter).use { inner ->
                assertNotEquals(
                    PlatformAbi.pointerKey(outer.pointer),
                    PlatformAbi.pointerKey(inner.pointer),
                )
                inner.write(SmallValue(99), adapter)
            }

            assertEquals(SmallValue(41), outer.read(adapter))
        }
    }

    @Test
    fun rejects_non_lifo_struct_frame_close() {
        val adapter = SmallValueAdapter()
        val outer = acquire(adapter)
        val inner = acquire(adapter)
        try {
            assertFailsWith<IllegalStateException> { outer.close() }
        } finally {
            inner.close()
            outer.close()
        }
    }

    @Test
    fun grows_reused_storage_for_a_larger_layout() {
        val smallAdapter = SmallValueAdapter()
        acquire(smallAdapter).use { frame ->
            frame.write(SmallValue(7), smallAdapter)
            assertEquals(SmallValue(7), frame.read(smallAdapter))
        }

        val largeAdapter = LargeValueAdapter()
        val expected = LargeValue(11L, 22L, 33L, 44L)
        acquire(largeAdapter).use { frame ->
            frame.write(expected, largeAdapter)
            assertEquals(expected, frame.read(largeAdapter))
            assertEquals(0L, PlatformAbi.pointerKey(frame.pointer) % largeAdapter.layout.alignmentBytes)
        }
    }

    @Test
    fun delegates_read_write_and_dispose_to_the_adapter() {
        val adapter = SmallValueAdapter()
        acquire(adapter).use { frame ->
            frame.write(SmallValue(123), adapter)
            assertEquals(SmallValue(123), frame.read(adapter))
            frame.disposeAbi(adapter)
        }

        assertEquals(1, adapter.disposeCount)
    }

    private fun acquire(adapter: NativeStructAdapter<*>): NativeStructScratchFrame =
        acquireNativeStructScratchFrame(
            sizeBytes = adapter.layout.sizeBytes,
            alignmentBytes = adapter.layout.alignmentBytes,
        )

    private data class SmallValue(val value: Int)

    private class SmallValueAdapter : NativeStructAdapter<SmallValue> {
        override val layout: NativeStructLayout = NativeStructLayout.sequential(
            NativeScalarFieldSpec("value", NativeStructScalarKind.INT32),
        )

        var disposeCount: Int = 0
            private set

        override fun read(source: RawAddress): SmallValue =
            SmallValue(PlatformAbi.readInt32(layout.slice(source, "value")))

        override fun write(value: SmallValue, destination: RawAddress) {
            PlatformAbi.writeInt32(layout.slice(destination, "value"), value.value)
        }

        override fun disposeAbi(source: RawAddress) {
            disposeCount += 1
        }
    }

    private data class LargeValue(
        val first: Long,
        val second: Long,
        val third: Long,
        val fourth: Long,
    )

    private class LargeValueAdapter : NativeStructAdapter<LargeValue> {
        override val layout: NativeStructLayout = NativeStructLayout.sequential(
            NativeScalarFieldSpec("first", NativeStructScalarKind.INT64),
            NativeScalarFieldSpec("second", NativeStructScalarKind.INT64),
            NativeScalarFieldSpec("third", NativeStructScalarKind.INT64),
            NativeScalarFieldSpec("fourth", NativeStructScalarKind.INT64),
        )

        override fun read(source: RawAddress): LargeValue =
            LargeValue(
                first = PlatformAbi.readInt64(layout.slice(source, "first")),
                second = PlatformAbi.readInt64(layout.slice(source, "second")),
                third = PlatformAbi.readInt64(layout.slice(source, "third")),
                fourth = PlatformAbi.readInt64(layout.slice(source, "fourth")),
            )

        override fun write(value: LargeValue, destination: RawAddress) {
            PlatformAbi.writeInt64(layout.slice(destination, "first"), value.first)
            PlatformAbi.writeInt64(layout.slice(destination, "second"), value.second)
            PlatformAbi.writeInt64(layout.slice(destination, "third"), value.third)
            PlatformAbi.writeInt64(layout.slice(destination, "fourth"), value.fourth)
        }
    }
}
