package io.github.composefluent.winrt.runtime;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class JvmNativeHeap {
    private static final int HEAP_ZERO_MEMORY = 0x00000008;
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
    private static final MethodHandle GET_PROCESS_HEAP = downcall(
            "GetProcessHeap",
            FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle HEAP_ALLOC = downcall(
            "HeapAlloc",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG));
    private static final MethodHandle HEAP_FREE = downcall(
            "HeapFree",
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MemorySegment PROCESS_HEAP = processHeap();

    private JvmNativeHeap() {
    }

    static long allocateZeroed(long sizeBytes) {
        try {
            MemorySegment allocation = (MemorySegment) HEAP_ALLOC.invokeExact(
                    PROCESS_HEAP,
                    HEAP_ZERO_MEMORY,
                    sizeBytes);
            return allocation.address();
        } catch (Throwable failure) {
            throw propagate("HeapAlloc failed.", failure);
        }
    }

    static void free(long address) {
        try {
            int freed = (int) HEAP_FREE.invokeExact(
                    PROCESS_HEAP,
                    0,
                    MemorySegment.ofAddress(address));
            if (freed == 0) {
                throw new IllegalStateException("HeapFree failed for native address " + address + '.');
            }
        } catch (Throwable failure) {
            throw propagate("HeapFree invocation failed.", failure);
        }
    }

    private static MemorySegment processHeap() {
        try {
            MemorySegment heap = (MemorySegment) GET_PROCESS_HEAP.invokeExact();
            if (heap.address() == 0L) {
                throw new IllegalStateException("GetProcessHeap returned a null handle.");
            }
            return heap;
        } catch (Throwable failure) {
            throw propagate("GetProcessHeap failed.", failure);
        }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = KERNEL32.find(name).orElseThrow();
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static RuntimeException propagate(String message, Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(message, failure);
    }
}
