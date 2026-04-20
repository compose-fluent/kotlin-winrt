package io.github.kitectlab.winrt.runtime

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
    private const val REFERENCE_RUNTIME_NAME_PREFIX = "Windows.Foundation.IReference`1<"
    private const val REFERENCE_ARRAY_RUNTIME_NAME_PREFIX = "Windows.Foundation.IReferenceArray`1<"

    private sealed interface TypeLookupResult {
        data class Found(val type: Class<*>) : TypeLookupResult
        data object Missing : TypeLookupResult
    }

    private val registeredProjectionTypes = ConcurrentCacheMap<String, Class<*>>()
    private val registeredReferenceArrayTypes = ConcurrentCacheMap<Class<*>, Class<*>>()
    private val projectionTypeNameToBaseTypeNameMappings = mutableListOf<Map<String, String>>()
    private val typeNameCache = ConcurrentCacheMap<String, TypeLookupResult>()
    private val baseRcwTypeCache = ConcurrentCacheMap<String, TypeLookupResult>()

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
            WinRtTypeRegistry.registerAlias(type.kotlin, runtimeClassName)
        }
        registeredProjectionTypes[type.name] = type
        WinRtTypeRegistry.registerAlias(type.kotlin, type.name)
        type.canonicalName?.let { alias ->
            WinRtTypeRegistry.registerAlias(type.kotlin, alias)
        }
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

    fun registerReferenceArrayType(
        elementType: Class<*>,
        arrayType: Class<*>,
    ) {
        typeNameCache.clear()
        registeredReferenceArrayTypes[elementType] = arrayType
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
            return ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type) ?: ""
        }

        return type.name
    }

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
    ): Class<*>? {
        parseSingleGenericArgument(runtimeClassName, REFERENCE_RUNTIME_NAME_PREFIX)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)
        }
        parseSingleGenericArgument(runtimeClassName, REFERENCE_ARRAY_RUNTIME_NAME_PREFIX)?.let { elementTypeName ->
            return resolveTypeByName(elementTypeName)?.let(::arrayClassForElementType)
        }

        Projections.findCustomTypeForAbiTypeName(runtimeClassName)?.let { return it }
        WinRtTypeRegistry.findByName(runtimeClassName)?.let { return it.registeredClass() }
        registeredProjectionTypes[runtimeClassName]?.let { return it }
        WinRtTypeClassifier.resolve(runtimeClassName)?.let { return it.representativeClass }

        val genericBaseName = runtimeClassName.substringBefore('<')
        if (genericBaseName != runtimeClassName) {
            Projections.findCustomTypeForAbiTypeName(genericBaseName)?.let { return it }
            WinRtTypeRegistry.findByName(genericBaseName)?.let { return it.registeredClass() }
            registeredProjectionTypes[genericBaseName]?.let { return it }
            WinRtTypeClassifier.resolve(genericBaseName)?.let { return it.representativeClass }
        }

        return null
    }

    private fun parseSingleGenericArgument(
        runtimeClassName: String,
        prefix: String,
    ): String? =
        if (runtimeClassName.startsWith(prefix) && runtimeClassName.endsWith(">")) {
            runtimeClassName.substring(prefix.length, runtimeClassName.length - 1)
        } else {
            null
        }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun arrayClassForElementType(
        elementType: Class<*>,
    ): Class<*>? =
        when (elementType) {
            java.lang.Byte.TYPE,
            java.lang.Byte::class.java,
            -> ByteArray::class.java

            UByte::class.java -> UByteArray::class.java

            java.lang.Short.TYPE,
            java.lang.Short::class.java,
            -> ShortArray::class.java

            UShort::class.java -> UShortArray::class.java

            java.lang.Integer.TYPE,
            java.lang.Integer::class.java,
            -> IntArray::class.java

            UInt::class.java -> UIntArray::class.java

            java.lang.Long.TYPE,
            java.lang.Long::class.java,
            -> LongArray::class.java

            ULong::class.java -> ULongArray::class.java

            java.lang.Float.TYPE,
            java.lang.Float::class.java,
            -> FloatArray::class.java

            java.lang.Double.TYPE,
            java.lang.Double::class.java,
            -> DoubleArray::class.java

            java.lang.Boolean.TYPE,
            java.lang.Boolean::class.java,
            -> BooleanArray::class.java

            java.lang.Character.TYPE,
            java.lang.Character::class.java,
            -> CharArray::class.java

            else -> registeredReferenceArrayTypes[elementType]
        }
}
