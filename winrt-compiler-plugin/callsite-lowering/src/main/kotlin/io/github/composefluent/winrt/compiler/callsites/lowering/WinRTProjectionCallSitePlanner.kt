@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
@file:OptIn(
    org.jetbrains.kotlin.fir.symbols.SymbolInternals::class,
    org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class,
)

package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_ABI_CODEC_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_CALLER_OWNED_RESULT_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteMetadata
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

/** Builds private lowering recipes from typed IR and generated ABI metadata. */
internal class WinRTProjectionCallSitePlanner(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    private val projectedTypes: WinRTProjectedTypeCanonicalizer,
) {
    private val abiTypesByName = linkedMapOf<String, AbiTypeFacts>()
    private val codecsByAbiType = linkedMapOf<String, MutableList<CodecFacts>>()

    init {
        indexGeneratedAbiMetadata(moduleFragment, pluginContext)
    }

    fun plan(
        function: IrSimpleFunction,
        metadata: WinRTProjectionCallSiteMetadata,
    ): PlannedWinRTProjectionCallSite {
        val parameters = function.regularParameters()
        require(parameters.size >= 2) { "must declare a receiver and vtable slot" }
        val projectedParameters = parameters.drop(2)
        require(metadata.parameters.size == projectedParameters.size) {
            "metadata describes ${metadata.parameters.size} projected parameters, " +
                "but the typed declaration has ${projectedParameters.size}"
        }

        val slots = projectedParameters.zip(metadata.parameters).mapIndexed { index, (parameter, facts) ->
            val direction = facts.direction.loweringDirection()
            val projectedType = if (direction == WinRTProjectionCallSiteSlotDirection.OUT) {
                val holder = parameter.type as? IrSimpleType
                    ?: error("parameter $index must use WinRTOut<T>")
                require(holder.classFqName == WINRT_OUT_FQ_NAME && !holder.isNullable()) {
                    "parameter $index must use non-null WinRTOut<T>"
                }
                holder.arguments.singleOrNull()?.typeOrNull
                    ?: error("parameter $index must use a closed WinRTOut<T>")
            } else {
                parameter.type
            }
            val recipe = recipeFor(
                type = projectedType,
                abiType = facts.abiType,
                usage = direction.recipeUsage(),
            )
            WinRTProjectionCallSiteSlot(
                direction = direction,
                ownership = ownership(direction, recipe),
                recipe = recipe,
            )
        }.toMutableList()

        val callerOwnedConstructor = if (metadata.resultKind == WinRTProjectionCallSiteResultKind.CALLER_OWNED) {
            require(metadata.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.RETURN) {
                "a caller-owned result cannot be combined with raw HRESULT return"
            }
            require(!function.returnType.isNullable()) { "a caller-owned result must be non-null" }
            val resultClass = function.returnType.classOrNull
                ?: error("caller-owned result ${function.returnType} has no concrete result class")
            require(resultClass.owner.annotations.any(::isCallerOwnedResultAnnotation)) {
                "caller-owned result ${resultClass.owner.fqNameWhenAvailable} is not marked " +
                    "@$WINRT_CALLER_OWNED_RESULT_ANNOTATION_FQ_NAME"
            }
            require(resultClass.owner.typeParameters.isEmpty()) {
                "caller-owned result ${resultClass.owner.fqNameWhenAvailable} must expose a closed non-generic constructor"
            }
            val constructor = resultClass.owner.declarations
                .filterIsInstance<IrConstructor>()
                .singleOrNull(IrConstructor::isPrimary)
                ?: error("caller-owned result ${resultClass.owner.fqNameWhenAvailable} must have exactly one primary constructor")
            val outputParameters = constructor.regularParameters()
            require(outputParameters.isNotEmpty()) {
                "caller-owned result ${resultClass.owner.fqNameWhenAvailable} must expose at least one constructor output"
            }
            outputParameters.forEach { parameter ->
                val recipe = recipeFor(parameter.type, usage = RecipeUsage.OUTPUT)
                slots += WinRTProjectionCallSiteSlot(
                    direction = WinRTProjectionCallSiteSlotDirection.CALLER_OUT,
                    ownership = if (recipe.requiresNativeOwnership) {
                        WinRTProjectionCallSiteOwnership.OWNED
                    } else {
                        WinRTProjectionCallSiteOwnership.NONE
                    },
                    recipe = recipe,
                )
            }
            constructor.symbol
        } else null

        if (callerOwnedConstructor == null &&
            metadata.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.RETURN &&
            function.returnType.classFqName != KOTLIN_UNIT_FQ_NAME
        ) {
            val recipe = recipeFor(function.returnType, metadata.returnAbiType, RecipeUsage.OUTPUT)
            slots += WinRTProjectionCallSiteSlot(
                direction = if (recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
                    WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY
                } else {
                    WinRTProjectionCallSiteSlotDirection.RETURN
                },
                ownership = if (recipe.requiresNativeOwnership) {
                    WinRTProjectionCallSiteOwnership.OWNED
                } else {
                    WinRTProjectionCallSiteOwnership.NONE
                },
                recipe = recipe,
            )
        }

        return PlannedWinRTProjectionCallSite(
            descriptor = WinRTProjectionCallSiteDescriptor(
                hResultPolicy = metadata.hResultPolicy,
                slots = slots,
            ),
            callerOwnedConstructor = callerOwnedConstructor,
        )
    }

    /**
     * Reuses the outbound recipe graph in the inverse ABI direction for one inbound carrier.
     * Incoming ABI arguments are decoded like outbound results; Kotlin returns are encoded like
     * outbound inputs.
     */
    internal fun directInboundRecipe(
        type: IrType,
        abiType: String,
        usage: RecipeUsage,
    ): WinRTProjectionCallSiteRecipe? = runCatching {
        recipeFor(
            type = type,
            abiType = abiType,
            usage = usage,
        )
    }.getOrNull()?.takeIf { recipe ->
        recipe.kind in DIRECT_INBOUND_RECIPE_KINDS && recipe.abiCarriers.size == 1
    }

    private fun recipeFor(
        type: IrType,
        abiType: String = "",
        usage: RecipeUsage,
    ): WinRTProjectionCallSiteRecipe {
        val projectedName = projectedTypes.canonicalize(type)
            ?: error("cannot canonicalize closed projected type $type")
        val explicitAbiTypeName = abiType.takeIf(String::isNotEmpty)?.let { explicit ->
            projectedTypes.canonicalize(explicit)
                ?: error("cannot canonicalize explicit ABI type '$explicit'")
        }
        val abiTypeName = explicitAbiTypeName ?: projectedName
        val directDeclarationIdentity = abiTypeName.matchesProjectedDeclaration(type, projectedName)
        if (directDeclarationIdentity) {
            directEnumRecipe(type, projectedName)?.let { return it }
            directStructRecipe(type, projectedName)?.let { return it }
            if (usage == RecipeUsage.INPUT && !hasSpecializedInputCodec(abiTypeName)) {
                directProjectionInputRecipe(type, projectedName)?.let { return it }
            }
        }
        if (usage == RecipeUsage.INPUT && !hasSpecializedInputCodec(abiTypeName)) {
            directAsyncReferenceInputRecipe(type, abiTypeName, projectedName)?.let { return it }
        }
        if (usage == RecipeUsage.OUTPUT &&
            codecsByAbiType[abiTypeName].orEmpty().none { codec -> codec.role == AbiCodecRole.FROM_ABI }
        ) {
            if (directDeclarationIdentity) {
                directArrayRecipe(type, abiTypeName, projectedName)?.let { return it }
                directProjectionOutputRecipe(type, projectedName)?.let { return it }
            }
        }
        val facts = abiTypesByName[abiTypeName]
        if (facts == null) {
            primitiveRecipe(type, explicitAbiTypeName)?.let { return it }
            directArrayRecipe(type, abiTypeName, projectedName)?.let { return it }
        }
        val requiredFacts = facts
            ?: error("has no generated ABI type metadata for closed type $abiTypeName")
        val signature = requiredFacts.kind.typeSignature(projectedName)
        return when (requiredFacts.kind) {
            AbiTypeKind.ENUM -> enumRecipe(type, abiTypeName, projectedName, signature, requiredFacts)
            AbiTypeKind.STRUCT -> structRecipe(type, abiTypeName, projectedName, signature, requiredFacts)
            AbiTypeKind.COM_REFERENCE,
            AbiTypeKind.PROJECTION -> projectionRecipe(
                type,
                abiTypeName,
                projectedName,
                signature,
                requiredFacts,
                usage,
            )
            AbiTypeKind.ARRAY -> arrayRecipe(type, abiTypeName, projectedName, signature, requiredFacts, usage)
        }
    }

    private fun hasSpecializedInputCodec(abiTypeName: String): Boolean =
        codecsByAbiType[abiTypeName].orEmpty().any { codec ->
            codec.role == AbiCodecRole.TO_ABI || codec.role == AbiCodecRole.CREATE_MARSHALER
        }

    /**
     * Enum Metadata is already a generated, typed ABI codec. Resolve it from the exact IR enum
     * class instead of requiring one module helper function and one codec annotation per enum.
     * The generated Metadata owner remains the single source of the ABI conversion contract.
     */
    private fun directEnumRecipe(
        type: IrType,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        val metadata = metadataClass(type) ?: return null
        val projectedBaseName = projectedName.removeSuffix("?")
        val toAbi = metadata.metadataFunction("toAbi") { function ->
            function.regularParameters().singleOrNull()?.type?.let(projectedTypes::canonicalize) == projectedBaseName
        } ?: return null
        val carrier = primitiveRecipe(toAbi.returnType) ?: return null
        if (carrier.kind != WinRTProjectionCallSiteRecipeKind.VALUE) return null
        val fromAbi = metadata.metadataFunction("fromAbi") { function ->
            function.regularParameters().singleOrNull()?.type?.let(projectedTypes::canonicalize) ==
                projectedTypes.canonicalize(toAbi.returnType)
        } ?: return null
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.ENUM,
            abiCarriers = carrier.abiCarriers,
            valueCarrier = carrier.valueCarrier,
            nullable = type.isNullable(),
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = metadata.fqNameWhenAvailable?.asString()
                    ?: return null,
                toAbi = toAbi.name.asString(),
                fromAbi = fromAbi.name.asString(),
            ),
            children = listOf(carrier),
            typeSignature = AbiTypeKind.ENUM.typeSignature(projectedName),
        )
    }

    /**
     * Ordinary generated structs own both their WinMD-resolved layout facts and their typed ABI
     * operations. Resolve that declaration directly instead of indexing module forwarding codecs.
     */
    private fun directStructRecipe(
        type: IrType,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        val metadata = metadataClass(type) ?: return null
        val annotation = metadata.annotations.singleOrNull { candidate ->
            candidate.type.classFqName?.asString() == WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME
        } ?: return null
        val (metadataName, facts) = abiTypeFacts(annotation)
        val projectedBaseName = projectedName.removeSuffix("?")
        require(metadataName == projectedBaseName) {
            "struct $projectedBaseName Metadata declares ABI identity $metadataName"
        }
        require(facts.kind == AbiTypeKind.STRUCT) {
            "struct $projectedBaseName Metadata declares non-struct ABI kind ${facts.kind}"
        }
        val copyToAbi = metadata.metadataFunction("copyTo") { function ->
            val parameters = function.regularParameters()
            parameters.size == 2 &&
                projectedTypes.canonicalize(parameters[0].type)?.removeSuffix("?") == projectedBaseName &&
                parameters[1].type.classFqName == WINRT_RAW_ADDRESS_FQ_NAME
        } ?: return null
        val fromAbi = metadata.metadataFunction("fromAbi") { function ->
            function.regularParameters().singleOrNull()?.type?.classFqName == WINRT_RAW_ADDRESS_FQ_NAME &&
                projectedTypes.canonicalize(function.returnType)?.removeSuffix("?") == projectedBaseName
        } ?: return null
        val disposeAbi = metadata.metadataFunction("disposeAbi") { function ->
            function.regularParameters().singleOrNull()?.type?.classFqName == WINRT_RAW_ADDRESS_FQ_NAME
        } ?: return null
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.STRUCT,
            abiCarriers = listOf(facts.carrier.loweringCarrier()),
            valueCarrier = facts.carrier.loweringCarrier(),
            nullable = type.isNullable(),
            sizeBytes = facts.size,
            alignmentBytes = facts.alignment,
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = metadata.fqNameWhenAvailable?.asString() ?: return null,
                fromAbi = fromAbi.name.asString(),
                copyToAbi = copyToAbi.name.asString(),
                disposeAbi = disposeAbi.name.asString(),
            ),
            typeSignature = AbiTypeKind.STRUCT.typeSignature(projectedName),
        )
    }

    /**
     * Plain projected interfaces and runtime classes already expose the exact typed construction
     * entry point on their generated Metadata companion. Prefer it when no specialized output
     * codec was generated for mapped, closed-generic, collection, async, or custom ABI behavior.
     */
    private fun directProjectionOutputRecipe(
        type: IrType,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        val metadata = metadataClass(type) ?: return null
        val projectedBaseName = projectedName.removeSuffix("?")
        val projectedClassifier = type.classOrNull
        val wrapCandidates = metadata.metadataFunctions("wrap").filter { function ->
            (function.returnType.classOrNull == projectedClassifier ||
                projectedTypes.canonicalize(function.returnType)?.removeSuffix("?") == projectedBaseName) &&
                function.regularParameters().size == 1
        }
        val wrap = wrapCandidates.firstOrNull { function ->
            function.regularParameters().single().type.classFqName == WINRT_INSPECTABLE_REFERENCE_FQ_NAME
        } ?: wrapCandidates.firstOrNull { function ->
            function.regularParameters().single().type.classFqName == WINRT_IUNKNOWN_REFERENCE_FQ_NAME
        } ?: return null
        val referenceAccess = when (wrap.regularParameters().single().type.classFqName) {
            WINRT_INSPECTABLE_REFERENCE_FQ_NAME -> WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE
            WINRT_IUNKNOWN_REFERENCE_FQ_NAME -> WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE
            else -> return null
        }
        val owner = metadata.fqNameWhenAvailable?.asString() ?: return null
        val signature = AbiTypeKind.PROJECTION.typeSignature(projectedName)
        val storage = referenceRecipe(
            access = referenceAccess,
            signature = signature,
            nullable = type.isNullable(),
        )
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
            abiCarriers = storage.abiCarriers,
            valueCarrier = storage.valueCarrier,
            nullable = type.isNullable(),
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = owner,
                fromAbi = wrap.name.asString(),
                fromAbiConsumesOwnedReference = true,
                fromAbiSymbol = wrap.symbol,
            ),
            children = listOf(storage),
            typeSignature = signature,
        )
    }

    /**
     * A plain projection input is already an IWinRTObject at runtime. Reuse the same declaration
     * proof as the output path, then select its exact interface or runtime-class reference instead
     * of materializing one forwarding TO_ABI function for every closed projected type.
     */
    private fun directProjectionInputRecipe(
        type: IrType,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        val projection = directProjectionOutputRecipe(type, projectedName) ?: return null
        val outputStorage = projection.children.singleOrNull() ?: return null
        val (access, typeHandle) = when (outputStorage.referenceAccess) {
            WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE -> {
                val getter = metadataClass(type)?.metadataPropertyGetter("TYPE_HANDLE") ?: return null
                WinRTProjectionCallSiteReferenceAccess.PROJECTED_INTERFACE to getter.symbol
            }
            WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE ->
                WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT to null
            else -> return null
        }
        val storage = referenceRecipe(
            access = access,
            signature = projection.typeSignature,
            nullable = type.isNullable(),
            projectedTypeHandleSymbol = typeHandle,
        )
        return projection.copy(children = listOf(storage))
    }

    /**
     * Async references are mapped Kotlin wrappers around parameterized WinRT interfaces.  Most
     * generated call sites have a closed codec, but an authored inbound result can be the only
     * input use of a closed async shape, leaving only the generated FROM_ABI codec in the module
     * support shard.  Reuse the common runtime pointer adapter for that input instead of emitting
     * a target-specific codec or requiring a synthetic generated declaration.
     */
    private fun directAsyncReferenceInputRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        val typeName = type.classFqName?.asString() ?: return null
        val asyncAbiPrefix = when (typeName) {
            WINRT_ASYNC_ACTION_REFERENCE_FQ_NAME -> "Windows.Foundation.IAsyncAction"
            WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_FQ_NAME ->
                "Windows.Foundation.IAsyncActionWithProgress<"
            WINRT_ASYNC_OPERATION_REFERENCE_FQ_NAME -> "Windows.Foundation.IAsyncOperation<"
            WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_FQ_NAME ->
                "Windows.Foundation.IAsyncOperationWithProgress<"
            else -> return null
        }
        if (asyncAbiPrefix.endsWith('<')) {
            if (!abiTypeName.startsWith(asyncAbiPrefix) || !abiTypeName.endsWith('>')) return null
        } else if (abiTypeName != asyncAbiPrefix) {
            return null
        }
        val signature = AbiTypeKind.COM_REFERENCE.typeSignature(projectedName)
        val storage = referenceRecipe(
            access = WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
            signature = signature,
            nullable = type.isNullable(),
        )
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
            abiCarriers = storage.abiCarriers,
            valueCarrier = storage.valueCarrier,
            nullable = type.isNullable(),
            callables = WinRTProjectionCallSiteCallables(
                ownerFqName = WINRT_ASYNC_PROJECTION_INTEROP_FQ_NAME,
                toAbi = "toAbi",
            ),
            children = listOf(storage),
            typeSignature = signature,
        )
    }

    private fun metadataClass(type: IrType): IrClass? =
        type.classOrNull?.owner?.declarations
            ?.filterIsInstance<IrClass>()
            ?.singleOrNull { declaration -> declaration.name.asString() == "Metadata" }

    private fun String.matchesProjectedDeclaration(type: IrType, projectedName: String): Boolean {
        val abiBaseName = removeSuffix("?")
        if (abiBaseName == projectedName.removeSuffix("?")) return true
        val declaredTypeName = metadataClass(type)
            ?.metadataStringConstant("TYPE_NAME")
            ?.let(projectedTypes::canonicalize)
            ?.removeSuffix("?")
        return declaredTypeName == abiBaseName
    }

    private fun IrClass.metadataStringConstant(name: String): String? = declarations
        .filterIsInstance<IrProperty>()
        .singleOrNull { property -> property.name.asString() == name }
        ?.backingField
        ?.initializer
        ?.expression
        ?.let { expression -> (expression as? IrConst)?.value as? String }

    private fun IrClass.metadataFunction(
        name: String,
        predicate: (IrSimpleFunction) -> Boolean,
    ): IrSimpleFunction? = metadataFunctions(name)
        .filter(predicate)
        .singleOrNull()

    private fun IrClass.metadataFunctions(name: String): List<IrSimpleFunction> = declarations
        .filterIsInstance<IrSimpleFunction>()
        .filter { function -> function.name.asString() == name }

    private fun IrClass.metadataPropertyGetter(name: String): IrSimpleFunction? = declarations
        .filterIsInstance<IrProperty>()
        .singleOrNull { property -> property.name.asString() == name }
        ?.getter
        ?.takeIf { getter -> getter.regularParameters().isEmpty() }

    private fun directArrayRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
    ): WinRTProjectionCallSiteRecipe? {
        if (type.classFqName != KOTLIN_ARRAY_FQ_NAME) return null
        val simple = type as? IrSimpleType ?: return null
        val elementType = simple.arguments.singleOrNull()?.typeOrNull ?: return null
        val elementAbiTypeName = abiTypeName.closedTypeArgumentIdentities()?.singleOrNull() ?: return null
        val elementRecipe = directArrayElementRecipe(elementType, elementAbiTypeName) ?: return null
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.ARRAY,
            abiCarriers = listOf(
                WinRTProjectionCallSiteAbiCarrier.INT32,
                WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            ),
            valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            nullable = type.isNullable(),
            children = listOf(elementRecipe),
            typeSignature = AbiTypeKind.ARRAY.typeSignature(projectedName),
        )
    }

    private fun directArrayElementRecipe(
        type: IrType,
        abiTypeName: String,
    ): WinRTProjectionCallSiteRecipe? {
        primitiveRecipe(type)?.let { return it }
        val projectedName = projectedTypes.canonicalize(type) ?: return null
        val directDeclarationIdentity = abiTypeName.matchesProjectedDeclaration(type, projectedName)
        if (directDeclarationIdentity) {
            directEnumRecipe(type, projectedName)?.let { return it }
            directStructRecipe(type, projectedName)?.let { return it }
        }
        if (directDeclarationIdentity) {
            // The outer generated array codec owns input marshaling or output transfer and cleanup.
            // The planner only needs the element's storage shape, which is recoverable from the
            // exact Metadata.wrap declaration without a per-projection codec or ABI metadata holder.
            val projection = directProjectionOutputRecipe(type, projectedName) ?: return null
            return projection.children.singleOrNull()
                ?.takeIf { child -> child.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE }
        }
        return null
    }

    private fun primitiveRecipe(
        type: IrType,
        explicitAbiTypeName: String? = null,
    ): WinRTProjectionCallSiteRecipe? {
        val fqName = if (explicitAbiTypeName == null) {
            type.classFqName
        } else {
            if ('<' in explicitAbiTypeName) return null
            FqName(explicitAbiTypeName.removeSuffix("?"))
        }
        val nullable = type.isNullable()
        return when (fqName) {
            KOTLIN_BOOLEAN_FQ_NAME -> valueRecipe(
                carrier = WinRTProjectionCallSiteAbiCarrier.INT8,
                transform = WinRTProjectionCallSiteValueTransform.BOOLEAN,
                signature = "Boolean",
            )
            KOTLIN_BYTE_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.INT8, signature = "Int8")
            KOTLIN_UBYTE_FQ_NAME -> valueRecipe(
                WinRTProjectionCallSiteAbiCarrier.INT8,
                WinRTProjectionCallSiteValueTransform.UNSIGNED,
                "UInt8",
            )
            KOTLIN_SHORT_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.INT16, signature = "Int16")
            KOTLIN_USHORT_FQ_NAME -> valueRecipe(
                WinRTProjectionCallSiteAbiCarrier.INT16,
                WinRTProjectionCallSiteValueTransform.UNSIGNED,
                "UInt16",
            )
            KOTLIN_INT_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.INT32, signature = "Int32")
            KOTLIN_UINT_FQ_NAME -> valueRecipe(
                WinRTProjectionCallSiteAbiCarrier.INT32,
                WinRTProjectionCallSiteValueTransform.UNSIGNED,
                "UInt32",
            )
            KOTLIN_LONG_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.INT64, signature = "Int64")
            KOTLIN_ULONG_FQ_NAME -> valueRecipe(
                WinRTProjectionCallSiteAbiCarrier.INT64,
                WinRTProjectionCallSiteValueTransform.UNSIGNED,
                "UInt64",
            )
            KOTLIN_FLOAT_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.FLOAT32, signature = "Float")
            KOTLIN_DOUBLE_FQ_NAME -> valueRecipe(WinRTProjectionCallSiteAbiCarrier.FLOAT64, signature = "Double")
            KOTLIN_CHAR_FQ_NAME -> valueRecipe(
                WinRTProjectionCallSiteAbiCarrier.INT16,
                WinRTProjectionCallSiteValueTransform.CHAR16,
                "Char16",
            )
            KOTLIN_STRING_FQ_NAME -> WinRTProjectionCallSiteRecipe(
                kind = WinRTProjectionCallSiteRecipeKind.HSTRING,
                abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
                valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                nullable = nullable,
                typeSignature = "String",
            )
            WINRT_GUID_FQ_NAME -> WinRTProjectionCallSiteRecipe(
                kind = WinRTProjectionCallSiteRecipeKind.GUID,
                abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
                valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                nullable = nullable,
                sizeBytes = 16,
                alignmentBytes = 8,
                typeSignature = "GuidValue",
            )
            WINRT_RAW_ADDRESS_FQ_NAME -> referenceRecipe(
                WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS,
                "RawAddress",
                nullable,
            )
            WINRT_RAW_COM_PTR_FQ_NAME -> referenceRecipe(
                WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR,
                "RawComPtr",
                nullable,
            )
            WINRT_IUNKNOWN_REFERENCE_FQ_NAME -> referenceRecipe(
                WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
                "UnknownReference",
                nullable,
            )
            WINRT_INSPECTABLE_REFERENCE_FQ_NAME -> referenceRecipe(
                WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE,
                "InspectableReference",
                nullable,
            )
            else -> null
        }
    }

    private fun enumRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
        signature: String,
        facts: AbiTypeFacts,
    ): WinRTProjectionCallSiteRecipe {
        val toAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.TO_ABI) { codec ->
            codec.parameterTypes == listOf(projectedName)
        }
        val fromAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.FROM_ABI) { codec ->
            codec.returnType == projectedName && codec.parameterTypes.size == 1
        }
        val carrierType = toAbi?.returnType ?: fromAbi?.parameterTypes?.singleOrNull()
            ?: error("enum $projectedName has no typed to/from-ABI codec")
        val carrierRecipe = primitiveRecipe(
            toAbi?.returnIrType ?: fromAbi?.parameterIrTypes?.singleOrNull()
                ?: error("enum $projectedName has no closed ABI carrier type"),
        ) ?: error("enum $projectedName codec uses non-primitive ABI carrier $carrierType")
        require(carrierRecipe.valueCarrier == facts.carrier.loweringCarrier()) {
            "enum $projectedName metadata carrier ${facts.carrier} disagrees with codec carrier $carrierType"
        }
        require(carrierRecipe.valueTransform == facts.transform.loweringTransform()) {
            "enum $projectedName metadata transform ${facts.transform} disagrees with codec carrier $carrierType"
        }
        val callables = callables(
            projectedName,
            toAbi = toAbi,
            fromAbi = fromAbi,
        )
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.ENUM,
            abiCarriers = carrierRecipe.abiCarriers,
            valueCarrier = carrierRecipe.valueCarrier,
            nullable = type.isNullable(),
            callables = callables,
            children = listOf(carrierRecipe),
            typeSignature = signature,
        )
    }

    private fun structRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
        signature: String,
        facts: AbiTypeFacts,
    ): WinRTProjectionCallSiteRecipe {
        val carrier = facts.carrier.loweringCarrier()
        val carrierType = facts.carrier.kotlinTypeName()
        val toAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.TO_ABI) { codec ->
            codec.parameterTypes == listOf(projectedName) && codec.returnType == carrierType
        }
        val copyToAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.COPY_TO_ABI) { codec ->
            codec.parameterTypes == listOf(projectedName, WINRT_RAW_ADDRESS_FQ_NAME.asString())
        }
        val fromAbiCarrier = exactCodec(abiTypeName, projectedName, AbiCodecRole.FROM_ABI) { codec ->
            codec.parameterTypes == listOf(carrierType) && codec.returnType == projectedName
        }
        val fromAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.FROM_ABI) { codec ->
            codec.parameterTypes == listOf(WINRT_RAW_ADDRESS_FQ_NAME.asString()) && codec.returnType == projectedName
        }
        val disposeAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.DISPOSE_ABI) { codec ->
            codec.parameterTypes == listOf(WINRT_RAW_ADDRESS_FQ_NAME.asString())
        }
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.STRUCT,
            abiCarriers = listOf(carrier),
            valueCarrier = carrier,
            nullable = type.isNullable(),
            sizeBytes = facts.size,
            alignmentBytes = facts.alignment,
            callables = callables(
                projectedName = projectedName,
                toAbi = toAbi,
                fromAbi = fromAbi,
                copyToAbi = copyToAbi,
                disposeAbi = disposeAbi,
                fromAbiCarrier = fromAbiCarrier,
            ),
            typeSignature = signature,
        )
    }

    private fun projectionRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
        signature: String,
        facts: AbiTypeFacts,
        usage: RecipeUsage,
    ): WinRTProjectionCallSiteRecipe {
        val toAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.TO_ABI) { codec ->
            codec.parameterTypes == listOf(projectedName)
        }
        val fromAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.FROM_ABI) { codec ->
            codec.returnType == projectedName
        }
        val create = exactCodec(abiTypeName, projectedName, AbiCodecRole.CREATE_MARSHALER) { codec ->
            codec.parameterTypes == listOf(projectedName)
        }
        val copyFrom = exactCodec(abiTypeName, projectedName, AbiCodecRole.COPY_FROM_ABI) { codec ->
            codec.parameterTypes.size == 2 && codec.parameterTypes[1] == projectedName
        }
        require(usage == RecipeUsage.OUTPUT && fromAbi != null ||
            usage != RecipeUsage.OUTPUT && (toAbi != null || create != null)
        ) {
            "$projectedName has no typed ${usage.name.lowercase()} projection codec"
        }
        val (carrierProperty, extraCarrierProperties) = create?.factoryCarrierProperties().orEmptyPair()
        val storage = WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
            abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
            valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            referenceAccess = if (
                usage == RecipeUsage.INPUT && facts.reference == AbiReferenceKind.INSPECTABLE
            ) {
                // The WinMD ABI fact is an inspectable carrier, not a projected wrapper.  Keep the
                // owned factory for cold/unsupported values, but let lowering borrow a cached
                // managed CCW pointer for the synchronous object-input case.
                WinRTProjectionCallSiteReferenceAccess.MANAGED_INSPECTABLE
            } else {
                WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT
            },
            nullable = type.isNullable(),
            typeSignature = signature,
        )
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
            abiCarriers = storage.abiCarriers,
            valueCarrier = storage.valueCarrier,
            nullable = type.isNullable(),
            callables = callables(
                projectedName = projectedName,
                toAbi = toAbi,
                fromAbi = fromAbi,
                createMarshaler = create,
                copyFromAbi = copyFrom,
                carrierProperty = carrierProperty,
                extraCarrierProperties = extraCarrierProperties,
            ),
            children = listOf(storage),
            typeSignature = signature,
        )
    }

    private fun arrayRecipe(
        type: IrType,
        abiTypeName: String,
        projectedName: String,
        signature: String,
        facts: AbiTypeFacts,
        usage: RecipeUsage,
    ): WinRTProjectionCallSiteRecipe {
        require(facts.carrier == AbiCarrier.ADDRESS) { "array $projectedName must use address storage" }
        val simple = type as? IrSimpleType ?: error("array $projectedName has no closed IR type")
        val elementType = simple.arguments.singleOrNull()?.typeOrNull
            ?: error("array $projectedName has no closed element type")
        val elementAbiTypeName = abiTypeName.closedTypeArgumentIdentities()?.singleOrNull()
            ?: error("array ABI identity $abiTypeName has no single closed element identity")
        val elementRecipe = arrayElementRecipe(elementType, elementAbiTypeName)
        val storage = WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.ARRAY,
            abiCarriers = listOf(
                WinRTProjectionCallSiteAbiCarrier.INT32,
                WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            ),
            valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            nullable = type.isNullable(),
            children = listOf(elementRecipe),
            typeSignature = signature,
        )
        if (usage == RecipeUsage.OUTPUT) {
            val fromAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.FROM_ABI) { codec ->
                codec.returnType == projectedName && codec.parameterTypes ==
                    listOf(WINRT_RAW_ADDRESS_FQ_NAME.asString(), WINRT_RAW_ADDRESS_FQ_NAME.asString())
            } ?: error("array $projectedName has no typed output codec")
            val disposeAbi = exactCodec(abiTypeName, projectedName, AbiCodecRole.DISPOSE_ABI) { codec ->
                codec.parameterTypes ==
                    listOf(WINRT_RAW_ADDRESS_FQ_NAME.asString(), WINRT_RAW_ADDRESS_FQ_NAME.asString())
            } ?: error("array $projectedName has no typed disposal codec")
            return storage.copy(
                callables = callables(projectedName, fromAbi = fromAbi, disposeAbi = disposeAbi),
            )
        }
        val create = exactCodec(abiTypeName, projectedName, AbiCodecRole.CREATE_MARSHALER) { codec ->
            codec.parameterTypes == listOf(projectedName)
        } ?: error("array $projectedName has no typed input marshaler")
        val copyFrom = exactCodec(abiTypeName, projectedName, AbiCodecRole.COPY_FROM_ABI) { codec ->
            codec.parameterTypes.size == 2 && codec.parameterTypes[1] == projectedName
        }
        val (carrierProperty, extraCarrierProperties) = create.factoryCarrierProperties()
        return WinRTProjectionCallSiteRecipe(
            kind = WinRTProjectionCallSiteRecipeKind.PROJECTION,
            abiCarriers = storage.abiCarriers,
            valueCarrier = storage.valueCarrier,
            nullable = type.isNullable(),
            callables = callables(
                projectedName = projectedName,
                createMarshaler = create,
                copyFromAbi = copyFrom,
                carrierProperty = carrierProperty,
                extraCarrierProperties = extraCarrierProperties,
            ),
            children = listOf(storage),
            typeSignature = signature,
        )
    }

    private fun arrayElementRecipe(
        type: IrType,
        abiTypeName: String,
    ): WinRTProjectionCallSiteRecipe {
        directArrayElementRecipe(type, abiTypeName)?.let { return it }
        val projectedName = projectedTypes.canonicalize(type)
            ?: error("cannot canonicalize array element type $type")
        val facts = abiTypesByName[abiTypeName]
            ?: error("array element $abiTypeName has no generated ABI type metadata for $projectedName")
        val signature = facts.kind.typeSignature(projectedName)
        return when (facts.kind) {
            AbiTypeKind.ENUM -> valueRecipe(
                carrier = facts.carrier.loweringCarrier(),
                transform = facts.transform.loweringTransform(),
                signature = facts.carrier.primitiveSignature(facts.transform),
            )
            AbiTypeKind.STRUCT -> WinRTProjectionCallSiteRecipe(
                kind = WinRTProjectionCallSiteRecipeKind.STRUCT,
                abiCarriers = listOf(facts.carrier.loweringCarrier()),
                valueCarrier = facts.carrier.loweringCarrier(),
                nullable = type.isNullable(),
                sizeBytes = facts.size,
                alignmentBytes = facts.alignment,
                typeSignature = signature,
            )
            AbiTypeKind.COM_REFERENCE,
            AbiTypeKind.PROJECTION -> WinRTProjectionCallSiteRecipe(
                kind = WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
                abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
                valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                referenceAccess = WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
                nullable = type.isNullable(),
                typeSignature = signature,
            )
            AbiTypeKind.ARRAY -> error("WinRT arrays cannot contain nested ABI arrays")
        }
    }

    private fun valueRecipe(
        carrier: WinRTProjectionCallSiteAbiCarrier,
        transform: WinRTProjectionCallSiteValueTransform = WinRTProjectionCallSiteValueTransform.IDENTITY,
        signature: String,
    ): WinRTProjectionCallSiteRecipe = WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.VALUE,
        abiCarriers = listOf(carrier),
        valueCarrier = carrier,
        valueTransform = transform,
        typeSignature = signature,
    )

    private fun referenceRecipe(
        access: WinRTProjectionCallSiteReferenceAccess,
        signature: String,
        nullable: Boolean,
        projectedTypeHandleSymbol: org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol? = null,
    ): WinRTProjectionCallSiteRecipe = WinRTProjectionCallSiteRecipe(
        kind = WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        abiCarriers = listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS),
        valueCarrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        referenceAccess = access,
        nullable = nullable,
        projectedTypeHandleSymbol = projectedTypeHandleSymbol,
        typeSignature = signature,
    )

    private fun callables(
        projectedName: String,
        toAbi: CodecFacts? = null,
        fromAbi: CodecFacts? = null,
        copyToAbi: CodecFacts? = null,
        disposeAbi: CodecFacts? = null,
        fromAbiCarrier: CodecFacts? = null,
        createMarshaler: CodecFacts? = null,
        copyFromAbi: CodecFacts? = null,
        carrierProperty: String = "",
        extraCarrierProperties: List<String> = emptyList(),
    ): WinRTProjectionCallSiteCallables {
        val codecs = listOfNotNull(
            toAbi,
            fromAbi,
            copyToAbi,
            disposeAbi,
            fromAbiCarrier,
            createMarshaler,
            copyFromAbi,
        )
        require(codecs.isNotEmpty()) { "$projectedName has no typed ABI codecs" }
        val owners = codecs.map(CodecFacts::ownerFqName).distinct()
        require(owners.size == 1) { "$projectedName typed ABI codecs are split across owners $owners" }
        return WinRTProjectionCallSiteCallables(
            ownerFqName = owners.single(),
            toAbi = toAbi?.functionName.orEmpty(),
            fromAbi = fromAbi?.functionName.orEmpty(),
            copyToAbi = copyToAbi?.functionName.orEmpty(),
            copyFromAbi = copyFromAbi?.functionName.orEmpty(),
            disposeAbi = disposeAbi?.functionName.orEmpty(),
            fromAbiCarrier = fromAbiCarrier?.functionName.orEmpty(),
            createMarshaler = createMarshaler?.functionName.orEmpty(),
            carrierProperty = carrierProperty,
            extraCarrierProperties = extraCarrierProperties,
            fromAbiConsumesOwnedReference = fromAbi?.consumesOwnedAbi == true ||
                fromAbi?.consumesOwnedComReference() == true,
        )
    }

    private fun CodecFacts.consumesOwnedComReference(): Boolean {
        val parameterType = parameterIrTypes.singleOrNull()?.classFqName
        return parameterType == WINRT_IUNKNOWN_REFERENCE_FQ_NAME ||
            parameterType == WINRT_INSPECTABLE_REFERENCE_FQ_NAME
    }

    private fun exactCodec(
        abiTypeName: String,
        projectedName: String,
        role: AbiCodecRole,
        predicate: (CodecFacts) -> Boolean,
    ): CodecFacts? {
        val matches = codecsByAbiType[abiTypeName].orEmpty().filter { codec ->
            codec.role == role && predicate(codec)
        }
        require(matches.size <= 1) {
            "$abiTypeName projected as $projectedName has ambiguous $role codecs " +
                matches.map(CodecFacts::functionName)
        }
        return matches.singleOrNull()
    }

    private fun CodecFacts.factoryCarrierProperties(): Pair<String, List<String>> =
        when (returnType.removeSuffix("?")) {
            WINRT_ABI_ARRAY_FQ_NAME.asString() -> "length" to listOf("data")
            WINRT_COM_OBJECT_REFERENCE_FQ_NAME.asString() -> "pointer" to emptyList()
            else -> "abi" to emptyList()
        }

    private fun Pair<String, List<String>>?.orEmptyPair(): Pair<String, List<String>> =
        this ?: ("" to emptyList())

    private fun ownership(
        direction: WinRTProjectionCallSiteSlotDirection,
        recipe: WinRTProjectionCallSiteRecipe,
    ): WinRTProjectionCallSiteOwnership = when {
        !recipe.requiresNativeOwnership -> WinRTProjectionCallSiteOwnership.NONE
        direction == WinRTProjectionCallSiteSlotDirection.IN ||
            direction == WinRTProjectionCallSiteSlotDirection.PASS_ARRAY -> WinRTProjectionCallSiteOwnership.BORROWED
        else -> WinRTProjectionCallSiteOwnership.OWNED
    }

    private fun indexGeneratedAbiMetadata(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val visitor = generatedAbiMetadataVisitor()
        moduleFragment.acceptChildrenVoid(visitor)
        indexCompiledPackageSiblings(moduleFragment, pluginContext, visitor)
    }

    /**
     * Incremental JVM compilation may provide only dirty platform files in the IR fragment while
     * their common-source-set ABI helpers remain as compiled friend-module declarations. Recover
     * those annotated siblings through the frontend symbol index, then feed their real IR symbols
     * into the same registry used for a full compilation.
     */
    private fun indexCompiledPackageSiblings(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        if (pluginContext.afterK2) {
            indexFirPackageSiblings(moduleFragment, pluginContext, visitor)
        } else {
            indexDescriptorPackageSiblings(moduleFragment, pluginContext, visitor)
        }
    }

    private fun indexFirPackageSiblings(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        val module = pluginContext.moduleDescriptor as? FirModuleDescriptor
            ?: error("K2 WinRT lowering requires a FIR module descriptor")
        val moduleData = module.moduleData
        val symbolProvider = module.session.symbolProvider
        val symbolNames = symbolProvider.symbolNamesProvider
        moduleFragment.files
            .groupBy { file -> file.packageFqName }
            .forEach { (packageName, lookupFiles) ->
                symbolNames.getTopLevelClassifierNamesInPackage(packageName)
                    .orEmpty()
                    .asSequence()
                    .map { name -> ClassId(packageName, name) }
                    .filter { classId ->
                        val symbol = symbolProvider.getClassLikeSymbolByClassId(classId)
                            as? FirRegularClassSymbol
                        symbol != null &&
                            moduleData.canSeeInternalsOf(symbol.moduleData) &&
                            symbol.containsGeneratedAbiMetadata(module.session)
                    }
                    .forEach { classId ->
                        indexCompiledClass(classId, lookupFiles, pluginContext, visitor)
                    }

                symbolNames.getTopLevelCallableNamesInPackage(packageName)
                    .orEmpty()
                    .asSequence()
                    .map { name -> CallableId(packageName, name) }
                    .filter { callableId ->
                        symbolProvider.getTopLevelFunctionSymbols(packageName, callableId.callableName)
                            .any { symbol ->
                                moduleData.canSeeInternalsOf(symbol.moduleData) &&
                                    symbol.hasGeneratedAbiMetadata()
                            }
                    }
                    .forEach { callableId ->
                        indexCompiledFunctions(callableId, lookupFiles, pluginContext, visitor)
                    }
            }
    }

    private fun indexDescriptorPackageSiblings(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        val module = pluginContext.moduleDescriptor
        val metadataModules = linkedSetOf(module).apply {
            addAll(module.allExpectedByModules)
            module.allDependencyModules.filterTo(this) { dependency ->
                dependency === module || module.shouldSeeInternalsOf(dependency)
            }
        }
        moduleFragment.files
            .groupBy { file -> file.packageFqName }
            .forEach { (packageName, lookupFiles) ->
                module.getPackage(packageName)
                    .memberScope
                    .getContributedDescriptors(DescriptorKindFilter.ALL) { true }
                    .asSequence()
                    .filter { descriptor ->
                        DescriptorUtils.getContainingModuleOrNull(descriptor) in metadataModules
                    }
                    .filter { descriptor -> descriptor.containsGeneratedAbiMetadata() }
                    .forEach { descriptor ->
                        indexCompiledDescriptor(descriptor, lookupFiles, pluginContext, visitor)
                    }
            }
    }

    private fun indexCompiledClass(
        classId: ClassId,
        lookupFiles: List<IrFile>,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        var declaration: IrClass? = null
        lookupFiles.forEach { file ->
            pluginContext.finderForSource(file).findClass(classId)?.owner?.let { resolved ->
                if (declaration == null) declaration = resolved
            }
        }
        declaration?.let(visitor::visitClass)
    }

    private fun indexCompiledFunctions(
        callableId: CallableId,
        lookupFiles: List<IrFile>,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        val declarations = linkedSetOf<IrSimpleFunction>()
        lookupFiles.forEach { file ->
            pluginContext.finderForSource(file)
                .findFunctions(callableId)
                .mapTo(declarations) { symbol -> symbol.owner }
        }
        declarations
            .filter { declaration -> declaration.hasGeneratedAbiMetadata() }
            .forEach(visitor::visitSimpleFunction)
    }

    private fun indexCompiledDescriptor(
        descriptor: DeclarationDescriptor,
        lookupFiles: List<IrFile>,
        pluginContext: IrPluginContext,
        visitor: IrVisitorVoid,
    ) {
        when (descriptor) {
            is ClassDescriptor -> {
                val classId = runCatching { DescriptorUtils.getClassIdForNonLocalClass(descriptor) }.getOrNull()
                    ?: return
                val declaration = pluginContext.referenceClass(classId)?.owner ?: return
                lookupFiles.forEach { file -> pluginContext.recordLookup(declaration, file) }
                visitor.visitClass(declaration)
            }

            is SimpleFunctionDescriptor -> {
                val packageName = (descriptor.containingDeclaration as? PackageFragmentDescriptor)?.fqName
                    ?: return
                pluginContext.referenceFunctions(CallableId(packageName, descriptor.name))
                    .map { symbol -> symbol.owner }
                    .filter { declaration -> declaration.hasGeneratedAbiMetadata() }
                    .forEach { declaration ->
                        lookupFiles.forEach { file -> pluginContext.recordLookup(declaration, file) }
                        visitor.visitSimpleFunction(declaration)
                    }
            }
        }
    }

    private fun generatedAbiMetadataVisitor(): IrVisitorVoid =
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                declaration.annotations.singleOrNull { annotation ->
                    annotation.type.classFqName?.asString() == WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME
                }?.let(::indexAbiType)
                super.visitClass(declaration)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                declaration.annotations.singleOrNull { annotation ->
                    annotation.type.classFqName?.asString() == WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME
                }?.let(::indexAbiType)
                declaration.annotations.singleOrNull { annotation ->
                    annotation.type.classFqName?.asString() == WINRT_PROJECTION_ABI_CODEC_ANNOTATION_FQ_NAME
                }?.let { annotation -> indexCodec(declaration, annotation) }
                super.visitSimpleFunction(declaration)
            }
        }

    private fun DeclarationDescriptor.containsGeneratedAbiMetadata(): Boolean {
        if (annotations.hasAnnotation(WINRT_PROJECTION_ABI_TYPE_FQ_NAME) ||
            annotations.hasAnnotation(WINRT_PROJECTION_ABI_CODEC_FQ_NAME)
        ) {
            return true
        }
        if (this !is ClassDescriptor) return false
        return unsubstitutedMemberScope
            .getContributedDescriptors(DescriptorKindFilter.ALL) { true }
            .asSequence()
            .filter { member -> member.containingDeclaration == this }
            .any { member -> member.containsGeneratedAbiMetadata() }
    }

    private fun FirRegularClassSymbol.containsGeneratedAbiMetadata(session: FirSession): Boolean {
        if (hasGeneratedAbiMetadata()) return true
        var containsMetadata = false
        processAllDeclarations(session) { declaration ->
            if (!containsMetadata) {
                containsMetadata = declaration.hasGeneratedAbiMetadata() ||
                    (declaration as? FirRegularClassSymbol)
                        ?.containsGeneratedAbiMetadata(session) == true
            }
        }
        return containsMetadata
    }

    private fun FirBasedSymbol<*>.hasGeneratedAbiMetadata(): Boolean =
        resolvedAnnotationClassIds.any { annotationClassId ->
            annotationClassId == WINRT_PROJECTION_ABI_TYPE_CLASS_ID ||
                annotationClassId == WINRT_PROJECTION_ABI_CODEC_CLASS_ID
        }

    private fun IrSimpleFunction.hasGeneratedAbiMetadata(): Boolean =
        annotations.any { annotation ->
            annotation.type.classFqName?.let { name ->
                name == WINRT_PROJECTION_ABI_TYPE_FQ_NAME || name == WINRT_PROJECTION_ABI_CODEC_FQ_NAME
            } == true
        }


    private fun abiTypeFacts(annotation: IrFunctionAccessExpression): Pair<String, AbiTypeFacts> {
        val rawName = annotation.stringArgument("name")
        require(rawName.isNotEmpty()) { "generated ABI type metadata requires a closed type name" }
        val name = projectedTypes.canonicalize(rawName)
            ?: error("generated ABI type metadata has malformed name '$rawName'")
        val facts = AbiTypeFacts(
            kind = annotation.enumArgument("kind", AbiTypeKind.PROJECTION),
            carrier = annotation.enumArgument("carrier", AbiCarrier.ADDRESS),
            transform = annotation.enumArgument("transform", AbiValueTransform.IDENTITY),
            size = annotation.intArgument("size"),
            alignment = annotation.intArgument("alignment"),
            reference = annotation.enumArgument("reference", AbiReferenceKind.NONE),
        )
        return name to facts
    }

    private fun indexAbiType(annotation: IrFunctionAccessExpression) {
        val (name, facts) = abiTypeFacts(annotation)
        val existing = abiTypesByName.putIfAbsent(name, facts)
        require(existing == null || existing == facts) { "conflicting generated ABI type metadata for $name" }
    }

    private fun indexCodec(
        function: IrSimpleFunction,
        annotation: IrFunctionAccessExpression,
    ) {
        val rawName = annotation.stringArgument("type")
        require(rawName.isNotEmpty()) { "generated ABI codec ${function.name} requires a closed type name" }
        val abiTypeName = projectedTypes.canonicalize(rawName)
            ?: error("generated ABI codec ${function.name} has malformed type '$rawName'")
        val fqName = function.fqNameWhenAvailable
            ?: error("generated ABI codec ${function.name} has no stable FQ name")
        val parameters = function.regularParameters()
        val facts = CodecFacts(
            role = annotation.enumArgument("role", AbiCodecRole.TO_ABI),
            consumesOwnedAbi = annotation.booleanArgument("consumesOwnedAbi"),
            ownerFqName = fqName.parent().asString(),
            functionName = function.name.asString(),
            parameterTypes = parameters.map { parameter ->
                projectedTypes.canonicalize(parameter.type)
                    ?: error("generated ABI codec $fqName has an open parameter type")
            },
            parameterIrTypes = parameters.map { parameter -> parameter.type },
            returnType = projectedTypes.canonicalize(function.returnType)
                ?: error("generated ABI codec $fqName has an open return type"),
            returnIrType = function.returnType,
        )
        val codecs = codecsByAbiType.getOrPut(abiTypeName, ::mutableListOf)
        val existing = codecs.singleOrNull { codec -> codec.hasSameDeclarationAs(facts) }
        require(existing == null || existing.hasSameContractAs(facts)) {
            "conflicting generated ABI codec metadata for $fqName"
        }
        if (existing == null) codecs += facts
    }
}

internal data class PlannedWinRTProjectionCallSite(
    val descriptor: WinRTProjectionCallSiteDescriptor,
    val callerOwnedConstructor: org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol? = null,
)

private fun isCallerOwnedResultAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() == WINRT_CALLER_OWNED_RESULT_ANNOTATION_FQ_NAME

internal enum class RecipeUsage {
    INPUT,
    OUTPUT,
}

private val DIRECT_INBOUND_RECIPE_KINDS = setOf(
    WinRTProjectionCallSiteRecipeKind.VALUE,
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.PROJECTION,
)

private data class AbiTypeFacts(
    val kind: AbiTypeKind,
    val carrier: AbiCarrier,
    val transform: AbiValueTransform,
    val size: Int,
    val alignment: Int,
    val reference: AbiReferenceKind,
) {
    init {
        require(size >= 0 && alignment >= 0) { "generated ABI layout cannot be negative" }
        require(kind != AbiTypeKind.STRUCT || size > 0 && alignment > 0) {
            "generated struct ABI metadata requires a complete layout"
        }
    }
}

private enum class AbiTypeKind {
    ENUM,
    STRUCT,
    COM_REFERENCE,
    PROJECTION,
    ARRAY,
    ;

    fun typeSignature(projectedName: String): String =
        "${name.lowercase().replaceFirstChar(Char::uppercase)}($projectedName|typed)"
}

private enum class AbiCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
    ;

    fun loweringCarrier(): WinRTProjectionCallSiteAbiCarrier =
        WinRTProjectionCallSiteAbiCarrier.valueOf(name)

    fun kotlinTypeName(): String = when (this) {
        ADDRESS -> WINRT_RAW_ADDRESS_FQ_NAME.asString()
        INT8 -> KOTLIN_BYTE_FQ_NAME.asString()
        INT16 -> KOTLIN_SHORT_FQ_NAME.asString()
        INT32 -> KOTLIN_INT_FQ_NAME.asString()
        INT64 -> KOTLIN_LONG_FQ_NAME.asString()
        FLOAT32 -> KOTLIN_FLOAT_FQ_NAME.asString()
        FLOAT64 -> KOTLIN_DOUBLE_FQ_NAME.asString()
    }

    fun primitiveSignature(transform: AbiValueTransform): String = when (transform) {
        AbiValueTransform.BOOLEAN -> "Boolean"
        AbiValueTransform.CHAR16 -> "Char16"
        AbiValueTransform.UNSIGNED -> when (this) {
            INT8 -> "UInt8"
            INT16 -> "UInt16"
            INT32 -> "UInt32"
            INT64 -> "UInt64"
            else -> error("Unsigned array element metadata requires an integral carrier")
        }
        AbiValueTransform.IDENTITY -> when (this) {
            ADDRESS -> error("An address is not a primitive array element recipe")
            INT8 -> "Int8"
            INT16 -> "Int16"
            INT32 -> "Int32"
            INT64 -> "Int64"
            FLOAT32 -> "Float"
            FLOAT64 -> "Double"
        }
    }
}

private enum class AbiValueTransform {
    IDENTITY,
    BOOLEAN,
    UNSIGNED,
    CHAR16,
    ;

    fun loweringTransform(): WinRTProjectionCallSiteValueTransform =
        WinRTProjectionCallSiteValueTransform.valueOf(name)
}

private enum class AbiReferenceKind {
    NONE,
    UNKNOWN,
    INSPECTABLE,
}

private enum class AbiCodecRole {
    TO_ABI,
    FROM_ABI,
    CREATE_MARSHALER,
    COPY_TO_ABI,
    COPY_FROM_ABI,
    DISPOSE_ABI,
}

private data class CodecFacts(
    val role: AbiCodecRole,
    val consumesOwnedAbi: Boolean,
    val ownerFqName: String,
    val functionName: String,
    val parameterTypes: List<String>,
    val parameterIrTypes: List<IrType>,
    val returnType: String,
    val returnIrType: IrType,
) {
    fun hasSameDeclarationAs(other: CodecFacts): Boolean =
        ownerFqName == other.ownerFqName &&
            functionName == other.functionName &&
            parameterTypes == other.parameterTypes &&
            returnType == other.returnType

    fun hasSameContractAs(other: CodecFacts): Boolean =
        role == other.role && consumesOwnedAbi == other.consumesOwnedAbi
}

private fun WinRTProjectionCallSiteParameterDirection.loweringDirection(): WinRTProjectionCallSiteSlotDirection =
    when (this) {
        WinRTProjectionCallSiteParameterDirection.IN -> WinRTProjectionCallSiteSlotDirection.IN
        WinRTProjectionCallSiteParameterDirection.REF -> WinRTProjectionCallSiteSlotDirection.REF
        WinRTProjectionCallSiteParameterDirection.OUT -> WinRTProjectionCallSiteSlotDirection.OUT
        WinRTProjectionCallSiteParameterDirection.PASS_ARRAY -> WinRTProjectionCallSiteSlotDirection.PASS_ARRAY
        WinRTProjectionCallSiteParameterDirection.FILL_ARRAY -> WinRTProjectionCallSiteSlotDirection.FILL_ARRAY
    }

private fun WinRTProjectionCallSiteSlotDirection.recipeUsage(): RecipeUsage =
    when (this) {
        WinRTProjectionCallSiteSlotDirection.OUT,
        WinRTProjectionCallSiteSlotDirection.CALLER_OUT,
        WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY,
        WinRTProjectionCallSiteSlotDirection.RETURN -> RecipeUsage.OUTPUT
        WinRTProjectionCallSiteSlotDirection.IN,
        WinRTProjectionCallSiteSlotDirection.REF,
        WinRTProjectionCallSiteSlotDirection.PASS_ARRAY,
        WinRTProjectionCallSiteSlotDirection.FILL_ARRAY -> RecipeUsage.INPUT
    }

private fun IrSimpleFunction.regularParameters() =
    parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }

private fun IrConstructor.regularParameters() =
    parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }

private inline fun <reified T : Enum<T>> IrFunctionAccessExpression.enumArgument(
    name: String,
    default: T,
): T {
    val argument = namedArgument(name) ?: return default
    val enumName = (argument as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
        ?: error("annotation argument '$name' must be a constant enum value")
    return enumValues<T>().singleOrNull { value -> value.name == enumName }
        ?: error("annotation argument '$name' has unknown ${T::class.simpleName} value '$enumName'")
}

private fun IrFunctionAccessExpression.stringArgument(name: String): String {
    val argument = namedArgument(name) ?: return ""
    return (argument as? IrConst)?.value as? String
        ?: error("annotation argument '$name' must be a constant string")
}

private fun IrFunctionAccessExpression.intArgument(name: String): Int {
    val argument = namedArgument(name) ?: return 0
    return (argument as? IrConst)?.value as? Int
        ?: error("annotation argument '$name' must be a constant Int")
}

private fun IrFunctionAccessExpression.booleanArgument(name: String): Boolean {
    val argument = namedArgument(name) ?: return false
    return (argument as? IrConst)?.value as? Boolean
        ?: error("annotation argument '$name' must be a constant Boolean")
}

private fun IrFunctionAccessExpression.namedArgument(name: String): IrExpression? {
    val index = symbol.owner.parameters.indexOfFirst { parameter -> parameter.name.asString() == name }
    require(index >= 0) { "annotation has no '$name' parameter" }
    return arguments.getOrNull(index)
}

private val WINRT_OUT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTOut")
private val WINRT_GUID_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.Guid")
private val WINRT_RAW_ADDRESS_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawAddress")
private val WINRT_RAW_COM_PTR_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawComPtr")
private val WINRT_IUNKNOWN_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IUnknownReference")
private val WINRT_INSPECTABLE_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.InspectableReference")
private val WINRT_COM_OBJECT_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComObjectReference")
private val WINRT_ABI_ARRAY_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTAbiArray")
private const val WINRT_ASYNC_ACTION_REFERENCE_FQ_NAME =
    "io.github.composefluent.winrt.runtime.WinRTAsyncActionReference"
private const val WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_FQ_NAME =
    "io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference"
private const val WINRT_ASYNC_OPERATION_REFERENCE_FQ_NAME =
    "io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference"
private const val WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_FQ_NAME =
    "io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference"
private const val WINRT_ASYNC_PROJECTION_INTEROP_FQ_NAME =
    "io.github.composefluent.winrt.runtime.WinRTAsyncProjectionInterop"
private val KOTLIN_UNIT_FQ_NAME = FqName("kotlin.Unit")
private val KOTLIN_ARRAY_FQ_NAME = FqName("kotlin.Array")
private val KOTLIN_BOOLEAN_FQ_NAME = FqName("kotlin.Boolean")
private val KOTLIN_BYTE_FQ_NAME = FqName("kotlin.Byte")
private val KOTLIN_UBYTE_FQ_NAME = FqName("kotlin.UByte")
private val KOTLIN_SHORT_FQ_NAME = FqName("kotlin.Short")
private val KOTLIN_USHORT_FQ_NAME = FqName("kotlin.UShort")
private val KOTLIN_INT_FQ_NAME = FqName("kotlin.Int")
private val KOTLIN_UINT_FQ_NAME = FqName("kotlin.UInt")
private val KOTLIN_LONG_FQ_NAME = FqName("kotlin.Long")
private val KOTLIN_ULONG_FQ_NAME = FqName("kotlin.ULong")
private val KOTLIN_FLOAT_FQ_NAME = FqName("kotlin.Float")
private val KOTLIN_DOUBLE_FQ_NAME = FqName("kotlin.Double")
private val KOTLIN_CHAR_FQ_NAME = FqName("kotlin.Char")
private val KOTLIN_STRING_FQ_NAME = FqName("kotlin.String")
private val WINRT_PROJECTION_ABI_TYPE_FQ_NAME = FqName(WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME)
private val WINRT_PROJECTION_ABI_CODEC_FQ_NAME = FqName(WINRT_PROJECTION_ABI_CODEC_ANNOTATION_FQ_NAME)
private val WINRT_PROJECTION_ABI_TYPE_CLASS_ID = ClassId.topLevel(WINRT_PROJECTION_ABI_TYPE_FQ_NAME)
private val WINRT_PROJECTION_ABI_CODEC_CLASS_ID = ClassId.topLevel(WINRT_PROJECTION_ABI_CODEC_FQ_NAME)
