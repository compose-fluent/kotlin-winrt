package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * CsWinRT 3 resolves generated metadata names through its type maps and gives each projected
 * type one implementation assembly (ImplGenerator.EmitTypeForwards). Kotlin has no assembly
 * forwarding: a shared runtime must reject two distinct KClass identities for that name.
 * Custom/runtime-owned aliases remain the responsibility of WinRTTypeRegistry.
 */
private object GeneratedProjectionTypeOwners {
    data class Owner(val type: KClass<*>, val kind: String, val base: String, val iid: Guid?)
    val lock = WinRTProjectionLock()
    val owners = mutableMapOf<String, Owner>()
}

internal fun clearGeneratedProjectionTypeOwnersForTests() = GeneratedProjectionTypeOwners.lock.withLock {
    GeneratedProjectionTypeOwners.owners.clear()
}

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
    GeneratedProjectionTypeOwners.lock.withLock {
        val previous = GeneratedProjectionTypeOwners.owners[projectedTypeName]
        require(previous == null || (previous.type == kClass && previous.kind == kind &&
            previous.base == baseTypeName && (previous.iid == null || iid == null || previous.iid == iid))) {
            "Conflicting generated WinRT projection '$projectedTypeName': " +
                "${previous?.type} and $kClass must share one type identity and compatible metadata. " +
                "Use one projection dependency; types from different JVM class loaders are distinct."
        }
        GeneratedProjectionTypeOwners.owners[projectedTypeName] = GeneratedProjectionTypeOwners.Owner(
            kClass, kind, baseTypeName, iid ?: previous?.iid,
        )
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

