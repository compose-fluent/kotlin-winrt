package dev.winrt.winmd.parser

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import dev.winrt.winmd.plugin.WinMdTypeKind

internal fun splitGenericArguments(source: String): List<String> {
    if (source.isBlank()) {
        return emptyList()
    }
    return buildList {
        var depth = 0
        var start = 0
        source.forEachIndexed { index, char ->
            when (char) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    add(source.substring(start, index).trim())
                    start = index + 1
                }
            }
        }
        add(source.substring(start).trim())
    }
}

internal fun canonicalWinRtInterfaceName(interfaceName: String): String =
    interfaceName.substringBefore('<').substringBefore('`')

internal fun winRtCollectionProjectionTypeKey(typeName: String): String? =
    winRtCollectionProjectionTypeKeys[canonicalWinRtInterfaceName(typeName)]

internal fun isWinRtCollectionInterface(typeName: String): Boolean =
    winRtCollectionProjectionTypeKey(typeName) != null

internal fun TypeRegistry.signatureParameterType(type: String, currentNamespace: String): String =
    if (isEnumType(type, currentNamespace)) {
        enumSignatureType(this, type, currentNamespace)
    } else {
        type
    }

internal fun TypeRegistry.supportsClosedGenericInterfaceReturnType(
    typeName: String,
    currentNamespace: String,
    winRtSignatureMapper: WinRtSignatureMapper,
): Boolean = supportsClosedGenericProjectedType(
    typeName = typeName,
    currentNamespace = currentNamespace,
    winRtSignatureMapper = winRtSignatureMapper,
    supportedKinds = setOf(WinMdTypeKind.Interface),
    excludedRawTypeNames = setOf("Windows.Foundation.IReference"),
)

internal fun TypeRegistry.supportsClosedGenericProjectedType(
    typeName: String,
    currentNamespace: String,
    winRtSignatureMapper: WinRtSignatureMapper,
    supportedKinds: Set<WinMdTypeKind>,
    excludedRawTypeNames: Set<String> = emptySet(),
): Boolean {
    val rawTypeName = closedGenericRawTypeName(typeName) ?: return false
    if (rawTypeName in excludedRawTypeNames) {
        return false
    }
    val genericArgumentSource = typeName.substringAfter('<').substringBeforeLast('>')
    val hasResolvableArguments = splitGenericArguments(genericArgumentSource).all { argument ->
        try {
            winRtSignatureMapper.signatureFor(argument, currentNamespace)
            true
        } catch (_: IllegalStateException) {
            false
        }
    }
    if (!hasResolvableArguments) {
        return false
    }
    val rawType = findType(rawTypeName, currentNamespace)
    return when {
        rawType != null -> rawType.kind in supportedKinds
        '.' in rawTypeName -> true
        else -> false
    }
}

internal fun TypeRegistry.closedGenericInterfaceProjectionCall(
    typeName: String,
    currentNamespace: String,
    abiCall: CodeBlock,
    typeNameMapper: TypeNameMapper,
    winRtSignatureMapper: WinRtSignatureMapper,
    winRtProjectionTypeMapper: WinRtProjectionTypeMapper,
): CodeBlock? {
    val rawTypeName = closedGenericRawTypeName(typeName) ?: return null
    if (!supportsClosedGenericInterfaceReturnType(typeName, currentNamespace, winRtSignatureMapper)) {
        return null
    }
    val rawTypeClass = typeNameMapper.mapTypeName(rawTypeName, currentNamespace) as? ClassName ?: return null
    val genericArgumentSource = typeName.substringAfter('<').substringBeforeLast('>')
    val genericArguments = splitGenericArguments(genericArgumentSource)
    val builder = CodeBlock.builder()
        .add("%T.from(%T(%L)", rawTypeClass, PoetSymbols.inspectableClass, abiCall)
    genericArguments.forEach { argument ->
        builder.add(", %S", winRtSignatureMapper.signatureFor(argument, currentNamespace))
    }
    genericArguments.forEach { argument ->
        builder.add(", %S", winRtProjectionTypeMapper.projectionTypeKeyFor(argument, currentNamespace))
    }
    return builder.add(")").build()
}

internal fun twoArgumentReturnCode(returnType: String, abiCall: CodeBlock): CodeBlock =
    when (canonicalWinRtSpecialType(returnType)) {
        "String" -> HStringSupport.fromCall(abiCall)
        "Float32" -> CodeBlock.of("%T(%L)", PoetSymbols.float32Class, abiCall)
        "Float64" -> CodeBlock.of("%T(%L)", PoetSymbols.float64Class, abiCall)
        "DateTime" -> CodeBlock.of(
            "%T.fromEpochSeconds((%L - %L) / 10000000L, ((%L - %L) %% 10000000L * 100).toInt())",
            PoetSymbols.dateTimeClass,
            abiCall,
            WINDOWS_FOUNDATION_DATE_TIME_TICKS_OFFSET,
            abiCall,
            WINDOWS_FOUNDATION_DATE_TIME_TICKS_OFFSET,
        )
        "TimeSpan" -> CodeBlock.of("%T(%L)", PoetSymbols.timeSpanClass, abiCall)
        "Boolean" -> CodeBlock.of("%T(%L)", PoetSymbols.winRtBooleanClass, abiCall)
        "EventRegistrationToken" -> CodeBlock.of("%T(%L)", PoetSymbols.eventRegistrationTokenClass, abiCall)
        "HResult" -> CodeBlock.of("%M(%L)", PoetSymbols.exceptionFromHResultMember, abiCall)
        "Int32" -> CodeBlock.of("%T(%L)", PoetSymbols.int32Class, abiCall)
        "UInt32" -> CodeBlock.of("%T(%L)", PoetSymbols.uint32Class, abiCall)
        "Int64" -> CodeBlock.of("%T(%L)", PoetSymbols.int64Class, abiCall)
        "UInt64" -> CodeBlock.of("%T(%L)", PoetSymbols.uint64Class, abiCall)
        "Guid" -> CodeBlock.of("%T.parse(%L.toString())", PoetSymbols.guidValueClass, abiCall)
        else -> error("Unsupported two-argument return type: $returnType")
    }

internal data class ComMethodResultDescriptor(
    val kindName: String,
    val extractor: Any,
)

internal fun comMethodResultDescriptor(returnType: String): ComMethodResultDescriptor =
    when (canonicalWinRtSpecialType(returnType)) {
        "String" -> ComMethodResultDescriptor("HSTRING", PoetSymbols.requireHStringMember)
        "Float32" -> ComMethodResultDescriptor("FLOAT32", PoetSymbols.requireFloat32Member)
        "Float64" -> ComMethodResultDescriptor("FLOAT64", PoetSymbols.requireFloat64Member)
        "DateTime" -> ComMethodResultDescriptor("INT64", PoetSymbols.requireInt64Member)
        "TimeSpan" -> ComMethodResultDescriptor("INT64", PoetSymbols.requireInt64Member)
        "Boolean" -> ComMethodResultDescriptor("BOOLEAN", PoetSymbols.requireBooleanMember)
        "HResult" -> ComMethodResultDescriptor("INT32", PoetSymbols.requireInt32Member)
        "Int32" -> ComMethodResultDescriptor("INT32", PoetSymbols.requireInt32Member)
        "UInt32" -> ComMethodResultDescriptor("UINT32", PoetSymbols.requireUInt32Member)
        "Int64" -> ComMethodResultDescriptor("INT64", PoetSymbols.requireInt64Member)
        "UInt64" -> ComMethodResultDescriptor("UINT64", PoetSymbols.requireUInt64Member)
        "Guid" -> ComMethodResultDescriptor("GUID", PoetSymbols.requireGuidMember)
        else -> error("Unsupported result descriptor for two-argument return type: $returnType")
    }

internal fun supportsFillArrayResultKind(returnType: String): Boolean =
    when (canonicalWinRtSpecialType(returnType)) {
        "String",
        "Float32",
        "Float64",
        "DateTime",
        "TimeSpan",
        "Boolean",
        "HResult",
        "Int32",
        "UInt32",
        "Int64",
        "UInt64",
        "Guid",
        -> true
        else -> false
    }

internal fun int32ReturnCode(returnType: String, abiCall: CodeBlock): CodeBlock =
    if (isHResultType(returnType)) {
        CodeBlock.of("%M(%L)", PoetSymbols.exceptionFromHResultMember, abiCall)
    } else {
        CodeBlock.of("%T(%L)", PoetSymbols.int32Class, abiCall)
    }

private fun closedGenericRawTypeName(typeName: String): String? =
    typeName
        .takeIf { '<' in it && it.endsWith(">") }
        ?.substringBefore('<')

private val winRtCollectionProjectionTypeKeys = buildMap {
    val xamlBindableProjectionTypeKeys = mapOf(
        "IBindableIterable" to "kotlin.collections.Iterable",
        "IBindableIterator" to "kotlin.collections.Iterator",
        "IBindableVector" to "kotlin.collections.MutableList",
        "IBindableVectorView" to "kotlin.collections.List",
    )
    listOf("Microsoft.UI.Xaml.Interop", "Windows.UI.Xaml.Interop").forEach { namespace ->
        xamlBindableProjectionTypeKeys.forEach { (name, projectionTypeKey) ->
            put("$namespace.$name", projectionTypeKey)
        }
    }
    put("Windows.Foundation.Collections.IIterable", "kotlin.collections.Iterable")
    put("Windows.Foundation.Collections.IIterator", "kotlin.collections.Iterator")
    put("Windows.Foundation.Collections.IVector", "kotlin.collections.MutableList")
    put("Windows.Foundation.Collections.IVectorView", "kotlin.collections.List")
    put("Windows.Foundation.Collections.IMap", "kotlin.collections.MutableMap")
    put("Windows.Foundation.Collections.IMapView", "kotlin.collections.Map")
    put("Windows.Foundation.Collections.IKeyValuePair", "kotlin.collections.Map.Entry")
    put("Windows.Foundation.Collections.IObservableVector", "kotlin.collections.MutableList")
    put("Windows.Foundation.Collections.IObservableMap", "kotlin.collections.MutableMap")
}
