package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

enum class TypeNameGenerationFlag {
    GenerateBoxedName,
    ForGetRuntimeClassName,
}

/**
 * Shared runtime-class/type-name lookup layer corresponding to `.cswinrt/src/WinRT.Runtime/TypeNameSupport.cs`.
 */
object TypeNameSupport {
    private sealed interface TypeLookupResult {
        data class Found(val type: KClass<*>) : TypeLookupResult

        data object Missing : TypeLookupResult
    }

    private val registeredProjectionTypes = ConcurrentCacheMap<String, KClass<*>>()
    private val registeredReferenceArrayTypes = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    /** Reverse mapping: arrayType → elementType, populated alongside [registeredReferenceArrayTypes]. */
    private val registeredArrayElementTypes = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    private val projectionTypeNameToBaseTypeNameMappingsLock = PlatformLock()
    private val projectionTypeNameToBaseTypeNameMappings = mutableListOf<Map<String, String>>()
    private val typeNameCache = ConcurrentCacheMap<String, TypeLookupResult>()
    private val baseRcwTypeCache = ConcurrentCacheMap<String, TypeLookupResult>()

    fun registerProjectionAssembly(
        vararg projectionTypes: KClass<*>,
    ) {
        projectionTypes.forEach(::registerProjectionType)
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
        val typeName = type.typeDisplayName()
        registeredProjectionTypes[typeName] = type
        WinRtTypeRegistry.registerAlias(type, typeName)
        typeCanonicalName(type)?.let { alias ->
            WinRtTypeRegistry.registerAlias(type, alias)
        }
    }

    fun registerProjectionTypes(
        vararg types: KClass<*>,
    ) {
        types.forEach(::registerProjectionType)
    }

    fun registerProjectionTypeBaseTypeMapping(
        typeNameToBaseTypeNameMapping: Map<String, String>,
    ) {
        projectionTypeNameToBaseTypeNameMappingsLock.withLock {
            projectionTypeNameToBaseTypeNameMappings += typeNameToBaseTypeNameMapping.toMap()
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
                    if (isAssignableFrom(existing.type, baseType)) {
                        TypeLookupResult.Found(baseType)
                    } else {
                        existing
                    }

                else -> TypeLookupResult.Found(baseType)
            }
        }
    }

    fun registerReferenceArrayType(
        elementType: KClass<*>,
        arrayType: KClass<*>,
    ) {
        typeNameCache.clear()
        registeredReferenceArrayTypes[elementType] = arrayType
        registeredArrayElementTypes[arrayType] = elementType
    }

    /** Returns the element type if [arrayType] was registered via [registerReferenceArrayType], else null. */
    fun registeredArrayElementType(arrayType: KClass<*>): KClass<*>? =
        registeredArrayElementTypes[arrayType]

    internal fun findRcwKClassByNameCached(
        runtimeClassName: String,
    ): KClass<*>? {
        val direct = findKClassByNameCached(runtimeClassName)
        if (direct != null) {
            return direct
        }

        return when (val cached = baseRcwTypeCache.computeIfAbsent(runtimeClassName) { missingName ->
            projectionTypeNameToBaseTypeNameMappingsLock.withLock {
                projectionTypeNameToBaseTypeNameMappings.firstNotNullOfOrNull { mapping ->
                    mapping[missingName]?.let { baseTypeName ->
                        findRcwKClassByNameCached(baseTypeName)?.let(TypeLookupResult::Found)
                    }
                } ?: TypeLookupResult.Missing
            }
        }) {
            is TypeLookupResult.Found -> cached.type
            TypeLookupResult.Missing -> null
        }
    }

    internal fun findKClassByNameCached(
        runtimeClassName: String,
    ): KClass<*>? =
        when (val cached = typeNameCache.computeIfAbsent(runtimeClassName) { requestedName ->
            resolveTypeByName(requestedName)?.let(TypeLookupResult::Found) ?: TypeLookupResult.Missing
        }) {
            is TypeLookupResult.Found -> cached.type
            TypeLookupResult.Missing -> null
        }

    fun getNameForType(
        type: KClass<*>?,
        flags: Set<TypeNameGenerationFlag> = emptySet(),
    ): String {
        if (type == null) {
            return ""
        }

        if (flags.contains(TypeNameGenerationFlag.GenerateBoxedName)) {
            boxedRuntimeClassName(type)?.let { return it }
        }

        WinRtTypeClassifier.classify(type)?.let { return it.canonicalRuntimeName }

        type.registeredWinRtType()?.projectedTypeName
            ?.takeIf { type.registeredWinRtType()?.isWindowsRuntimeType == true || type.registeredWinRtType()?.isRuntimeClass == true }
            ?.let { return it }

        Projections.findCustomAbiTypeNameForType(type)?.let { return it }

        inferRuntimeClassName(type)?.let { return it }

        if (flags.contains(TypeNameGenerationFlag.ForGetRuntimeClassName)) {
            return runtimeClassNameForNonWinRtType(type) ?: ""
        }

        return typeName(type)
    }

    internal fun inferRuntimeClassName(
        type: KClass<*>,
    ): String? =
        type.registeredWinRtType()?.runtimeClassName
            ?: Projections.findCustomAbiTypeNameForType(type)?.takeIf(Projections::isProjectedRuntimeClassName)
            ?: type.registeredWinRtType()?.takeIf { it.isRuntimeClass }?.projectedTypeName

    internal fun clearRegistriesForTests() {
        registeredProjectionTypes.clear()
        registeredReferenceArrayTypes.clear()
        registeredArrayElementTypes.clear()
        projectionTypeNameToBaseTypeNameMappingsLock.withLock {
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
            return resolveTypeByName(elementTypeName)?.let(::arrayClassForElementType)
        }

        Projections.findCustomKClassForAbiTypeName(runtimeClassName)?.let { return it }
        WinRtTypeRegistry.findByName(runtimeClassName)?.let { return it.kClass }
        registeredProjectionTypes[runtimeClassName]?.let { return it }
        WinRtTypeClassifier.resolve(runtimeClassName)?.let { return it.representativeType }

        val genericBaseName = runtimeClassName.substringBefore('<')
        if (genericBaseName != runtimeClassName) {
            Projections.findCustomKClassForAbiTypeName(genericBaseName)?.let { return it }
            WinRtTypeRegistry.findByName(genericBaseName)?.let { return it.kClass }
            registeredProjectionTypes[genericBaseName]?.let { return it }
            WinRtTypeClassifier.resolve(genericBaseName)?.let { return it.representativeType }
        }

        return null
    }

    private fun arrayClassForElementType(
        elementType: KClass<*>,
    ): KClass<*>? =
        WinRtTypeClassifier.primitiveArrayTypeForElementType(elementType)
            ?: registeredReferenceArrayTypes[elementType]
}
