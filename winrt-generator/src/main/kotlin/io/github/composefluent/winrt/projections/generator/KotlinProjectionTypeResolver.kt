package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.isWinRTGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
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
import io.github.composefluent.winrt.runtime.WinRTBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTIterableProjection
import io.github.composefluent.winrt.runtime.WinRTListProjection
import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTUri
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
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

    if (isWinRTVoidTypeName(effectiveTypeName)) {
        return UNIT
    }
    if (isWinRTGuidTypeName(effectiveTypeName)) {
        return GUID_CLASS_NAME
    }
    winRTFundamentalTypeForName(effectiveTypeName)?.let { fundamentalType ->
        return fundamentalType.toProjectionTypeName().withOuterNullability(nullable)
    }
    if (isWinRTObjectTypeName(effectiveTypeName)) {
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
    return redirectedWinAppSdkAbiTypeExpression(typeName, useWinAppSdkTypeRedirects, emptyMap())
}

internal fun redirectedWinAppSdkAbiTypeExpression(
    typeName: String,
    useWinAppSdkTypeRedirects: Boolean,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): String {
    val trimmed = typeName.trim()
    val nullableSuffix = if (trimmed.endsWith("?")) "?" else ""
    val effectiveTypeName = trimmed.removeSuffix("?")
    val genericStart = effectiveTypeName.indexOf('<')
    if (genericStart >= 0 && effectiveTypeName.endsWith('>')) {
        val rawType = redirectedWinAppSdkAbiTypeName(
            effectiveTypeName.substring(0, genericStart),
            useWinAppSdkTypeRedirects,
        )
        val resolvedRawType = if (rawType != effectiveTypeName.substring(0, genericStart) &&
            !typesByQualifiedName.containsKey(rawType)
        ) {
            effectiveTypeName.substring(0, genericStart)
        } else {
            rawType
        }
        val arguments = splitGenericArguments(effectiveTypeName.substring(genericStart + 1, effectiveTypeName.length - 1))
            .joinToString(", ") { argument ->
                redirectedWinAppSdkAbiTypeExpression(argument, useWinAppSdkTypeRedirects, typesByQualifiedName)
            }
        return "$resolvedRawType<$arguments>$nullableSuffix"
    }
    val redirected = redirectedWinAppSdkAbiTypeName(effectiveTypeName, useWinAppSdkTypeRedirects)
    val resolved = if (redirected != effectiveTypeName && !typesByQualifiedName.containsKey(redirected)) {
        effectiveTypeName
    } else {
        redirected
    }
    return resolved + nullableSuffix
}

fun redirectedWinAppSdkProjectionSurfaceTypeReferences(type: WinRTTypeRef): List<WinRTTypeRef> {
    val redirected = redirectedWinAppSdkAbiTypeExpression(type.typeName, useWinAppSdkTypeRedirects = true)
    return if (redirected == type.typeName) {
        emptyList()
    } else {
        listOf(WinRTTypeRef.fromDisplayName(redirected))
    }
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
    type.kind == WinRTTypeKind.Struct &&
        type.fields.any { field ->
                !field.isStatic &&
                !field.isLiteral &&
                mappedTypeByAbiName(field.typeName.substringBefore('<').removeSuffix("?"))?.descriptionName == "TimeSpan"
        }

private fun TypeName.withOuterNullability(nullable: Boolean): TypeName =
    if (nullable) copy(nullable = true) else this

internal fun KotlinProjectionRenderer.resolveIntegralTypeName(type: WinRTIntegralType): TypeName =
    integralAbiDescriptor(type).kotlinTypeName

internal fun KotlinProjectionRenderer.integralLiteral(valueBits: ULong, type: WinRTIntegralType): CodeBlock =
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
