package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal actual fun platformRegisterWinRtType(type: KClass<*>): WinRtTypeId<*>? = type.registeredClass().registerAnnotatedWinRtType()

internal actual fun ensurePlatformProjectionMappingsRegistered() {
    JvmProjectionMappingsBootstrap.ensureRegistered()
}

internal actual fun clearPlatformProjectionMappingsForTests() {
    JvmProjectionMappingsBootstrap.reset()
}

internal actual fun isPlatformExceptionType(type: KClass<*>): Boolean =
    Exception::class.java.isAssignableFrom(type.registeredClass())

internal actual fun platformArrayElementType(type: KClass<*>): KClass<*>? =
    type.registeredClass().componentType?.kotlin

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
