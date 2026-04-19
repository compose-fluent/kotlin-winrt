package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

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
    fun tryGet(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, MemorySegment.NULL)
        }

        for (dllName in candidateDllNames(runtimeClassName)) {
            val module = DllModule.tryLoad(dllName) ?: continue
            val activationResult = module.getActivationFactory(runtimeClassName)
            if (activationResult.isSuccess || activationResult.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
                return activationResult
            }
        }

        return failure()
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
