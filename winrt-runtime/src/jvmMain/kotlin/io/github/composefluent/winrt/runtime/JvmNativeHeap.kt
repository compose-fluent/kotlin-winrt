package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

internal object JvmNativeHeap {
    private const val HEAP_ZERO_MEMORY = 0x00000008
    private val linker = Linker.nativeLinker()
    private val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    private val getProcessHeap = downcall(
        "GetProcessHeap",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val heapAlloc = downcall(
        "HeapAlloc",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG,
        ),
    )
    private val heapFree = downcall(
        "HeapFree",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ),
    )
    private val processHeap = processHeap()

    @JvmStatic
    internal fun allocateZeroed(sizeBytes: Long): Long =
        try {
            val allocation = heapAlloc.invokeExact(
                processHeap,
                HEAP_ZERO_MEMORY,
                sizeBytes,
            ) as MemorySegment
            allocation.address()
        } catch (failure: Throwable) {
            throw propagate("HeapAlloc failed.", failure)
        }

    @JvmStatic
    internal fun free(address: Long) {
        try {
            val freed = heapFree.invokeExact(
                processHeap,
                0,
                MemorySegment.ofAddress(address),
            ) as Int
            if (freed == 0) {
                throw IllegalStateException("HeapFree failed for native address $address.")
            }
        } catch (failure: Throwable) {
            throw propagate("HeapFree invocation failed.", failure)
        }
    }

    private fun processHeap(): MemorySegment =
        try {
            val heap = getProcessHeap.invokeExact() as MemorySegment
            if (heap.address() == 0L) {
                throw IllegalStateException("GetProcessHeap returned a null handle.")
            }
            heap
        } catch (failure: Throwable) {
            throw propagate("GetProcessHeap failed.", failure)
        }

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(kernel32.find(name).orElseThrow(), descriptor)

    private fun propagate(message: String, failure: Throwable): RuntimeException =
        when (failure) {
            is RuntimeException -> failure
            is Error -> throw failure
            else -> IllegalStateException(message, failure)
        }
}
