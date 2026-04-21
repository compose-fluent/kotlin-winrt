package io.github.kitectlab.winrt.runtime

/**
 * JVM reflection helpers corresponding to `.cswinrt/src/WinRT.Runtime/TypeExtensions.cs`.
 */
object TypeExtensions {
    private val helperTypeCache = ConcurrentCacheMap<Class<*>, Class<*>?>()
    private val vftblTypeCache = ConcurrentCacheMap<Class<*>, Class<*>?>()

    fun findHelperType(
        type: Class<*>,
        throwIfMissing: Boolean = true,
    ): Class<*>? {
        val helper = helperTypeCache.computeIfAbsent(type) { candidate ->
            if (Exception::class.java.isAssignableFrom(candidate)) {
                return@computeIfAbsent findHelperType(Exception::class.java, throwIfMissing = false)
            }

            Projections.findCustomHelperTypeMapping(candidate)
        }

        if (helper == null && throwIfMissing) {
            throw IllegalStateException("Target type is not a projected type: ${type.name}.")
        }

        return helper
    }

    fun getHelperType(
        type: Class<*>,
    ): Class<*> = findHelperType(type, throwIfMissing = true)
        ?: error("Unreachable: helper type lookup must either return a type or throw.")

    fun getGuidType(
        type: Class<*>,
    ): Class<*> = findHelperType(type, throwIfMissing = false) ?: type

    fun findVftblType(
        helperType: Class<*>,
    ): Class<*>? = vftblTypeCache.computeIfAbsent(helperType) { candidate ->
        candidate.registeredWinRtType()?.vftblType?.registeredClass()
    }

    fun isDelegate(
        type: Class<*>,
    ): Boolean = type.registeredWinRtType()?.isDelegate == true

    internal fun clearRegistriesForTests() {
        helperTypeCache.clear()
        vftblTypeCache.clear()
    }
}
