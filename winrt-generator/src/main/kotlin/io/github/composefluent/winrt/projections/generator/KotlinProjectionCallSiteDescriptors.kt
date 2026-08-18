package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTTypeKind

/**
 * Folds the recipes already produced by [buildAbiCallPlan] into one complete descriptor.
 * This layer deliberately has no ABI type classifier of its own.
 */
internal fun KotlinProjectionRenderer.composeTypedProjectionCallSite(
    callPlan: KotlinProjectionAbiCallPlan,
    callSiteSupport: KotlinModulePlatformAbiCallSupport? = modulePlatformAbiCalls,
    hResultPolicy: WinRTProjectionCallSiteHResultPolicy? = null,
    callerOwnedResultType: TypeName? = null,
): KotlinTypedProjectionCallSiteInvocation {
    val slots = mutableListOf<WinRTProjectionCallSiteSlot>()
    val parameters = mutableListOf<KotlinTypedProjectionCallSiteParameter>()
    val arguments = mutableListOf<CodeBlock>()
    var parameterResultType: TypeName? = null
    var parameterResultAbiType = ""

    callPlan.parameterSlots.forEach { slotPlan ->
        val binding = slotPlan.binding
        val sourceRecipePlan = slotPlan.recipePlan
        val sourceRecipe = sourceRecipePlan.recipe
        val direction = if (
            binding.category == WinRTMetadataParameterCategory.In &&
            sourceRecipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY
        ) {
            WinRTProjectionCallSiteSlotDirection.PASS_ARRAY
        } else {
            binding.category.callSiteDirection()
        }
        val recipe = if (
            direction == WinRTProjectionCallSiteSlotDirection.OUT ||
            direction == WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY
        ) {
            materializeOutputCallSiteRecipe(
                binding = binding.typeBinding,
                recipePlan = sourceRecipePlan,
                callSiteSupport = callSiteSupport,
            )
        } else {
            materializeParameterCallSiteRecipe(
                binding = binding,
                recipePlan = sourceRecipePlan,
                callSiteSupport = callSiteSupport,
            )
        }
        val slot = WinRTProjectionCallSiteSlot(
            direction = direction,
            ownership = binding.category.parameterOwnership(recipe),
            recipe = recipe,
        )
        registerCallSiteAbiType(binding.typeBinding, recipe, callSiteSupport)
        slots += slot
        if (direction == WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY) {
            require(parameterResultType == null) { "A WinRT call site cannot project multiple receive-array results." }
            parameterResultType = projectedCallSiteType(binding.typeBinding)
            parameterResultAbiType = binding.typeBinding.explicitCallSiteAbiTypeName()
        } else if (slot.functionParameterCount > 0) {
            val projectedType = projectedCallSiteType(binding.typeBinding)
            val parameterType = if (direction == WinRTProjectionCallSiteSlotDirection.OUT) {
                WINRT_OUT_CLASS_NAME.parameterizedBy(projectedType)
            } else {
                projectedType
            }
            parameters += KotlinTypedProjectionCallSiteParameter(
                type = parameterType,
                direction = direction.parameterDirection(),
                abiType = binding.typeBinding.explicitCallSiteAbiTypeName(),
            )
            arguments += CodeBlock.of("%L", binding.name.escapeAsKotlinIdentifierIfNeeded())
        }
    }

    val effectiveHResultPolicy = hResultPolicy ?: if (callPlan.suppressHResultCheck) {
        WinRTProjectionCallSiteHResultPolicy.IGNORE
    } else {
        WinRTProjectionCallSiteHResultPolicy.CHECK
    }
    if (callerOwnedResultType != null) {
        require(effectiveHResultPolicy != WinRTProjectionCallSiteHResultPolicy.RETURN) {
            "Caller-owned WinRT outputs cannot be combined with raw-HRESULT return policy."
        }
        require(callPlan.returnBinding.kind == KotlinProjectionAbiValueKind.Unit && parameterResultType == null) {
            "Caller-owned WinRT outputs cannot be combined with another projected result."
        }
    }
    val returnType = if (effectiveHResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN) {
        require(callPlan.returnBinding.kind == KotlinProjectionAbiValueKind.Unit && parameterResultType == null) {
            "A raw-HRESULT call site cannot also expose a projected result."
        }
        Int::class.asClassName()
    } else if (callerOwnedResultType != null) {
        callerOwnedResultType
    } else if (callPlan.returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
        parameterResultType ?: UNIT
    } else {
        require(parameterResultType == null) {
            "A WinRT call site cannot combine a receive-array result with a projected return value."
        }
        val sourceRecipePlan = callPlan.returnRecipePlan
            ?: callPlan.returnBinding.failCallSitePlan("has no return recipe")
        val recipe = materializeOutputCallSiteRecipe(
            binding = callPlan.returnBinding,
            recipePlan = sourceRecipePlan,
            callSiteSupport = callSiteSupport,
        )
        val direction = if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
            WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY
        } else {
            WinRTProjectionCallSiteSlotDirection.RETURN
        }
        registerCallSiteAbiType(callPlan.returnBinding, recipe, callSiteSupport)
        slots += WinRTProjectionCallSiteSlot(
            direction = direction,
            ownership = if (recipe.requiresNativeOwnership()) {
                WinRTProjectionCallSiteOwnership.OWNED
            } else {
                WinRTProjectionCallSiteOwnership.NONE
            },
            recipe = recipe,
        )
        runCatching { resolveTypeName(callPlan.returnBinding.typeName) }
            .getOrElse { failure -> callPlan.returnBinding.failCallSitePlan("cannot resolve its projected return type", failure) }
    }

    val descriptor = WinRTProjectionCallSiteDescriptor(
        hResultPolicy = effectiveHResultPolicy,
        slots = slots,
    )
    val returnAbiType = when {
        effectiveHResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN -> ""
        callerOwnedResultType != null -> ""
        callPlan.returnBinding.kind == KotlinProjectionAbiValueKind.Unit -> parameterResultAbiType
        else -> callPlan.returnBinding.explicitCallSiteAbiTypeName()
    }
    return KotlinTypedProjectionCallSiteInvocation(
        plan = KotlinTypedProjectionCallSitePlan(
            descriptor = descriptor,
            returnType = returnType,
            parameters = parameters,
            resultKind = if (callerOwnedResultType == null) {
                WinRTProjectionCallSiteResultKind.INFER
            } else {
                WinRTProjectionCallSiteResultKind.CALLER_OWNED
            },
            returnAbiType = returnAbiType,
        ),
        arguments = arguments,
    )
}

internal data class KotlinDirectInboundCallSiteParameter(
    val projectedType: TypeName,
    val abiType: String,
)

/**
 * Composes one borrowed inbound ABI value through the same closed WinMD recipe used for outbound
 * results. Unsupported or ownership-consuming shapes stay on the runtime compatibility path.
 */
internal fun KotlinProjectionRenderer.composeDirectInboundCallSiteParameter(
    binding: KotlinProjectionAbiTypeBinding,
    callSiteSupport: KotlinModulePlatformAbiCallSupport? = modulePlatformAbiCalls,
): KotlinDirectInboundCallSiteParameter? {
    val recipePlan = runCatching {
        buildCallSiteRecipe(binding, category = null)
    }.getOrNull() ?: return null
    if (recipePlan.outputCodecs.values.any(KotlinProjectionCallSiteOutputCodec::consumesOwnedAbi)) {
        return null
    }
    val recipe = runCatching {
        materializeOutputCallSiteRecipe(
            binding = binding,
            recipePlan = recipePlan,
            callSiteSupport = callSiteSupport,
        )
    }.getOrNull() ?: return null
    if (recipe.kind !in DIRECT_INBOUND_PARAMETER_RECIPE_KINDS || recipe.abiCarriers.size != 1) {
        return null
    }
    registerCallSiteAbiType(binding, recipe, callSiteSupport)
    val projectedType = projectedCallSiteType(binding)
    return KotlinDirectInboundCallSiteParameter(
        projectedType = projectedType,
        abiType = binding.callSiteAbiMetadataName(projectedType),
    )
}

private val DIRECT_INBOUND_PARAMETER_RECIPE_KINDS = setOf(
    WinRTProjectionCallSiteRecipeKind.VALUE,
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.PROJECTION,
)

private fun KotlinProjectionRenderer.registerCallSiteAbiType(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    support: KotlinModulePlatformAbiCallSupport?,
) {
    if (support == null) return
    if (recipe.kind !in GENERATED_ABI_METADATA_RECIPE_KINDS) return
    val storage = recipe.storageRecipe
    // Enums are fully described by their closed IR type and the generated Metadata.toAbi/fromAbi
    // pair. They do not need a standalone metadata holder once their module codec wrappers are
    // removed; array elements use the same direct planner path.
    if (storage.kind == WinRTProjectionCallSiteRecipeKind.ENUM) return
    // Plain projection outputs likewise resolve their own typed Metadata.wrap entry point from the
    // exact IR return type. Specialized mapped/closed projection helpers continue to publish ABI
    // metadata and codecs.
    if (isDirectMetadataProjection(binding, recipe)) return
    // Ordinary generated structs publish their layout and typed conversion entry points on their
    // own Metadata companion. Custom mapped structs keep their specialized runtime/module helper.
    if (storage.kind == WinRTProjectionCallSiteRecipeKind.STRUCT && customStructAbi(binding) == null) return
    // Ordinary arrays are reconstructed from their closed IR element type. Their declaration does
    // not need an ARRAY metadata holder or per-closed-array codec functions.
    if (storage.kind == WinRTProjectionCallSiteRecipeKind.ARRAY && recipe.callables == null) return
    if (storage.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
        val elementBinding = binding.typeArguments.singleOrNull()
            ?: error("Generated array ABI metadata '${binding.typeName}' has no element binding.")
        registerCallSiteAbiType(
            binding = elementBinding,
            recipe = storage.children.single(),
            support = support,
        )
    }
    val kind = when (storage.kind) {
        WinRTProjectionCallSiteRecipeKind.ENUM -> KotlinProjectionAbiTypeKind.ENUM
        WinRTProjectionCallSiteRecipeKind.STRUCT -> KotlinProjectionAbiTypeKind.STRUCT
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> KotlinProjectionAbiTypeKind.COM_REFERENCE
        WinRTProjectionCallSiteRecipeKind.ARRAY -> KotlinProjectionAbiTypeKind.ARRAY
        else -> error("Generated ABI metadata cannot describe ${storage.kind} storage.")
    }
    val reference = if (storage.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE) {
        when {
            binding.sourceTypeKind == WinRTTypeKind.RuntimeClass ||
                binding.kind == KotlinProjectionAbiValueKind.ProjectedRuntimeClass ||
                binding.kind == KotlinProjectionAbiValueKind.Object ||
                binding.kind == KotlinProjectionAbiValueKind.PropertyValue ->
                KotlinProjectionAbiReferenceKind.INSPECTABLE
            else -> KotlinProjectionAbiReferenceKind.UNKNOWN
        }
    } else {
        KotlinProjectionAbiReferenceKind.NONE
    }
    support.registerAbiType(
        KotlinProjectionAbiTypeMetadata(
            abiTypeName = binding.callSiteAbiMetadataName(projectedCallSiteType(binding)),
            kind = kind,
            carrier = (storage.valueCarrier ?: WinRTProjectionCallSiteAbiCarrier.ADDRESS).generatedCarrier(),
            transform = storage.valueTransform.generatedTransform(),
            size = storage.sizeBytes,
            alignment = storage.alignmentBytes,
            reference = reference,
        ),
    )
}

private fun WinRTProjectionCallSiteValueTransform.generatedTransform(): KotlinProjectionAbiValueTransform =
    KotlinProjectionAbiValueTransform.valueOf(name)

internal fun WinRTProjectionCallSiteAbiCarrier.generatedCarrier(): KotlinProjectionAbiCarrier =
    when (this) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS -> KotlinProjectionAbiCarrier.ADDRESS
        WinRTProjectionCallSiteAbiCarrier.INT8 -> KotlinProjectionAbiCarrier.INT8
        WinRTProjectionCallSiteAbiCarrier.INT16 -> KotlinProjectionAbiCarrier.INT16
        WinRTProjectionCallSiteAbiCarrier.INT32 -> KotlinProjectionAbiCarrier.INT32
        WinRTProjectionCallSiteAbiCarrier.INT64 -> KotlinProjectionAbiCarrier.INT64
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> KotlinProjectionAbiCarrier.FLOAT32
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> KotlinProjectionAbiCarrier.FLOAT64
    }

private val GENERATED_ABI_METADATA_RECIPE_KINDS = setOf(
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.STRUCT,
    WinRTProjectionCallSiteRecipeKind.ARRAY,
    WinRTProjectionCallSiteRecipeKind.PROJECTION,
)

private fun KotlinProjectionRenderer.materializeParameterCallSiteRecipe(
    binding: KotlinProjectionAbiParameterBinding,
    recipePlan: KotlinProjectionCallSiteRecipePlan,
    callSiteSupport: KotlinModulePlatformAbiCallSupport?,
): WinRTProjectionCallSiteRecipe {
    val recipe = materializeDirectTypeCodecs(
        binding = binding.typeBinding,
        recipe = recipePlan.recipe,
        callSiteSupport = callSiteSupport,
    )
    if (binding.category != WinRTMetadataParameterCategory.FillArray &&
        isDirectCallSiteArray(binding.typeBinding, recipe)
    ) {
        return recipe
    }
    val factory = recipePlan.inputFactory
    if (factory != null) {
        val support = callSiteSupport
            ?: binding.typeBinding.failCallSitePlan("requires a generated closed input codec without module support")
        val projectedType = projectedCallSiteType(binding.typeBinding)
        val abiTypeName = binding.typeBinding.callSiteAbiMetadataName(projectedType)
        val signature = "${binding.category}|${recipe.typeSignature}|${factory.returnType}|${factory.carrierProperties}"
        val factoryName = support.registerCodec(
            operation = "create",
            role = KotlinProjectionAbiCodecRole.CREATE_MARSHALER,
            abiTypeName = abiTypeName,
            signature = signature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__value", projectedType)),
            returnType = factory.returnType,
            body = factory.body,
        )
        val copyFromAbi = factory.copyFromAbiBody?.let { body ->
            support.registerCodec(
                operation = "copyFromAbi",
                role = KotlinProjectionAbiCodecRole.COPY_FROM_ABI,
                abiTypeName = abiTypeName,
                signature = signature,
                parameters = listOf(
                    KotlinProjectionCallSiteCodecParameter("__abi", factory.returnType),
                    KotlinProjectionCallSiteCodecParameter("__value", projectedType),
                ),
                returnType = UNIT,
                body = body,
            )
        }.orEmpty()
        return recipe.withCallSiteProjection(
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = support.codecOwnerFqName(abiTypeName),
                createMarshaler = factoryName,
                copyFromAbi = copyFromAbi,
                carrierProperty = factory.carrierProperties.first(),
                extraCarrierProperties = factory.carrierProperties.drop(1),
                closeMarshaler = factory.closeFunction,
            ),
        )
    }
    val codec = recipePlan.inputCodec ?: return recipe
    val support = callSiteSupport
        ?: binding.typeBinding.failCallSitePlan("requires a generated closed input conversion without module support")
    val projectedType = projectedCallSiteType(binding.typeBinding)
    val abiTypeName = binding.typeBinding.callSiteAbiMetadataName(projectedType)
    val functionName = support.registerCodec(
        operation = "toAbi",
        role = KotlinProjectionAbiCodecRole.TO_ABI,
        abiTypeName = abiTypeName,
        signature = "${binding.category}|${recipe.typeSignature}|${codec.returnType}",
        parameters = listOf(
            KotlinProjectionCallSiteCodecParameter("__value", projectedType),
        ),
        returnType = codec.returnType,
        body = codec.body,
    )
    val callables = WinRTProjectionCallSiteCallables(
        ownerFqName = support.codecOwnerFqName(abiTypeName),
        toAbi = functionName,
    )
    return if (recipe.kind == WinRTProjectionCallSiteRecipeKind.PROJECTION) recipe.copy(
        callables = callables,
    ) else recipe.withCallSiteProjection(
        callables = callables,
    )
}

private fun KotlinProjectionRenderer.materializeOutputCallSiteRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    recipePlan: KotlinProjectionCallSiteRecipePlan,
    callSiteSupport: KotlinModulePlatformAbiCallSupport?,
): WinRTProjectionCallSiteRecipe {
    val recipe = materializeProjectionOutputCodecs(
        binding = binding,
        recipePlan = recipePlan,
        callSiteSupport = callSiteSupport,
    ).let { materialized ->
        materializeDirectTypeCodecs(binding, materialized, callSiteSupport)
    }
    if (
        recipe.kind == WinRTProjectionCallSiteRecipeKind.VALUE ||
        recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING ||
        recipe.kind == WinRTProjectionCallSiteRecipeKind.GUID ||
        recipe.kind == WinRTProjectionCallSiteRecipeKind.ENUM
    ) {
        return recipe
    }
    if (isDirectCallSiteArray(binding, recipe)) return recipe
    val support = callSiteSupport
        ?: binding.failCallSitePlan("requires a generated closed return codec without module support")
    if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
        val projectedType = projectedCallSiteType(binding)
        val abiTypeName = binding.callSiteAbiMetadataName(projectedType)
        val outputNames = listOf("__resultLengthOut", "__resultDataOut")
        require(outputNames.size == 2) {
            "Closed WinMD array '${binding.typeName}' must expose length and data output carriers."
        }
        val parameters = outputNames.map { name ->
            KotlinProjectionCallSiteCodecParameter(name, RAW_ADDRESS_CLASS_NAME)
        }
        val fromAbiFunctionName = support.registerCodec(
            operation = "fromAbiArray",
            role = KotlinProjectionAbiCodecRole.FROM_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = parameters,
            returnType = projectedType,
            body = renderClosedArrayOutputCodec(binding, recipe),
        )
        val disposeAbiFunctionName = support.registerCodec(
            operation = "disposeAbiArray",
            role = KotlinProjectionAbiCodecRole.DISPOSE_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = parameters,
            returnType = UNIT,
            body = renderClosedArrayDisposalCodec(recipe),
        )
        return recipe.copy(
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = support.codecOwnerFqName(abiTypeName),
                fromAbi = fromAbiFunctionName,
                disposeAbi = disposeAbiFunctionName,
            ),
        )
    }
    return recipe
}

private fun KotlinProjectionRenderer.materializeDirectTypeCodecs(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    callSiteSupport: KotlinModulePlatformAbiCallSupport?,
): WinRTProjectionCallSiteRecipe {
    if (recipe.kind != WinRTProjectionCallSiteRecipeKind.ENUM &&
        recipe.kind != WinRTProjectionCallSiteRecipeKind.STRUCT
    ) {
        return recipe
    }
    val support = callSiteSupport
        ?: binding.failCallSitePlan("requires generated typed ABI codec support")
    val original = requireNotNull(recipe.callables)
    val owner = com.squareup.kotlinpoet.ClassName.bestGuess(original.ownerFqName)
    val projectedType = projectedCallSiteType(binding)
    val abiTypeName = binding.callSiteAbiMetadataName(projectedType)
    val carrierType = requireNotNull(recipe.valueCarrier).callSiteCarrierType()

    if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ENUM) {
        // Enum Metadata already owns the exact ABI carrier conversion. Keeping a module wrapper
        // here only duplicates one typed call per WinMD enum and forces the compiler plugin to
        // rediscover the same projection owner through generated codec annotations. Leave the
        // generator-composed Metadata callables intact; the IR planner resolves them from the
        // closed enum type.
        return recipe
    }

    if (customStructAbi(binding) == null) {
        // Generated struct Metadata already owns the exact closed conversion and cleanup methods.
        // Its declaration-level ABI annotation supplies layout facts directly to the IR planner.
        return recipe
    }

    val toAbi = original.toAbi.takeIf(String::isNotBlank)?.let { functionName ->
        support.registerCodec(
            operation = "toAbi",
            role = KotlinProjectionAbiCodecRole.TO_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__value", projectedType)),
            returnType = carrierType,
            body = CodeBlock.of("return %T.%L(__value)\n", owner, functionName),
        )
    }.orEmpty()
    val copyToAbi = original.copyToAbi.takeIf(String::isNotBlank)?.let { functionName ->
        support.registerCodec(
            operation = "copyToAbi",
            role = KotlinProjectionAbiCodecRole.COPY_TO_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(
                KotlinProjectionCallSiteCodecParameter("__value", projectedType),
                KotlinProjectionCallSiteCodecParameter("__abi", RAW_ADDRESS_CLASS_NAME),
            ),
            returnType = UNIT,
            body = CodeBlock.of("%T.%L(__value, __abi)\n", owner, functionName),
        )
    }.orEmpty()
    val fromAbiCarrier = original.fromAbiCarrier.takeIf(String::isNotBlank)?.let { functionName ->
        support.registerCodec(
            operation = "fromAbiCarrier",
            role = KotlinProjectionAbiCodecRole.FROM_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__abi", carrierType)),
            returnType = projectedType,
            body = CodeBlock.of("return %T.%L(__abi)\n", owner, functionName),
        )
    }.orEmpty()
    val fromAbi = original.fromAbi.takeIf(String::isNotBlank)?.let { functionName ->
        support.registerCodec(
            operation = "fromAbi",
            role = KotlinProjectionAbiCodecRole.FROM_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__abi", RAW_ADDRESS_CLASS_NAME)),
            returnType = projectedType,
            body = CodeBlock.of("return %T.%L(__abi)\n", owner, functionName),
        )
    }.orEmpty()
    val disposeAbi = original.disposeAbi.takeIf(String::isNotBlank)?.let { functionName ->
        support.registerCodec(
            operation = "disposeAbi",
            role = KotlinProjectionAbiCodecRole.DISPOSE_ABI,
            abiTypeName = abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__abi", RAW_ADDRESS_CLASS_NAME)),
            returnType = UNIT,
            body = CodeBlock.of("%T.%L(__abi)\n", owner, functionName),
        )
    }.orEmpty()
    return recipe.copy(
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = support.codecOwnerFqName(abiTypeName),
            toAbi = toAbi,
            fromAbi = fromAbi,
            copyToAbi = copyToAbi,
            disposeAbi = disposeAbi,
            fromAbiCarrier = fromAbiCarrier,
        ),
    )
}

private fun WinRTProjectionCallSiteAbiCarrier.callSiteCarrierType(): TypeName =
    when (this) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS -> RAW_ADDRESS_CLASS_NAME
        WinRTProjectionCallSiteAbiCarrier.INT8 -> Byte::class.asClassName()
        WinRTProjectionCallSiteAbiCarrier.INT16 -> Short::class.asClassName()
        WinRTProjectionCallSiteAbiCarrier.INT32 -> Int::class.asClassName()
        WinRTProjectionCallSiteAbiCarrier.INT64 -> Long::class.asClassName()
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> Float::class.asClassName()
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> Double::class.asClassName()
    }

private fun KotlinProjectionRenderer.materializeProjectionOutputCodecs(
    binding: KotlinProjectionAbiTypeBinding,
    recipePlan: KotlinProjectionCallSiteRecipePlan,
    callSiteSupport: KotlinModulePlatformAbiCallSupport?,
): WinRTProjectionCallSiteRecipe {
    if (recipePlan.outputCodecs.isEmpty()) return recipePlan.recipe
    val support = callSiteSupport
        ?: binding.failCallSitePlan("requires generated closed projection output codecs without module support")

    fun materialize(recipe: WinRTProjectionCallSiteRecipe): WinRTProjectionCallSiteRecipe {
        val codec = recipePlan.outputCodecs[recipe]
        val composed = recipe.copy(
            children = recipe.children.map(::materialize),
            fields = recipe.fields.map { field -> field.copy(recipe = materialize(field.recipe)) },
        )
        if (codec == null) return composed
        require(recipe.kind == WinRTProjectionCallSiteRecipeKind.PROJECTION) {
            "Only a projection recipe may own a closed output codec: '${recipe.typeSignature}'."
        }
        val functionName = support.registerCodec(
            operation = "fromAbi",
            role = KotlinProjectionAbiCodecRole.FROM_ABI,
            abiTypeName = codec.abiTypeName,
            signature = recipe.typeSignature,
            parameters = listOf(KotlinProjectionCallSiteCodecParameter("__abi", codec.parameterType)),
            returnType = codec.returnType,
            body = codec.body,
            consumesOwnedAbi = codec.consumesOwnedAbi,
        )
        return composed.copy(
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = support.codecOwnerFqName(codec.abiTypeName),
                fromAbi = functionName,
            ),
        )
    }

    return materialize(recipePlan.recipe)
}

/** Renders an array codec solely by folding the element recipe already attached by buildAbiCallPlan. */
private fun KotlinProjectionRenderer.renderClosedArrayOutputCodec(
    binding: KotlinProjectionAbiTypeBinding,
    arrayRecipe: WinRTProjectionCallSiteRecipe,
): CodeBlock {
    val elementBinding = binding.typeArguments.singleOrNull()
        ?: error("A closed WinMD array binding must contain exactly one element binding.")
    val element = arrayRecipe.children.singleOrNull()
        ?: error("A closed WinMD array recipe must contain exactly one element recipe.")
    val decode = arrayElementDecodeExpression(elementBinding, element)
    val cleanup = arrayElementCleanupStatement(element)
    val transfersOwnership = element.transfersArrayElementOwnership()
    return CodeBlock.builder()
        .add("val __arrayLength = %T.readInt32(__resultLengthOut)\n", PLATFORM_ABI_CLASS_NAME)
        .add("val __arrayData = %T.readPointer(__resultDataOut)\n", PLATFORM_ABI_CLASS_NAME)
        .add("require(__arrayLength >= 0) { %S }\n", "WinRT returned a negative array length.")
        .add(
            "require(__arrayLength == 0 || !%T.isNull(__arrayData)) { %S }\n",
            PLATFORM_ABI_CLASS_NAME,
            "WinRT returned a null array buffer with a non-zero length.",
        )
        .apply {
            if (transfersOwnership) add("var __transferredCount = 0\n")
        }
        .add("return try {\n")
        .indent()
        .add("Array(__arrayLength) { __index ->\n")
        .indent()
        .apply {
            if (transfersOwnership) {
                add("val __element = %L\n", decode)
                add("__transferredCount = __index + 1\n")
                add("__element\n")
            } else {
                add("%L\n", decode)
            }
        }
        .unindent()
        .add("}\n")
        .unindent()
        .add("} finally {\n")
        .indent()
        .apply {
            if (cleanup != null) {
                if (transfersOwnership) {
                    add("for (__cleanupIndex in __transferredCount until __arrayLength) {\n")
                } else {
                    add("for (__cleanupIndex in 0 until __arrayLength) {\n")
                }
                indent()
                add("val __index = __cleanupIndex\n")
                add("%L\n", cleanup)
                unindent()
                add("}\n")
            }
            add("if (!%T.isNull(__arrayData)) %T.coTaskMemFreeRaw(__arrayData)\n", PLATFORM_ABI_CLASS_NAME, WINRT_PLATFORM_API_CLASS_NAME)
        }
        .unindent()
        .add("}\n")
        .build()
}

/** Disposes a failed owned array result by folding the same element recipe as the decode codec. */
private fun renderClosedArrayDisposalCodec(
    arrayRecipe: WinRTProjectionCallSiteRecipe,
): CodeBlock {
    val element = arrayRecipe.children.singleOrNull()
        ?: error("A closed WinMD array recipe must contain exactly one element recipe.")
    val cleanup = arrayElementCleanupStatement(element)
    return CodeBlock.builder()
        .add("val __arrayLength = %T.readInt32(__resultLengthOut)\n", PLATFORM_ABI_CLASS_NAME)
        .add("val __arrayData = %T.readPointer(__resultDataOut)\n", PLATFORM_ABI_CLASS_NAME)
        .add("try {\n")
        .indent()
        .apply {
            if (cleanup != null) {
                add("if (__arrayLength > 0 && !%T.isNull(__arrayData)) {\n", PLATFORM_ABI_CLASS_NAME)
                indent()
                add("for (__cleanupIndex in 0 until __arrayLength) {\n")
                indent()
                add("val __index = __cleanupIndex\n")
                add("%L\n", cleanup)
                unindent()
                add("}\n")
                unindent()
                add("}\n")
            }
        }
        .unindent()
        .add("} finally {\n")
        .indent()
        .add(
            "if (!%T.isNull(__arrayData)) %T.coTaskMemFreeRaw(__arrayData)\n",
            PLATFORM_ABI_CLASS_NAME,
            WINRT_PLATFORM_API_CLASS_NAME,
        )
        .unindent()
        .add("}\n")
        .build()
}

private fun KotlinProjectionRenderer.arrayElementDecodeExpression(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
): CodeBlock =
    when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> {
            val raw = arrayElementCarrierExpression(recipe)
            when (recipe.valueTransform) {
                WinRTProjectionCallSiteValueTransform.IDENTITY -> raw
                WinRTProjectionCallSiteValueTransform.BOOLEAN -> CodeBlock.of("%L.toInt() != 0", raw)
                WinRTProjectionCallSiteValueTransform.UNSIGNED -> when (recipe.valueCarrier) {
                    WinRTProjectionCallSiteAbiCarrier.INT8 -> CodeBlock.of("%L.toUByte()", raw)
                    WinRTProjectionCallSiteAbiCarrier.INT16 -> CodeBlock.of("%L.toUShort()", raw)
                    WinRTProjectionCallSiteAbiCarrier.INT32 -> CodeBlock.of("%L.toUInt()", raw)
                    WinRTProjectionCallSiteAbiCarrier.INT64 -> CodeBlock.of("%L.toULong()", raw)
                    else -> error("Unsigned array recipe '${recipe.typeSignature}' has no integral carrier.")
                }
                WinRTProjectionCallSiteValueTransform.CHAR16 -> CodeBlock.of("%L.toInt().toChar()", raw)
            }
        }
        WinRTProjectionCallSiteRecipeKind.HSTRING ->
            CodeBlock.of("%T.fromAbi(%L)", NATIVE_STRING_MARSHALER_CLASS_NAME, arrayElementCarrierExpression(recipe))
        WinRTProjectionCallSiteRecipeKind.GUID ->
            CodeBlock.of("%T.readGuid(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteRecipeKind.ENUM -> {
            val callables = requireNotNull(recipe.callables)
            CodeBlock.of(
                "%T.%L(%L)",
                com.squareup.kotlinpoet.ClassName.bestGuess(callables.ownerFqName),
                callables.fromAbi,
                arrayElementDecodeExpression(binding, recipe.children.single()),
            )
        }
        WinRTProjectionCallSiteRecipeKind.STRUCT -> {
            val callables = requireNotNull(recipe.callables)
            CodeBlock.of(
                "%T.%L(%L)",
                com.squareup.kotlinpoet.ClassName.bestGuess(callables.ownerFqName),
                callables.fromAbi,
                arrayElementAddressExpression(recipe),
            )
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> {
            val pointer = arrayElementCarrierExpression(recipe)
            when (recipe.referenceAccess) {
                WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS -> pointer
                WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR ->
                    CodeBlock.of("%T.toRawComPtr(%L)", PLATFORM_ABI_CLASS_NAME, pointer)
                WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
                WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT ->
                    CodeBlock.of("%T(%T.toRawComPtr(%L))", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, pointer)
                WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE ->
                    CodeBlock.of("%T(%T.toRawComPtr(%L))", IINSPECTABLE_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, pointer)
                null -> error("COM array recipe '${recipe.typeSignature}' has no reference access contract.")
            }
        }
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
            val callables = requireNotNull(recipe.callables)
            val child = recipe.children.single()
            val abiValue = if (isDirectMetadataProjection(binding, recipe)) {
                arrayElementDecodeExpression(binding, child)
            } else {
                arrayElementCarrierExpression(child)
            }
            CodeBlock.of(
                "%T.%L(%L)",
                com.squareup.kotlinpoet.ClassName.bestGuess(callables.ownerFqName),
                callables.fromAbi,
                abiValue,
            )
        }
        WinRTProjectionCallSiteRecipeKind.ARRAY ->
            error("WinRT ABI arrays cannot contain nested array carriers: '${recipe.typeSignature}'.")
    }

private fun arrayElementCarrierExpression(recipe: WinRTProjectionCallSiteRecipe): CodeBlock =
    when (recipe.valueCarrier ?: recipe.abiCarriers.singleOrNull()) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS ->
            CodeBlock.of("%T.readPointerAt(__arrayData, __index)", PLATFORM_ABI_CLASS_NAME)
        WinRTProjectionCallSiteAbiCarrier.INT8 ->
            CodeBlock.of("%T.readInt8(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteAbiCarrier.INT16 ->
            CodeBlock.of("%T.readInt16(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteAbiCarrier.INT32 ->
            CodeBlock.of("%T.readInt32(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteAbiCarrier.INT64 ->
            CodeBlock.of("%T.readInt64(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 ->
            CodeBlock.of("%T.readFloat(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 ->
            CodeBlock.of("%T.readDouble(%L)", PLATFORM_ABI_CLASS_NAME, arrayElementAddressExpression(recipe))
        null -> error("Array element recipe '${recipe.typeSignature}' has no ABI carrier.")
    }

private fun arrayElementAddressExpression(recipe: WinRTProjectionCallSiteRecipe): CodeBlock {
    val size = recipe.arrayElementSizeBytes()
    return CodeBlock.of(
        "%T.slice(__arrayData, __index.toLong() * %L, %L)",
        PLATFORM_ABI_CLASS_NAME,
        size,
        size,
    )
}

private tailrec fun WinRTProjectionCallSiteRecipe.arrayStorageRecipe(): WinRTProjectionCallSiteRecipe =
    if (kind == WinRTProjectionCallSiteRecipeKind.PROJECTION) children.single().arrayStorageRecipe() else this

private fun WinRTProjectionCallSiteRecipe.arrayElementSizeBytes(): Long =
    when (val storage = arrayStorageRecipe()) {
        is WinRTProjectionCallSiteRecipe -> when (storage.kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE,
            WinRTProjectionCallSiteRecipeKind.ENUM -> when (storage.valueCarrier ?: storage.abiCarriers.singleOrNull()) {
                WinRTProjectionCallSiteAbiCarrier.INT8 -> 1L
                WinRTProjectionCallSiteAbiCarrier.INT16 -> 2L
                WinRTProjectionCallSiteAbiCarrier.INT32,
                WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> 4L
                WinRTProjectionCallSiteAbiCarrier.INT64,
                WinRTProjectionCallSiteAbiCarrier.FLOAT64,
                WinRTProjectionCallSiteAbiCarrier.ADDRESS -> 8L
                null -> error("Array element recipe '$typeSignature' has no sized carrier.")
            }
            WinRTProjectionCallSiteRecipeKind.GUID -> 16L
            WinRTProjectionCallSiteRecipeKind.STRUCT -> storage.sizeBytes.toLong()
            WinRTProjectionCallSiteRecipeKind.HSTRING,
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> 8L
            WinRTProjectionCallSiteRecipeKind.PROJECTION,
            WinRTProjectionCallSiteRecipeKind.ARRAY -> error("Array storage recipe did not reduce to one ABI element.")
        }
    }

private fun arrayElementCleanupStatement(recipe: WinRTProjectionCallSiteRecipe): CodeBlock? =
    when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.GUID -> null
        WinRTProjectionCallSiteRecipeKind.HSTRING ->
            CodeBlock.of("%T.disposeAbi(%L)", NATIVE_STRING_MARSHALER_CLASS_NAME, arrayElementCarrierExpression(recipe))
        WinRTProjectionCallSiteRecipeKind.ENUM -> arrayElementCleanupStatement(recipe.children.single())
        WinRTProjectionCallSiteRecipeKind.STRUCT -> recipe.callables
            ?.disposeAbi
            ?.takeIf(String::isNotBlank)
            ?.let { functionName ->
                CodeBlock.of(
                    "%T.%L(%L)",
                    com.squareup.kotlinpoet.ClassName.bestGuess(requireNotNull(recipe.callables).ownerFqName),
                    functionName,
                    arrayElementAddressExpression(recipe),
                )
            }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> {
            val pointer = arrayElementCarrierExpression(recipe)
            CodeBlock.of(
                "if (!%T.isNull(%L)) %T(%T.toRawComPtr(%L)).close()",
                PLATFORM_ABI_CLASS_NAME,
                pointer,
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                pointer,
            )
        }
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
            val callables = requireNotNull(recipe.callables)
            callables.disposeAbi.takeIf(String::isNotBlank)?.let { functionName ->
                CodeBlock.of(
                    "%T.%L(%L)",
                    com.squareup.kotlinpoet.ClassName.bestGuess(callables.ownerFqName),
                    functionName,
                    arrayElementCarrierExpression(recipe.children.single()),
                )
            } ?: arrayElementCleanupStatement(recipe.children.single())
        }
        WinRTProjectionCallSiteRecipeKind.ARRAY ->
            error("WinRT ABI arrays cannot contain nested array carriers: '${recipe.typeSignature}'.")
    }

private fun WinRTProjectionCallSiteRecipe.transfersArrayElementOwnership(): Boolean =
    when (kind) {
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> true
        WinRTProjectionCallSiteRecipeKind.PROJECTION ->
            requireNotNull(callables).disposeAbi.isBlank() && children.single().transfersArrayElementOwnership()
        WinRTProjectionCallSiteRecipeKind.ENUM -> children.single().transfersArrayElementOwnership()
        else -> false
    }

private fun WinRTProjectionCallSiteRecipe.withCallSiteProjection(
    callables: WinRTProjectionCallSiteCallables,
): WinRTProjectionCallSiteRecipe =
    WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
        abiCarriers = abiCarriers,
        valueCarrier = valueCarrier,
        nullable = nullable,
        callables = callables,
        children = listOf(this),
        typeSignature = typeSignature,
    )

private fun KotlinProjectionRenderer.projectedCallSiteType(binding: KotlinProjectionAbiTypeBinding): TypeName =
    runCatching { resolveTypeName(binding.typeName) }
        .getOrElse { failure -> binding.failCallSitePlan("cannot resolve its projected type", failure) }

private fun KotlinProjectionComArgumentKind.callSiteCarrierType(): TypeName =
    when (this) {
        KotlinProjectionComArgumentKind.Pointer -> RAW_ADDRESS_CLASS_NAME
        KotlinProjectionComArgumentKind.Int8 -> Byte::class.asClassName()
        KotlinProjectionComArgumentKind.Int16 -> Short::class.asClassName()
        KotlinProjectionComArgumentKind.Int32 -> Int::class.asClassName()
        KotlinProjectionComArgumentKind.Int64 -> Long::class.asClassName()
        KotlinProjectionComArgumentKind.Float -> Float::class.asClassName()
        KotlinProjectionComArgumentKind.Double -> Double::class.asClassName()
    }

private fun KotlinProjectionAbiTypeBinding.failCallSitePlan(
    detail: String,
    cause: Throwable? = null,
): Nothing =
    throw IllegalStateException(
        "WinMD call-site slot '$typeName' ($resolvedTypeName, $kind) $detail.",
        cause,
    )

private fun WinRTMetadataParameterCategory.callSiteDirection(): WinRTProjectionCallSiteSlotDirection =
    when (this) {
        WinRTMetadataParameterCategory.In -> WinRTProjectionCallSiteSlotDirection.IN
        WinRTMetadataParameterCategory.Ref -> WinRTProjectionCallSiteSlotDirection.REF
        WinRTMetadataParameterCategory.Out -> WinRTProjectionCallSiteSlotDirection.OUT
        WinRTMetadataParameterCategory.PassArray -> WinRTProjectionCallSiteSlotDirection.PASS_ARRAY
        WinRTMetadataParameterCategory.FillArray -> WinRTProjectionCallSiteSlotDirection.FILL_ARRAY
        WinRTMetadataParameterCategory.ReceiveArray -> WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY
    }

private fun WinRTProjectionCallSiteSlotDirection.parameterDirection():
    io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection =
    when (this) {
        WinRTProjectionCallSiteSlotDirection.IN ->
            io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection.IN
        WinRTProjectionCallSiteSlotDirection.REF ->
            io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection.REF
        WinRTProjectionCallSiteSlotDirection.OUT ->
            io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection.OUT
        WinRTProjectionCallSiteSlotDirection.PASS_ARRAY ->
            io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection.PASS_ARRAY
        WinRTProjectionCallSiteSlotDirection.FILL_ARRAY ->
            io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection.FILL_ARRAY
        WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY,
        WinRTProjectionCallSiteSlotDirection.RETURN,
        WinRTProjectionCallSiteSlotDirection.CALLER_OUT -> error(
            "A $this slot is not a typed CallSite function parameter.",
        )
    }

private fun WinRTMetadataParameterCategory.parameterOwnership(
    recipe: WinRTProjectionCallSiteRecipe,
): WinRTProjectionCallSiteOwnership =
    when {
        !recipe.requiresNativeOwnership() -> WinRTProjectionCallSiteOwnership.NONE
        this == WinRTMetadataParameterCategory.In || this == WinRTMetadataParameterCategory.PassArray ->
            WinRTProjectionCallSiteOwnership.BORROWED
        else -> WinRTProjectionCallSiteOwnership.OWNED
    }

private fun WinRTProjectionCallSiteRecipe.requiresNativeOwnership(): Boolean = requiresNativeOwnership
