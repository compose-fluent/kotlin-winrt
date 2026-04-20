package io.github.kitectlab.winrt.runtime

import java.util.concurrent.ConcurrentHashMap

/**
 * JVM-side projection/type-mapping registry corresponding to `.cswinrt/src/WinRT.Runtime/Projections.cs`.
 *
 * Unlike the .NET path, JVM projected types do not preserve WinRT runtime class names as loadable
 * binary names. The registry therefore owns the same mapping responsibilities, but stores explicit
 * type registrations instead of relying on assembly type lookup.
 */
object Projections {
    private val customTypeToHelperTypeMappings = ConcurrentHashMap<Class<*>, Class<*>>()
    private val customAbiTypeToTypeMappings = ConcurrentHashMap<Class<*>, Class<*>>()
    private val customAbiTypeNameToTypeMappings = ConcurrentHashMap<String, Class<*>>()
    private val customTypeToAbiTypeNameMappings = ConcurrentHashMap<Class<*>, String>()
    private val projectedRuntimeClassNames = ConcurrentHashMap.newKeySet<String>()
    private val projectedCustomTypeRuntimeClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val runtimeClassToDefaultInterfaceMappings = ConcurrentHashMap<Class<*>, Class<*>>()
    private val isTypeWindowsRuntimeTypeCache = ConcurrentHashMap<Class<*>, Boolean>()

    fun registerCustomHelperTypeMapping(
        publicType: Class<*>,
        helperType: Class<*>,
    ): Boolean {
        clearDerivedCaches()
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
            projectedRuntimeClassNames += abiTypeName
            projectedCustomTypeRuntimeClasses += publicType
        }
        return helperAdded && publicAdded && abiNameAdded && typeNameAdded
    }

    fun registerDefaultInterfaceType(
        runtimeClass: Class<*>,
        defaultInterface: Class<*>,
    ): Boolean = runtimeClassToDefaultInterfaceMappings.putIfAbsent(runtimeClass, defaultInterface) == null

    fun findCustomHelperTypeMapping(
        publicType: Class<*>,
        filterToRuntimeClass: Boolean = false,
    ): Class<*>? {
        if (filterToRuntimeClass && !projectedCustomTypeRuntimeClasses.contains(publicType)) {
            return null
        }

        customTypeToHelperTypeMappings[publicType]?.let { return it }
        return publicType.getAnnotation(WindowsRuntimeHelperType::class.java)?.helperType?.java
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
        runtimeClassToDefaultInterfaceMappings[runtimeClass]?.let { return it }
        return runtimeClass.getAnnotation(WinRtDefaultInterface::class.java)?.type?.java
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
    }

    private fun isTypeWindowsRuntimeTypeNoArray(type: Class<*>): Boolean {
        if (WinRtTypeClassifier.isIntrinsicWindowsRuntimeType(type)) return true

        return customTypeToAbiTypeNameMappings.containsKey(type) ||
            type.isAnnotationPresent(WindowsRuntimeType::class.java) ||
            type.isAnnotationPresent(WinRtRuntimeClassName::class.java)
    }

    private fun clearDerivedCaches() {
        isTypeWindowsRuntimeTypeCache.clear()
    }
}
