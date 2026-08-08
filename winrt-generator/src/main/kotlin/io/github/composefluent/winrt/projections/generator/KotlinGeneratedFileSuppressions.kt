package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec

internal fun FileSpec.Builder.addGeneratedProjectionSuppressions(): FileSpec.Builder =
    addAnnotation(generatedProjectionSuppressAnnotation())

internal fun FileSpec.Builder.addGeneratedProjectionAtomicOptIn(): FileSpec.Builder =
    addAnnotation(
        AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
            .addMember(
                "%T::class",
                ClassName("kotlin.concurrent.atomics", "ExperimentalAtomicApi"),
            )
            .build(),
    )

internal fun generatedProjectionSuppressAnnotation(): AnnotationSpec =
    AnnotationSpec.builder(Suppress::class)
        .addMember("%S", KOTLIN_WINRT_GENERATED_SUPPRESS_MARKER)
        .addMember("%S", "USELESS_IS_CHECK")
        .addMember("%S", "USELESS_CAST")
        .addMember("%S", "UNCHECKED_CAST")
        .addMember("%S", "REDUNDANT_CALL_OF_CONVERSION_METHOD")
        .addMember("%S", "REDUNDANT_NULLABLE")
        .addMember("%S", "DEPRECATION_ERROR")
        .addMember("%S", "NOTHING_TO_INLINE")
        .addMember("%S", "OVERRIDE_BY_INLINE")
        .build()

internal const val KOTLIN_WINRT_GENERATED_SUPPRESS_MARKER = "KOTLIN_WINRT_GENERATED"
