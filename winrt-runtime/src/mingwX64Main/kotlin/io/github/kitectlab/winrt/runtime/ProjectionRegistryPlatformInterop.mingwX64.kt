package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual fun platformRegisterWinRtType(type: KClass<*>): WinRtTypeId<*>? = null

internal actual fun ensurePlatformProjectionMappingsRegistered() {
}

internal actual fun clearPlatformProjectionMappingsForTests() {
}

internal actual fun isPlatformExceptionType(type: KClass<*>): Boolean = false

internal actual fun platformArrayElementType(type: KClass<*>): KClass<*>? = null

internal actual fun platformTypeCanonicalName(type: KClass<*>): String? = type.qualifiedName

internal actual fun platformTypeName(type: KClass<*>): String = type.typeDisplayName()

internal actual fun platformIsAssignableFrom(
    targetType: KClass<*>,
    candidateType: KClass<*>,
): Boolean = targetType == candidateType

internal actual fun platformBoxedRuntimeClassName(type: KClass<*>): String? = null

internal actual fun platformRuntimeClassNameForNonWinRtType(type: KClass<*>): String? = null
