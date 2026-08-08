@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
    kotlin.native.concurrent.ObsoleteWorkersApi::class,
)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.Vector128
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

private const val mixedHResult = 0x10203040
private const val packedHResult = 0x11223344
private const val innerHResult = 0x01020304
private const val outerHResult = 0x05060708
private const val innerValue = 0x1020_3040_5060_7080L
private const val outerValue = 0x1122_3344_5566_7788L
private const val hStringHResult = 0x21324354
private const val scalarHStringInputHResult = 0x31425364
private const val scalarHStringInputValue = 0x7766_5544_3322_1100L
private const val highArityHResult = 0x41526374

private typealias HResultRecipeThunk7 =
    CFunction<(Long, Long, Long, Long, Long, Long, Long, Long, Long) -> Int>
private typealias HResultRecipeThunk12 =
    CFunction<(
        Long, Long, Long, Long, Long, Long, Long,
        Long, Long, Long, Long, Long, Long, Long,
    ) -> Int>
private typealias PackedRecipeThunk1 = CFunction<(Long, Long, Long) -> Long>
private typealias ScalarRecipeThunk1 = CFunction<(Long, Long, Long, Long) -> Long>
private typealias WideScalarRecipeThunk1 = CFunction<(Long, Long, Long) -> Vector128>
private typealias PackedHStringRecipeThunk =
    CFunction<(Long, Long, Long, Long, Long, Long, Long, Long) -> Long>
private typealias PackedEmptyHStringRecipeThunk = CFunction<(Long, Long, Long, Long) -> Long>
private typealias ScalarHStringInputRecipeThunk = CFunction<(Long, Long, Long, Long, Long) -> Long>
private typealias WideScalarHStringInputRecipeThunk = CFunction<(Long, Long, Long, Long) -> Vector128>

@ThreadLocal
private var reentrantThunk: CPointer<ScalarRecipeThunk1>? = null

@ThreadLocal
private var reentrantInstance: Long = 0L

@ThreadLocal
private var reentrantWideThunk: CPointer<WideScalarRecipeThunk1>? = null

@ThreadLocal
private var reentrantWideInstance: Long = 0L

class Win64ComRecipeThunkTest {
    @Test
    fun scalarResultRecordIsStableAndIsolatedAcrossWorkers() {
        val mainRecord = winRTScalarResultRecord()
        assertEquals(mainRecord, winRTScalarResultRecord())

        val worker = Worker.start()
        try {
            val workerRecord = worker.execute(TransferMode.SAFE, { Unit }) {
                val first = winRTScalarResultRecord()
                check(first == winRTScalarResultRecord())
                first.value
            }.result
            assertNotEquals(mainRecord.value, workerRecord)
        } finally {
            worker.requestTermination().result
        }
    }

    @Test
    fun forwardsSevenMixedCarriersToGprXmmAndStackPositions() {
        withFakeComObject(staticCFunction(::mixedCarrierTarget).reinterpret<COpaque>()) { instance ->
            val encodedKinds = encodeFloatingPointKinds(1, 2, 0, 1, 2, 0, 0)
            val address = winRTCreateHResultRecipeThunk(7, encodedKinds)
            val cachedAddress = winRTCreateHResultRecipeThunk(7, encodedKinds)
            assertEquals(address, cachedAddress)
            val thunk = requireNotNull(address.value.toCPointer<HResultRecipeThunk7>())

            val result = thunk.invoke(
                instance.value,
                0L,
                1.25f.toBits().toLong(),
                (-9.5).toBits(),
                0x1020_3040_5060_7080L,
                (-3.75f).toBits().toLong(),
                17.125.toBits(),
                -0x1020_3040_5060_708L,
                0x55667788L,
            )

            assertEquals(mixedHResult, result)
        }
    }

    @Test
    fun forwardsTwelveCarriersAcrossMultipleStackPositions() {
        withFakeComObject(staticCFunction(::highArityTarget).reinterpret<COpaque>()) { instance ->
            val address = winRTCreateHResultRecipeThunk(12, 0L)
            val thunk = requireNotNull(address.value.toCPointer<HResultRecipeThunk12>())

            val result = thunk.invoke(
                instance.value,
                0L,
                0x101L,
                0x202L,
                0x303L,
                0x404L,
                0x505L,
                0x606L,
                0x707L,
                0x808L,
                0x909L,
                0xA0AL,
                0xB0BL,
                0xC0CL,
            )

            assertEquals(highArityHResult, result)
        }
    }

    @Test
    fun packsHResultAndFourByteOutputAfterFloatingPointInput() {
        withFakeComObject(staticCFunction(::packedFloatTarget).reinterpret<COpaque>()) { instance ->
            val address = winRTCreatePackedScalarResultRecipeThunk(1, encodeFloatingPointKinds(2))
            val thunk = requireNotNull(address.value.toCPointer<PackedRecipeThunk1>())

            val packed = thunk.invoke(instance.value, 0L, 6.625.toBits())

            assertEquals(packedHResult, winRTPackedScalarResultHResult(packed))
            assertEquals(13.25f, winRTPackedScalarResultFloat32(packed))
        }
    }

    @Test
    fun wideResultRecordSurvivesSynchronousSameThreadReentry() {
        withFakeComObject(staticCFunction(::reentrantScalarTarget).reinterpret<COpaque>()) { instance ->
            val address = winRTCreateScalarResultRecipeThunk(1, 0L)
            val thunk = requireNotNull(address.value.toCPointer<ScalarRecipeThunk1>())
            reentrantThunk = thunk
            reentrantInstance = instance.value
            try {
                val record = winRTScalarResultRecord()
                val returnedRecord = thunk.invoke(instance.value, 0L, 1L, record.value)

                assertEquals(record.value, returnedRecord)
                assertEquals(outerHResult, winRTScalarResultHResult(record))
                assertEquals(outerValue, PlatformAbi.readInt64(winRTScalarResultValue(record)))
            } finally {
                reentrantThunk = null
                reentrantInstance = 0L
            }
        }
    }

    @Test
    fun wideScalarResultSurvivesSynchronousSameThreadReentry() {
        withFakeComObject(staticCFunction(::reentrantWideScalarTarget).reinterpret<COpaque>()) { instance ->
            val address = winRTCreateWideScalarResultRecipeThunk(1, 0L)
            assertEquals(address, winRTCreateWideScalarResultRecipeThunk(1, 0L))
            val thunk = requireNotNull(address.value.toCPointer<WideScalarRecipeThunk1>())
            reentrantWideThunk = thunk
            reentrantWideInstance = instance.value
            try {
                val result = thunk.invoke(instance.value, 0L, 1L)

                assertEquals(outerValue, result.getLongAt(0))
                assertEquals(outerHResult, result.getIntAt(2))
            } finally {
                reentrantWideThunk = null
                reentrantWideInstance = 0L
            }
        }
    }

    @Test
    fun scalarResultRecordUsesExpandedWordPositionForHStringInput() {
        val input = nativeHeap.allocArray<UShortVar>(4)
        input[0] = 0x0041u
        input[1] = 0u
        input[2] = 0x4E2Du
        input[3] = 0u
        try {
            withFakeComObject(staticCFunction(::scalarHStringInputTarget).reinterpret<COpaque>()) { instance ->
                val address = winRTCreateScalarResultRecipeThunk(1, encodeFloatingPointKinds(3))
                val thunk = requireNotNull(address.value.toCPointer<ScalarHStringInputRecipeThunk>())
                val record = winRTScalarResultRecord()

                val returnedRecord = thunk.invoke(
                    instance.value,
                    0L,
                    input.rawValue.toLong(),
                    3L,
                    record.value,
                )

                assertEquals(record.value, returnedRecord)
                assertEquals(scalarHStringInputHResult, winRTScalarResultHResult(record))
                assertEquals(
                    scalarHStringInputValue,
                    PlatformAbi.readInt64(winRTScalarResultValue(record)),
                )
            }
        } finally {
            nativeHeap.free(input.rawValue)
        }
    }

    @Test
    fun wideScalarResultUsesExpandedWordPositionForHStringInput() {
        val input = nativeHeap.allocArray<UShortVar>(4)
        input[0] = 0x0041u
        input[1] = 0u
        input[2] = 0x4E2Du
        input[3] = 0u
        try {
            withFakeComObject(staticCFunction(::scalarHStringInputTarget).reinterpret<COpaque>()) { instance ->
                val address = winRTCreateWideScalarResultRecipeThunk(1, encodeFloatingPointKinds(3))
                val thunk = requireNotNull(address.value.toCPointer<WideScalarHStringInputRecipeThunk>())

                val result = thunk.invoke(
                    instance.value,
                    0L,
                    input.rawValue.toLong(),
                    3L,
                )

                assertEquals(scalarHStringInputValue, result.getLongAt(0))
                assertEquals(scalarHStringInputHResult, result.getIntAt(2))
            }
        } finally {
            nativeHeap.free(input.rawValue)
        }
    }

    @Test
    fun wideScalarResultConsumesOwnedHStringAndChecksOriginalHResult() {
        val successful = HString.create("scalar-result")
        var successfulTransferred = false
        try {
            successfulTransferred = true
            assertEquals(
                "scalar-result",
                winRTConsumeOwnedHStringScalarResult(
                    successful.handle.value,
                    KnownHResults.S_OK.value,
                    checkHResult = true,
                ),
            )
        } finally {
            if (!successfulTransferred) successful.close()
        }

        val failed = HString.create("discarded")
        var failedTransferred = false
        try {
            failedTransferred = true
            val error = assertFailsWith<WinRTRuntimeException> {
                winRTConsumeOwnedHStringScalarResult(
                    failed.handle.value,
                    KnownHResults.E_FAIL.value,
                    checkHResult = true,
                )
            }
            assertEquals(KnownHResults.E_FAIL, error.hResult)
        } finally {
            if (!failedTransferred) failed.close()
        }
    }

    @Test
    fun buildsStackHStringHeadersFromComposedEntryWords() {
        val first = nativeHeap.allocArray<UShortVar>(4)
        val second = nativeHeap.allocArray<UShortVar>(3)
        first[0] = 0x0041u
        first[1] = 0u
        first[2] = 0x4E2Du
        first[3] = 0u
        second[0] = 0x03A9u
        second[1] = 0xD83Du
        second[2] = 0u
        try {
            withFakeComObject(staticCFunction(::mixedHStringTarget).reinterpret<COpaque>()) { instance ->
                val kinds = encodeFloatingPointKinds(0, 3, 1, 3)
                val address = winRTCreatePackedScalarResultRecipeThunk(4, kinds)
                val thunk = requireNotNull(address.value.toCPointer<PackedHStringRecipeThunk>())

                val packed = thunk.invoke(
                    instance.value,
                    0L,
                    0x1020_3040_5060_7080L,
                    first.rawValue.toLong(),
                    3L,
                    6.625f.toBits().toLong(),
                    second.rawValue.toLong(),
                    2L,
                )

                assertEquals(hStringHResult, winRTPackedScalarResultHResult(packed))
                assertEquals(1, winRTPackedScalarResultInt8(packed).toInt())
            }
        } finally {
            nativeHeap.free(second.rawValue)
            nativeHeap.free(first.rawValue)
        }
    }

    @Test
    fun mapsEmptyHStringEntryToNullAbiHandle() {
        withFakeComObject(staticCFunction(::emptyHStringTarget).reinterpret<COpaque>()) { instance ->
            val address = winRTCreatePackedScalarResultRecipeThunk(1, encodeFloatingPointKinds(3))
            val thunk = requireNotNull(address.value.toCPointer<PackedEmptyHStringRecipeThunk>())

            val packed = thunk.invoke(instance.value, 0L, 0L, 0L)

            assertEquals(hStringHResult, winRTPackedScalarResultHResult(packed))
            assertEquals(1, winRTPackedScalarResultInt8(packed).toInt())
        }
    }
}

private fun mixedCarrierTarget(
    instance: COpaquePointer?,
    floatRegister: Float,
    doubleRegister: Double,
    integerRegister: Long,
    floatStack: Float,
    doubleStack: Double,
    longStack: Long,
    intStack: Int,
): Int = when {
    instance == null -> -1
    floatRegister.toBits() != 1.25f.toBits() -> -2
    doubleRegister.toBits() != (-9.5).toBits() -> -3
    integerRegister != 0x1020_3040_5060_7080L -> -4
    floatStack.toBits() != (-3.75f).toBits() -> -5
    doubleStack.toBits() != 17.125.toBits() -> -6
    longStack != -0x1020_3040_5060_708L -> -7
    intStack != 0x55667788 -> -8
    else -> mixedHResult
}

private fun highArityTarget(
    instance: COpaquePointer?,
    first: Long,
    second: Long,
    third: Long,
    fourth: Long,
    fifth: Long,
    sixth: Long,
    seventh: Long,
    eighth: Long,
    ninth: Long,
    tenth: Long,
    eleventh: Long,
    twelfth: Long,
): Int = when {
    instance == null -> -1
    first != 0x101L -> -2
    second != 0x202L -> -3
    third != 0x303L -> -4
    fourth != 0x404L -> -5
    fifth != 0x505L -> -6
    sixth != 0x606L -> -7
    seventh != 0x707L -> -8
    eighth != 0x808L -> -9
    ninth != 0x909L -> -10
    tenth != 0xA0AL -> -11
    eleventh != 0xB0BL -> -12
    twelfth != 0xC0CL -> -13
    else -> highArityHResult
}

private fun packedFloatTarget(
    instance: COpaquePointer?,
    value: Double,
    output: CPointer<FloatVar>?,
): Int {
    if (instance == null || value.toBits() != 6.625.toBits() || output == null) return -1
    output.pointed.value = 13.25f
    return packedHResult
}

private fun reentrantScalarTarget(
    instance: COpaquePointer?,
    depth: Long,
    output: CPointer<LongVar>?,
): Int {
    if (instance == null || output == null) return -1
    if (depth == 1L) {
        val thunk = reentrantThunk ?: return -2
        val innerRecord = winRTScalarResultRecord()
        val returnedRecord = thunk.invoke(reentrantInstance, 0L, 0L, innerRecord.value)
        if (returnedRecord != innerRecord.value) return -3
        if (winRTScalarResultHResult(innerRecord) != innerHResult) return -4
        if (PlatformAbi.readInt64(winRTScalarResultValue(innerRecord)) != innerValue) return -5
    }
    output.pointed.value = if (depth == 0L) innerValue else outerValue
    return if (depth == 0L) innerHResult else outerHResult
}

private fun reentrantWideScalarTarget(
    instance: COpaquePointer?,
    depth: Long,
    output: CPointer<LongVar>?,
): Int {
    if (instance == null || output == null) return -1
    if (depth == 1L) {
        val thunk = reentrantWideThunk ?: return -2
        val innerResult = thunk.invoke(reentrantWideInstance, 0L, 0L)
        if (innerResult.getIntAt(2) != innerHResult) return -3
        if (innerResult.getLongAt(0) != innerValue) return -4
    }
    output.pointed.value = if (depth == 0L) innerValue else outerValue
    return if (depth == 0L) innerHResult else outerHResult
}

private fun mixedHStringTarget(
    instance: COpaquePointer?,
    integer: Long,
    first: COpaquePointer?,
    floatValue: Float,
    second: COpaquePointer?,
    output: CPointer<ByteVar>?,
): Int {
    if (instance == null || integer != 0x1020_3040_5060_7080L || floatValue.toBits() != 6.625f.toBits()) {
        return -1
    }
    if (!matchesHString(first, ushortArrayOf(0x0041u, 0u, 0x4E2Du))) return -2
    if (!matchesHString(second, ushortArrayOf(0x03A9u, 0xD83Du))) return -3
    output?.pointed?.value = 1
    return if (output == null) -4 else hStringHResult
}

private fun emptyHStringTarget(
    instance: COpaquePointer?,
    value: COpaquePointer?,
    output: CPointer<ByteVar>?,
): Int {
    if (instance == null || value != null || output == null) return -1
    output.pointed.value = 1
    return hStringHResult
}

private fun scalarHStringInputTarget(
    instance: COpaquePointer?,
    value: COpaquePointer?,
    output: CPointer<LongVar>?,
): Int {
    if (instance == null || !matchesHString(value, ushortArrayOf(0x0041u, 0u, 0x4E2Du)) || output == null) {
        return -1
    }
    output.pointed.value = scalarHStringInputValue
    return scalarHStringInputHResult
}

private fun matchesHString(handle: COpaquePointer?, expected: UShortArray): Boolean {
    if (handle == null) return false
    val fields = handle.reinterpret<IntVar>()
    if (fields[0] != 1 || fields[1] != expected.size) return false
    val buffer = (handle.rawValue.toLong() + 16L)
        .toCPointer<COpaquePointerVar>()
        ?.pointed?.value
        ?.reinterpret<UShortVar>() ?: return false
    return expected.indices.all { index -> buffer[index] == expected[index] } && buffer[expected.size] == 0.toUShort()
}

private inline fun <T> withFakeComObject(
    vararg methods: COpaquePointer?,
    block: (RawComPtr) -> T,
): T {
    val vtable = nativeHeap.allocArray<COpaquePointerVar>(methods.size)
    val instance = nativeHeap.allocArray<COpaquePointerVar>(1)
    methods.forEachIndexed { index, method -> vtable[index] = method }
    instance[0] = vtable.reinterpret<COpaque>()
    return try {
        block(RawComPtr(instance.rawValue.toLong()))
    } finally {
        nativeHeap.free(instance.rawValue)
        nativeHeap.free(vtable.rawValue)
    }
}

private fun encodeFloatingPointKinds(vararg kinds: Int): Long =
    kinds.foldIndexed(0L) { index, encoded, kind -> encoded or (kind.toLong() shl (index * 2)) }
