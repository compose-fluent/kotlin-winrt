package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.metadata.WinRTComInteropAdapterDescriptor
import io.github.composefluent.winrt.metadata.WinRTComInteropMethodDescriptor
import io.github.composefluent.winrt.metadata.WinRTComInteropParameterDescriptor
import io.github.composefluent.winrt.metadata.WinRTComInteropParameterType
import io.github.composefluent.winrt.metadata.WinRTComInteropResultDescriptor
import io.github.composefluent.winrt.metadata.WinRTComInteropRuntimeClassIidSource
import io.github.composefluent.winrt.metadata.WinRTMetadataLookupIndex
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.lookupIndex

internal class KotlinComInteropSourceRenderer(
    private val typeRenderer: KotlinProjectionRenderer = KotlinProjectionRenderer(),
    private val modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport =
        inlineOnlyModulePlatformAbiCallSupport(),
) {
    fun render(
        descriptor: WinRTComInteropAdapterDescriptor,
        model: WinRTMetadataModel,
    ): KotlinProjectionFile {
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)
        val metadataLookupIndex = model.lookupIndex()
        val queryIidName = "I_${descriptor.name.toScreamingSnakeCase()}_IID"
        val methods = descriptor.methods.map { method ->
            when (method.result) {
                is WinRTComInteropResultDescriptor.ProjectedRuntimeClass ->
                    renderRuntimeClassMethod(descriptor, method, queryIidName, typesByQualifiedName)
                WinRTComInteropResultDescriptor.UnitResult ->
                    renderUnitMethod(descriptor, method, queryIidName)
                WinRTComInteropResultDescriptor.AsyncAction,
                is WinRTComInteropResultDescriptor.AsyncOperation,
                -> renderAsyncMethod(descriptor, method, queryIidName, typesByQualifiedName)
            }
        }
        val constantResultIids = descriptor.methods
            .mapNotNull { method ->
                val result = method.result as? WinRTComInteropResultDescriptor.ProjectedRuntimeClass
                    ?: return@mapNotNull null
                val iid = (result.iidSource as? WinRTComInteropRuntimeClassIidSource.Constant)?.iid
                    ?: return@mapNotNull null
                result.typeName to iid
            }
            .distinctBy { (typeName, _) -> typeName }
        val foreignProjectedImports = renderForeignProjectedImports(descriptor, metadataLookupIndex, typesByQualifiedName)
        val contents = buildString {
            appendLine("package ${descriptor.projectedPackageName}")
            appendLine()
            appendLine("import io.github.composefluent.winrt.runtime.ActivationFactory")
            appendLine("import io.github.composefluent.winrt.runtime.Guid")
            appendLine("import io.github.composefluent.winrt.runtime.IUnknownReference")
            appendLine("import io.github.composefluent.winrt.runtime.PlatformAbi")
            appendLine("import io.github.composefluent.winrt.runtime.RawAddress")
            appendLine("import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference")
            appendLine("import io.github.composefluent.winrt.runtime.WinRTAsyncInterfaceIds")
            appendLine("import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference")
            appendLine("import io.github.composefluent.winrt.runtime.WinRTAsyncProjectionInterop")
            appendLine("import io.github.composefluent.winrt.runtime.WinRTTypeSignature")
            foreignProjectedImports.forEach { importName ->
                appendLine("import $importName")
            }
            appendLine()
            appendLine("public object ${descriptor.name} {")
            appendLine("    private val $queryIidName: Guid = Guid(\"${descriptor.queryIid}\")")
            constantResultIids.forEach { (typeName, iid) ->
                appendLine(
                    "    private val ${typeName.substringAfterLast('.').toScreamingSnakeCase()}_RESULT_IID: Guid = " +
                        "Guid(\"$iid\")",
                )
            }
            if (methods.isNotEmpty()) {
                appendLine()
            }
            methods.forEachIndexed { index, methodSource ->
                append(methodSource.prependIndent("    "))
                if (index != methods.lastIndex) {
                    appendLine()
                    appendLine()
                } else {
                    appendLine()
                }
            }
            appendLine("}")
        }
        return KotlinProjectionFile(
            relativePath = descriptor.projectedTypeName.replace('.', '/') + ".kt",
            packageName = descriptor.projectedPackageName,
            contents = contents,
        )
    }

    private fun renderForeignProjectedImports(
        descriptor: WinRTComInteropAdapterDescriptor,
        metadataLookupIndex: WinRTMetadataLookupIndex,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): List<String> =
        descriptor.requiredTypeNames
            .asSequence()
            .map { typeName ->
                metadataLookupIndex.canonicalType(typeName, descriptor.namespace)
            }
            .map { canonicalType ->
                canonicalType.definitionQualifiedName
                    ?.takeIf { canonicalType.definitionType?.genericParameterCount != 0 }
                    ?.let(::projectionClassNameForQualifiedName)
                    ?: typeRenderer.resolveTypeName(
                        typeRenderer.renderAbiTypeBinding(
                            canonicalType.displayName,
                            typesByQualifiedName,
                            descriptor.namespace,
                        ).typeName,
                    )
            }
            .filterIsInstance<ClassName>()
            .filter { className ->
                className.packageName.isNotBlank() &&
                    className.packageName != descriptor.projectedPackageName &&
                    !className.packageName.startsWith("io.github.composefluent.winrt.runtime") &&
                    !className.packageName.startsWith("kotlin")
            }
            .map(ClassName::canonicalName)
            .distinct()
            .sorted()
            .toList()

    private fun renderRuntimeClassMethod(
        descriptor: WinRTComInteropAdapterDescriptor,
        method: WinRTComInteropMethodDescriptor,
        queryIidName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String {
        val result = method.result as? WinRTComInteropResultDescriptor.ProjectedRuntimeClass
            ?: unsupportedMethod(descriptor, method, "expected a projected runtime-class result")
        if (method.parameters.any { parameter -> parameter.type != WinRTComInteropParameterType.RawAddress }) {
            unsupportedMethod(descriptor, method, "runtime-class results only support RawAddress parameters")
        }
        val resultBinding = typeRenderer.renderAbiTypeBinding(
            result.typeName,
            typesByQualifiedName,
            descriptor.namespace,
        )
        val resultTypeName = projectionClassNameForQualifiedName(resultBinding.resolvedTypeName).simpleName
        val activationTypeName = descriptor.activationTypeName.substringAfterLast('.')
        val resultIidExpression = when (val iidSource = result.iidSource) {
            WinRTComInteropRuntimeClassIidSource.DefaultInterface ->
                "$resultTypeName.Metadata.DEFAULT_INTERFACE_IID"
            is WinRTComInteropRuntimeClassIidSource.Constant ->
                "${resultTypeName.toScreamingSnakeCase()}_RESULT_IID"
        }
        val publicParameters = method.parameters.joinToString(", ") { parameter ->
            "${parameter.name}: RawAddress"
        }
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = typeRenderer.renderAbiTypeBinding("RawAddress"),
            )
        } + KotlinProjectionAbiParameterBinding(
            name = "resultIid",
            typeBinding = typeRenderer.renderAbiTypeBinding("RawAddress"),
        )
        val invocation = renderCallSite(
            descriptor = descriptor,
            method = method,
            returnBinding = KotlinProjectionAbiTypeBinding(
                KotlinProjectionAbiValueKind.InspectableReference,
                IINSPECTABLE_REFERENCE_CLASS_NAME.canonicalName,
            ),
            parameterBindings = parameterBindings,
        )
        return buildString {
            appendLine("public fun ${method.name}($publicParameters): $resultTypeName =")
            appendLine("    PlatformAbi.confinedScope().use { scope ->")
            appendLine("        val resultIid = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())")
            appendLine("        PlatformAbi.writeGuid(resultIid, $resultIidExpression)")
            appendLine("        ActivationFactory.get($activationTypeName.Metadata.TYPE_NAME, $queryIidName).use { interop ->")
            appendLine("            $resultTypeName.Metadata.wrap(")
            appendLine(invocation.renderCallSiteSource(descriptor.projectedPackageName).prependIndent("                "))
            appendLine("            )")
            appendLine("        }")
            append("    }")
        }
    }

    private fun renderUnitMethod(
        descriptor: WinRTComInteropAdapterDescriptor,
        method: WinRTComInteropMethodDescriptor,
        queryIidName: String,
    ): String {
        if (method.result != WinRTComInteropResultDescriptor.UnitResult) {
            unsupportedMethod(descriptor, method, "expected a unit result")
        }
        if (method.parameters.any { parameter -> parameter.type != WinRTComInteropParameterType.RawAddress }) {
            unsupportedMethod(descriptor, method, "unit results only support RawAddress parameters")
        }
        val activationTypeName = descriptor.activationTypeName.substringAfterLast('.')
        val publicParameters = method.parameters.joinToString(", ") { parameter ->
            "${parameter.name}: RawAddress"
        }
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = typeRenderer.renderAbiTypeBinding("RawAddress"),
            )
        }
        val invocation = renderCallSite(
            descriptor = descriptor,
            method = method,
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
            parameterBindings = parameterBindings,
        )
        return buildString {
            appendLine("public fun ${method.name}($publicParameters) {")
            appendLine("    ActivationFactory.get($activationTypeName.Metadata.TYPE_NAME, $queryIidName).use { interop ->")
            appendLine(invocation.renderCallSiteSource(descriptor.projectedPackageName).prependIndent("        "))
            appendLine("    }")
            append("}")
        }
    }

    private fun renderAsyncMethod(
        descriptor: WinRTComInteropAdapterDescriptor,
        method: WinRTComInteropMethodDescriptor,
        queryIidName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String {
        val result = method.result
        val asyncReturnTypeName = when (result) {
            WinRTComInteropResultDescriptor.AsyncAction -> "Windows.Foundation.IAsyncAction"
            is WinRTComInteropResultDescriptor.AsyncOperation ->
                "Windows.Foundation.IAsyncOperation<${result.resultTypeName}>"
            WinRTComInteropResultDescriptor.UnitResult,
            is WinRTComInteropResultDescriptor.ProjectedRuntimeClass,
            -> unsupportedMethod(descriptor, method, "expected an async result")
        }
        val asyncReturnBinding = typeRenderer.renderAbiTypeBinding(
            asyncReturnTypeName,
            typesByQualifiedName,
            descriptor.namespace,
        )
        val publicReturnType = when (result) {
            WinRTComInteropResultDescriptor.AsyncAction -> "WinRTAsyncActionReference"
            is WinRTComInteropResultDescriptor.AsyncOperation -> {
                val resultBinding = typeRenderer.renderAbiTypeBinding(
                    result.resultTypeName,
                    typesByQualifiedName,
                    descriptor.namespace,
                )
                "WinRTAsyncOperationReference<${projectionClassNameForQualifiedName(resultBinding.resolvedTypeName).simpleName}>"
            }
            WinRTComInteropResultDescriptor.UnitResult,
            is WinRTComInteropResultDescriptor.ProjectedRuntimeClass,
            -> unsupportedMethod(descriptor, method, "expected an async result")
        }
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = typeRenderer.renderAbiTypeBinding(
                    comInteropParameterTypeName(parameter),
                    typesByQualifiedName,
                    descriptor.namespace,
                ),
            )
        }
        val resultIidExpression = when (result) {
            WinRTComInteropResultDescriptor.AsyncAction -> "WinRTAsyncInterfaceIds.IAsyncAction"
            is WinRTComInteropResultDescriptor.AsyncOperation -> {
                val resultBinding = typeRenderer.renderAbiTypeBinding(
                    result.resultTypeName,
                    typesByQualifiedName,
                    descriptor.namespace,
                )
                val resultSignature = typeRenderer.abiTypeSignature(resultBinding)
                    ?: unsupportedMethod(
                        descriptor,
                        method,
                        "async operation result '${result.resultTypeName}' has no ABI type signature",
                    )
                "WinRTAsyncOperationReference.interfaceId(${resultSignature.renderComInteropSource(descriptor.projectedPackageName)})"
            }
            WinRTComInteropResultDescriptor.UnitResult,
            is WinRTComInteropResultDescriptor.ProjectedRuntimeClass,
            -> unsupportedMethod(descriptor, method, "expected an async result")
        }
        val asyncExpression = typeRenderer.asyncReferenceExpression(
            returnBinding = asyncReturnBinding,
            pointerExpression = CodeBlock.of(
                "%T.fromRawComPtr(__asyncReference.pointer)",
                PLATFORM_ABI_CLASS_NAME,
            ),
        ) ?: unsupportedMethod(
            descriptor,
            method,
            "async return type '$asyncReturnTypeName' cannot be lowered to an async reference",
        )
        val publicParameters = method.parameters.zip(parameterBindings).joinToString(", ") { (parameter, binding) ->
            "${parameter.name}: ${projectionClassNameForQualifiedName(binding.typeBinding.resolvedTypeName).simpleName}"
        }
        val callParameterBindings = parameterBindings + KotlinProjectionAbiParameterBinding(
            name = "resultIid",
            typeBinding = typeRenderer.renderAbiTypeBinding("RawAddress"),
        )
        val invocation = renderCallSite(
            descriptor = descriptor,
            method = method,
            returnBinding = KotlinProjectionAbiTypeBinding(
                KotlinProjectionAbiValueKind.UnknownReference,
                IUNKNOWN_REFERENCE_CLASS_NAME.canonicalName,
            ),
            parameterBindings = callParameterBindings,
        )
        val activationTypeName = descriptor.activationTypeName.substringAfterLast('.')
        return buildString {
            appendLine("public fun ${method.name}($publicParameters): $publicReturnType =")
            appendLine("    PlatformAbi.confinedScope().use { scope ->")
            appendLine("        val resultIid = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())")
            appendLine("        PlatformAbi.writeGuid(resultIid, $resultIidExpression)")
            appendLine("        ActivationFactory.get($activationTypeName.Metadata.TYPE_NAME, $queryIidName).use { interop ->")
            appendLine("            val __asyncReference =")
            appendLine(invocation.renderCallSiteSource(descriptor.projectedPackageName).prependIndent("                "))
            appendLine(asyncExpression.renderComInteropSource(descriptor.projectedPackageName).prependIndent("            "))
            appendLine("        }")
            append("    }")
        }
    }

    private fun renderCallSite(
        descriptor: WinRTComInteropAdapterDescriptor,
        method: WinRTComInteropMethodDescriptor,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    ): CodeBlock {
        val callPlan = typeRenderer.requireAbiCallPlan(
            bindingName = "${descriptor.projectedTypeName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
        )
        val invocation = requireNotNull(typeRenderer.composeTypedProjectionCallSite(callPlan, modulePlatformAbiCalls)) {
            "COM interop method ${descriptor.projectedTypeName}.${method.name} must fold every ABI marshaler slot."
        }
        return modulePlatformAbiCalls.typedInvocation(
            referenceExpression = "interop",
            slotExpression = CodeBlock.of("%L", method.slot),
            invocation = invocation,
        )
    }

    private fun unsupportedMethod(
        descriptor: WinRTComInteropAdapterDescriptor,
        method: WinRTComInteropMethodDescriptor,
        reason: String,
    ): Nothing = throw IllegalArgumentException(
        "Unsupported COM interop method '${descriptor.projectedTypeName}.${method.name}' " +
            "at vtable slot ${method.slot}: $reason",
    )
}

private fun comInteropParameterTypeName(parameter: WinRTComInteropParameterDescriptor): String =
    when (val type = parameter.type) {
        WinRTComInteropParameterType.RawAddress -> "RawAddress"
        WinRTComInteropParameterType.StringValue -> "String"
        is WinRTComInteropParameterType.ProjectedObject -> type.typeName
    }

private fun CodeBlock.renderComInteropSource(projectedPackageName: String): String =
    toString()
        .replace("io.github.composefluent.winrt.runtime.", "")
        .replace("$projectedPackageName.", "")

private fun CodeBlock.renderCallSiteSource(projectedPackageName: String): String =
    toString().replace("$projectedPackageName.", "")

private fun String.toScreamingSnakeCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()
