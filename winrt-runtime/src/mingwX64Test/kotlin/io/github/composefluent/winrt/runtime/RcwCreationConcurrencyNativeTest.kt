@file:OptIn(kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertFalse
import platform.windows.GetTickCount64
import platform.windows.Sleep

class RcwCreationConcurrencyNativeTest {
    // CsWinRT RuntimeWrapperCache allows racing factories, but publishes one live identity.
    @Test
    fun racing_native_factories_publish_one_live_wrapper() {
        ComWrappersSupport.clearRegistriesForTests()
        val entered = AtomicInt(0)
        val candidates = List(2) { AtomicReference<IWinRTObject?>(null) }
        val host = WinRTInspectableComObject.inspectableBox(null, "test.NativeRcwRace")
        ComWrappersSupport.registerRuntimeClassFactory("test.NativeRcwRace") { reference ->
            val candidate = object : IWinRTObject { override val nativeObject: ComObjectReference = reference }
            val slot = entered.fetchAndAdd(1)
            candidates[slot].store(candidate)
            val deadline = GetTickCount64() + 10_000uL
            while (entered.load() < 2 && GetTickCount64() < deadline) Sleep(1u)
            check(entered.load() == 2) { "Native factory ran inside the publication lock" }
            ComWrappersSupport.registerRuntimeClassWrapper(candidate, reference)
            candidate
        }
        val workers = List(2) { Worker.start() }
        try {
            val futures = workers.map { worker ->
                worker.execute(TransferMode.SAFE, { host }) { host ->
                    val pointer = host.createReference(IID.IInspectable).use { it.getRefPointer().asRawAddress() }
                    ComWrappersSupport.createRcwForOwnedComObject(pointer) as IWinRTObject
                }
            }
            assertSame(futures[0].result, futures[1].result)
            candidates.forEach { assertFalse(requireNotNull(it.load()).nativeObject.isDisposed) }
        } finally {
            workers.forEach { it.requestTermination().result }
            candidates.forEach { it.load()?.nativeObject?.close() }
            host.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }
}
