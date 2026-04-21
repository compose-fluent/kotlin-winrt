package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

/**
 * Shared reflection/type-descriptor helpers corresponding to `.cswinrt/src/WinRT.Runtime/TypeExtensions.cs`.
 */
object TypeExtensions {
    private val helperTypeCache = ConcurrentCacheMap<KClass<*>, KClass<*>?>()
    private val vftblTypeCache = ConcurrentCacheMap<KClass<*>, KClass<*>?>()

    fun findHelperType(
        type: KClass<*>,
        throwIfMissing: Boolean = true,
    ): KClass<*>? {
        val helper = helperTypeCache.computeIfAbsent(type) { candidate ->
            if (isPlatformExceptionType(candidate)) {
                return@computeIfAbsent findHelperType(Exception::class, throwIfMissing = false)
            }

            Projections.findCustomHelperTypeMapping(candidate)
        }

        if (helper == null && throwIfMissing) {
            throw IllegalStateException("Target type is not a projected type: ${type.typeDisplayName()}.")
        }

        return helper
    }

    fun getHelperType(
        type: KClass<*>,
    ): KClass<*> =
        findHelperType(type, throwIfMissing = true)
            ?: error("Unreachable: helper type lookup must either return a type or throw.")

    fun getGuidType(
        type: KClass<*>,
    ): KClass<*> = findHelperType(type, throwIfMissing = false) ?: type

    fun findVftblType(
        helperType: KClass<*>,
    ): KClass<*>? = vftblTypeCache.computeIfAbsent(helperType) { candidate ->
        candidate.registeredWinRtType()?.vftblType
    }

    fun isDelegate(
        type: KClass<*>,
    ): Boolean = type.registeredWinRtType()?.isDelegate == true

    internal fun clearRegistriesForTests() {
        helperTypeCache.clear()
        vftblTypeCache.clear()
    }
}
