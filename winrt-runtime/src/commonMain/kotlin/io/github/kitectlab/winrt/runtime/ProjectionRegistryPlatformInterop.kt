package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal fun KClass<*>.registeredWinRtType(): WinRtTypeId<*>? =
    WinRtTypeRegistry.findByClass(this) ?: platformRegisterWinRtType(this)

internal fun KClass<*>.typeDisplayName(): String = qualifiedName ?: simpleName ?: "<anonymous>"

internal expect fun platformRegisterWinRtType(type: KClass<*>): WinRtTypeId<*>?

internal expect fun ensurePlatformProjectionMappingsRegistered()

internal expect fun clearPlatformProjectionMappingsForTests()

internal expect fun isPlatformExceptionType(type: KClass<*>): Boolean

internal expect fun platformArrayElementType(type: KClass<*>): KClass<*>?

internal expect fun platformIsEnumType(type: KClass<*>): Boolean

internal expect fun platformEnumConstants(type: KClass<*>): Array<Any>?

internal expect fun platformTypeCanonicalName(type: KClass<*>): String?

internal expect fun platformTypeName(type: KClass<*>): String

internal expect fun platformIsAssignableFrom(
    targetType: KClass<*>,
    candidateType: KClass<*>,
): Boolean

internal expect fun platformBoxedRuntimeClassName(type: KClass<*>): String?

internal expect fun platformRuntimeClassNameForNonWinRtType(type: KClass<*>): String?
