package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

/**
 * Shared projection/type-mapping registry corresponding to `.cswinrt/src/WinRT.Runtime/Projections.cs`.
 */
object Projections {
    private val customTypeToHelperTypeMappings = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    private val customAbiTypeToTypeMappings = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    private val customAbiTypeNameToTypeMappings = ConcurrentCacheMap<String, KClass<*>>()
    private val customTypeToAbiTypeNameMappings = ConcurrentCacheMap<KClass<*>, String>()
    private val projectedRuntimeClassNames = ConcurrentCacheSet<String>()
    private val projectedCustomTypeRuntimeClasses = ConcurrentCacheSet<KClass<*>>()
    private val runtimeClassToDefaultInterfaceMappings = ConcurrentCacheMap<KClass<*>, KClass<*>>()
    private val isTypeWindowsRuntimeTypeCache = ConcurrentCacheMap<KClass<*>, Boolean>()

    init {
        ensureProjectionMappingsRegistered()
    }

    fun registerCustomHelperTypeMapping(
        publicType: KClass<*>,
        helperType: KClass<*>,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        clearDerivedCaches()
        registerTypeDescriptor(
            type = publicType,
            projectedTypeName = customTypeToAbiTypeNameMappings[publicType] ?: (publicType.registeredWinRtType()?.projectedTypeName ?: publicType.typeDisplayName()),
            helperType = helperType,
            runtimeClassName = publicType.registeredWinRtType()?.runtimeClassName,
            defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
            isRuntimeClass = projectedCustomTypeRuntimeClasses.contains(publicType),
            isWindowsRuntimeType = true,
        )
        return customTypeToHelperTypeMappings.putIfAbsent(publicType, helperType) == null
    }

    fun registerCustomAbiTypeMapping(
        publicType: KClass<*>,
        helperType: KClass<*>,
        abiTypeName: String,
        isRuntimeClass: Boolean = false,
    ): Boolean {
        ensureProjectionMappingsRegistered()
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
        runtimeClass: KClass<*>,
        defaultInterface: KClass<*>,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        registerTypeDescriptor(
            type = runtimeClass,
            projectedTypeName = customTypeToAbiTypeNameMappings[runtimeClass] ?: (runtimeClass.registeredWinRtType()?.projectedTypeName ?: runtimeClass.typeDisplayName()),
            helperType = findCustomHelperTypeMapping(runtimeClass),
            runtimeClassName = inferRuntimeClassName(runtimeClass) ?: runtimeClass.registeredWinRtType()?.runtimeClassName,
            defaultInterface = defaultInterface,
            isRuntimeClass = projectedCustomTypeRuntimeClasses.contains(runtimeClass),
            isWindowsRuntimeType = true,
        )
        return runtimeClassToDefaultInterfaceMappings.putIfAbsent(runtimeClass, defaultInterface) == null
    }

    fun findCustomHelperTypeMapping(
        publicType: KClass<*>,
        filterToRuntimeClass: Boolean = false,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        if (filterToRuntimeClass && !projectedCustomTypeRuntimeClasses.contains(publicType)) {
            return null
        }

        publicType.registeredWinRtType()?.helperType?.let { return it }
        customTypeToHelperTypeMappings[publicType]?.let { return it }
        return null
    }

    fun findCustomPublicTypeForAbiType(
        abiType: KClass<*>,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        return customAbiTypeToTypeMappings[abiType]
    }

    internal fun findCustomKClassForAbiTypeName(
        abiTypeName: String,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        return customAbiTypeNameToTypeMappings[abiTypeName]
    }

    fun findCustomAbiTypeNameForType(
        type: KClass<*>,
    ): String? {
        ensureProjectionMappingsRegistered()
        return customTypeToAbiTypeNameMappings[type]
    }

    fun isTypeWindowsRuntimeType(
        type: KClass<*>,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        return isTypeWindowsRuntimeTypeCache.computeIfAbsent(type) { candidate ->
            isTypeWindowsRuntimeTypeNoArray(candidate)
        }
    }

    fun tryGetDefaultInterfaceTypeForRuntimeClassType(
        runtimeClass: KClass<*>,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        runtimeClass.registeredWinRtType()?.defaultInterface?.let { return it }
        runtimeClassToDefaultInterfaceMappings[runtimeClass]?.let { return it }
        return null
    }

    internal fun isProjectedRuntimeClassName(
        runtimeClassName: String,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        return projectedRuntimeClassNames.contains(runtimeClassName)
    }

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
        ValueBoxingMetadata.clearDynamicDescriptorsForTests()
        ValueBoxingInterop.clearDynamicAdaptersForTests()
        clearProjectionMappingsForTests()
        ensureProjectionMappingsRegistered()
    }

    private fun isTypeWindowsRuntimeTypeNoArray(type: KClass<*>): Boolean {
        val candidate = arrayElementType(type) ?: type
        if (WinRtTypeClassifier.isIntrinsicWindowsRuntimeType(candidate)) {
            return true
        }

        return customTypeToAbiTypeNameMappings.containsKey(candidate) ||
            candidate.registeredWinRtType()?.isWindowsRuntimeType == true ||
            candidate.registeredWinRtType()?.isRuntimeClass == true
    }

    private fun clearDerivedCaches() {
        isTypeWindowsRuntimeTypeCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerTypeDescriptor(
        type: KClass<*>,
        projectedTypeName: String,
        helperType: KClass<*>?,
        runtimeClassName: String?,
        defaultInterface: KClass<*>?,
        isRuntimeClass: Boolean,
        isWindowsRuntimeType: Boolean,
    ) {
        val kClass = type as KClass<Any>
        WinRtTypeRegistry.update(kClass) { existing ->
            WinRtTypeId(
                kClass = kClass,
                projectedTypeName = projectedTypeName,
                guid = existing?.guid,
                iid = existing?.iid,
                signature = existing?.signature,
                enumAbiValue = existing?.enumAbiValue,
                helperType = helperType ?: existing?.helperType,
                defaultInterface = defaultInterface ?: existing?.defaultInterface,
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

    private fun inferRuntimeClassName(type: KClass<*>): String? =
        type.registeredWinRtType()?.runtimeClassName
            ?: findCustomAbiTypeNameForType(type)?.takeIf(::isProjectedRuntimeClassName)
            ?: type.registeredWinRtType()?.takeIf { it.isRuntimeClass }?.projectedTypeName
}
