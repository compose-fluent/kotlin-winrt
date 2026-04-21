package io.github.kitectlab.winrt.runtime

internal fun Projections.registerCustomHelperTypeMapping(
    publicType: Class<*>,
    helperType: Class<*>,
): Boolean = registerCustomHelperTypeMapping(publicType.registeredKClass(), helperType.registeredKClass())

internal fun Projections.registerCustomAbiTypeMapping(
    publicType: Class<*>,
    helperType: Class<*>,
    abiTypeName: String,
    isRuntimeClass: Boolean = false,
): Boolean =
    registerCustomAbiTypeMapping(
        publicType = publicType.registeredKClass(),
        helperType = helperType.registeredKClass(),
        abiTypeName = abiTypeName,
        isRuntimeClass = isRuntimeClass,
    )

internal fun Projections.registerDefaultInterfaceType(
    runtimeClass: Class<*>,
    defaultInterface: Class<*>,
): Boolean = registerDefaultInterfaceType(runtimeClass.registeredKClass(), defaultInterface.registeredKClass())

internal fun Projections.findCustomHelperTypeMapping(
    publicType: Class<*>,
    filterToRuntimeClass: Boolean = false,
): Class<*>? = findCustomHelperTypeMapping(publicType.registeredKClass(), filterToRuntimeClass)?.registeredClass()

internal fun Projections.findCustomPublicTypeForAbiType(
    abiType: Class<*>,
): Class<*>? = findCustomPublicTypeForAbiType(abiType.registeredKClass())?.registeredClass()

internal fun Projections.findCustomTypeForAbiTypeName(
    abiTypeName: String,
): Class<*>? = findCustomKClassForAbiTypeName(abiTypeName)?.registeredClass()

internal fun Projections.findCustomAbiTypeNameForType(
    type: Class<*>,
): String? = findCustomAbiTypeNameForType(type.registeredKClass())

internal fun Projections.isTypeWindowsRuntimeType(
    type: Class<*>,
): Boolean = isTypeWindowsRuntimeType(type.registeredKClass())

internal fun Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(
    runtimeClass: Class<*>,
): Class<*>? = tryGetDefaultInterfaceTypeForRuntimeClassType(runtimeClass.registeredKClass())?.registeredClass()
