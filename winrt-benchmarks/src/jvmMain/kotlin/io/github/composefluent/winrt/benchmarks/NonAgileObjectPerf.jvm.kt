package io.github.composefluent.winrt.benchmarks

import io.github.composefluent.winrt.runtime.RuntimeScope
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import windows.ui.popups.PopupMenu

internal actual fun createNonAgileObjectPerf(): NonAgileObjectPerf = JvmNonAgileObjectPerf()

private class JvmNonAgileObjectPerf : NonAgileObjectPerf {
    private val createObject = JvmWindowsWait.createAutoResetEvent()
    private val exitThread = JvmWindowsWait.createAutoResetEvent()
    private val objectCreated = JvmWindowsWait.createAutoResetEvent()

    @Volatile
    private var nonAgileObject: PopupMenu? = null

    @Volatile
    private var threadFailure: Throwable? = null

    private val staThread = Thread(::objectAllocationLoop, "kotlin-winrt-non-agile-benchmark").apply {
        start()
    }

    override fun constructAndQueryNonAgileObject() {
        constructNonAgileObject()
        callObject()
    }

    override fun constructNonAgileObject() {
        JvmWindowsWait.setEvent(createObject)
        JvmWindowsWait.waitForSingleObject(objectCreated)
        JvmWindowsWait.resetEvent(objectCreated)
        threadFailure?.let { failure ->
            throw IllegalStateException("The non-agile object STA worker failed.", failure)
        }
    }

    override fun close() {
        try {
            JvmWindowsWait.setEvent(exitThread)
            JvmWindowsWait.setEvent(createObject)
            staThread.join()
        } finally {
            JvmWindowsWait.closeHandle(objectCreated)
            JvmWindowsWait.closeHandle(exitThread)
            JvmWindowsWait.closeHandle(createObject)
        }
    }

    private fun objectAllocationLoop() {
        try {
            RuntimeScope.initializeSingleThreaded().use {
                Arena.ofConfined().use { waitArena ->
                    val handles = waitArena.allocate(ValueLayout.ADDRESS)
                    handles.set(ValueLayout.ADDRESS, 0L, createObject)
                    val signaledIndex = waitArena.allocate(ValueLayout.JAVA_INT)
                    try {
                        while (true) {
                            JvmWindowsWait.waitWithComDispatch(handles, signaledIndex)
                            if (JvmWindowsWait.isSignaled(exitThread, 1)) {
                                return
                            }
                            JvmWindowsWait.resetEvent(createObject)
                            nonAgileObject = PopupMenu()
                            callObject()
                            JvmWindowsWait.setEvent(objectCreated)
                        }
                    } finally {
                        nonAgileObject?.nativeObject?.close()
                        nonAgileObject = null
                    }
                }
            }
        } catch (failure: Throwable) {
            threadFailure = failure
            runCatching { JvmWindowsWait.setEvent(objectCreated) }
        }
    }

    private fun callObject(): Int = requireNotNull(nonAgileObject).commands.size
}

private object JvmWindowsWait {
    private const val waitObject0: Int = 0
    private const val waitTimeout: Int = 0x102
    private const val infinite: Int = -1
    private const val comAwareWaitFlags: Int = 0x0000000A

    private val linker = java.lang.foreign.Linker.nativeLinker()
    private val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    private val ole32 = SymbolLookup.libraryLookup("ole32", Arena.global())
    private val createEventW = downcall(
        kernel32,
        "CreateEventW",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ),
    )
    private val setEvent = downcall(
        kernel32,
        "SetEvent",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
    )
    private val resetEvent = downcall(
        kernel32,
        "ResetEvent",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
    )
    private val waitForSingleObject = downcall(
        kernel32,
        "WaitForSingleObject",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
    )
    private val closeHandle = downcall(
        kernel32,
        "CloseHandle",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
    )
    private val coWaitForMultipleHandles = downcall(
        ole32,
        "CoWaitForMultipleHandles",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ),
    )

    fun createAutoResetEvent(): MemorySegment {
        val value = createEventW.invokeWithArguments(
            MemorySegment.NULL,
            0,
            0,
            MemorySegment.NULL,
        ) as MemorySegment
        check(value.address() != 0L) { "CreateEventW failed." }
        return value
    }

    fun setEvent(value: MemorySegment) {
        check((setEvent.invokeWithArguments(value) as Int) != 0) { "SetEvent failed." }
    }

    fun resetEvent(value: MemorySegment) {
        check((resetEvent.invokeWithArguments(value) as Int) != 0) { "ResetEvent failed." }
    }

    fun waitForSingleObject(value: MemorySegment) {
        check((waitForSingleObject.invokeWithArguments(value, infinite) as Int) == waitObject0) {
            "WaitForSingleObject failed."
        }
    }

    fun isSignaled(value: MemorySegment, timeoutMillis: Int): Boolean =
        when (val result = waitForSingleObject.invokeWithArguments(value, timeoutMillis) as Int) {
            waitObject0 -> true
            waitTimeout -> false
            else -> error("WaitForSingleObject failed with result 0x${result.toUInt().toString(16)}.")
        }

    fun waitWithComDispatch(handles: MemorySegment, signaledIndex: MemorySegment) {
        val result = coWaitForMultipleHandles.invokeWithArguments(
            comAwareWaitFlags,
            infinite,
            1,
            handles,
            signaledIndex,
        ) as Int
        check(result >= 0) {
            "CoWaitForMultipleHandles failed with HRESULT 0x${result.toUInt().toString(16).padStart(8, '0')}."
        }
        check(signaledIndex.get(ValueLayout.JAVA_INT, 0L) == 0) {
            "CoWaitForMultipleHandles returned an unexpected handle index."
        }
    }

    fun closeHandle(value: MemorySegment) {
        check((closeHandle.invokeWithArguments(value) as Int) != 0) { "CloseHandle failed." }
    }

    private fun downcall(lookup: SymbolLookup, name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor)
}
