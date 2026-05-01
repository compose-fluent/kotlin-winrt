package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

object WinRtWindowsMessageLoop {
    private const val wmQuit = 0x0012

    private val linker: Linker by lazy { Linker.nativeLinker() }
    private val arena: Arena by lazy { Arena.ofAuto() }
    private val user32: SymbolLookup by lazy { SymbolLookup.libraryLookup("user32", arena) }
    private val kernel32: SymbolLookup by lazy { SymbolLookup.libraryLookup("kernel32", arena) }
    private val msgLayout: MemoryLayout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("hwnd"),
        ValueLayout.JAVA_INT.withName("message"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.ADDRESS.withName("wParam"),
        ValueLayout.ADDRESS.withName("lParam"),
        ValueLayout.JAVA_INT.withName("time"),
        ValueLayout.JAVA_INT.withName("ptX"),
        ValueLayout.JAVA_INT.withName("ptY"),
        ValueLayout.JAVA_INT.withName("lPrivate"),
        MemoryLayout.paddingLayout(4),
    )

    fun run() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        Arena.ofConfined().use { scope ->
            val message = scope.allocate(msgLayout)
            val getMessage = user32Downcall(
                "GetMessageW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                ),
            )
            val translateMessage = user32Downcall(
                "TranslateMessage",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
            )
            val dispatchMessage = user32Downcall(
                "DispatchMessageW",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
            )
            while (true) {
                val result = getMessage.invokeWithArguments(message, MemorySegment.NULL, 0, 0) as Int
                when {
                    result > 0 -> {
                        translateMessage.invokeWithArguments(message)
                        dispatchMessage.invokeWithArguments(message)
                    }
                    result == 0 -> return
                    else -> throw IllegalStateException("GetMessageW failed")
                }
            }
        }
    }

    fun currentThreadId(): Int {
        if (!PlatformRuntime.isWindows) {
            return 0
        }
        val getCurrentThreadId = kernel32Downcall("GetCurrentThreadId", FunctionDescriptor.of(ValueLayout.JAVA_INT))
        return getCurrentThreadId.invokeWithArguments() as Int
    }

    fun postQuit(threadId: Int) {
        if (!PlatformRuntime.isWindows || threadId == 0) {
            return
        }
        val postThreadMessage = user32Downcall(
            "PostThreadMessageW",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
        postThreadMessage.invokeWithArguments(threadId, wmQuit, MemorySegment.NULL, MemorySegment.NULL)
    }

    private fun user32Downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        downcall(user32, name, descriptor)

    private fun kernel32Downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        downcall(kernel32, name, descriptor)

    private fun downcall(lookup: SymbolLookup, name: String, descriptor: FunctionDescriptor): MethodHandle {
        val symbol = lookup.find(name).orElse(null)
        requireNotNull(symbol) { "Win32 symbol not found: $name" }
        return linker.downcallHandle(symbol, descriptor)
    }
}
