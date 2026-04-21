@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal val WinRtKnownType.representativeClass: Class<*>
    get() = representativeType.jvmRepresentativeClass()

internal val WinRtKnownType.classAliases: Set<Class<*>>
    get() = typeAliases.flatMap(::jvmClassAliasesForType).toSet()

internal fun WinRtTypeClassifier.classify(type: Class<*>): WinRtKnownType? = classify(type.kotlin)

internal fun WinRtTypeClassifier.primitiveArrayElementType(arrayType: Class<*>): Class<*>? =
    primitiveArrayElementType(arrayType.kotlin)?.jvmRepresentativeClass()

internal fun WinRtTypeClassifier.primitiveArrayClassForElementType(elementType: Class<*>): Class<*>? =
    primitiveArrayTypeForElementType(elementType.kotlin)?.registeredClass()

internal fun WinRtTypeClassifier.isIntrinsicWindowsRuntimeType(type: Class<*>): Boolean =
    isIntrinsicWindowsRuntimeType(type.kotlin)

private fun KClass<*>.jvmRepresentativeClass(): Class<*> =
    when (this) {
        Byte::class -> Byte::class.javaObjectType
        Short::class -> Short::class.javaObjectType
        Int::class -> Int::class.javaObjectType
        Long::class -> Long::class.javaObjectType
        Boolean::class -> Boolean::class.javaObjectType
        Char::class -> Char::class.javaObjectType
        Float::class -> Float::class.javaObjectType
        Double::class -> Double::class.javaObjectType
        else -> registeredClass()
    }

private fun jvmClassAliasesForType(type: KClass<*>): Set<Class<*>> =
    when (type) {
        Byte::class -> setOf(Byte::class.javaPrimitiveType!!, Byte::class.javaObjectType)
        Short::class -> setOf(Short::class.javaPrimitiveType!!, Short::class.javaObjectType)
        Int::class -> setOf(Int::class.javaPrimitiveType!!, Int::class.javaObjectType)
        Long::class -> setOf(Long::class.javaPrimitiveType!!, Long::class.javaObjectType)
        Boolean::class -> setOf(Boolean::class.javaPrimitiveType!!, Boolean::class.javaObjectType)
        Char::class -> setOf(Char::class.javaPrimitiveType!!, Char::class.javaObjectType)
        Float::class -> setOf(Float::class.javaPrimitiveType!!, Float::class.javaObjectType)
        Double::class -> setOf(Double::class.javaPrimitiveType!!, Double::class.javaObjectType)
        else -> setOf(type.registeredClass())
    }
