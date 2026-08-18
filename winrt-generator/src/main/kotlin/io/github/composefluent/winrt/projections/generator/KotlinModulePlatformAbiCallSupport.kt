package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionParameterMetadata
import java.security.MessageDigest

private val WINRT_PROJECTION_ABI_TYPE_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiType")
private val WINRT_PROJECTION_ABI_TYPE_KIND_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiTypeKind")
private val WINRT_PROJECTION_ABI_CARRIER_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiCarrier")
private val WINRT_PROJECTION_ABI_VALUE_TRANSFORM_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiValueTransform")
private val WINRT_PROJECTION_ABI_REFERENCE_KIND_CLASS_NAME =
    ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiReferenceKind")

/** Selects ownership and placement for an already-composed typed WinRT call site. */
class KotlinModulePlatformAbiCallSupport internal constructor(
    private val className: ClassName,
    enabledCalls: Set<KotlinTypedProjectionCallSitePlan>? = null,
    private val abiSupportShardCount: Int = 1,
    private val emitSupportFile: Boolean = true,
) {
    private val enabledCallNames = enabledCalls?.mapTo(linkedSetOf()) { plan -> plan.functionName }
    private val calls = linkedMapOf<String, KotlinTypedProjectionCallSitePlan>()
    private val observedCalls = linkedMapOf<String, KotlinTypedProjectionCallSitePlan>()
    private val observedCallCounts = linkedMapOf<String, Int>()
    private val codecs = linkedMapOf<String, KotlinProjectionCallSiteCodec>()
    private val codecsByIdentity = linkedMapOf<KotlinProjectionCallSiteCodecIdentity, KotlinProjectionCallSiteCodec>()
    private val abiTypes = linkedMapOf<String, KotlinProjectionAbiTypeMetadata>()
    private val metadata = linkedMapOf<String, KotlinProjectionModuleMetadata>()

    init {
        require(
            abiSupportShardCount > 0 &&
                (abiSupportShardCount and (abiSupportShardCount - 1)) == 0,
        ) {
            "The module ABI support shard count must be a positive power of two."
        }
    }

    internal fun codecOwnerFqName(abiTypeName: String): String =
        abiSupportClassName(abiTypeName).canonicalName

    internal fun callSiteOwnerFqName(plan: KotlinTypedProjectionCallSitePlan): String =
        callSiteSupportClassName(plan.functionName).canonicalName

    internal fun registerCodec(
        operation: String,
        role: KotlinProjectionAbiCodecRole? = null,
        abiTypeName: String,
        signature: String,
        parameters: List<KotlinProjectionCallSiteCodecParameter>,
        returnType: TypeName,
        body: CodeBlock,
        consumesOwnedAbi: Boolean = false,
    ): String {
        require(!consumesOwnedAbi || role == KotlinProjectionAbiCodecRole.FROM_ABI) {
            "Only a generated FROM_ABI codec may consume an owned ABI value."
        }
        val identity = KotlinProjectionCallSiteCodecIdentity(
            role = role,
            abiTypeName = abiTypeName,
            parameterTypes = parameters.map(KotlinProjectionCallSiteCodecParameter::type),
            returnType = returnType,
            privateDiscriminator = if (role == null) "$operation|$signature" else "",
        )
        codecsByIdentity[identity]?.let { existing ->
            require(existing.hasSameImplementation(parameters, body, consumesOwnedAbi)) {
                "Conflicting generated ABI codec implementations for ${role ?: operation} '$abiTypeName': " +
                    "'${existing.sourceSignature}' vs '$signature'."
            }
            return existing.name
        }
        val name = "codec_${operation}_${stableCodecHash("codec", identity.stableDescriptor())}"
        val codec = KotlinProjectionCallSiteCodec(
            name = name,
            sourceSignature = signature,
            role = role,
            abiTypeName = abiTypeName,
            parameters = parameters,
            returnType = returnType,
            body = body,
            consumesOwnedAbi = consumesOwnedAbi,
        )
        require(codecs.putIfAbsent(name, codec) == null) {
            "WinMD call-site codec hash collision for $operation '$signature'."
        }
        codecsByIdentity[identity] = codec
        return name
    }

    internal fun registerAbiType(metadata: KotlinProjectionAbiTypeMetadata) {
        val existing = abiTypes.putIfAbsent(metadata.abiTypeName, metadata)
        require(existing == null || existing == metadata) {
            "Conflicting generated ABI metadata for '${metadata.abiTypeName}'."
        }
    }

    /**
     * Publishes one immutable module-owned value derived from closed WinMD metadata.
     *
     * The expression is deliberately kept as a KotlinPoet initializer rather than serialized
     * CallSite data. Runtime-owned and compiler-expanded paths therefore consume the same
     * generated representation, while inline-only generation falls back to the original
     * expression because no module support file exists in that mode.
     */
    internal fun registerMetadataExpression(
        identity: String,
        type: TypeName,
        initializer: CodeBlock,
    ): CodeBlock {
        if (!emitSupportFile) return initializer
        val owner = metadataSupportClassName(identity)
        val name = "metadata_${stableCodecHash("metadata", identity)}"
        val value = KotlinProjectionModuleMetadata(
            identity = identity,
            name = name,
            owner = owner,
            type = type,
            initializer = initializer,
        )
        metadata[name]?.let { existing ->
            require(existing.hasSameImplementation(value)) {
                "Conflicting generated module metadata for '$identity'."
            }
        } ?: run {
            metadata[name] = value
        }
        return CodeBlock.of("%T.%L", owner, name)
    }

    internal fun typedInvocation(
        referenceExpression: String,
        slotExpression: CodeBlock,
        invocation: KotlinTypedProjectionCallSiteInvocation,
    ): CodeBlock {
        val target = record(invocation.plan)
        return renderTargetInvocation(
            target = target,
            arguments = listOf(CodeBlock.of("%L", referenceExpression), slotExpression) + invocation.arguments,
        )
    }

    internal fun renderFiles(layout: KotlinProjectionGenerationLayout): List<KotlinProjectionFile> {
        val renderedMetadata = reachableMetadata()
        if (calls.isEmpty() && codecs.isEmpty() && abiTypes.isEmpty() && renderedMetadata.isEmpty()) return emptyList()
        val sourcePrefix = when (layout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> ""
            KotlinProjectionGenerationLayout.ExpectActualJvm -> "commonMain/kotlin/"
        }
        if (abiSupportShardCount == 1) {
            return listOf(
                renderFile(
                    sourcePrefix = sourcePrefix,
                    owner = className,
                    renderedCalls = calls.values,
                    renderedCodecs = codecs.values,
                    renderedAbiTypes = abiTypes.values,
                    renderedMetadata = renderedMetadata,
                ),
            )
        }

        val callsByOwner = calls.values.groupBy { plan -> callSiteSupportClassName(plan.functionName) }
        val codecsByOwner = codecs.values.groupBy { codec -> abiSupportClassName(codec.abiTypeName) }
        val abiTypesByOwner = abiTypes.values.groupBy { metadata -> abiSupportClassName(metadata.abiTypeName) }
        val metadataByOwner = renderedMetadata.groupBy(KotlinProjectionModuleMetadata::owner)
        val supportOwners = (callsByOwner.keys + codecsByOwner.keys + abiTypesByOwner.keys + metadataByOwner.keys)
            .distinct()
            .sortedBy(ClassName::canonicalName)
        return buildList {
            supportOwners.forEach { owner ->
                add(
                    renderFile(
                        sourcePrefix = sourcePrefix,
                        owner = owner,
                        renderedCalls = callsByOwner[owner].orEmpty(),
                        renderedCodecs = codecsByOwner[owner].orEmpty(),
                        renderedAbiTypes = abiTypesByOwner[owner].orEmpty(),
                        renderedMetadata = metadataByOwner[owner].orEmpty(),
                    ),
                )
            }
        }
    }

    internal fun plannedCalls(minCallOccurrences: Int = 2): Set<KotlinTypedProjectionCallSitePlan> =
        observedCallCounts
            .filter { (functionName, count) ->
                KotlinRuntimeOwnedProjectionCallSites.declarationFor(observedCalls.getValue(functionName)) == null &&
                    count >= minCallOccurrences
            }
            .keys
            .mapTo(linkedSetOf()) { functionName -> observedCalls.getValue(functionName) }

    private fun record(plan: KotlinTypedProjectionCallSitePlan): ModuleCallTarget {
        val functionName = plan.functionName
        observedCalls.putCallSite(functionName, plan)
        observedCallCounts[functionName] = (observedCallCounts[functionName] ?: 0) + 1
        KotlinRuntimeOwnedProjectionCallSites.declarationFor(plan)?.let { declaration ->
            return ModuleCallTarget(
                className = ClassName.bestGuess(declaration.ownerFqName),
                functionName = declaration.functionName,
            )
        }
        if (enabledCallNames != null && functionName !in enabledCallNames) {
            return ModuleCallTarget(
                functionName = "__winrtCallSite_$functionName",
                inlineCall = plan,
            )
        }
        calls.putCallSite(functionName, plan)
        return ModuleCallTarget(
            className = callSiteSupportClassName(functionName),
            functionName = functionName,
        )
    }

    private fun MutableMap<String, KotlinTypedProjectionCallSitePlan>.putCallSite(
        functionName: String,
        plan: KotlinTypedProjectionCallSitePlan,
    ) {
        val existing = putIfAbsent(functionName, plan) ?: return
        require(existing.hasSameRenderedDeclarationAs(plan)) {
            "Generated WinRT CallSite '$functionName' maps to conflicting emitted declarations."
        }
    }

    private fun renderFile(
        sourcePrefix: String,
        owner: ClassName,
        renderedCalls: Collection<KotlinTypedProjectionCallSitePlan>,
        renderedCodecs: Collection<KotlinProjectionCallSiteCodec>,
        renderedAbiTypes: Collection<KotlinProjectionAbiTypeMetadata>,
        renderedMetadata: Collection<KotlinProjectionModuleMetadata>,
    ): KotlinProjectionFile {
        val fileName = owner.simpleName
        val abiTypesByName = renderedAbiTypes.associateBy(KotlinProjectionAbiTypeMetadata::abiTypeName)
        val abiTypeCodecNames = renderedCodecs
            .asSequence()
            .filter { codec -> codec.role != null && codec.abiTypeName in abiTypesByName }
            .groupBy(KotlinProjectionCallSiteCodec::abiTypeName)
            .mapValues { (_, codecs) -> codecs.minOf(KotlinProjectionCallSiteCodec::name) }
        val type = TypeSpec.objectBuilder(fileName)
            .addAnnotation(KOTLIN_PUBLISHED_API_CLASS_NAME)
            .addModifiers(KModifier.INTERNAL)
            .apply {
                renderedMetadata
                    .forEach { value -> addProperty(renderMetadata(value)) }
                renderedAbiTypes
                    .filter { metadata -> metadata.abiTypeName !in abiTypeCodecNames }
                    .sortedBy(KotlinProjectionAbiTypeMetadata::abiTypeName)
                    .forEach { metadata -> addType(renderAbiType(metadata)) }
                renderedCodecs.sortedBy(KotlinProjectionCallSiteCodec::name)
                    .forEach { codec ->
                        val metadata = abiTypesByName[codec.abiTypeName]
                            ?.takeIf { abiTypeCodecNames[codec.abiTypeName] == codec.name }
                        addFunction(renderCodec(codec, metadata))
                    }
                renderedCalls.sortedBy(KotlinTypedProjectionCallSitePlan::functionName)
                    .forEach { plan -> addFunction(renderFunction(plan)) }
            }
            .build()
        val contents = FileSpec.builder(owner.packageName, fileName)
            .addGeneratedProjectionSuppressions()
            .addType(type)
            .build()
            .toString()
        return KotlinProjectionFile(
            relativePath = "$sourcePrefix${owner.packageName.replace('.', '/')}/$fileName.kt",
            packageName = owner.packageName,
            contents = contents,
        )
    }

    private fun abiSupportClassName(abiTypeName: String): ClassName {
        return supportShardClassName("abi-shard", abiTypeName)
    }

    private fun metadataSupportClassName(identity: String): ClassName =
        supportShardClassName("metadata-shard", identity)

    private fun callSiteSupportClassName(functionName: String): ClassName {
        return supportShardClassName("call-shard", functionName)
    }

    private fun supportShardClassName(kind: String, identity: String): ClassName {
        if (abiSupportShardCount == 1) return className
        val bucket = stableCodecHash(kind, identity)
            .take(2)
            .toInt(16)
            .and(abiSupportShardCount - 1)
            .toString(16)
            .padStart(2, '0')
        return ClassName(className.packageName, "${className.simpleName}_Abi_$bucket")
    }

    private fun renderMetadata(metadata: KotlinProjectionModuleMetadata): PropertySpec =
        PropertySpec.builder(metadata.name, metadata.type)
            .addAnnotation(KOTLIN_PUBLISHED_API_CLASS_NAME)
            .addModifiers(KModifier.INTERNAL)
            .initializer(metadata.initializer)
            .build()

    /**
     * Metadata expressions are composed while a call-site plan is being inspected, before the
     * planner knows whether that call will become a module codec or remain an inline call site.
     * Keep only values reachable from emitted codec bodies, otherwise a one-off call would create
     * an otherwise empty support file. Dependencies are returned before their users so same-object
     * Kotlin initializers cannot observe an uninitialized later property.
     */
    private fun reachableMetadata(): List<KotlinProjectionModuleMetadata> {
        if (metadata.isEmpty() || codecs.isEmpty()) return emptyList()
        val values = metadata.values.toList()
        val byName = values.associateBy(KotlinProjectionModuleMetadata::name)
        val renderedCodecText = codecs.values.joinToString("\n") { codec -> codec.body.toString() }
        val roots = values.filter { value -> renderedCodecText.contains(value.name) }
        if (roots.isEmpty()) return emptyList()

        val reachable = linkedSetOf<String>()
        val visiting = linkedSetOf<String>()
        fun visit(value: KotlinProjectionModuleMetadata) {
            if (value.name in reachable) return
            check(visiting.add(value.name)) {
                "Cyclic generated module metadata dependency at '${value.name}'."
            }
            values
                .asSequence()
                .filter { dependency ->
                    dependency.name != value.name && value.initializer.toString().contains(dependency.name)
                }
                .sortedBy(KotlinProjectionModuleMetadata::name)
                .forEach(::visit)
            visiting.remove(value.name)
            reachable += value.name
        }
        roots.sortedBy(KotlinProjectionModuleMetadata::name).forEach { root ->
            visit(root)
        }
        return reachable.mapNotNull(byName::get)
    }

    private fun renderFunction(plan: KotlinTypedProjectionCallSitePlan): FunSpec =
        FunSpec.builder(plan.functionName)
            .addAnnotation(KOTLIN_PUBLISHED_API_CLASS_NAME)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("instance", COM_OBJECT_REFERENCE_CLASS_NAME)
            .addParameter("slot", Int::class)
            .apply {
                plan.parameters.forEachIndexed { index, parameter ->
                    addParameter(
                        ParameterSpec.builder("arg$index", parameter.type)
                            .apply {
                                plan.metadata.parameters[index].annotationSpecOrNull()
                                    ?.let(::addAnnotation)
                            }
                            .build(),
                    )
                }
            }
            .returns(plan.returnType)
            .addAnnotation(plan.callSiteAnnotationSpec())
            .addStatement("return TODO(%S)", MODULE_CALL_SITE_PLACEHOLDER)
            .build()

    private fun renderCodec(
        codec: KotlinProjectionCallSiteCodec,
        abiTypeMetadata: KotlinProjectionAbiTypeMetadata?,
    ): FunSpec =
        FunSpec.builder(codec.name)
            .addModifiers(KModifier.INTERNAL)
            .apply {
                codec.parameters.forEach { parameter ->
                    addParameter(ParameterSpec.builder(parameter.name, parameter.type).build())
                }
            }
            .returns(codec.returnType)
            .apply {
                abiTypeMetadata?.let { metadata -> addAnnotation(metadata.annotationSpec()) }
                codec.role?.let { role ->
                    addAnnotation(
                        AnnotationSpec.builder(WINRT_PROJECTION_ABI_CODEC_CLASS_NAME)
                            .addMember(
                                "role = %T.%L",
                                WINRT_PROJECTION_ABI_CODEC_ROLE_CLASS_NAME,
                                role.name,
                            )
                            .addMember("type = %S", codec.abiTypeName)
                            .apply {
                                if (codec.consumesOwnedAbi) addMember("consumesOwnedAbi = true")
                            }
                            .build(),
                    )
                }
            }
            .addCode(codec.body)
            .build()

    private fun renderAbiType(metadata: KotlinProjectionAbiTypeMetadata): TypeSpec =
        TypeSpec.objectBuilder("AbiType_${stableCodecHash("type", metadata.abiTypeName)}")
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(metadata.annotationSpec())
            .build()

    private fun renderTargetInvocation(
        target: ModuleCallTarget,
        arguments: List<CodeBlock>,
    ): CodeBlock {
        target.inlineCall?.let { plan -> return renderInlineCallSite(plan, arguments) }
        return CodeBlock.builder()
            .apply {
                target.className?.let { owner -> add("%T.%L(\n", owner, target.functionName) }
                    ?: add("%L(\n", target.functionName)
            }
            .indent()
            .apply { arguments.forEach { argument -> add("%L,\n", argument) } }
            .unindent()
            .add(")")
            .build()
    }

    private fun renderInlineCallSite(
        plan: KotlinTypedProjectionCallSitePlan,
        arguments: List<CodeBlock>,
    ): CodeBlock {
        val parameterTypes = listOf(COM_OBJECT_REFERENCE_CLASS_NAME, Int::class.asClassName()) +
            plan.parameters.map(KotlinTypedProjectionCallSiteParameter::type)
        require(arguments.size == parameterTypes.size) {
            "WinMD call-site plan ${plan.functionName} expected ${parameterTypes.size} typed arguments, " +
                "but generation supplied ${arguments.size}."
        }
        return CodeBlock.builder()
            .add("kotlin.run {\n")
            .indent()
            .apply {
                parameterTypes.zip(arguments).forEachIndexed { index, (type, argument) ->
                    if (index >= 2) {
                        plan.metadata.parameters[index - 2].annotationSpecOrNull()?.let { annotation ->
                            add("%L\n", annotation)
                        }
                    }
                    add("val %L%L: %T = %L\n", INLINE_CALL_SITE_ARGUMENT_PREFIX, index, type, argument)
                }
            }
            .add("%L\n", plan.callSiteAnnotationSpec())
            .add(
                "val %L: %T = TODO(%S)\n",
                INLINE_CALL_SITE_RESULT_NAME,
                plan.returnType,
                MODULE_CALL_SITE_PLACEHOLDER,
            )
            .apply {
                if (plan.returnType != Unit::class.asClassName()) add("%L\n", INLINE_CALL_SITE_RESULT_NAME)
            }
            .unindent()
            .add("}")
            .build()
    }

    private data class ModuleCallTarget(
        val className: ClassName? = null,
        val functionName: String,
        val inlineCall: KotlinTypedProjectionCallSitePlan? = null,
    )

    private companion object {
        const val MODULE_CALL_SITE_PLACEHOLDER = "Lowered while compiling the generated WinRT module"
        const val INLINE_CALL_SITE_ARGUMENT_PREFIX = "__winrtCallSiteArgument"
        const val INLINE_CALL_SITE_RESULT_NAME = "__winrtCallSiteResult"

        val WINRT_PROJECTION_CALL_SITE_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionCallSite")
        val WINRT_PROJECTION_PARAMETER_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionParameter")
        val WINRT_CALL_SITE_HRESULT_POLICY_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteHResultPolicy")
        val WINRT_CALL_SITE_RESULT_KIND_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteResultKind")
        val WINRT_CALL_SITE_PARAMETER_DIRECTION_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteParameterDirection")
        val WINRT_PROJECTION_ABI_CODEC_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiCodec")
        val WINRT_PROJECTION_ABI_CODEC_ROLE_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionAbiCodecRole")

        fun stableCodecHash(operation: String, signature: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$operation|$signature".toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
    }
}

private fun KotlinTypedProjectionCallSitePlan.hasSameRenderedDeclarationAs(
    other: KotlinTypedProjectionCallSitePlan,
): Boolean =
    metadata == other.metadata &&
        returnType == other.returnType &&
        parameters.map(KotlinTypedProjectionCallSiteParameter::type) ==
        other.parameters.map(KotlinTypedProjectionCallSiteParameter::type)

private fun KotlinTypedProjectionCallSitePlan.callSiteAnnotationSpec(): AnnotationSpec =
    AnnotationSpec.builder(
        ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionCallSite"),
    ).apply {
        if (metadata.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.CHECK) {
            addMember(
                "hResult = %T.%L",
                ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteHResultPolicy"),
                metadata.hResultPolicy.name,
            )
        }
        if (metadata.resultKind != WinRTProjectionCallSiteResultKind.INFER) {
            addMember(
                "result = %T.%L",
                ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteResultKind"),
                metadata.resultKind.name,
            )
        }
        if (metadata.returnAbiType.isNotEmpty()) addMember("returnAbiType = %S", metadata.returnAbiType)
    }.build()

private fun WinRTProjectionParameterMetadata.annotationSpecOrNull(): AnnotationSpec? {
    if (direction == WinRTProjectionCallSiteParameterDirection.IN && abiType.isEmpty()) return null
    return AnnotationSpec.builder(
        ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionParameter"),
    ).apply {
        if (direction != WinRTProjectionCallSiteParameterDirection.IN) {
            addMember(
                "direction = %T.%L",
                ClassName("io.github.composefluent.winrt.runtime", "WinRTCallSiteParameterDirection"),
                direction.name,
            )
        }
        if (abiType.isNotEmpty()) addMember("abiType = %S", abiType)
    }.build()
}

internal data class KotlinProjectionCallSiteCodecParameter(
    val name: String,
    val type: TypeName,
)

private data class KotlinProjectionCallSiteCodec(
    val name: String,
    val sourceSignature: String,
    val role: KotlinProjectionAbiCodecRole?,
    val abiTypeName: String,
    val parameters: List<KotlinProjectionCallSiteCodecParameter>,
    val returnType: TypeName,
    val body: CodeBlock,
    val consumesOwnedAbi: Boolean,
) {
    fun hasSameImplementation(
        otherParameters: List<KotlinProjectionCallSiteCodecParameter>,
        otherBody: CodeBlock,
        otherConsumesOwnedAbi: Boolean,
    ): Boolean =
        parameters == otherParameters &&
            body.toString() == otherBody.toString() &&
            consumesOwnedAbi == otherConsumesOwnedAbi
}

private data class KotlinProjectionModuleMetadata(
    val identity: String,
    val name: String,
    val owner: ClassName,
    val type: TypeName,
    val initializer: CodeBlock,
) {
    fun hasSameImplementation(other: KotlinProjectionModuleMetadata): Boolean =
        identity == other.identity &&
            owner == other.owner &&
            type == other.type &&
            initializer.toString() == other.initializer.toString()
}

private data class KotlinProjectionCallSiteCodecIdentity(
    val role: KotlinProjectionAbiCodecRole?,
    val abiTypeName: String,
    val parameterTypes: List<TypeName>,
    val returnType: TypeName,
    val privateDiscriminator: String,
) {
    fun stableDescriptor(): String = buildString {
        append(role?.name ?: "PRIVATE")
        append('|')
        append(abiTypeName)
        parameterTypes.joinTo(this, prefix = "|(", postfix = ")")
        append("|")
        append(returnType)
        if (privateDiscriminator.isNotEmpty()) {
            append('|')
            append(privateDiscriminator)
        }
    }
}

internal enum class KotlinProjectionAbiCodecRole {
    TO_ABI,
    FROM_ABI,
    CREATE_MARSHALER,
    COPY_TO_ABI,
    COPY_FROM_ABI,
    DISPOSE_ABI,
}

internal enum class KotlinProjectionAbiTypeKind {
    ENUM,
    STRUCT,
    COM_REFERENCE,
    ARRAY,
}

internal enum class KotlinProjectionAbiCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
}

internal enum class KotlinProjectionAbiValueTransform {
    IDENTITY,
    BOOLEAN,
    UNSIGNED,
    CHAR16,
}

internal enum class KotlinProjectionAbiReferenceKind {
    NONE,
    UNKNOWN,
    INSPECTABLE,
}

internal data class KotlinProjectionAbiTypeMetadata(
    val abiTypeName: String,
    val kind: KotlinProjectionAbiTypeKind,
    val carrier: KotlinProjectionAbiCarrier = KotlinProjectionAbiCarrier.ADDRESS,
    val transform: KotlinProjectionAbiValueTransform = KotlinProjectionAbiValueTransform.IDENTITY,
    val size: Int = 0,
    val alignment: Int = 0,
    val reference: KotlinProjectionAbiReferenceKind = KotlinProjectionAbiReferenceKind.NONE,
)

internal fun KotlinProjectionAbiTypeMetadata.annotationSpec(): AnnotationSpec =
    AnnotationSpec.builder(WINRT_PROJECTION_ABI_TYPE_CLASS_NAME)
        .addMember("name = %S", abiTypeName)
        .addMember("kind = %T.%L", WINRT_PROJECTION_ABI_TYPE_KIND_CLASS_NAME, kind.name)
        .addMember("carrier = %T.%L", WINRT_PROJECTION_ABI_CARRIER_CLASS_NAME, carrier.name)
        .apply {
            if (size > 0) addMember("size = %L", size)
            if (alignment > 0) addMember("alignment = %L", alignment)
            if (transform != KotlinProjectionAbiValueTransform.IDENTITY) {
                addMember(
                    "transform = %T.%L",
                    WINRT_PROJECTION_ABI_VALUE_TRANSFORM_CLASS_NAME,
                    transform.name,
                )
            }
            if (reference != KotlinProjectionAbiReferenceKind.NONE) {
                addMember(
                    "reference = %T.%L",
                    WINRT_PROJECTION_ABI_REFERENCE_KIND_CLASS_NAME,
                    reference.name,
                )
            }
        }
        .build()

internal fun inlineOnlyModulePlatformAbiCallSupport(): KotlinModulePlatformAbiCallSupport =
    KotlinModulePlatformAbiCallSupport(
        className = ClassName(
            "io.github.composefluent.winrt.projections.support",
            "WinRTModulePlatformAbiCall",
        ),
        enabledCalls = emptySet(),
        emitSupportFile = false,
    )
