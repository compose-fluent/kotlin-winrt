package io.github.kitectlab.winrt.runtime

internal actual fun platformCreateInspectableReference(value: Any): ComObjectReference =
    ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

internal actual fun platformTryProjectBindableInspectable(pointer: RawAddress): Any? =
    tryProjectBorrowedInspectableValue(pointer)

internal actual fun platformEnsureInspectableProjectionInteropRegistered() {
    WinRtBuiltInProjectionRuntimeHooks.ensureRegistered()
}

internal actual fun platformTryProjectInspectable(
    inspectable: IInspectableReference,
    runtimeClassName: String?,
): Any? = tryProjectInspectableValue(inspectable, runtimeClassName)

internal actual fun platformTryCreateProjectedReference(
    value: Any,
    interfaceId: Guid?,
): ComObjectReference? = WinRtBuiltInProjectionRuntimeHooks.tryCreateProjectedReference(value, interfaceId)

internal actual fun platformCreateSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? =
    tryCreateGeneratedTypeDetailsCcwDefinition(value) ?: createSyntheticInspectableCcwDefinition(value)

internal actual fun platformRuntimeClassNameFor(value: Any): String? =
    defaultInspectableRuntimeClassNameFor(value)

private fun tryCreateGeneratedTypeDetailsCcwDefinition(value: Any): WinRtCcwDefinition? {
    val generatedDetailsTypeName = generatedTypeDetailsName(value) ?: return null
    val detailsType = runCatching {
        Class.forName(generatedDetailsTypeName, true, value.javaClass.classLoader)
    }.getOrNull() ?: return null
    val createDefinition = runCatching {
        detailsType.getMethod("createCcwDefinition", Any::class.java)
    }.getOrNull() ?: return null
    return createDefinition.invoke(null, value) as? WinRtCcwDefinition
}

private fun generatedTypeDetailsName(value: Any): String? {
    val qualifiedName = value::class.qualifiedName ?: return null
    val packageName = qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    val simpleName = qualifiedName.substringAfterLast('.').replace('$', '_')
    val detailsName = "WinRT_${simpleName}_TypeDetails"
    return if (packageName.isBlank()) detailsName else "$packageName.$detailsName"
}
