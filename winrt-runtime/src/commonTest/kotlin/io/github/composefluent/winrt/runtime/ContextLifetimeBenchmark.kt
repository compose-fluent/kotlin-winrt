package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.TimeSource

class ContextLifetimeBenchmark {
    @Test
    fun same_apartment_capture_call_and_close() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT 3 ContextAwareObjectReference keeps cross-context state lazy.
        // A sentinel object pointer is never dereferenced: this exercises the real
        // COM context callback lifetime and same-apartment pointer routing only.
        val benchmark = referenceContentionBenchmarkEnabled() && referenceContentionBenchmarkMode() == "context"
        RuntimeScope.initializeMultithreaded().use {
            val rounds = if (benchmark) 60 else 1
            val batch = if (benchmark) 4096 else 1
            val samples = ArrayList<Double>()
            repeat(rounds) { round ->
                val start = TimeSource.Monotonic.markNow()
                repeat(batch) {
                    val context = assertNotNull(ObjectReferenceContext.capture(
                        RawComPtr(1), IID.IUnknown.abiLowBits, IID.IUnknown.abiHighBits,
                        IID.IUnknown, callsAreFreeThreaded = false,
                    ))
                    context.use { assertEquals(RawComPtr(1), it.pointerForCurrentContext()) }
                }
                val ns = start.elapsedNow().inWholeNanoseconds.toDouble() / batch
                if (round >= 30) samples.add(ns)
            }
            if (benchmark) {
                samples.sort()
                println("CONTEXT medianNsPerOp=${samples[samples.size / 2]} meanNsPerOp=${samples.average()}")
            }
        }
    }
}
