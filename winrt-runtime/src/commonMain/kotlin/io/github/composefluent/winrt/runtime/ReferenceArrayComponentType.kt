package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal expect fun platformReferenceArrayComponentType(type: KClass<*>): KClass<*>?

internal expect fun platformReferenceArrayComponentType(value: Array<*>): KClass<*>?
