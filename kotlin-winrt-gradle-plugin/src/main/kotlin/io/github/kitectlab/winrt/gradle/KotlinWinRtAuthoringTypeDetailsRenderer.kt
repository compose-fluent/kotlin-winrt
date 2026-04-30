package io.github.kitectlab.winrt.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtParameterDirection
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRtAuthoringTypeDetailsRenderer {
    private val comAbiValueKindType = ClassName("io.github.kitectlab.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.kitectlab.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.kitectlab.winrt.runtime", "Guid")
    private val hStringType = ClassName("io.github.kitectlab.winrt.runtime", "HString")
    private val iInspectableReferenceType = ClassName("io.github.kitectlab.winrt.runtime", "IInspectableReference")
    private val iidType = ClassName("io.github.kitectlab.winrt.runtime", "IID")
    private val knownHResultsType = ClassName("io.github.kitectlab.winrt.runtime", "KnownHResults")
    private val platformAbiType = ClassName("io.github.kitectlab.winrt.runtime", "PlatformAbi")
    private val rawAddressType = ClassName("io.github.kitectlab.winrt.runtime", "RawAddress")
    private val winRtCcwDefinitionType = ClassName("io.github.kitectlab.winrt.runtime", "WinRtCcwDefinition")
    private val winRtInspectableInterfaceDefinitionType =
        ClassName("io.github.kitectlab.winrt.runtime", "WinRtInspectableInterfaceDefinition")
    private val winRtInspectableMethodDefinitionType =
        ClassName("io.github.kitectlab.winrt.runtime", "WinRtInspectableMethodDefinition")

    fun renderTo(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        metadataModel: WinRtMetadataModel,
        outputDirectory: Path,
    ) {
        val typesByName = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        candidates.forEach { candidate ->
            val interfaces = candidate.winRtInterfaceNames.mapNotNull(typesByName::get)
                .filter { type -> type.kind == WinRtTypeKind.Interface && type.iid != null }
            if (interfaces.isEmpty()) {
                return@forEach
            }
            val packageDirectory = outputDirectory.resolve(candidate.packageName.replace('.', '/'))
            packageDirectory.createDirectories()
            render(candidate, interfaces).writeTo(outputDirectory)
        }
    }

    private fun render(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
    ): FileSpec {
        val typeDetailsName = detailsObjectName(candidate)
        return FileSpec.builder(candidate.packageName, typeDetailsName)
            .addType(
                TypeSpec.objectBuilder(typeDetailsName)
                    .addFunction(renderCreateCcwDefinition(candidate, interfaces))
                    .build(),
            )
            .build()
    }

    private fun renderCreateCcwDefinition(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
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
                            add("%L,\n", renderInterface(type))
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

    private fun renderInterface(type: WinRtTypeDefinition): CodeBlock =
        CodeBlock.builder()
            .add("%T(\n", winRtInspectableInterfaceDefinitionType)
            .indent()
            .add("interfaceId = %T(%S),\n", guidType, type.iid.toString().lowercase())
            .add("methods = listOf(\n")
            .indent()
            .apply {
                type.methods.filterNot { method -> method.isStatic }.forEach { method ->
                    add("%L,\n", renderMethod(method))
                }
            }
            .unindent()
            .add("),\n")
            .unindent()
            .add(")")
            .build()

    private fun renderMethod(
        method: WinRtMethodDefinition,
    ): CodeBlock {
        val sourceMethodName = method.name
        return CodeBlock.builder()
            .add("%T(%L) { rawArgs ->\n", winRtInspectableMethodDefinitionType, renderSignature(method))
            .indent()
            .apply {
                method.parameters.forEachIndexed { index, parameter ->
                    addStatement("val __arg%L = %L", index, renderParameterProjection(index, parameter))
                }
            }
            .add(
                "val method = generateSequence(value.javaClass) { type -> type.superclass }\n" +
                    ".mapNotNull { type -> runCatching { type.getDeclaredMethod(%S%L) }.getOrNull() }\n" +
                    ".first()\n",
                sourceMethodName,
                renderReflectionParameterTypes(method),
            )
            .addStatement("method.isAccessible = true")
            .addStatement("method.invoke(value%L)", renderInvokeArguments(method))
            .addStatement("%T.S_OK.value", knownHResultsType)
            .unindent()
            .add("}")
            .build()
    }

    private fun renderReflectionParameterTypes(method: WinRtMethodDefinition): CodeBlock =
        CodeBlock.builder()
            .apply {
                method.parameters.forEach { parameter ->
                    add(", %T::class.java", projectedParameterType(parameter))
                }
            }
            .build()

    private fun renderInvokeArguments(method: WinRtMethodDefinition): CodeBlock =
        CodeBlock.builder()
            .apply {
                method.parameters.indices.forEach { index ->
                    add(", __arg%L", index)
                }
            }
            .build()

    private fun renderParameterProjection(
        index: Int,
        parameter: WinRtParameterDefinition,
    ): CodeBlock {
        val rawArg = CodeBlock.of("rawArgs[%L]", index)
        return when (parameter.typeName) {
            "Boolean" -> CodeBlock.of("(%L as %T) != 0", rawArg, Int::class.asClassName())
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
            else -> renderObjectParameterProjection(rawArg, parameter)
        }
    }

    private fun renderObjectParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
    ): CodeBlock =
        if (parameter.direction == WinRtParameterDirection.Out || parameter.typeIsByRef) {
            CodeBlock.of("%L as %T", rawArg, rawAddressType)
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

    private fun projectedParameterType(parameter: WinRtParameterDefinition) =
        when (parameter.typeName) {
            "Boolean" -> Boolean::class.asClassName()
            "Int8", "SByte" -> Byte::class.asClassName()
            "UInt8", "Byte" -> UByte::class.asClassName()
            "Int16" -> Short::class.asClassName()
            "UInt16" -> UShort::class.asClassName()
            "Int32" -> Int::class.asClassName()
            "UInt32" -> UInt::class.asClassName()
            "Int64" -> Long::class.asClassName()
            "UInt64" -> ULong::class.asClassName()
            "Single", "Float" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "String" -> String::class.asClassName()
            else -> if (parameter.direction == WinRtParameterDirection.Out || parameter.typeIsByRef) {
                rawAddressType
            } else {
                projectionClassName(parameter.typeName)
            }
        }

    private fun renderSignature(method: WinRtMethodDefinition): CodeBlock =
        if (method.parameters.isEmpty()) {
            CodeBlock.of("%T()", comMethodSignatureType)
        } else {
            CodeBlock.builder()
                .add("%T.of(", comMethodSignatureType)
                .apply {
                    method.parameters.forEachIndexed { index, parameter ->
                        if (index > 0) {
                            add(", ")
                        }
                        add("%T.%L", comAbiValueKindType, abiKindName(parameter))
                    }
                }
                .add(")")
                .build()
        }

    private fun abiKindName(parameter: WinRtParameterDefinition): String =
        when (parameter.typeName) {
            "Int8", "SByte" -> "Int8"
            "Int16" -> "Int16"
            "Int32", "UInt32", "Boolean" -> "Int32"
            "Int64", "UInt64" -> "Int64"
            "Single", "Float" -> "Float"
            "Double" -> "Double"
            else -> "Pointer"
        }

    private fun detailsObjectName(candidate: KotlinWinRtAuthoredTypeCandidate): String =
        "WinRT_${candidate.className.replace('$', '_')}_TypeDetails"

    private fun projectionClassName(qualifiedName: String): ClassName {
        val lastDot = qualifiedName.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", qualifiedName)
        }
        val namespace = qualifiedName.substring(0, lastDot)
        val simpleName = qualifiedName.substring(lastDot + 1)
        val packageName = (listOf("io", "github", "kitectlab", "winrt", "projections") +
            namespace.split('.').filter(String::isNotBlank).map(String::lowercase))
            .joinToString(".")
        return ClassName(packageName, simpleName)
    }
}
