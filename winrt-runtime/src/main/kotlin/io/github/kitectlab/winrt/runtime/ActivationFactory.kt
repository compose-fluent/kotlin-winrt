package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

data class ActivationResult(
    val hResult: HResult,
    val pointer: MemorySegment,
) {
    val isSuccess: Boolean
        get() = hResult.isSuccess && pointer != MemorySegment.NULL
}

object ActivationFactory {
    val iActivationFactoryIid: Guid = IID.IActivationFactory

    fun get(runtimeClassName: String): ActivationFactoryReference {
        val result = tryGet(runtimeClassName, iActivationFactoryIid)
        if (!result.isSuccess) {
            throw WinRtRuntimeException(
                "Activation factory lookup failed for $runtimeClassName with ${result.hResult}",
                result.hResult,
            )
        }
        return ActivationFactoryReference(result.pointer, iActivationFactoryIid)
    }

    fun tryGet(runtimeClassName: String, interfaceId: Guid = iActivationFactoryIid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, MemorySegment.NULL)
        }

        HString.create(runtimeClassName).use { classId ->
            val activationResult = WindowsRuntimePlatform.roGetActivationFactory(classId, interfaceId)
            if (activationResult.isSuccess || activationResult.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
                return activationResult
            }
        }

        return ManifestFreeActivation.tryGet(runtimeClassName, interfaceId)
    }
}

internal object ManifestFreeActivation {
    private const val loadWithAlteredSearchPath = 0x00000008

    fun tryGet(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, MemorySegment.NULL)
        }

        val dllName = candidateDllNames(runtimeClassName).firstOrNull() ?: return failure()
        val module = WindowsRuntimePlatform.loadLibraryExW(dllName, loadWithAlteredSearchPath)
        if (module == MemorySegment.NULL) {
            return failure()
        }

        val entryPoint = WindowsRuntimePlatform.getProcAddress(module, "DllGetActivationFactory")
        if (entryPoint == MemorySegment.NULL) {
            return failure()
        }

        HString.create(runtimeClassName).use { classId ->
            Arena.ofConfined().use { arena ->
                val factoryOut = arena.allocate(ValueLayout.ADDRESS)
                val getActivationFactory = java.lang.foreign.Linker.nativeLinker().downcallHandle(
                    entryPoint,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                )
                val hr = getActivationFactory.invokeWithArguments(
                    classId.handle,
                    factoryOut,
                ) as Int
                return ActivationResult(HResult(hr), factoryOut.get(ValueLayout.ADDRESS, 0))
            }
        }
    }

    internal fun candidateDllNames(runtimeClassName: String): List<String> {
        val parts = runtimeClassName.split('.')
        if (parts.size < 2) {
            return emptyList()
        }

        return buildList {
            for (i in parts.lastIndex downTo 1) {
                add(parts.take(i).joinToString(".") + ".dll")
            }
        }.distinct()
    }

    private fun failure(): ActivationResult =
        ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, MemorySegment.NULL)
}
