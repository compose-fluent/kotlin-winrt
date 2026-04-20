package io.github.kitectlab.winrt.runtime

import java.util.concurrent.ConcurrentHashMap

enum class TypeNameGenerationFlag {
    GenerateBoxedName,
    ForGetRuntimeClassName,
}

/**
 * JVM runtime-class/type-name lookup layer corresponding to `.cswinrt/src/WinRT.Runtime/TypeNameSupport.cs`.
 *
 * The .NET implementation can search loaded projection assemblies directly because projected type names
 * remain loadable CLR names. The JVM path uses explicit type registration for WinRT runtime class names
 * instead, which is the narrow platform-specific deviation required to keep the same responsibility split.
 */
object TypeNameSupport {
    private sealed interface TypeLookupResult {
        data class Found(val type: Class<*>) : TypeLookupResult
        data object Missing : TypeLookupResult
    }

    private val registeredProjectionTypes = ConcurrentHashMap<String, Class<*>>()
    private val projectionTypeNameToBaseTypeNameMappings = mutableListOf<Map<String, String>>()
    private val typeNameCache = ConcurrentHashMap<String, TypeLookupResult>()
    private val baseRcwTypeCache = ConcurrentHashMap<String, TypeLookupResult>()

    fun registerProjectionAssembly(
        vararg projectionTypes: Class<*>,
    ) {
        projectionTypes.forEach(::registerProjectionType)
    }

    fun registerProjectionType(
        type: Class<*>,
        runtimeClassName: String? = inferRuntimeClassName(type),
    ) {
        typeNameCache.clear()
        baseRcwTypeCache.clear()
        if (runtimeClassName != null) {
            registeredProjectionTypes[runtimeClassName] = type
        }
        registeredProjectionTypes[type.name] = type
    }

    fun registerProjectionTypes(
        vararg types: Class<*>,
    ) {
        types.forEach(::registerProjectionType)
    }

    fun registerProjectionTypeBaseTypeMapping(
        typeNameToBaseTypeNameMapping: Map<String, String>,
    ) {
        synchronized(projectionTypeNameToBaseTypeNameMappings) {
            projectionTypeNameToBaseTypeNameMappings += LinkedHashMap(typeNameToBaseTypeNameMapping)
        }
        baseRcwTypeCache.clear()
    }

    fun registerBaseTypeForTypeName(
        runtimeClassName: String,
        baseType: Class<*>,
    ) {
        baseRcwTypeCache.compute(runtimeClassName) { _, existing ->
            when (existing) {
                is TypeLookupResult.Found ->
                    if (existing.type.isAssignableFrom(baseType)) {
                        TypeLookupResult.Found(baseType)
                    } else {
                        existing
                    }

                else -> TypeLookupResult.Found(baseType)
            }
        }
    }

    fun findRcwTypeByNameCached(
        runtimeClassName: String,
    ): Class<*>? {
        val direct = findTypeByNameCached(runtimeClassName)
        if (direct != null) {
            return direct
        }

        return when (val cached = baseRcwTypeCache.computeIfAbsent(runtimeClassName) { missingName ->
            synchronized(projectionTypeNameToBaseTypeNameMappings) {
                projectionTypeNameToBaseTypeNameMappings.firstNotNullOfOrNull { mapping ->
                    mapping[missingName]?.let { baseTypeName ->
                        findRcwTypeByNameCached(baseTypeName)?.let(TypeLookupResult::Found)
                    }
                } ?: TypeLookupResult.Missing
            }
        }) {
            is TypeLookupResult.Found -> cached.type
            TypeLookupResult.Missing -> null
        }
    }

    fun findTypeByNameCached(
        runtimeClassName: String,
    ): Class<*>? =
        when (val cached = typeNameCache.computeIfAbsent(runtimeClassName) { requestedName ->
            resolveTypeByName(requestedName)?.let(TypeLookupResult::Found) ?: TypeLookupResult.Missing
        }) {
            is TypeLookupResult.Found -> cached.type
            TypeLookupResult.Missing -> null
        }

    fun getNameForType(
        type: Class<*>?,
        flags: Set<TypeNameGenerationFlag> = emptySet(),
    ): String {
        if (type == null) {
            return ""
        }

        WinRtTypeClassifier.classify(type)?.let { return it.canonicalRuntimeName }

        Projections.findCustomAbiTypeNameForType(type)?.let { return it }

        val runtimeClassName = inferRuntimeClassName(type)
        if (runtimeClassName != null) {
            return runtimeClassName
        }

        if (flags.contains(TypeNameGenerationFlag.ForGetRuntimeClassName)) {
            return ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type) ?: ""
        }

        return type.name
    }

    internal fun inferRuntimeClassName(
        type: Class<*>,
    ): String? {
        type.getAnnotation(WinRtRuntimeClassName::class.java)?.runtimeClassName?.let { return it }
        Projections.findCustomAbiTypeNameForType(type)?.let { abiTypeName ->
            if (Projections.isProjectedRuntimeClassName(abiTypeName)) {
                return abiTypeName
            }
        }
        return null
    }

    internal fun clearRegistriesForTests() {
        registeredProjectionTypes.clear()
        synchronized(projectionTypeNameToBaseTypeNameMappings) {
            projectionTypeNameToBaseTypeNameMappings.clear()
        }
        typeNameCache.clear()
        baseRcwTypeCache.clear()
    }

    private fun resolveTypeByName(
        runtimeClassName: String,
    ): Class<*>? {
        Projections.findCustomTypeForAbiTypeName(runtimeClassName)?.let { return it }
        registeredProjectionTypes[runtimeClassName]?.let { return it }
        WinRtTypeClassifier.resolve(runtimeClassName)?.let { return it.representativeClass }

        val genericBaseName = runtimeClassName.substringBefore('<')
        if (genericBaseName != runtimeClassName) {
            Projections.findCustomTypeForAbiTypeName(genericBaseName)?.let { return it }
            registeredProjectionTypes[genericBaseName]?.let { return it }
            WinRtTypeClassifier.resolve(genericBaseName)?.let { return it.representativeClass }
        }

        return runCatching { Class.forName(runtimeClassName) }.getOrNull()
            ?: if (genericBaseName != runtimeClassName) {
                runCatching { Class.forName(genericBaseName) }.getOrNull()
            } else {
                null
            }
    }
}
