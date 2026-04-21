package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

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
        data class Found(val type: KClass<*>) : TypeLookupResult
        data object Missing : TypeLookupResult
    }

    private val registeredProjectionTypes = ConcurrentCacheMap<String, KClass<*>>()
    private val registeredReferenceArrayTypes = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    private val projectionTypeNameToBaseTypeNameMappings = mutableListOf<Map<String, String>>()
    private val typeNameCache = ConcurrentCacheMap<String, TypeLookupResult>()
    private val baseRcwTypeCache = ConcurrentCacheMap<String, TypeLookupResult>()

    fun registerProjectionAssembly(
        vararg projectionTypes: KClass<*>,
    ) {
        projectionTypes.forEach(::registerProjectionType)
    }

    fun registerProjectionAssembly(
        vararg projectionTypes: Class<*>,
    ) {
        registerProjectionAssembly(*projectionTypes.map { it.kotlin }.toTypedArray())
    }

    fun registerProjectionType(
        type: KClass<*>,
        runtimeClassName: String? = inferRuntimeClassName(type),
    ) {
        typeNameCache.clear()
        baseRcwTypeCache.clear()
        if (runtimeClassName != null) {
            registeredProjectionTypes[runtimeClassName] = type
            WinRtTypeRegistry.registerAlias(type, runtimeClassName)
        }
        registeredProjectionTypes[type.qualifiedName ?: type.simpleName.orEmpty()] = type
        WinRtTypeRegistry.registerAlias(type, type.qualifiedName ?: type.simpleName.orEmpty())
        type.registeredClass().canonicalName?.let { alias ->
            WinRtTypeRegistry.registerAlias(type, alias)
        }
    }

    fun registerProjectionType(
        type: Class<*>,
        runtimeClassName: String? = inferRuntimeClassName(type),
    ) {
        registerProjectionType(type.kotlin, runtimeClassName)
    }

    fun registerProjectionTypes(
        vararg types: Class<*>,
    ) {
        types.forEach(::registerProjectionType)
    }

    fun registerProjectionTypes(
        vararg types: KClass<*>,
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
        baseType: KClass<*>,
    ) {
        baseRcwTypeCache.compute(runtimeClassName) { _, existing ->
            when (existing) {
                is TypeLookupResult.Found ->
                    if (existing.type.registeredClass().isAssignableFrom(baseType.registeredClass())) {
                        TypeLookupResult.Found(baseType)
                    } else {
                        existing
                    }

                else -> TypeLookupResult.Found(baseType)
            }
        }
    }

    fun registerBaseTypeForTypeName(
        runtimeClassName: String,
        baseType: Class<*>,
    ) {
        registerBaseTypeForTypeName(runtimeClassName, baseType.kotlin)
    }

    fun registerReferenceArrayType(
        elementType: KClass<*>,
        arrayType: KClass<*>,
    ) {
        typeNameCache.clear()
        registeredReferenceArrayTypes[elementType] = arrayType
    }

    fun registerReferenceArrayType(
        elementType: Class<*>,
        arrayType: Class<*>,
    ) {
        registerReferenceArrayType(elementType.kotlin, arrayType.kotlin)
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
                        findRcwTypeByNameCached(baseTypeName)?.kotlin?.let(TypeLookupResult::Found)
                    }
                } ?: TypeLookupResult.Missing
            }
        }) {
            is TypeLookupResult.Found -> cached.type.registeredClass()
            TypeLookupResult.Missing -> null
        }
    }

    fun findTypeByNameCached(
        runtimeClassName: String,
    ): Class<*>? =
        when (val cached = typeNameCache.computeIfAbsent(runtimeClassName) { requestedName ->
            resolveTypeByName(requestedName)?.let(TypeLookupResult::Found) ?: TypeLookupResult.Missing
        }) {
            is TypeLookupResult.Found -> cached.type.registeredClass()
            TypeLookupResult.Missing -> null
        }

    fun getNameForType(
        type: Class<*>?,
        flags: Set<TypeNameGenerationFlag> = emptySet(),
    ): String {
        if (type == null) {
            return ""
        }

        if (flags.contains(TypeNameGenerationFlag.GenerateBoxedName)) {
            WinRtValueBoxing.boxedRuntimeClassNameForType(type)?.let { return it }
        }

        WinRtTypeClassifier.classify(type)?.let { return it.canonicalRuntimeName }

        type.registeredWinRtType()?.projectedTypeName
            ?.takeIf { type.registeredWinRtType()?.isWindowsRuntimeType == true || type.registeredWinRtType()?.isRuntimeClass == true }
            ?.let { return it }

        Projections.findCustomAbiTypeNameForType(type)?.let { return it }

        val runtimeClassName = inferRuntimeClassName(type)
        if (runtimeClassName != null) {
            return runtimeClassName
        }

        if (flags.contains(TypeNameGenerationFlag.ForGetRuntimeClassName)) {
            return ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type.kotlin) ?: ""
        }

        return type.name
    }

    internal fun inferRuntimeClassName(
        type: KClass<*>,
    ): String? = inferRuntimeClassName(type.registeredClass())

    internal fun inferRuntimeClassName(
        type: Class<*>,
    ): String? =
        type.registeredWinRtType()?.runtimeClassName
            ?: Projections.findCustomAbiTypeNameForType(type)?.takeIf(Projections::isProjectedRuntimeClassName)
            ?: type.registeredWinRtType()?.takeIf { it.isRuntimeClass }?.projectedTypeName

    internal fun clearRegistriesForTests() {
        registeredProjectionTypes.clear()
        registeredReferenceArrayTypes.clear()
        synchronized(projectionTypeNameToBaseTypeNameMappings) {
            projectionTypeNameToBaseTypeNameMappings.clear()
        }
        typeNameCache.clear()
        baseRcwTypeCache.clear()
    }

    private fun resolveTypeByName(
        runtimeClassName: String,
    ): KClass<*>? {
        WinRtReferenceTypeNames.parseReferenceElement(runtimeClassName)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)
        }
        WinRtReferenceTypeNames.parseReferenceArrayElement(runtimeClassName)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)?.let { arrayClassForElementType(it.registeredClass())?.kotlin }
        }

        Projections.findCustomKClassForAbiTypeName(runtimeClassName)?.let { return it }
        WinRtTypeRegistry.findByName(runtimeClassName)?.let { return it.kClass }
        registeredProjectionTypes[runtimeClassName]?.let { return it }
        WinRtTypeClassifier.resolve(runtimeClassName)?.let { return it.representativeClass.kotlin }

        val genericBaseName = runtimeClassName.substringBefore('<')
        if (genericBaseName != runtimeClassName) {
            Projections.findCustomKClassForAbiTypeName(genericBaseName)?.let { return it }
            WinRtTypeRegistry.findByName(genericBaseName)?.let { return it.kClass }
            registeredProjectionTypes[genericBaseName]?.let { return it }
            WinRtTypeClassifier.resolve(genericBaseName)?.let { return it.representativeClass.kotlin }
        }

        return null
    }

    private fun arrayClassForElementType(
        elementType: Class<*>,
    ): Class<*>? =
        WinRtTypeClassifier.primitiveArrayClassForElementType(elementType)
            ?: registeredReferenceArrayTypes[elementType.kotlin]?.registeredClass()
}
