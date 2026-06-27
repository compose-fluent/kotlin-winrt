package io.github.composefluent.winrt.runtime

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
            WinRTTypeRegistry.registerAlias(type, runtimeClassName)
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
        ensureProjectionMappingsRegistered()

        referenceArrayRuntimeClassName(type)?.let { return it }
        if (type.isUnsupportedErasedArrayType()) {
            return ""
        }

        if (flags.contains(TypeNameGenerationFlag.GenerateBoxedName)) {
            boxedRuntimeClassName(type)?.let { return it }
        }

        WinRTTypeClassifier.classify(type)?.let { return it.canonicalRuntimeName }

        type.registeredWinRTType()?.projectedTypeName
            ?.takeIf { type.registeredWinRTType()?.isWindowsRuntimeType == true || type.registeredWinRTType()?.isRuntimeClass == true }
            ?.let { return it }

        inferRuntimeClassName(type)?.let { return it }

        if (flags.contains(TypeNameGenerationFlag.ForGetRuntimeClassName)) {
            return runtimeClassNameForNonWinRTType(type) ?: ""
        }

        return typeName(type)
    }

    internal fun inferRuntimeClassName(
        type: KClass<*>,
    ): String? =
        type.registeredWinRTType()?.runtimeClassName
            ?: type.registeredWinRTType()?.takeIf { it.isRuntimeClass }?.projectedTypeName

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
        WinRTReferenceTypeNames.parseReferenceElement(runtimeClassName)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)
        }
        WinRTReferenceTypeNames.parseReferenceArrayElement(runtimeClassName)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)?.let(::arrayClassForElementType)
        }

        WinRTTypeRegistry.findByName(runtimeClassName)?.let { return it.kClass }
        registeredProjectionTypes[runtimeClassName]?.let { return it }
        WinRTTypeClassifier.resolve(runtimeClassName)?.let { return it.representativeType }

        val genericBaseName = runtimeClassName.substringBefore('<')
        if (genericBaseName != runtimeClassName) {
            WinRTTypeRegistry.findByName(genericBaseName)?.let { return it.kClass }
            registeredProjectionTypes[genericBaseName]?.let { return it }
            WinRTTypeClassifier.resolve(genericBaseName)?.let { return it.representativeType }
        }

        return null
    }

    private fun arrayClassForElementType(
        elementType: KClass<*>,
    ): KClass<*>? =
        WinRTTypeClassifier.primitiveArrayTypeForElementType(elementType)
            ?: registeredReferenceArrayTypes[elementType]

    private fun referenceArrayRuntimeClassName(type: KClass<*>): String? {
        if (WinRTTypeClassifier.primitiveArrayElementType(type) == null && registeredArrayElementTypes[type] == null) {
            return null
        }
        return WinRTValueBoxing.boxedRuntimeClassNameForType(type)
    }

    // Kotlin common KClass does not preserve Array<T>'s element type. Match CsWinRT's
    // fail-closed array path by returning an empty type name instead of a Kotlin name.
    private fun KClass<*>.isUnsupportedErasedArrayType(): Boolean =
        qualifiedName == "kotlin.Array"
}
