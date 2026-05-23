package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDirection
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRtAuthoringTypeDetailsRenderer {
    private val authoringTypeDetailsRegistrarPackage = "io.github.composefluent.winrt.projections.support"
    private val authoringTypeDetailsRegistrarName = "WinRTAuthoringTypeDetailsRegistrar"
    private val comAbiValueKindType = ClassName("io.github.composefluent.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.composefluent.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.composefluent.winrt.runtime", "Guid")
    private val hStringType = ClassName("io.github.composefluent.winrt.runtime", "HString")
    private val iInspectableReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IInspectableReference")
    private val iidType = ClassName("io.github.composefluent.winrt.runtime", "IID")
    private val knownHResultsType = ClassName("io.github.composefluent.winrt.runtime", "KnownHResults")
    private val platformAbiType = ClassName("io.github.composefluent.winrt.runtime", "PlatformAbi")
    private val projectionsType = ClassName("io.github.composefluent.winrt.runtime", "Projections")
    private val rawAddressType = ClassName("io.github.composefluent.winrt.runtime", "RawAddress")
    private val comWrappersSupportType = ClassName("io.github.composefluent.winrt.runtime", "ComWrappersSupport")
    private val winRtCcwDefinitionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtCcwDefinition")
    private val winRtInspectableInterfaceDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableInterfaceDefinition")
    private val winRtInspectableMethodDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableMethodDefinition")
    private val winRtIterableProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtIterableProjection")
    private val winRtListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtListProjection")
    private val winRtObjectMarshallerType = ClassName("io.github.composefluent.winrt.runtime", "WinRtObjectMarshaller")
    private val winRtReadOnlyListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReadOnlyListProjection")
    private val winRtReferenceValueAdaptersType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceValueAdapters")

    fun renderTo(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        metadataModel: WinRtMetadataModel,
        outputDirectory: Path,
    ) {
        val typesByName = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        val semanticHelpers = WinRtMetadataSemanticHelpers(metadataModel)
        val renderedCandidates = candidates.mapNotNull { candidate ->
            val interfaces = candidate.winRtInterfaceNames.mapNotNull(typesByName::get)
                .filter { type -> type.kind == WinRtTypeKind.Interface && type.iid != null }
            if (interfaces.isEmpty()) {
                return@mapNotNull null
            }
            val packageDirectory = outputDirectory.resolve(candidate.packageName.replace('.', '/'))
            packageDirectory.createDirectories()
            render(candidate, interfaces, typesByName, semanticHelpers).writeTo(outputDirectory)
            candidate
        }
        if (renderedCandidates.isNotEmpty()) {
            renderRegistrar(renderedCandidates).writeTo(outputDirectory)
        }
    }

    private fun render(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): FileSpec {
        val typeDetailsName = detailsObjectName(candidate)
        return FileSpec.builder(candidate.packageName, typeDetailsName)
            .addImport("io.github.composefluent.winrt.runtime", "abiLayout")
            .addType(
                TypeSpec.objectBuilder(typeDetailsName)
                    .addFunction(renderRegister(candidate))
                    .addFunction(renderCreateCcwDefinition(candidate, interfaces, typesByName, semanticHelpers))
                    .build(),
            )
            .build()
    }

    private fun renderRegister(candidate: KotlinWinRtAuthoredTypeCandidate): FunSpec =
        FunSpec.builder("register")
            .addAnnotation(JvmStatic::class)
            .addStatement(
                "%T.registerAuthoredRuntimeClassType(%T::class, %S)",
                projectionsType,
                sourceClassName(candidate),
                candidate.sourceTypeName,
            )
            .addStatement(
                "%T.registerAuthoringMetadataTypeMappings(mapOf(%S to %S))",
                comWrappersSupportType,
                candidate.sourceTypeName,
                "ABI.${candidate.sourceTypeName}",
            )
            .addStatement(
                "%T.registerAuthoringTypeDetailsFactory(%T::class, ::createCcwDefinition)",
                comWrappersSupportType,
                sourceClassName(candidate),
            )
            .build()

    private fun renderCreateCcwDefinition(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): FunSpec {
        val defaultInterface = interfaces.first()
        return FunSpec.builder("createCcwDefinition")
            .addAnnotation(JvmStatic::class)
            .addParameter(ParameterSpec.builder("value", ANY).build())
            .returns(winRtCcwDefinitionType)
            .addCode(
                CodeBlock.builder()
                    .add("return %T(\n", winRtCcwDefinitionType)
                    .indent()
                    .add("interfaceDefinitions = listOf(\n")
                    .indent()
                    .apply {
                        interfaces.forEach { type ->
                            add("%L,\n", renderInterface(candidate, type, typesByName, semanticHelpers))
                        }
                    }
                    .unindent()
                    .add("),\n")
                    .add("defaultInterfaceId = %T(%S),\n", guidType, defaultInterface.iid.toString().lowercase())
                    .add("runtimeClassName = %S,\n", candidate.sourceTypeName)
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build()
    }

    private fun renderRegistrar(candidates: List<KotlinWinRtAuthoredTypeCandidate>): FileSpec =
        FileSpec.builder(authoringTypeDetailsRegistrarPackage, authoringTypeDetailsRegistrarName)
            .addType(
                TypeSpec.objectBuilder(authoringTypeDetailsRegistrarName)
                    .addModifiers(KModifier.INTERNAL)
                    .addFunction(renderRegistrarRegister(candidates))
                    .build(),
            )
            .build()

    private fun renderRegistrarRegister(candidates: List<KotlinWinRtAuthoredTypeCandidate>): FunSpec {
        val code = CodeBlock.builder()
        candidates
            .sortedBy { candidate -> candidate.sourceTypeName }
            .forEach { candidate ->
                code.addStatement("%T.register()", ClassName(candidate.packageName, detailsObjectName(candidate)))
            }
        return FunSpec.builder("register")
            .addCode(code.build())
            .build()
    }

    private fun renderInterface(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        type: WinRtTypeDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        val dispatchBaseClassName = semanticHelpers.getExclusiveToType(type)?.qualifiedName
            ?: candidate.winRtBaseClassName
        return CodeBlock.builder()
            .add("%T(\n", winRtInspectableInterfaceDefinitionType)
            .indent()
            .add("interfaceId = %T(%S),\n", guidType, type.iid.toString().lowercase())
            .add("methods = listOf(\n")
            .indent()
            .apply {
                type.methods.filterNot { method -> method.isStatic }.forEach { method ->
                    add("%L,\n", renderMethod(method, typesByName, dispatchBaseClassName))
                }
            }
            .unindent()
            .add("),\n")
            .unindent()
            .add(")")
            .build()
    }

    private fun renderMethod(
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        dispatchBaseClassName: String?,
    ): CodeBlock {
        val dispatchBase = dispatchBaseClassName
            ?: error("Authored WinRT override ${method.name} has no declaring WinRT base class.")
        val bridgeMethodName = authoringInvokeBridgeName(method)
        val bridgeArguments = method.parameters.indices.joinToString(", ") { index -> "__arg$index" }
        return CodeBlock.builder()
            .add("%T(%L) { rawArgs ->\n", winRtInspectableMethodDefinitionType, renderSignature(method, typesByName))
            .indent()
            .apply {
                method.parameters.forEachIndexed { index, parameter ->
                    add("%L", renderParameterProjectionStatement(index, parameter, typesByName))
                }
            }
            .apply {
                if (isVoidReturn(method)) {
                    addStatement("(value as %T).%L(%L)", projectionClassName(dispatchBase), bridgeMethodName, bridgeArguments)
                } else {
                    addStatement(
                        "val __result = (value as %T).%L(%L)",
                        projectionClassName(dispatchBase),
                        bridgeMethodName,
                        bridgeArguments,
                    )
                    add(
                        "%L\n",
                        renderReturnProjection(
                            method,
                            "rawArgs[${method.parameters.size}] as RawAddress",
                            "__result",
                            typesByName,
                        ),
                    )
                }
            }
            .addStatement("%T.S_OK.value", knownHResultsType)
            .add("}")
            .build()
    }

    private fun renderParameterProjection(
        index: Int,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val rawArg = CodeBlock.of("rawArgs[%L]", index)
        if (isWinRtObjectTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromAbi(%L as %T)", winRtObjectMarshallerType, rawArg, rawAddressType)
        }
        return when (parameter.typeName) {
            "Boolean" -> CodeBlock.of("(%L as %T).toInt() != 0", rawArg, Byte::class.asClassName())
            "Int8", "SByte" -> CodeBlock.of("%L as %T", rawArg, Byte::class.asClassName())
            "UInt8", "Byte" -> CodeBlock.of("(%L as %T).toUByte()", rawArg, Byte::class.asClassName())
            "Int16" -> CodeBlock.of("%L as %T", rawArg, Short::class.asClassName())
            "UInt16" -> CodeBlock.of("(%L as %T).toUShort()", rawArg, Short::class.asClassName())
            "Int32" -> CodeBlock.of("%L as %T", rawArg, Int::class.asClassName())
            "UInt32" -> CodeBlock.of("(%L as %T).toUInt()", rawArg, Int::class.asClassName())
            "Int64" -> CodeBlock.of("%L as %T", rawArg, Long::class.asClassName())
            "UInt64" -> CodeBlock.of("(%L as %T).toULong()", rawArg, Long::class.asClassName())
            "Single", "Float" -> CodeBlock.of("%L as %T", rawArg, Float::class.asClassName())
            "Double" -> CodeBlock.of("%L as %T", rawArg, Double::class.asClassName())
            "String" -> CodeBlock.of("%T.fromHandle(%L as %T, owner = false).use { it.toKString() }", hStringType, rawArg, rawAddressType)
            else -> renderComplexParameterProjection(rawArg, parameter, typesByName)
        }
    }

    private fun renderParameterProjectionStatement(
        index: Int,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock =
        if (parameter.typeName == "String") {
            CodeBlock.builder()
                .addStatement(
                    "val __hString%L = %T.fromHandle(rawArgs[%L] as %T, owner = false)",
                    index,
                    hStringType,
                    index,
                    rawAddressType,
                )
                .add("val __arg%L = try {\n", index)
                .indent()
                .addStatement("__hString%L.toKString()", index)
                .unindent()
                .add("} finally {\n")
                .indent()
                .addStatement("__hString%L.close()", index)
                .unindent()
                .add("}\n")
                .build()
        } else {
            CodeBlock.of("val __arg%L = %L\n", index, renderParameterProjection(index, parameter, typesByName))
        }

    private fun renderComplexParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        if (parameter.direction == WinRtParameterDirection.Out || parameter.typeIsByRef) {
            return CodeBlock.of("%L as %T", rawArg, rawAddressType)
        }
        val type = typesByName[parameter.typeName]
        return when (type?.kind) {
            WinRtTypeKind.Enum -> CodeBlock.of(
                "%T.Metadata.fromAbi(%L)",
                projectionClassName(parameter.typeName),
                renderEnumRawArgument(rawArg, type),
            )
            WinRtTypeKind.Struct -> CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", projectionClassName(parameter.typeName), rawArg, rawAddressType)
            else -> {
                CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(%L as %T), %T.IInspectable, preventReleaseOnDispose = true))",
                    projectionClassName(parameter.typeName),
                    iInspectableReferenceType,
                    platformAbiType,
                    rawArg,
                    rawAddressType,
                    iidType,
                )
            }
        }
    }

    private fun renderEnumRawArgument(rawArg: CodeBlock, type: WinRtTypeDefinition): CodeBlock =
        when (type.enumUnderlyingType) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%L as %T", rawArg, Byte::class.asClassName())
            WinRtIntegralType.UInt8 -> CodeBlock.of("(%L as %T).toUByte()", rawArg, Byte::class.asClassName())
            WinRtIntegralType.Int16 -> CodeBlock.of("%L as %T", rawArg, Short::class.asClassName())
            WinRtIntegralType.UInt16 -> CodeBlock.of("(%L as %T).toUShort()", rawArg, Short::class.asClassName())
            WinRtIntegralType.Int64 -> CodeBlock.of("%L as %T", rawArg, Long::class.asClassName())
            WinRtIntegralType.UInt64 -> CodeBlock.of("(%L as %T).toULong()", rawArg, Long::class.asClassName())
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32,
            null -> CodeBlock.of("%L as %T", rawArg, Int::class.asClassName())
        }

    private fun renderSignature(
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val kinds = method.parameters.map { parameter -> abiKindName(parameter, typesByName) } +
            listOfNotNull("Pointer".takeUnless { isVoidReturn(method) })
        return if (kinds.isEmpty()) {
            CodeBlock.of("%T()", comMethodSignatureType)
        } else {
            CodeBlock.builder()
                .add("%T.of(", comMethodSignatureType)
                .apply {
                    kinds.forEachIndexed { index, kind ->
                        if (index > 0) {
                            add(", ")
                        }
                        if (kind.startsWith("Struct:")) {
                            add(
                                "%T.Struct(%T.Metadata.layout.abiLayout)",
                                comAbiValueKindType,
                                projectionClassName(kind.removePrefix("Struct:")),
                            )
                        } else {
                            add("%T.%L", comAbiValueKindType, kind)
                        }
                    }
                }
                .add(")")
                .build()
        }
    }

    private fun abiKindName(
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): String =
        when (parameter.typeName) {
            "Int8", "SByte", "UInt8", "Byte", "Boolean" -> "Int8"
            "Int16", "UInt16" -> "Int16"
            "Int32", "UInt32" -> "Int32"
            "Int64", "UInt64" -> "Int64"
            "Single", "Float" -> "Float"
            "Double" -> "Double"
            else -> {
                val type = typesByName[parameter.typeName]
                when {
                    type?.kind == WinRtTypeKind.Enum -> enumAbiKindName(type)
                    type?.kind == WinRtTypeKind.Struct &&
                        parameter.direction != WinRtParameterDirection.Out &&
                        !parameter.typeIsByRef -> "Struct:${parameter.typeName}"
                    else -> "Pointer"
                }
            }
        }

    private fun enumAbiKindName(type: WinRtTypeDefinition): String =
        when (type.enumUnderlyingType) {
            WinRtIntegralType.Int8,
            WinRtIntegralType.UInt8 -> "Int8"
            WinRtIntegralType.Int16,
            WinRtIntegralType.UInt16 -> "Int16"
            WinRtIntegralType.Int64,
            WinRtIntegralType.UInt64 -> "Int64"
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32,
            null -> "Int32"
        }

    private fun renderReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: String,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock =
        if (isWinRtObjectTypeName(method.returnTypeName)) {
            CodeBlock.of(
                "%T.writePointer(%L, %T.fromManaged(%L))",
                platformAbiType,
                outExpression,
                winRtObjectMarshallerType,
                valueExpression,
            )
        } else when (method.returnTypeName) {
            "Boolean" -> CodeBlock.of(
                "%T.writeInt8(%L, if (%L as %T) 1.toByte() else 0.toByte())",
                platformAbiType,
                outExpression,
                valueExpression,
                Boolean::class.asClassName(),
            )
            "Int8", "SByte" -> CodeBlock.of("%T.writeInt8(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Byte::class.asClassName())
            "UInt8", "Byte" -> CodeBlock.of("%T.writeInt8(%L, (%L as %T).toByte())", platformAbiType, outExpression, valueExpression, UByte::class.asClassName())
            "Int16" -> CodeBlock.of("%T.writeInt16(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Short::class.asClassName())
            "UInt16" -> CodeBlock.of("%T.writeInt16(%L, (%L as %T).toShort())", platformAbiType, outExpression, valueExpression, UShort::class.asClassName())
            "Int32" -> CodeBlock.of("%T.writeInt32(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Int::class.asClassName())
            "UInt32" -> CodeBlock.of("%T.writeInt32(%L, (%L as %T).toInt())", platformAbiType, outExpression, valueExpression, UInt::class.asClassName())
            "Int64" -> CodeBlock.of("%T.writeInt64(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Long::class.asClassName())
            "UInt64" -> CodeBlock.of("%T.writeInt64(%L, (%L as %T).toLong())", platformAbiType, outExpression, valueExpression, ULong::class.asClassName())
            "Single", "Float" -> CodeBlock.of("%T.writeFloat(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Float::class.asClassName())
            "Double" -> CodeBlock.of("%T.writeDouble(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Double::class.asClassName())
            "String" -> CodeBlock.of("%T.writePointer(%L, %T.create(%L as %T).handle)", platformAbiType, outExpression, hStringType, valueExpression, String::class.asClassName())
            else -> {
                renderCollectionReturnProjection(method.returnTypeName, outExpression, valueExpression, typesByName)?.let {
                    return it
                }
                val returnType = typesByName[method.returnTypeName]
                when (returnType?.kind) {
                    WinRtTypeKind.Enum -> renderEnumReturnProjection(method.returnTypeName, returnType, outExpression, valueExpression)
                    WinRtTypeKind.Struct -> CodeBlock.of(
                        "%T.Metadata.copyTo(%L as %T, %L)",
                        projectionClassName(method.returnTypeName),
                        valueExpression,
                        projectionClassName(method.returnTypeName),
                        outExpression,
                    )
                    else -> renderObjectReturnProjection(returnType, outExpression, valueExpression, typesByName)
                }
            }
        }

    private fun renderCollectionReturnProjection(
        returnTypeName: String,
        outExpression: String,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val returnType = WinRtTypeRef.fromDisplayName(returnTypeName).normalized()
        val collectionTypeName = returnType.qualifiedName ?: return null
        val elementType = returnType.typeArguments.singleOrNull()?.normalized() ?: return null
        val elementAdapter = renderCollectionElementAdapter(elementType, typesByName) ?: return null
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRtIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRtReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRtListProjectionType
            else -> return null
        }
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L, %L))",
            platformAbiType,
            outExpression,
            projectionType,
            valueExpression,
            elementAdapter,
        )
    }

    private fun renderCollectionElementAdapter(
        elementType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val elementTypeName = elementType.qualifiedName ?: return null
        if (isWinRtObjectTypeName(elementTypeName)) {
            return CodeBlock.of("%T.object_", winRtReferenceValueAdaptersType)
        }
        return when (elementTypeName) {
            "String" -> CodeBlock.of("%T.string", winRtReferenceValueAdaptersType)
            else -> {
                val elementDefinition = typesByName[elementTypeName] ?: return null
                when (elementDefinition.kind) {
                    WinRtTypeKind.RuntimeClass -> CodeBlock.of(
                        "%T.runtimeClass(%T::class, %S, %T.Metadata.DEFAULT_INTERFACE_IID) { %T.Metadata.wrap(it) }",
                        winRtReferenceValueAdaptersType,
                        projectionClassName(elementTypeName),
                        elementTypeName,
                        projectionClassName(elementTypeName),
                        projectionClassName(elementTypeName),
                    )
                    else -> null
                }
            }
        }
    }

    private fun renderObjectReturnProjection(
        returnType: WinRtTypeDefinition?,
        outExpression: String,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val interfaceId = when (returnType?.kind) {
            WinRtTypeKind.RuntimeClass -> returnType.defaultInterfaceName
                ?.let { defaultInterfaceName ->
                    typesByName[defaultInterfaceName]
                        ?: typesByName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                }
                ?.iid
                ?.let { CodeBlock.of("%T(%S)", guidType, it.toString().lowercase()) }
            WinRtTypeKind.Interface -> returnType.iid?.let { CodeBlock.of("%T(%S)", guidType, it.toString().lowercase()) }
            else -> null
        } ?: CodeBlock.of("%T.IInspectable", iidType)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachCCWForObject(%L, %L))",
            platformAbiType,
            outExpression,
            comWrappersSupportType,
            valueExpression,
            interfaceId,
        )
    }

    private fun renderEnumReturnProjection(
        typeName: String,
        type: WinRtTypeDefinition,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val abiValue = CodeBlock.of("%T.Metadata.toAbi(%L as %T)", projectionClassName(typeName), valueExpression, projectionClassName(typeName))
        return when (type.enumUnderlyingType) {
            WinRtIntegralType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", platformAbiType, outExpression, abiValue)
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32,
            null -> CodeBlock.of("%T.writeInt32(%L, %L)", platformAbiType, outExpression, abiValue)
        }
    }

    private fun isVoidReturn(method: WinRtMethodDefinition): Boolean =
        method.returnTypeName == "Void" || method.returnTypeName == "System.Void" || method.returnTypeName == "Unit"

    private fun authoringInvokeBridgeName(method: WinRtMethodDefinition): String =
        "__winrtAuthoringInvoke${method.name}"

    private fun detailsObjectName(candidate: KotlinWinRtAuthoredTypeCandidate): String =
        "WinRT_${candidate.className.replace('$', '_')}_TypeDetails"

    private fun sourceClassName(candidate: KotlinWinRtAuthoredTypeCandidate): ClassName {
        val names = candidate.className.split('$').filter(String::isNotBlank)
        return ClassName(candidate.packageName, names.first(), *names.drop(1).toTypedArray())
    }

    private fun projectionClassName(qualifiedName: String): ClassName {
        val lastDot = qualifiedName.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", qualifiedName)
        }
        val namespace = qualifiedName.substring(0, lastDot)
        val simpleName = qualifiedName.substring(lastDot + 1)
        val packageName = namespace.split('.')
            .filter(String::isNotBlank)
            .joinToString(".") { it.lowercase() }
        return ClassName(packageName, simpleName)
    }
}
