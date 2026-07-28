package io.github.composefluent.winrt.samples

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets

internal actual fun winRTSampleOption(name: String): Boolean =
    java.lang.Boolean.getBoolean(name)

internal actual fun winRTSampleOptionConfigured(name: String): Boolean =
    System.getProperty(name) != null

internal actual fun configureWebView2TransparentBackground() {
    val linker = Linker.nativeLinker()
    val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    val setEnvironmentVariable =
        linker.downcallHandle(
            kernel32.find("SetEnvironmentVariableW").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        )
    Arena.ofConfined().use { arena ->
        val result =
            setEnvironmentVariable.invokeWithArguments(
                allocateWideString(arena, WEBVIEW2_DEFAULT_BACKGROUND_COLOR_VARIABLE),
                allocateWideString(arena, "0"),
            ) as Int
        check(result != 0) { "SetEnvironmentVariableW failed for $WEBVIEW2_DEFAULT_BACKGROUND_COLOR_VARIABLE" }
    }
}

private fun allocateWideString(arena: Arena, value: String): MemorySegment {
    val bytes = (value + '\u0000').toByteArray(StandardCharsets.UTF_16LE)
    return arena.allocate(bytes.size.toLong(), 2).copyFrom(MemorySegment.ofArray(bytes))
}
