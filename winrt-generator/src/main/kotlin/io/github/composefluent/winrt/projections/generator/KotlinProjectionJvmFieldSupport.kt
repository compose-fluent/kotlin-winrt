package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.PropertySpec

internal fun PropertySpec.Builder.addJvmFieldAnnotation(): PropertySpec.Builder =
    addAnnotation(JVM_FIELD_CLASS_NAME)
