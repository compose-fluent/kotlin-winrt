package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiInventory
import io.github.kitectlab.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.kitectlab.winrt.metadata.WinRtMetadataParameterCategory
import io.github.kitectlab.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtMetadataValidationOptions
import io.github.kitectlab.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.kitectlab.winrt.metadata.requireValidForProjection
import io.github.kitectlab.winrt.metadata.semanticHelpers
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.Marshaler
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.ParameterizedInterfaceId
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.NativeNestedStructFieldSpec
import io.github.kitectlab.winrt.runtime.NativeScalarFieldSpec
import io.github.kitectlab.winrt.runtime.NativeStructLayout
import io.github.kitectlab.winrt.runtime.NativeStructScalarKind
import io.github.kitectlab.winrt.runtime.WinRtBindableIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.kitectlab.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.kitectlab.winrt.runtime.WinRtDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtListProjection
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyListProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceArrayProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceValueAdapter
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

internal fun KotlinProjectionRenderer.resolveTypeName(typeName: String): TypeName {
    val trimmed = typeName.trim()
    val genericStart = trimmed.indexOf('<')
    if (genericStart >= 0 && trimmed.endsWith('>')) {
        val rawType = trimmed.substring(0, genericStart)
        val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
            .map(::resolveTypeName)
        if (rawType == "Array") {
            return Array::class.asClassName().parameterizedBy(arguments)
        }
        mappedTypeByAbiName(rawType)?.let { mappedType ->
            return mappedType.projectedTypeResolver(arguments)
        }
        val rawClassName = if ('.' in rawType) projectionClassName(rawType) else ClassName.bestGuess(rawType)
        return rawClassName.parameterizedBy(arguments)
    }

    mappedTypeByAbiName(trimmed)?.let { mappedType ->
        return mappedType.projectedTypeResolver(emptyList())
    }
    if ((trimmed.startsWith("T") || trimmed.startsWith("M")) && trimmed.drop(1).toIntOrNull() != null) {
        return TypeVariableName(trimmed)
    }

    return when (trimmed) {
        "Unit" -> UNIT
        "Any",
        "System.Object" -> IINSPECTABLE_REFERENCE_CLASS_NAME
        "String" -> String::class.asClassName()
        "Int" -> Int::class.asClassName()
        "UInt" -> UInt::class.asClassName()
        "Boolean" -> Boolean::class.asClassName()
        "Byte" -> Byte::class.asClassName()
        "SByte",
        "Int8" -> Byte::class.asClassName()
        "UInt8" -> UByte::class.asClassName()
        "Short" -> Short::class.asClassName()
        "Int16" -> Short::class.asClassName()
        "UShort" -> UShort::class.asClassName()
        "UInt16" -> UShort::class.asClassName()
        "Long" -> Long::class.asClassName()
        "Int64" -> Long::class.asClassName()
        "ULong",
        "UInt64" -> ULong::class.asClassName()
        "Float" -> Float::class.asClassName()
        "Double" -> Double::class.asClassName()
        "Char" -> Char::class.asClassName()
        "Guid",
        "System.Guid" -> GUID_CLASS_NAME
        IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
        "io.github.kitectlab.winrt.runtime.IUnknownReference" -> IUNKNOWN_REFERENCE_CLASS_NAME
        IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
        "io.github.kitectlab.winrt.runtime.IInspectableReference" -> IINSPECTABLE_REFERENCE_CLASS_NAME
        IWINRT_OBJECT_CLASS_NAME.simpleName,
        "io.github.kitectlab.winrt.runtime.IWinRTObject" -> IWINRT_OBJECT_CLASS_NAME
        else -> if ('.' in trimmed) projectionClassName(trimmed) else ClassName.bestGuess(trimmed)
    }
}

internal fun KotlinProjectionRenderer.resolveIntegralTypeName(type: WinRtIntegralType): TypeName =
    integralAbiDescriptor(type).kotlinTypeName

internal fun KotlinProjectionRenderer.integralLiteral(valueBits: ULong, type: WinRtIntegralType): CodeBlock =
    integralAbiDescriptor(type).literalRenderer(valueBits)

internal fun KotlinProjectionRenderer.splitGenericArguments(arguments: String): List<String> {
    if (arguments.isBlank()) {
        return emptyList()
    }
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    arguments.forEachIndexed { index, character ->
        when (character) {
            '<' -> depth += 1
            '>' -> depth -= 1
            ',' -> if (depth == 0) {
                result += arguments.substring(start, index).trim()
                start = index + 1
            }
        }
    }
    result += arguments.substring(start).trim()
    return result.filter(String::isNotEmpty)
}

internal fun KotlinProjectionRenderer.projectionClassName(qualifiedName: String?): ClassName {
    require(!qualifiedName.isNullOrBlank()) {
        "Projection class name requires a non-blank qualified name."
    }
    val trimmed = qualifiedName.trim()
    val lastDot = trimmed.lastIndexOf('.')
    if (lastDot < 0) {
        return ClassName("", trimmed)
    }
    val namespace = trimmed.substring(0, lastDot)
    val simpleName = trimmed.substring(lastDot + 1)
    val packageName = (ROOT_PACKAGE_SEGMENTS + namespace.split('.').filter { it.isNotBlank() }.map { it.lowercase() })
        .joinToString(".")
    return ClassName(packageName, simpleName)
}
