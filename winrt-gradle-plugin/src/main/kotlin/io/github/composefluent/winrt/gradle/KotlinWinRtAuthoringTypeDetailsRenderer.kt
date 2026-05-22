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
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDirection
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
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
    private val winRtObjectMarshallerType = ClassName("io.github.composefluent.winrt.runtime", "WinRtObjectMarshaller")

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
            "System.Object", "Object" -> CodeBlock.of("%T.fromAbi(%L as %T)", winRtObjectMarshallerType, rawArg, rawAddressType)
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
        return if (typesByName[parameter.typeName]?.kind == WinRtTypeKind.Struct) {
            CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", projectionClassName(parameter.typeName), rawArg, rawAddressType)
        } else {
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
            else -> if (typesByName[parameter.typeName]?.kind == WinRtTypeKind.Struct &&
                parameter.direction != WinRtParameterDirection.Out &&
                !parameter.typeIsByRef
            ) {
                "Struct:${parameter.typeName}"
            } else {
                "Pointer"
            }
        }

    private fun renderReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: String,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock =
        when (method.returnTypeName) {
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
            "System.Object", "Object" -> CodeBlock.of(
                "%T.createMarshaler(%L).use { __returnMarshaler -> %T.writePointer(%L, __returnMarshaler.abi) }",
                winRtObjectMarshallerType,
                valueExpression,
                platformAbiType,
                outExpression,
            )
            else -> if (typesByName[method.returnTypeName]?.kind == WinRtTypeKind.Struct) {
                CodeBlock.of(
                    "%T.Metadata.copyTo(%L as %T, %L)",
                    projectionClassName(method.returnTypeName),
                    valueExpression,
                    projectionClassName(method.returnTypeName),
                    outExpression,
                )
            } else {
                CodeBlock.builder()
                    .addStatement(
                        "val __returnReference = %T.createCCWForObject(%L, %T.IInspectable)",
                        comWrappersSupportType,
                        valueExpression,
                        iidType,
                    )
                    .add("try {\n")
                    .indent()
                    .addStatement(
                        "%T.writePointer(%L, %T.fromRawComPtr(__returnReference.getRefPointer()))",
                        platformAbiType,
                        outExpression,
                        platformAbiType,
                    )
                    .unindent()
                    .add("} finally {\n")
                    .indent()
                    .addStatement("__returnReference.close()")
                    .unindent()
                    .add("}")
                    .build()
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
