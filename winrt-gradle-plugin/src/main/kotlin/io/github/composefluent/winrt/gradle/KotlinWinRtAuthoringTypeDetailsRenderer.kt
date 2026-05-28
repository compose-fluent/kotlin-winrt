package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRtFundamentalType
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDirection
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRtVoidTypeName
import io.github.composefluent.winrt.metadata.winRtFundamentalTypeForName
import org.gradle.api.GradleException
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRtAuthoringTypeDetailsRenderer {
    private val authoringTypeDetailsRegistrarPackage = "io.github.composefluent.winrt.projections.support"
    private val authoringTypeDetailsRegistrarName = "WinRTAuthoringTypeDetailsRegistrar"
    private val comAbiValueKindType = ClassName("io.github.composefluent.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.composefluent.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.composefluent.winrt.runtime", "Guid")
    private val hStringType = ClassName("io.github.composefluent.winrt.runtime", "HString")
    private val iUnknownReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IUnknownReference")
    private val iInspectableReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IInspectableReference")
    private val iidType = ClassName("io.github.composefluent.winrt.runtime", "IID")
    private val knownHResultsType = ClassName("io.github.composefluent.winrt.runtime", "KnownHResults")
    private val parameterizedInterfaceIdType = ClassName("io.github.composefluent.winrt.runtime", "ParameterizedInterfaceId")
    private val platformAbiType = ClassName("io.github.composefluent.winrt.runtime", "PlatformAbi")
    private val projectionsType = ClassName("io.github.composefluent.winrt.runtime", "Projections")
    private val rawAddressType = ClassName("io.github.composefluent.winrt.runtime", "RawAddress")
    private val comWrappersSupportType = ClassName("io.github.composefluent.winrt.runtime", "ComWrappersSupport")
    private val winRtCcwDefinitionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtCcwDefinition")
    private val winRtInspectableInterfaceDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableInterfaceDefinition")
    private val winRtInspectableMethodDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableMethodDefinition")
    private val winRtCollectionInterfaceIdsType = ClassName("io.github.composefluent.winrt.runtime", "WinRtCollectionInterfaceIds")
    private val winRtIterableProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtIterableProjection")
    private val winRtListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtListProjection")
    private val winRtObjectMarshallerType = ClassName("io.github.composefluent.winrt.runtime", "WinRtObjectMarshaller")
    private val winRtReadOnlyListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReadOnlyListProjection")
    private val winRtReferenceValueAdapterType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceValueAdapter")
    private val winRtReferenceValueAdaptersType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceValueAdapters")
    private val winRtTypeSignatureType = ClassName("io.github.composefluent.winrt.runtime", "WinRtTypeSignature")
    private val enumIntegralAbiDescriptors = mapOf(
        WinRtIntegralType.Int8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            abiKindName = "Int8",
            writeFunctionName = "writeInt8",
        ),
        WinRtIntegralType.UInt8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            abiKindName = "Int8",
            rawCarrierConversionSuffix = ".toUByte()",
            writeFunctionName = "writeInt8",
            abiWriteConversionSuffix = ".toByte()",
        ),
        WinRtIntegralType.Int16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            abiKindName = "Int16",
            writeFunctionName = "writeInt16",
        ),
        WinRtIntegralType.UInt16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            abiKindName = "Int16",
            rawCarrierConversionSuffix = ".toUShort()",
            writeFunctionName = "writeInt16",
            abiWriteConversionSuffix = ".toShort()",
        ),
        WinRtIntegralType.Int32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            abiKindName = "Int32",
            writeFunctionName = "writeInt32",
        ),
        WinRtIntegralType.UInt32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            abiKindName = "Int32",
            rawCarrierConversionSuffix = ".toUInt()",
            writeFunctionName = "writeInt32",
            abiWriteConversionSuffix = ".toInt()",
        ),
        WinRtIntegralType.Int64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            abiKindName = "Int64",
            writeFunctionName = "writeInt64",
        ),
        WinRtIntegralType.UInt64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            abiKindName = "Int64",
            rawCarrierConversionSuffix = ".toULong()",
            writeFunctionName = "writeInt64",
            abiWriteConversionSuffix = ".toLong()",
        ),
    )

    fun renderTo(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        metadataModel: WinRtMetadataModel,
        outputDirectory: Path,
    ) {
        val typesByName = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        val semanticHelpers = WinRtMetadataSemanticHelpers(metadataModel)
        val renderedCandidates = candidates.map { candidate ->
            val interfaces = resolveAuthoringInterfaces(candidate, typesByName)
            val packageDirectory = outputDirectory.resolve(candidate.packageName.replace('.', '/'))
            packageDirectory.createDirectories()
            render(candidate, interfaces, typesByName, semanticHelpers).writeTo(outputDirectory)
            candidate
        }
        renderRegistrar(renderedCandidates).writeTo(outputDirectory)
    }

    private fun resolveAuthoringInterfaces(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): List<WinRtTypeDefinition> {
        if (candidate.winRtInterfaceNames.isEmpty()) {
            throw GradleException(
                "Authored type '${candidate.sourceTypeName}' has no WinRT interfaces for TypeDetails generation.",
            )
        }
        return candidate.winRtInterfaceNames.map { interfaceName ->
            val type = typesByName[interfaceName]
                ?: throw GradleException(
                    "Authored type '${candidate.sourceTypeName}' references missing WinRT interface '$interfaceName'.",
                )
            if (type.kind != WinRtTypeKind.Interface) {
                throw GradleException(
                    "Authored type '${candidate.sourceTypeName}' references non-interface WinRT type '$interfaceName'.",
                )
            }
            if (type.iid == null) {
                throw GradleException(
                    "Authored type '${candidate.sourceTypeName}' references WinRT interface '$interfaceName' without metadata IID.",
                )
            }
            type
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
            .addAnnotation(generatedAuthoringTypeDetailsSuppressAnnotation())
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
                    add("%L,\n", renderMethod(method, typesByName, semanticHelpers, dispatchBaseClassName))
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
        semanticHelpers: WinRtMetadataSemanticHelpers,
        dispatchBaseClassName: String?,
    ): CodeBlock {
        val dispatchBase = dispatchBaseClassName
            ?: error("Authored WinRT override ${method.name} has no declaring WinRT base class.")
        val bridgeMethodName = authoringInvokeBridgeName(method)
        val bridgeArguments = method.parameters.indices.joinToString(", ") { index -> "__arg$index" }
        return CodeBlock.builder()
            .add("%T(%L) { rawArgs ->\n", winRtInspectableMethodDefinitionType, renderSignature(method, typesByName, semanticHelpers))
            .indent()
            .apply {
                method.parameters.forEachIndexed { index, parameter ->
                    add("%L", renderParameterProjectionStatement(index, parameter, typesByName, semanticHelpers))
                }
            }
            .apply {
                if (isVoidReturn(method)) {
                    addStatement("(value as %T).%L(%L)", projectionClassName(dispatchBase, semanticHelpers), bridgeMethodName, bridgeArguments)
                } else {
                    addStatement(
                        "val __result = (value as %T).%L(%L)",
                        projectionClassName(dispatchBase, semanticHelpers),
                        bridgeMethodName,
                        bridgeArguments,
                    )
                    add(
                        "%L\n",
                        renderReturnProjection(
                            method,
                            CodeBlock.of("rawArgs[%L] as %T", method.parameters.size, rawAddressType),
                            "__result",
                            typesByName,
                            semanticHelpers,
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
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        val rawArg = CodeBlock.of("rawArgs[%L]", index)
        if (isWinRtObjectTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromAbi(%L as %T)", winRtObjectMarshallerType, rawArg, rawAddressType)
        }
        if (isWinRtStringTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromHandle(%L as %T, owner = false).use { it.toKString() }", hStringType, rawArg, rawAddressType)
        }
        fundamentalType(parameter.typeName)?.let { type ->
            renderFundamentalParameterProjection(rawArg, type)?.let { projection ->
                return projection
            }
        }
        return renderComplexParameterProjection(rawArg, parameter, typesByName, semanticHelpers)
    }

    private fun renderParameterProjectionStatement(
        index: Int,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock =
        if (isWinRtStringTypeName(parameter.typeName)) {
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
            CodeBlock.of("val __arg%L = %L\n", index, renderParameterProjection(index, parameter, typesByName, semanticHelpers))
        }

    private fun renderComplexParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        if (parameter.direction == WinRtParameterDirection.Out || parameter.typeIsByRef) {
            return CodeBlock.of("%L as %T", rawArg, rawAddressType)
        }
        val type = typesByName[parameter.typeName]
        return when (type?.kind) {
            WinRtTypeKind.Enum -> CodeBlock.of(
                "%T.Metadata.fromAbi(%L)",
                projectionClassName(parameter.typeName, semanticHelpers),
                renderEnumRawArgument(rawArg, type),
            )
            WinRtTypeKind.Struct -> CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", projectionClassName(parameter.typeName, semanticHelpers), rawArg, rawAddressType)
            WinRtTypeKind.RuntimeClass,
            WinRtTypeKind.Interface,
            -> {
                CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(%L as %T), %T.IInspectable, preventReleaseOnDispose = true))",
                    projectionClassName(parameter.typeName, semanticHelpers),
                    iInspectableReferenceType,
                    platformAbiType,
                    rawArg,
                    rawAddressType,
                    iidType,
                )
            }
            null -> throw GradleException(
                "Authored WinRT override parameter '${parameter.name}' of type '${parameter.typeName}' has no metadata.",
            )
            else -> throw GradleException(
                "Authored WinRT override parameter '${parameter.name}' has unsupported object type '${parameter.typeName}'.",
            )
        }
    }

    private fun renderEnumRawArgument(rawArg: CodeBlock, type: WinRtTypeDefinition): CodeBlock =
        enumIntegralAbiDescriptor(type).let { descriptor ->
            val carrier = CodeBlock.of("%L as %T", rawArg, descriptor.carrierTypeName)
            if (descriptor.rawCarrierConversionSuffix.isEmpty()) {
                carrier
            } else {
                CodeBlock.of("(%L)%L", carrier, descriptor.rawCarrierConversionSuffix)
            }
        }

    private fun renderFundamentalParameterProjection(
        rawArg: CodeBlock,
        type: WinRtFundamentalType,
    ): CodeBlock? =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of("(%L as %T).toInt() != 0", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.Char -> CodeBlock.of("(%L as %T).toInt().toChar()", rawArg, Short::class.asClassName())
            WinRtFundamentalType.Int8 -> CodeBlock.of("%L as %T", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.UInt8 -> CodeBlock.of("(%L as %T).toUByte()", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.Int16 -> CodeBlock.of("%L as %T", rawArg, Short::class.asClassName())
            WinRtFundamentalType.UInt16 -> CodeBlock.of("(%L as %T).toUShort()", rawArg, Short::class.asClassName())
            WinRtFundamentalType.Int32 -> CodeBlock.of("%L as %T", rawArg, Int::class.asClassName())
            WinRtFundamentalType.UInt32 -> CodeBlock.of("(%L as %T).toUInt()", rawArg, Int::class.asClassName())
            WinRtFundamentalType.Int64 -> CodeBlock.of("%L as %T", rawArg, Long::class.asClassName())
            WinRtFundamentalType.UInt64 -> CodeBlock.of("(%L as %T).toULong()", rawArg, Long::class.asClassName())
            WinRtFundamentalType.Float -> CodeBlock.of("%L as %T", rawArg, Float::class.asClassName())
            WinRtFundamentalType.Double -> CodeBlock.of("%L as %T", rawArg, Double::class.asClassName())
            WinRtFundamentalType.String -> null
        }

    private fun renderSignature(
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
                                projectionClassName(kind.removePrefix("Struct:"), semanticHelpers),
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
    ): String {
        fundamentalType(parameter.typeName)?.let { type ->
            return fundamentalAbiKindName(type)
        }
        val type = typesByName[parameter.typeName]
        return when {
            type?.kind == WinRtTypeKind.Enum -> enumAbiKindName(type)
            type?.kind == WinRtTypeKind.Struct &&
                parameter.direction != WinRtParameterDirection.Out &&
                !parameter.typeIsByRef -> "Struct:${parameter.typeName}"
            else -> "Pointer"
        }
    }

    private fun fundamentalAbiKindName(type: WinRtFundamentalType): String =
        when (type) {
            WinRtFundamentalType.Boolean,
            WinRtFundamentalType.Int8,
            WinRtFundamentalType.UInt8 -> "Int8"
            WinRtFundamentalType.Char,
            WinRtFundamentalType.Int16,
            WinRtFundamentalType.UInt16 -> "Int16"
            WinRtFundamentalType.Int32,
            WinRtFundamentalType.UInt32 -> "Int32"
            WinRtFundamentalType.Int64,
            WinRtFundamentalType.UInt64 -> "Int64"
            WinRtFundamentalType.Float -> "Float"
            WinRtFundamentalType.Double -> "Double"
            WinRtFundamentalType.String -> "Pointer"
        }

    private fun enumAbiKindName(type: WinRtTypeDefinition): String =
        enumIntegralAbiDescriptor(type).abiKindName

    private fun renderReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        if (isWinRtObjectTypeName(method.returnTypeName)) {
            return CodeBlock.of(
                "%T.writePointer(%L, %T.fromManaged(%L))",
                platformAbiType,
                outExpression,
                winRtObjectMarshallerType,
                valueExpression,
            )
        }
        fundamentalType(method.returnTypeName)?.let { type ->
            return renderFundamentalReturnProjection(type, outExpression, valueExpression)
        }
        renderCollectionReturnProjection(method, outExpression, valueExpression, typesByName, semanticHelpers)?.let {
            return it
        }
        val returnType = typesByName[method.returnTypeName]
        return when (returnType?.kind) {
            WinRtTypeKind.Enum -> renderEnumReturnProjection(method.returnTypeName, returnType, outExpression, valueExpression, semanticHelpers)
            WinRtTypeKind.Struct -> CodeBlock.of(
                "%T.Metadata.copyTo(%L as %T, %L)",
                projectionClassName(method.returnTypeName, semanticHelpers),
                valueExpression,
                projectionClassName(method.returnTypeName, semanticHelpers),
                outExpression,
            )
            else -> renderObjectReturnProjection(method, returnType, outExpression, valueExpression, typesByName)
        }
    }

    private fun renderFundamentalReturnProjection(
        type: WinRtFundamentalType,
        outExpression: CodeBlock,
        valueExpression: String,
    ): CodeBlock =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of(
                "%T.writeInt8(%L, if (%L as %T) 1.toByte() else 0.toByte())",
                platformAbiType,
                outExpression,
                valueExpression,
                Boolean::class.asClassName(),
            )
            WinRtFundamentalType.Char -> CodeBlock.of(
                "%T.writeInt16(%L, (%L as %T).code.toShort())",
                platformAbiType,
                outExpression,
                valueExpression,
                Char::class.asClassName(),
            )
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Byte::class.asClassName())
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, (%L as %T).toByte())", platformAbiType, outExpression, valueExpression, UByte::class.asClassName())
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Short::class.asClassName())
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, (%L as %T).toShort())", platformAbiType, outExpression, valueExpression, UShort::class.asClassName())
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Int::class.asClassName())
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, (%L as %T).toInt())", platformAbiType, outExpression, valueExpression, UInt::class.asClassName())
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Long::class.asClassName())
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, (%L as %T).toLong())", platformAbiType, outExpression, valueExpression, ULong::class.asClassName())
            WinRtFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Float::class.asClassName())
            WinRtFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Double::class.asClassName())
            WinRtFundamentalType.String -> CodeBlock.of("%T.writePointer(%L, %T.create(%L as %T).handle)", platformAbiType, outExpression, hStringType, valueExpression, String::class.asClassName())
        }

    private fun isWinRtStringTypeName(typeName: String): Boolean =
        fundamentalType(typeName) == WinRtFundamentalType.String

    private fun fundamentalType(typeName: String): WinRtFundamentalType? =
        winRtFundamentalTypeForName(typeName)

    private fun renderCollectionReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val returnTypeName = method.returnTypeName
        val returnType = WinRtTypeRef.fromDisplayName(returnTypeName).normalized()
        val collectionTypeName = returnType.qualifiedName ?: return null
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRtIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRtReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRtListProjectionType
            else -> return null
        }
        val elementType = returnType.typeArguments.singleOrNull()?.normalized()
            ?: throw GradleException(
                "Authored WinRT override ${method.name} returns collection type '$returnTypeName' without exactly one element type.",
            )
        val elementAdapter = renderCollectionElementAdapter(method, elementType, typesByName, semanticHelpers)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L, %L))",
            platformAbiType,
            outExpression,
            projectionType,
            valueExpression,
            elementAdapter,
        )
    }

    private fun WinRtTypeRef.displayName(): String =
        qualifiedName ?: toString()

    private fun renderCollectionElementAdapter(
        method: WinRtMethodDefinition,
        elementType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        val elementTypeName = elementType.qualifiedName
            ?: throw GradleException(
                "Authored WinRT override ${method.name} returns collection element '${elementType.displayName()}' without a qualified type name.",
            )
        renderNestedCollectionElementAdapter(method, elementType, typesByName, semanticHelpers)?.let { return it }
        if (isWinRtObjectTypeName(elementTypeName)) {
            return CodeBlock.of("%T.object_", winRtReferenceValueAdaptersType)
        }
        if (isWinRtStringTypeName(elementTypeName)) {
            return CodeBlock.of("%T.string", winRtReferenceValueAdaptersType)
        }
        val elementDefinition = typesByName[elementTypeName]
            ?: throw GradleException(
                "Authored WinRT override ${method.name} returns collection element type '$elementTypeName' without metadata.",
            )
        return when (elementDefinition.kind) {
            WinRtTypeKind.RuntimeClass -> CodeBlock.of(
                "%T.runtimeClass(%T::class, %S, %T.Metadata.DEFAULT_INTERFACE_IID) { %T.Metadata.wrap(it) }",
                winRtReferenceValueAdaptersType,
                projectionClassName(elementTypeName, semanticHelpers),
                elementTypeName,
                projectionClassName(elementTypeName, semanticHelpers),
                projectionClassName(elementTypeName, semanticHelpers),
            )
            WinRtTypeKind.Struct -> {
                val projectedType = runtimeMappedClassName(elementTypeName, semanticHelpers)
                    ?: throw GradleException(
                        "Authored WinRT override ${method.name} returns unsupported collection element type '$elementTypeName'.",
                    )
                CodeBlock.of(
                    "%T.valueType(%T::class, %S, %L)",
                    winRtReferenceValueAdaptersType,
                    projectedType,
                    elementTypeName,
                    renderWinRtTypeSignature(elementType, typesByName),
                )
            }
            else -> throw GradleException(
                "Authored WinRT override ${method.name} returns unsupported collection element type '$elementTypeName'.",
            )
        }
    }

    private fun renderNestedCollectionElementAdapter(
        method: WinRtMethodDefinition,
        elementType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val elementTypeName = elementType.qualifiedName ?: return null
        val descriptor = when (elementTypeName) {
            "Windows.Foundation.Collections.IIterable" -> NestedCollectionProjectionDescriptor(
                projectedType = Iterable::class.asClassName(),
                projectionType = winRtIterableProjectionType,
                signatureFunctionName = "iterableSignature",
            )
            "Windows.Foundation.Collections.IVectorView" -> NestedCollectionProjectionDescriptor(
                projectedType = List::class.asClassName(),
                projectionType = winRtReadOnlyListProjectionType,
                signatureFunctionName = "vectorViewSignature",
            )
            "Windows.Foundation.Collections.IVector" -> NestedCollectionProjectionDescriptor(
                projectedType = MutableList::class.asClassName(),
                projectionType = winRtListProjectionType,
                signatureFunctionName = "vectorSignature",
            )
            else -> return null
        }
        val nestedElementType = elementType.typeArguments.singleOrNull()?.normalized()
            ?: throw GradleException(
                "Authored WinRT override ${method.name} returns collection element type '${elementType.typeName}' without exactly one nested element type.",
            )
        val nestedElementAdapter = renderCollectionElementAdapter(method, nestedElementType, typesByName, semanticHelpers)
        val nestedProjectedType = authoringProjectedTypeName(nestedElementType, typesByName, semanticHelpers)
        val projectedType = descriptor.projectedType.parameterizedBy(nestedProjectedType)
        val typeSignature = renderWinRtTypeSignature(elementType, typesByName)
        return CodeBlock.of(
            "%T<%T>(projectedTypeName = %S, typeSignature = %L, projector = { reference -> if (reference == null) emptyList() else %T.fromAbi(%T.fromRawComPtr(reference.pointer), %L) ?: emptyList() }, marshaller = { value -> %T(%T.toRawComPtr(%T.fromManaged(value, %L)), %T.createFromSignature(%L)) })",
            winRtReferenceValueAdapterType,
            projectedType,
            elementType.typeName,
            typeSignature,
            descriptor.projectionType,
            platformAbiType,
            nestedElementAdapter,
            iUnknownReferenceType,
            platformAbiType,
            descriptor.projectionType,
            nestedElementAdapter,
            parameterizedInterfaceIdType,
            typeSignature,
        )
    }

    private fun authoringProjectedTypeName(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): TypeName {
        val typeName = type.qualifiedName
            ?: throw GradleException("Authored WinRT collection element '${type.displayName()}' has no projected type name.")
        renderNestedCollectionProjectedType(type, typesByName, semanticHelpers)?.let { return it }
        if (isWinRtObjectTypeName(typeName)) {
            return ANY.copy(nullable = true)
        }
        if (isWinRtStringTypeName(typeName)) {
            return String::class.asClassName()
        }
        val definition = typesByName[typeName]
            ?: throw GradleException("Authored WinRT collection element type '$typeName' has no metadata.")
        return when (definition.kind) {
            WinRtTypeKind.RuntimeClass -> projectionClassName(typeName, semanticHelpers)
            WinRtTypeKind.Struct -> runtimeMappedClassName(typeName, semanticHelpers)
                ?: throw GradleException("Authored WinRT collection element type '$typeName' is not projectable.")
            else -> throw GradleException("Authored WinRT collection element type '$typeName' is not projectable.")
        }
    }

    private fun renderNestedCollectionProjectedType(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): TypeName? {
        val projectedType = when (type.qualifiedName) {
            "Windows.Foundation.Collections.IIterable" -> Iterable::class.asClassName()
            "Windows.Foundation.Collections.IVectorView" -> List::class.asClassName()
            "Windows.Foundation.Collections.IVector" -> MutableList::class.asClassName()
            else -> return null
        }
        val argument = type.typeArguments.singleOrNull()?.normalized()
            ?: throw GradleException("Authored WinRT collection element type '${type.typeName}' has no projected type argument.")
        return projectedType.parameterizedBy(authoringProjectedTypeName(argument, typesByName, semanticHelpers))
    }

    private fun renderWinRtTypeSignature(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val typeName = type.qualifiedName
            ?: throw GradleException("Authored WinRT collection element '${type.displayName()}' has no type signature name.")
        when (typeName) {
            "Windows.Foundation.Collections.IIterable" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.iterableSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVectorView" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorViewSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVector" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
        }
        if (isWinRtObjectTypeName(typeName)) {
            return CodeBlock.of("%T.object_()", winRtTypeSignatureType)
        }
        if (isWinRtStringTypeName(typeName)) {
            return CodeBlock.of("%T.string()", winRtTypeSignatureType)
        }
        fundamentalType(typeName)?.let { type ->
            return renderFundamentalTypeSignature(type)
        }
        val definition = typesByName[typeName]
            ?: throw GradleException("Authored WinRT collection element type '$typeName' has no metadata signature.")
        return when (definition.kind) {
            WinRtTypeKind.RuntimeClass -> CodeBlock.of("%T.object_()", winRtTypeSignatureType)
            WinRtTypeKind.Struct -> CodeBlock.of(
                "%T.struct(%S%L)",
                winRtTypeSignatureType,
                typeName,
                definition.fields.joinToString(separator = "") { field ->
                    ", ${renderWinRtTypeSignature(WinRtTypeRef.fromDisplayName(field.typeName).normalized(), typesByName)}"
                },
            )
            else -> throw GradleException("Authored WinRT collection element type '$typeName' has no supported type signature.")
        }
    }

    private fun renderFundamentalTypeSignature(type: WinRtFundamentalType): CodeBlock =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of("%T.boolean()", winRtTypeSignatureType)
            WinRtFundamentalType.Char -> CodeBlock.of("%T.char16()", winRtTypeSignatureType)
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.int8()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.uint8()", winRtTypeSignatureType)
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.int16()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.uint16()", winRtTypeSignatureType)
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.int32()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.uint32()", winRtTypeSignatureType)
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.int64()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.uint64()", winRtTypeSignatureType)
            WinRtFundamentalType.Float -> CodeBlock.of("%T.float32()", winRtTypeSignatureType)
            WinRtFundamentalType.Double -> CodeBlock.of("%T.float64()", winRtTypeSignatureType)
            WinRtFundamentalType.String -> CodeBlock.of("%T.string()", winRtTypeSignatureType)
        }

    private fun renderObjectReturnProjection(
        method: WinRtMethodDefinition,
        returnType: WinRtTypeDefinition?,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val interfaceId = when (returnType?.kind) {
            WinRtTypeKind.RuntimeClass -> {
                val defaultInterfaceName = returnType.defaultInterfaceName
                    ?: throw GradleException(
                        "Authored WinRT override ${method.name} returns runtime class '${method.returnTypeName}' without default interface metadata.",
                    )
                val defaultInterface = typesByName[defaultInterfaceName]
                    ?: typesByName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                    ?: throw GradleException(
                        "Authored WinRT override ${method.name} returns runtime class '${method.returnTypeName}' whose default interface '$defaultInterfaceName' is missing.",
                    )
                val iid = defaultInterface.iid
                    ?: throw GradleException(
                        "Authored WinRT override ${method.name} returns runtime class '${method.returnTypeName}' whose default interface '$defaultInterfaceName' has no IID.",
                    )
                CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
            }
            WinRtTypeKind.Interface -> {
                val iid = returnType.iid
                    ?: throw GradleException(
                        "Authored WinRT override ${method.name} returns interface '${method.returnTypeName}' without IID metadata.",
                    )
                CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
            }
            null -> throw GradleException(
                "Authored WinRT override ${method.name} returns '${method.returnTypeName}' without metadata.",
            )
            else -> throw GradleException(
                "Authored WinRT override ${method.name} returns unsupported object type '${method.returnTypeName}'.",
            )
        }
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
        outExpression: CodeBlock,
        valueExpression: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        val descriptor = enumIntegralAbiDescriptor(type)
        val abiValue = CodeBlock.of("%T.Metadata.toAbi(%L as %T)", projectionClassName(typeName, semanticHelpers), valueExpression, projectionClassName(typeName, semanticHelpers))
        val writeValue = if (descriptor.abiWriteConversionSuffix.isEmpty()) {
            abiValue
        } else {
            CodeBlock.of("%L%L", abiValue, descriptor.abiWriteConversionSuffix)
        }
        return CodeBlock.of("%T.%L(%L, %L)", platformAbiType, descriptor.writeFunctionName, outExpression, writeValue)
    }

    private fun isVoidReturn(method: WinRtMethodDefinition): Boolean =
        isWinRtVoidTypeName(method.returnTypeName)

    private fun authoringInvokeBridgeName(method: WinRtMethodDefinition): String =
        "__winrtAuthoringInvoke${method.name}"

    private fun detailsObjectName(candidate: KotlinWinRtAuthoredTypeCandidate): String =
        "WinRT_${candidate.className.replace('$', '_')}_TypeDetails"

    private fun generatedAuthoringTypeDetailsSuppressAnnotation(): AnnotationSpec =
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "USELESS_CAST")
            .addMember("%S", "UNCHECKED_CAST")
            .build()

    private fun enumIntegralAbiDescriptor(type: WinRtTypeDefinition): AuthoringEnumIntegralAbiDescriptor =
        enumIntegralAbiDescriptors.getValue(type.enumUnderlyingType ?: WinRtIntegralType.Int32)

    private data class AuthoringEnumIntegralAbiDescriptor(
        val carrierTypeName: ClassName,
        val abiKindName: String,
        val rawCarrierConversionSuffix: String = "",
        val writeFunctionName: String,
        val abiWriteConversionSuffix: String = "",
    )

    private data class NestedCollectionProjectionDescriptor(
        val projectedType: ClassName,
        val projectionType: ClassName,
        val signatureFunctionName: String,
    )

    private fun sourceClassName(candidate: KotlinWinRtAuthoredTypeCandidate): ClassName {
        val names = candidate.className.split('$').filter(String::isNotBlank)
        return ClassName(candidate.packageName, names.first(), *names.drop(1).toTypedArray())
    }

    private fun projectionClassName(
        qualifiedName: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): ClassName {
        runtimeMappedClassName(qualifiedName, semanticHelpers)?.let { return it }
        return classNameFromWinRtName(qualifiedName)
    }

    private fun runtimeMappedClassName(
        qualifiedName: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): ClassName? =
        semanticHelpers.getMappedType(WinRtTypeRef.fromDisplayName(qualifiedName), "")
            ?.mappedQualifiedName
            ?.takeIf { mappedName -> mappedName.startsWith("io.github.composefluent.winrt.runtime.") }
            ?.let(::classNameFromQualifiedName)

    private fun classNameFromWinRtName(qualifiedName: String): ClassName {
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

    private fun classNameFromQualifiedName(qualifiedName: String): ClassName {
        val lastDot = qualifiedName.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", qualifiedName)
        }
        return ClassName(qualifiedName.substring(0, lastDot), qualifiedName.substring(lastDot + 1))
    }
}
