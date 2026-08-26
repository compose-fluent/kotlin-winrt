@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Lowers every declaration placement through one ordered recipe fold. */
internal class WinRTCallSiteRecipeLowering private constructor(
    private val directCallBackend: WinRTDirectCallBackend,
    private val resolver: CallSiteSymbolResolver,
    private val comObjectReferencePointerGetter: IrSimpleFunctionSymbol,
    private val acquireScalarScratchFrame: IrSimpleFunctionSymbol,
    private val scalarScratchFramePointerGetter: IrSimpleFunctionSymbol,
    private val scalarScratchFrameConsumeOwnedHString: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadPointer: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt8: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt16: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt32: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt64: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadFloat: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadDouble: IrSimpleFunctionSymbol,
    private val scalarScratchFrameClose: IrSimpleFunctionSymbol,
    private val acquireHStringReferenceFrame: IrSimpleFunctionSymbol,
    private val hStringReferenceFrameHandleGetter: IrSimpleFunctionSymbol,
    private val hStringReferenceFrameClose: IrSimpleFunctionSymbol,
    private val winRTPinString: IrSimpleFunctionSymbol,
    private val winRTStringAddress: IrSimpleFunctionSymbol,
    private val winRTStringLength: IrSimpleFunctionSymbol,
    private val acquireStructScratchFrame: IrSimpleFunctionSymbol?,
    private val structScratchFramePointerGetter: IrSimpleFunctionSymbol?,
    private val structScratchFrameReadInt8Carrier: IrSimpleFunctionSymbol?,
    private val structScratchFrameReadInt16Carrier: IrSimpleFunctionSymbol?,
    private val structScratchFrameReadInt32Carrier: IrSimpleFunctionSymbol?,
    private val structScratchFrameReadInt64Carrier: IrSimpleFunctionSymbol?,
    private val structScratchFrameClose: IrSimpleFunctionSymbol?,
    private val platformAbi: IrClassSymbol?,
    private val platformAbiNullPointerGetter: IrSimpleFunctionSymbol?,
    private val platformAbiNullComPtrGetter: IrSimpleFunctionSymbol?,
    private val platformAbiFromRawComPtr: IrSimpleFunctionSymbol?,
    private val platformAbiToRawComPtr: IrSimpleFunctionSymbol?,
    private val platformAbiReadPointer: IrSimpleFunctionSymbol?,
    private val platformAbiReadInt8: IrSimpleFunctionSymbol?,
    private val platformAbiReadInt16: IrSimpleFunctionSymbol?,
    private val platformAbiReadInt32: IrSimpleFunctionSymbol?,
    private val platformAbiReadInt64: IrSimpleFunctionSymbol?,
    private val platformAbiReadFloat: IrSimpleFunctionSymbol?,
    private val platformAbiReadDouble: IrSimpleFunctionSymbol?,
    private val platformAbiReadGuid: IrSimpleFunctionSymbol?,
    private val platformAbiSlice: IrSimpleFunctionSymbol?,
    private val platformAbiWritePointer: IrSimpleFunctionSymbol?,
    private val platformAbiWriteInt8: IrSimpleFunctionSymbol?,
    private val platformAbiWriteInt16: IrSimpleFunctionSymbol?,
    private val platformAbiWriteInt32: IrSimpleFunctionSymbol?,
    private val platformAbiWriteInt64: IrSimpleFunctionSymbol?,
    private val platformAbiWriteFloat: IrSimpleFunctionSymbol?,
    private val platformAbiWriteDouble: IrSimpleFunctionSymbol?,
    private val platformAbiWriteGuid: IrSimpleFunctionSymbol?,
    private val platformAbiIsNullPointer: IrSimpleFunctionSymbol?,
    private val iWinRTObjectNativeObjectGetter: IrSimpleFunctionSymbol?,
    private val iWinRTObjectGetObjectReferenceForType: IrSimpleFunctionSymbol?,
    private val tryAcquireWinRTManagedProjectionCallLease: IrSimpleFunctionSymbol,
    private val tryAcquireWinRTManagedProjectionCallLeaseWithState: IrSimpleFunctionSymbol,
    private val releaseWinRTManagedProjectionCallLease: IrSimpleFunctionSymbol,
    private val tryBorrowWinRTManagedProjectionAbi: IrSimpleFunctionSymbol,
    private val tryBorrowWinRTManagedProjectionAbiWithState: IrSimpleFunctionSymbol,
    private val tryBorrowWinRTManagedInspectableAbi: IrSimpleFunctionSymbol,
    private val winRTManagedProjectionStateAccessor: IrSimpleFunctionSymbol,
    private val winRTProjectionMarshaler: IrSimpleFunctionSymbol,
    private val winRTProjectionMarshalerAbiGetter: IrSimpleFunctionSymbol,
    private val winRTProjectionMarshalerClose: IrSimpleFunctionSymbol,
    private val winRTKeepAlive: IrSimpleFunctionSymbol,
    private val winRTAbiArrayAllocateInput: IrSimpleFunctionSymbol,
    private val winRTAbiArrayLengthGetter: IrSimpleFunctionSymbol,
    private val winRTAbiArrayDataGetter: IrSimpleFunctionSymbol,
    private val winRTAbiArrayClose: IrSimpleFunctionSymbol,
    private val nativeStringMarshallerFromAbi: IrSimpleFunctionSymbol,
    private val nativeStringMarshallerFromManaged: IrSimpleFunctionSymbol,
    private val nativeStringMarshallerGetAbiHString: IrSimpleFunctionSymbol,
    private val nativeStringMarshallerDisposeAbi: IrSimpleFunctionSymbol,
    private val winRTProjectionInboundRetainAddress: IrSimpleFunctionSymbol,
    private val winRTPlatformApiReleaseRaw: IrSimpleFunctionSymbol,
    private val winRTPlatformApiCoTaskMemFreeRaw: IrSimpleFunctionSymbol,
    private val hResultConstructor: IrConstructorSymbol,
    private val hResultIsFailureGetter: IrSimpleFunctionSymbol,
    private val hResultRequireSuccess: IrSimpleFunctionSymbol,
    private val winRTConsumeOwnedHStringScalarResult: IrSimpleFunctionSymbol,
    private val winRTScalarResultRecord: IrSimpleFunctionSymbol?,
    private val winRTScalarResultHResult: IrSimpleFunctionSymbol?,
    private val winRTScalarResultValue: IrSimpleFunctionSymbol?,
    private val winRTWideScalarResultFloat64: IrSimpleFunctionSymbol?,
    private val winRTPackedScalarResultHResult: IrSimpleFunctionSymbol,
    private val winRTPackedScalarResultInt8: IrSimpleFunctionSymbol,
    private val winRTPackedScalarResultInt16: IrSimpleFunctionSymbol,
    private val winRTPackedScalarResultInt32: IrSimpleFunctionSymbol,
    private val winRTPackedScalarResultFloat32: IrSimpleFunctionSymbol,
    private val primitiveSymbols: PrimitiveCallSiteSymbols,
) {
    var lastFailureDetail: String? = null
        private set

    fun lowerPlan(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        callerOwnedConstructor: IrConstructorSymbol?,
        pluginContext: IrPluginContext,
    ): Boolean {
        val foldedSlots = mutableListOf<FoldedSlot>()
        var parameterIndex = 2
        descriptor.slots.forEach { slot ->
            val folded = if (slot.functionParameterCount == 0) {
                FoldedSlot(slot, null)
            } else {
                FoldedSlot(slot, parameterIndex).also { parameterIndex += slot.functionParameterCount }
            }
            foldedSlots += folded
        }
        if (parameterIndex != descriptor.functionParameterCount + 2) return false

        val parameters = function.regularParameters()
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        val invoke: (LoweringState) -> IrExpression? = { state ->
            emitInvocation(
                builder,
                function,
                descriptor,
                callerOwnedConstructor,
                parameters,
                pluginContext,
                state,
            )
        }
        val emitPlan = foldedSlots.asReversed().fold(invoke) { continuation, folded ->
            { state -> emitSlot(builder, function, descriptor, folded, parameters, pluginContext, state, continuation) }
        }
        lastFailureDetail = null
        val body = try {
            emitScalarResultInvocationIfPossible(
                builder = builder,
                function = function,
                descriptor = descriptor,
                foldedSlots = foldedSlots,
                parameters = parameters,
                pluginContext = pluginContext,
            ) ?: emitPlan(LoweringState())
        } catch (failure: CallSiteLoweringAborted) {
            lastFailureDetail = failure.message
            null
        } ?: run {
            if (lastFailureDetail == null) lastFailureDetail = "recipe emission returned no complete expression"
            return false
        }
        function.body = builder.irBlockBody { +builder.irReturn(body) }
        return true
    }

    internal fun decodeDirectInboundValue(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        projectedType: IrType,
        abiValue: IrExpression,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        if (recipe.kind !in DIRECT_INBOUND_LOWERING_RECIPE_KINDS || recipe.abiCarriers.size != 1) return null
        val carrier = recipe.abiCarriers.single()
        val normalized = normalizeInboundCarrier(builder, carrier, abiValue) ?: return null
        val decodedAbi = if (recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE &&
            recipe.inboundDecodeConsumesOwnedComReference
        ) {
            builder.irCall(winRTProjectionInboundRetainAddress).apply { arguments[0] = normalized }
        } else {
            normalized
        }
        val slot = WinRTProjectionCallSiteSlot(
            direction = WinRTProjectionCallSiteSlotDirection.RETURN,
            ownership = if (recipe.requiresNativeOwnership) {
                WinRTProjectionCallSiteOwnership.OWNED
            } else {
                WinRTProjectionCallSiteOwnership.NONE
            },
            recipe = recipe,
        )
        return decodeDirectResult(
            builder = builder,
            returnType = projectedType,
            recipe = recipe,
            storage = OutputStorage(
                addresses = emptyList(),
                scalarFrames = emptyList(),
                structFrame = null,
                directScalarValue = decodedAbi,
            ),
            slot = slot,
            pluginContext = pluginContext,
        )
    }

    internal fun emitDirectInboundResult(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        projectedValue: IrExpression,
        pluginContext: IrPluginContext,
        publish: (IrExpression) -> IrExpression?,
    ): IrExpression? {
        if (recipe.kind !in DIRECT_INBOUND_LOWERING_RECIPE_KINDS || recipe.abiCarriers.size != 1) return null
        return emitInputRecipe(
            builder = builder,
            function = function,
            recipe = recipe,
            value = projectedValue,
            pluginContext = pluginContext,
            allowNativeDirectHString = false,
        ) { prepared ->
            val abiValue = prepared.abiValues.singleOrNull() ?: return@emitInputRecipe null
            val callerOwnedValue = if (recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE) {
                builder.irCall(winRTProjectionInboundRetainAddress).apply { arguments[0] = abiValue }
            } else {
                abiValue
            }
            val publication = publish(callerOwnedValue) ?: return@emitInputRecipe null
            builder.irBlock(resultType = function.returnType) {
                +publication
                prepared.keepAliveOwners.forEach { owner ->
                    +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
                }
                +builder.irInt(0)
            }
        }
    }

    /**
     * Uses the Native stack-result thunk for the closed scalar shape. The shape is derived from
     * the recipe graph, so enum/projection wrappers share this route without naming concrete
     * WinRT types or methods. JVM deliberately returns null from the backend and keeps the FFM
     * scratch path below.
     */
    private fun emitScalarResultInvocationIfPossible(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        foldedSlots: List<FoldedSlot>,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        if (!directCallBackend.supportsNativeRecipeThunks) return null
        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN) {
            return null
        }
        val resultSlot = descriptor.slots.singleOrNull { slot ->
            slot.direction == WinRTProjectionCallSiteSlotDirection.RETURN
        } ?: return null
        if (descriptor.slots.lastOrNull() !== resultSlot || resultSlot.abiCarriers.size != 1) {
            return null
        }
        // HSTRING returns are the one owned scalar shape that can use the wide-result thunk.
        // The storage kind comes from the recursive WinMD recipe; no projected type names are
        // inspected here. Keep wrapped/other native-owned recipes on the transaction path until
        // their post-call ownership contract can be represented without a temporary frame.
        val ownedHStringResult =
            resultSlot.ownership == WinRTProjectionCallSiteOwnership.OWNED &&
                resultSlot.recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING &&
                resultSlot.recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING &&
                resultSlot.recipe.abiCarriers == listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS)
        val unownedScalarResult =
            resultSlot.ownership == WinRTProjectionCallSiteOwnership.NONE &&
                isScalarValueRecipe(resultSlot.recipe)
        if (!ownedHStringResult && !unownedScalarResult) return null
        val inputSlots = foldedSlots.dropLast(1)
        if (inputSlots.any { folded ->
                val slot = folded.slot
                slot.direction != WinRTProjectionCallSiteSlotDirection.IN ||
                    slot.functionParameterCount != 1 ||
                    slot.abiCarriers.size != 1 ||
                    slot.recipe.hasInputPostCall() ||
                    folded.functionParameterIndex == null
            }) {
            return null
        }
        val carriers = inputSlots.flatMap { folded -> folded.slot.abiCarriers }
        if (carriers.size > MAX_SCALAR_RESULT_ARGUMENT_WORD_COUNT) return null
        val resultCarrier = resultSlot.recipe.abiCarriers.single()
        val recordHResultReader = winRTScalarResultHResult
        val wideFloat64Reader = winRTWideScalarResultFloat64
        val wideScalarResult = ownedHStringResult ||
            (unownedScalarResult && when (resultCarrier) {
                WinRTProjectionCallSiteAbiCarrier.INT64 -> true
                WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> wideFloat64Reader != null
                else -> false
            })
        val useWideScalarResult =
            wideScalarResult &&
                directCallBackend.supportsNativeRecipeThunks
        val packedHResultReader = winRTPackedScalarResultHResult
        val packedValueReader = if (ownedHStringResult) {
            null
        } else {
            packedScalarResultValueReader(resultCarrier)
        }

        return emitScalarInputSlots(
            builder = builder,
            function = function,
            slots = inputSlots,
            index = 0,
            parameters = parameters,
            pluginContext = pluginContext,
            values = emptyList(),
            directInputs = emptyList(),
            keepAliveOwners = emptyList(),
        ) { inputValues, inputDirectInputs, inputKeepAliveOwners ->
            val instance = builder.irCall(comObjectReferencePointerGetter).apply {
                arguments[0] = builder.irGet(parameters[0])
            }
            val packedCall = if (packedValueReader != null) {
                directCallBackend.emitPackedScalarResultDirect(
                    builder = builder,
                    pluginContext = pluginContext,
                    ownerFunction = function,
                    instance = instance,
                    slot = builder.irGet(parameters[1]),
                    inputs = inputDirectInputs,
                )
            } else {
                null
            }
            val recordValueReader = winRTScalarResultValue
            val recordCall = if (
                packedCall == null &&
                !useWideScalarResult &&
                recordHResultReader != null &&
                recordValueReader != null
            ) {
                directCallBackend.emitScalarResultDirect(
                    builder = builder,
                    pluginContext = pluginContext,
                    ownerFunction = function,
                    instance = instance,
                    slot = builder.irGet(parameters[1]),
                    inputs = inputDirectInputs,
                )
            } else {
                null
            }
            if (packedCall == null && recordCall == null && !useWideScalarResult) return@emitScalarInputSlots null
            builder.irBlock(resultType = function.returnType) {
                val owners = listOf(builder.irGet(parameters[0])) + inputKeepAliveOwners
                if (ownedHStringResult) {
                    val wideResult = irTemporary(
                        directCallBackend.emitWideScalarResultDirect(
                            builder = builder,
                            pluginContext = pluginContext,
                            ownerFunction = function,
                            instance = instance,
                            slot = builder.irGet(parameters[1]),
                            inputs = inputDirectInputs,
                        ) ?: abortCallSiteLowering("owned HSTRING result cannot use the Native wide backend"),
                        nameHint = "ownedHStringWideResult",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                    owners.forEach { owner ->
                        +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
                    }
                    val handleBits = directCallBackend.emitWideScalarResultValueBits(
                        builder,
                        builder.irGet(wideResult),
                    ) ?: abortCallSiteLowering("owned HSTRING handle cannot be read from the Native vector")
                    val hResult = directCallBackend.emitWideScalarResultHResult(
                        builder,
                        builder.irGet(wideResult),
                    ) ?: abortCallSiteLowering("owned HSTRING HRESULT cannot be read from the Native vector")
                    +builder.irCall(winRTConsumeOwnedHStringScalarResult).apply {
                        arguments[0] = handleBits
                        arguments[1] = hResult
                        arguments[2] = builder.irBoolean(
                            descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK,
                        )
                    }
                } else {
                    val result = if (useWideScalarResult) {
                        val wideResult = irTemporary(
                            directCallBackend.emitWideScalarResultDirect(
                                builder = builder,
                                pluginContext = pluginContext,
                                ownerFunction = function,
                                instance = instance,
                                slot = builder.irGet(parameters[1]),
                                inputs = inputDirectInputs,
                            ) ?: abortCallSiteLowering("wide scalar result cannot use the Native direct backend"),
                            nameHint = "wideScalarResult",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        owners.forEach { owner ->
                            +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
                        }
                        val wideValueBits = directCallBackend.emitWideScalarResultValueBits(
                            builder,
                            builder.irGet(wideResult),
                        ) ?: abortCallSiteLowering("wide scalar result value cannot be read from the Native vector")
                        val directValue = irTemporary(
                            when (resultCarrier) {
                                WinRTProjectionCallSiteAbiCarrier.INT64 -> wideValueBits
                                WinRTProjectionCallSiteAbiCarrier.FLOAT64 ->
                                    builder.irCall(requireNotNull(wideFloat64Reader)).apply {
                                        arguments[0] = wideValueBits
                                    }
                                else -> abortCallSiteLowering("unsupported wide scalar carrier $resultCarrier")
                            },
                            nameHint = "scalarResultValue",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                            val hResult = irTemporary(
                                directCallBackend.emitWideScalarResultHResult(
                                    builder,
                                    builder.irGet(wideResult),
                                ) ?: abortCallSiteLowering(
                                    "wide scalar HRESULT cannot be read from the Native vector",
                                ),
                                nameHint = "hr",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            +builder.irCall(hResultRequireSuccess).apply {
                                arguments[0] = builder.irCall(hResultConstructor).apply {
                                    arguments[0] = builder.irGet(hResult)
                                }
                                arguments[1] = builder.irString("WinRT call")
                            }
                        }
                        PreparedResult(
                            slot = resultSlot,
                            storage = OutputStorage(
                                addresses = emptyList(),
                                scalarFrames = emptyList(),
                                structFrame = null,
                                directScalarValue = builder.irGet(directValue),
                            ),
                        )
                    } else if (packedCall != null) {
                        val packed = irTemporary(
                            packedCall,
                            nameHint = "packedScalarResult",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        owners.forEach { owner ->
                            +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
                        }
                        val directValue = irTemporary(
                            builder.irCall(requireNotNull(packedValueReader)).apply {
                                arguments[0] = builder.irGet(packed)
                            },
                            nameHint = "scalarResultValue",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                            val hResult = irTemporary(
                                builder.irCall(packedHResultReader).apply {
                                    arguments[0] = builder.irGet(packed)
                                },
                                nameHint = "hr",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            +builder.irCall(hResultRequireSuccess).apply {
                                arguments[0] = builder.irCall(hResultConstructor).apply {
                                    arguments[0] = builder.irGet(hResult)
                                }
                                arguments[1] = builder.irString("WinRT call")
                            }
                        }
                        PreparedResult(
                            slot = resultSlot,
                            storage = OutputStorage(
                                addresses = emptyList(),
                                scalarFrames = emptyList(),
                                structFrame = null,
                                directScalarValue = builder.irGet(directValue),
                            ),
                        )
                    } else {
                        val record = irTemporary(
                            requireNotNull(recordCall),
                            nameHint = "scalarResultRecord",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        owners.forEach { owner ->
                            +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
                        }
                        val valueAddress = irTemporary(
                            builder.irCall(requireNotNull(recordValueReader)).apply {
                                arguments[0] = builder.irGet(record)
                            },
                            nameHint = "scalarResultValue",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                            val hResult = irTemporary(
                                builder.irCall(requireNotNull(recordHResultReader)).apply {
                                    arguments[0] = builder.irGet(record)
                                },
                                nameHint = "hr",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            +builder.irCall(hResultRequireSuccess).apply {
                                arguments[0] = builder.irCall(hResultConstructor).apply {
                                    arguments[0] = builder.irGet(hResult)
                                }
                                arguments[1] = builder.irString("WinRT call")
                            }
                        }
                        PreparedResult(
                            slot = resultSlot,
                            storage = OutputStorage(
                                addresses = listOf(builder.irGet(valueAddress)),
                                scalarFrames = emptyList(),
                                structFrame = null,
                            ),
                        )
                    }
                    +(decodeResult(
                        builder = builder,
                        function = function,
                        returnType = function.returnType,
                        result = result,
                        ownedOutputs = emptyList(),
                        pluginContext = pluginContext,
                    ) ?: abortCallSiteLowering())
                }
            }
        }
    }

    private fun packedScalarResultValueReader(
        carrier: WinRTProjectionCallSiteAbiCarrier,
    ): IrSimpleFunctionSymbol? = when (carrier) {
        WinRTProjectionCallSiteAbiCarrier.INT8 -> winRTPackedScalarResultInt8
        WinRTProjectionCallSiteAbiCarrier.INT16 -> winRTPackedScalarResultInt16
        WinRTProjectionCallSiteAbiCarrier.INT32 -> winRTPackedScalarResultInt32
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> winRTPackedScalarResultFloat32
        WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        WinRTProjectionCallSiteAbiCarrier.INT64,
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> null
    }

    private fun emitScalarInputSlots(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        slots: List<FoldedSlot>,
        index: Int,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
        values: List<IrExpression>,
        directInputs: List<WinRTDirectCallInput>,
        keepAliveOwners: List<IrExpression>,
        continuation: (List<IrExpression>, List<WinRTDirectCallInput>, List<IrExpression>) -> IrExpression?,
    ): IrExpression? {
        if (index == slots.size) return continuation(values, directInputs, keepAliveOwners)
        val folded = slots[index]
        val parameterIndex = folded.functionParameterIndex ?: return null
        val slot = folded.slot
        val value = builder.irGet(parameters[parameterIndex])
        return emitInputRecipe(
            builder = builder,
            function = function,
            recipe = slot.recipe,
            value = value,
            pluginContext = pluginContext,
            allowNativeDirectHString = directCallBackend.supportsNativeRecipeThunks,
        ) { prepared ->
            if (prepared.abiValues.size != slot.abiCarriers.size ||
                prepared.postCall != null
            ) {
                return@emitInputRecipe null
            }
            emitScalarInputSlots(
                builder = builder,
                function = function,
                slots = slots,
                index = index + 1,
                parameters = parameters,
                pluginContext = pluginContext,
                values = values + prepared.abiValues,
                directInputs = directInputs + directInputsForPrepared(slot.recipe, prepared),
                keepAliveOwners = keepAliveOwners + prepared.keepAliveOwners,
                continuation = continuation,
            )
        }
    }

    private fun WinRTProjectionCallSiteRecipe.hasInputPostCall(): Boolean =
        if (kind != WinRTProjectionCallSiteRecipeKind.PROJECTION) {
            false
        } else {
            val projectionCallables = requireNotNull(callables)
            when {
                projectionCallables.createMarshaler.isNotBlank() ->
                    projectionCallables.copyFromAbi.isNotBlank()
                projectionCallables.toAbi.isNotBlank() -> false
                else -> children.single().hasInputPostCall()
            }
        }

    private fun isScalarValueRecipe(recipe: WinRTProjectionCallSiteRecipe): Boolean = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE ->
            recipe.abiCarriers.size == 1 && recipe.valueCarrier != null
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.PROJECTION ->
            recipe.abiCarriers.size == 1 && recipe.children.singleOrNull()?.let(::isScalarValueRecipe) == true
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.GUID,
        WinRTProjectionCallSiteRecipeKind.STRUCT,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> false
    }

    private fun emitEnumToAbi(
        builder: DeclarationIrBuilder,
        value: IrExpression,
        callables: WinRTProjectionCallSiteCallables,
    ): IrExpression? {
        val converter = resolver.function(callables.ownerFqName, callables.toAbi, 1) ?: return null
        val enumClass = value.type.classOrNull?.owner ?: return null
        if (!enumClass.isValue) return null
        val constructorParameter = enumClass.declarations
            .filterIsInstance<IrConstructor>()
            .mapNotNull { constructor -> constructor.regularParameters().singleOrNull() }
            .singleOrNull { parameter -> parameter.type == converter.owner.returnType }
            ?: return null
        val getter = enumClass.declarations
            .filterIsInstance<IrProperty>()
            .singleOrNull { property ->
                property.name == constructorParameter.name &&
                    property.getter?.returnType == converter.owner.returnType
            }
            ?.getter
            ?.symbol
            ?: return null
        return resolver.memberCall(builder, getter, value, emptyList())
    }

    private fun emitEnumFromAbi(
        builder: DeclarationIrBuilder,
        enumType: IrType,
        abi: IrExpression,
    ): IrExpression? {
        val enumClass = enumType.classOrNull?.owner ?: return null
        if (!enumClass.isValue) return null
        val constructor = enumClass.declarations
            .filterIsInstance<IrConstructor>()
            .singleOrNull { candidate ->
                candidate.regularParameters().singleOrNull()?.type == abi.type
            }
            ?: return null
        return builder.irCall(constructor.symbol).apply {
            constructor.parameters.forEachIndexed { index, parameter ->
                if (parameter.kind == IrParameterKind.Regular) {
                    arguments[index] = abi
                }
            }
        }
    }

    private fun emitSlot(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        folded: FoldedSlot,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
        state: LoweringState,
        continuation: (LoweringState) -> IrExpression?,
    ): IrExpression? {
        val slot = folded.slot
        if (slot.direction.isProjectedResult) {
            if (state.result != null) return null
            return emitOutputStorage(
                builder = builder,
                function = function,
                recipe = slot.recipe,
                clear = descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.IGNORE ||
                    slot.ownership ==
                    WinRTProjectionCallSiteOwnership.OWNED,
                pluginContext = pluginContext,
            ) { storage ->
                continuation(
                    state.copy(
                        abiArguments = state.abiArguments + storage.addresses,
                        directInputs = state.directInputs + standardDirectInputs(
                            slot.abiCarriers,
                            storage.addresses,
                        ),
                        allocatedOutputs = state.allocatedOutputs + PreparedStorage(slot, storage),
                        result = PreparedResult(slot, storage),
                    ),
                )
            }
        }

        if (slot.direction == WinRTProjectionCallSiteSlotDirection.CALLER_OUT) {
            return emitOutputStorage(
                builder = builder,
                function = function,
                recipe = slot.recipe,
                clear = descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.IGNORE ||
                    slot.ownership ==
                    WinRTProjectionCallSiteOwnership.OWNED,
                pluginContext = pluginContext,
            ) { storage ->
                continuation(
                    state.copy(
                        abiArguments = state.abiArguments + storage.addresses,
                        directInputs = state.directInputs + standardDirectInputs(
                            slot.abiCarriers,
                            storage.addresses,
                        ),
                        allocatedOutputs = state.allocatedOutputs + PreparedStorage(slot, storage),
                        callerOutputs = state.callerOutputs + PreparedCallerOutput(slot, storage),
                    ),
                )
            }
        }

        val value = builder.irGet(parameters[requireNotNull(folded.functionParameterIndex)])
        if (slot.direction == WinRTProjectionCallSiteSlotDirection.OUT) {
            val holderType = value.type as? IrSimpleType ?: return null
            if (holderType.classFqName != WINRT_OUT_FQ_NAME) return null
            val projectedType = holderType.arguments.singleOrNull()?.typeOrNull ?: return null
            return emitOutputStorage(
                builder = builder,
                function = function,
                recipe = slot.recipe,
                clear = slot.ownership == WinRTProjectionCallSiteOwnership.OWNED,
                pluginContext = pluginContext,
            ) { storage ->
                if (storage.addresses.size != 1) return@emitOutputStorage null
                continuation(
                    state.copy(
                        abiArguments = state.abiArguments + storage.addresses.single(),
                        directInputs = state.directInputs + standardDirectInputs(
                            slot.abiCarriers,
                            storage.addresses,
                        ),
                        allocatedOutputs = state.allocatedOutputs + PreparedStorage(slot, storage),
                        outputs = state.outputs + PreparedOutput(slot, value, projectedType, storage),
                    ),
                )
            }
        }
        if (slot.direction == WinRTProjectionCallSiteSlotDirection.REF) {
            return emitReferenceStorage(
                builder = builder,
                function = function,
                recipe = slot.recipe,
                value = value,
                pluginContext = pluginContext,
            ) { address, postCall, keepAliveOwners ->
                continuation(
                    state.copy(
                        abiArguments = state.abiArguments + address,
                        directInputs = state.directInputs + standardDirectInputs(
                            slot.abiCarriers,
                            listOf(address),
                        ),
                        postCalls = state.postCalls + listOfNotNull(postCall),
                        keepAliveOwners = state.keepAliveOwners + keepAliveOwners,
                    ),
                )
            }
        }

        return emitInputRecipe(
            builder = builder,
            function = function,
            recipe = slot.recipe,
            value = value,
            pluginContext = pluginContext,
            allowNativeDirectHString = directCallBackend.supportsNativeRecipeThunks,
        ) { prepared ->
            continuation(
                state.copy(
                    abiArguments = state.abiArguments + prepared.abiValues,
                    directInputs = state.directInputs + directInputsForPrepared(slot.recipe, prepared),
                    postCalls = state.postCalls + listOfNotNull(prepared.postCall),
                    keepAliveOwners = state.keepAliveOwners + prepared.keepAliveOwners,
                ),
            )
        }
    }

    private fun emitInputRecipe(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        allowNativeDirectHString: Boolean = false,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? =
        when (recipe.kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE ->
                continuation(PreparedInput(listOf(primitiveSymbols.toAbi(builder, recipe, value) ?: return null)))
            WinRTProjectionCallSiteRecipeKind.HSTRING ->
                if (allowNativeDirectHString) {
                    emitDirectHStringInput(builder, function, value, continuation)
                } else {
                    emitHStringInput(builder, function, recipe, value, pluginContext, continuation)
                }
            WinRTProjectionCallSiteRecipeKind.GUID ->
                emitGuidInput(builder, function, recipe, value, pluginContext, continuation)
            WinRTProjectionCallSiteRecipeKind.ENUM -> {
                val callables = requireNotNull(recipe.callables)
                val abi = emitEnumToAbi(builder, value, callables)
                    ?: resolver.call(builder, callables.ownerFqName, callables.toAbi, listOf(value))
                    ?: return null
                emitInputRecipe(
                    builder,
                    function,
                    recipe.children.single(),
                    abi,
                    pluginContext,
                    allowNativeDirectHString,
                    continuation,
                )
            }
            WinRTProjectionCallSiteRecipeKind.STRUCT ->
                emitStructInput(builder, function, recipe, value, pluginContext, continuation)
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE ->
                emitComReferenceInput(builder, function, recipe, value, pluginContext, continuation)
            WinRTProjectionCallSiteRecipeKind.ARRAY ->
                emitArrayInput(builder, function, recipe, value, pluginContext, continuation)
            WinRTProjectionCallSiteRecipeKind.PROJECTION ->
                emitProjectionInput(
                    builder,
                    function,
                    recipe,
                    value,
                    pluginContext,
                    allowNativeDirectHString,
                    continuation,
                )
        }

    private fun emitReferenceStorage(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (IrExpression, (() -> IrExpression?)?, List<IrExpression>) -> IrExpression?,
    ): IrExpression? {
        val storageRecipe = recipe.storageRecipe
        if (storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.GUID) {
            return emitStructFrame(builder, function, storageRecipe, clear = false, pluginContext) { _, pointer ->
                val writeGuid = platformAbiWriteGuid ?: return@emitStructFrame null
                builder.irBlock(resultType = function.returnType) {
                    +builder.irCall(writeGuid).apply {
                        arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                        arguments[1] = pointer
                        arguments[2] = value
                    }
                    +(continuation(pointer, null, emptyList()) ?: abortCallSiteLowering())
                }
            }
        }
        if (storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.STRUCT) {
            val callables = storageRecipe.callables ?: return null
            if (callables.copyToAbi.isBlank()) return null
            return emitStructFrame(builder, function, storageRecipe, clear = false, pluginContext) { _, pointer ->
                val copy = resolver.call(
                    builder,
                    callables.ownerFqName,
                    callables.copyToAbi,
                    listOf(value, pointer),
                ) ?: return@emitStructFrame null
                builder.irBlock(resultType = function.returnType) {
                    +copy
                    val downstream = continuation(pointer, null, emptyList()) ?: abortCallSiteLowering()
                    if (callables.disposeAbi.isBlank()) {
                        +downstream
                    } else {
                        +builder.irTry(
                            type = function.returnType,
                            tryResult = downstream,
                            catches = emptyList(),
                            finallyExpression = resolver.call(
                                builder,
                                callables.ownerFqName,
                                callables.disposeAbi,
                                listOf(pointer),
                            ) ?: abortCallSiteLowering(),
                        )
                    }
                }
            }
        }
        if (recipe.abiCarriers.size != 1) return null
        return emitScalarOutputFrames(
            builder = builder,
            function = function,
            count = 1,
            clear = false,
            pluginContext = pluginContext,
            frames = emptyList(),
        ) { storage ->
            val address = storage.addresses.single()
            emitInputRecipe(
                builder,
                function,
                recipe,
                value,
                pluginContext,
                allowNativeDirectHString = false,
            ) { prepared ->
                val abiValue = prepared.abiValues.singleOrNull() ?: return@emitInputRecipe null
                val write = writeCarrier(builder, address, recipe.abiCarriers.single(), abiValue)
                    ?: return@emitInputRecipe null
                builder.irBlock(resultType = function.returnType) {
                    +write
                    +(continuation(address, prepared.postCall, prepared.keepAliveOwners) ?: abortCallSiteLowering())
                }
            }
        }
    }

    private fun writeCarrier(
        builder: DeclarationIrBuilder,
        address: IrExpression,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? {
        val symbol = when (carrier) {
            WinRTProjectionCallSiteAbiCarrier.ADDRESS -> platformAbiWritePointer
            WinRTProjectionCallSiteAbiCarrier.INT8 -> platformAbiWriteInt8
            WinRTProjectionCallSiteAbiCarrier.INT16 -> platformAbiWriteInt16
            WinRTProjectionCallSiteAbiCarrier.INT32 -> platformAbiWriteInt32
            WinRTProjectionCallSiteAbiCarrier.INT64 -> platformAbiWriteInt64
            WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> platformAbiWriteFloat
            WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> platformAbiWriteDouble
        } ?: return null
        return builder.irCall(symbol).apply {
            arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
            arguments[1] = address
            arguments[2] = value
        }
    }

    private fun readCarrier(
        builder: DeclarationIrBuilder,
        address: IrExpression,
        carrier: WinRTProjectionCallSiteAbiCarrier,
    ): IrExpression? {
        val symbol = when (carrier) {
            WinRTProjectionCallSiteAbiCarrier.ADDRESS -> platformAbiReadPointer
            WinRTProjectionCallSiteAbiCarrier.INT8 -> platformAbiReadInt8
            WinRTProjectionCallSiteAbiCarrier.INT16 -> platformAbiReadInt16
            WinRTProjectionCallSiteAbiCarrier.INT32 -> platformAbiReadInt32
            WinRTProjectionCallSiteAbiCarrier.INT64 -> platformAbiReadInt64
            WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> platformAbiReadFloat
            WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> platformAbiReadDouble
        } ?: return null
        return builder.irCall(symbol).apply {
            arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
            arguments[1] = address
        }
    }

    private fun encodeArrayElement(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        address: IrExpression,
    ): IrExpression? = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> writeCarrier(
            builder,
            address,
            recipe.valueCarrier ?: return null,
            primitiveSymbols.toAbi(builder, recipe, value) ?: return null,
        )
        WinRTProjectionCallSiteRecipeKind.HSTRING -> writeCarrier(
            builder,
            address,
            WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            objectCall(
                builder,
                nativeStringMarshallerGetAbiHString,
                listOf(objectCall(builder, nativeStringMarshallerFromManaged, listOf(value))),
            ),
        )
        WinRTProjectionCallSiteRecipeKind.GUID -> {
            val write = platformAbiWriteGuid ?: return null
            builder.irCall(write).apply {
                arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                arguments[1] = address
                arguments[2] = value
            }
        }
        WinRTProjectionCallSiteRecipeKind.ENUM -> {
            val callables = recipe.callables ?: return null
            val abi = emitEnumToAbi(builder, value, callables)
                ?: resolver.call(builder, callables.ownerFqName, callables.toAbi, listOf(value))
                ?: return null
            encodeArrayElement(builder, recipe.children.single(), abi, address)
        }
        WinRTProjectionCallSiteRecipeKind.STRUCT -> {
            val callables = recipe.callables ?: return null
            resolver.call(builder, callables.ownerFqName, callables.copyToAbi, listOf(value, address))
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.PROJECTION,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> null
    }

    private fun decodeArrayElement(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        elementType: IrType,
        address: IrExpression,
    ): IrExpression? = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> primitiveSymbols.fromAbi(
            builder,
            recipe,
            elementType,
            readCarrier(builder, address, recipe.valueCarrier ?: return null) ?: return null,
        )
        WinRTProjectionCallSiteRecipeKind.HSTRING -> objectCall(
            builder,
            nativeStringMarshallerFromAbi,
            listOf(readCarrier(builder, address, WinRTProjectionCallSiteAbiCarrier.ADDRESS) ?: return null),
        )
        WinRTProjectionCallSiteRecipeKind.GUID -> {
            val read = platformAbiReadGuid ?: return null
            builder.irCall(read).apply {
                arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                arguments[1] = address
            }
        }
        WinRTProjectionCallSiteRecipeKind.ENUM -> {
            val callables = recipe.callables ?: return null
            val fromAbi = resolver.function(callables.ownerFqName, callables.fromAbi, 1) ?: return null
            val carrierType = fromAbi.owner.regularParameters().singleOrNull()?.type ?: return null
            val abi = decodeArrayElement(builder, recipe.children.single(), carrierType, address) ?: return null
            emitEnumFromAbi(builder, elementType, abi) ?: resolver.call(builder, fromAbi, listOf(abi))
        }
        WinRTProjectionCallSiteRecipeKind.STRUCT -> {
            val callables = recipe.callables ?: return null
            resolver.call(builder, callables.ownerFqName, callables.fromAbi, listOf(address))
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.PROJECTION,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> null
    }?.let { decoded ->
        if (decoded.type == elementType) decoded else builder.irAs(decoded, elementType)
    }

    private fun cleanupArrayElement(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        address: IrExpression,
    ): IrExpression? = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.GUID -> builder.irUnit()
        WinRTProjectionCallSiteRecipeKind.HSTRING -> objectCall(
            builder,
            nativeStringMarshallerDisposeAbi,
            listOf(readCarrier(builder, address, WinRTProjectionCallSiteAbiCarrier.ADDRESS) ?: return null),
        )
        WinRTProjectionCallSiteRecipeKind.ENUM ->
            cleanupArrayElement(builder, recipe.children.single(), address)
        WinRTProjectionCallSiteRecipeKind.STRUCT -> {
            val callables = recipe.callables ?: return null
            if (callables.disposeAbi.isBlank()) builder.irUnit()
            else resolver.call(builder, callables.ownerFqName, callables.disposeAbi, listOf(address))
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.PROJECTION,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> null
    }

    private fun cleanupArrayElements(
        builder: DeclarationIrBuilder,
        elementRecipe: WinRTProjectionCallSiteRecipe,
        data: IrExpression,
        length: IrExpression,
        elementSize: Int,
        pluginContext: IrPluginContext,
    ): IrExpression? = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
        val stableData = irTemporary(data, nameHint = "arrayCleanupData", isMutable = false)
        val stableLength = irTemporary(length, nameHint = "arrayCleanupLength", isMutable = false)
        val index = irTemporary(builder.irInt(0), nameHint = "arrayCleanupIndex", isMutable = true)
        val loop = builder.irWhile()
        loop.condition = intLessThan(builder, builder.irGet(index), builder.irGet(stableLength))
        loop.body = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
            val address = arrayElementAddress(
                builder,
                builder.irGet(stableData),
                builder.irGet(index),
                elementSize,
            ) ?: return null
            +(cleanupArrayElement(builder, elementRecipe, address) ?: return null)
            +builder.irSet(index.symbol, intPlus(builder, builder.irGet(index), builder.irInt(1)))
        }
        +loop
        +builder.irUnit()
    }

    private fun arrayElementAddress(
        builder: DeclarationIrBuilder,
        data: IrExpression,
        index: IrExpression,
        elementSize: Int,
    ): IrExpression? {
        val slice = platformAbiSlice ?: return null
        val longIndex = builder.irCall(primitiveSymbols.intToLong).apply { arguments[0] = index }
        val offset = builder.irCall(primitiveSymbols.longTimes).apply {
            arguments[0] = longIndex
            arguments[1] = builder.irLong(elementSize.toLong())
        }
        return resolver.memberCall(
            builder,
            slice,
            builder.irGetObject(requireNotNull(platformAbi)),
            listOf(data, offset, builder.irLong(elementSize.toLong())),
        )
    }

    private fun arrayElementSizeBytes(recipe: WinRTProjectionCallSiteRecipe): Int? = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> recipe.valueCarrier?.storageSizeBytesForArray()
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> Long.SIZE_BYTES
        WinRTProjectionCallSiteRecipeKind.GUID -> 16
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> arrayElementSizeBytes(recipe.children.single())
        WinRTProjectionCallSiteRecipeKind.STRUCT -> recipe.sizeBytes
        WinRTProjectionCallSiteRecipeKind.ARRAY -> null
    }

    private fun arrayElementAlignmentBytes(recipe: WinRTProjectionCallSiteRecipe): Int? = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> recipe.valueCarrier?.storageSizeBytesForArray()
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> Long.SIZE_BYTES
        WinRTProjectionCallSiteRecipeKind.GUID -> Int.SIZE_BYTES
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> arrayElementAlignmentBytes(recipe.children.single())
        WinRTProjectionCallSiteRecipeKind.STRUCT -> recipe.alignmentBytes
        WinRTProjectionCallSiteRecipeKind.ARRAY -> null
    }

    private fun arrayElementNeedsCleanup(recipe: WinRTProjectionCallSiteRecipe): Boolean = when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> true
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> arrayElementNeedsCleanup(recipe.children.single())
        WinRTProjectionCallSiteRecipeKind.STRUCT -> recipe.callables?.disposeAbi?.isNotBlank() == true
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.GUID -> false
        WinRTProjectionCallSiteRecipeKind.ARRAY -> error("WinRT ABI arrays cannot contain nested arrays.")
    }

    private fun WinRTProjectionCallSiteAbiCarrier.storageSizeBytesForArray(): Int = when (this) {
        WinRTProjectionCallSiteAbiCarrier.INT8 -> Byte.SIZE_BYTES
        WinRTProjectionCallSiteAbiCarrier.INT16 -> Short.SIZE_BYTES
        WinRTProjectionCallSiteAbiCarrier.INT32,
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> Int.SIZE_BYTES
        WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        WinRTProjectionCallSiteAbiCarrier.INT64,
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> Long.SIZE_BYTES
    }

    private fun intLessThan(
        builder: DeclarationIrBuilder,
        left: IrExpression,
        right: IrExpression,
    ): IrExpression = builder.irCall(
        builder.context.irBuiltIns.lessFunByOperandType.getValue(builder.context.irBuiltIns.intClass),
    ).apply {
        arguments[0] = left
        arguments[1] = right
    }

    private fun intPlus(
        builder: DeclarationIrBuilder,
        left: IrExpression,
        right: IrExpression,
    ): IrExpression = builder.irCall(builder.context.irBuiltIns.intPlusSymbol).apply {
        arguments[0] = left
        arguments[1] = right
    }

    /**
     * Keeps the Kotlin String as the pin owner and lets the Native recipe thunk build the
     * temporary fast-pass HSTRING header on its own stack. No frame pool, header allocation, or
     * exception/finally scope is needed for this input shape.
     */
    private fun emitDirectHStringInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        value: IrExpression,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression = builder.irBlock(resultType = function.returnType) {
        val length = irTemporary(
            builder.irCall(winRTStringLength).apply { arguments[0] = value },
            nameHint = "hstringLength",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val pinned = irTemporary(
            builder.irCall(winRTPinString).apply {
                arguments[0] = value
                arguments[1] = builder.irGet(length)
            },
            nameHint = "pinnedHString",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val address = builder.irCall(winRTStringAddress).apply {
            arguments[0] = builder.irGet(pinned)
            arguments[1] = builder.irGet(length)
        }
        val downstream = continuation(
            PreparedInput(
                // The target HSTRING value is synthesized by the thunk. Keep an address-shaped
                // placeholder so the ordinary ABI carrier vector remains one value wide.
                abiValues = listOf(address),
                directInputs = listOf(
                    WinRTDirectCallInput(
                        carrier = WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                        kind = WinRTDirectCallInputKind.HSTRING,
                        words = listOf(address, builder.irGet(length)),
                    ),
                ),
                keepAliveOwners = listOf(builder.irGet(pinned)),
            ),
        ) ?: abortCallSiteLowering()
        +downstream
    }

    private fun emitHStringInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression = builder.irBlock(resultType = function.returnType) {
        val frame = irTemporary(
            builder.irCall(acquireHStringReferenceFrame).apply { arguments[0] = value },
            nameHint = "hstringFrame",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val handle = builder.irCall(hStringReferenceFrameHandleGetter).apply { arguments[0] = builder.irGet(frame) }
        +builder.irTry(
            type = function.returnType,
            tryResult = continuation(PreparedInput(listOf(handle))) ?: abortCallSiteLowering(),
            catches = emptyList(),
            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                +builder.irCall(hStringReferenceFrameClose).apply { arguments[0] = builder.irGet(frame) }
            },
        )
    }

    private fun emitGuidInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? = emitStructFrame(builder, function, recipe, clear = false, pluginContext) { frame, pointer ->
        val writeGuid = platformAbiWriteGuid ?: return@emitStructFrame null
        builder.irBlock(resultType = function.returnType) {
            +builder.irCall(writeGuid).apply {
                arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                arguments[1] = pointer
                arguments[2] = value
            }
            +(continuation(PreparedInput(listOf(pointer))) ?: abortCallSiteLowering())
        }
    }

    private fun emitArrayInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? {
        if (recipe.callables != null) return null
        val elementRecipe = recipe.children.singleOrNull() ?: return null
        val arrayType = value.type as? IrSimpleType ?: return null
        val elementType = arrayType.arguments.singleOrNull()?.typeOrNull ?: return null
        val arrayClass = arrayType.classOrNull ?: return null
        val sizeGetter = arrayClass.propertyGetter("size") ?: return null
        val getElement = arrayClass.functionNamedWithRegularParameterCount("get", 1) ?: return null
        val elementSize = arrayElementSizeBytes(elementRecipe) ?: return null
        val elementAlignment = arrayElementAlignmentBytes(elementRecipe) ?: return null
        val needsCleanup = arrayElementNeedsCleanup(elementRecipe)

        return builder.irBlock(resultType = function.returnType) {
            val stableArray = irTemporary(
                value,
                nameHint = "inputArray",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val length = irTemporary(
                resolver.memberCall(builder, sizeGetter, builder.irGet(stableArray), emptyList()),
                nameHint = "inputArrayLength",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val array = irTemporary(
                objectCall(
                    builder,
                    winRTAbiArrayAllocateInput,
                    listOf(
                        builder.irGet(length),
                        builder.irInt(elementSize),
                        builder.irInt(elementAlignment),
                    ),
                ),
                nameHint = "inputAbiArray",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val data = irTemporary(
                resolver.memberCall(builder, winRTAbiArrayDataGetter, builder.irGet(array), emptyList()),
                nameHint = "inputArrayData",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val initialized = if (needsCleanup) {
                irTemporary(
                    builder.irInt(0),
                    nameHint = "initializedArrayElements",
                    isMutable = true,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
            } else {
                null
            }
            val encode = builder.irBlock(resultType = function.returnType) {
                val index = irTemporary(
                    builder.irInt(0),
                    nameHint = "arrayIndex",
                    isMutable = true,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val loop = builder.irWhile()
                loop.condition = intLessThan(builder, builder.irGet(index), builder.irGet(length))
                loop.body = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    val address = arrayElementAddress(
                        builder,
                        builder.irGet(data),
                        builder.irGet(index),
                        elementSize,
                    ) ?: abortCallSiteLowering("cannot address direct array element")
                    if (initialized != null) {
                        +builder.irSet(
                            initialized.symbol,
                            intPlus(builder, builder.irGet(index), builder.irInt(1)),
                        )
                    }
                    val element = resolver.memberCall(
                        builder,
                        getElement,
                        builder.irGet(stableArray),
                        listOf(builder.irGet(index)),
                        elementType,
                    )
                    +(encodeArrayElement(builder, elementRecipe, element, address)
                        ?: abortCallSiteLowering("cannot encode direct array element ${elementRecipe.typeSignature}"))
                    +builder.irSet(index.symbol, intPlus(builder, builder.irGet(index), builder.irInt(1)))
                }
                +loop
                +(
                    continuation(
                        PreparedInput(
                            abiValues = listOf(builder.irGet(length), builder.irGet(data)),
                            keepAliveOwners = listOf(builder.irGet(stableArray)),
                        ),
                    ) ?: abortCallSiteLowering()
                )
            }
            val close = resolver.memberCall(builder, winRTAbiArrayClose, builder.irGet(array), emptyList())
            val cleanup = initialized?.let { count ->
                cleanupArrayElements(
                    builder = builder,
                    elementRecipe = elementRecipe,
                    data = builder.irGet(data),
                    length = builder.irGet(count),
                    elementSize = elementSize,
                    pluginContext = pluginContext,
                ) ?: abortCallSiteLowering("cannot clean direct input array elements")
            }
            val cleanupAndClose = if (cleanup == null) {
                close
            } else {
                emitCleanupSequence(
                    builder = builder,
                    function = function,
                    actions = listOf(cleanup, close),
                    pluginContext = pluginContext,
                    namePrefix = "inputArrayCleanup",
                ) ?: abortCallSiteLowering("cannot lower direct input array cleanup")
            }
            +builder.irTry(
                type = function.returnType,
                tryResult = encode,
                catches = emptyList(),
                finallyExpression = cleanupAndClose,
            )
        }
    }

    private fun emitStructInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? {
        val callables = recipe.callables ?: return null
        if (recipe.abiCarriers.size == 1 &&
            recipe.abiCarriers.single() != WinRTProjectionCallSiteAbiCarrier.ADDRESS &&
            callables.toAbi.isNotBlank()
        ) {
            val abi = resolver.call(builder, callables.ownerFqName, callables.toAbi, listOf(value)) ?: return null
            return continuation(
                PreparedInput(listOf(normalizeCarrier(builder, recipe.abiCarriers.single(), abi) ?: return null)),
            )
        }
        if (callables.copyToAbi.isBlank()) return null
        return emitStructFrame(builder, function, recipe, clear = false, pluginContext) { frame, pointer ->
            val copy = resolver.call(
                builder,
                callables.ownerFqName,
                callables.copyToAbi,
                listOf(value, pointer),
            ) ?: return@emitStructFrame null
            val carrier = structCarrier(builder, frame, pointer, recipe.abiCarriers.singleOrNull() ?: return@emitStructFrame null)
                ?: return@emitStructFrame null
            builder.irBlock(resultType = function.returnType) {
                +copy
                val downstream = continuation(PreparedInput(listOf(carrier))) ?: abortCallSiteLowering()
                if (callables.disposeAbi.isBlank()) {
                    +downstream
                } else {
                    +builder.irTry(
                        type = function.returnType,
                        tryResult = downstream,
                        catches = emptyList(),
                        finallyExpression = resolver.call(
                            builder,
                            callables.ownerFqName,
                            callables.disposeAbi,
                            listOf(pointer),
                        ) ?: abortCallSiteLowering(),
                    )
                }
            }
        }
    }

    private fun emitProjectionInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        allowNativeDirectHString: Boolean,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? {
        val callables = recipe.callables ?: return null
        if (callables.createMarshaler.isNotBlank()) {
            if (recipe.children.singleOrNull()?.referenceAccess ==
                WinRTProjectionCallSiteReferenceAccess.MANAGED_INSPECTABLE
            ) {
                return emitManagedInspectableFactoryInput(
                    builder = builder,
                    function = function,
                    recipe = recipe,
                    value = value,
                    callables = callables,
                    pluginContext = pluginContext,
                    continuation = continuation,
                )
            }
            return emitFactoryInput(
                builder,
                function,
                recipe,
                value,
                callables,
                pluginContext,
                continuation,
            )
        }
        if (callables.toAbi.isNotBlank()) {
            if (recipe.abiCarriers.size != 1) return null
            return builder.irBlock(resultType = function.returnType) {
                val stableValue = irTemporary(
                    value,
                    nameHint = "projectedInput",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val abi = irTemporary(
                    resolver.call(
                        builder,
                        callables.ownerFqName,
                        callables.toAbi,
                        listOf(builder.irGet(stableValue)),
                    ) ?: abortCallSiteLowering(),
                    nameHint = "projectedAbi",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val normalized = normalizeCarrier(
                    builder,
                    recipe.abiCarriers.single(),
                    builder.irGet(abi),
                ) ?: abortCallSiteLowering()
                val owners = if (recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE) {
                    listOf(builder.irGet(stableValue))
                } else {
                    emptyList()
                }
                +(continuation(PreparedInput(listOf(normalized), keepAliveOwners = owners)) ?: abortCallSiteLowering())
            }
        }
        return emitInputRecipe(
            builder,
            function,
            recipe.children.single(),
            value,
            pluginContext,
            allowNativeDirectHString,
            continuation,
        )
    }

    /**
     * Lower an inspectable managed-object input as borrow-first/owned-fallback.  The ABI fact is
     * supplied by generated WinMD metadata; the concrete branch and keep-alive are emitted here so
     * runtime-owned and module/intrinsic call sites share exactly the same fast path.
     */
    private fun emitManagedInspectableFactoryInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        callables: WinRTProjectionCallSiteCallables,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? {
        val platformAbi = platformAbi ?: return null
        val isNullPointer = platformAbiIsNullPointer ?: return null
        val nullPointer = platformAbiStaticProperty(builder, platformAbiNullPointerGetter)
            ?: return null
        return builder.irBlock(resultType = function.returnType) {
            val stableValue = irTemporary(
                value,
                nameHint = "managedInspectableInput",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )

            fun invokeWith(address: IrExpression, keepAlive: Boolean): IrExpression {
                val owners = if (keepAlive) listOf(builder.irGet(stableValue)) else emptyList()
                return continuation(
                    PreparedInput(
                        abiValues = listOf(address),
                        keepAliveOwners = owners,
                    ),
                ) ?: abortCallSiteLowering()
            }

            fun borrowOrFallback(): IrExpression = builder.irBlock(resultType = function.returnType) {
                val borrowedAbi = irTemporary(
                    resolver.topLevelCall(
                        builder,
                        tryBorrowWinRTManagedInspectableAbi,
                        listOf(builder.irGet(stableValue)),
                    ),
                    nameHint = "borrowedInspectableAbi",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val borrowMiss = builder.irCall(isNullPointer).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = builder.irGet(borrowedAbi)
                }
                +builder.irIfThenElse(
                    type = function.returnType,
                    condition = borrowMiss,
                    thenPart = emitFactoryInput(
                        builder = builder,
                        function = function,
                        recipe = recipe,
                        value = builder.irGet(stableValue),
                        callables = callables,
                        pluginContext = pluginContext,
                        continuation = continuation,
                    ) ?: abortCallSiteLowering(),
                    elsePart = invokeWith(builder.irGet(borrowedAbi), keepAlive = true),
                )
            }

            if (recipe.nullable) {
                +builder.irIfNull(
                    type = function.returnType,
                    subject = builder.irGet(stableValue),
                    thenPart = invokeWith(nullPointer, keepAlive = false),
                    elsePart = borrowOrFallback(),
                )
            } else {
                +borrowOrFallback()
            }
        }
    }

    private fun emitFactoryInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        callables: WinRTProjectionCallSiteCallables,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? {
        val factoryCall = resolver.call(
            builder,
            callables.ownerFqName,
            callables.createMarshaler,
            listOf(value),
        ) ?: return null
        val factoryClass = factoryCall.type.classOrNull ?: return null
        val propertyNames = listOf(callables.carrierProperty) + callables.extraCarrierProperties
        if (propertyNames.size != recipe.abiCarriers.size) return null
        return builder.irBlock(resultType = function.returnType) {
            val marshaler = irTemporary(
                factoryCall,
                nameHint = "marshaler",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val carrierValues = propertyNames.mapIndexed { index, propertyName ->
                val getter = factoryClass.propertyGetter(propertyName) ?: abortCallSiteLowering()
                val nonNullReceiver = builder.irAs(builder.irGet(marshaler), factoryClass.owner.defaultType)
                val property = resolver.memberCall(builder, getter, nonNullReceiver, emptyList())
                val selected = if (factoryCall.type.isNullable()) {
                    builder.irIfNull(
                        type = property.type,
                        subject = builder.irGet(marshaler),
                        thenPart = zeroValue(builder, property.type) ?: abortCallSiteLowering(),
                        elsePart = property,
                    )
                } else {
                    property
                }
                normalizeCarrier(builder, recipe.abiCarriers[index], selected) ?: abortCallSiteLowering()
            }
            val postCall = callables.copyFromAbi.takeIf(String::isNotBlank)?.let { copyName ->
                {
                    resolver.call(
                        builder,
                        callables.ownerFqName,
                        copyName,
                        listOf(builder.irGet(marshaler), value),
                    )
                }
            }
            val downstream = continuation(PreparedInput(abiValues = carrierValues, postCall = postCall)) ?: abortCallSiteLowering()
            +builder.irTry(
                type = function.returnType,
                tryResult = downstream,
                catches = emptyList(),
                finallyExpression = closeFactoryMarshaler(
                    builder,
                    marshaler,
                    factoryClass,
                    callables.closeMarshaler,
                    pluginContext,
                ) ?: abortCallSiteLowering(),
            )
        }
    }

    private fun closeFactoryMarshaler(
        builder: DeclarationIrBuilder,
        marshaler: IrVariable,
        factoryClass: IrClassSymbol,
        closeName: String,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        val close = factoryClass.functionNamedWithRegularParameterCount(closeName, 0) ?: return null
        val receiver = builder.irAs(builder.irGet(marshaler), factoryClass.owner.defaultType)
        val call = resolver.memberCall(builder, close, receiver, emptyList())
        return if (marshaler.type.isNullable()) {
            builder.irIfNull(
                type = pluginContext.irBuiltIns.unitType,
                subject = builder.irGet(marshaler),
                thenPart = builder.irUnit(),
                elsePart = call,
            )
        } else {
            call
        }
    }

    private fun emitOutputStorage(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        clear: Boolean,
        pluginContext: IrPluginContext,
        continuation: (OutputStorage) -> IrExpression?,
    ): IrExpression? {
        val storageRecipe = recipe.storageRecipe
        return if (storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.STRUCT ||
            storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.GUID
        ) {
            emitStructFrame(builder, function, storageRecipe, clear, pluginContext) { frame, pointer ->
                continuation(OutputStorage(listOf(pointer), emptyList(), frame))
            }
        } else {
            emitScalarOutputFrames(
                builder,
                function,
                count = recipe.abiCarriers.size,
                clear = clear,
                pluginContext = pluginContext,
                frames = emptyList(),
                continuation = continuation,
            )
        }
    }

    private fun emitScalarOutputFrames(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        count: Int,
        clear: Boolean,
        pluginContext: IrPluginContext,
        frames: List<IrVariable>,
        continuation: (OutputStorage) -> IrExpression?,
    ): IrExpression? {
        if (frames.size == count) {
            val addresses = frames.map { frame ->
                builder.irCall(scalarScratchFramePointerGetter).apply { arguments[0] = builder.irGet(frame) }
            }
            return continuation(OutputStorage(addresses, frames, null))
        }
        return builder.irBlock(resultType = function.returnType) {
            val frame = irTemporary(
                builder.irCall(acquireScalarScratchFrame).apply { arguments[0] = builder.irBoolean(clear) },
                nameHint = "resultFrame${frames.size}",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            +builder.irTry(
                type = function.returnType,
                tryResult = emitScalarOutputFrames(
                    builder,
                    function,
                    count,
                    clear,
                    pluginContext,
                    frames + frame,
                    continuation,
                ) ?: abortCallSiteLowering(),
                catches = emptyList(),
                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irCall(scalarScratchFrameClose).apply { arguments[0] = builder.irGet(frame) }
                },
            )
        }
    }

    private fun emitStructFrame(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        clear: Boolean,
        pluginContext: IrPluginContext,
        continuation: (IrVariable, IrExpression) -> IrExpression?,
    ): IrExpression? {
        val acquire = acquireStructScratchFrame ?: return null
        val pointerGetter = structScratchFramePointerGetter ?: return null
        val close = structScratchFrameClose ?: return null
        return builder.irBlock(resultType = function.returnType) {
            val frame = irTemporary(
                builder.irCall(acquire).apply {
                    arguments[0] = builder.irLong(recipe.sizeBytes.toLong())
                    arguments[1] = builder.irLong(recipe.alignmentBytes.toLong())
                    arguments[2] = builder.irBoolean(clear)
                },
                nameHint = "structFrame",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val pointer = builder.irCall(pointerGetter).apply { arguments[0] = builder.irGet(frame) }
            +builder.irTry(
                type = function.returnType,
                tryResult = continuation(frame, pointer) ?: abortCallSiteLowering(),
                catches = emptyList(),
                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irCall(close).apply { arguments[0] = builder.irGet(frame) }
                },
            )
        }
    }

    private fun emitInvocation(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        callerOwnedConstructor: IrConstructorSymbol?,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
        state: LoweringState,
    ): IrExpression? {
        val carriers = descriptor.slots.flatMap(WinRTProjectionCallSiteSlot::abiCarriers)
        if (carriers.size != state.abiArguments.size) return null
        val directInputs = state.directInputs.takeIf { inputs -> inputs.size == carriers.size }
            ?: standardDirectInputs(carriers, state.abiArguments)
        if (canUseSimpleResultInvocation(descriptor, state)) {
            return emitSimpleResultInvocation(
                builder = builder,
                function = function,
                descriptor = descriptor,
                parameters = parameters,
                pluginContext = pluginContext,
                state = state,
                carriers = carriers,
                directInputs = directInputs,
            )
        }
        val allocatedOwnedOutputs = state.allocatedOutputs.filter { output ->
            output.slot.ownership == WinRTProjectionCallSiteOwnership.OWNED
        }
        val owners = listOf(builder.irGet(parameters[0])) + state.keepAliveOwners
        if (allocatedOwnedOutputs.isEmpty()) {
            return emitInvocationBody(
                builder = builder,
                function = function,
                descriptor = descriptor,
                callerOwnedConstructor = callerOwnedConstructor,
                parameters = parameters,
                pluginContext = pluginContext,
                state = state,
                directInputs = directInputs,
                owners = owners,
                ownedOutputs = emptyList(),
            )
        }
        return builder.irBlock(resultType = function.returnType) {
            val ownedOutputs = allocatedOwnedOutputs.mapIndexed { index, output ->
                OwnedOutputState(
                    output = output,
                    transferred = irTemporary(
                        builder.irBoolean(false),
                        nameHint = "outputTransferred$index",
                        isMutable = true,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    ),
                    cleanupClaimed = irTemporary(
                        builder.irBoolean(false),
                        nameHint = "outputCleanupClaimed$index",
                        isMutable = true,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    ),
                )
            }
            val primaryFailure = irTemporary(
                builder.irNull(pluginContext.irBuiltIns.throwableType.makeNullable()),
                nameHint = "primaryFailure",
                isMutable = true,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            +builder.irTry(
                type = function.returnType,
                tryResult = emitInvocationBody(
                    builder = builder,
                    function = function,
                    descriptor = descriptor,
                    callerOwnedConstructor = callerOwnedConstructor,
                    parameters = parameters,
                    pluginContext = pluginContext,
                    state = state,
                    directInputs = directInputs,
                    owners = owners,
                    ownedOutputs = ownedOutputs,
                ) ?: return null,
                catches = listOf(
                    catchThrowable(builder, function, pluginContext, "callFailure") { error ->
                        builder.irBlock(resultType = function.returnType) {
                            +builder.irSet(primaryFailure.symbol, builder.irGet(error))
                            +IrThrowImpl(
                                builder.startOffset,
                                builder.endOffset,
                                pluginContext.irBuiltIns.nothingType,
                                builder.irGet(error),
                            )
                        }
                    }
                ),
                finallyExpression = emitOwnedOutputCleanup(
                    builder,
                    function,
                    ownedOutputs,
                    primaryFailure,
                    pluginContext,
                ) ?: return null,
            )
        }
    }

    private fun emitInvocationBody(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        callerOwnedConstructor: IrConstructorSymbol?,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
        state: LoweringState,
        directInputs: List<WinRTDirectCallInput>,
        owners: List<IrExpression>,
        ownedOutputs: List<OwnedOutputState>,
    ): IrExpression? = builder.irBlock(resultType = function.returnType) {
        val hResult = irTemporary(
            directCallBackend.emitDirect(
                builder = builder,
                pluginContext = pluginContext,
                ownerFunction = function,
                instance = builder.irCall(comObjectReferencePointerGetter).apply {
                    arguments[0] = builder.irGet(parameters[0])
                },
                slot = builder.irGet(parameters[1]),
                inputs = directInputs,
            ) ?: return null,
            nameHint = "hr",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        owners.forEach { owner ->
            +builder.irCall(winRTKeepAlive).apply { arguments[0] = owner }
        }
        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
            +builder.irCall(hResultRequireSuccess).apply {
                arguments[0] = builder.irCall(hResultConstructor).apply {
                    arguments[0] = builder.irGet(hResult)
                }
                arguments[1] = builder.irString("WinRT call")
            }
        }
        state.postCalls.forEach { postCall -> +(postCall() ?: return null) }
        state.outputs.forEach { output ->
            +(assignOutput(builder, function, output, ownedOutputs, pluginContext) ?: return null)
        }
        when {
            descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN -> +builder.irGet(hResult)
            callerOwnedConstructor != null -> +(
                assembleCallerOutputs(
                    builder,
                    function,
                    callerOwnedConstructor,
                    state,
                    ownedOutputs,
                    pluginContext,
                ) ?: return null
            )
            state.result != null -> +(
                decodeResult(
                    builder,
                    function,
                    function.returnType,
                    state.result,
                    ownedOutputs,
                    pluginContext,
                ) ?: return null
            )
            else -> +builder.irUnit()
        }
    }

    /**
     * Keeps single-scalar results on the small typed-getter route. An owned projection may join
     * only when its exact codec consumes ownership on every exit; all other owned outputs retain
     * the full transaction below.
     */
    private fun canUseSimpleResultInvocation(
        descriptor: WinRTProjectionCallSiteDescriptor,
        state: LoweringState,
    ): Boolean {
        val result = state.result ?: return false
        if (state.outputs.isNotEmpty() || state.callerOutputs.isNotEmpty()) {
            return false
        }
        if (state.postCalls.isNotEmpty() || state.keepAliveOwners.isNotEmpty()) return false
        if (state.allocatedOutputs.size != 1 || state.allocatedOutputs.single().slot != result.slot) return false
        if (result.slot.ownership != WinRTProjectionCallSiteOwnership.NONE &&
            !result.isSingleConsumingOwnedProjection(descriptor)
        ) {
            return false
        }
        val storage = result.storage
        return storage.scalarFrames.size == 1 && storage.structFrame == null && storage.addresses.size == 1
    }

    private fun emitSimpleResultInvocation(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        parameters: List<IrValueParameter>,
        pluginContext: IrPluginContext,
        state: LoweringState,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        directInputs: List<WinRTDirectCallInput>,
    ): IrExpression? {
        val result = state.result ?: return null
        val consumingOwnedProjection = result.isSingleConsumingOwnedProjection(descriptor)
        val instance = builder.irCall(comObjectReferencePointerGetter).apply {
            arguments[0] = builder.irGet(parameters[0])
        }
        val call = directCallBackend.emitDirect(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = function,
            instance = instance,
            slot = builder.irGet(parameters[1]),
            inputs = directInputs,
        ) ?: return null
        return builder.irBlock(resultType = function.returnType) {
            val hResult = irTemporary(
                call,
                nameHint = "hr",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            +builder.irCall(winRTKeepAlive).apply {
                arguments[0] = builder.irGet(parameters[0])
            }
            if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                val checkedHResult = builder.irCall(hResultConstructor).apply {
                    arguments[0] = builder.irGet(hResult)
                }
                if (consumingOwnedProjection) {
                    +builder.irIfThen(
                        type = pluginContext.irBuiltIns.unitType,
                        condition = resolver.memberCall(
                            builder,
                            hResultIsFailureGetter,
                            checkedHResult,
                            emptyList(),
                        ),
                        thenPart = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            +(cleanupOwnedRecipe(
                                builder,
                                function,
                                result.slot.recipe,
                                result.storage,
                                pluginContext,
                            ) ?: return null)
                            +builder.irCall(hResultRequireSuccess).apply {
                                arguments[0] = builder.irCall(hResultConstructor).apply {
                                    arguments[0] = builder.irGet(hResult)
                                }
                                arguments[1] = builder.irString("WinRT call")
                            }
                            +builder.irUnit()
                        },
                    )
                } else {
                    +builder.irCall(hResultRequireSuccess).apply {
                        arguments[0] = checkedHResult
                        arguments[1] = builder.irString("WinRT call")
                    }
                }
            }
            +(if (consumingOwnedProjection) {
                decodeDirectResult(
                    builder = builder,
                    returnType = function.returnType,
                    recipe = result.slot.recipe,
                    storage = result.storage,
                    slot = result.slot,
                    pluginContext = pluginContext,
                ) ?: return null
            } else {
                decodeResult(
                    builder = builder,
                    function = function,
                    returnType = function.returnType,
                    result = result,
                    ownedOutputs = emptyList(),
                    pluginContext = pluginContext,
                ) ?: return null
            })
        }
    }

    private fun PreparedResult.isSingleConsumingOwnedProjection(
        descriptor: WinRTProjectionCallSiteDescriptor,
    ): Boolean =
        descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK &&
            slot.ownership == WinRTProjectionCallSiteOwnership.OWNED &&
            slot.recipe.kind == WinRTProjectionCallSiteRecipeKind.PROJECTION &&
            slot.recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.COM_REFERENCE &&
            slot.recipe.callables?.fromAbiConsumesOwnedReference == true

    private fun assembleCallerOutputs(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        constructor: IrConstructorSymbol,
        state: LoweringState,
        ownedOutputs: List<OwnedOutputState>,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        return builder.irBlock(resultType = function.returnType) {
            val constructorParameters = constructor.owner.regularParameters()
            if (state.callerOutputs.size != constructorParameters.size) return null
            val values = state.callerOutputs.zip(constructorParameters).mapIndexed { index, (output, parameter) ->
                irTemporary(
                    decodeDirectResult(
                        builder = builder,
                        returnType = parameter.type,
                        recipe = output.slot.recipe,
                        storage = output.storage,
                        slot = output.slot,
                        pluginContext = pluginContext,
                    ) ?: return null,
                    nameHint = "callerOutput$index",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
            }
            val result = irTemporary(
                builder.irCall(constructor).apply {
                    constructor.owner.parameters.forEachIndexed { parameterIndex, parameter ->
                        if (parameter.kind == IrParameterKind.Regular) {
                            val regularIndex = constructor.owner.parameters.take(parameterIndex)
                                .count { candidate -> candidate.kind == IrParameterKind.Regular }
                            arguments[parameterIndex] = builder.irGet(values[regularIndex])
                        }
                    }
                },
                nameHint = "callerOutputResult",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            state.callerOutputs.forEach { output ->
                +(completeOwnedOutput(builder, function, output.slot, output.storage, ownedOutputs, pluginContext) ?: return null)
            }
            +builder.irGet(result)
        }
    }

    private fun emitOwnedOutputCleanup(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        ownedOutputs: List<OwnedOutputState>,
        primaryFailure: IrVariable,
        pluginContext: IrPluginContext,
    ): IrExpression? = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
        val throwableType = pluginContext.irBuiltIns.throwableType
        val nullableThrowableType = throwableType.makeNullable()
        val addSuppressed = resolver.topLevelFunction(FqName("kotlin"), "addSuppressed", 1)
            ?: pluginContext.irBuiltIns.throwableClass.functionNamedWithRegularParameterCount("addSuppressed", 1)
            ?: return null
        val cleanupFailure = irTemporary(
            builder.irNull(nullableThrowableType),
            nameHint = "cleanupFailure",
            isMutable = true,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        ownedOutputs.asReversed().forEachIndexed { index, output ->
            val cleanup = cleanupOwnedRecipe(
                builder,
                function,
                output.output.slot.recipe,
                output.output.storage,
                pluginContext,
            ) ?: return null
            +builder.irIfThenElse(
                type = pluginContext.irBuiltIns.unitType,
                condition = builder.irIfThenElse(
                    type = pluginContext.irBuiltIns.booleanType,
                    condition = builder.irNotEquals(builder.irGet(output.transferred), builder.irBoolean(true)),
                    thenPart = builder.irNotEquals(builder.irGet(output.cleanupClaimed), builder.irBoolean(true)),
                    elsePart = builder.irBoolean(false),
                ),
                thenPart = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irSet(output.cleanupClaimed.symbol, builder.irBoolean(true))
                    +builder.irTry(
                        type = pluginContext.irBuiltIns.unitType,
                        tryResult = cleanup,
                        catches = listOf(
                            catchThrowable(builder, function, pluginContext, "cleanupFailure$index") { error ->
                                builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    +builder.irIfThenElse(
                                        type = pluginContext.irBuiltIns.unitType,
                                        condition = builder.irNotEquals(
                                            builder.irGet(cleanupFailure),
                                            builder.irNull(nullableThrowableType),
                                        ),
                                        thenPart = resolver.memberCall(
                                            builder,
                                            addSuppressed,
                                            builder.irAs(builder.irGet(cleanupFailure), throwableType),
                                            listOf(builder.irGet(error)),
                                        ),
                                        elsePart = builder.irSet(cleanupFailure.symbol, builder.irGet(error)),
                                    )
                                    +builder.irUnit()
                                }
                            },
                        ),
                        finallyExpression = null,
                    )
                    +builder.irUnit()
                },
                elsePart = builder.irUnit(),
            )
        }
        +builder.irIfThenElse(
            type = pluginContext.irBuiltIns.unitType,
            condition = builder.irNotEquals(builder.irGet(cleanupFailure), builder.irNull(nullableThrowableType)),
            thenPart = builder.irIfThenElse(
                type = pluginContext.irBuiltIns.unitType,
                condition = builder.irNotEquals(builder.irGet(primaryFailure), builder.irNull(nullableThrowableType)),
                thenPart = resolver.memberCall(
                    builder,
                    addSuppressed,
                    builder.irAs(builder.irGet(primaryFailure), throwableType),
                    listOf(builder.irAs(builder.irGet(cleanupFailure), throwableType)),
                ),
                elsePart = IrThrowImpl(
                    builder.startOffset,
                    builder.endOffset,
                    pluginContext.irBuiltIns.nothingType,
                    builder.irAs(builder.irGet(cleanupFailure), throwableType),
                ),
            ),
            elsePart = builder.irUnit(),
        )
        +builder.irUnit()
    }

    private fun emitCleanupSequence(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        actions: List<IrExpression>,
        pluginContext: IrPluginContext,
        namePrefix: String,
    ): IrExpression? = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
        val throwableType = pluginContext.irBuiltIns.throwableType
        val nullableThrowableType = throwableType.makeNullable()
        val addSuppressed = resolver.topLevelFunction(FqName("kotlin"), "addSuppressed", 1)
            ?: pluginContext.irBuiltIns.throwableClass.functionNamedWithRegularParameterCount("addSuppressed", 1)
            ?: return null
        val cleanupFailure = irTemporary(
            builder.irNull(nullableThrowableType),
            nameHint = "${namePrefix}Failure",
            isMutable = true,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        actions.forEachIndexed { index, action ->
            +builder.irTry(
                type = pluginContext.irBuiltIns.unitType,
                tryResult = action,
                catches = listOf(
                    catchThrowable(builder, function, pluginContext, "${namePrefix}Error$index") { error ->
                        builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            +builder.irIfThenElse(
                                type = pluginContext.irBuiltIns.unitType,
                                condition = builder.irNotEquals(
                                    builder.irGet(cleanupFailure),
                                    builder.irNull(nullableThrowableType),
                                ),
                                thenPart = resolver.memberCall(
                                    builder,
                                    addSuppressed,
                                    builder.irAs(builder.irGet(cleanupFailure), throwableType),
                                    listOf(builder.irGet(error)),
                                ),
                                elsePart = builder.irSet(cleanupFailure.symbol, builder.irGet(error)),
                            )
                            +builder.irUnit()
                        }
                    },
                ),
                finallyExpression = null,
            )
        }
        +builder.irIfThenElse(
            type = pluginContext.irBuiltIns.unitType,
            condition = builder.irNotEquals(
                builder.irGet(cleanupFailure),
                builder.irNull(nullableThrowableType),
            ),
            thenPart = IrThrowImpl(
                builder.startOffset,
                builder.endOffset,
                pluginContext.irBuiltIns.nothingType,
                builder.irAs(builder.irGet(cleanupFailure), throwableType),
            ),
            elsePart = builder.irUnit(),
        )
        +builder.irUnit()
    }

    private fun catchThrowable(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        pluginContext: IrPluginContext,
        name: String,
        result: (IrVariable) -> IrExpression,
    ): IrCatch {
        val parameter = IrVariableImpl(
            null,
            builder.startOffset,
            builder.endOffset,
            IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier(name),
            pluginContext.irBuiltIns.throwableType,
            IrVariableSymbolImpl(),
            isVar = false,
            isConst = false,
            isLateinit = false,
        ).apply { parent = function }
        return IrCatchImpl(builder.startOffset, builder.endOffset, parameter).apply {
            this.result = result(parameter)
        }
    }

    private fun assignOutput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        output: PreparedOutput,
        ownedOutputs: List<OwnedOutputState>,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        val decoded = decodeDirectResult(
            builder = builder,
            returnType = output.projectedType,
            recipe = output.slot.recipe,
            storage = output.storage,
            slot = output.slot,
            pluginContext = pluginContext,
        ) ?: return null
        return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
            val value = irTemporary(
                decoded,
                nameHint = "outputValue",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val valueSetter = output.holder.type.classOrNull?.propertySetter("value")
                ?: return null
            +resolver.memberCall(
                builder = builder,
                function = valueSetter,
                receiver = output.holder,
                arguments = listOf(builder.irGet(value)),
            )
            +(completeOwnedOutput(builder, function, output.slot, output.storage, ownedOutputs, pluginContext) ?: return null)
            +builder.irUnit()
        }
    }

    private fun cleanupOwnedOutput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        output: PreparedOutput,
        pluginContext: IrPluginContext,
    ): IrExpression? = cleanupOwnedRecipe(builder, function, output.slot.recipe, output.storage, pluginContext)

    private fun cleanupOwnedRecipe(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        pluginContext: IrPluginContext,
    ): IrExpression? =
        when (recipe.kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE,
            WinRTProjectionCallSiteRecipeKind.GUID,
            WinRTProjectionCallSiteRecipeKind.ENUM -> builder.irUnit()
            WinRTProjectionCallSiteRecipeKind.HSTRING -> {
                val handle = scalarRead(builder, recipe, storage) ?: return null
                objectCall(builder, nativeStringMarshallerDisposeAbi, listOf(handle))
            }
            WinRTProjectionCallSiteRecipeKind.STRUCT -> {
                val callables = recipe.callables ?: return null
                if (callables.disposeAbi.isBlank()) {
                    builder.irUnit()
                } else {
                    resolver.call(
                        builder,
                        callables.ownerFqName,
                        callables.disposeAbi,
                        listOf(storage.addresses.singleOrNull() ?: return null),
                    )
                }
            }
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> {
                val pointer = scalarRead(builder, recipe, storage) ?: return null
                val isNull = platformAbiIsNullPointer ?: return null
                builder.irIfThenElse(
                    type = pluginContext.irBuiltIns.unitType,
                    condition = builder.irCall(isNull).apply {
                        arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                        arguments[1] = pointer
                    },
                    thenPart = builder.irUnit(),
                    elsePart = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +objectCall(builder, winRTPlatformApiReleaseRaw, listOf(pointer))
                        +builder.irUnit()
                    },
                )
            }
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
                val callables = recipe.callables ?: return null
                if (callables.disposeAbi.isBlank()) {
                    cleanupOwnedRecipe(builder, function, recipe.children.single(), storage, pluginContext)
                } else {
                    val abiValue = rawStoredValue(builder, recipe.children.single(), storage) ?: return null
                    resolver.call(builder, callables.ownerFqName, callables.disposeAbi, listOf(abiValue))
                }
            }
            WinRTProjectionCallSiteRecipeKind.ARRAY -> {
                val callables = recipe.callables
                if (callables == null) {
                    cleanupOwnedDirectArray(builder, function, recipe, storage, pluginContext)
                } else {
                    if (callables.disposeAbi.isBlank() || storage.addresses.size != recipe.abiCarriers.size) return null
                    resolver.call(builder, callables.ownerFqName, callables.disposeAbi, storage.addresses)
                }
            }
        }

    private fun rawStoredValue(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
    ): IrExpression? =
        when (recipe.kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE,
            WinRTProjectionCallSiteRecipeKind.HSTRING,
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> scalarRead(builder, recipe, storage)
            WinRTProjectionCallSiteRecipeKind.GUID,
            WinRTProjectionCallSiteRecipeKind.STRUCT -> storage.addresses.singleOrNull()
            WinRTProjectionCallSiteRecipeKind.ENUM,
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> rawStoredValue(
                builder,
                recipe.children.single(),
                storage,
            )
            WinRTProjectionCallSiteRecipeKind.ARRAY -> null
        }

    private fun objectCall(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunctionSymbol,
        arguments: List<IrExpression>,
    ): IrExpression {
        val owner = function.owner.parent as IrClass
        return resolver.memberCall(builder, function, builder.irGetObject(owner.symbol), arguments)
    }

    private fun decodeResult(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        returnType: IrType,
        result: PreparedResult,
        ownedOutputs: List<OwnedOutputState>,
        pluginContext: IrPluginContext,
    ): IrExpression? = builder.irBlock(resultType = returnType) {
        val value = irTemporary(
            decodeDirectResult(builder, returnType, result.slot.recipe, result.storage, result.slot, pluginContext)
                ?: return null,
            nameHint = "resultValue",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        +(completeOwnedOutput(builder, function, result.slot, result.storage, ownedOutputs, pluginContext) ?: return null)
        +builder.irGet(value)
    }

    private fun completeOwnedOutput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        slot: WinRTProjectionCallSiteSlot,
        storage: OutputStorage,
        ownedOutputs: List<OwnedOutputState>,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        val ownedOutput = ownedOutputs.singleOrNull { output -> output.output.storage === storage }
            ?: return builder.irUnit()
        return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
            if (slot.recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY ||
                slot.recipe.storageRecipe.kind == WinRTProjectionCallSiteRecipeKind.STRUCT
            ) {
                +builder.irSet(ownedOutput.cleanupClaimed.symbol, builder.irBoolean(true))
                +(cleanupOwnedRecipe(builder, function, slot.recipe, storage, pluginContext) ?: return null)
            }
            +builder.irSet(ownedOutput.transferred.symbol, builder.irBoolean(true))
            +builder.irUnit()
        }
    }

    private fun decodeDirectResult(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        slot: WinRTProjectionCallSiteSlot,
        pluginContext: IrPluginContext,
    ): IrExpression? =
        when (recipe.kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE ->
                primitiveSymbols.fromAbi(builder, recipe, returnType, scalarRead(builder, recipe, storage) ?: return null)
            WinRTProjectionCallSiteRecipeKind.HSTRING -> {
                val frame = storage.scalarFrames.singleOrNull() ?: return null
                builder.irCall(scalarScratchFrameConsumeOwnedHString).apply { arguments[0] = builder.irGet(frame) }
            }
            WinRTProjectionCallSiteRecipeKind.GUID -> {
                val readGuid = platformAbiReadGuid ?: return null
                builder.irCall(readGuid).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = storage.addresses.single()
                }
            }
            WinRTProjectionCallSiteRecipeKind.ENUM -> {
                val child = recipe.children.single()
                val callables = recipe.callables ?: return null
                val fromAbi = resolver.function(callables.ownerFqName, callables.fromAbi, 1) ?: return null
                val childProjectedType = fromAbi.owner.regularParameters().singleOrNull()?.type ?: return null
                val raw = decodeDirectResult(builder, childProjectedType, child, storage, slot, pluginContext)
                    ?: return null
                emitEnumFromAbi(builder, returnType, raw) ?: resolver.call(builder, fromAbi, listOf(raw))
            }
            WinRTProjectionCallSiteRecipeKind.STRUCT -> decodeStructResult(builder, returnType, recipe, storage, slot)
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE ->
                decodeComReferenceResult(builder, returnType, recipe, storage)
            WinRTProjectionCallSiteRecipeKind.ARRAY -> {
                val callables = recipe.callables
                if (callables == null) {
                    decodeDirectArrayResult(builder, returnType, recipe, storage, pluginContext)
                } else {
                    if (callables.fromAbi.isBlank()) return null
                    resolver.call(builder, callables.ownerFqName, callables.fromAbi, storage.addresses)
                }
            }
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
                val callables = recipe.callables ?: return null
                if (callables.fromAbi.isBlank()) return null
                callables.fromAbiSymbol?.let { fromAbi ->
                    return decodeDirectProjectionResult(
                        builder = builder,
                        returnType = returnType,
                        recipe = recipe,
                        storage = storage,
                        slot = slot,
                        pluginContext = pluginContext,
                        fromAbi = fromAbi,
                    )
                }
                val rawAddressType = resolver.classSymbol(WINRT_RAW_ADDRESS_FQ_NAME)?.owner?.defaultType ?: return null
                val raw = decodeDirectResult(
                    builder,
                    rawAddressType,
                    recipe.children.single(),
                    storage,
                    slot,
                    pluginContext,
                ) ?: return null
                resolver.call(builder, callables.ownerFqName, callables.fromAbi, listOf(raw))
                    ?.let { expression ->
                        if (expression.type == returnType) expression else builder.irAs(expression, returnType)
                    }
            }
        }

    private fun decodeDirectArrayResult(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        val addresses = storage.addresses.takeIf { it.size == 2 } ?: return null
        val elementRecipe = recipe.children.singleOrNull() ?: return null
        val arrayType = returnType as? IrSimpleType ?: return null
        if (arrayType.classFqName != KOTLIN_ARRAY_FQ_NAME) return null
        val elementType = arrayType.arguments.singleOrNull()?.typeOrNull ?: return null
        val arrayClass = arrayType.classOrNull ?: return null
        val setElement = arrayClass.functionNamedWithRegularParameterCount("set", 2) ?: return null
        val elementSize = arrayElementSizeBytes(elementRecipe) ?: return null
        val error = resolver.topLevelFunction(FqName("kotlin"), "error", 1) ?: return null
        val isNull = platformAbiIsNullPointer ?: return null
        val readLength = platformAbiReadInt32 ?: return null
        val readData = platformAbiReadPointer ?: return null

        return builder.irBlock(resultType = returnType) {
            val length = irTemporary(
                builder.irCall(readLength).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = addresses[0]
                },
                nameHint = "resultArrayLength",
                isMutable = false,
            )
            val data = irTemporary(
                builder.irCall(readData).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = addresses[1]
                },
                nameHint = "resultArrayData",
                isMutable = false,
            )
            +builder.irIfThen(
                type = pluginContext.irBuiltIns.unitType,
                condition = intLessThan(builder, builder.irGet(length), builder.irInt(0)),
                thenPart = resolver.topLevelCall(
                    builder,
                    error,
                    listOf(builder.irString("WinRT returned a negative array length.")),
                ),
            )
            +builder.irIfThen(
                type = pluginContext.irBuiltIns.unitType,
                condition = intLessThan(builder, builder.irInt(0), builder.irGet(length)),
                thenPart = builder.irIfThen(
                    type = pluginContext.irBuiltIns.unitType,
                    condition = builder.irCall(isNull).apply {
                        arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                        arguments[1] = builder.irGet(data)
                    },
                    thenPart = resolver.topLevelCall(
                        builder,
                        error,
                        listOf(builder.irString("WinRT returned a null array buffer with a non-zero length.")),
                    ),
                ),
            )
            val result = irTemporary(
                builder.irCall(pluginContext.irBuiltIns.arrayOfNulls, returnType).apply {
                    typeArguments[0] = elementType
                    arguments[0] = builder.irGet(length)
                },
                nameHint = "resultArray",
                isMutable = false,
            )
            val index = irTemporary(builder.irInt(0), nameHint = "arrayIndex", isMutable = true)
            val loop = builder.irWhile()
            loop.condition = intLessThan(builder, builder.irGet(index), builder.irGet(length))
            loop.body = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val address = arrayElementAddress(
                    builder,
                    builder.irGet(data),
                    builder.irGet(index),
                    elementSize,
                ) ?: abortCallSiteLowering("cannot address direct output array element")
                val element = decodeArrayElement(builder, elementRecipe, elementType, address)
                    ?: abortCallSiteLowering("cannot decode direct array element ${elementRecipe.typeSignature}")
                +resolver.memberCall(
                    builder,
                    setElement,
                    builder.irGet(result),
                    listOf(builder.irGet(index), element),
                )
                +builder.irSet(index.symbol, intPlus(builder, builder.irGet(index), builder.irInt(1)))
            }
            +loop
            +builder.irGet(result)
        }
    }

    private fun cleanupOwnedDirectArray(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        pluginContext: IrPluginContext,
    ): IrExpression? {
        val addresses = storage.addresses.takeIf { it.size == 2 } ?: return null
        val elementRecipe = recipe.children.singleOrNull() ?: return null
        val elementSize = arrayElementSizeBytes(elementRecipe) ?: return null
        val readLength = platformAbiReadInt32 ?: return null
        val readData = platformAbiReadPointer ?: return null
        val isNull = platformAbiIsNullPointer ?: return null
        return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
            val length = irTemporary(
                builder.irCall(readLength).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = addresses[0]
                },
                nameHint = "ownedArrayLength",
                isMutable = false,
            )
            val data = irTemporary(
                builder.irCall(readData).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = addresses[1]
                },
                nameHint = "ownedArrayData",
                isMutable = false,
            )
            val cleanupElements = if (arrayElementNeedsCleanup(elementRecipe)) {
                cleanupArrayElements(
                    builder = builder,
                    elementRecipe = elementRecipe,
                    data = builder.irGet(data),
                    length = builder.irGet(length),
                    elementSize = elementSize,
                    pluginContext = pluginContext,
                ) ?: abortCallSiteLowering("cannot clean direct output array elements")
            } else {
                builder.irUnit()
            }
            val free = objectCall(
                builder,
                winRTPlatformApiCoTaskMemFreeRaw,
                listOf(builder.irGet(data)),
            )
            +builder.irIfThenElse(
                type = pluginContext.irBuiltIns.unitType,
                condition = builder.irCall(isNull).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = builder.irGet(data)
                },
                thenPart = builder.irUnit(),
                elsePart = emitCleanupSequence(
                    builder = builder,
                    function = function,
                    actions = listOf(cleanupElements, free),
                    pluginContext = pluginContext,
                    namePrefix = "ownedArrayCleanup",
                ) ?: return null,
            )
        }
    }

    private fun decodeStructResult(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        slot: WinRTProjectionCallSiteSlot,
    ): IrExpression? {
        val callables = recipe.callables ?: abortCallSiteLowering("struct result has no callable contract")
        val pointer = storage.addresses.singleOrNull()
            ?: abortCallSiteLowering("struct result has ${storage.addresses.size} storage addresses instead of one")
        val decode = if (recipe.abiCarriers.singleOrNull() != WinRTProjectionCallSiteAbiCarrier.ADDRESS &&
            callables.fromAbiCarrier.isNotBlank()
        ) {
            val raw = structCarrier(
                builder,
                storage.structFrame ?: abortCallSiteLowering("struct result has no scratch frame"),
                pointer,
                recipe.abiCarriers.single(),
            ) ?: abortCallSiteLowering("struct result cannot read ${recipe.abiCarriers.single()} carrier")
            resolver.call(builder, callables.ownerFqName, callables.fromAbiCarrier, listOf(raw))
        } else {
            resolver.call(builder, callables.ownerFqName, callables.fromAbi, listOf(pointer))
        } ?: abortCallSiteLowering(
            "struct result cannot call ${callables.ownerFqName}." +
                if (recipe.abiCarriers.singleOrNull() != WinRTProjectionCallSiteAbiCarrier.ADDRESS &&
                    callables.fromAbiCarrier.isNotBlank()
                ) {
                    callables.fromAbiCarrier
                } else {
                    callables.fromAbi
                },
        )
        return if (decode.type == returnType) decode else builder.irAs(decode, returnType)
    }

    private fun decodeDirectProjectionResult(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
        slot: WinRTProjectionCallSiteSlot,
        pluginContext: IrPluginContext,
        fromAbi: IrSimpleFunctionSymbol,
    ): IrExpression? {
        val child = recipe.children.singleOrNull() ?: return null
        val parameterType = fromAbi.owner.regularParameters().singleOrNull()?.type ?: return null
        val rawAddressType = resolver.classSymbol(WINRT_RAW_ADDRESS_FQ_NAME)?.owner?.defaultType ?: return null
        val rawAddress = decodeDirectResult(
            builder,
            rawAddressType,
            child,
            storage,
            slot,
            pluginContext,
        ) ?: return null
        val isNull = platformAbiIsNullPointer ?: return null
        val abi = platformAbi ?: return null
        val error = if (recipe.nullable) {
            null
        } else {
            resolver.topLevelFunction(FqName("kotlin"), "error", 1) ?: return null
        }
        return builder.irBlock(resultType = returnType) {
            val address = irTemporary(
                rawAddress,
                nameHint = "projectionAbi",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val reference = decodeComReferenceAddress(
                builder = builder,
                returnType = parameterType,
                referenceAccess = child.referenceAccess,
                address = builder.irGet(address),
            ) ?: abortCallSiteLowering("plain projection output cannot construct ${parameterType.classFqName}")
            val wrapped = resolver.call(builder, fromAbi, listOf(reference)).let { expression ->
                if (expression.type == returnType) expression else builder.irAs(expression, returnType)
            }
            val nullResult = if (recipe.nullable) {
                builder.irNull(returnType)
            } else {
                resolver.topLevelCall(
                    builder,
                    requireNotNull(error),
                    listOf(builder.irString("WINRT_E_NULL_ABI_RETURN")),
                )
            }
            +builder.irIfThenElse(
                type = returnType,
                condition = builder.irCall(isNull).apply {
                    arguments[0] = builder.irGetObject(abi)
                    arguments[1] = builder.irGet(address)
                },
                thenPart = nullResult,
                elsePart = wrapped,
            )
        }
    }

    private fun decodeComReferenceResult(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
    ): IrExpression? {
        val address = scalarRead(builder, recipe, storage) ?: return null
        return decodeComReferenceAddress(builder, returnType, recipe.referenceAccess, address)
    }

    private fun decodeComReferenceAddress(
        builder: DeclarationIrBuilder,
        returnType: IrType,
        referenceAccess: WinRTProjectionCallSiteReferenceAccess?,
        address: IrExpression,
    ): IrExpression? {
        if (returnType.classFqName == WINRT_RAW_ADDRESS_FQ_NAME) return address
        return when (referenceAccess) {
            WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS -> address
            WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR -> {
                val toRawComPtr = platformAbiToRawComPtr ?: return null
                builder.irCall(toRawComPtr).apply {
                    arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
                    arguments[1] = address
                }
            }
            WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE ->
                constructOwnedComReference(builder, WINRT_IUNKNOWN_REFERENCE_FQ_NAME, address)
            WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE ->
                constructOwnedComReference(builder, WINRT_INSPECTABLE_REFERENCE_FQ_NAME, address)
            else -> null
        }
    }

    private fun constructOwnedComReference(
        builder: DeclarationIrBuilder,
        classFqName: FqName,
        address: IrExpression,
    ): IrExpression? {
        val toRawComPtr = platformAbiToRawComPtr ?: return null
        val rawComPtr = builder.irCall(toRawComPtr).apply {
            arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
            arguments[1] = address
        }
        val referenceClass = resolver.classSymbol(classFqName) ?: return null
        val constructor = referenceClass.rawComPtrConstructor() ?: return null
        return builder.irCall(constructor).apply {
            val pointerIndex = constructor.owner.parameters.indexOfFirst { parameter ->
                parameter.kind == IrParameterKind.Regular && parameter.type.classFqName == WINRT_RAW_COM_PTR_FQ_NAME
            }
            if (pointerIndex < 0) return null
            arguments[pointerIndex] = rawComPtr
        }
    }

    private fun scalarRead(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        storage: OutputStorage,
    ): IrExpression? {
        storage.directScalarValue?.let { return it }
        val frame = storage.scalarFrames.singleOrNull()
        if (frame != null) {
            val symbol = when (recipe.valueCarrier) {
                WinRTProjectionCallSiteAbiCarrier.ADDRESS -> scalarScratchFrameReadPointer
                WinRTProjectionCallSiteAbiCarrier.INT8 -> scalarScratchFrameReadInt8
                WinRTProjectionCallSiteAbiCarrier.INT16 -> scalarScratchFrameReadInt16
                WinRTProjectionCallSiteAbiCarrier.INT32 -> scalarScratchFrameReadInt32
                WinRTProjectionCallSiteAbiCarrier.INT64 -> scalarScratchFrameReadInt64
                WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> scalarScratchFrameReadFloat
                WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> scalarScratchFrameReadDouble
                null -> return null
            }
            return builder.irCall(symbol).apply { arguments[0] = builder.irGet(frame) }
        }
        val address = storage.addresses.singleOrNull() ?: return null
        val symbol = when (recipe.valueCarrier) {
            WinRTProjectionCallSiteAbiCarrier.ADDRESS -> platformAbiReadPointer
            WinRTProjectionCallSiteAbiCarrier.INT8 -> platformAbiReadInt8
            WinRTProjectionCallSiteAbiCarrier.INT16 -> platformAbiReadInt16
            WinRTProjectionCallSiteAbiCarrier.INT32 -> platformAbiReadInt32
            WinRTProjectionCallSiteAbiCarrier.INT64 -> platformAbiReadInt64
            WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> platformAbiReadFloat
            WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> platformAbiReadDouble
            null -> return null
        } ?: return null
        val abi = platformAbi ?: return null
        return builder.irCall(symbol).apply {
            arguments[0] = builder.irGetObject(abi)
            arguments[1] = address
        }
    }

    private fun structCarrier(
        builder: DeclarationIrBuilder,
        frame: IrVariable,
        pointer: IrExpression,
        carrier: WinRTProjectionCallSiteAbiCarrier,
    ): IrExpression? {
        if (carrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS) return pointer
        val symbol = when (carrier) {
            WinRTProjectionCallSiteAbiCarrier.INT8 -> structScratchFrameReadInt8Carrier
            WinRTProjectionCallSiteAbiCarrier.INT16 -> structScratchFrameReadInt16Carrier
            WinRTProjectionCallSiteAbiCarrier.INT32 -> structScratchFrameReadInt32Carrier
            WinRTProjectionCallSiteAbiCarrier.INT64 -> structScratchFrameReadInt64Carrier
            WinRTProjectionCallSiteAbiCarrier.ADDRESS,
            WinRTProjectionCallSiteAbiCarrier.FLOAT32,
            WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> return null
        } ?: return null
        return builder.irCall(symbol).apply { arguments[0] = builder.irGet(frame) }
    }

    private fun emitComReferenceInput(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunction,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
        pluginContext: IrPluginContext,
        continuation: (PreparedInput) -> IrExpression?,
    ): IrExpression? = when (recipe.referenceAccess) {
            WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS ->
                continuation(PreparedInput(listOf(value)))
            WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR ->
                continuation(PreparedInput(listOf(rawComPtrToAddress(builder, value) ?: return null)))
            WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
            WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE -> {
                val pointer = builder.irCall(comObjectReferencePointerGetter).apply { arguments[0] = value }
                continuation(
                    PreparedInput(
                        abiValues = listOf(rawComPtrToAddress(builder, pointer) ?: return null),
                        keepAliveOwners = listOf(value),
                    ),
                )
            }
            WinRTProjectionCallSiteReferenceAccess.MANAGED_INSPECTABLE -> null
            WinRTProjectionCallSiteReferenceAccess.PROJECTED_INTERFACE -> builder.irBlock(
                resultType = function.returnType,
            ) {
                val stableValue = irTemporary(
                    value,
                    nameHint = "projectedInput",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val typeHandleGetter = recipe.projectedTypeHandleSymbol ?: return null
                val typeHandleOwner = typeHandleGetter.owner.parent as? IrClass ?: return null
                val typeHandle = irTemporary(
                    builder.irCall(typeHandleGetter).apply {
                        arguments[0] = builder.irGetObject(typeHandleOwner.symbol)
                    },
                    nameHint = "projectedTypeHandle",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val marshalerClass = winRTProjectionMarshalerAbiGetter.owner.parent as? IrClass
                    ?: return null
                val isNullPointer = platformAbiIsNullPointer ?: return null
                val platformAbi = platformAbi ?: return null
                val nullPointer = platformAbiStaticProperty(builder, platformAbiNullPointerGetter)
                    ?: return null
                val hasDirectManagedStateAccess = stableValue.type.classOrNull
                    ?.hasSupertype(WINRT_MANAGED_PROJECTION_STATE_ACCESS_FQ_NAME) == true
                val marshalerType = marshalerClass.defaultType
                val nullableMarshalerType = marshalerType.makeNullable()
                val address = irTemporary(
                    nullPointer,
                    nameHint = "projectedAbi",
                    isMutable = true,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val callLease = irTemporary(
                    builder.irNull(nullableMarshalerType),
                    nameHint = "managedProjectedCallLease",
                    isMutable = true,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val ownedMarshaler = irTemporary(
                    builder.irNull(nullableMarshalerType),
                    nameHint = "ownedProjectedMarshaler",
                    isMutable = true,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )

                fun nonNullManagedValue(): IrExpression =
                    builder.irAs(builder.irGet(stableValue), stableValue.type.makeNotNull())

                fun nonNullMarshaler(variable: IrVariable): IrExpression =
                    builder.irAs(builder.irGet(variable), marshalerType)

                fun prepareNonNullInput(): IrExpression = builder.irBlock(
                    resultType = pluginContext.irBuiltIns.unitType,
                ) {
                    val managedState = if (hasDirectManagedStateAccess) {
                        irTemporary(
                            resolver.memberCall(
                                builder,
                                winRTManagedProjectionStateAccessor,
                                nonNullManagedValue(),
                                emptyList(),
                            ),
                            nameHint = "managedProjectionState",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                    } else {
                        null
                    }
                    val leaseArguments = buildList {
                        add(builder.irGet(stableValue))
                        managedState?.let { add(builder.irGet(it)) }
                        add(builder.irGet(typeHandle))
                    }
                    +builder.irSet(
                        callLease.symbol,
                        resolver.topLevelCall(
                            builder,
                            if (managedState == null) {
                                tryAcquireWinRTManagedProjectionCallLease
                            } else {
                                tryAcquireWinRTManagedProjectionCallLeaseWithState
                            },
                            leaseArguments,
                        ),
                    )
                    +builder.irIfNull(
                        type = pluginContext.irBuiltIns.unitType,
                        subject = builder.irGet(callLease),
                        thenPart = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            val borrowedArguments = buildList {
                                add(builder.irGet(stableValue))
                                managedState?.let { add(builder.irGet(it)) }
                                add(builder.irGet(typeHandle))
                            }
                            val borrowedAbi = irTemporary(
                                resolver.topLevelCall(
                                    builder,
                                    if (managedState == null) {
                                        tryBorrowWinRTManagedProjectionAbi
                                    } else {
                                        tryBorrowWinRTManagedProjectionAbiWithState
                                    },
                                    borrowedArguments,
                                ),
                                nameHint = "borrowedProjectedAbi",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            val borrowMiss = builder.irCall(isNullPointer).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(borrowedAbi)
                            }
                            +builder.irIfThenElse(
                                type = pluginContext.irBuiltIns.unitType,
                                condition = borrowMiss,
                                thenPart = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    val marshaler = irTemporary(
                                        resolver.topLevelCall(
                                            builder,
                                            winRTProjectionMarshaler,
                                            listOf(builder.irGet(stableValue), builder.irGet(typeHandle)),
                                        ),
                                        nameHint = "projectedMarshaler",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irSet(ownedMarshaler.symbol, builder.irGet(marshaler))
                                    +builder.irSet(
                                        address.symbol,
                                        resolver.memberCall(
                                            builder,
                                            winRTProjectionMarshalerAbiGetter,
                                            builder.irGet(marshaler),
                                            emptyList(),
                                        ),
                                    )
                                },
                                elsePart = builder.irSet(address.symbol, builder.irGet(borrowedAbi)),
                            )
                        },
                        elsePart = builder.irSet(
                            address.symbol,
                            resolver.memberCall(
                                builder,
                                winRTProjectionMarshalerAbiGetter,
                                nonNullMarshaler(callLease),
                                emptyList(),
                            ),
                        ),
                    )
                }

                val preparation = if (recipe.nullable) {
                    builder.irIfNull(
                        type = pluginContext.irBuiltIns.unitType,
                        subject = builder.irGet(stableValue),
                        thenPart = builder.irUnit(),
                        elsePart = prepareNonNullInput(),
                    )
                } else {
                    prepareNonNullInput()
                }
                val downstream = continuation(
                    PreparedInput(
                        abiValues = listOf(builder.irGet(address)),
                        keepAliveOwners = listOf(builder.irGet(stableValue)),
                    ),
                ) ?: abortCallSiteLowering()
                +builder.irTry(
                    type = function.returnType,
                    tryResult = builder.irBlock(resultType = function.returnType) {
                        +preparation
                        +downstream
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irIfNull(
                        type = pluginContext.irBuiltIns.unitType,
                        subject = builder.irGet(callLease),
                        thenPart = builder.irIfNull(
                            type = pluginContext.irBuiltIns.unitType,
                            subject = builder.irGet(ownedMarshaler),
                            thenPart = builder.irUnit(),
                            elsePart = resolver.memberCall(
                                builder,
                                winRTProjectionMarshalerClose,
                                nonNullMarshaler(ownedMarshaler),
                                emptyList(),
                            ),
                        ),
                        elsePart = resolver.topLevelCall(
                            builder,
                            releaseWinRTManagedProjectionCallLease,
                            listOf(nonNullMarshaler(callLease), nonNullManagedValue()),
                        ),
                    ),
                )
            }
            WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT -> builder.irBlock(
                resultType = function.returnType,
            ) {
                val nativeObject = iWinRTObjectNativeObjectGetter ?: return null
                val receiverType = nativeObject.owner.parameters
                    .singleOrNull { parameter -> parameter.kind == IrParameterKind.DispatchReceiver }
                    ?.type ?: return null
                val stableValue = irTemporary(
                    value,
                    nameHint = "projectedInput",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                fun projectedAddress(): IrExpression {
                    val projectedValue = builder.irAs(builder.irGet(stableValue), receiverType)
                    val reference = builder.irCall(nativeObject).apply { arguments[0] = projectedValue }
                    val pointer = builder.irCall(comObjectReferencePointerGetter).apply {
                        arguments[0] = reference
                    }
                    return rawComPtrToAddress(builder, pointer) ?: abortCallSiteLowering()
                }
                val address = irTemporary(
                    if (recipe.nullable) {
                        val nullPointer = platformAbiStaticProperty(builder, platformAbiNullPointerGetter)
                            ?: return null
                        builder.irIfNull(
                            type = nullPointer.type,
                            subject = builder.irGet(stableValue),
                            thenPart = nullPointer,
                            elsePart = projectedAddress(),
                        )
                    } else {
                        projectedAddress()
                    },
                    nameHint = "projectedAbi",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +(
                    continuation(
                        PreparedInput(
                            abiValues = listOf(builder.irGet(address)),
                            keepAliveOwners = listOf(builder.irGet(stableValue)),
                        ),
                    ) ?: abortCallSiteLowering()
                )
            }
            else -> null
        }

    private fun rawComPtrToAddress(builder: DeclarationIrBuilder, value: IrExpression): IrExpression? {
        val fromRawComPtr = platformAbiFromRawComPtr ?: return null
        return builder.irCall(fromRawComPtr).apply {
            arguments[0] = builder.irGetObject(requireNotNull(platformAbi))
            arguments[1] = value
        }
    }

    private fun normalizeCarrier(
        builder: DeclarationIrBuilder,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? {
        if (carrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            return when (value.type.classFqName) {
                WINRT_RAW_ADDRESS_FQ_NAME -> value
                WINRT_RAW_COM_PTR_FQ_NAME -> rawComPtrToAddress(builder, value)
                else -> null
            }
        }
        return primitiveSymbols.normalizeCarrier(builder, carrier, value)
    }

    private fun normalizeInboundCarrier(
        builder: DeclarationIrBuilder,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? {
        if (carrier != WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            return primitiveSymbols.normalizeCarrier(builder, carrier, value)
        }
        if (value.type.classFqName == WINRT_RAW_ADDRESS_FQ_NAME) return value
        if (value.type.classFqName != KOTLIN_LONG_FQ_NAME) return null
        val rawAddress = resolver.classSymbol(WINRT_RAW_ADDRESS_FQ_NAME) ?: return null
        val constructor = rawAddress.singleValueConstructor() ?: return null
        val valueIndex = constructor.owner.parameters.indexOfFirst { parameter ->
            parameter.kind == IrParameterKind.Regular
        }
        if (valueIndex < 0) return null
        return builder.irCall(constructor).apply {
            arguments[valueIndex] = value
        }
    }

    private fun zeroValue(builder: DeclarationIrBuilder, type: IrType): IrExpression? =
        when (type.classFqName) {
            WINRT_RAW_ADDRESS_FQ_NAME -> platformAbiStaticProperty(builder, platformAbiNullPointerGetter)
            WINRT_RAW_COM_PTR_FQ_NAME -> platformAbiStaticProperty(builder, platformAbiNullComPtrGetter)
            KOTLIN_BYTE_FQ_NAME -> builder.irByte(0)
            KOTLIN_SHORT_FQ_NAME -> builder.irCall(primitiveSymbols.intToShort).apply { arguments[0] = builder.irInt(0) }
            KOTLIN_INT_FQ_NAME -> builder.irInt(0)
            KOTLIN_LONG_FQ_NAME -> builder.irLong(0L)
            KOTLIN_FLOAT_FQ_NAME -> primitiveSymbols.zeroFloat(builder)
            KOTLIN_DOUBLE_FQ_NAME -> primitiveSymbols.zeroDouble(builder)
            else -> if (type.isNullable()) builder.irNull(type) else null
        }

    private fun platformAbiStaticProperty(
        builder: DeclarationIrBuilder,
        getter: IrSimpleFunctionSymbol?,
    ): IrExpression? = getter?.let { symbol ->
        builder.irCall(symbol).apply { arguments[0] = builder.irGetObject(requireNotNull(platformAbi)) }
    }

    companion object {
        fun create(
            pluginContext: IrPluginContext,
            moduleFragment: IrModuleFragment,
        ): WinRTCallSiteRecipeLowering? {
            val fromFile = moduleFragment.files.firstOrNull()
            val sourceClasses = mutableMapOf<FqName, IrClassSymbol>()
            val sourceFunctions = mutableMapOf<CallableId, MutableList<IrSimpleFunctionSymbol>>()
            moduleFragment.acceptChildrenVoid(
                object : IrVisitorVoid() {
                    override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
                    override fun visitClass(declaration: IrClass) {
                        declaration.fqNameWhenAvailable?.let { sourceClasses[it] = declaration.symbol }
                        super.visitClass(declaration)
                    }
                    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                        if (declaration.parent is IrFile) {
                            declaration.fqNameWhenAvailable?.let { fqName ->
                                val callableId = CallableId(fqName.parent(), fqName.shortName())
                                sourceFunctions.getOrPut(callableId, ::mutableListOf) += declaration.symbol
                            }
                        }
                        super.visitSimpleFunction(declaration)
                    }
                },
            )
            val resolver = CallSiteSymbolResolver(
                pluginContext,
                fromFile,
                sourceClasses,
                sourceFunctions,
            )
            val reference = requiredCallSiteSymbol(
                "ComObjectReference",
                resolver.classSymbol(WINRT_COM_OBJECT_REFERENCE_FQ_NAME),
            ) ?: return null
            val scalarFrame = requiredCallSiteSymbol(
                "NativeScalarScratchFrame",
                resolver.classSymbol(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME),
            ) ?: return null
            val hStringFrame = requiredCallSiteSymbol(
                "NativeHStringReferenceFrame",
                resolver.classSymbol(WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME),
            ) ?: return null
            val winRTAbiArray = requiredCallSiteSymbol(
                "WinRTAbiArray",
                resolver.classSymbol(WINRT_ABI_ARRAY_FQ_NAME),
            ) ?: return null
            val winRTAbiArrayCompanion = requiredCallSiteSymbol(
                "WinRTAbiArray.Companion",
                winRTAbiArray.owner.declarations.filterIsInstance<IrClass>()
                    .singleOrNull { declaration -> declaration.name.asString() == "Companion" }
                    ?.symbol,
            ) ?: return null
            val nativeStringMarshaller = requiredCallSiteSymbol(
                "NativeStringMarshaller",
                resolver.classSymbol(WINRT_NATIVE_STRING_MARSHALLER_FQ_NAME),
            ) ?: return null
            val winRTPlatformApi = requiredCallSiteSymbol(
                "WinRTPlatformApi",
                resolver.classSymbol(WINRT_PLATFORM_API_FQ_NAME),
            ) ?: return null
            val hResult = requiredCallSiteSymbol("HResult", resolver.classSymbol(WINRT_HRESULT_FQ_NAME)) ?: return null
            val structFrame = resolver.classSymbol(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME)
            val platformAbi = resolver.classSymbol(WINRT_PLATFORM_ABI_FQ_NAME)
            val iWinRTObject = resolver.classSymbol(WINRT_IWINRT_OBJECT_FQ_NAME)
            val winRTProjectionMarshaler = requiredCallSiteSymbol(
                "WinRTProjectionMarshaler",
                resolver.classSymbol(WINRT_PROJECTION_MARSHALER_FQ_NAME),
            ) ?: return null
            return WinRTCallSiteRecipeLowering(
                directCallBackend = requiredCallSiteSymbol(
                    "WinRTDirectCallBackend",
                    WinRTDirectCallBackend.create(pluginContext, fromFile),
                ) ?: return null,
                resolver = resolver,
                comObjectReferencePointerGetter = requiredCallSiteSymbol(
                    "ComObjectReference.pointer",
                    reference.propertyGetter("pointer"),
                ) ?: return null,
                acquireScalarScratchFrame = requiredCallSiteSymbol(
                    "acquireNativeScalarScratchFrame",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "acquireNativeScalarScratchFrame", 1),
                ) ?: return null,
                scalarScratchFramePointerGetter = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.pointer",
                    scalarFrame.propertyGetter("pointer"),
                ) ?: return null,
                scalarScratchFrameConsumeOwnedHString = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.consumeOwnedHString",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "consumeOwnedHString", 0),
                ) ?: return null,
                scalarScratchFrameReadPointer = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readPointer",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readPointer", 0),
                ) ?: return null,
                scalarScratchFrameReadInt8 = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readInt8",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readInt8", 0),
                ) ?: return null,
                scalarScratchFrameReadInt16 = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readInt16",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readInt16", 0),
                ) ?: return null,
                scalarScratchFrameReadInt32 = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readInt32",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readInt32", 0),
                ) ?: return null,
                scalarScratchFrameReadInt64 = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readInt64",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readInt64", 0),
                ) ?: return null,
                scalarScratchFrameReadFloat = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readFloat",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readFloat", 0),
                ) ?: return null,
                scalarScratchFrameReadDouble = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.readDouble",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "readDouble", 0),
                ) ?: return null,
                scalarScratchFrameClose = requiredCallSiteSymbol(
                    "NativeScalarScratchFrame.close",
                    resolver.function(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME, "close", 0),
                ) ?: return null,
                acquireHStringReferenceFrame = requiredCallSiteSymbol(
                    "acquireInitializedNativeHStringReferenceFrame",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "acquireInitializedNativeHStringReferenceFrame",
                        1,
                    ),
                ) ?: return null,
                hStringReferenceFrameHandleGetter = requiredCallSiteSymbol(
                    "NativeHStringReferenceFrame.handle",
                    hStringFrame.propertyGetter("handle"),
                ) ?: return null,
                hStringReferenceFrameClose = requiredCallSiteSymbol(
                    "NativeHStringReferenceFrame.close",
                    resolver.function(WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME, "close", 0),
                ) ?: return null,
                winRTPinString = requiredCallSiteSymbol(
                    "winRTPinString",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPinString", 2),
                ) ?: return null,
                winRTStringAddress = requiredCallSiteSymbol(
                    "winRTStringAddress",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTStringAddress", 2),
                ) ?: return null,
                winRTStringLength = requiredCallSiteSymbol(
                    "winRTStringLength",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTStringLength", 1),
                ) ?: return null,
                acquireStructScratchFrame = resolver.topLevelFunction(
                    WINRT_RUNTIME_PACKAGE_FQ_NAME,
                    "acquireNativeStructScratchFrame",
                    3,
                ),
                structScratchFramePointerGetter = structFrame?.propertyGetter("pointer"),
                structScratchFrameReadInt8Carrier = structFrame?.let {
                    resolver.function(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME, "readInt8Carrier", 0)
                },
                structScratchFrameReadInt16Carrier = structFrame?.let {
                    resolver.function(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME, "readInt16Carrier", 0)
                },
                structScratchFrameReadInt32Carrier = structFrame?.let {
                    resolver.function(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME, "readInt32Carrier", 0)
                },
                structScratchFrameReadInt64Carrier = structFrame?.let {
                    resolver.function(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME, "readInt64Carrier", 0)
                },
                structScratchFrameClose = structFrame?.let {
                    resolver.function(WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME, "close", 0)
                },
                platformAbi = platformAbi,
                platformAbiNullPointerGetter = platformAbi?.propertyGetter("nullPointer"),
                platformAbiNullComPtrGetter = platformAbi?.propertyGetter("nullComPtr"),
                platformAbiFromRawComPtr = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "fromRawComPtr", 1)
                },
                platformAbiToRawComPtr = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "toRawComPtr", 1)
                },
                platformAbiReadPointer = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readPointer", 1)
                },
                platformAbiReadInt8 = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readInt8", 1)
                },
                platformAbiReadInt16 = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readInt16", 1)
                },
                platformAbiReadInt32 = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readInt32", 1)
                },
                platformAbiReadInt64 = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readInt64", 1)
                },
                platformAbiReadFloat = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readFloat", 1)
                },
                platformAbiReadDouble = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readDouble", 1)
                },
                platformAbiReadGuid = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "readGuid", 1) },
                platformAbiSlice = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "slice", 3) },
                platformAbiWritePointer = platformAbi?.let {
                    resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writePointer", 2)
                },
                platformAbiWriteInt8 = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeInt8", 2) },
                platformAbiWriteInt16 = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeInt16", 2) },
                platformAbiWriteInt32 = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeInt32", 2) },
                platformAbiWriteInt64 = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeInt64", 2) },
                platformAbiWriteFloat = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeFloat", 2) },
                platformAbiWriteDouble = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeDouble", 2) },
                platformAbiWriteGuid = platformAbi?.let { resolver.function(WINRT_PLATFORM_ABI_FQ_NAME, "writeGuid", 2) },
                platformAbiIsNullPointer = platformAbi?.functionNamedWithRegularParameterTypes(
                    "isNull",
                    listOf(WINRT_RAW_ADDRESS_FQ_NAME),
                ),
                iWinRTObjectNativeObjectGetter = iWinRTObject?.propertyGetter("nativeObject"),
                iWinRTObjectGetObjectReferenceForType =
                    iWinRTObject?.functionNamedWithRegularParameterCount("getObjectReferenceForType", 1),
                tryAcquireWinRTManagedProjectionCallLease = requiredCallSiteSymbol(
                    "tryAcquireWinRTManagedProjectionCallLease",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "tryAcquireWinRTManagedProjectionCallLease",
                        2,
                    ),
                ) ?: return null,
                tryAcquireWinRTManagedProjectionCallLeaseWithState = requiredCallSiteSymbol(
                    "tryAcquireWinRTManagedProjectionCallLease with projected state",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "tryAcquireWinRTManagedProjectionCallLease",
                        3,
                    ),
                ) ?: return null,
                releaseWinRTManagedProjectionCallLease = requiredCallSiteSymbol(
                    "releaseWinRTManagedProjectionCallLease",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "releaseWinRTManagedProjectionCallLease",
                        2,
                    ),
                ) ?: return null,
                tryBorrowWinRTManagedProjectionAbi = requiredCallSiteSymbol(
                    "tryBorrowWinRTManagedProjectionAbi",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "tryBorrowWinRTManagedProjectionAbi",
                        2,
                    ),
                ) ?: return null,
                tryBorrowWinRTManagedProjectionAbiWithState = requiredCallSiteSymbol(
                    "tryBorrowWinRTManagedProjectionAbi with projected state",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "tryBorrowWinRTManagedProjectionAbi",
                        3,
                    ),
                ) ?: return null,
                tryBorrowWinRTManagedInspectableAbi = requiredCallSiteSymbol(
                    "tryBorrowWinRTManagedInspectableAbi",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "tryBorrowWinRTManagedInspectableAbi",
                        1,
                    ),
                ) ?: return null,
                winRTManagedProjectionStateAccessor = requiredCallSiteSymbol(
                    "WinRTManagedProjectionStateAccess.winRTManagedProjectionState",
                    resolver.classSymbol(WINRT_MANAGED_PROJECTION_STATE_ACCESS_FQ_NAME)
                        ?.functionNamedWithRegularParameterCount("winRTManagedProjectionState", 0),
                ) ?: return null,
                winRTProjectionMarshaler = requiredCallSiteSymbol(
                    "winRTProjectionMarshaler",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "winRTProjectionMarshaler",
                        2,
                    ),
                ) ?: return null,
                winRTProjectionMarshalerAbiGetter = requiredCallSiteSymbol(
                    "WinRTProjectionMarshaler.abi",
                    winRTProjectionMarshaler.propertyGetter("abi"),
                ) ?: return null,
                winRTProjectionMarshalerClose = requiredCallSiteSymbol(
                    "WinRTProjectionMarshaler.close",
                    winRTProjectionMarshaler.functionNamedWithRegularParameterCount("close", 0),
                ) ?: return null,
                winRTKeepAlive = requiredCallSiteSymbol(
                    "winRTKeepAlive",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTKeepAlive", 1),
                ) ?: return null,
                winRTAbiArrayAllocateInput = requiredCallSiteSymbol(
                    "WinRTAbiArray.allocateInput",
                    winRTAbiArrayCompanion.functionNamedWithRegularParameterCount("allocateInput", 3),
                ) ?: return null,
                winRTAbiArrayLengthGetter = requiredCallSiteSymbol(
                    "WinRTAbiArray.length",
                    winRTAbiArray.propertyGetter("length"),
                ) ?: return null,
                winRTAbiArrayDataGetter = requiredCallSiteSymbol(
                    "WinRTAbiArray.data",
                    winRTAbiArray.propertyGetter("data"),
                ) ?: return null,
                winRTAbiArrayClose = requiredCallSiteSymbol(
                    "WinRTAbiArray.close",
                    winRTAbiArray.functionNamedWithRegularParameterCount("close", 0),
                ) ?: return null,
                nativeStringMarshallerFromAbi = requiredCallSiteSymbol(
                    "NativeStringMarshaller.fromAbi",
                    nativeStringMarshaller.functionNamedWithRegularParameterCount("fromAbi", 1),
                ) ?: return null,
                nativeStringMarshallerFromManaged = requiredCallSiteSymbol(
                    "NativeStringMarshaller.fromManaged",
                    nativeStringMarshaller.functionNamedWithRegularParameterCount("fromManaged", 1),
                ) ?: return null,
                nativeStringMarshallerGetAbiHString = requiredCallSiteSymbol(
                    "NativeStringMarshaller.getAbi(HString)",
                    nativeStringMarshaller.functionNamedWithRegularParameterTypes(
                        "getAbi",
                        listOf(WINRT_HSTRING_FQ_NAME),
                    ),
                ) ?: return null,
                nativeStringMarshallerDisposeAbi = requiredCallSiteSymbol(
                    "NativeStringMarshaller.disposeAbi",
                    resolver.function(WINRT_NATIVE_STRING_MARSHALLER_FQ_NAME, "disposeAbi", 1),
                ) ?: return null,
                winRTProjectionInboundRetainAddress = requiredCallSiteSymbol(
                    "winRTProjectionInboundRetainAddress",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "winRTProjectionInboundRetainAddress",
                        1,
                    ),
                ) ?: return null,
                winRTPlatformApiReleaseRaw = requiredCallSiteSymbol(
                    "WinRTPlatformApi.releaseRaw",
                    resolver.function(WINRT_PLATFORM_API_FQ_NAME, "releaseRaw", 1),
                ) ?: return null,
                winRTPlatformApiCoTaskMemFreeRaw = requiredCallSiteSymbol(
                    "WinRTPlatformApi.coTaskMemFreeRaw",
                    resolver.function(WINRT_PLATFORM_API_FQ_NAME, "coTaskMemFreeRaw", 1),
                ) ?: return null,
                hResultConstructor = requiredCallSiteSymbol(
                    "HResult constructor",
                    hResult.singleValueConstructor(),
                ) ?: return null,
                hResultIsFailureGetter = requiredCallSiteSymbol(
                    "HResult.isFailure",
                    hResult.propertyGetter("isFailure"),
                ) ?: return null,
                hResultRequireSuccess = requiredCallSiteSymbol(
                    "HResult.requireSuccess",
                    resolver.function(WINRT_HRESULT_FQ_NAME, "requireSuccess", 1),
                ) ?: return null,
                winRTConsumeOwnedHStringScalarResult = requiredCallSiteSymbol(
                    "winRTConsumeOwnedHStringScalarResult",
                    resolver.topLevelFunction(
                        WINRT_RUNTIME_PACKAGE_FQ_NAME,
                        "winRTConsumeOwnedHStringScalarResult",
                        3,
                    ),
                ) ?: return null,
                winRTScalarResultRecord = resolver.topLevelFunction(
                    WINRT_RUNTIME_PACKAGE_FQ_NAME,
                    "winRTScalarResultRecord",
                    0,
                ),
                winRTScalarResultHResult = resolver.topLevelFunction(
                    WINRT_RUNTIME_PACKAGE_FQ_NAME,
                    "winRTScalarResultHResult",
                    1,
                ),
                winRTScalarResultValue = resolver.topLevelFunction(
                    WINRT_RUNTIME_PACKAGE_FQ_NAME,
                    "winRTScalarResultValue",
                    1,
                ),
                winRTWideScalarResultFloat64 = resolver.topLevelFunction(
                    WINRT_RUNTIME_PACKAGE_FQ_NAME,
                    "winRTWideScalarResultFloat64",
                    1,
                ),
                winRTPackedScalarResultHResult = requiredCallSiteSymbol(
                    "winRTPackedScalarResultHResult",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPackedScalarResultHResult", 1),
                ) ?: return null,
                winRTPackedScalarResultInt8 = requiredCallSiteSymbol(
                    "winRTPackedScalarResultInt8",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPackedScalarResultInt8", 1),
                ) ?: return null,
                winRTPackedScalarResultInt16 = requiredCallSiteSymbol(
                    "winRTPackedScalarResultInt16",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPackedScalarResultInt16", 1),
                ) ?: return null,
                winRTPackedScalarResultInt32 = requiredCallSiteSymbol(
                    "winRTPackedScalarResultInt32",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPackedScalarResultInt32", 1),
                ) ?: return null,
                winRTPackedScalarResultFloat32 = requiredCallSiteSymbol(
                    "winRTPackedScalarResultFloat32",
                    resolver.topLevelFunction(WINRT_RUNTIME_PACKAGE_FQ_NAME, "winRTPackedScalarResultFloat32", 1),
                ) ?: return null,
                primitiveSymbols = requiredCallSiteSymbol(
                    "PrimitiveCallSiteSymbols",
                    PrimitiveCallSiteSymbols.create(resolver),
                ) ?: return null,
            )
        }
    }
}

private fun <T> requiredCallSiteSymbol(name: String, value: T?): T? {
    if (value == null) error("kotlin-winrt missing shared call-site symbol: $name")
    return value
}

private class CallSiteLoweringAborted(detail: String?) : RuntimeException(detail, null, false, false)

private fun abortCallSiteLowering(detail: String? = null): Nothing = throw CallSiteLoweringAborted(detail)

private data class FoldedSlot(
    val slot: WinRTProjectionCallSiteSlot,
    val functionParameterIndex: Int?,
)

private data class LoweringState(
    val abiArguments: List<IrExpression> = emptyList(),
    val directInputs: List<WinRTDirectCallInput> = emptyList(),
    val postCalls: List<() -> IrExpression?> = emptyList(),
    val keepAliveOwners: List<IrExpression> = emptyList(),
    val allocatedOutputs: List<PreparedStorage> = emptyList(),
    val outputs: List<PreparedOutput> = emptyList(),
    val callerOutputs: List<PreparedCallerOutput> = emptyList(),
    val result: PreparedResult? = null,
)

private data class PreparedStorage(
    val slot: WinRTProjectionCallSiteSlot,
    val storage: OutputStorage,
)

private data class PreparedInput(
    val abiValues: List<IrExpression>,
    val directInputs: List<WinRTDirectCallInput> = emptyList(),
    val postCall: (() -> IrExpression?)? = null,
    val keepAliveOwners: List<IrExpression> = emptyList(),
)

private data class PreparedOutput(
    val slot: WinRTProjectionCallSiteSlot,
    val holder: IrExpression,
    val projectedType: IrType,
    val storage: OutputStorage,
)

private data class PreparedResult(
    val slot: WinRTProjectionCallSiteSlot,
    val storage: OutputStorage,
)

private data class PreparedCallerOutput(
    val slot: WinRTProjectionCallSiteSlot,
    val storage: OutputStorage,
)

private data class OwnedOutputState(
    val output: PreparedStorage,
    val transferred: IrVariable,
    val cleanupClaimed: IrVariable,
)

private data class OutputStorage(
    val addresses: List<IrExpression>,
    val scalarFrames: List<IrVariable>,
    val structFrame: IrVariable?,
    val directScalarValue: IrExpression? = null,
)

private fun standardDirectInputs(
    carriers: List<WinRTProjectionCallSiteAbiCarrier>,
    values: List<IrExpression>,
): List<WinRTDirectCallInput> {
    if (carriers.size != values.size) return emptyList()
    return carriers.zip(values) { carrier, value ->
        WinRTDirectCallInput(
            carrier = carrier,
            kind = carrier.directInputKindForLowering,
            words = listOf(value),
        )
    }
}

private fun directInputsForPrepared(
    recipe: WinRTProjectionCallSiteRecipe,
    prepared: PreparedInput,
): List<WinRTDirectCallInput> =
    prepared.directInputs.ifEmpty {
        standardDirectInputs(recipe.abiCarriers, prepared.abiValues)
    }

private val WinRTProjectionCallSiteAbiCarrier.directInputKindForLowering: WinRTDirectCallInputKind
    get() = when (this) {
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> WinRTDirectCallInputKind.FLOAT32
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> WinRTDirectCallInputKind.FLOAT64
        else -> WinRTDirectCallInputKind.INTEGER_OR_ADDRESS
    }

private const val MAX_SCALAR_RESULT_ARGUMENT_WORD_COUNT = 6

private val WinRTProjectionCallSiteSlotDirection.isProjectedResult: Boolean
    get() = this == WinRTProjectionCallSiteSlotDirection.RETURN ||
        this == WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY

private class PrimitiveCallSiteSymbols private constructor(
    private val ubyteConstructor: IrConstructorSymbol,
    private val ushortConstructor: IrConstructorSymbol,
    private val uintConstructor: IrConstructorSymbol,
    private val ulongConstructor: IrConstructorSymbol,
    private val ubyteToByte: IrSimpleFunctionSymbol,
    private val ushortToShort: IrSimpleFunctionSymbol,
    private val uintToInt: IrSimpleFunctionSymbol,
    private val ulongToLong: IrSimpleFunctionSymbol,
    val intToShort: IrSimpleFunctionSymbol,
    val intToLong: IrSimpleFunctionSymbol,
    val longTimes: IrSimpleFunctionSymbol,
    private val shortToInt: IrSimpleFunctionSymbol,
    private val intToChar: IrSimpleFunctionSymbol,
    private val charToInt: IrSimpleFunctionSymbol,
) {
    fun toAbi(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        value: IrExpression,
    ): IrExpression? =
        when (recipe.valueTransform) {
            WinRTProjectionCallSiteValueTransform.IDENTITY -> value
            WinRTProjectionCallSiteValueTransform.BOOLEAN -> builder.irIfThenElse(
                type = value.type.classOrNull?.owner?.parent.let { value.type }.let { builder.context.irBuiltIns.byteType },
                condition = value,
                thenPart = builder.irByte(1),
                elsePart = builder.irByte(0),
            )
            WinRTProjectionCallSiteValueTransform.UNSIGNED -> unsignedToAbi(builder, value)
            WinRTProjectionCallSiteValueTransform.CHAR16 -> {
                val code = builder.irCall(charToInt).apply { arguments[0] = value }
                builder.irCall(intToShort).apply { arguments[0] = code }
            }
        }

    fun fromAbi(
        builder: DeclarationIrBuilder,
        recipe: WinRTProjectionCallSiteRecipe,
        returnType: IrType,
        value: IrExpression,
    ): IrExpression? =
        when (recipe.valueTransform) {
            WinRTProjectionCallSiteValueTransform.IDENTITY -> value
            WinRTProjectionCallSiteValueTransform.BOOLEAN -> builder.irNotEquals(value, builder.irByte(0))
            WinRTProjectionCallSiteValueTransform.UNSIGNED -> when (returnType.classFqName) {
                KOTLIN_UBYTE_FQ_NAME -> builder.irCall(ubyteConstructor).apply { arguments[0] = value }
                KOTLIN_USHORT_FQ_NAME -> builder.irCall(ushortConstructor).apply { arguments[0] = value }
                KOTLIN_UINT_FQ_NAME -> builder.irCall(uintConstructor).apply { arguments[0] = value }
                KOTLIN_ULONG_FQ_NAME -> builder.irCall(ulongConstructor).apply { arguments[0] = value }
                else -> null
            }
            WinRTProjectionCallSiteValueTransform.CHAR16 -> {
                val intValue = builder.irCall(shortToInt).apply { arguments[0] = value }
                builder.irCall(intToChar).apply { arguments[0] = intValue }
            }
        }

    fun normalizeCarrier(
        builder: DeclarationIrBuilder,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? {
        val expected = when (carrier) {
            WinRTProjectionCallSiteAbiCarrier.INT8 -> KOTLIN_BYTE_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.INT16 -> KOTLIN_SHORT_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.INT32 -> KOTLIN_INT_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.INT64 -> KOTLIN_LONG_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> KOTLIN_FLOAT_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> KOTLIN_DOUBLE_FQ_NAME
            WinRTProjectionCallSiteAbiCarrier.ADDRESS -> return null
        }
        if (value.type.classFqName == expected) return value
        return unsignedToAbi(builder, value)
    }

    private fun unsignedToAbi(builder: DeclarationIrBuilder, value: IrExpression): IrExpression? =
        when (value.type.classFqName) {
            KOTLIN_UBYTE_FQ_NAME -> builder.irCall(ubyteToByte).apply { arguments[0] = value }
            KOTLIN_USHORT_FQ_NAME -> builder.irCall(ushortToShort).apply { arguments[0] = value }
            KOTLIN_UINT_FQ_NAME -> builder.irCall(uintToInt).apply { arguments[0] = value }
            KOTLIN_ULONG_FQ_NAME -> builder.irCall(ulongToLong).apply { arguments[0] = value }
            else -> null
        }

    fun zeroFloat(builder: DeclarationIrBuilder): IrExpression =
        builder.irAs(builder.irInt(0), builder.context.irBuiltIns.floatType)

    fun zeroDouble(builder: DeclarationIrBuilder): IrExpression =
        builder.irAs(builder.irLong(0L), builder.context.irBuiltIns.doubleType)

    companion object {
        fun create(resolver: CallSiteSymbolResolver): PrimitiveCallSiteSymbols? {
            val ubyte = requiredCallSiteSymbol("kotlin.UByte", resolver.classSymbol(KOTLIN_UBYTE_FQ_NAME)) ?: return null
            val ushort = requiredCallSiteSymbol("kotlin.UShort", resolver.classSymbol(KOTLIN_USHORT_FQ_NAME)) ?: return null
            val uint = requiredCallSiteSymbol("kotlin.UInt", resolver.classSymbol(KOTLIN_UINT_FQ_NAME)) ?: return null
            val ulong = requiredCallSiteSymbol("kotlin.ULong", resolver.classSymbol(KOTLIN_ULONG_FQ_NAME)) ?: return null
            val int = requiredCallSiteSymbol("kotlin.Int", resolver.classSymbol(KOTLIN_INT_FQ_NAME)) ?: return null
            val long = requiredCallSiteSymbol("kotlin.Long", resolver.classSymbol(KOTLIN_LONG_FQ_NAME)) ?: return null
            val short = requiredCallSiteSymbol("kotlin.Short", resolver.classSymbol(KOTLIN_SHORT_FQ_NAME)) ?: return null
            val char = requiredCallSiteSymbol("kotlin.Char", resolver.classSymbol(KOTLIN_CHAR_FQ_NAME)) ?: return null
            return PrimitiveCallSiteSymbols(
                ubyteConstructor = requiredCallSiteSymbol("UByte constructor", ubyte.singleValueConstructor()) ?: return null,
                ushortConstructor = requiredCallSiteSymbol("UShort constructor", ushort.singleValueConstructor()) ?: return null,
                uintConstructor = requiredCallSiteSymbol("UInt constructor", uint.singleValueConstructor()) ?: return null,
                ulongConstructor = requiredCallSiteSymbol("ULong constructor", ulong.singleValueConstructor()) ?: return null,
                ubyteToByte = requiredCallSiteSymbol("UByte.toByte", ubyte.functionNamedWithRegularParameterCount("toByte", 0)) ?: return null,
                ushortToShort = requiredCallSiteSymbol("UShort.toShort", ushort.functionNamedWithRegularParameterCount("toShort", 0)) ?: return null,
                uintToInt = requiredCallSiteSymbol("UInt.toInt", uint.functionNamedWithRegularParameterCount("toInt", 0)) ?: return null,
                ulongToLong = requiredCallSiteSymbol("ULong.toLong", ulong.functionNamedWithRegularParameterCount("toLong", 0)) ?: return null,
                intToShort = requiredCallSiteSymbol("Int.toShort", int.functionNamedWithRegularParameterCount("toShort", 0)) ?: return null,
                intToLong = requiredCallSiteSymbol("Int.toLong", int.functionNamedWithRegularParameterCount("toLong", 0)) ?: return null,
                longTimes = requiredCallSiteSymbol(
                    "Long.times(Long)",
                    long.functionNamedWithRegularParameterTypes("times", listOf(KOTLIN_LONG_FQ_NAME)),
                ) ?: return null,
                shortToInt = requiredCallSiteSymbol("Short.toInt", short.functionNamedWithRegularParameterCount("toInt", 0)) ?: return null,
                intToChar = requiredCallSiteSymbol("Int.toChar", int.functionNamedWithRegularParameterCount("toChar", 0)) ?: return null,
                charToInt = requiredCallSiteSymbol(
                    "Char.toInt",
                    char.functionNamedWithRegularParameterCount("toInt", 0),
                ) ?: return null,
            )
        }
    }
}

private class CallSiteSymbolResolver(
    private val pluginContext: IrPluginContext,
    private val fromFile: IrFile?,
    private val sourceClasses: Map<FqName, IrClassSymbol>,
    private val sourceFunctions: Map<CallableId, List<IrSimpleFunctionSymbol>>,
) {
    private val memberFunctionsByName = mutableMapOf<MemberFunctionName, List<IrSimpleFunctionSymbol>>()
    private val memberFunctionsByArity = mutableMapOf<MemberFunctionArity, IrSimpleFunctionSymbol?>()

    fun classSymbol(fqName: FqName): IrClassSymbol? {
        sourceClasses[fqName]?.let { return it }
        for (classId in fqName.candidateClassIds()) {
            pluginContext.findCallSiteClass(classId, fromFile)?.let { return it }
        }
        return null
    }

    fun topLevelFunction(packageName: FqName, name: String, regularParameterCount: Int): IrSimpleFunctionSymbol? {
        val callableId = CallableId(packageName, Name.identifier(name))
        sourceFunctions[callableId]
            .orEmpty()
            .filter { it.owner.regularParameters().size == regularParameterCount }
            .uniqueImplementation()
            ?.let { return it }
        val source = fromFile?.let { pluginContext.finderForSource(it).findFunctions(callableId) }.orEmpty()
        return source.ifEmpty { pluginContext.finderForBuiltins().findFunctions(callableId) }
            .filter { it.owner.regularParameters().size == regularParameterCount }
            .uniqueImplementation()
    }

    fun call(
        builder: DeclarationIrBuilder,
        ownerFqName: String,
        functionName: String,
        arguments: List<IrExpression>,
    ): IrExpression? {
        val function = function(ownerFqName, functionName, arguments.size)
            ?: abortCallSiteLowering(
                "cannot resolve $ownerFqName.$functionName with ${arguments.size} regular parameters",
            )
        return call(builder, function, arguments)
    }

    fun function(
        ownerFqName: FqName,
        functionName: String,
        regularParameterCount: Int,
    ): IrSimpleFunctionSymbol? = function(ownerFqName.asString(), functionName, regularParameterCount)

    fun function(
        ownerFqName: String,
        functionName: String,
        regularParameterCount: Int,
    ): IrSimpleFunctionSymbol? {
        if (functionName.isBlank()) return null
        val key = MemberFunctionArity(ownerFqName, functionName, regularParameterCount)
        if (memberFunctionsByArity.containsKey(key)) return memberFunctionsByArity[key]
        return functions(ownerFqName, functionName)
            .filter { function -> function.owner.regularParameters().size == regularParameterCount }
            .uniqueImplementation()
            .also { function -> memberFunctionsByArity[key] = function }
    }

    fun functions(ownerFqName: String, functionName: String): List<IrSimpleFunctionSymbol> {
        if (functionName.isBlank()) return emptyList()
        val key = MemberFunctionName(ownerFqName, functionName)
        memberFunctionsByName[key]?.let { return it }
        val owner = classSymbol(FqName(ownerFqName)) ?: return emptyList()
        val callableId = owner.owner.classId?.let { resolvedClassId ->
            CallableId(resolvedClassId, Name.identifier(functionName))
        }
        val materialized = callableId?.let(::findMemberFunctions).orEmpty()
        return (materialized + owner.owner.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { function -> function.name.asString() == functionName }
            .map(IrSimpleFunction::symbol))
            .distinctBy { symbol -> symbol.signature ?: symbol.owner }
            .also { functions -> memberFunctionsByName[key] = functions }
    }

    private fun findMemberFunctions(callableId: CallableId): Collection<IrSimpleFunctionSymbol> {
        val source = fromFile?.let { pluginContext.finderForSource(it).findFunctions(callableId) }.orEmpty()
        return source.ifEmpty { pluginContext.finderForBuiltins().findFunctions(callableId) }
    }

    fun call(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunctionSymbol,
        arguments: List<IrExpression>,
    ): IrExpression {
        val owner = function.owner.parent as IrClass
        val call = builder.irCall(function)
        function.owner.parameters.forEachIndexed { index, parameter ->
            when (parameter.kind) {
                IrParameterKind.DispatchReceiver -> call.arguments[index] = builder.irGetObject(owner.symbol)
                IrParameterKind.Regular -> {
                    val regularIndex = function.owner.parameters.take(index).count { it.kind == IrParameterKind.Regular }
                    call.arguments[index] = arguments[regularIndex]
                }
                else -> Unit
            }
        }
        return call
    }

    fun topLevelCall(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunctionSymbol,
        arguments: List<IrExpression>,
    ): IrExpression {
        val call = builder.irCall(function)
        function.owner.parameters.forEachIndexed { index, parameter ->
            if (parameter.kind == IrParameterKind.Regular) {
                val regularIndex = function.owner.parameters.take(index).count { it.kind == IrParameterKind.Regular }
                call.arguments[index] = arguments[regularIndex]
            }
        }
        return call
    }

    fun memberCall(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunctionSymbol,
        receiver: IrExpression,
        arguments: List<IrExpression>,
    ): IrExpression {
        val call = builder.irCall(function)
        function.owner.parameters.forEachIndexed { index, parameter ->
            when (parameter.kind) {
                IrParameterKind.DispatchReceiver,
                IrParameterKind.ExtensionReceiver -> call.arguments[index] = receiver
                IrParameterKind.Regular -> {
                    val regularIndex = function.owner.parameters.take(index).count { it.kind == IrParameterKind.Regular }
                    call.arguments[index] = arguments[regularIndex]
                }
                else -> Unit
            }
        }
        return call
    }

    fun memberCall(
        builder: DeclarationIrBuilder,
        function: IrSimpleFunctionSymbol,
        receiver: IrExpression,
        arguments: List<IrExpression>,
        returnType: IrType,
    ): IrExpression {
        val call = builder.irCall(function, returnType)
        function.owner.parameters.forEachIndexed { index, parameter ->
            when (parameter.kind) {
                IrParameterKind.DispatchReceiver,
                IrParameterKind.ExtensionReceiver -> call.arguments[index] = receiver
                IrParameterKind.Regular -> {
                    val regularIndex = function.owner.parameters.take(index).count { it.kind == IrParameterKind.Regular }
                    call.arguments[index] = arguments[regularIndex]
                }
                else -> Unit
            }
        }
        return call
    }
}

private data class MemberFunctionName(
    val ownerFqName: String,
    val functionName: String,
)

private data class MemberFunctionArity(
    val ownerFqName: String,
    val functionName: String,
    val regularParameterCount: Int,
)

private fun FqName.candidateClassIds(): List<ClassId> {
    val segments = pathSegments().map(Name::asString)
    return buildList {
        add(ClassId.topLevel(this@candidateClassIds))
        for (packageSize in segments.lastIndex downTo 0) {
            val packageName = FqName(segments.take(packageSize).joinToString("."))
            val relativeName = FqName(segments.drop(packageSize).joinToString("."))
            add(ClassId(packageName, relativeName, false))
        }
    }.distinct()
}

internal fun List<IrSimpleFunctionSymbol>.uniqueImplementation(): IrSimpleFunctionSymbol? =
    singleOrNull() ?: singleOrNull { symbol -> symbol.owner.body != null }

private fun IrPluginContext.findCallSiteClass(classId: ClassId, fromFile: IrFile?): IrClassSymbol? =
    fromFile?.let { finderForSource(it).findClass(classId) } ?: finderForBuiltins().findClass(classId)

private fun IrFunction.regularParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

private fun IrClassSymbol.functionNamedWithRegularParameterCount(
    name: String,
    count: Int,
): IrSimpleFunctionSymbol? = owner.declarations.filterIsInstance<IrSimpleFunction>()
    .singleOrNull { it.name.asString() == name && it.regularParameters().size == count }
    ?.symbol

private fun IrClassSymbol.functionNamed(name: String, count: Int): IrSimpleFunctionSymbol? =
    functionNamedWithRegularParameterCount(name, count)

private fun IrClassSymbol.functionNamedWithRegularParameterTypes(
    name: String,
    types: List<FqName>,
): IrSimpleFunctionSymbol? = owner.declarations.filterIsInstance<IrSimpleFunction>()
    .singleOrNull { function ->
        function.name.asString() == name &&
            function.regularParameters().map { parameter -> parameter.type.classFqName } == types
    }
    ?.symbol

private fun IrClassSymbol.propertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrProperty>().singleOrNull { it.name.asString() == name }?.getter?.symbol

private fun IrClassSymbol.propertySetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrProperty>().singleOrNull { it.name.asString() == name }?.setter?.symbol

private fun IrClassSymbol.singleValueConstructor(): IrConstructorSymbol? =
    owner.declarations.filterIsInstance<IrConstructor>().singleOrNull { it.regularParameters().size == 1 }?.symbol

private fun IrClassSymbol.rawComPtrConstructor(): IrConstructorSymbol? =
    owner.declarations.filterIsInstance<IrConstructor>().singleOrNull { constructor ->
        val parameters = constructor.regularParameters()
        parameters.firstOrNull()?.type?.classFqName == WINRT_RAW_COM_PTR_FQ_NAME &&
            parameters.drop(1).all { parameter -> parameter.defaultValue != null }
    }?.symbol

private fun IrClassSymbol.hasSupertype(
    fqName: FqName,
    visited: MutableSet<IrClassSymbol> = mutableSetOf(),
): Boolean {
    if (!visited.add(this)) return false
    if (owner.fqNameWhenAvailable == fqName) return true
    return owner.superTypes.any { type ->
        type.classOrNull?.hasSupertype(fqName, visited) == true
    }
}

private val WINRT_RUNTIME_PACKAGE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime")
private val WINRT_COM_OBJECT_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComObjectReference")
private val WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeScalarScratchFrame")
private val WINRT_NATIVE_STRUCT_SCRATCH_FRAME_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeStructScratchFrame")
private val WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeHStringReferenceFrame")
private val WINRT_NATIVE_STRING_MARSHALLER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeStringMarshaller")
private val WINRT_PLATFORM_ABI_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.PlatformAbi")
private val WINRT_PLATFORM_API_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTPlatformApi")
private val WINRT_IWINRT_OBJECT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IWinRTObject")
private val WINRT_MANAGED_PROJECTION_STATE_ACCESS_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTManagedProjectionStateAccess")
private val WINRT_PROJECTION_MARSHALER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTProjectionMarshaler")
private val WINRT_IUNKNOWN_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IUnknownReference")
private val WINRT_INSPECTABLE_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.InspectableReference")
private val WINRT_RAW_ADDRESS_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawAddress")
private val WINRT_RAW_COM_PTR_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawComPtr")
private val WINRT_OUT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTOut")
private val WINRT_HRESULT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.HResult")
private val WINRT_ABI_ARRAY_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTAbiArray")
private val WINRT_HSTRING_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.HString")
private val KOTLIN_BYTE_FQ_NAME = FqName("kotlin.Byte")
private val KOTLIN_ARRAY_FQ_NAME = FqName("kotlin.Array")
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

private val DIRECT_INBOUND_LOWERING_RECIPE_KINDS = setOf(
    WinRTProjectionCallSiteRecipeKind.VALUE,
    WinRTProjectionCallSiteRecipeKind.ENUM,
    WinRTProjectionCallSiteRecipeKind.PROJECTION,
)
