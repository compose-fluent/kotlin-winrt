package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
internal fun Class<*>.registeredKClass(): KClass<Any> = kotlin as KClass<Any>

internal fun Class<*>.registeredWinRtType(): WinRtTypeId<*>? =
    WinRtTypeRegistry.findByClass(registeredKClass()) ?: registerAnnotatedWinRtType()

internal fun KClass<*>.registeredClass(): Class<*> = java

internal fun WinRtTypeId<*>.registeredClass(): Class<*> = kClass.registeredClass()

@Suppress("UNCHECKED_CAST")
internal fun WinRtTypeId<*>.readEnumAbiValue(enumValue: Any): Int =
    (enumAbiValue as? (Any) -> Int)?.invoke(enumValue)
        ?: error("Type '${kClass.qualifiedName ?: kClass.simpleName}' is missing enum ABI metadata.")

internal fun Class<*>.registerAnnotatedWinRtType(): WinRtTypeId<*>? {
    val windowsRuntimeType = getAnnotation(WindowsRuntimeType::class.java)
    val winRtGuid = getAnnotation(WinRtGuid::class.java)
    val helperType = getAnnotation(WindowsRuntimeHelperType::class.java)
    val runtimeClassName = getAnnotation(WinRtRuntimeClassName::class.java)
    val defaultInterface = getAnnotation(WinRtDefaultInterface::class.java)
    val isDelegate = isAnnotationPresent(WinRtDelegateType::class.java)

    if (
        windowsRuntimeType == null &&
        winRtGuid == null &&
        helperType == null &&
        runtimeClassName == null &&
        defaultInterface == null &&
        !isDelegate
    ) {
        return null
    }

    val signature = windowsRuntimeType?.guidSignature?.takeIf(String::isNotBlank)
    val projectedTypeName =
        signature?.extractProjectedTypeNameFromSignature()
            ?: runtimeClassName?.runtimeClassName
            ?: name
    val resolvedRuntimeClassName =
        runtimeClassName?.runtimeClassName
            ?: signature
                ?.takeIf(::isRuntimeClassSignature)
                ?.extractProjectedTypeNameFromSignature()
    val resolvedGuid = winRtGuid?.let { Guid(it.value) }
    val type = registeredKClass()

    return WinRtTypeRegistry.update(type) { existing ->
        WinRtTypeId(
            kClass = type,
            projectedTypeName = existing?.projectedTypeName ?: projectedTypeName,
            guid = resolvedGuid ?: existing?.guid,
            iid = resolvedGuid ?: existing?.iid,
            signature = signature ?: existing?.signature,
            enumAbiValue = existing?.enumAbiValue,
            helperType = helperType?.helperType ?: existing?.helperType,
            defaultInterface = defaultInterface?.type ?: existing?.defaultInterface,
            boxedName = existing?.boxedName,
            runtimeClassName = resolvedRuntimeClassName ?: existing?.runtimeClassName,
            vftblType = existing?.vftblType,
            isDelegate = isDelegate || signature?.let(::isDelegateSignature) == true || existing?.isDelegate == true,
            isRuntimeClass = resolvedRuntimeClassName != null || existing?.isRuntimeClass == true,
            isWindowsRuntimeType = true,
            aliases = existing?.aliases.orEmpty(),
        )
    }
}

private fun String.extractProjectedTypeNameFromSignature(): String? {
    val openParen = indexOf('(')
    if (openParen < 0 || openParen == lastIndex) {
        return null
    }
    val start = openParen + 1
    val end =
        listOf(
            indexOf(';', start),
            indexOf(')', start),
        ).filter { it >= 0 }
            .minOrNull()
            ?: return null
    return substring(start, end).takeIf(String::isNotBlank)
}

private fun isRuntimeClassSignature(signature: String): Boolean = signature.startsWith("rc(")

private fun isDelegateSignature(signature: String): Boolean = signature.startsWith("delegate(")
