package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtFieldDefinition
import io.github.composefluent.winrt.metadata.isWinRtGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRtVoidTypeName
import io.github.composefluent.winrt.metadata.winRtFundamentalTypeForName
import io.github.composefluent.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRtGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRtMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.Marshaler
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.WinRtBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRtDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtIterableProjection
import io.github.composefluent.winrt.runtime.WinRtListProjection
import io.github.composefluent.winrt.runtime.WinRtAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.WinRtUri
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRtDelegateReference
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtEvent
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
    if (trimmed == "Any?") {
        return ANY.copy(nullable = true)
    }
    val nullable = trimmed.endsWith("?")
    val effectiveTypeName = redirectedAbiTypeName(trimmed.removeSuffix("?"))
    val genericStart = effectiveTypeName.indexOf('<')
    if (genericStart >= 0 && effectiveTypeName.endsWith('>')) {
        val rawType = effectiveTypeName.substring(0, genericStart)
        val arguments = splitGenericArguments(effectiveTypeName.substring(genericStart + 1, effectiveTypeName.length - 1))
            .map(::resolveTypeName)
        if (rawType == "Array") {
            return Array::class.asClassName().parameterizedBy(arguments).withOuterNullability(nullable)
        }
        mappedTypeByAbiName(rawType)?.let { mappedType ->
            return mappedType.projectedTypeResolver(arguments).withOuterNullability(nullable)
        }
        val rawClassName = if ('.' in rawType) projectionClassName(rawType) else ClassName.bestGuess(rawType)
        return rawClassName.parameterizedBy(arguments).withOuterNullability(nullable)
    }

    mappedTypeByAbiName(effectiveTypeName)?.let { mappedType ->
        return mappedType.projectedTypeResolver(emptyList()).withOuterNullability(nullable)
    }
    if ((effectiveTypeName.startsWith("T") || effectiveTypeName.startsWith("M")) && effectiveTypeName.drop(1).toIntOrNull() != null) {
        return TypeVariableName(effectiveTypeName).withOuterNullability(nullable)
    }

    if (isWinRtVoidTypeName(effectiveTypeName)) {
        return UNIT
    }
    if (isWinRtGuidTypeName(effectiveTypeName)) {
        return GUID_CLASS_NAME
    }
    winRtFundamentalTypeForName(effectiveTypeName)?.let { fundamentalType ->
        return fundamentalType.toProjectionTypeName().withOuterNullability(nullable)
    }
    if (isWinRtObjectTypeName(effectiveTypeName)) {
        return ANY.copy(nullable = true)
    }

    return when (effectiveTypeName) {
        IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
        "io.github.composefluent.winrt.runtime.IUnknownReference" -> IUNKNOWN_REFERENCE_CLASS_NAME
        IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
        "io.github.composefluent.winrt.runtime.IInspectableReference" -> IINSPECTABLE_REFERENCE_CLASS_NAME
        IWINRT_OBJECT_CLASS_NAME.simpleName,
        "io.github.composefluent.winrt.runtime.IWinRTObject" -> IWINRT_OBJECT_CLASS_NAME
        else -> if ('.' in effectiveTypeName) projectionClassName(effectiveTypeName) else ClassName.bestGuess(effectiveTypeName)
    }.withOuterNullability(nullable)
}

internal fun redirectedWinAppSdkAbiTypeName(typeName: String, useWinAppSdkTypeRedirects: Boolean): String {
    if (!useWinAppSdkTypeRedirects) {
        return typeName
    }
    return when {
        typeName.startsWith("Windows.UI.Composition.") ->
            "Microsoft.UI.Composition.${typeName.removePrefix("Windows.UI.Composition.")}"
        typeName == "Windows.UI.Xaml.Data.INotifyPropertyChanged" ->
            "Microsoft.UI.Xaml.Data.INotifyPropertyChanged"
        typeName == "Windows.UI.Xaml.Data.INotifyDataErrorInfo" ->
            "Microsoft.UI.Xaml.Data.INotifyDataErrorInfo"
        typeName == "Windows.UI.Xaml.Input.ICommand" ->
            "Microsoft.UI.Xaml.Input.ICommand"
        typeName == "Windows.UI.Xaml.Interop.ICommand" ->
            "Microsoft.UI.Xaml.Interop.ICommand"
        typeName == "Windows.UI.Xaml.Interop.INotifyCollectionChanged" ->
            "Microsoft.UI.Xaml.Interop.INotifyCollectionChanged"
        typeName == "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventArgs" ->
            "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs"
        typeName == "Windows.UI.Xaml.Interop.NotifyCollectionChangedEventHandler" ->
            "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler"
        else -> typeName
    }
}

internal fun redirectedWinAppSdkAbiTypeExpression(typeName: String, useWinAppSdkTypeRedirects: Boolean): String {
    val trimmed = typeName.trim()
    val nullableSuffix = if (trimmed.endsWith("?")) "?" else ""
    val effectiveTypeName = trimmed.removeSuffix("?")
    val genericStart = effectiveTypeName.indexOf('<')
    if (genericStart >= 0 && effectiveTypeName.endsWith('>')) {
        val rawType = redirectedWinAppSdkAbiTypeName(
            effectiveTypeName.substring(0, genericStart),
            useWinAppSdkTypeRedirects,
        )
        val arguments = splitGenericArguments(effectiveTypeName.substring(genericStart + 1, effectiveTypeName.length - 1))
            .joinToString(", ") { argument ->
                redirectedWinAppSdkAbiTypeExpression(argument, useWinAppSdkTypeRedirects)
            }
        return "$rawType<$arguments>$nullableSuffix"
    }
    return redirectedWinAppSdkAbiTypeName(effectiveTypeName, useWinAppSdkTypeRedirects) + nullableSuffix
}

internal fun KotlinProjectionRenderer.redirectedAbiTypeName(typeName: String): String =
    redirectedWinAppSdkAbiTypeName(typeName, useWinAppSdkTypeRedirects)

internal fun KotlinProjectionRenderer.redirectedAbiTypeExpression(typeName: String): String =
    redirectedWinAppSdkAbiTypeExpression(typeName, useWinAppSdkTypeRedirects)

internal fun KotlinProjectionRenderer.resolveStructFieldTypeName(
    @Suppress("UNUSED_PARAMETER") plan: KotlinTypeProjectionPlan,
    typeName: String,
): TypeName {
    return resolveTypeName(typeName)
}

internal fun KotlinTypeProjectionPlan.requiresKotlinDurationAlias(): Boolean =
    type.kind == WinRtTypeKind.Struct &&
        type.fields.any { field ->
                !field.isStatic &&
                !field.isLiteral &&
                mappedTypeByAbiName(field.typeName.substringBefore('<').removeSuffix("?"))?.descriptionName == "TimeSpan"
        }

private fun TypeName.withOuterNullability(nullable: Boolean): TypeName =
    if (nullable) copy(nullable = true) else this

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
    return projectionClassNameForQualifiedName(qualifiedName)
}

internal fun projectionClassNameForQualifiedName(qualifiedName: String?): ClassName {
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
