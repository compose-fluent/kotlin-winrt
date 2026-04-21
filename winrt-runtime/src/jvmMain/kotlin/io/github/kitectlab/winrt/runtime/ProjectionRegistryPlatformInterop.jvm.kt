package io.github.kitectlab.winrt.runtime

import kotlin.jvm.java
import kotlin.jvm.kotlin
import kotlin.reflect.KClass

internal actual fun platformRegisterWinRtType(type: KClass<*>): WinRtTypeId<*>? =
    WinRtTypeRegistry.findByClass(type) ?: type.java.registerAnnotatedWinRtType()

internal actual fun ensurePlatformProjectionMappingsRegistered() {
    JvmProjectionMappingsBootstrap.ensureRegistered()
}

internal actual fun clearPlatformProjectionMappingsForTests() {
    JvmProjectionMappingsBootstrap.reset()
}

internal actual fun isPlatformExceptionType(type: KClass<*>): Boolean =
    Exception::class.java.isAssignableFrom(type.java)

internal actual fun platformArrayElementType(type: KClass<*>): KClass<*>? =
    type.java.componentType?.kotlin

internal actual fun platformIsEnumType(type: KClass<*>): Boolean = type.java.isEnum

internal actual fun platformEnumConstants(type: KClass<*>): Array<Any>? =
    type.java.enumConstants?.map { it as Any }?.toTypedArray()

internal actual fun platformTypeCanonicalName(type: KClass<*>): String? = type.java.canonicalName

internal actual fun platformTypeName(type: KClass<*>): String = type.java.name

internal actual fun platformIsAssignableFrom(
    targetType: KClass<*>,
    candidateType: KClass<*>,
): Boolean = targetType.java.isAssignableFrom(candidateType.java)

internal actual fun platformBoxedRuntimeClassName(type: KClass<*>): String? =
    WinRtValueBoxing.boxedRuntimeClassNameForType(type)

internal actual fun platformRuntimeClassNameForNonWinRtType(type: KClass<*>): String? =
    ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type)

private object JvmProjectionMappingsBootstrap {
    private var registered = false
    private var registering = false

    fun ensureRegistered() {
        if (registered || registering) {
            return
        }
        registering = true
        try {
            WinRtBuiltInProjectionMappings.register()
            registered = true
        } finally {
            registering = false
        }
    }

    fun reset() {
        registered = false
        registering = false
    }
}

private fun Class<*>.registerAnnotatedWinRtType(): WinRtTypeId<*>? {
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
    @Suppress("UNCHECKED_CAST")
    val type = kotlin as KClass<Any>

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
