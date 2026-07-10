package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// Kotlin/Native erases every Array<T> specialization to the same KClass and exposes no
// declared component metadata. Returning null keeps registration order out of semantics and
// forces the shared value classifier to inspect all non-null elements. Consequently an empty
// array, or a homogeneous value held in a declared Array<Any?>, cannot retain that static type.
internal actual fun platformReferenceArrayComponentType(type: KClass<*>): KClass<*>? = null

internal actual fun platformReferenceArrayComponentType(value: Array<*>): KClass<*>? = null
