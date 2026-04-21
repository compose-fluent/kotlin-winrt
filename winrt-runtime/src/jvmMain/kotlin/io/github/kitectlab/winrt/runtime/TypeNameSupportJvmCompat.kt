package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal fun TypeNameSupport.registerProjectionAssembly(
    vararg projectionTypes: Class<*>,
) {
    registerProjectionAssembly(*projectionTypes.map(Class<*>::registeredKClass).toTypedArray())
}

internal fun TypeNameSupport.registerProjectionType(
    type: Class<*>,
    runtimeClassName: String? = inferRuntimeClassName(type),
) {
    registerProjectionType(type.registeredKClass(), runtimeClassName)
}

internal fun TypeNameSupport.registerProjectionTypes(
    vararg types: Class<*>,
) {
    registerProjectionTypes(*types.map(Class<*>::registeredKClass).toTypedArray())
}

internal fun TypeNameSupport.registerBaseTypeForTypeName(
    runtimeClassName: String,
    baseType: Class<*>,
) {
    registerBaseTypeForTypeName(runtimeClassName, baseType.registeredKClass())
}

internal fun TypeNameSupport.registerReferenceArrayType(
    elementType: Class<*>,
    arrayType: Class<*>,
) {
    registerReferenceArrayType(elementType.registeredKClass(), arrayType.registeredKClass())
}

internal fun TypeNameSupport.findRcwTypeByNameCached(
    runtimeClassName: String,
): Class<*>? = findRcwKClassByNameCached(runtimeClassName)?.jvmTypeNameLookupClass()

internal fun TypeNameSupport.findTypeByNameCached(
    runtimeClassName: String,
): Class<*>? = findKClassByNameCached(runtimeClassName)?.jvmTypeNameLookupClass()

internal fun TypeNameSupport.getNameForType(
    type: Class<*>?,
    flags: Set<TypeNameGenerationFlag> = emptySet(),
): String = getNameForType(type?.registeredKClass(), flags)

internal fun TypeNameSupport.inferRuntimeClassName(
    type: Class<*>,
): String? = inferRuntimeClassName(type.registeredKClass())

private fun KClass<*>.jvmTypeNameLookupClass(): Class<*> =
    WinRtTypeClassifier.classify(registeredClass())?.representativeClass ?: registeredClass()
