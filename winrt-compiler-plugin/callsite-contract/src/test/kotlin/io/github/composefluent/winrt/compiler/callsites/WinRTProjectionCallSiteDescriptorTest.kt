package io.github.composefluent.winrt.compiler.callsites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WinRTProjectionCallSiteDescriptorTest {
    @Test
    fun canonical_descriptor_round_trips_every_codegen_semantic() {
        val descriptor = WinRTProjectionCallSiteDescriptor(
            receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
            slotPolicy = WinRTProjectionCallSiteSlotPolicy.CONSTANT,
            constantSlot = 17,
            hResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
            resultStrategy = WinRTProjectionCallSiteResultStrategy.STRUCT_OUT,
            result = WinRTProjectionCallSiteValue(
                kind = WinRTProjectionCallSiteValueKind.STRUCT,
                ownership = WinRTProjectionCallSiteOwnership.OWNED,
                sizeBytes = 16,
                alignmentBytes = 8,
            ),
            parameters = listOf(
                WinRTProjectionCallSiteParameter(
                    role = WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT,
                    value = WinRTProjectionCallSiteValue(
                        kind = WinRTProjectionCallSiteValueKind.STRING,
                        ownership = WinRTProjectionCallSiteOwnership.BORROWED,
                    ),
                ),
                WinRTProjectionCallSiteParameter(
                    role = WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER,
                    value = WinRTProjectionCallSiteValue(
                        kind = WinRTProjectionCallSiteValueKind.STRUCT,
                        sizeBytes = 16,
                        alignmentBytes = 8,
                    ),
                ),
            ),
        )

        assertEquals(descriptor, WinRTProjectionCallSiteDescriptor.parse(descriptor.encode()))
    }

    @Test
    fun parameterized_slot_and_empty_parameters_have_one_canonical_encoding() {
        val descriptor = WinRTProjectionCallSiteDescriptor(
            receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
            resultStrategy = WinRTProjectionCallSiteResultStrategy.SCALAR_OUT,
            result = WinRTProjectionCallSiteValue(WinRTProjectionCallSiteValueKind.INT32),
        )

        assertEquals(
            "v1|COM_OBJECT_REFERENCE|PARAMETER|-1|CHECK|SCALAR_OUT|INT32~NONE~0~0~0|-",
            descriptor.encode(),
        )
        assertEquals(descriptor, WinRTProjectionCallSiteDescriptor.parse(descriptor.encode()))
    }

    @Test
    fun descriptor_rejects_semantically_incomplete_shapes() {
        assertFailsWith<IllegalArgumentException> {
            WinRTProjectionCallSiteValue(WinRTProjectionCallSiteValueKind.STRUCT)
        }
        assertFailsWith<IllegalArgumentException> {
            WinRTProjectionCallSiteDescriptor(
                receiver = WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE,
                resultStrategy = WinRTProjectionCallSiteResultStrategy.STRING_OUT,
                result = WinRTProjectionCallSiteValue(WinRTProjectionCallSiteValueKind.STRING),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WinRTProjectionCallSiteDescriptor.parse(
                "v2|COM_OBJECT_REFERENCE|PARAMETER|-1|CHECK|UNIT|VOID~NONE~0~0~0|-",
            )
        }
    }
}
