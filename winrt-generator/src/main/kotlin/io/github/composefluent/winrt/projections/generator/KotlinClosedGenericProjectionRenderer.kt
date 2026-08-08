package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRTGenericTypeInstantiationDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import java.security.MessageDigest

private const val CLOSED_GENERIC_SUPPORT_PACKAGE = "io.github.composefluent.winrt.projections.support"

internal fun KotlinProjectionAbiTypeBinding.closedGenericProjectionHelperClassName(
    supportOwnerIdentity: String?,
): ClassName? {
    if (
        kind != KotlinProjectionAbiValueKind.ProjectedInterface &&
        kind != KotlinProjectionAbiValueKind.MappedKeyValuePair
    ) {
        return null
    }
    if (typeArguments.isEmpty() || containsOpenGenericShape()) {
        return null
    }
    val rawName = resolvedTypeName
        .removeSuffix("?")
        .substringBefore('<')
        .substringAfterLast('.')
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(closedGenericProjectionIdentity().toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    val ownerSuffix = winRTSupportOwnerIdentifierSuffix(supportOwnerIdentity)
        ?.let { suffix -> "_$suffix" }
        .orEmpty()
    return ClassName(
        CLOSED_GENERIC_SUPPORT_PACKAGE,
        "WinRTClosedProjection_${generatedLocalIdentifier("", rawName)}_${hash}$ownerSuffix",
    )
}

internal fun KotlinProjectionAbiTypeBinding.containsOpenGenericShape(): Boolean =
    kind == KotlinProjectionAbiValueKind.GenericParameter ||
        typeArguments.any(KotlinProjectionAbiTypeBinding::containsOpenGenericShape) ||
        structFieldBindings.any(KotlinProjectionAbiTypeBinding::containsOpenGenericShape) ||
        delegateInvokeShape?.let { shape ->
            shape.returnBinding.containsOpenGenericShape() ||
                shape.parameterBindings.any { parameter -> parameter.typeBinding.containsOpenGenericShape() }
        } == true

private fun KotlinProjectionAbiTypeBinding.closedGenericProjectionIdentity(): String =
    buildString {
        append(kind.name)
        append(':')
        append(resolvedTypeName.removeSuffix("?").substringBefore('<'))
        if (typeArguments.isNotEmpty()) {
            typeArguments.joinTo(this, prefix = "<", postfix = ">") { argument ->
                argument.closedGenericProjectionIdentity()
            }
        }
    }

internal fun KotlinProjectionAbiTypeBinding.hasClosedGenericProjectionHelper(
    plansByType: Map<String, KotlinTypeProjectionPlan>,
): Boolean {
    if (closedGenericProjectionHelperClassName(supportOwnerIdentity = null) == null) return false
    val rawName = resolvedTypeName.removeSuffix("?").substringBefore('<')
    val plan = plansByType[rawName] ?: return false
    return plan.type.kind == WinRTTypeKind.Interface && plan.type.genericParameterCount == typeArguments.size
}

internal fun renderClosedGenericProjectionHelpers(
    planner: KotlinProjectionPlanner,
    model: WinRTMetadataModel,
    plans: List<KotlinTypeProjectionPlan>,
    instantiations: List<WinRTGenericTypeInstantiationDescriptor>,
    modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport?,
    supportOwnerIdentity: String?,
): List<KotlinProjectionFile> {
    val plansByType = plans.associateBy { plan -> plan.type.qualifiedName }
    val typesByQualifiedName = model.namespaces
        .flatMap { namespace -> namespace.types }
        .associateBy(WinRTTypeDefinition::qualifiedName)
    val projectedSlotLiterals = plans
        .asSequence()
        .filter { plan -> plan.type.kind == WinRTTypeKind.Interface }
        .flatMap { plan ->
            plan.abiSlotBindings.asSequence().map { binding ->
                KotlinProjectionSlotLiteralKey(plan.type.qualifiedName, binding.constantName) to binding.slot
            }
        }
        .toMap()
    val renderer = KotlinProjectionRenderer(
        useInterfaceProjectionArtifacts = true,
        suppressProjectedMemberSlotConstants = true,
        projectedSlotLiterals = projectedSlotLiterals,
        modulePlatformAbiCalls = modulePlatformAbiCalls,
        supportOwnerIdentity = supportOwnerIdentity,
    )
    val helperTypes = instantiations
        .asSequence()
        .mapNotNull { instantiation ->
            val definition = instantiation.definitionType
                ?.takeIf { type -> type.kind == WinRTTypeKind.Interface }
                ?: return@mapNotNull null
            val plan = plansByType[definition.qualifiedName] ?: return@mapNotNull null
            val binding = planner.classifyAbiTypeBinding(
                typeName = instantiation.type.typeName,
                currentNamespace = definition.namespace,
                typesByQualifiedName = typesByQualifiedName,
            )
            if (binding.closedGenericProjectionHelperClassName(supportOwnerIdentity) == null) {
                return@mapNotNull null
            }
            renderer.renderClosedGenericProjectionHelper(
                plan = plan,
                binding = binding,
                genericArguments = instantiation.genericArguments,
            )
        }
        .distinctBy(TypeSpec::name)
        .sortedBy(TypeSpec::name)
        .toList()
    if (helperTypes.isEmpty()) return emptyList()

    val filePrefix = buildString {
        append("WinRTClosedGenericProjectionHelper")
        winRTSupportOwnerIdentifierSuffix(supportOwnerIdentity)?.let { suffix -> append('_').append(suffix) }
    }
    return helperTypes.chunked(96).mapIndexed { index, chunk ->
        val fileName = "${filePrefix}_${index.toString().padStart(3, '0')}"
        val fileSpec = FileSpec.builder(CLOSED_GENERIC_SUPPORT_PACKAGE, fileName)
            .addGeneratedProjectionSuppressions()
            .addGeneratedProjectionAtomicOptIn()
            .addFileComment("Metadata-composed closed generic projection helpers.")
            .apply { chunk.forEach(::addType) }
            .build()
        KotlinProjectionFile(
            relativePath = "io/github/composefluent/winrt/projections/support/$fileName.kt",
            packageName = CLOSED_GENERIC_SUPPORT_PACKAGE,
            contents = fileSpec.toString(),
        )
    }
}

private fun KotlinProjectionRenderer.renderClosedGenericProjectionHelper(
    plan: KotlinTypeProjectionPlan,
    binding: KotlinProjectionAbiTypeBinding,
    genericArguments: List<io.github.composefluent.winrt.metadata.WinRTTypeRef>,
): TypeSpec {
    val helperClass = requireNotNull(binding.closedGenericProjectionHelperClassName(supportOwnerIdentity))
    val projectedType = resolveTypeName(binding.typeName).copy(nullable = false)
    val typeSignature = abiTypeSignature(binding)
        ?: error("Closed generic projection '${binding.typeName}' has no parameterized WinRT signature.")
    val mappedAdapter = listOf(binding.resolvedTypeName, binding.typeName)
        .asSequence()
        .map { typeName -> typeName.removeSuffix("?").substringBefore('<') }
        .mapNotNull(::mappedTypeByAbiName)
        .mapNotNull(KotlinProjectionMappedType::closedGenericAdapter)
        .firstOrNull()
    val mappedAdapterArguments = mappedAdapter?.let { adapter ->
        require(binding.typeArguments.size == adapter.typeArgumentCount) {
            "Mapped closed generic '${binding.typeName}' requires ${adapter.typeArgumentCount} type arguments."
        }
        binding.typeArguments.map { argument ->
            collectionReferenceAdapterCode(argument)
                ?: error("Mapped closed generic '${binding.typeName}' has no reference adapter for ${argument.typeName}.")
        }
    }.orEmpty()
    val mappedAdapterArgumentCode = CodeBlock.builder()
        .apply { mappedAdapterArguments.forEach { argument -> add(", %L", argument) } }
        .build()
    val nativeProjection = if (mappedAdapter == null) {
        renderClosedInterfaceNativeProjection(
            plan = plan,
            projectedType = projectedType,
            interfaceInstanceName = binding.typeName.removeSuffix("?"),
            genericArguments = genericArguments,
            genericTypeArguments = binding.typeArguments,
            primaryTypeHandleExpression = CodeBlock.of("%T.TYPE_HANDLE", helperClass),
        )
    } else {
        null
    }

    val isMappedKeyValuePair = binding.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair
    val entrySnapshot = if (isMappedKeyValuePair) {
        val keyType = resolveTypeName(binding.typeArguments[0].typeName)
        val valueType = resolveTypeName(binding.typeArguments[1].typeName)
        TypeSpec.classBuilder("EntrySnapshot")
            .addModifiers(KModifier.PRIVATE, KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("key", keyType)
                    .addParameter("value", valueType)
                    .build(),
            )
            .addSuperinterface(projectedType)
            .addProperty(
                PropertySpec.builder("key", keyType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("key")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("value", valueType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("value")
                    .build(),
            )
            .build()
    } else {
        null
    }

    return TypeSpec.objectBuilder(helperClass.simpleName)
        .addModifiers(KModifier.INTERNAL)
        .addProperty(
            PropertySpec.builder("TYPE_HANDLE", WinRTTypeHandle::class.asClassName())
                .addModifiers(KModifier.INTERNAL)
                .initializer(
                    "%T(%S, %T.createFromSignature(%L))",
                    WinRTTypeHandle::class,
                    binding.typeName.removeSuffix("?"),
                    ParameterizedInterfaceId::class,
                    typeSignature,
                )
                .build(),
        )
        .addFunction(
            FunSpec.builder("wrap")
                .addModifiers(KModifier.INTERNAL)
                .addParameter("instance", IUnknownReference::class)
                .returns(projectedType)
                .addCode(
                    if (mappedAdapter == null) {
                        CodeBlock.of("return NativeProjection(instance)\n")
                    } else {
                        CodeBlock.of(
                            "return %T.%L(instance%L)\n",
                            mappedAdapter.runtimeProjectionClassName,
                            mappedAdapter.fromReferenceFunctionName,
                            mappedAdapterArgumentCode,
                        )
                    },
                )
                .build(),
        )
        .apply {
            if (isMappedKeyValuePair) {
                addFunction(
                    FunSpec.builder("fromAbi")
                        .addModifiers(KModifier.INTERNAL)
                        .addParameter("instance", RAW_ADDRESS_CLASS_NAME)
                        .returns(projectedType)
                        .addCode(
                            "require(!%T.isNull(instance)) { %S }\n" +
                                "return %T(%T.toRawComPtr(instance), TYPE_HANDLE.interfaceId, preventReleaseOnDispose = true).use { __reference ->\n" +
                                "  val __projection = NativeProjection(__reference)\n" +
                                "  EntrySnapshot(__projection.key, __projection.value)\n" +
                                "}\n",
                            PLATFORM_ABI_CLASS_NAME,
                            "IKeyValuePair ABI reference cannot be null.",
                            IUNKNOWN_REFERENCE_CLASS_NAME,
                            PLATFORM_ABI_CLASS_NAME,
                        )
                        .build(),
                )
                addFunction(
                    FunSpec.builder("disposeAbi")
                        .addModifiers(KModifier.INTERNAL)
                        .addParameter("instance", RAW_ADDRESS_CLASS_NAME)
                        .addCode(
                            "if (!%T.isNull(instance)) %T(%T.toRawComPtr(instance), TYPE_HANDLE.interfaceId).close()\n",
                            PLATFORM_ABI_CLASS_NAME,
                            IUNKNOWN_REFERENCE_CLASS_NAME,
                            PLATFORM_ABI_CLASS_NAME,
                        )
                        .build(),
                )
                addType(requireNotNull(entrySnapshot))
            } else {
                addProperty(
                    PropertySpec.builder(
                        "referenceValueAdapter",
                        WinRTReferenceValueAdapter::class.asClassName().parameterizedBy(projectedType),
                    )
                        .addModifiers(KModifier.INTERNAL)
                        .initializer(
                            if (mappedAdapter == null) {
                                CodeBlock.of(
                                    "%T<%T>(projectedTypeName = %S, typeSignature = %L, " +
                                        "projector = { reference -> wrap(requireNotNull(reference)) }, " +
                                        "marshaller = { value -> %T((value as %T).nativeObject.getRefPointer()) })",
                                    WinRTReferenceValueAdapter::class,
                                    projectedType,
                                    binding.typeName.removeSuffix("?"),
                                    typeSignature,
                                    IUnknownReference::class,
                                    IWinRTObject::class,
                                )
                            } else {
                                CodeBlock.of(
                                    "%T<%T>(projectedTypeName = %S, typeSignature = %L, " +
                                        "projector = { reference -> %T.%L(requireNotNull(reference)%L) }, " +
                                        "marshaller = { value -> %T.%L(value%L) })",
                                    WinRTReferenceValueAdapter::class,
                                    projectedType,
                                    binding.typeName.removeSuffix("?"),
                                    typeSignature,
                                    mappedAdapter.runtimeProjectionClassName,
                                    mappedAdapter.fromReferenceFunctionName,
                                    mappedAdapterArgumentCode,
                                    mappedAdapter.runtimeProjectionClassName,
                                    mappedAdapter.createReferenceFunctionName,
                                    mappedAdapterArgumentCode,
                                )
                            },
                        )
                        .build(),
                )
            }
        }
        .apply { nativeProjection?.let(::addType) }
        .build()
}
