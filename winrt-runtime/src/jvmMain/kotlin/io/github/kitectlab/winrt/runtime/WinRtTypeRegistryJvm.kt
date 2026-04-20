package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
internal fun Class<*>.registeredKClass(): KClass<Any> = kotlin as KClass<Any>

internal fun Class<*>.registeredWinRtType(): WinRtTypeId<*>? = WinRtTypeRegistry.findByClass(registeredKClass())

internal fun KClass<*>.registeredClass(): Class<*> = java

internal fun WinRtTypeId<*>.registeredClass(): Class<*> = kClass.registeredClass()
