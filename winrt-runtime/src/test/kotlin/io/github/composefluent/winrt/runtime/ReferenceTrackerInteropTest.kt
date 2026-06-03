package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceTrackerInteropTest {
    @Test
    fun com_object_reference_initializes_and_releases_reference_tracker() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawAddress().asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertTrue(reference.hasReferenceTracker)
            assertEquals(1, host.trackerAddRefFromSourceCalls.get())

            reference.close()

            assertEquals(2, host.trackerReleaseFromSourceCalls.get())
            assertEquals(1, host.objectReleaseCalls.get())
            assertEquals(2, host.trackerReleaseCalls.get())
        }
    }

    @Test
    fun composable_reference_initialization_tracks_without_addref_from_tracker_source() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawAddress().asRawComPtr(), IID.IInspectable)

            val initialized = ComWrappersSupport.initializeComposableReference(reference)

            assertTrue(initialized.hasReferenceTracker)
            assertEquals(0, host.trackerAddRefFromSourceCalls.get())

            initialized.close()

            assertEquals(1, host.trackerReleaseFromSourceCalls.get())
            assertEquals(1, host.objectReleaseCalls.get())
            assertEquals(2, host.trackerReleaseCalls.get())
        }
    }

    @Test
    fun get_ref_adds_com_reference_without_tracker_source_reference() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawAddress().asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertEquals(1, host.trackerAddRefFromSourceCalls.get())

            val pointer = reference.getRefPointer()

            assertEquals(host.objectPointer.address(), PlatformAbi.pointerKey(pointer))
            assertEquals(1, host.objectAddRefCalls.get())
            assertEquals(1, host.trackerAddRefFromSourceCalls.get())

            WinRtPlatformApi.releaseRaw(pointer.asRawAddress())
            reference.close()
            assertEquals(2, host.objectReleaseCalls.get())
        }
    }

    private class FakeReferenceTrackerHost private constructor(
        private val arena: Arena,
        val objectPointer: MemorySegment,
        private val trackerPointer: MemorySegment,
        val objectAddRefCalls: AtomicInteger,
        val objectReleaseCalls: AtomicInteger,
        val trackerReleaseCalls: AtomicInteger,
        val trackerAddRefFromSourceCalls: AtomicInteger,
        val trackerReleaseFromSourceCalls: AtomicInteger,
    ) : AutoCloseable {
        override fun close() {
            registry.remove(objectPointer.address())
            registry.remove(trackerPointer.address())
            arena.close()
        }

        companion object {
            private val registry = ConcurrentHashMap<Long, FakeReferenceTrackerHost>()
            private val linker = Linker.nativeLinker()
            private val lookup = MethodHandles.lookup()
            private val sharedArena = Arena.global()
            private val queryInterfaceStub = linker.upcallStub(
                lookup.findStatic(
                    FakeReferenceTrackerHost::class.java,
                    "queryInterfaceBridge",
                    MethodType.methodType(
                        Int::class.javaPrimitiveType,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                    ),
                ),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                sharedArena,
            )
            private val addRefStub = linker.upcallStub(
                lookup.findStatic(
                    FakeReferenceTrackerHost::class.java,
                    "addRefBridge",
                    MethodType.methodType(Int::class.javaPrimitiveType, MemorySegment::class.java),
                ),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                sharedArena,
            )
            private val releaseStub = linker.upcallStub(
                lookup.findStatic(
                    FakeReferenceTrackerHost::class.java,
                    "releaseBridge",
                    MethodType.methodType(Int::class.javaPrimitiveType, MemorySegment::class.java),
                ),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                sharedArena,
            )
            private val addRefFromTrackerSourceStub = linker.upcallStub(
                lookup.findStatic(
                    FakeReferenceTrackerHost::class.java,
                    "addRefFromTrackerSourceBridge",
                    MethodType.methodType(Int::class.javaPrimitiveType, MemorySegment::class.java),
                ),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                sharedArena,
            )
            private val releaseFromTrackerSourceStub = linker.upcallStub(
                lookup.findStatic(
                    FakeReferenceTrackerHost::class.java,
                    "releaseFromTrackerSourceBridge",
                    MethodType.methodType(Int::class.javaPrimitiveType, MemorySegment::class.java),
                ),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                sharedArena,
            )

            fun create(): FakeReferenceTrackerHost {
                val arena = Arena.ofConfined()
                val objectVtable = arena.allocate(MemoryLayout.sequenceLayout(3, ValueLayout.ADDRESS))
                objectVtable.setAtIndex(ValueLayout.ADDRESS, 0, queryInterfaceStub)
                objectVtable.setAtIndex(ValueLayout.ADDRESS, 1, addRefStub)
                objectVtable.setAtIndex(ValueLayout.ADDRESS, 2, releaseStub)
                val objectMemory = arena.allocate(ValueLayout.ADDRESS)
                objectMemory.set(ValueLayout.ADDRESS, 0, objectVtable)

                val trackerVtable = arena.allocate(MemoryLayout.sequenceLayout(10, ValueLayout.ADDRESS))
                trackerVtable.setAtIndex(ValueLayout.ADDRESS, 0, queryInterfaceStub)
                trackerVtable.setAtIndex(ValueLayout.ADDRESS, 1, addRefStub)
                trackerVtable.setAtIndex(ValueLayout.ADDRESS, 2, releaseStub)
                trackerVtable.setAtIndex(ValueLayout.ADDRESS, 7, addRefFromTrackerSourceStub)
                trackerVtable.setAtIndex(ValueLayout.ADDRESS, 8, releaseFromTrackerSourceStub)
                val trackerMemory = arena.allocate(ValueLayout.ADDRESS)
                trackerMemory.set(ValueLayout.ADDRESS, 0, trackerVtable)

                return FakeReferenceTrackerHost(
                    arena = arena,
                    objectPointer = objectMemory,
                    trackerPointer = trackerMemory,
                    objectAddRefCalls = AtomicInteger(0),
                    objectReleaseCalls = AtomicInteger(0),
                    trackerReleaseCalls = AtomicInteger(0),
                    trackerAddRefFromSourceCalls = AtomicInteger(0),
                    trackerReleaseFromSourceCalls = AtomicInteger(0),
                ).also { host ->
                    registry[objectMemory.address()] = host
                    registry[trackerMemory.address()] = host
                }
            }

            @JvmStatic
            private fun queryInterfaceBridge(thisPointer: MemorySegment, iidPointer: MemorySegment, resultPointer: MemorySegment): Int {
                val host = registry[thisPointer.address()] ?: return KnownHResults.RO_E_CLOSED.value
                val iid = Guid.readFrom(iidPointer.reinterpret(NativeLayoutsJvmCompat.GUID_SIZE_BYTES).asRawAddress())
                val resultOut = resultPointer.reinterpret(ValueLayout.ADDRESS.byteSize())
                val resolved = when (iid) {
                    IID.IUnknown,
                    IID.IInspectable,
                    -> host.objectPointer

                    IID.IReferenceTracker -> host.trackerPointer
                    else -> MemorySegment.NULL
                }
                if (resolved == MemorySegment.NULL) {
                    resultOut.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
                    return KnownHResults.E_NOINTERFACE.value
                }
                resultOut.set(ValueLayout.ADDRESS, 0, resolved)
                return KnownHResults.S_OK.value
            }

            @JvmStatic
            private fun addRefBridge(thisPointer: MemorySegment): Int {
                val host = registry[thisPointer.address()] ?: return 0
                if (thisPointer.address() == host.objectPointer.address()) {
                    host.objectAddRefCalls.incrementAndGet()
                }
                return 2
            }

            @JvmStatic
            private fun releaseBridge(thisPointer: MemorySegment): Int {
                val host = registry[thisPointer.address()] ?: return 0
                if (thisPointer.address() == host.objectPointer.address()) {
                    host.objectReleaseCalls.incrementAndGet()
                } else {
                    host.trackerReleaseCalls.incrementAndGet()
                }
                return 1
            }

            @JvmStatic
            private fun addRefFromTrackerSourceBridge(thisPointer: MemorySegment): Int {
                val host = registry[thisPointer.address()] ?: return 0
                host.trackerAddRefFromSourceCalls.incrementAndGet()
                return 1
            }

            @JvmStatic
            private fun releaseFromTrackerSourceBridge(thisPointer: MemorySegment): Int {
                val host = registry[thisPointer.address()] ?: return 0
                host.trackerReleaseFromSourceCalls.incrementAndGet()
                return 1
            }
        }
    }
}
