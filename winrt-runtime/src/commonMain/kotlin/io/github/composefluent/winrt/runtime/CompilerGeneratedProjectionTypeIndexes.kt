package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
public fun registerGeneratedProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
    baseTypeName: String,
) {
    registerGeneratedProjectionTypeIndex(kClass, projectedTypeName, kind, baseTypeName, interfaceIid = "")
}

@Suppress("UNCHECKED_CAST")
public fun registerGeneratedProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
    baseTypeName: String,
    interfaceIid: String,
) {
    if (projectedTypeName.isBlank()) {
        return
    }
    val typedClass = kClass as KClass<Any>
    val isRuntimeClass = kind == "RuntimeClass"
    val iid = interfaceIid.takeIf(String::isNotBlank)?.let(::Guid)
    WinRTTypeRegistry.update(typedClass) { existing ->
        WinRTTypeId(
            kClass = typedClass,
            projectedTypeName = projectedTypeName,
            guid = existing?.guid,
            iid = iid ?: existing?.iid,
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

internal fun registerCompilerGeneratedProjectionTypeIndex(
    kClass: KClass<*>,
    projectedTypeName: String,
    kind: String,
    baseTypeName: String,
    interfaceIid: String,
) {
    registerGeneratedProjectionTypeIndex(kClass, projectedTypeName, kind, baseTypeName, interfaceIid)
}

private fun String.isMeaningfulProjectionBaseTypeName(): Boolean =
    isNotBlank() && !WinRTTypeClassifier.isObjectRuntimeName(this)

