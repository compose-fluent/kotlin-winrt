package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

internal actual fun allocateWin64ExecutableCode(
    code: ByteArray,
    description: String,
): RawAddress {
    check(PlatformRuntime.isWindows) { "Win64 executable stubs require Windows." }
    val memory = win64ExecutableCodeSupport.virtualAlloc(
        MemorySegment.NULL,
        code.size.toLong(),
        memCommit or memReserve,
        pageReadWrite,
    ) as MemorySegment
    check(memory != MemorySegment.NULL) { "VirtualAllocFromApp failed for $description." }

    try {
        memory.reinterpret(code.size.toLong()).copyFrom(MemorySegment.ofArray(code))
        Arena.ofConfined().use { arena ->
            val oldProtect = arena.allocate(ValueLayout.JAVA_INT)
            check(
                (win64ExecutableCodeSupport.virtualProtect(
                    memory,
                    code.size.toLong(),
                    pageExecuteRead,
                    oldProtect,
                ) as Int) != 0,
            ) { "VirtualProtectFromApp failed for $description. AppContainer hosts require the codeGeneration capability." }
        }
        val process = win64ExecutableCodeSupport.getCurrentProcess() as MemorySegment
        check(
            (win64ExecutableCodeSupport.flushInstructionCache(
                process,
                memory,
                code.size.toLong(),
            ) as Int) != 0,
        ) { "FlushInstructionCache failed for $description." }
    } catch (failure: Throwable) {
        win64ExecutableCodeSupport.virtualFree(memory, 0L, memRelease)
        throw failure
    }
    return RawAddress(memory.address())
}

private object win64ExecutableCodeSupport {
    private val linker = Linker.nativeLinker()
    private val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    // WindowsApp.lib imports these functions from this API set, not kernel32's exports.
    // FromApp also supports desktop; AppContainer RX requires the codeGeneration capability.
    private val appMemory = SymbolLookup.libraryLookup("api-ms-win-core-memory-l1-1-3.dll", Arena.global())

    val virtualAlloc: MethodHandle = downcall(
        "VirtualAllocFromApp",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ),
        appMemory,
    )
    val virtualProtect: MethodHandle = downcall(
        "VirtualProtectFromApp",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        ),
        appMemory,
    )
    val flushInstructionCache: MethodHandle = downcall(
        "FlushInstructionCache",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
        ),
    )
    val getCurrentProcess: MethodHandle = downcall(
        "GetCurrentProcess",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    val virtualFree: MethodHandle = downcall(
        "VirtualFree",
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
        ),
    )

    private fun downcall(
        name: String,
        descriptor: FunctionDescriptor,
        library: SymbolLookup = kernel32,
    ): MethodHandle = linker.downcallHandle(
        library.find(name).orElseThrow { UnsatisfiedLinkError("Missing Windows executable-memory entry point: $name") },
        descriptor,
    )
}

private const val memCommit = 0x00001000
private const val memReserve = 0x00002000
private const val memRelease = 0x00008000
private const val pageReadWrite = 0x04
private const val pageExecuteRead = 0x20
