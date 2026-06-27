package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * Shared projection/type-mapping registry corresponding to `.cswinrt/src/WinRT.Runtime/Projections.cs`.
 */
object Projections {
    private val customTypeToHelperTypeMappings = ConcurrentCacheMap<KClass<*>, KClass<*>>()
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
        explicitProjectedTypeName(publicType)?.let { projectedTypeName ->
            registerTypeDescriptor(
                type = publicType,
                projectedTypeName = projectedTypeName,
                helperType = helperType,
                runtimeClassName = publicType.registeredWinRTType()?.runtimeClassName,
                defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
                isRuntimeClass = publicType.registeredWinRTType()?.isRuntimeClass == true,
                isWindowsRuntimeType = true,
            )
        }
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
        val existing = publicType.registeredWinRTType()
        registerTypeDescriptor(
            type = publicType,
            projectedTypeName = abiTypeName,
            helperType = helperType,
            runtimeClassName = if (isRuntimeClass) abiTypeName else publicType.registeredWinRTType()?.runtimeClassName,
            defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
            isRuntimeClass = isRuntimeClass,
            isWindowsRuntimeType = true,
        )
        return helperAdded || existing == null || existing.projectedTypeName != abiTypeName || existing.helperType != helperType
    }

    fun registerAuthoredRuntimeClassType(
        publicType: KClass<*>,
        runtimeClassName: String,
    ): Boolean {
        require(runtimeClassName.isNotBlank()) { "Runtime class name must not be blank." }
        ensureProjectionMappingsRegistered()
        clearDerivedCaches()
        val existing = publicType.registeredWinRTType()
        registerTypeDescriptor(
            type = publicType,
            projectedTypeName = runtimeClassName,
            helperType = findCustomHelperTypeMapping(publicType),
            runtimeClassName = runtimeClassName,
            defaultInterface = tryGetDefaultInterfaceTypeForRuntimeClassType(publicType),
            isRuntimeClass = true,
            isWindowsRuntimeType = true,
        )
        return existing == null || existing.runtimeClassName != runtimeClassName || !existing.isRuntimeClass
    }

    fun registerDefaultInterfaceType(
        runtimeClass: KClass<*>,
        defaultInterface: KClass<*>,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        explicitProjectedTypeName(runtimeClass)?.let { projectedTypeName ->
            registerTypeDescriptor(
                type = runtimeClass,
                projectedTypeName = projectedTypeName,
                helperType = findCustomHelperTypeMapping(runtimeClass),
                runtimeClassName = inferRuntimeClassName(runtimeClass) ?: runtimeClass.registeredWinRTType()?.runtimeClassName,
                defaultInterface = defaultInterface,
                isRuntimeClass = runtimeClass.registeredWinRTType()?.isRuntimeClass == true,
                isWindowsRuntimeType = true,
            )
        }
        return runtimeClassToDefaultInterfaceMappings.putIfAbsent(runtimeClass, defaultInterface) == null
    }

    fun registerDefaultInterfaceTypeName(
        runtimeClassName: String,
        defaultInterfaceName: String,
        defaultInterfaceSignature: String? = null,
    ): Boolean {
        require(runtimeClassName.isNotBlank()) { "Runtime class name must not be blank." }
        require(defaultInterfaceName.isNotBlank()) { "Default interface name must not be blank." }
        ensureProjectionMappingsRegistered()
        val existing = WinRTTypeRegistry.findRuntimeClassInfo(runtimeClassName)
        val normalizedSignature = defaultInterfaceSignature?.takeIf(String::isNotBlank)
        WinRTTypeRegistry.registerRuntimeClassInfo(
            runtimeClassName = runtimeClassName,
            defaultInterfaceName = defaultInterfaceName,
            defaultInterfaceSignature = normalizedSignature,
            isProjectedRuntimeClass = true,
        )
        return existing == null ||
            existing.defaultInterfaceName != defaultInterfaceName ||
            (normalizedSignature != null && existing.defaultInterfaceSignature != normalizedSignature)
    }

    fun findCustomHelperTypeMapping(
        publicType: KClass<*>,
        filterToRuntimeClass: Boolean = false,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        val registered = publicType.registeredWinRTType()
        if (filterToRuntimeClass && registered?.isRuntimeClass != true) {
            return null
        }

        registered?.helperType?.let { return it }
        customTypeToHelperTypeMappings[publicType]?.let { return it }
        return null
    }

    fun findCustomPublicTypeForAbiType(
        abiType: KClass<*>,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        return WinRTTypeRegistry.findByHelperClass(abiType)?.kClass
    }

    internal fun findCustomKClassForAbiTypeName(
        abiTypeName: String,
    ): KClass<*>? {
        ensureProjectionMappingsRegistered()
        return WinRTTypeRegistry.findByName(abiTypeName)?.kClass
    }

    fun findCustomAbiTypeNameForType(
        type: KClass<*>,
    ): String? {
        ensureProjectionMappingsRegistered()
        val registered = type.registeredWinRTType() ?: return null
        if (registered.isWindowsRuntimeType || registered.isRuntimeClass) {
            return registered.projectedTypeName
        }
        return null
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
        runtimeClass.registeredWinRTType()?.defaultInterface?.let { return it }
        runtimeClassToDefaultInterfaceMappings[runtimeClass]?.let { return it }
        return null
    }

    fun tryGetDefaultInterfaceTypeNameForRuntimeClassName(
        runtimeClassName: String,
    ): String? {
        ensureProjectionMappingsRegistered()
        return WinRTTypeRegistry.findRuntimeClassInfo(runtimeClassName)?.defaultInterfaceName
    }

    fun tryGetDefaultInterfaceSignatureForRuntimeClassName(
        runtimeClassName: String,
    ): String? {
        ensureProjectionMappingsRegistered()
        return WinRTTypeRegistry.findRuntimeClassInfo(runtimeClassName)?.defaultInterfaceSignature
    }

    internal fun isProjectedRuntimeClassName(
        runtimeClassName: String,
    ): Boolean {
        ensureProjectionMappingsRegistered()
        return WinRTTypeRegistry.isProjectedRuntimeClassName(runtimeClassName)
    }

    internal fun clearRegistriesForTests() {
        customTypeToHelperTypeMappings.clear()
        runtimeClassToDefaultInterfaceMappings.clear()
        isTypeWindowsRuntimeTypeCache.clear()
        WinRTTypeRegistry.clearForTests()
        ValueBoxingMetadata.clearDynamicDescriptorsForTests()
        ValueBoxingInterop.clearDynamicAdaptersForTests()
        clearProjectionMappingsForTests()
        ensureProjectionMappingsRegistered()
    }

    private fun isTypeWindowsRuntimeTypeNoArray(type: KClass<*>): Boolean {
        val candidate = arrayElementType(type) ?: type
        if (WinRTTypeClassifier.isIntrinsicScalarOrObjectType(candidate)) {
            return true
        }

        return candidate.registeredWinRTType()?.isWindowsRuntimeType == true ||
            candidate.registeredWinRTType()?.isRuntimeClass == true
    }

    private fun clearDerivedCaches() {
        isTypeWindowsRuntimeTypeCache.clear()
    }

    private fun explicitProjectedTypeName(type: KClass<*>): String? =
        type.registeredWinRTType()?.projectedTypeName

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
        WinRTTypeRegistry.update(kClass) { existing ->
            WinRTTypeId(
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
        type.registeredWinRTType()?.runtimeClassName
            ?: findCustomAbiTypeNameForType(type)?.takeIf(::isProjectedRuntimeClassName)
            ?: type.registeredWinRTType()?.takeIf { it.isRuntimeClass }?.projectedTypeName
}
