package io.github.composefluent.winrt.runtime

import java.io.PrintWriter
import java.io.StringWriter
import java.util.spi.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object Task2CallSiteLoweringFixture {
    @WinRTProjectionCallSite
    fun directComInputAndTwoOut(
        reference: ComObjectReference,
        slot: Int,
        input: InspectableReference,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.OUT)
        first: WinRTOut<String>,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.OUT)
        second: WinRTOut<String>,
    ): Unit = TODO("Task 2 compiler-plugin fixture")

    @WinRTProjectionCallSite
    fun mixedCarriers(
        reference: ComObjectReference,
        slot: Int,
        boolean: Boolean,
        float: Float,
        double: Double,
    ): Unit = TODO("Task 2 compiler-plugin fixture")
}

@JvmInline
private value class IntEnumConstantFixture(val abiValue: Int) {
    companion object {
        @get:WinRTEnumConstant(valueBits = 5L)
        val Object: IntEnumConstantFixture = IntEnumConstantFixture(5)
    }
}

@JvmInline
private value class UIntEnumConstantFixture(val abiValue: UInt) {
    companion object {
        @get:WinRTEnumConstant(valueBits = 0xffff_ffffL)
        val All: UIntEnumConstantFixture = UIntEnumConstantFixture(UInt.MAX_VALUE)
    }
}

private object EnumConstantReadFixture {
    fun intValue(): Int = IntEnumConstantFixture.Object.abiValue

    fun uintValue(): Int = UIntEnumConstantFixture.All.abiValue.toInt()
}

@WinRTProjectionAbiType(
    name = "kotlin.Array<kotlin.Int>",
    kind = WinRTProjectionAbiTypeKind.ARRAY,
)
private class Task3IntArrayAbiMetadata

private object Task3CallSiteLoweringFixture {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.FROM_ABI, type = "kotlin.Array<kotlin.Int>")
    fun decodeIntArray(lengthOut: RawAddress, dataOut: RawAddress): Array<Int> =
        arrayOf(PlatformAbi.pointerKey(lengthOut).toInt(), PlatformAbi.pointerKey(dataOut).toInt())

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.DISPOSE_ABI, type = "kotlin.Array<kotlin.Int>")
    fun disposeIntArray(lengthOut: RawAddress, dataOut: RawAddress) {
        PlatformAbi.pointerKey(lengthOut)
        PlatformAbi.pointerKey(dataOut)
    }

    @WinRTProjectionCallSite(result = WinRTCallSiteResultKind.CALLER_OWNED)
    fun composableOutputs(
        reference: ComObjectReference,
        slot: Int,
        baseInterface: RawAddress,
    ): WinRTComposableFactoryResult = TODO("Task 3 compiler-plugin fixture")

    @WinRTProjectionCallSite
    fun ordinaryReturn(reference: ComObjectReference, slot: Int): Int =
        TODO("Task 3 ordinary return fixture")

    @WinRTProjectionCallSite
    fun receiveArray(reference: ComObjectReference, slot: Int): Array<Int> =
        TODO("Task 3 receive array fixture")
}

@WinRTProjectionAbiType(
    name = "Windows.Foundation.IReference<kotlin.Int>",
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
)
private class MappedReferenceIntAbiMetadata

@WinRTProjectionAbiType(
    name = "Sample.Foundation.IAlternateIntReference",
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
)
private class AlternateIntReferenceAbiMetadata

private object MappedAbiIdentityCallSiteFixture {
    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.FROM_ABI,
        type = "Windows.Foundation.IReference<kotlin.Int>",
    )
    fun decodeReferenceInt(abi: RawAddress): Int? = PlatformAbi.pointerKey(abi).toInt()

    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.FROM_ABI,
        type = "Sample.Foundation.IAlternateIntReference",
    )
    fun decodeAlternateInt(abi: RawAddress): Int? = PlatformAbi.pointerKey(abi).toInt()

    @WinRTProjectionCallSite(returnAbiType = "Windows.Foundation.IReference<kotlin.Int>")
    fun referenceInt(reference: ComObjectReference, slot: Int): Int? =
        TODO("mapped ABI identity fixture")

    @WinRTProjectionCallSite(returnAbiType = "Sample.Foundation.IAlternateIntReference")
    fun alternateInt(reference: ComObjectReference, slot: Int): Int? =
        TODO("alternate ABI identity fixture")
}

private interface DirectUnknownProjection {
    companion object Metadata {
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle(
            "io.github.composefluent.winrt.runtime.DirectUnknownProjection",
            Guid("11111111-2222-3333-4444-555555555555"),
        )

        fun wrap(reference: IUnknownReference): DirectUnknownProjection =
            DirectUnknownProjectionImpl(reference)
    }
}

private class DirectUnknownProjectionImpl(
    val reference: IUnknownReference,
) : DirectUnknownProjection,
    IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = reference
}

private interface SpecializedUnknownProjection {
    companion object Metadata {
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle(
            "io.github.composefluent.winrt.runtime.SpecializedUnknownProjection",
            Guid("22222222-3333-4444-5555-666666666666"),
        )

        fun wrap(reference: IUnknownReference): SpecializedUnknownProjection =
            error("Specialized input fixture does not decode values: $reference")
    }
}

@WinRTProjectionAbiType(
    name = "io.github.composefluent.winrt.runtime.SpecializedUnknownProjection",
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
    reference = WinRTProjectionAbiReferenceKind.UNKNOWN,
)
private object SpecializedProjectionInputCallSiteFixture {
    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.TO_ABI,
        type = "io.github.composefluent.winrt.runtime.SpecializedUnknownProjection",
    )
    fun toAbi(value: SpecializedUnknownProjection): RawAddress =
        error("Specialized input fixture does not execute values: $value")

    @WinRTProjectionCallSite
    fun consume(
        reference: ComObjectReference,
        slot: Int,
        value: SpecializedUnknownProjection,
    ): Unit = TODO("specialized projection input fixture")
}

private class DirectInspectableProjection private constructor(
    val reference: InspectableReference,
) : IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = reference

    companion object Metadata {
        fun wrap(reference: IUnknownReference): DirectInspectableProjection =
            error("The IUnknown overload must not be selected: $reference")

        fun wrap(reference: InspectableReference): DirectInspectableProjection =
            DirectInspectableProjection(reference)
    }
}

private object DirectProjectionOutputCallSiteFixture {
    @WinRTProjectionCallSite
    fun consumeInterface(
        reference: ComObjectReference,
        slot: Int,
        value: DirectUnknownProjection,
    ): Unit = TODO("direct interface projection input fixture")

    @WinRTProjectionCallSite
    fun consumeNullableRuntimeClass(
        reference: ComObjectReference,
        slot: Int,
        value: DirectInspectableProjection?,
    ): Unit = TODO("direct nullable runtime-class projection input fixture")

    @WinRTProjectionCallSite
    fun nonNullInterface(reference: ComObjectReference, slot: Int): DirectUnknownProjection =
        TODO("direct non-null interface projection fixture")

    @WinRTProjectionCallSite
    fun nullableInterface(reference: ComObjectReference, slot: Int): DirectUnknownProjection? =
        TODO("direct nullable interface projection fixture")

    @WinRTProjectionCallSite
    fun runtimeClass(reference: ComObjectReference, slot: Int): DirectInspectableProjection =
        TODO("direct runtime-class projection fixture")
}

private data class DirectStructProjection(
    val value: Int,
) {
    @WinRTProjectionAbiType(
        name = "io.github.composefluent.winrt.runtime.DirectStructProjection",
        kind = WinRTProjectionAbiTypeKind.STRUCT,
        carrier = WinRTProjectionAbiCarrier.INT32,
        size = 4,
        alignment = 4,
    )
    companion object Metadata {
        fun copyTo(value: DirectStructProjection, destination: RawAddress) {
            PlatformAbi.writeInt32(destination, value.value)
        }

        fun fromAbi(source: RawAddress): DirectStructProjection =
            DirectStructProjection(PlatformAbi.readInt32(source))

        fun disposeAbi(source: RawAddress) {
            PlatformAbi.pointerKey(source)
        }
    }
}

private object DirectStructCallSiteFixture {
    @WinRTProjectionCallSite
    fun consume(
        reference: ComObjectReference,
        slot: Int,
        value: DirectStructProjection,
    ): Unit = TODO("direct struct input fixture")

    @WinRTProjectionCallSite
    fun produce(reference: ComObjectReference, slot: Int): DirectStructProjection =
        TODO("direct struct output fixture")
}

private object DirectPrimitiveArrayCallSiteFixture {
    @WinRTProjectionCallSite
    fun consumeLongs(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.PASS_ARRAY)
        values: Array<Long>,
    ): Unit = TODO("direct primitive array input fixture")

    @WinRTProjectionCallSite
    fun produceLongs(reference: ComObjectReference, slot: Int): Array<Long> =
        TODO("direct primitive array output fixture")
}

@WinRTProjectionAbiType(
    name = "kotlin.Array<kotlin.UByte>",
    kind = WinRTProjectionAbiTypeKind.ARRAY,
)
private object SharedUsageByteArrayAbiMetadata

private object SharedUsageByteArrayCallSiteFixture {
    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.CREATE_MARSHALER,
        type = "kotlin.Array<kotlin.UByte>",
    )
    fun createByteArray(value: Array<UByte>): WinRTAbiArray? =
        Marshaler.uint8().createMarshalerArray(value)

    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.COPY_FROM_ABI,
        type = "kotlin.Array<kotlin.UByte>",
    )
    fun copyByteArray(abi: WinRTAbiArray?, value: Array<UByte>) {
        Marshaler.uint8().fromAbiArray(value.size, abi?.data ?: PlatformAbi.nullPointer)
            ?.forEachIndexed { index, element -> (value as Array<Any?>)[index] = element }
    }

    @WinRTProjectionCallSite
    fun fillBytes(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.FILL_ARRAY)
        values: Array<UByte>,
    ): Unit = TODO("shared-usage byte-array fill fixture")

    @WinRTProjectionCallSite
    fun produceBytes(reference: ComObjectReference, slot: Int): Array<UByte> =
        TODO("shared-usage byte-array output fixture")
}

private object DirectStructArrayCallSiteFixture {
    @WinRTProjectionCallSite
    fun consumeStructs(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.PASS_ARRAY)
        values: Array<DirectStructProjection>,
    ): Unit = TODO("direct struct array input fixture")

    @WinRTProjectionCallSite
    fun produceStructs(reference: ComObjectReference, slot: Int): Array<DirectStructProjection> =
        TODO("direct struct array output fixture")
}

class WinRTCallSiteLoweringContractTest {
    @Test
    fun annotated_enum_constant_getters_fold_from_exact_value_class_carriers() {
        val bytecode = javap(EnumConstantReadFixture::class.java.name)
        val intValue = bytecode.methodBytecode("intValue")
        val uintValue = bytecode.methodBytecode("uintValue")

        assertTrue(intValue.contains("iconst_5"), intValue)
        assertFalse(intValue.contains("getObject"), intValue)
        assertTrue(uintValue.contains("iconst_m1"), uintValue)
        assertFalse(uintValue.contains("getAll"), uintValue)
    }

    @Test
    fun unit_calls_without_owned_outputs_do_not_expand_owned_output_transactions() {
        val bytecode = javap(Task2CallSiteLoweringFixture::class.java.name)
            .methodBytecode("mixedCarriers")

        assertFalse(bytecode.contains("Task 2 compiler-plugin fixture"), bytecode)
        assertFalse(bytecode.contains("addSuppressed"), bytecode)
        assertFalse(bytecode.contains("java/lang/Throwable"), bytecode)
        assertTrue(bytecode.contains("reachabilityFence"), bytecode)
    }

    @Test
    fun plain_primitive_arrays_lower_to_contiguous_typed_loops_without_array_codecs() {
        val bytecode = javap(DirectPrimitiveArrayCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("direct primitive array input fixture"))
        assertFalse(bytecode.contains("direct primitive array output fixture"))

        val consume = bytecode.methodBytecode("consumeLongs")
        assertTrue(consume.contains("WinRTAbiArray\$Companion.allocateInput"), consume)
        assertTrue(consume.contains("writeInt64"), consume)
        assertFalse(consume.contains("codec_create_"), consume)
        assertFalse(consume.contains("referenceValueAdapter"), consume)

        val produce = bytecode.methodBytecode("produceLongs")
        assertTrue(produce.contains("readInt64"), produce)
        assertTrue(produce.contains("coTaskMemFreeRaw"), produce)
        assertFalse(produce.contains("codec_fromAbiArray_"), produce)
        assertFalse(produce.contains("codec_disposeAbiArray_"), produce)
    }

    @Test
    fun array_codecs_are_selected_per_usage_without_shadowing_direct_output_lowering() {
        val bytecode = javap(SharedUsageByteArrayCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("shared-usage byte-array fill fixture"))
        assertFalse(bytecode.contains("shared-usage byte-array output fixture"))

        val fill = bytecode.methodBytecode("fillBytes")
        assertTrue(fill.contains("createByteArray"), fill)
        assertTrue(fill.contains("copyByteArray"), fill)

        val produce = bytecode.methodBytecode("produceBytes")
        assertTrue(produce.contains("readInt8"), produce)
        assertTrue(produce.contains("coTaskMemFreeRaw"), produce)
        assertFalse(produce.contains("createByteArray"), produce)
        assertFalse(produce.contains("copyByteArray"), produce)
    }

    @Test
    fun plain_struct_arrays_use_declared_inline_layout_and_typed_metadata_codecs() {
        val bytecode = javap(DirectStructArrayCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("direct struct array input fixture"))
        assertFalse(bytecode.contains("direct struct array output fixture"))

        val consume = bytecode.methodBytecode("consumeStructs")
        assertTrue(consume.contains("WinRTAbiArray\$Companion.allocateInput"), consume)
        assertTrue(consume.contains("iconst_4"), consume)
        assertTrue(consume.contains("DirectStructProjection\$Metadata"), consume)
        assertTrue(consume.contains("copyTo"), consume)
        assertTrue(consume.contains("disposeAbi"), consume)
        assertFalse(consume.contains("codec_create_"), consume)
        assertFalse(consume.contains("referenceValueAdapter"), consume)

        val produce = bytecode.methodBytecode("produceStructs")
        assertTrue(produce.contains("DirectStructProjection\$Metadata"), produce)
        assertTrue(produce.contains("fromAbi"), produce)
        assertTrue(produce.contains("disposeAbi"), produce)
        assertTrue(produce.contains("coTaskMemFreeRaw"), produce)
        assertFalse(produce.contains("codec_fromAbiArray_"), produce)
        assertFalse(produce.contains("codec_disposeAbiArray_"), produce)
    }

    @Test
    fun plain_structs_resolve_typed_metadata_codecs_and_layout_from_ir() {
        val bytecode = javap(DirectStructCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("direct struct input fixture"))
        assertFalse(bytecode.contains("direct struct output fixture"))

        val consume = bytecode.methodBytecode("consume")
        assertTrue(consume.contains("DirectStructProjection\$Metadata"), consume)
        assertTrue(consume.contains("copyTo"), consume)
        assertFalse(consume.contains("WinRTProjectionAbiCodec"), consume)

        val produce = bytecode.methodBytecode("produce")
        assertTrue(produce.contains("DirectStructProjection\$Metadata"), produce)
        assertTrue(produce.contains("fromAbi"), produce)
        assertTrue(produce.contains("DirectStructProjection"), produce)
        assertFalse(produce.contains("WINRT_E_NULL_ABI_RETURN"), produce)
    }

    @Test
    fun plain_projection_outputs_resolve_typed_metadata_wrap_and_nullability_from_ir() {
        val bytecode = javap(DirectProjectionOutputCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("direct non-null interface projection fixture"))
        assertFalse(bytecode.contains("direct nullable interface projection fixture"))
        assertFalse(bytecode.contains("direct runtime-class projection fixture"))

        val nonNullInterface = bytecode.methodBytecode("nonNullInterface")
        assertTrue(nonNullInterface.contains("IUnknownReference"), nonNullInterface)
        assertTrue(nonNullInterface.contains("Metadata.wrap"), nonNullInterface)
        assertTrue(nonNullInterface.contains("WINRT_E_NULL_ABI_RETURN"), nonNullInterface)
        assertFalse(nonNullInterface.contains("InspectableReference"), nonNullInterface)

        val nullableInterface = bytecode.methodBytecode("nullableInterface")
        assertTrue(nullableInterface.contains("IUnknownReference"), nullableInterface)
        assertTrue(nullableInterface.contains("Metadata.wrap"), nullableInterface)
        assertTrue(nullableInterface.contains("aconst_null"), nullableInterface)
        assertFalse(nullableInterface.contains("WINRT_E_NULL_ABI_RETURN"), nullableInterface)

        val runtimeClass = bytecode.methodBytecode("runtimeClass")
        assertTrue(runtimeClass.contains("InspectableReference"), runtimeClass)
        assertTrue(runtimeClass.contains("Metadata.wrap"), runtimeClass)
        assertFalse(runtimeClass.contains("IUnknownReference"), runtimeClass)
    }

    @Test
    fun plain_projection_inputs_read_iwinrtobject_directly_without_module_codecs() {
        val bytecode = javap(DirectProjectionOutputCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("direct interface projection input fixture"))
        assertFalse(bytecode.contains("direct nullable runtime-class projection input fixture"))

        val nonNull = bytecode.methodBytecode("consumeInterface")
        assertTrue(nonNull.contains("Metadata.getTYPE_HANDLE"), nonNull)
        assertTrue(nonNull.contains("IWinRTObject.getObjectReferenceForType"), nonNull)
        assertTrue(nonNull.contains("ComObjectReference.getComPtr"), nonNull)
        assertTrue(nonNull.contains("ComPtr.getSupport"), nonNull)
        assertTrue(nonNull.contains("RawComObjectReferenceSupport.isDisposed"), nonNull)
        assertTrue(nonNull.contains("ComPtr.\"getRaw-"), nonNull)
        assertFalse(nonNull.contains("ComPtr.\"getPointer-"), nonNull)
        assertFalse(nonNull.contains("IWinRTObject.getNativeObject"), nonNull)
        assertFalse(nonNull.contains("codec_toAbi_"), nonNull)

        val nullable = bytecode.methodBytecode("consumeNullableRuntimeClass")
        assertTrue(nullable.contains("IWinRTObject.getNativeObject"), nullable)
        assertTrue(nullable.contains("ComObjectReference.getComPtr"), nullable)
        assertTrue(nullable.contains("ComPtr.getSupport"), nullable)
        assertTrue(nullable.contains("RawComObjectReferenceSupport.isDisposed"), nullable)
        assertTrue(nullable.contains("ComPtr.\"getRaw-"), nullable)
        assertFalse(nullable.contains("ComPtr.\"getPointer-"), nullable)
        assertTrue(nullable.contains("ifnonnull") || nullable.contains("ifnull"), nullable)
        assertFalse(nullable.contains("IWinRTObject.getObjectReferenceForType"), nullable)
        assertFalse(nullable.contains("codec_toAbi_"), nullable)
    }

    @Test
    fun specialized_projection_input_codec_takes_precedence_over_direct_metadata_shape() {
        val bytecode = javap(SpecializedProjectionInputCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("specialized projection input fixture"))

        val consume = bytecode.methodBytecode("consume")
        assertTrue(consume.contains("toAbi"), consume)
        assertFalse(consume.contains("getObjectReferenceForType"), consume)
        assertFalse(consume.contains("getNativeObject"), consume)
    }

    @Test
    fun mapped_abi_identity_overrides_the_projected_primitive_shape() {
        val bytecode = javap(MappedAbiIdentityCallSiteFixture::class.java.name)
        assertFalse(bytecode.contains("mapped ABI identity fixture"))
        assertFalse(bytecode.contains("alternate ABI identity fixture"))

        val reference = bytecode.methodBytecode("referenceInt")
        val alternate = bytecode.methodBytecode("alternateInt")
        assertTrue(reference.contains("decodeReferenceInt"), reference)
        assertFalse(reference.contains("decodeAlternateInt"), reference)
        assertTrue(alternate.contains("decodeAlternateInt"), alternate)
        assertFalse(alternate.contains("decodeReferenceInt"), alternate)
        assertFalse(reference.contains("readInt32"), reference)
        assertFalse(alternate.contains("readInt32"), alternate)
    }

    @Test
    fun caller_owned_outputs_are_allocated_and_constructed_from_the_result_type() {
        val bytecode = javap(Task3CallSiteLoweringFixture::class.java.name)
        assertFalse(bytecode.contains("Task 3 compiler-plugin fixture"))
        val method = bytecode.methodBytecode("composableOutputs")
        assertTrue(method.countOccurrences("acquireNativeScalarScratchFrame") >= 2)
        assertTrue(method.contains("WinRTComposableFactoryResult"))
        assertTrue(method.contains("<init>"))
        assertFalse(method.contains("assemble"))
        assertTrue(method.countOccurrences("iconst_0") >= 2)
        assertTrue(method.countOccurrences("iconst_1") >= 4)
        method.assertOrderedAfter(
            "access\$getKotlinWinRTExactHResultHandle_address_address_address",
            "aload         16",
            "lload         13",
            "lload_3",
            "aload         5",
            "NativeScalarScratchFrame.\"getPointer-f81YIUw\":()J",
            "aload         6",
            "NativeScalarScratchFrame.\"getPointer-f81YIUw\":()J",
            "Method java/lang/invoke/MethodHandle.invokeExact:(Ljava/lang/foreign/MemorySegment;JJJJ)I",
        )
        assertEquals(1, method.countOccurrences("MemorySegment.ofAddress:(J)"))
        val cleanupLocals = method.readPointerFrameLocals().takeLast(4).distinct()
        assertEquals(2, cleanupLocals.size)
        assertTrue(cleanupLocals[0] > cleanupLocals[1], cleanupLocals.toString())
        assertTrue(
            Regex("""composableOutputs-[^(]+\([^)]*ComObjectReference, int, long\);""")
                .containsMatchIn(bytecode),
        )
    }

    @Test
    fun ordinary_return_and_receive_array_use_closed_ordered_output_paths() {
        val bytecode = javap(Task3CallSiteLoweringFixture::class.java.name)
        assertFalse(bytecode.contains("Task 3 ordinary return fixture"))
        assertFalse(bytecode.contains("Task 3 receive array fixture"))

        val ordinaryReturn = bytecode.methodBytecode("ordinaryReturn")
        assertTrue(ordinaryReturn.contains("acquireNativeScalarScratchFrame"))
        assertTrue(ordinaryReturn.contains("readInt32"))

        val receiveArray = bytecode.methodBytecode("receiveArray")
        assertTrue(receiveArray.countOccurrences("acquireNativeScalarScratchFrame") >= 2)
        assertTrue(receiveArray.contains("decodeIntArray"))
        assertTrue(receiveArray.countOccurrences("disposeIntArray") >= 2)
        assertTrue(receiveArray.indexOf("decodeIntArray") < receiveArray.indexOf("disposeIntArray"))
        receiveArray.assertOrderedAfter(
            "access\$getKotlinWinRTExactHResultHandle_address_address",
            "aload         12",
            "lload         9",
            "aload_3",
            "NativeScalarScratchFrame.\"getPointer-f81YIUw\":()J",
            "aload         4",
            "NativeScalarScratchFrame.\"getPointer-f81YIUw\":()J",
            "Method java/lang/invoke/MethodHandle.invokeExact:(Ljava/lang/foreign/MemorySegment;JJJ)I",
        )
        assertEquals(1, receiveArray.countOccurrences("MemorySegment.ofAddress:(J)"))
        receiveArray.assertOrderedAfter("throwHResultFailure", "aload_3", "aload         4", "decodeIntArray")
        receiveArray.assertOrderedAfter("astore        9", "aload_3", "aload         4", "disposeIntArray")
    }

    @Test
    fun behavioral_output_plan_covers_all_failure_and_transfer_boundaries() {
        listOf(
            FailurePoint.HRESULT,
            FailurePoint.FIRST_DECODER,
            FailurePoint.SECOND_DECODER,
            FailurePoint.RESULT_CONSTRUCTION,
            FailurePoint.POST_CALL,
            FailurePoint.OUT_SETTER,
        ).forEach { failurePoint ->
            val fixture = BehavioralOutputPlanFixture()
            val failure = assertFailsWith<BehavioralFixtureFailure> { fixture.execute(failurePoint) }
            assertEquals(failurePoint.name, failure.message)
            assertEquals(listOf("dispose:second", "dispose:first"), fixture.events.filter { it.startsWith("dispose:") }, failurePoint.name)
            assertEquals(1, fixture.disposeCount("first"), failurePoint.name)
            assertEquals(1, fixture.disposeCount("second"), failurePoint.name)
        }

        BehavioralOutputPlanFixture().also { fixture ->
            assertEquals("first+second", fixture.execute(null))
            assertEquals(listOf("transfer:first", "transfer:second"), fixture.events.filter { it.startsWith("transfer:") })
            assertEquals(1, fixture.disposeCount("first"))
            assertEquals(1, fixture.disposeCount("second"))
            assertTrue(fixture.events.indexOf("setter:ordinary") < fixture.events.indexOf("decode:first"))
            assertTrue(fixture.events.indexOf("decode:second") < fixture.events.indexOf("constructor"))
            assertTrue(fixture.events.indexOf("constructor") < fixture.events.indexOf("complete:claim:first"))
        }
    }

    @Test
    fun throwing_disposer_is_claimed_once_and_cleanup_failures_are_preserved() {
        val fixture = BehavioralOutputPlanFixture(throwingDisposers = setOf("second", "first"))
        val primary = assertFailsWith<BehavioralFixtureFailure> { fixture.execute(FailurePoint.RESULT_CONSTRUCTION) }

        assertEquals("RESULT_CONSTRUCTION", primary.message)
        assertEquals(listOf("dispose:second", "dispose:first"), fixture.events.filter { it.startsWith("dispose:") })
        assertEquals(1, fixture.disposeCount("second"))
        assertEquals(1, fixture.disposeCount("first"))
        assertEquals(1, primary.suppressed.size)
        assertEquals("dispose:second", primary.suppressed.single().message)
        assertEquals(listOf("dispose:first"), primary.suppressed.single().suppressed.map { it.message })
    }

    @Test
    fun successful_struct_completion_claims_before_throwing_disposal_without_finally_retry() {
        val fixture = BehavioralOutputPlanFixture(
            throwingCompletionDisposers = setOf("first"),
            throwingDisposers = setOf("second"),
        )
        val primary = assertFailsWith<BehavioralFixtureFailure> { fixture.execute(null) }

        assertEquals("dispose:first", primary.message)
        assertEquals(
            listOf(
                "complete:claim:first",
                "dispose:first",
                "cleanup:claim:second",
                "dispose:second",
            ),
            fixture.events.takeLast(4),
        )
        assertEquals(1, fixture.disposeCount("first"))
        assertEquals(1, fixture.disposeCount("second"))
        assertEquals(1, primary.suppressed.size)
        assertEquals("dispose:second", primary.suppressed.single().message)
    }

    @Test
    fun simulated_downcall_and_receive_array_carriers_have_exact_order() {
        val fixture = BehavioralOutputPlanFixture()
        fixture.execute(null)
        assertEquals(
            listOf("zero:first", "zero:second", "call:receiver", "call:slot", "call:base", "call:first", "call:second"),
            fixture.events.take(7),
        )

        val receiveArray = ReceiveArrayBehaviorFixture()
        assertEquals(intArrayOf(3, 41).toList(), receiveArray.execute().toList())
        assertEquals(
            listOf("zero:length", "zero:data", "call:length,data", "decode:length,data", "dispose:length,data"),
            receiveArray.events,
        )
        assertEquals(listOf(3, 41), receiveArray.decoderCarrierValues)
        assertEquals(listOf(3, 41), receiveArray.disposerCarrierValues)
        assertTrue(receiveArray.decoderAndDisposerReceivedSameCarrier)

        val failedReceiveArray = ReceiveArrayBehaviorFixture()
        val failure = assertFailsWith<BehavioralFixtureFailure> {
            failedReceiveArray.execute(ReceiveArrayFailurePoint.DECODER)
        }
        assertEquals("RECEIVE_ARRAY_DECODER", failure.message)
        assertEquals(listOf("decode:length,data", "cleanup:claim:length,data", "dispose:length,data"), failedReceiveArray.events.takeLast(3))
        assertEquals(listOf(3, 41), failedReceiveArray.decoderCarrierValues)
        assertEquals(listOf(3, 41), failedReceiveArray.disposerCarrierValues)
        assertTrue(failedReceiveArray.decoderAndDisposerReceivedSameCarrier)
        assertEquals(1, failedReceiveArray.disposeCount)
    }

    @Test
    fun receiver_and_direct_com_input_are_fenced_immediately_after_the_downcall() {
        val bytecode = javap(Task2CallSiteLoweringFixture::class.java.name)
        assertFalse(bytecode.contains("Task 2 compiler-plugin fixture"))

        val methodStart = bytecode.indexOf("directComInputAndTwoOut(")
        val methodEnd = bytecode.indexOf("mixedCarriers(", methodStart)
        val method = bytecode.substring(methodStart, methodEnd)
        val downcall = method.indexOf("java/lang/invoke/MethodHandle.invokeExact:")
        val firstFence = method.indexOf("java/lang/ref/Reference.reachabilityFence:(Ljava/lang/Object;)V")
        val successBranch = method.indexOf("ifge")
        val failureTranslation = method.indexOf("HResultKt.\"throwHResultFailure")
        assertTrue(downcall >= 0 && firstFence > downcall, method)
        assertTrue(successBranch > firstFence, method)
        assertTrue(failureTranslation > successBranch, method)
        assertEquals(2, method.countOccurrences("java/lang/ref/Reference.reachabilityFence:(Ljava/lang/Object;)V"))

        val disposedFrameLocals = method.readPointerFrameLocals().takeLast(4).distinct()
        assertEquals(2, disposedFrameLocals.size)
        assertTrue(disposedFrameLocals[0] > disposedFrameLocals[1], disposedFrameLocals.toString())
        assertTrue(method.countOccurrences("WinRTOut.setValue") >= 2)
        assertTrue(method.countOccurrences("iconst_0") >= 2)
        assertTrue(method.countOccurrences("iconst_1") >= 2)
    }

    @Test
    fun mixed_carriers_use_a_compiler_synthesized_static_exact_handle() {
        val bytecode = javap(Task2CallSiteLoweringFixture::class.java.name)
        val fieldOwner = javap("io.github.composefluent.winrt.runtime.WinRTCallSiteLoweringContractTestKt")
        assertTrue(bytecode.contains("access\$getKotlinWinRTExactHResultHandle_int8_float32_float64"))
        assertTrue(fieldOwner.contains("kotlinWinRTExactHResultHandle_int8_float32_float64"))
        assertFalse(bytecode.contains("hResult:(Ljava/lang/String;)"))
    }

    private fun javap(className: String): String {
        val output = StringWriter()
        val errors = StringWriter()
        val outputWriter = PrintWriter(output)
        val errorWriter = PrintWriter(errors)
        val result = ToolProvider.findFirst("javap").orElseThrow().run(
            outputWriter,
            errorWriter,
            "-c",
            "-p",
            "-classpath",
            System.getProperty("java.class.path"),
            className,
        )
        outputWriter.flush()
        errorWriter.flush()
        assertEquals(0, result, errors.toString())
        return output.toString()
    }
}

private fun String.countOccurrences(value: String): Int = windowed(value.length).count { candidate -> candidate == value }

private fun String.methodBytecode(name: String): String {
    val start = indexOf(" $name")
    require(start >= 0) { "Missing javap method $name" }
    val next = indexOf("\n  public final ", start + 1).takeIf { index -> index >= 0 } ?: length
    return substring(start, next)
}

private fun String.readPointerFrameLocals(): List<Int> = Regex(
    "(?m)^\\s*\\d+:\\s+aload\\s+(\\d+)\\s*$\\R" +
        "\\s*\\d+:\\s+invokevirtual.*NativeScalarScratchFrame.*readPointer",
).findAll(this).map { match -> match.groupValues[1].toInt() }.toList()

private fun String.assertOrderedAfter(anchor: String, vararg tokens: String) {
    var index = indexOf(anchor)
    assertTrue(index >= 0, "Missing anchor $anchor")
    tokens.forEach { token ->
        index = indexOf(token, index + 1)
        assertTrue(index >= 0, "Missing ordered token $token after $anchor")
    }
}

private enum class FailurePoint {
    HRESULT,
    FIRST_DECODER,
    SECOND_DECODER,
    RESULT_CONSTRUCTION,
    POST_CALL,
    OUT_SETTER,
}

private enum class ReceiveArrayFailurePoint {
    DECODER,
}

private class BehavioralFixtureFailure(message: String) : RuntimeException(message)

private class BehavioralOutputPlanFixture(
    private val throwingDisposers: Set<String> = emptySet(),
    private val throwingCompletionDisposers: Set<String> = emptySet(),
) {
    val events = mutableListOf<String>()
    private val outputs = listOf(Output("first"), Output("second"))

    fun execute(failurePoint: FailurePoint?): String {
        outputs.forEach { output -> events += "zero:${output.name}" }
        var primaryFailure: Throwable? = null
        try {
            events += listOf("call:receiver", "call:slot", "call:base", "call:first", "call:second")
            failAt(failurePoint, FailurePoint.HRESULT)
            failAt(failurePoint, FailurePoint.POST_CALL)
            assignOrdinaryOut(failurePoint)
            decode(outputs[0], failurePoint, FailurePoint.FIRST_DECODER)
            decode(outputs[1], failurePoint, FailurePoint.SECOND_DECODER)
            events += "constructor"
            failAt(failurePoint, FailurePoint.RESULT_CONSTRUCTION)
            outputs.forEach { output ->
                complete(output)
                events += "transfer:${output.name}"
            }
            return "first+second"
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            outputs.asReversed().forEach { output ->
                if (!output.transferred && !output.cleanupClaimed) {
                    output.cleanupClaimed = true
                    events += "cleanup:claim:${output.name}"
                    try {
                        dispose(output, throwingDisposers)
                    } catch (error: Throwable) {
                        cleanupFailure?.addSuppressed(error) ?: run { cleanupFailure = error }
                    }
                }
            }
            cleanupFailure?.let { error ->
                primaryFailure?.addSuppressed(error) ?: throw error
            }
        }
    }

    fun disposeCount(name: String): Int = events.count { it == "dispose:$name" }

    private fun decode(output: Output, failurePoint: FailurePoint?, expected: FailurePoint) {
        events += "decode:${output.name}"
        failAt(failurePoint, expected)
    }

    private fun assignOrdinaryOut(failurePoint: FailurePoint?) {
        events += "setter:ordinary"
        failAt(failurePoint, FailurePoint.OUT_SETTER)
    }

    private fun complete(output: Output) {
        output.cleanupClaimed = true
        events += "complete:claim:${output.name}"
        dispose(output, throwingCompletionDisposers)
        output.transferred = true
    }

    private fun dispose(output: Output, throwing: Set<String>) {
        events += "dispose:${output.name}"
        if (output.name in throwing) throw BehavioralFixtureFailure("dispose:${output.name}")
    }

    private fun failAt(actual: FailurePoint?, expected: FailurePoint) {
        if (actual == expected) throw BehavioralFixtureFailure(expected.name)
    }

    private data class Output(
        val name: String,
        var transferred: Boolean = false,
        var cleanupClaimed: Boolean = false,
    )
}

private class ReceiveArrayBehaviorFixture {
    val events = mutableListOf<String>()
    private var decodedCarrier: Carrier? = null
    private var disposedCarrier: Carrier? = null

    val decoderCarrierValues: List<Int> get() = decodedCarrier?.values ?: emptyList()
    val disposerCarrierValues: List<Int> get() = disposedCarrier?.values ?: emptyList()
    val decoderAndDisposerReceivedSameCarrier: Boolean get() = decodedCarrier === disposedCarrier
    val disposeCount: Int get() = events.count { it == "dispose:length,data" }

    fun execute(failurePoint: ReceiveArrayFailurePoint? = null): IntArray {
        events += "zero:length"
        events += "zero:data"
        events += "call:length,data"
        val carrier = Carrier(length = 3, data = 41)
        var transferred = false
        var cleanupClaimed = false
        try {
            val decoded = decode(carrier, failurePoint)
            cleanupClaimed = true
            dispose(carrier)
            transferred = true
            return decoded
        } finally {
            if (!transferred && !cleanupClaimed) {
                cleanupClaimed = true
                events += "cleanup:claim:length,data"
                dispose(carrier)
            }
        }
    }

    private fun decode(carrier: Carrier, failurePoint: ReceiveArrayFailurePoint?): IntArray {
        decodedCarrier = carrier
        events += "decode:length,data"
        if (failurePoint == ReceiveArrayFailurePoint.DECODER) {
            throw BehavioralFixtureFailure("RECEIVE_ARRAY_DECODER")
        }
        return carrier.values.toIntArray()
    }

    private fun dispose(carrier: Carrier) {
        disposedCarrier = carrier
        events += "dispose:length,data"
    }

    private data class Carrier(val length: Int, val data: Int) {
        val values: List<Int> get() = listOf(length, data)
    }
}
