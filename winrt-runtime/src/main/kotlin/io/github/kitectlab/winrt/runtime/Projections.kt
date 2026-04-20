package io.github.kitectlab.winrt.runtime

/**
 * JVM-side projection/type-mapping registry corresponding to `.cswinrt/src/WinRT.Runtime/Projections.cs`.
 *
 * Unlike the .NET path, JVM projected types do not preserve WinRT runtime class names as loadable
 * binary names. The registry therefore owns the same mapping responsibilities, but stores explicit
 * type registrations instead of relying on assembly type lookup.
 */
object Projections {
    private val customTypeToHelperTypeMappings = ConcurrentCacheMap<Class<*>, Class<*>>()
    private val customAbiTypeToTypeMappings = ConcurrentCacheMap<Class<*>, Class<*>>()
    private val customAbiTypeNameToTypeMappings = ConcurrentCacheMap<String, Class<*>>()
    private val customTypeToAbiTypeNameMappings = ConcurrentCacheMap<Class<*>, String>()
    private val projectedRuntimeClassNames = ConcurrentCacheSet<String>()
    private val projectedCustomTypeRuntimeClasses = ConcurrentCacheSet<Class<*>>()
    private val runtimeClassToDefaultInterfaceMappings = ConcurrentCacheMap<Class<*>, Class<*>>()
    private val isTypeWindowsRuntimeTypeCache = ConcurrentCacheMap<Class<*>, Boolean>()

    init {
        WinRtBuiltInProjectionMappings.register()
    }

    fun registerCustomHelperTypeMapping(
        publicType: Class<*>,
        helperType: Class<*>,
    ): Boolean {
        clearDerivedCaches()
        registerTypeDescriptor(
            type = publicType,
            projectedTypeName = customTypeToAbiTypeNameMappings[publicType] ?: (publicType.registeredWinRtType()?.projectedTypeName ?: publicType.name),
            helperType = helperType,
            runtimeClassName = publicType.registeredWinRtType()?.runtimeClassName,
            defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
            isRuntimeClass = projectedCustomTypeRuntimeClasses.contains(publicType),
            isWindowsRuntimeType = true,
        )
        return customTypeToHelperTypeMappings.putIfAbsent(publicType, helperType) == null
    }

    fun registerCustomAbiTypeMapping(
        publicType: Class<*>,
        helperType: Class<*>,
        abiTypeName: String,
        isRuntimeClass: Boolean = false,
    ): Boolean {
        clearDerivedCaches()
        val helperAdded = customTypeToHelperTypeMappings.putIfAbsent(publicType, helperType) == null
        val publicAdded = customAbiTypeToTypeMappings.putIfAbsent(helperType, publicType) == null
        val abiNameAdded = customAbiTypeNameToTypeMappings.putIfAbsent(abiTypeName, publicType) == null
        val typeNameAdded = customTypeToAbiTypeNameMappings.putIfAbsent(publicType, abiTypeName) == null
        if (isRuntimeClass) {
            projectedRuntimeClassNames.add(abiTypeName)
            projectedCustomTypeRuntimeClasses.add(publicType)
        }
        registerTypeDescriptor(
            type = publicType,
            projectedTypeName = abiTypeName,
            helperType = helperType,
            runtimeClassName = if (isRuntimeClass) abiTypeName else publicType.registeredWinRtType()?.runtimeClassName,
            defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
            isRuntimeClass = isRuntimeClass,
            isWindowsRuntimeType = true,
        )
        return helperAdded && publicAdded && abiNameAdded && typeNameAdded
    }

    fun registerDefaultInterfaceType(
        runtimeClass: Class<*>,
        defaultInterface: Class<*>,
    ): Boolean {
        registerTypeDescriptor(
            type = runtimeClass,
            projectedTypeName = customTypeToAbiTypeNameMappings[runtimeClass] ?: (runtimeClass.registeredWinRtType()?.projectedTypeName ?: runtimeClass.name),
            helperType = findCustomHelperTypeMapping(runtimeClass),
            runtimeClassName = inferRuntimeClassName(runtimeClass) ?: runtimeClass.registeredWinRtType()?.runtimeClassName,
            defaultInterface = defaultInterface,
            isRuntimeClass = projectedCustomTypeRuntimeClasses.contains(runtimeClass),
            isWindowsRuntimeType = true,
        )
        return runtimeClassToDefaultInterfaceMappings.putIfAbsent(runtimeClass, defaultInterface) == null
    }

    fun findCustomHelperTypeMapping(
        publicType: Class<*>,
        filterToRuntimeClass: Boolean = false,
    ): Class<*>? {
        if (filterToRuntimeClass && !projectedCustomTypeRuntimeClasses.contains(publicType)) {
            return null
        }

        publicType.registeredWinRtType()?.helperType?.registeredClass()?.let { return it }
        customTypeToHelperTypeMappings[publicType]?.let { return it }
        return null
    }

    fun findCustomPublicTypeForAbiType(
        abiType: Class<*>,
    ): Class<*>? = customAbiTypeToTypeMappings[abiType]

    fun findCustomTypeForAbiTypeName(
        abiTypeName: String,
    ): Class<*>? = customAbiTypeNameToTypeMappings[abiTypeName]

    fun findCustomAbiTypeNameForType(
        type: Class<*>,
    ): String? = customTypeToAbiTypeNameMappings[type]

    fun isTypeWindowsRuntimeType(
        type: Class<*>,
    ): Boolean = isTypeWindowsRuntimeTypeCache.computeIfAbsent(type) { candidate ->
        isTypeWindowsRuntimeTypeNoArray(
            if (candidate.isArray) {
                candidate.componentType
            } else {
                candidate
            },
        )
    }

    fun tryGetDefaultInterfaceTypeForRuntimeClassType(
        runtimeClass: Class<*>,
    ): Class<*>? {
        runtimeClass.registeredWinRtType()?.defaultInterface?.registeredClass()?.let { return it }
        runtimeClassToDefaultInterfaceMappings[runtimeClass]?.let { return it }
        return null
    }

    internal fun isProjectedRuntimeClassName(
        runtimeClassName: String,
    ): Boolean = projectedRuntimeClassNames.contains(runtimeClassName)

    internal fun clearRegistriesForTests() {
        customTypeToHelperTypeMappings.clear()
        customAbiTypeToTypeMappings.clear()
        customAbiTypeNameToTypeMappings.clear()
        customTypeToAbiTypeNameMappings.clear()
        projectedRuntimeClassNames.clear()
        projectedCustomTypeRuntimeClasses.clear()
        runtimeClassToDefaultInterfaceMappings.clear()
        isTypeWindowsRuntimeTypeCache.clear()
        WinRtTypeRegistry.clearForTests()
        WinRtBuiltInProjectionMappings.register()
    }

    private fun isTypeWindowsRuntimeTypeNoArray(type: Class<*>): Boolean {
        if (WinRtTypeClassifier.isIntrinsicWindowsRuntimeType(type)) return true

        return customTypeToAbiTypeNameMappings.containsKey(type) ||
            type.registeredWinRtType()?.isWindowsRuntimeType == true ||
            type.registeredWinRtType()?.isRuntimeClass == true
    }

    private fun clearDerivedCaches() {
        isTypeWindowsRuntimeTypeCache.clear()
    }

    private fun registerTypeDescriptor(
        type: Class<*>,
        projectedTypeName: String,
        helperType: Class<*>?,
        runtimeClassName: String?,
        defaultInterface: Class<*>?,
        isRuntimeClass: Boolean,
        isWindowsRuntimeType: Boolean,
    ) {
        val kClass = type.registeredKClass()
        WinRtTypeRegistry.update(kClass) { existing ->
            WinRtTypeId(
                kClass = kClass,
                projectedTypeName = projectedTypeName,
                guid = existing?.guid,
                iid = existing?.iid,
                signature = existing?.signature,
                enumAbiValue = existing?.enumAbiValue,
                helperType = helperType?.registeredKClass() ?: existing?.helperType,
                defaultInterface = defaultInterface?.registeredKClass() ?: existing?.defaultInterface,
                boxedName = existing?.boxedName,
                runtimeClassName = runtimeClassName ?: existing?.runtimeClassName,
                vftblType = existing?.vftblType,
                isDelegate = existing?.isDelegate == true,
                isRuntimeClass = isRuntimeClass || existing?.isRuntimeClass == true,
                isWindowsRuntimeType = isWindowsRuntimeType || existing?.isWindowsRuntimeType == true,
                aliases = existing?.aliases.orEmpty(),
            )
        }
    }

    private fun inferRuntimeClassName(type: Class<*>): String? = TypeNameSupport.inferRuntimeClassName(type)
}
