package io.github.kitectlab.winrt.gradle

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRtAuthoringTypeDetailsRenderer {
    private val comAbiValueKindType = ClassName("io.github.kitectlab.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.kitectlab.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.kitectlab.winrt.runtime", "Guid")
    private val knownHResultsType = ClassName("io.github.kitectlab.winrt.runtime", "KnownHResults")
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
        val sourceMethodName = method.name.replaceFirstChar(Char::lowercaseChar)
        return CodeBlock.builder()
            .add("%T(%L) {\n", winRtInspectableMethodDefinitionType, renderSignature(method))
            .indent()
            .addStatement("val method = value.javaClass.getDeclaredMethod(%S)", sourceMethodName)
            .addStatement("method.isAccessible = true")
            .addStatement("method.invoke(value)")
            .addStatement("%T.S_OK.value", knownHResultsType)
            .unindent()
            .add("}")
            .build()
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
}
