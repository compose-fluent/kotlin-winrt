package io.github.kitectlab.winrt.runtime

import java.util.concurrent.ConcurrentHashMap

/**
 * JVM reflection helpers corresponding to `.cswinrt/src/WinRT.Runtime/TypeExtensions.cs`.
 */
object TypeExtensions {
    private val helperTypeCache = ConcurrentHashMap<Class<*>, Class<*>?>()

    fun findHelperType(
        type: Class<*>,
        throwIfMissing: Boolean = true,
    ): Class<*>? {
        val helper = helperTypeCache.computeIfAbsent(type) { candidate ->
            if (Exception::class.java.isAssignableFrom(candidate)) {
                return@computeIfAbsent findHelperType(Exception::class.java, throwIfMissing = false)
            }

            Projections.findCustomHelperTypeMapping(candidate)?.let { return@computeIfAbsent it }
            candidate.getAnnotation(WindowsRuntimeHelperType::class.java)?.helperType?.java?.let { return@computeIfAbsent it }
            null
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
    ): Class<*> = if (isDelegate(type)) {
        findHelperType(type, throwIfMissing = false) ?: type
    } else {
        type
    }

    fun findVftblType(
        helperType: Class<*>,
    ): Class<*>? = helperType.declaredClasses.firstOrNull { it.simpleName == "Vftbl" }

    fun isDelegate(
        type: Class<*>,
    ): Boolean = type.isAnnotationPresent(WinRtDelegateType::class.java)

    internal fun clearRegistriesForTests() {
        helperTypeCache.clear()
    }
}
