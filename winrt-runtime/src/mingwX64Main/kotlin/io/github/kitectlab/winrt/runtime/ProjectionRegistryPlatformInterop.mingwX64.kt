package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual fun platformRegisterWinRtType(type: KClass<*>): WinRtTypeId<*>? = null

internal actual fun ensurePlatformProjectionMappingsRegistered() {
}

internal actual fun clearPlatformProjectionMappingsForTests() {
}

internal actual fun isPlatformExceptionType(type: KClass<*>): Boolean = false

internal actual fun platformArrayElementType(type: KClass<*>): KClass<*>? = null
