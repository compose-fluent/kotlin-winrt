package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal actual fun platformReferenceArrayComponentType(type: KClass<*>): KClass<*>? =
    type.java
        .takeIf { javaType -> javaType.isArray && !javaType.componentType.isPrimitive }
        ?.componentType
        ?.kotlin

internal actual fun platformReferenceArrayComponentType(value: Array<*>): KClass<*>? =
    value.javaClass.componentType.kotlin
