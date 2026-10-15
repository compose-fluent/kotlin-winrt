package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WinRTReferenceReadbackTest {
    @Test
    fun typed_readback_preserves_null_and_borrows_the_reference() {
        // CsWinRT 3 WindowsRuntimeValueTypeMarshaller.UnboxToManaged<T> reads a
        // known IReference<T> without QI or taking ownership of its pointer.
        assertNull(WinRTReferenceProjection.fromAbi<Int>(
            pointer = RawAddress.Null,
            sizeBytes = 4,
            alignmentBytes = 4,
            readValue = { error("A null reference has no value.") },
            disposeValue = { error("A null reference has no ABI output.") },
        ))
        if (!PlatformRuntime.isWindows) return
        WinRTReferenceProjection.createMarshaler(123456, IID.NullableInt)!!.use { marshaler ->
            val count = WinRTInspectableComObject.tryProbeReferenceCount(marshaler.abi)
            repeat(2) {
                assertEquals(123456, WinRTReferenceProjection.fromAbi(
                    pointer = marshaler.abi,
                    sizeBytes = 4,
                    alignmentBytes = 4,
                    readValue = { PlatformAbi.readInt32(it) },
                ))
                assertEquals(count, WinRTInspectableComObject.tryProbeReferenceCount(marshaler.abi))
            }
            WinRTReferenceReference(marshaler.abi, IID.NullableInt, preventReleaseOnDispose = true).use { reference ->
                assertEquals(123456, reference.readValue(4, 4, { PlatformAbi.readInt32(it) }))
                assertEquals(count, WinRTInspectableComObject.tryProbeReferenceCount(marshaler.abi))
            }
        }
        val guid = Guid("17fca6b4-f72a-4ab2-a605-44c45a6c71f6")
        WinRTReferenceProjection.createMarshaler(guid, IID.NullableGuid)!!.use { marshaler ->
            assertEquals(guid, WinRTReferenceProjection.fromAbi(
                pointer = marshaler.abi,
                sizeBytes = Guid.BYTE_SIZE.toLong(),
                alignmentBytes = 4,
                readValue = { PlatformAbi.readGuid(it) },
            ))
        }
    }

    @Test
    fun typed_readback_disposes_non_blittable_output_when_conversion_fails() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT code_writers.h separates the ABI struct and its conversion.
        // Preserve Kotlin's existing output cleanup when typed decoding fails.
        val expected = "typed \u6c49\u5b57\u0000value"
        var disposals = 0
        WinRTReferenceProjection.createMarshaler(expected, IID.NullableString)!!.use { marshaler ->
            val count = WinRTInspectableComObject.tryProbeReferenceCount(marshaler.abi)
            repeat(2) { attempt ->
                fun read(): String? = WinRTReferenceProjection.fromAbi(
                    pointer = marshaler.abi,
                    sizeBytes = NativeAbiLayout.ADDRESS.byteSize,
                    alignmentBytes = NativeAbiLayout.ADDRESS.byteAlignment,
                    readValue = { source ->
                        val value = NativeStringMarshaller.fromAbi(PlatformAbi.readPointer(source))
                        assertEquals(expected, value)
                        if (attempt == 0) error("Conversion failed after reading an owned ABI value.")
                        value
                    },
                    disposeValue = { source ->
                        val value = PlatformAbi.readPointer(source)
                        assertEquals(expected, NativeStringMarshaller.fromAbi(value))
                        NativeStringMarshaller.disposeAbi(value)
                        disposals += 1
                    },
                )
                if (attempt == 0) assertFailsWith<IllegalStateException> { read() }
                else assertEquals(expected, read())
                assertEquals(attempt + 1, disposals)
                assertEquals(count, WinRTInspectableComObject.tryProbeReferenceCount(marshaler.abi))
            }
        }
    }

    @Test
    fun failed_get_value_does_not_decode_or_dispose_an_untransferred_output() {
        if (!PlatformRuntime.isWindows) return
        val definition = WinRTInspectableInterfaceDefinition(
            interfaceId = IID.NullableInt,
            methods = listOf(WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                handler = { KnownHResults.E_FAIL.value },
            )),
        )
        var conversions = 0
        var disposals = 0
        WinRTInspectableComObject(listOf(definition), defaultInterfaceId = IID.NullableInt).use { host ->
            val pointer = host.borrowCachedInterfacePointer(IID.NullableInt)
            val failure = assertFailsWith<WinRTRuntimeException> {
                WinRTReferenceProjection.fromAbi(
                    pointer = pointer,
                    sizeBytes = 4,
                    alignmentBytes = 4,
                    readValue = { conversions += 1; PlatformAbi.readInt32(it) },
                    disposeValue = { disposals += 1 },
                )
            }
            assertEquals(KnownHResults.E_FAIL, failure.hResult)
            assertEquals(0, conversions)
            assertEquals(0, disposals)
            assertEquals(1u, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
        }
    }
}
