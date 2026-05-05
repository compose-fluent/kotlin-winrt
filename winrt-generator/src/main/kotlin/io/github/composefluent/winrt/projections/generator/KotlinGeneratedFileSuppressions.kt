package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec

internal fun FileSpec.Builder.addGeneratedProjectionSuppressions(): FileSpec.Builder =
    addAnnotation(generatedProjectionSuppressAnnotation())

internal fun generatedProjectionSuppressAnnotation(): AnnotationSpec =
    AnnotationSpec.builder(Suppress::class)
        .addMember("%S", "USELESS_IS_CHECK")
        .addMember("%S", "USELESS_CAST")
        .addMember("%S", "UNCHECKED_CAST")
        .addMember("%S", "REDUNDANT_CALL_OF_CONVERSION_METHOD")
        .addMember("%S", "REDUNDANT_NULLABLE")
        .build()
