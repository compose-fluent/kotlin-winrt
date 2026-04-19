package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap

data class ActivationResult(
    val hResult: HResult,
    val pointer: MemorySegment,
) {
    val isSuccess: Boolean
        get() = hResult.isSuccess && pointer != MemorySegment.NULL
}

object ActivationFactory {
    val iActivationFactoryIid: Guid = IID.IActivationFactory

    private data class CacheKey(
        val runtimeClassName: String,
        val interfaceId: Guid,
    )

    private val cache = ConcurrentHashMap<CacheKey, IUnknownReference>()

    fun get(runtimeClassName: String): ActivationFactoryReference {
        return get(runtimeClassName, iActivationFactoryIid) as ActivationFactoryReference
    }

    fun get(runtimeClassName: String, interfaceId: Guid): IUnknownReference {
        val cached = cache[CacheKey(runtimeClassName, interfaceId)]
        if (cached != null) {
            return cloneCachedReference(cached)
        }

        val result = tryGet(runtimeClassName, interfaceId)
        if (!result.isSuccess) {
            throw WinRtExceptionTranslator.exceptionFor(
                result.hResult,
                "Activation factory lookup for $runtimeClassName",
            )
        }

        val created = wrapFactory(result.pointer, interfaceId)
        val existing = cache.putIfAbsent(CacheKey(runtimeClassName, interfaceId), created)
        if (existing != null) {
            created.close()
            return cloneCachedReference(existing)
        }

        return cloneCachedReference(created)
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

    fun activateInstance(runtimeClassName: String): IInspectableReference =
        get(runtimeClassName).use { it.activateInstance() }

    internal fun cachedFactoryCount(): Int = cache.size

    internal fun clearCacheForTests() {
        clearRuntimeCache()
    }

    internal fun clearRuntimeCache() {
        cache.values.forEach { it.close() }
        cache.clear()
    }

    private fun cloneCachedReference(reference: IUnknownReference): IUnknownReference =
        wrapFactory(reference.getRef(), reference.interfaceId)

    private fun wrapFactory(pointer: MemorySegment, interfaceId: Guid): IUnknownReference =
        if (interfaceId == IID.IActivationFactory) {
            ActivationFactoryReference(pointer, interfaceId)
        } else {
            IUnknownReference(pointer, interfaceId)
        }
}

internal object ManifestFreeActivation {
    fun tryGet(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, MemorySegment.NULL)
        }

        for (dllName in candidateDllNames(runtimeClassName)) {
            val module = DllModule.tryLoad(dllName) ?: continue
            val activationFactoryResult = module.getActivationFactory(runtimeClassName)
            if (!activationFactoryResult.isSuccess) {
                if (activationFactoryResult.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
                    return activationFactoryResult
                }
                continue
            }

            if (interfaceId == IID.IActivationFactory) {
                return activationFactoryResult
            }

            val queried = ActivationFactoryReference(activationFactoryResult.pointer, IID.IActivationFactory).use { factory ->
                factory.queryInterface(interfaceId)
            }
            if (queried.isSuccess) {
                return queried.getOrThrow().use {
                    ActivationResult(KnownHResults.S_OK, it.getRef())
                }
            }

            val error = queried.exceptionOrNull() as? WinRtRuntimeException
            if (error?.hResult != null) {
                return ActivationResult(error.hResult, MemorySegment.NULL)
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
