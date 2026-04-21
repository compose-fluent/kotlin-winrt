package io.github.kitectlab.winrt.runtime

internal fun TypeExtensions.findHelperType(
    type: Class<*>,
    throwIfMissing: Boolean = true,
): Class<*>? = findHelperType(type.registeredKClass(), throwIfMissing)?.registeredClass()

internal fun TypeExtensions.getHelperType(
    type: Class<*>,
): Class<*> = getHelperType(type.registeredKClass()).registeredClass()

internal fun TypeExtensions.getGuidType(
    type: Class<*>,
): Class<*> = getGuidType(type.registeredKClass()).registeredClass()

internal fun TypeExtensions.findVftblType(
    helperType: Class<*>,
): Class<*>? = findVftblType(helperType.registeredKClass())?.registeredClass()

internal fun TypeExtensions.isDelegate(
    type: Class<*>,
): Boolean = isDelegate(type.registeredKClass())
