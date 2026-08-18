package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory

/** The sole recursive recipe builder, called only while [buildAbiCallPlan] is assembled. */
internal fun KotlinProjectionRenderer.buildCallSiteRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    category: WinRTMetadataParameterCategory? = WinRTMetadataParameterCategory.In,
): KotlinProjectionCallSiteRecipePlan {
    require(binding.kind != KotlinProjectionAbiValueKind.GenericParameter) {
        "Closed WinMD call-site shape '${binding.typeName}' still contains a generic parameter."
    }
    val recipe = buildStorageRecipe(binding).copy(
        nullable = isNullableProjectedType(binding),
        typeSignature = callSiteTypeSignature(binding),
    )
    val childInputValueAdapters = if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
        val elementBinding = binding.typeArguments.singleOrNull()
            ?: error("Closed WinMD array '${binding.typeName}' has no element binding.")
        val elementRecipe = recipe.children.singleOrNull()
            ?: error("Closed WinMD array '${binding.typeName}' has no element recipe.")
        listOf(buildCallSiteInputValueAdapter(elementBinding, elementRecipe))
    } else {
        emptyList()
    }
    val outputCodecs = linkedMapOf<WinRTProjectionCallSiteRecipe, KotlinProjectionCallSiteOutputCodec>()
    collectProjectionOutputCodecs(binding, recipe, outputCodecs)
    val inputFactory = category?.let {
        buildCallSiteInputFactory(binding, recipe, childInputValueAdapters.singleOrNull(), it)
    }
    return KotlinProjectionCallSiteRecipePlan(
        recipe = recipe,
        inputFactory = inputFactory,
        inputCodec = category?.let { buildCallSiteInputCodec(binding, recipe, inputFactory != null, it) },
        inputValueAdapter = buildCallSiteInputValueAdapter(binding, recipe),
        childInputValueAdapters = childInputValueAdapters,
        outputCodecs = outputCodecs,
    )
}

internal data class KotlinProjectionCallSiteRecipePlan(
    val recipe: WinRTProjectionCallSiteRecipe,
    val inputFactory: KotlinProjectionCallSiteFactory? = null,
    val inputCodec: KotlinProjectionCallSiteInputCodec? = null,
    val inputValueAdapter: CodeBlock? = null,
    val childInputValueAdapters: List<CodeBlock?> = emptyList(),
    val outputCodecs: Map<WinRTProjectionCallSiteRecipe, KotlinProjectionCallSiteOutputCodec>,
) {
    init {
        require(inputFactory == null || inputCodec == null) {
            "A closed WinMD recipe cannot use both an input resource factory and a direct input codec."
        }
    }
}

internal data class KotlinProjectionCallSiteOutputCodec(
    val abiTypeName: String,
    val parameterType: TypeName,
    val returnType: TypeName,
    val body: CodeBlock,
    /** The codec expects an owned COM value and therefore cannot decode a borrowed inbound argument. */
    val consumesOwnedAbi: Boolean,
)

/** Ordinary inline array elements are fully recoverable from the closed IR element declaration. */
internal fun KotlinProjectionRenderer.isDirectCallSiteArray(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
): Boolean {
    if (recipe.kind != WinRTProjectionCallSiteRecipeKind.ARRAY) return false
    val elementBinding = binding.typeArguments.singleOrNull() ?: return false
    val elementRecipe = recipe.children.singleOrNull() ?: return false
    return when (elementRecipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.GUID,
        WinRTProjectionCallSiteRecipeKind.ENUM -> true
        WinRTProjectionCallSiteRecipeKind.STRUCT -> customStructAbi(elementBinding) == null
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.PROJECTION,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> false
    }
}

private fun KotlinProjectionRenderer.buildCallSiteInputFactory(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    childInputValueAdapter: CodeBlock?,
    category: WinRTMetadataParameterCategory,
): KotlinProjectionCallSiteFactory? {
    if (!category.acceptsProjectedInput()) return null
    if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
        val elementRecipe = recipe.children.singleOrNull()
            ?: error("Closed WinMD array '${binding.typeName}' has no element recipe.")
        val elementMarshaler = arrayElementMarshalerFromRecipe(elementRecipe, childInputValueAdapter)
        return KotlinProjectionCallSiteFactory(
            returnType = WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true),
            body = CodeBlock.of(
                "val __elementMarshaler = %L\nreturn __elementMarshaler.createMarshalerArray(__value)\n",
                elementMarshaler,
            ),
            carrierProperties = listOf("length", "data"),
            copyFromAbiBody = if (category == WinRTMetadataParameterCategory.FillArray) {
                CodeBlock.of(
                    "val __elementMarshaler = %L\n" +
                        "__elementMarshaler.fromAbiArray(__value.size, __abi?.data ?: %T.nullPointer)" +
                        "?.forEachIndexed { __index, __element -> " +
                        "(__value as Array<Any?>)[__index] = __element }\n",
                    elementMarshaler,
                    PLATFORM_ABI_CLASS_NAME,
                )
            } else {
                null
            },
        )
    }

    customObjectAbi(binding)?.let { customAbi ->
        val nullable = isNullableProjectedType(binding)
        return KotlinProjectionCallSiteFactory(
            returnType = COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = nullable),
            body = CodeBlock.of(
                "return %T.%L(__value, %T(%S))\n",
                WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                if (nullable) "createObjectReferenceOrNull" else customAbi.createReferenceFunctionName,
                GUID_CLASS_NAME,
                customAbi.interfaceId.toString(),
            ),
            carrierProperties = listOf("pointer"),
        )
    }

    if (binding.kind == KotlinProjectionAbiValueKind.Delegate) {
        return KotlinProjectionCallSiteFactory(
            returnType = WINRT_DELEGATE_ARGUMENT_MARSHALER_CLASS_NAME,
            body = CodeBlock.of(
                "return %T.createProjectedDelegateArgument(__value)\n",
                WINRT_DELEGATE_BRIDGE_CLASS_NAME,
            ),
            carrierProperties = listOf("abi"),
        )
    }

    val adapter = mappedCallSiteAdapter(binding) ?: return null
    val returnType = adapter.inputFactoryReturnType ?: return null
    val body = when {
        adapter.inputUsesSelfReferenceAdapter -> {
            val referenceAdapter = collectionReferenceAdapterCode(binding, hoistMetadata = true)
                ?: error("Mapped WinMD input '${binding.typeName}' has no closed self adapter.")
            CodeBlock.of("return %L.createInputMarshaler(__value)\n", referenceAdapter)
        }
        else -> {
            val owner = adapter.runtimeProjectionClassName
                ?: error("Mapped WinMD input '${binding.typeName}' has no static factory owner.")
            val arguments = buildList {
                if (adapter.inputUsesParameterizedInterfaceId) {
                    add(referenceInterfaceIdCode(binding, hoistMetadata = true)
                        ?: error("Mapped WinMD input '${binding.typeName}' has no parameterized IID."))
                } else {
                    require(binding.typeArguments.size == adapter.inputTypeArgumentAdapterCount) {
                        "Mapped WinMD input '${binding.typeName}' expected " +
                            "${adapter.inputTypeArgumentAdapterCount} closed adapter arguments."
                    }
                    binding.typeArguments.forEach { argument ->
                        add(collectionReferenceAdapterCode(argument, hoistMetadata = true)
                            ?: error("Mapped WinMD input '${binding.typeName}' has an unbound argument adapter."))
                    }
                    if (binding.kind == KotlinProjectionAbiValueKind.MappedMapView ||
                        binding.kind == KotlinProjectionAbiValueKind.MappedMap
                    ) {
                        add(collectionInterfaceIdCode(binding, hoistMetadata = true)
                            ?: error("Mapped WinMD input '${binding.typeName}' has no collection IID."))
                    }
                }
            }
            CodeBlock.builder()
                .add("return %T.%L(__value", owner, adapter.createMarshalerFunctionName)
                .apply { arguments.forEach { argument -> add(", %L", argument) } }
                .add(")")
                .apply {
                    if (adapter.rejectNullInputFactory) {
                        add(" ?: error(%S)", "Unable to marshal closed WinRT input ${binding.typeName}.")
                    }
                }
                .add("\n")
                .build()
        }
    }
    return KotlinProjectionCallSiteFactory(
        returnType = returnType.copy(nullable = adapter.inputFactoryNullable),
        body = body,
        carrierProperties = listOf("abi"),
    )
}

private fun KotlinProjectionRenderer.buildCallSiteInputCodec(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    hasInputFactory: Boolean,
    category: WinRTMetadataParameterCategory,
): KotlinProjectionCallSiteInputCodec? {
    if (!category.acceptsProjectedInput()) return null
    if (hasInputFactory) return null
    if (isDirectMetadataProjection(binding, recipe)) return null
    if (recipe.kind != WinRTProjectionCallSiteRecipeKind.PROJECTION) return null
    val child = recipe.children.singleOrNull() ?: return null
    val callables = recipe.callables
    callables?.toAbi?.takeIf(String::isNotBlank)?.let { functionName ->
        return KotlinProjectionCallSiteInputCodec(
            returnType = RAW_ADDRESS_CLASS_NAME,
            body = CodeBlock.of(
                "return %T.%L(__value)\n",
                ClassName.bestGuess(callables.ownerFqName),
                functionName,
            ),
        )
    }
    require(callables?.createMarshaler.isNullOrBlank()) {
        "Projected WinMD input '${binding.typeName}' has a marshaler callable but no generated input factory."
    }
    val body = when (child.referenceAccess) {
        WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT -> if (isNullableProjectedType(binding)) {
            CodeBlock.of(
                "return if (__value == null) {\n" +
                    "  %T.nullPointer\n" +
                    "} else {\n" +
                    "  %T.fromRawComPtr((__value as %T).nativeObject.pointer)\n" +
                    "}\n",
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                IWINRT_OBJECT_CLASS_NAME,
            )
        } else CodeBlock.of(
            "return %T.fromRawComPtr((__value as %T).nativeObject.pointer)\n",
            PLATFORM_ABI_CLASS_NAME,
            IWINRT_OBJECT_CLASS_NAME,
        )
        else -> null
    } ?: return null
    return KotlinProjectionCallSiteInputCodec(returnType = RAW_ADDRESS_CLASS_NAME, body = body)
}

private fun WinRTMetadataParameterCategory.acceptsProjectedInput(): Boolean =
    this != WinRTMetadataParameterCategory.Out && this != WinRTMetadataParameterCategory.ReceiveArray

private fun arrayElementMarshalerFromRecipe(
    recipe: WinRTProjectionCallSiteRecipe,
    inputValueAdapter: CodeBlock?,
): CodeBlock = when (recipe.kind) {
    WinRTProjectionCallSiteRecipeKind.VALUE -> {
        val functionName = when (recipe.valueTransform) {
            WinRTProjectionCallSiteValueTransform.BOOLEAN -> "boolean"
            WinRTProjectionCallSiteValueTransform.CHAR16 -> "char16"
            WinRTProjectionCallSiteValueTransform.UNSIGNED -> when (recipe.valueCarrier) {
                WinRTProjectionCallSiteAbiCarrier.INT8 -> "uint8"
                WinRTProjectionCallSiteAbiCarrier.INT16 -> "uint16"
                WinRTProjectionCallSiteAbiCarrier.INT32 -> "uint32"
                WinRTProjectionCallSiteAbiCarrier.INT64 -> "uint64"
                else -> error("Unsigned array recipe '${recipe.typeSignature}' has no integral carrier.")
            }
            WinRTProjectionCallSiteValueTransform.IDENTITY -> when (recipe.valueCarrier) {
                WinRTProjectionCallSiteAbiCarrier.INT8 -> "int8"
                WinRTProjectionCallSiteAbiCarrier.INT16 -> "int16"
                WinRTProjectionCallSiteAbiCarrier.INT32 -> "int32"
                WinRTProjectionCallSiteAbiCarrier.INT64 -> "int64"
                WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> "float32"
                WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> "float64"
                WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                null -> error("Value array recipe '${recipe.typeSignature}' has no scalar carrier.")
            }
        }
        CodeBlock.of("%T.%L()", MARSHALER_CLASS_NAME, functionName)
    }
    WinRTProjectionCallSiteRecipeKind.HSTRING -> CodeBlock.of("%T.string()", MARSHALER_CLASS_NAME)
    WinRTProjectionCallSiteRecipeKind.GUID -> CodeBlock.of("%T.guid()", MARSHALER_CLASS_NAME)
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.STRUCT,
    WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
    WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
        val adapter = inputValueAdapter
            ?: error("Closed array element recipe '${recipe.typeSignature}' has no static value adapter.")
        CodeBlock.of("%T.referenceValueAdapter(%L)", MARSHALER_CLASS_NAME, adapter)
    }
    WinRTProjectionCallSiteRecipeKind.ARRAY ->
        error("WinRT ABI arrays cannot contain nested array carriers: '${recipe.typeSignature}'.")
}

private fun KotlinProjectionRenderer.buildCallSiteInputValueAdapter(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
): CodeBlock? = when (recipe.kind) {
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.STRUCT,
    WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
    WinRTProjectionCallSiteRecipeKind.PROJECTION -> collectionReferenceAdapterCode(binding, hoistMetadata = true)
    WinRTProjectionCallSiteRecipeKind.VALUE,
    WinRTProjectionCallSiteRecipeKind.HSTRING,
    WinRTProjectionCallSiteRecipeKind.GUID,
    WinRTProjectionCallSiteRecipeKind.ARRAY -> null
}

private fun KotlinProjectionRenderer.collectProjectionOutputCodecs(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    output: MutableMap<WinRTProjectionCallSiteRecipe, KotlinProjectionCallSiteOutputCodec>,
) {
    when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
            if (!isDirectMetadataProjection(binding, recipe)) {
                output[recipe] = buildProjectionOutputCodec(binding, recipe)
            }
        }
        WinRTProjectionCallSiteRecipeKind.ARRAY -> {
            collectProjectionOutputCodecs(
                binding = binding.typeArguments.single(),
                recipe = recipe.children.single(),
                output = output,
            )
        }
        WinRTProjectionCallSiteRecipeKind.STRUCT -> {
            binding.structFieldBindings.zip(recipe.fields).forEach { (fieldBinding, field) ->
                collectProjectionOutputCodecs(fieldBinding, field.recipe, output)
            }
        }
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.GUID,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> Unit
    }
}

/** Plain projections use their own typed Metadata.wrap entry point in the IR plugin. */
internal fun KotlinProjectionRenderer.isDirectMetadataProjection(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
): Boolean {
    if (binding.kind != KotlinProjectionAbiValueKind.ProjectedInterface &&
        binding.kind != KotlinProjectionAbiValueKind.ProjectedRuntimeClass
    ) {
        return false
    }
    if (binding.typeArguments.isNotEmpty() || recipe.kind != WinRTProjectionCallSiteRecipeKind.PROJECTION) {
        return false
    }
    val metadataOwner = projectedClassName(binding)?.nestedClass("Metadata")?.canonicalName ?: return false
    val callables = recipe.callables ?: return false
    // Expect/actual interface artifacts use a separate *JvmProjection owner and therefore remain
    // specialized. The exact owner comparison keeps that distinction structural, not layout-based.
    return callables.ownerFqName == metadataOwner &&
        callables.fromAbi == "wrap" &&
        callables.toAbi.isBlank() &&
        callables.createMarshaler.isBlank()
}

private fun KotlinProjectionRenderer.buildStorageRecipe(
    binding: KotlinProjectionAbiTypeBinding,
): WinRTProjectionCallSiteRecipe {
    val typeSignature = callSiteTypeSignature(binding)
    customObjectAbi(binding)?.let { customAbi ->
        val child = comReferenceRecipe(
            binding = binding,
            access = WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
        )
        return projectionRecipe(
            binding = binding,
            child = child,
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME.canonicalName,
                fromAbi = customAbi.fromAbiFunctionName,
                createMarshaler = customAbi.createReferenceFunctionName,
                carrierProperty = "pointer",
            ),
        )
    }
    mappedCallSiteAdapter(binding)?.let { adapter ->
        return mappedProjectionRecipe(binding, adapter)
    }
    return when (binding.kind) {
        KotlinProjectionAbiValueKind.Unit -> error("Unit has no ABI marshaler recipe.")
        KotlinProjectionAbiValueKind.Unsupported -> {
            require(binding.resolvedTypeName.isProjectedWinRTInterfaceReferenceName()) {
                "WinMD call-site type '${binding.typeName}' has no ABI recipe."
            }
            projectedTypeRecipe(binding)
        }
        KotlinProjectionAbiValueKind.String -> WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.HSTRING,
            abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
            valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            nullable = isNullableProjectedType(binding),
            typeSignature = typeSignature,
        )
        KotlinProjectionAbiValueKind.Boolean -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT8,
            WinRTProjectionCallSiteValueTransform.BOOLEAN,
        )
        KotlinProjectionAbiValueKind.Int8 -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.INT8)
        KotlinProjectionAbiValueKind.UInt8 -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT8,
            WinRTProjectionCallSiteValueTransform.UNSIGNED,
        )
        KotlinProjectionAbiValueKind.Int16 -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.INT16)
        KotlinProjectionAbiValueKind.UInt16 -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT16,
            WinRTProjectionCallSiteValueTransform.UNSIGNED,
        )
        KotlinProjectionAbiValueKind.Int32 -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.INT32)
        KotlinProjectionAbiValueKind.UInt32 -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT32,
            WinRTProjectionCallSiteValueTransform.UNSIGNED,
        )
        KotlinProjectionAbiValueKind.Int64 -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.INT64)
        KotlinProjectionAbiValueKind.UInt64 -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT64,
            WinRTProjectionCallSiteValueTransform.UNSIGNED,
        )
        KotlinProjectionAbiValueKind.Float -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.FLOAT32)
        KotlinProjectionAbiValueKind.Double -> valueRecipe(binding, WinRTProjectionCallSiteAbiCarrier.FLOAT64)
        KotlinProjectionAbiValueKind.Char16 -> valueRecipe(
            binding,
            WinRTProjectionCallSiteAbiCarrier.INT16,
            WinRTProjectionCallSiteValueTransform.CHAR16,
        )
        KotlinProjectionAbiValueKind.RawAddress -> comReferenceRecipe(
            binding,
            WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS,
        )
        KotlinProjectionAbiValueKind.RawComPtr -> comReferenceRecipe(
            binding,
            WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR,
        )
        KotlinProjectionAbiValueKind.UnknownReference -> comReferenceRecipe(
            binding,
            WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
        )
        KotlinProjectionAbiValueKind.InspectableReference -> comReferenceRecipe(
            binding,
            WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE,
        )
        KotlinProjectionAbiValueKind.GuidValue -> WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.GUID,
            abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
            valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            nullable = isNullableProjectedType(binding),
            sizeBytes = 16,
            alignmentBytes = binding.abiAlignment?.takeIf { it > 0 } ?: 4,
            typeSignature = typeSignature,
        )
        KotlinProjectionAbiValueKind.Enum -> enumRecipe(binding)
        KotlinProjectionAbiValueKind.Struct -> structRecipe(binding)
        KotlinProjectionAbiValueKind.Array -> arrayRecipe(binding)
        KotlinProjectionAbiValueKind.GenericParameter -> error(
            "Closed WinMD call-site shape '${binding.typeName}' still contains a generic parameter.",
        )
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> projectedTypeRecipe(binding)
        KotlinProjectionAbiValueKind.Delegate -> projectedMetadataRecipe(
            binding,
            fromAbi = "fromAbi",
            createMarshaler = "createReference",
        )
        else -> error("Mapped WinMD call-site type '${binding.typeName}' has no central adapter description.")
    }
}

private fun KotlinProjectionRenderer.valueRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    carrier: WinRTProjectionCallSiteAbiCarrier,
    transform: WinRTProjectionCallSiteValueTransform = WinRTProjectionCallSiteValueTransform.IDENTITY,
): WinRTProjectionCallSiteRecipe =
    WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.VALUE,
        abiCarriers = listOf(carrier),
        valueCarrier = carrier,
        valueTransform = transform,
        nullable = isNullableProjectedType(binding),
        typeSignature = callSiteTypeSignature(binding),
    )

private fun KotlinProjectionRenderer.comReferenceRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    access: WinRTProjectionCallSiteReferenceAccess,
): WinRTProjectionCallSiteRecipe =
    WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
        valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        referenceAccess = access,
        nullable = isNullableProjectedType(binding),
        typeSignature = callSiteTypeSignature(binding),
    )

private fun KotlinProjectionRenderer.enumRecipe(
    binding: KotlinProjectionAbiTypeBinding,
): WinRTProjectionCallSiteRecipe {
    val carrier = binding.enumUnderlyingType?.callSiteCarrier()
        ?: error("WinMD enum '${binding.typeName}' has no underlying ABI carrier.")
    val owner = projectedClassName(binding)?.nestedClass("Metadata")
        ?: error("WinMD enum '${binding.typeName}' has no projected Metadata owner.")
    return WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.ENUM,
        abiCarriers = listOf(carrier),
        valueCarrier = carrier,
        nullable = isNullableProjectedType(binding),
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = owner.canonicalName,
            toAbi = "toAbi",
            fromAbi = "fromAbi",
        ),
        children = listOf(
            valueRecipe(
                binding.copy(kind = binding.enumUnderlyingType.valueKind()),
                carrier,
                binding.enumUnderlyingType.callSiteValueTransform(),
            ),
        ),
        typeSignature = callSiteTypeSignature(binding),
    )
}

private fun KotlinProjectionRenderer.structRecipe(
    binding: KotlinProjectionAbiTypeBinding,
): WinRTProjectionCallSiteRecipe {
    val custom = customStructAbi(binding)
    val size = binding.abiSize?.takeIf { it > 0 }
        ?: custom?.sizeBytes?.toInt()?.takeIf { it > 0 }
        ?: error("WinMD struct '${binding.typeName}' has no resolved ABI size.")
    val alignment = binding.abiAlignment?.takeIf { it > 0 }
        ?: custom?.alignmentBytes?.takeIf { it > 0 }
        ?: custom?.abiArgumentKind?.storageAlignment()
        ?: error("WinMD struct '${binding.typeName}' has no resolved ABI alignment.")
    val fieldRecipes = if (custom == null) binding.structFieldBindings.map(::buildStorageRecipe) else emptyList()
    val offsets = if (custom == null) binding.structFieldOffsets else emptyList()
    if (custom == null) {
        require(fieldRecipes.isNotEmpty()) {
            "WinMD struct '${binding.typeName}' has no recursively resolved fields."
        }
        require(offsets.size == fieldRecipes.size) {
            "WinMD struct '${binding.typeName}' has no complete resolved field-offset graph."
        }
    }
    val owner = custom?.helperTypeName ?: projectedClassName(binding)?.nestedClass("Metadata")
        ?: error("WinMD struct '${binding.typeName}' has no static ABI owner.")
    val carrier = custom?.abiArgumentKind?.callSiteCarrier()
        ?: size.win64StructArgumentCarrier()
    return WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.STRUCT,
        abiCarriers = listOf(carrier),
        valueCarrier = carrier,
        nullable = isNullableProjectedType(binding),
        sizeBytes = size,
        alignmentBytes = alignment,
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = owner.canonicalName,
            toAbi = custom?.toAbiFunctionName.orEmpty(),
            fromAbi = custom?.fromAbiFunctionName ?: "fromAbi",
            copyToAbi = custom?.copyToFunctionName ?: "copyTo",
            disposeAbi = if (custom == null) "disposeAbi" else custom.disposeAbiFunctionName.orEmpty(),
            fromAbiCarrier = custom?.fromAbiCarrierFunctionName.orEmpty(),
        ),
        fields = offsets.zip(fieldRecipes) { offset, recipe ->
            WinRTProjectionCallSiteStructField(offset, recipe)
        },
        typeSignature = callSiteTypeSignature(binding),
    )
}

internal fun Int.win64StructArgumentCarrier(): WinRTProjectionCallSiteAbiCarrier = when (this) {
    Byte.SIZE_BYTES -> WinRTProjectionCallSiteAbiCarrier.INT8
    Short.SIZE_BYTES -> WinRTProjectionCallSiteAbiCarrier.INT16
    Int.SIZE_BYTES -> WinRTProjectionCallSiteAbiCarrier.INT32
    Long.SIZE_BYTES -> WinRTProjectionCallSiteAbiCarrier.INT64
    else -> WinRTProjectionCallSiteAbiCarrier.ADDRESS
}

private fun KotlinProjectionRenderer.arrayRecipe(
    binding: KotlinProjectionAbiTypeBinding,
): WinRTProjectionCallSiteRecipe {
    val element = binding.typeArguments.singleOrNull()
        ?: error("WinMD array '${binding.typeName}' has no concrete element binding.")
    require(element.kind != KotlinProjectionAbiValueKind.GenericParameter) {
        "Closed WinMD array '${binding.typeName}' still contains a generic element parameter."
    }
    return WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.ARRAY,
        abiCarriers = listOf(
            WinRTProjectionCallSiteAbiCarrier.INT32,
            WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        ),
        valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        nullable = isNullableProjectedType(binding),
        children = listOf(buildStorageRecipe(element)),
        typeSignature = callSiteTypeSignature(binding),
    )
}

private fun KotlinProjectionRenderer.projectedTypeRecipe(
    binding: KotlinProjectionAbiTypeBinding,
): WinRTProjectionCallSiteRecipe =
    binding.closedGenericProjectionHelperClassName(supportOwnerIdentity)?.let { owner ->
        projectedOwnerRecipe(binding, owner = owner, fromAbi = "wrap")
    } ?: projectedMetadataRecipe(binding, fromAbi = "wrap")

private fun KotlinProjectionRenderer.projectedMetadataRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    fromAbi: String,
    createMarshaler: String = "",
): WinRTProjectionCallSiteRecipe {
    val owner = projectedClassName(binding)?.nestedClass("Metadata")
        ?: error("WinMD projection '${binding.typeName}' has no static Metadata owner.")
    return projectedOwnerRecipe(binding, owner, fromAbi, createMarshaler)
}

private fun KotlinProjectionRenderer.projectedOwnerRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    owner: ClassName,
    fromAbi: String,
    createMarshaler: String = "",
): WinRTProjectionCallSiteRecipe {
    return projectionRecipe(
        binding = binding,
        child = comReferenceRecipe(binding, WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT),
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = owner.canonicalName,
            fromAbi = fromAbi,
            createMarshaler = createMarshaler,
            carrierProperty = if (createMarshaler.isEmpty()) "" else "abi",
        ),
    )
}

private fun KotlinProjectionRenderer.mappedProjectionRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    adapter: KotlinProjectionMappedCallSiteAdapter,
): WinRTProjectionCallSiteRecipe {
    val owner = if (adapter.usesClosedGenericHelper) {
        binding.closedGenericProjectionHelperClassName(supportOwnerIdentity)
            ?: error("Closed mapped projection '${binding.typeName}' has no metadata-composed helper.")
    } else {
        adapter.runtimeProjectionClassName
            ?: error("Mapped projection '${binding.typeName}' has no static call-site owner.")
    }
    return projectionRecipe(
        binding = binding,
        child = comReferenceRecipe(binding, WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT),
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = owner.canonicalName,
            fromAbi = adapter.fromAbiFunctionName,
            toAbi = adapter.toAbiFunctionName,
            createMarshaler = adapter.createMarshalerFunctionName.takeIf {
                adapter.inputFactoryReturnType == null
            }.orEmpty(),
            carrierProperty = adapter.createMarshalerFunctionName.takeIf {
                adapter.inputFactoryReturnType == null
            }?.let { "abi" }.orEmpty(),
            disposeAbi = if (adapter.usesClosedGenericHelper) "disposeAbi" else "",
        ),
    )
}

internal fun mappedCallSiteType(
    binding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionMappedType? =
    sequenceOf(binding.resolvedTypeName, binding.typeName)
        .map { typeName -> typeName.substringBefore('<').removeSuffix("?") }
        .distinct()
        .mapNotNull(::mappedTypeByAbiName)
        .firstOrNull { mappedType -> mappedType.abiValueKind == binding.kind }

internal fun KotlinProjectionAbiTypeBinding.explicitCallSiteAbiTypeName(): String =
    closedCallSiteAbiTypeName().takeIf { hasExplicitCallSiteAbiIdentity() }.orEmpty()

internal fun KotlinProjectionAbiTypeBinding.callSiteAbiMetadataName(projectedType: TypeName): String =
    explicitCallSiteAbiTypeName().ifEmpty(projectedType::toString)

internal fun KotlinProjectionAbiTypeBinding.closedCallSiteAbiTypeName(): String {
    val rootName = mappedCallSiteType(this)?.abiQualifiedName
        ?: resolvedTypeName.substringBefore('<').removeSuffix("?")
    if (typeArguments.isEmpty()) return rootName
    return typeArguments.joinToString(separator = ",", prefix = "$rootName<", postfix = ">") { argument ->
        argument.closedCallSiteAbiTypeName()
    }
}

private fun KotlinProjectionAbiTypeBinding.hasExplicitCallSiteAbiIdentity(): Boolean =
    mappedCallSiteType(this) != null || typeArguments.any { argument ->
        argument.hasExplicitCallSiteAbiIdentity()
    }

private fun mappedCallSiteAdapter(
    binding: KotlinProjectionAbiTypeBinding,
): KotlinProjectionMappedCallSiteAdapter? =
    mappedCallSiteType(binding)?.callSiteAdapter

private fun KotlinProjectionRenderer.runtimeProjectionRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    owner: ClassName,
    fromAbi: String,
    toAbi: String = "",
    createMarshaler: String = "",
    referenceAccess: WinRTProjectionCallSiteReferenceAccess =
        WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
): WinRTProjectionCallSiteRecipe =
    projectionRecipe(
        binding = binding,
        child = comReferenceRecipe(binding, referenceAccess),
        callables = WinRTProjectionCallSiteCallables(
            ownerFqName = owner.canonicalName,
            toAbi = toAbi,
            fromAbi = fromAbi,
            createMarshaler = createMarshaler,
            carrierProperty = if (createMarshaler.isEmpty()) "" else "abi",
        ),
    )

private fun KotlinProjectionRenderer.projectionRecipe(
    binding: KotlinProjectionAbiTypeBinding,
    child: WinRTProjectionCallSiteRecipe,
    callables: WinRTProjectionCallSiteCallables,
): WinRTProjectionCallSiteRecipe =
    WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
        abiCarriers = child.abiCarriers,
        valueCarrier = child.valueCarrier,
        nullable = isNullableProjectedType(binding),
        callables = callables,
        children = listOf(child),
        typeSignature = callSiteTypeSignature(binding),
    )

internal fun KotlinProjectionRenderer.buildProjectionOutputCodec(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
): KotlinProjectionCallSiteOutputCodec {
    require(recipe.children.single().kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE) {
        "Projection '${recipe.typeSignature}' must compose one COM-reference ABI child."
    }
    val returnType = resolveTypeName(binding.typeName)
    return KotlinProjectionCallSiteOutputCodec(
        abiTypeName = binding.callSiteAbiMetadataName(returnType),
        parameterType = RAW_ADDRESS_CLASS_NAME,
        returnType = returnType,
        body = renderProjectionOutputCodecBody(binding, recipe, returnType),
        consumesOwnedAbi = projectionOutputCodecConsumesOwnedAbi(binding),
    )
}

private fun KotlinProjectionRenderer.projectionOutputCodecConsumesOwnedAbi(
    binding: KotlinProjectionAbiTypeBinding,
): Boolean {
    val adapter = mappedCallSiteAdapter(binding) ?: return true
    if (adapter.usesClosedGenericHelper ||
        adapter.outputUsesAsyncExpression ||
        adapter.outputConsumesAbi ||
        adapter.outputFromAbiFunctionName != adapter.fromAbiFunctionName
    ) {
        return true
    }
    // Object projection is explicitly borrowed: WinRTObjectMarshaller resolves identity without
    // consuming the incoming reference. Other mapped wrappers retain ownership of their ABI view.
    return binding.kind != KotlinProjectionAbiValueKind.Object
}

private fun KotlinProjectionRenderer.renderProjectionOutputCodecBody(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    returnType: TypeName,
): CodeBlock {
    customObjectAbi(binding)?.let { customAbi ->
        val projectedClass = returnType.copy(nullable = false)
        return CodeBlock.builder()
            .add("%L", projectionNullGuard(binding))
            .add(
                "return %T.%L(__abi, %T(%S, %T(%S)), %T::class) ?: error(%S)\n",
                WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                customAbi.fromAbiFunctionName,
                WINRT_TYPE_HANDLE_CLASS_NAME,
                customAbi.typeHandleName,
                GUID_CLASS_NAME,
                customAbi.interfaceId.toString(),
                projectedClass,
                "WINRT_E_NULL_ABI_PROJECTED_RETURN",
            )
            .build()
    }
    mappedCallSiteAdapter(binding)?.let { adapter ->
        return renderMappedProjectionOutputCodec(binding, recipe, returnType, adapter)
    }
    return when (binding.kind) {
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            renderProjectedReferenceOutputCodec(binding, recipe, inspectable = false)
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            renderProjectedReferenceOutputCodec(binding, recipe, inspectable = true)
        KotlinProjectionAbiValueKind.Delegate -> {
            renderProjectionExpressionResult(
                binding,
                delegateFromOwnedAbiCode(binding, CodeBlock.of("__abi")),
            )
        }
        else -> error("ABI recipe '${recipe.typeSignature}' unexpectedly classified ${binding.kind} as a projection.")
    }
}

private fun KotlinProjectionRenderer.renderMappedProjectionOutputCodec(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    returnType: TypeName,
    adapter: KotlinProjectionMappedCallSiteAdapter,
): CodeBlock {
    if (adapter.outputUsesAsyncExpression) {
        val expression = asyncReferenceExpression(binding, CodeBlock.of("__abi"), hoistMetadata = true)
            ?: error("Closed async projection '${binding.typeName}' has no direct ABI expression.")
        return CodeBlock.builder()
            .add("%L", projectionNullGuard(binding))
            .add("return %L\n", expression)
            .build()
    }

    val callables = requireNotNull(recipe.callables)
    val owner = ClassName.bestGuess(callables.ownerFqName)
    if (adapter.usesClosedGenericHelper) {
        require(callables.fromAbi.isNotBlank() && callables.disposeAbi.isNotBlank()) {
            "Closed mapped projection '${binding.typeName}' requires consuming projection callables."
        }
        return CodeBlock.builder()
            .add("%L", projectionNullGuard(binding))
            .add("return try {\n")
            .indent()
            .add("%T.%L(__abi)\n", owner, callables.fromAbi)
            .unindent()
            .add("} finally {\n")
            .indent()
            .add("%T.%L(__abi)\n", owner, callables.disposeAbi)
            .unindent()
            .add("}\n")
            .build()
    }

    if (adapter.outputConsumesAbi) {
        val interfaceId = referenceInterfaceIdCode(binding, hoistMetadata = true)
            ?: error("Closed mapped projection '${binding.typeName}' has no parameterized IID.")
        return CodeBlock.builder()
            .add("return try {\n")
            .indent()
            .add("%T.%L(__abi, %L) as %T\n", owner, adapter.outputFromAbiFunctionName, interfaceId, returnType)
            .unindent()
            .add("} finally {\n")
            .indent()
            .add(
                "if (!%T.isNull(__abi)) %T(%T.toRawComPtr(__abi)).close()\n",
                PLATFORM_ABI_CLASS_NAME,
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
            .unindent()
            .add("}\n")
            .build()
    }

    require(binding.typeArguments.size == adapter.inputTypeArgumentAdapterCount) {
        "Mapped WinMD output '${binding.typeName}' expected ${adapter.inputTypeArgumentAdapterCount} closed adapters."
    }
    val arguments = binding.typeArguments.map { argument ->
        collectionReferenceAdapterCode(argument, hoistMetadata = true)
            ?: error("Mapped WinMD output '${binding.typeName}' has an unbound argument adapter.")
    }.toMutableList()
    if (binding.kind == KotlinProjectionAbiValueKind.MappedMapView ||
        binding.kind == KotlinProjectionAbiValueKind.MappedMap
    ) {
        arguments += collectionInterfaceIdCode(binding, hoistMetadata = true)
            ?: error("Mapped WinMD output '${binding.typeName}' has no collection IID.")
    }
    val expression = CodeBlock.builder()
        .add("%T.%L(__abi", owner, adapter.outputFromAbiFunctionName)
        .apply { arguments.forEach { argument -> add(", %L", argument) } }
        .add(")")
        .build()
    return renderProjectionExpressionResult(binding, expression)
}

private fun KotlinProjectionRenderer.renderProjectedReferenceOutputCodec(
    binding: KotlinProjectionAbiTypeBinding,
    recipe: WinRTProjectionCallSiteRecipe,
    inspectable: Boolean,
): CodeBlock {
    val callables = requireNotNull(recipe.callables)
    val owner = ClassName.bestGuess(callables.ownerFqName)
    return CodeBlock.builder()
        .add("%L", projectionNullGuard(binding))
        .add("val __reference = %T(%T.toRawComPtr(__abi))\n", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
        .apply {
            if (inspectable) {
                add("val __inspectable = try {\n")
                indent()
                add("__reference.asInspectable()\n")
                unindent()
                add("} finally {\n")
                indent()
                add("__reference.close()\n")
                unindent()
                add("}\n")
                add("return %T.%L(__inspectable)\n", owner, callables.fromAbi)
            } else {
                add("return %T.%L(__reference)\n", owner, callables.fromAbi)
            }
        }
        .build()
}

private fun KotlinProjectionRenderer.renderProjectionExpressionResult(
    binding: KotlinProjectionAbiTypeBinding,
    expression: CodeBlock,
): CodeBlock = if (isNullableProjectedType(binding)) {
    CodeBlock.of("return %L\n", expression)
} else {
    CodeBlock.of("return %L ?: error(%S)\n", expression, "WINRT_E_NULL_ABI_RETURN")
}

private fun KotlinProjectionRenderer.projectionNullGuard(binding: KotlinProjectionAbiTypeBinding): CodeBlock =
    if (isNullableProjectedType(binding)) {
        CodeBlock.of("if (%T.isNull(__abi)) return null\n", PLATFORM_ABI_CLASS_NAME)
    } else {
        CodeBlock.of("if (%T.isNull(__abi)) error(%S)\n", PLATFORM_ABI_CLASS_NAME, "WINRT_E_NULL_ABI_RETURN")
    }

internal fun KotlinProjectionRenderer.projectedClassName(binding: KotlinProjectionAbiTypeBinding): ClassName? {
    val resolved = runCatching { resolveTypeName(binding.typeName.substringBefore('?')) }.getOrNull()
        ?: runCatching { resolveTypeName(binding.resolvedTypeName.substringBefore('?')) }.getOrNull()
    return when (resolved) {
        is ClassName -> resolved
        is ParameterizedTypeName -> resolved.rawType
        else -> null
    }
}

private fun KotlinProjectionComArgumentKind.callSiteCarrier(): WinRTProjectionCallSiteAbiCarrier =
    when (this) {
        KotlinProjectionComArgumentKind.Pointer -> WinRTProjectionCallSiteAbiCarrier.ADDRESS
        KotlinProjectionComArgumentKind.Int8 -> WinRTProjectionCallSiteAbiCarrier.INT8
        KotlinProjectionComArgumentKind.Int16 -> WinRTProjectionCallSiteAbiCarrier.INT16
        KotlinProjectionComArgumentKind.Int32 -> WinRTProjectionCallSiteAbiCarrier.INT32
        KotlinProjectionComArgumentKind.Int64 -> WinRTProjectionCallSiteAbiCarrier.INT64
        KotlinProjectionComArgumentKind.Float -> WinRTProjectionCallSiteAbiCarrier.FLOAT32
        KotlinProjectionComArgumentKind.Double -> WinRTProjectionCallSiteAbiCarrier.FLOAT64
    }

private fun KotlinProjectionComArgumentKind.storageAlignment(): Int =
    when (this) {
        KotlinProjectionComArgumentKind.Int8 -> 1
        KotlinProjectionComArgumentKind.Int16 -> 2
        KotlinProjectionComArgumentKind.Int32,
        KotlinProjectionComArgumentKind.Float -> 4
        KotlinProjectionComArgumentKind.Pointer,
        KotlinProjectionComArgumentKind.Int64,
        KotlinProjectionComArgumentKind.Double -> 8
    }

private fun WinRTIntegralType.callSiteCarrier(): WinRTProjectionCallSiteAbiCarrier =
    when (this) {
        WinRTIntegralType.Int8,
        WinRTIntegralType.UInt8 -> WinRTProjectionCallSiteAbiCarrier.INT8
        WinRTIntegralType.Int16,
        WinRTIntegralType.UInt16 -> WinRTProjectionCallSiteAbiCarrier.INT16
        WinRTIntegralType.Int32,
        WinRTIntegralType.UInt32 -> WinRTProjectionCallSiteAbiCarrier.INT32
        WinRTIntegralType.Int64,
        WinRTIntegralType.UInt64 -> WinRTProjectionCallSiteAbiCarrier.INT64
    }

private fun WinRTIntegralType.valueKind(): KotlinProjectionAbiValueKind =
    when (this) {
        WinRTIntegralType.Int8 -> KotlinProjectionAbiValueKind.Int8
        WinRTIntegralType.UInt8 -> KotlinProjectionAbiValueKind.UInt8
        WinRTIntegralType.Int16 -> KotlinProjectionAbiValueKind.Int16
        WinRTIntegralType.UInt16 -> KotlinProjectionAbiValueKind.UInt16
        WinRTIntegralType.Int32 -> KotlinProjectionAbiValueKind.Int32
        WinRTIntegralType.UInt32 -> KotlinProjectionAbiValueKind.UInt32
        WinRTIntegralType.Int64 -> KotlinProjectionAbiValueKind.Int64
        WinRTIntegralType.UInt64 -> KotlinProjectionAbiValueKind.UInt64
    }

private fun WinRTIntegralType.callSiteValueTransform(): WinRTProjectionCallSiteValueTransform =
    when (this) {
        WinRTIntegralType.UInt8,
        WinRTIntegralType.UInt16,
        WinRTIntegralType.UInt32,
        WinRTIntegralType.UInt64 -> WinRTProjectionCallSiteValueTransform.UNSIGNED
        WinRTIntegralType.Int8,
        WinRTIntegralType.Int16,
        WinRTIntegralType.Int32,
        WinRTIntegralType.Int64 -> WinRTProjectionCallSiteValueTransform.IDENTITY
    }

private fun KotlinProjectionRenderer.callSiteTypeSignature(binding: KotlinProjectionAbiTypeBinding): String =
    binding.canonicalCallSiteTypeSignature(resolveTypeName(binding.typeName).toString())

private fun KotlinProjectionRenderer.isNullableProjectedType(binding: KotlinProjectionAbiTypeBinding): Boolean =
    resolveTypeName(binding.typeName).isNullable

internal fun KotlinProjectionAbiTypeBinding.canonicalCallSiteTypeSignature(
    projectedTypeName: String = typeName,
): String {
    if (kind in CANONICAL_RECIPE_SIGNATURE_KINDS) return kind.name
    return buildString {
        append(kind.name)
        append('(')
        append(projectedTypeName)
        append('|')
        append(resolvedTypeName)
        append('|')
        append(sourceTypeKind?.name ?: "-")
        append('|')
        append(abiSize ?: 0)
        append(':')
        append(abiAlignment ?: 0)
        append('|')
        append(interfaceId?.toString() ?: "-")
        append('|')
        append(enumUnderlyingType?.name ?: "-")
        typeArguments.forEach { argument ->
            append(';')
            append(argument.canonicalCallSiteTypeSignature())
        }
        if (structFieldBindings.isNotEmpty()) {
            append(";fields[")
            structFieldBindings.forEachIndexed { index, field ->
                if (index > 0) append(',')
                append(field.canonicalCallSiteTypeSignature())
                structFieldOffsets.getOrNull(index)?.let { offset -> append('@').append(offset) }
            }
            append(']')
        }
        append(')')
    }
}

private val CANONICAL_RECIPE_SIGNATURE_KINDS =
    setOf(
        KotlinProjectionAbiValueKind.String,
        KotlinProjectionAbiValueKind.Boolean,
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8,
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16,
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64,
        KotlinProjectionAbiValueKind.Float,
        KotlinProjectionAbiValueKind.Double,
        KotlinProjectionAbiValueKind.Char16,
        KotlinProjectionAbiValueKind.GuidValue,
        KotlinProjectionAbiValueKind.RawAddress,
        KotlinProjectionAbiValueKind.RawComPtr,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
    )
