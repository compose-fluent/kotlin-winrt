package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private val xamlTypeClassifier = WinRTMetadataModel(emptyList()).typeClassifier()

/** Same primitive/mapped/Guid decisions as KotlinProjectionTypeResolver. */
internal fun xamlTypeClassId(name: String): ClassId {
    val type = xamlTypeClassifier.classify(WinRTTypeRef.named(name), name.substringBeforeLast('.', ""))
    type.mappedType?.kotlinQualifiedName?.let { return ClassId.topLevel(FqName(it)) }
    return when (type.projectionCategory) {
        WinRTProjectionCategory.Guid -> ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.Guid"))
        WinRTProjectionCategory.Object -> ClassId.topLevel(FqName("kotlin.Any"))
        WinRTProjectionCategory.Type -> ClassId.topLevel(FqName("kotlin.reflect.KClass"))
        else -> winRTFundamentalTypeForName(name)?.toKotlinProjectionTypeName()
            ?.let { ClassId.topLevel(FqName("kotlin.$it")) } ?: xamlProjectionClassId(name)
    }
}
