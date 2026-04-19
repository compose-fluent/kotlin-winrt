package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind

private val ROOT_PACKAGE_SEGMENTS = listOf("io", "github", "kitectlab", "winrt", "projections")

enum class KotlinProjectionDeclarationKind {
    Interface,
    Class,
}

data class KotlinTypeProjectionPlan(
    val type: WinRtTypeDefinition,
    val packageName: String,
    val relativePath: String,
    val declarationKind: KotlinProjectionDeclarationKind,
)

data class KotlinProjectionFile(
    val relativePath: String,
    val packageName: String,
    val contents: String,
)

class KotlinProjectionPlanner {
    fun plan(model: WinRtMetadataModel): List<KotlinTypeProjectionPlan> =
        model.normalized().namespaces.flatMap(::planNamespace)

    fun planNamespace(namespace: WinRtNamespace): List<KotlinTypeProjectionPlan> =
        namespace.normalized().types.mapNotNull(::planType)

    private fun planType(type: WinRtTypeDefinition): KotlinTypeProjectionPlan? {
        val declarationKind = when (type.kind) {
            WinRtTypeKind.Interface -> KotlinProjectionDeclarationKind.Interface
            WinRtTypeKind.RuntimeClass -> KotlinProjectionDeclarationKind.Class
            else -> return null
        }
        val packageName = (ROOT_PACKAGE_SEGMENTS + namespaceSegments(type.namespace)).joinToString(".")
        val relativePath = packageName.replace('.', '/') + "/${type.name}.kt"
        return KotlinTypeProjectionPlan(
            type = type,
            packageName = packageName,
            relativePath = relativePath,
            declarationKind = declarationKind,
        )
    }

    private fun namespaceSegments(namespace: String): List<String> =
        namespace.split('.')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
}

class KotlinProjectionRenderer {
    fun render(plan: KotlinTypeProjectionPlan): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = plan.relativePath,
            packageName = plan.packageName,
            contents = buildString {
                appendLine("package ${plan.packageName}")
                appendLine()
                when (plan.declarationKind) {
                    KotlinProjectionDeclarationKind.Interface -> append(renderInterface(plan))
                    KotlinProjectionDeclarationKind.Class -> append(renderClass(plan))
                }
            }.trimEnd() + "\n",
        )

    private fun renderInterface(plan: KotlinTypeProjectionPlan): String = buildString {
        appendLine("interface ${plan.type.name} {")
        if (plan.type.methods.isEmpty()) {
            appendLine("}")
            return@buildString
        }
        plan.type.methods.forEach { method ->
            appendLine("    ${renderSignature(method)}")
        }
        appendLine("}")
    }

    private fun renderClass(plan: KotlinTypeProjectionPlan): String = buildString {
        val instanceMethods = plan.type.methods.filterNot { it.isStatic }
        val staticMethods = plan.type.methods.filter { it.isStatic }

        appendLine("class ${plan.type.name} {")
        instanceMethods.forEach { method ->
            appendLine("    ${renderSignature(method)} = error(\"Not yet bound to winrt-runtime\")")
        }
        if (staticMethods.isNotEmpty()) {
            if (instanceMethods.isNotEmpty()) {
                appendLine()
            }
            appendLine("    companion object {")
            staticMethods.forEach { method ->
                appendLine("        ${renderSignature(method)} = error(\"Not yet bound to winrt-runtime\")")
            }
            appendLine("    }")
        }
        appendLine("}")
    }

    private fun renderSignature(method: WinRtMethodDefinition): String {
        val parameters = method.parameters.joinToString(", ") { parameter ->
            "${parameter.name}: ${parameter.typeName}"
        }
        return "fun ${method.name}($parameters): ${method.returnTypeName}"
    }
}

class KotlinProjectionGenerator(
    private val planner: KotlinProjectionPlanner = KotlinProjectionPlanner(),
    private val renderer: KotlinProjectionRenderer = KotlinProjectionRenderer(),
) {
    fun generate(model: WinRtMetadataModel): List<KotlinProjectionFile> =
        planner.plan(model).map(renderer::render)
}