package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal const val WINRT_PROJECTION_TYPE_INDEX_RESOURCE: String = "kotlin-winrt/type-index.tsv"

internal expect fun registerCompilerGeneratedProjectionTypeIndexes()

@Suppress("UNCHECKED_CAST")
public fun registerGeneratedProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
    baseTypeName: String,
) {
    if (projectedTypeName.isBlank()) {
        return
    }
    val typedClass = kClass as KClass<Any>
    val isRuntimeClass = kind == "RuntimeClass"
    WinRtTypeRegistry.update(typedClass) { existing ->
        WinRtTypeId(
            kClass = typedClass,
            projectedTypeName = projectedTypeName,
            guid = existing?.guid,
            iid = existing?.iid,
            signature = existing?.signature,
            enumAbiValue = existing?.enumAbiValue,
            enumEntries = existing?.enumEntries,
            isExceptionType = existing?.isExceptionType == true,
            helperType = existing?.helperType,
            defaultInterface = existing?.defaultInterface,
            boxedName = existing?.boxedName,
            runtimeClassName = if (isRuntimeClass) projectedTypeName else existing?.runtimeClassName,
            vftblType = existing?.vftblType,
            isDelegate = kind == "Delegate" || existing?.isDelegate == true,
            isRuntimeClass = isRuntimeClass || existing?.isRuntimeClass == true,
            isWindowsRuntimeType = true,
            aliases = existing?.aliases.orEmpty() + projectedTypeName,
        )
    }
    TypeNameSupport.registerProjectionType(
        type = kClass,
        runtimeClassName = projectedTypeName.takeIf { isRuntimeClass },
    )
    if (isRuntimeClass && baseTypeName.isMeaningfulProjectionBaseTypeName()) {
        TypeNameSupport.registerProjectionTypeBaseTypeMapping(mapOf(projectedTypeName to baseTypeName))
    }
}

internal fun registerCompilerGeneratedProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
    baseTypeName: String,
) {
    registerGeneratedProjectionTypeIndex(kClass, projectedTypeName, kind, baseTypeName)
}

private fun String.isMeaningfulProjectionBaseTypeName(): Boolean =
    isNotBlank() && this != "System.Object" && this != "Any"

