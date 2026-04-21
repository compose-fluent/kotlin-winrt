package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

fun ComWrappersSupport.registerCcwFactory(
    implementationType: Class<*>,
    factory: (Any) -> WinRtCcwDefinition,
): Boolean = registerCcwFactory(implementationType.kotlin, factory)

fun ComWrappersSupport.registerProjectionType(
    type: Class<*>,
    runtimeClassName: String? = null,
) {
    registerProjectionType(type.kotlin, runtimeClassName)
}

fun ComWrappersSupport.registerProjectionAssembly(
    vararg projectionTypes: Class<*>,
) {
    registerProjectionAssembly(*projectionTypes.map { it.kotlin }.toTypedArray())
}

fun ComWrappersSupport.registerJavaTypeRuntimeClassNameLookup(
    lookup: (Class<*>) -> String?,
) {
    registerTypeRuntimeClassNameLookup { type: KClass<*> -> lookup(type.registeredClass()) }
}

fun <T : Any> ComWrappersSupport.findObject(
    pointer: NativePointer,
    expectedType: Class<T>,
): T? = findObject(pointer, expectedType.kotlin)

internal fun ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(
    type: Class<*>,
): String? = getRuntimeClassNameForNonWinRTTypeFromLookupTable(type.kotlin)
