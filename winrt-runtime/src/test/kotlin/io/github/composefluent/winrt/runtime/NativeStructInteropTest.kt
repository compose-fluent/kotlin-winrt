package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeStructInteropTest {
    @Test
    fun native_interop_reads_and_writes_float_and_char16() {
        PlatformAbi.confinedScope().use { scope ->
            val floatSlot = PlatformAbi.allocateBytes(scope, NativeStructScalarKind.FLOAT32.sizeBytes)
            PlatformAbi.writeFloat(floatSlot, 1.5f)
            assertEquals(1.5f, PlatformAbi.readFloat(floatSlot))

            val charSlot = PlatformAbi.allocateBytes(scope, NativeStructScalarKind.CHAR16.sizeBytes)
            PlatformAbi.writeChar16(charSlot, 'K')
            assertEquals('K', PlatformAbi.readChar16(charSlot))
        }
    }

    @Test
    fun native_struct_layout_tracks_offsets_for_geometry_values() {
        assertEquals(8L, Point.Metadata.layout.sizeBytes)
        assertEquals(0L, Point.Metadata.layout.field("x").offsetBytes)
        assertEquals(4L, Point.Metadata.layout.field("y").offsetBytes)

        assertEquals(16L, Rect.Metadata.layout.sizeBytes)
        assertEquals(8L, Rect.Metadata.layout.field("width").offsetBytes)
        assertEquals(12L, Rect.Metadata.layout.field("height").offsetBytes)
    }

    @Test
    fun common_geometry_and_numerics_adapters_round_trip() {
        PlatformAbi.confinedScope().use { scope ->
            val pointMemory = PlatformAbi.allocateBytes(scope, Point.Metadata.layout.sizeBytes)
            val point = Point(1.5f, 2.5f)
            Point.Metadata.copyTo(point, pointMemory)
            assertEquals(point, Point.Metadata.fromAbi(pointMemory))

            val matrixMemory = PlatformAbi.allocateBytes(scope, Matrix3x2.Metadata.layout.sizeBytes)
            val matrix =
                Matrix3x2(
                    m11 = 1f,
                    m12 = 2f,
                    m21 = 3f,
                    m22 = 4f,
                    m31 = 5f,
                    m32 = 6f,
                )
            Matrix3x2.Metadata.copyTo(matrix, matrixMemory)
            assertEquals(matrix, Matrix3x2.Metadata.fromAbi(matrixMemory))

            val planeMemory = PlatformAbi.allocateBytes(scope, Plane.Metadata.layout.sizeBytes)
            val plane = Plane(Vector3(7f, 8f, 9f), 10f)
            Plane.Metadata.copyTo(plane, planeMemory)
            assertEquals(plane, Plane.Metadata.fromAbi(planeMemory))
        }
    }
}
