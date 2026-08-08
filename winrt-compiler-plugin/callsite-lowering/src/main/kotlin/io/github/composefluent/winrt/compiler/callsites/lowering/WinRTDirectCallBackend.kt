@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** One target ABI parameter and the words supplied to the Native recipe thunk for it. */
internal enum class WinRTDirectCallInputKind {
    INTEGER_OR_ADDRESS,
    FLOAT32,
    FLOAT64,
    HSTRING,
}

internal data class WinRTDirectCallInput(
    val carrier: WinRTProjectionCallSiteAbiCarrier,
    val kind: WinRTDirectCallInputKind,
    val words: List<IrExpression>,
) {
    init {
        require(words.size == if (kind == WinRTDirectCallInputKind.HSTRING) 2 else 1) {
            "A direct WinRT input must contain one word, except HSTRING which contains address and length."
        }
        require(kind != WinRTDirectCallInputKind.HSTRING || carrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            "A direct HSTRING input must occupy one address carrier in the target ABI."
        }
    }
}

/** Emits one direct JVM FFM or Native typed-function-pointer call from canonical ABI carriers. */
internal class WinRTDirectCallBackend private constructor(
    private val jvm: JvmFfmSymbols?,
    private val native: NativeCInteropSymbols?,
    private val rawComPtrAsRawAddress: IrSimpleFunctionSymbol,
    private val uintToInt: IrSimpleFunctionSymbol?,
    private val ulongToLong: IrSimpleFunctionSymbol?,
) {
    val supportsNativeRecipeThunks: Boolean
        get() = native != null

    fun emit(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? {
        require(carriers.size == values.size) {
            "A direct WinRT call requires one value for every ABI carrier."
        }
        return emitDirect(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            instance = instance,
            slot = slot,
            inputs = carriers.zip(values) { carrier, value ->
                WinRTDirectCallInput(
                    carrier = carrier,
                    kind = carrier.directInputKind,
                    words = listOf(value),
                )
            },
        )
    }

    fun emitDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? {
        val canonicalInputs = inputs.map { input ->
            canonicalInput(builder, input) ?: return null
        }
        jvm?.let { symbols ->
            if (canonicalInputs.any { input -> input.kind == WinRTDirectCallInputKind.HSTRING }) return null
            return symbols.emit(
                builder,
                pluginContext,
                ownerFunction,
                instance,
                slot,
                canonicalInputs.map(WinRTDirectCallInput::carrier),
                canonicalInputs.map { input -> input.words.single() },
            )
        }
        native?.let { symbols ->
            return symbols.emitDirect(
                builder,
                pluginContext,
                ownerFunction,
                instance,
                slot,
                canonicalInputs,
            )
        }
        return null
    }

    /**
     * Emits the Native scalar-result transport for a single non-owned scalar return. The
     * runtime thunk composes the canonical input words and keeps the indirect result on its
     * native stack; all other shapes continue through [emit] and the checked scratch path.
     */
    fun emitScalarResult(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? = native?.emitScalarResult(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        instance = instance,
        slot = slot,
        carriers = carriers,
        values = carriers.zip(values) { carrier, value ->
            canonicalValue(builder, carrier, value) ?: return null
        },
    )

    fun emitScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? = native?.emitScalarResultDirect(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        instance = instance,
        slot = slot,
        inputs = inputs.map { input -> canonicalInput(builder, input) ?: return null },
    )

    /** Emits an 8-byte scalar and HRESULT together in the Native vector return register. */
    fun emitWideScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? = native?.emitWideScalarResultDirect(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        instance = instance,
        slot = slot,
        inputs = inputs.map { input -> canonicalInput(builder, input) ?: return null },
    )

    fun emitWideScalarResultValueBits(
        builder: DeclarationIrBuilder,
        result: IrExpression,
    ): IrExpression? = native?.emitWideScalarResultValueBits(builder, result)

    fun emitWideScalarResultHResult(
        builder: DeclarationIrBuilder,
        result: IrExpression,
    ): IrExpression? = native?.emitWideScalarResultHResult(builder, result)

    /** Emits the Native register-packed transport for a 1/2/4-byte scalar result. */
    fun emitPackedScalarResult(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? = native?.emitPackedScalarResult(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        instance = instance,
        slot = slot,
        carriers = carriers,
        values = carriers.zip(values) { carrier, value ->
            canonicalValue(builder, carrier, value) ?: return null
        },
    )

    fun emitPackedScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? = native?.emitPackedScalarResultDirect(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        instance = instance,
        slot = slot,
        inputs = inputs.map { input -> canonicalInput(builder, input) ?: return null },
    )

    private fun canonicalInput(
        builder: DeclarationIrBuilder,
        input: WinRTDirectCallInput,
    ): WinRTDirectCallInput? {
        if (input.kind == WinRTDirectCallInputKind.HSTRING) {
            val address = input.words[0]
            val length = input.words[1]
            return input.takeIf {
                address.type.classFqName == WINRT_RAW_ADDRESS_FQ_NAME &&
                    length.type.classFqName == KOTLIN_INT_FQ_NAME
            }
        }
        val value = canonicalValue(builder, input.carrier, input.words.single()) ?: return null
        return input.copy(words = listOf(value))
    }

    private fun canonicalValue(
        builder: DeclarationIrBuilder,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? {
        val canonical = when {
            carrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS &&
                value.type.classFqName == WINRT_RAW_COM_PTR_FQ_NAME ->
                builder.irCall(rawComPtrAsRawAddress).apply { arguments[0] = value }
            carrier == WinRTProjectionCallSiteAbiCarrier.INT32 &&
                value.type.classFqName == KOTLIN_UINT_FQ_NAME ->
                builder.irCall(uintToInt ?: return null).apply { arguments[0] = value }
            carrier == WinRTProjectionCallSiteAbiCarrier.INT64 &&
                value.type.classFqName == KOTLIN_ULONG_FQ_NAME ->
                builder.irCall(ulongToLong ?: return null).apply { arguments[0] = value }
            else -> value
        }
        return canonical.takeIf { expression -> expression.type.classFqName == carrier.kotlinCarrierFqName }
    }

    companion object {
        fun create(pluginContext: IrPluginContext, fromFile: IrFile?): WinRTDirectCallBackend? {
            val rawComPtr = pluginContext.findDirectClass(WINRT_RAW_COM_PTR_CLASS_ID, fromFile) ?: return null
            val rawAddress = pluginContext.findDirectClass(WINRT_RAW_ADDRESS_CLASS_ID, fromFile) ?: return null
            val rawComPtrValue = rawComPtr.directPropertyGetter("value") ?: return null
            val rawAddressValue = rawAddress.directPropertyGetter("value") ?: return null
            val rawComPtrAsRawAddress = pluginContext.findDirectFunctions(
                CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("asRawAddress")),
                fromFile,
            ).filter { symbol ->
                symbol.owner.parameters.singleOrNull { parameter ->
                    parameter.kind == IrParameterKind.ExtensionReceiver
                }?.type?.classFqName == WINRT_RAW_COM_PTR_FQ_NAME &&
                    symbol.owner.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
            }.toList().uniqueImplementation() ?: return null
            val jvm = JvmFfmSymbols.create(pluginContext, fromFile, rawComPtrValue, rawAddressValue)
            val native = NativeCInteropSymbols.create(
                pluginContext,
                fromFile,
                rawComPtrValue,
                rawAddressValue,
            )
            if (jvm == null && native == null) return null
            val uintToInt = pluginContext.findDirectClass(KOTLIN_UINT_CLASS_ID, fromFile)
                ?.directFunction("toInt", emptyList())
            val ulongToLong = pluginContext.findDirectClass(KOTLIN_ULONG_CLASS_ID, fromFile)
                ?.directFunction("toLong", emptyList())
            return WinRTDirectCallBackend(
                jvm = jvm,
                native = native,
                rawComPtrAsRawAddress = rawComPtrAsRawAddress,
                uintToInt = uintToInt,
                ulongToLong = ulongToLong,
            )
        }
    }
}

private class JvmFfmSymbols private constructor(
    private val memoryLayoutType: IrType,
    private val memorySegmentOfAddress: IrSimpleFunctionSymbol,
    private val memorySegmentReinterpret: IrSimpleFunctionSymbol,
    private val memorySegmentGetAddress: IrSimpleFunctionSymbol,
    private val memorySegmentGetAtIndexAddress: IrSimpleFunctionSymbol,
    private val methodHandleInvoke: IrSimpleFunctionSymbol,
    private val valueLayoutAddress: StaticValue,
    private val intToLong: IrSimpleFunctionSymbol,
    private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
    private val rawAddressValueGetter: IrSimpleFunctionSymbol,
    private val handlesOwner: IrClassSymbol,
    private val createExactHResultHandle: IrSimpleFunctionSymbol,
    private val carrierLayouts: Map<WinRTProjectionCallSiteAbiCarrier, StaticValue>,
) {
    private val exactHandleFields = mutableMapOf<Pair<IrFile, List<WinRTProjectionCallSiteAbiCarrier>>, IrField>()

    fun emit(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
        val instanceBits = irTemporary(
            rawComPtrBits(builder, instance),
            nameHint = "instanceBits",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val instanceSegment = irTemporary(
            segmentFromAddressBits(builder, builder.irGet(instanceBits)),
            nameHint = "instanceSegment",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val function = irTemporary(
            vtableEntry(builder, builder.irGet(instanceSegment), slot),
            nameHint = "function",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        val handle = irTemporary(
            downcallHandle(builder, pluginContext, ownerFunction, carriers),
            nameHint = "handle",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
        )
        +invokeHResult(
            builder,
            pluginContext,
            builder.irGet(handle),
            listOf(builder.irGet(function), builder.irGet(instanceBits)) +
                values.mapIndexed { index, value -> carrier(builder, carriers[index], value) },
        )
    }

    private fun rawComPtrBits(builder: DeclarationIrBuilder, pointer: IrExpression): IrExpression =
        builder.irCall(rawComPtrValueGetter).apply { arguments[0] = pointer }

    private fun segmentFromAddressBits(builder: DeclarationIrBuilder, address: IrExpression): IrExpression =
        builder.irCall(memorySegmentOfAddress).apply { arguments[0] = address }

    private fun vtableEntry(
        builder: DeclarationIrBuilder,
        instanceSegment: IrExpression,
        slot: IrExpression,
    ): IrExpression {
        val objectMemory = builder.irCall(memorySegmentReinterpret).apply {
            arguments[0] = instanceSegment
            arguments[1] = builder.irLong(8L)
        }
        val vtable = builder.irCall(memorySegmentGetAddress).apply {
            arguments[0] = objectMemory
            arguments[1] = valueLayoutAddress.get(builder)
            arguments[2] = builder.irLong(0L)
        }
        return builder.irCall(memorySegmentGetAtIndexAddress).apply {
            arguments[0] = builder.irCall(memorySegmentReinterpret).apply {
                arguments[0] = vtable
                arguments[1] = builder.irLong(Long.MAX_VALUE)
            }
            arguments[1] = valueLayoutAddress.get(builder)
            arguments[2] = builder.irCall(intToLong).apply { arguments[0] = slot }
        }
    }

    private fun downcallHandle(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
    ): IrExpression {
        val file = ownerFunction.containingFile()
            ?: error("kotlin-winrt could not locate the JVM call site's file owner.")
        val key = file to carriers.toList()
        val field = exactHandleFields.getOrPut(key) {
            val fieldName = Name.identifier(
                "kotlinWinRTExactHResultHandle_" +
                    carriers.joinToString("_") { carrier -> carrier.name.lowercase() }.ifEmpty { "no_args" },
            )
            file.declarations.filterIsInstance<IrField>().singleOrNull { candidate -> candidate.name == fieldName }
                ?: pluginContext.irFactory.buildField {
                    startOffset = ownerFunction.startOffset
                    endOffset = ownerFunction.endOffset
                    origin = IrDeclarationOrigin.DEFINED
                    name = fieldName
                    visibility = DescriptorVisibilities.PRIVATE
                    type = createExactHResultHandle.owner.returnType
                    isFinal = true
                    isStatic = true
                }.also { created ->
                    created.parent = file
                    val initializerBuilder = DeclarationIrBuilder(
                        pluginContext,
                        created.symbol,
                        ownerFunction.startOffset,
                        ownerFunction.endOffset,
                    )
                    val layouts = carriers.map { carrier ->
                        requireNotNull(carrierLayouts[carrier]) {
                            "No exact JVM FFM layout exists for closed carrier $carrier."
                        }.get(initializerBuilder)
                    }
                    created.initializer = pluginContext.irFactory.createExpressionBody(
                        initializerBuilder.irCall(createExactHResultHandle).apply {
                            arguments[0] = initializerBuilder.irGetObject(handlesOwner)
                            arguments[1] = initializerBuilder.irVararg(memoryLayoutType, layouts)
                        },
                    )
                    file.declarations += created
                }
        }
        return builder.irGetField(null, field)
    }

    private fun carrier(
        builder: DeclarationIrBuilder,
        kind: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression =
        if (kind == WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            builder.irCall(rawAddressValueGetter).apply { arguments[0] = value }
        } else {
            value
        }

    private fun invokeHResult(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        handle: IrExpression,
        arguments: List<IrExpression>,
    ): IrExpression {
        val original = methodHandleInvoke.owner
        val instantiated = pluginContext.irFactory.buildFun {
            updateFrom(original)
            name = original.name
            origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
            returnType = pluginContext.irBuiltIns.intType
        }.apply {
            parent = original.parent
            parameters = original.parameters.filter { it.kind != IrParameterKind.Regular } +
                arguments.mapIndexed { index, argument ->
                    pluginContext.irFactory.buildValueParameter(
                        IrValueParameterBuilder().apply {
                            name = Name.identifier("\$$index")
                            type = argument.type
                            origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
                            kind = IrParameterKind.Regular
                        },
                        this,
                    )
                }
        }
        return builder.irCall(instantiated.symbol, pluginContext.irBuiltIns.intType).apply {
            this.arguments[0] = handle
            arguments.forEachIndexed { index, argument -> this.arguments[index + 1] = argument }
        }
    }

    private class StaticValue(
        private val field: IrField?,
        private val getter: IrSimpleFunctionSymbol?,
    ) {
        fun get(builder: DeclarationIrBuilder): IrExpression =
            field?.let { builder.irGetField(null, it) } ?: builder.irCall(requireNotNull(getter))
    }

    private class StaticObjectValue(
        private val owner: IrClassSymbol,
        private val field: IrField?,
        private val getter: IrSimpleFunctionSymbol?,
    ) {
        fun get(builder: DeclarationIrBuilder): IrExpression =
            field?.let { builder.irGetField(null, it) }
                ?: builder.irCall(requireNotNull(getter)).apply { dispatchReceiver = builder.irGetObject(owner) }
    }

    companion object {
        fun create(
            pluginContext: IrPluginContext,
            fromFile: IrFile?,
            rawComPtrValueGetter: IrSimpleFunctionSymbol,
            rawAddressValueGetter: IrSimpleFunctionSymbol,
        ): JvmFfmSymbols? {
            val memorySegment = pluginContext.findDirectClass(JAVA_MEMORY_SEGMENT_CLASS_ID, fromFile) ?: return null
            val memoryLayout = pluginContext.findDirectClass(JAVA_MEMORY_LAYOUT_CLASS_ID, fromFile) ?: return null
            val methodHandle = pluginContext.findDirectClass(JAVA_METHOD_HANDLE_CLASS_ID, fromFile) ?: return null
            val valueLayout = pluginContext.findDirectClass(JAVA_VALUE_LAYOUT_CLASS_ID, fromFile) ?: return null
            val addressLayout = pluginContext.findDirectClass(JAVA_ADDRESS_LAYOUT_CLASS_ID, fromFile) ?: return null
            val handles = pluginContext.findDirectClass(WINRT_JVM_FFM_HANDLES_CLASS_ID, fromFile) ?: return null
            val exactHandle = handles.directFunction("createExactHResultWordHandle") ?: return null
            fun staticLayoutValue(classId: ClassId, owner: IrClassSymbol, name: String): StaticValue? {
                val property = owner.owner.declarations.filterIsInstance<IrProperty>()
                    .singleOrNull { it.name.asString() == name }
                val field = owner.directField(name) ?: property?.backingField
                val getter = property?.getter?.symbol
                    ?: pluginContext.findDirectProperties(CallableId(classId, Name.identifier(name)), fromFile)
                        .singleOrNull()?.owner?.getter?.symbol
                return if (field != null || getter != null) StaticValue(field, getter) else null
            }
            val address = staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "ADDRESS")
                ?: staticLayoutValue(JAVA_ADDRESS_LAYOUT_CLASS_ID, addressLayout, "ADDRESS")
                ?: return null
            val long = staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_LONG") ?: return null
            val layouts = mapOf(
                WinRTProjectionCallSiteAbiCarrier.ADDRESS to long,
                WinRTProjectionCallSiteAbiCarrier.INT8 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_BYTE") ?: return null),
                WinRTProjectionCallSiteAbiCarrier.INT16 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_SHORT") ?: return null),
                WinRTProjectionCallSiteAbiCarrier.INT32 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_INT") ?: return null),
                WinRTProjectionCallSiteAbiCarrier.INT64 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_LONG") ?: return null),
                WinRTProjectionCallSiteAbiCarrier.FLOAT32 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_FLOAT") ?: return null),
                WinRTProjectionCallSiteAbiCarrier.FLOAT64 to
                    (staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, "JAVA_DOUBLE") ?: return null),
            )
            val intToLong = pluginContext.irBuiltIns.intClass.owner.declarations
                .filterIsInstance<IrSimpleFunction>()
                .singleOrNull { it.name.asString() == "toLong" }?.symbol ?: return null
            return JvmFfmSymbols(
                memoryLayoutType = memoryLayout.owner.defaultType,
                memorySegmentOfAddress = memorySegment.directFunction("ofAddress", listOf(KOTLIN_LONG_FQ_NAME)) ?: return null,
                memorySegmentReinterpret = memorySegment.directFunction("reinterpret", listOf(KOTLIN_LONG_FQ_NAME)) ?: return null,
                memorySegmentGetAddress = memorySegment.directFunction(
                    "get",
                    listOf(JAVA_ADDRESS_LAYOUT_FQ_NAME, KOTLIN_LONG_FQ_NAME),
                ) ?: return null,
                memorySegmentGetAtIndexAddress = memorySegment.directFunction(
                    "getAtIndex",
                    listOf(JAVA_ADDRESS_LAYOUT_FQ_NAME, KOTLIN_LONG_FQ_NAME),
                ) ?: return null,
                methodHandleInvoke = methodHandle.directFunction("invokeExact") ?: return null,
                valueLayoutAddress = address,
                intToLong = intToLong,
                rawComPtrValueGetter = rawComPtrValueGetter,
                rawAddressValueGetter = rawAddressValueGetter,
                handlesOwner = handles,
                createExactHResultHandle = exactHandle,
                carrierLayouts = layouts,
            )
        }
    }
}

private class NativeCInteropSymbols private constructor(
    private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
    private val rawAddressValueGetter: IrSimpleFunctionSymbol,
    private val cPointer: IrClassSymbol,
    private val cFunction: IrClassSymbol,
    private val vector128: IrClassSymbol,
    private val vector128GetLongAt: IrSimpleFunctionSymbol,
    private val vector128GetIntAt: IrSimpleFunctionSymbol,
    private val interpretCPointer: IrSimpleFunctionSymbol,
    private val getNativeNullPtr: IrSimpleFunctionSymbol,
    private val nativePtrPlus: IrSimpleFunctionSymbol,
    private val invokesByArity: Map<Int, IrSimpleFunctionSymbol>,
    private val createHResultThunk: IrSimpleFunctionSymbol,
    private val createPackedScalarResultThunk: IrSimpleFunctionSymbol,
    private val createScalarResultThunk: IrSimpleFunctionSymbol,
    private val createWideScalarResultThunk: IrSimpleFunctionSymbol,
    private val scalarResultRecord: IrSimpleFunctionSymbol,
    private val rawAddressToLong: IrSimpleFunctionSymbol,
    private val byteToLong: IrSimpleFunctionSymbol,
    private val shortToLong: IrSimpleFunctionSymbol,
    private val intToLong: IrSimpleFunctionSymbol,
    private val floatToBits: IrSimpleFunctionSymbol,
    private val doubleToBits: IrSimpleFunctionSymbol,
) {
    private val exactThunkFields = mutableMapOf<NativeThunkFieldKey, NativeThunkStorage>()
    fun emit(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? {
        require(carriers.size == values.size) {
            "A Native HRESULT thunk requires one value for every input carrier."
        }
        return emitDirect(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            transport = NativeThunkTransport.HRESULT,
            instance = instance,
            slot = slot,
            inputs = carriers.zip(values) { carrier, value ->
                WinRTDirectCallInput(carrier, carrier.directInputKind, listOf(value))
            },
            resultType = pluginContext.irBuiltIns.intType,
        )
    }

    fun emitDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? = emitDirect(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        transport = NativeThunkTransport.HRESULT,
        instance = instance,
        slot = slot,
        inputs = inputs,
        resultType = pluginContext.irBuiltIns.intType,
    )

    private fun emitDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        transport: NativeThunkTransport,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
        trailingWords: List<IrExpression> = emptyList(),
        resultType: IrType,
    ): IrExpression? {
        val words = inputs.flatMap { input ->
            when (input.kind) {
                WinRTDirectCallInputKind.HSTRING -> listOf(
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.ADDRESS, input.words[0])
                        ?: return null,
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.INT32, input.words[1])
                        ?: return null,
                )
                else -> listOf(
                    scalarWord(builder, pluginContext, input.carrier, input.words.single()) ?: return null,
                )
            }
        }
        return emitThunkInvocation(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            transport = transport,
            instance = instance,
            slot = slot,
            inputs = inputs,
            words = words,
            trailingWords = trailingWords,
            resultType = resultType,
        )
    }

    fun emitScalarResult(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? {
        require(carriers.size == values.size) {
            "A scalar-result call requires one value for every input carrier."
        }
        return emitScalarResultDirect(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            instance = instance,
            slot = slot,
            inputs = carriers.zip(values) { carrier, value ->
                WinRTDirectCallInput(carrier, carrier.directInputKind, listOf(value))
            },
        )
    }

    fun emitScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? {
        val words = inputs.flatMap { input ->
            when (input.kind) {
                WinRTDirectCallInputKind.HSTRING -> listOf(
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.ADDRESS, input.words[0])
                        ?: return null,
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.INT32, input.words[1])
                        ?: return null,
                )
                else -> listOf(
                    scalarWord(builder, pluginContext, input.carrier, input.words.single()) ?: return null,
                )
            }
        }
        return builder.irBlock(resultType = WINRT_RAW_ADDRESS_IR_TYPE(pluginContext)) {
            val record = irTemporary(
                builder.irCall(scalarResultRecord),
                nameHint = "scalarResultRecord",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val recordWord = builder.irCall(rawAddressToLong).apply { arguments[0] = builder.irGet(record) }
            +(emitThunkInvocation(
                builder = builder,
                pluginContext = pluginContext,
                ownerFunction = ownerFunction,
                transport = NativeThunkTransport.SCALAR_RESULT_RECORD,
                instance = instance,
                slot = slot,
                inputs = inputs,
                words = words,
                trailingWords = listOf(recordWord),
                resultType = pluginContext.irBuiltIns.longType,
            ) ?: return null)
            +builder.irGet(record)
        }
    }

    fun emitWideScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? = emitDirect(
        builder = builder,
        pluginContext = pluginContext,
        ownerFunction = ownerFunction,
        transport = NativeThunkTransport.WIDE_SCALAR_RESULT,
        instance = instance,
        slot = slot,
        inputs = inputs,
        resultType = vector128.owner.defaultType,
    )

    fun emitWideScalarResultValueBits(
        builder: DeclarationIrBuilder,
        result: IrExpression,
    ): IrExpression? = result.takeIf { it.type.classFqName == KOTLINX_CINTEROP_VECTOR128_FQ_NAME }?.let { value ->
        builder.irCall(vector128GetLongAt).apply {
            arguments[0] = value
            arguments[1] = builder.irInt(0)
        }
    }

    fun emitWideScalarResultHResult(
        builder: DeclarationIrBuilder,
        result: IrExpression,
    ): IrExpression? = result.takeIf { it.type.classFqName == KOTLINX_CINTEROP_VECTOR128_FQ_NAME }?.let { value ->
        builder.irCall(vector128GetIntAt).apply {
            arguments[0] = value
            arguments[1] = builder.irInt(2)
        }
    }

    fun emitPackedScalarResult(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        carriers: List<WinRTProjectionCallSiteAbiCarrier>,
        values: List<IrExpression>,
    ): IrExpression? {
        require(carriers.size == values.size) {
            "A packed scalar-result call requires one value for every input carrier."
        }
        return emitPackedScalarResultDirect(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            instance = instance,
            slot = slot,
            inputs = carriers.zip(values) { carrier, value ->
                WinRTDirectCallInput(carrier, carrier.directInputKind, listOf(value))
            },
        )
    }

    fun emitPackedScalarResultDirect(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression? {
        val words = inputs.flatMap { input ->
            when (input.kind) {
                WinRTDirectCallInputKind.HSTRING -> listOf(
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.ADDRESS, input.words[0])
                        ?: return null,
                    scalarWord(builder, pluginContext, WinRTProjectionCallSiteAbiCarrier.INT32, input.words[1])
                        ?: return null,
                )
                else -> listOf(
                    scalarWord(builder, pluginContext, input.carrier, input.words.single()) ?: return null,
                )
            }
        }
        return emitThunkInvocation(
            builder = builder,
            pluginContext = pluginContext,
            ownerFunction = ownerFunction,
            transport = NativeThunkTransport.PACKED_SCALAR_RESULT,
            instance = instance,
            slot = slot,
            inputs = inputs,
            words = words,
            resultType = pluginContext.irBuiltIns.longType,
        )
    }

    private fun scalarWord(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        value: IrExpression,
    ): IrExpression? = when (carrier) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS ->
            builder.irCall(rawAddressToLong).apply { arguments[0] = value }
        WinRTProjectionCallSiteAbiCarrier.INT8 ->
            builder.irCall(byteToLong).apply { arguments[0] = value }
        WinRTProjectionCallSiteAbiCarrier.INT16 ->
            builder.irCall(shortToLong).apply { arguments[0] = value }
        WinRTProjectionCallSiteAbiCarrier.INT32 ->
            builder.irCall(intToLong).apply { arguments[0] = value }
        WinRTProjectionCallSiteAbiCarrier.INT64 -> value
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 ->
            builder.irCall(intToLong).apply {
                arguments[0] = builder.irCall(floatToBits).apply { arguments[0] = value }
            }
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 ->
            builder.irCall(doubleToBits).apply { arguments[0] = value }
    }.takeIf { expression -> expression.type.classFqName == KOTLIN_LONG_FQ_NAME }

    private fun emitThunkInvocation(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        transport: NativeThunkTransport,
        instance: IrExpression,
        slot: IrExpression,
        inputs: List<WinRTDirectCallInput>,
        words: List<IrExpression>,
        trailingWords: List<IrExpression> = emptyList(),
        resultType: IrType,
    ): IrExpression? {
        if (inputs.size > MAX_NATIVE_RECIPE_THUNK_INPUT_COUNT) return null
        val invokeArguments = listOf(
            builder.irCall(rawComPtrValueGetter).apply { arguments[0] = instance },
            builder.irCall(intToLong).apply { arguments[0] = slot },
        ) + words + trailingWords
        val invoke = invokesByArity[invokeArguments.size] ?: return null
        val pointer = functionPointer(
            builder = builder,
            pluginContext = pluginContext,
            address = thunkField(
                builder = builder,
                pluginContext = pluginContext,
                ownerFunction = ownerFunction,
                transport = transport,
                inputs = inputs,
            ),
            parameterTypes = invokeArguments.map(IrExpression::type),
            resultType = resultType,
        )
        return builder.irCall(invoke, resultType).apply {
            (invokeArguments.map(IrExpression::type) + resultType).forEachIndexed { index, type ->
                typeArguments[index] = type
            }
            arguments[0] = pointer
            invokeArguments.forEachIndexed { index, argument -> arguments[index + 1] = argument }
        }
    }

    private fun thunkField(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        ownerFunction: IrSimpleFunction,
        transport: NativeThunkTransport,
        inputs: List<WinRTDirectCallInput>,
    ): IrExpression {
        val file = ownerFunction.containingFile()
            ?: error("kotlin-winrt could not locate the Native call site's file owner.")
        val key = NativeThunkFieldKey(file, transport, inputs.map { input -> input.shape })
        val storage = exactThunkFields.getOrPut(key) {
            val fieldName = Name.identifier(
                "kotlinWinRT${transport.fieldNameComponent}Thunk_" +
                    inputs.joinToString("_") { input -> input.shape.fieldNameComponent }.ifEmpty { "no_args" } +
                    "_" + ownerFunction.stableThunkOwnerSuffix(),
            )
            val accessorName = Name.identifier(fieldName.asString() + "Address")
            val existingField = file.declarations.filterIsInstance<IrField>()
                .singleOrNull { candidate -> candidate.name == fieldName }
            val existingAccessor = file.declarations.filterIsInstance<IrSimpleFunction>()
                .singleOrNull { candidate -> candidate.name == accessorName }
            if (existingField != null && existingAccessor != null) {
                NativeThunkStorage(existingField, existingAccessor)
            } else {
                val createdField = pluginContext.irFactory.buildField {
                    startOffset = ownerFunction.startOffset
                    endOffset = ownerFunction.endOffset
                    origin = IrDeclarationOrigin.DEFINED
                    name = fieldName
                    visibility = DescriptorVisibilities.PRIVATE
                    type = pluginContext.irBuiltIns.longType
                    isFinal = false
                    isStatic = true
                }.also { created ->
                    created.parent = file
                    val initializerBuilder = DeclarationIrBuilder(
                        pluginContext,
                        created.symbol,
                        ownerFunction.startOffset,
                        ownerFunction.endOffset,
                    )
                    created.initializer = pluginContext.irFactory.createExpressionBody(
                        initializerBuilder.irLong(0L),
                    )
                }
                val createdAccessor = pluginContext.irFactory.buildFun {
                    startOffset = ownerFunction.startOffset
                    endOffset = ownerFunction.endOffset
                    origin = IrDeclarationOrigin.DEFINED
                    name = accessorName
                    visibility = DescriptorVisibilities.PUBLIC
                    returnType = pluginContext.irBuiltIns.longType
                }.also { created ->
                    created.parent = file
                    val accessorBuilder = DeclarationIrBuilder(
                        pluginContext,
                        created.symbol,
                        ownerFunction.startOffset,
                        ownerFunction.endOffset,
                    )
                    created.body = accessorBuilder.irBlockBody {
                        val cached = irTemporary(
                            accessorBuilder.irGetField(null, createdField),
                            nameHint = "cachedThunkAddress",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +accessorBuilder.irReturn(
                            accessorBuilder.irIfThenElse(
                                type = pluginContext.irBuiltIns.longType,
                                condition = accessorBuilder.irNotEquals(
                                    accessorBuilder.irGet(cached),
                                    accessorBuilder.irLong(0L),
                                ),
                                thenPart = accessorBuilder.irGet(cached),
                                elsePart = accessorBuilder.irBlock(resultType = pluginContext.irBuiltIns.longType) {
                                    val createdAddress = irTemporary(
                                        accessorBuilder.irCall(rawAddressValueGetter).apply {
                                            arguments[0] = accessorBuilder.irCall(factory(transport)).apply {
                                                arguments[0] = accessorBuilder.irInt(inputs.size)
                                                arguments[1] = accessorBuilder.irLong(inputs.floatingPointKinds())
                                            }
                                        },
                                        nameHint = "createdThunkAddress",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +accessorBuilder.irSetField(
                                        null,
                                        createdField,
                                        accessorBuilder.irGet(createdAddress),
                                    )
                                    +accessorBuilder.irGet(createdAddress)
                                },
                            ),
                        )
                    }
                }
                file.declarations += createdField
                file.declarations += createdAccessor
                NativeThunkStorage(createdField, createdAccessor)
            }
        }
        return builder.irCall(storage.accessor.symbol)
    }

    private fun factory(transport: NativeThunkTransport): IrSimpleFunctionSymbol = when (transport) {
        NativeThunkTransport.HRESULT -> createHResultThunk
        NativeThunkTransport.PACKED_SCALAR_RESULT -> createPackedScalarResultThunk
        NativeThunkTransport.SCALAR_RESULT_RECORD -> createScalarResultThunk
        NativeThunkTransport.WIDE_SCALAR_RESULT -> createWideScalarResultThunk
    }

    private fun functionPointer(
        builder: DeclarationIrBuilder,
        pluginContext: IrPluginContext,
        address: IrExpression,
        parameterTypes: List<IrType>,
        resultType: IrType,
    ): IrExpression {
        val functionClass = pluginContext.findDirectClass(
            ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function${parameterTypes.size}")),
            null,
        ) ?: error("kotlin-winrt requires kotlin.Function${parameterTypes.size} for a Native WinRT call site.")
        val functionType = functionClass.typeWith(parameterTypes + resultType)
        val pointerType = cPointer.typeWith(cFunction.typeWith(functionType))
        val cFunctionType = pointerType.arguments.single().typeOrNull
            ?: error("kotlin-winrt could not build a Native CFunction pointer type.")
        val nativePointer = builder.irCall(nativePtrPlus).apply {
            arguments[0] = builder.irCall(getNativeNullPtr)
            arguments[1] = address
        }
        return builder.irAs(
            builder.irCall(interpretCPointer, pointerType.makeNullable()).apply {
                typeArguments[0] = cFunctionType
                arguments[0] = nativePointer
            },
            pointerType,
        )
    }

    companion object {
        fun create(
            pluginContext: IrPluginContext,
            fromFile: IrFile?,
            rawComPtrValueGetter: IrSimpleFunctionSymbol,
            rawAddressValueGetter: IrSimpleFunctionSymbol,
        ): NativeCInteropSymbols? {
            val cPointer = pluginContext.findDirectClass(KOTLINX_CINTEROP_CPOINTER_CLASS_ID, fromFile) ?: return null
            val cFunction = pluginContext.findDirectClass(KOTLINX_CINTEROP_CFUNCTION_CLASS_ID, fromFile) ?: return null
            val vector128 = pluginContext.findDirectClass(KOTLINX_CINTEROP_VECTOR128_CLASS_ID, fromFile) ?: return null
            val nativePtr = pluginContext.findDirectClass(KOTLIN_NATIVE_PTR_CLASS_ID, fromFile) ?: return null
            val vector128GetLongAt = vector128.directFunction("getLongAt", listOf(KOTLIN_INT_FQ_NAME)) ?: return null
            val vector128GetIntAt = vector128.directFunction("getIntAt", listOf(KOTLIN_INT_FQ_NAME)) ?: return null
            val interpretCPointer = pluginContext.findDirectFunctions(
                CallableId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("interpretCPointer")),
                fromFile,
            ).singleOrNull() ?: return null
            val getNativeNullPtr = pluginContext.findDirectFunctions(
                CallableId(KOTLIN_NATIVE_INTERNAL_PACKAGE_FQ_NAME, Name.identifier("getNativeNullPtr")),
                fromFile,
            ).singleOrNull() ?: return null
            val nativePtrPlus = nativePtr.directFunction("plus", listOf(KOTLIN_LONG_FQ_NAME)) ?: return null
            val invokes = pluginContext.findDirectFunctions(
                CallableId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("invoke")),
                fromFile,
            ).mapNotNull { symbol ->
                val arity = symbol.owner.parameters.count { it.kind == IrParameterKind.Regular }
                arity to symbol
            }.toMap()
            if (invokes.isEmpty()) return null
            fun runtimeFunction(
                name: String,
                parameterTypes: List<FqName>,
                returnType: FqName,
            ): IrSimpleFunctionSymbol {
                val candidates = pluginContext.findDirectFunctions(
                    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier(name)),
                    fromFile,
                ).toList()
                val matching = candidates.filter { symbol ->
                    symbol.owner.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
                        .map { parameter -> parameter.type.classFqName } == parameterTypes &&
                        symbol.owner.returnType.classFqName == returnType
                }
                return matching.singleOrNull { symbol -> !symbol.owner.isExpect } ?: error(
                    "kotlin-winrt could not resolve the implemented Native runtime function $name; " +
                        "expectedParameters=$parameterTypes, expectedReturn=$returnType, candidates=" +
                        candidates.joinToString(prefix = "[", postfix = "]") { symbol ->
                            val owner = symbol.owner
                            "parameters=${owner.parameters.map { parameter -> parameter.kind to parameter.type.classFqName }}, " +
                                "return=${owner.returnType.classFqName}, expect=${owner.isExpect}, " +
                                "body=${owner.body != null}, origin=${owner.origin}"
                        },
                )
            }
            val recipeFactoryParameters = listOf(KOTLIN_INT_FQ_NAME, KOTLIN_LONG_FQ_NAME)
            val createHResultThunk = runtimeFunction(
                "winRTCreateHResultRecipeThunk",
                recipeFactoryParameters,
                WINRT_RAW_ADDRESS_FQ_NAME,
            )
            val createPackedScalarResultThunk = runtimeFunction(
                "winRTCreatePackedScalarResultRecipeThunk",
                recipeFactoryParameters,
                WINRT_RAW_ADDRESS_FQ_NAME,
            )
            val createScalarResultThunk = runtimeFunction(
                "winRTCreateScalarResultRecipeThunk",
                recipeFactoryParameters,
                WINRT_RAW_ADDRESS_FQ_NAME,
            )
            val createWideScalarResultThunk = runtimeFunction(
                "winRTCreateWideScalarResultRecipeThunk",
                recipeFactoryParameters,
                WINRT_RAW_ADDRESS_FQ_NAME,
            )
            val scalarResultRecord = runtimeFunction(
                "winRTScalarResultRecord",
                emptyList(),
                WINRT_RAW_ADDRESS_FQ_NAME,
            )
            fun requirePrimitiveSymbol(name: String, symbol: IrSimpleFunctionSymbol?): IrSimpleFunctionSymbol =
                symbol ?: error("kotlin-winrt could not resolve the Native primitive conversion $name.")

            val byteToLong = requirePrimitiveSymbol(
                "kotlin.Byte.toLong",
                primitiveMember(pluginContext, KOTLIN_BYTE_CLASS_ID, "toLong"),
            )
            val shortToLong = requirePrimitiveSymbol(
                "kotlin.Short.toLong",
                primitiveMember(pluginContext, KOTLIN_SHORT_CLASS_ID, "toLong"),
            )
            val intToLong = requirePrimitiveSymbol(
                "kotlin.Int.toLong",
                primitiveMember(pluginContext, KOTLIN_INT_CLASS_ID, "toLong"),
            )
            val floatToBits = requirePrimitiveSymbol(
                "kotlin.Float.toBits",
                primitiveExtension(
                    pluginContext,
                    fromFile,
                    KOTLIN_FLOAT_FQ_NAME,
                    "toBits",
                ),
            )
            val doubleToBits = requirePrimitiveSymbol(
                "kotlin.Double.toBits",
                primitiveExtension(
                    pluginContext,
                    fromFile,
                    KOTLIN_DOUBLE_FQ_NAME,
                    "toBits",
                ),
            )
            return NativeCInteropSymbols(
                rawComPtrValueGetter,
                rawAddressValueGetter,
                cPointer,
                cFunction,
                vector128,
                vector128GetLongAt,
                vector128GetIntAt,
                interpretCPointer,
                getNativeNullPtr,
                nativePtrPlus,
                invokes,
                createHResultThunk,
                createPackedScalarResultThunk,
                createScalarResultThunk,
                createWideScalarResultThunk,
                scalarResultRecord,
                rawAddressValueGetter,
                byteToLong,
                shortToLong,
                intToLong,
                floatToBits,
                doubleToBits,
            )
        }
    }
}

private enum class NativeThunkTransport(
    val fieldNameComponent: String,
) {
    HRESULT("NativeHResult"),
    PACKED_SCALAR_RESULT("NativePackedScalarResult"),
    SCALAR_RESULT_RECORD("NativeScalarResult"),
    WIDE_SCALAR_RESULT("NativeWideScalarResult"),
}

private data class NativeThunkFieldKey(
    val file: IrFile,
    val transport: NativeThunkTransport,
    val inputs: List<NativeThunkInputShape>,
)

private data class NativeThunkStorage(
    val field: IrField,
    val accessor: IrSimpleFunction,
)

private data class NativeThunkInputShape(
    val carrier: WinRTProjectionCallSiteAbiCarrier,
    val kind: WinRTDirectCallInputKind,
) {
    val fieldNameComponent: String
        get() = "${carrier.name.lowercase()}_${kind.name.lowercase()}"
}

private val WinRTDirectCallInput.shape: NativeThunkInputShape
    get() = NativeThunkInputShape(carrier, kind)

private val WinRTProjectionCallSiteAbiCarrier.directInputKind: WinRTDirectCallInputKind
    get() = when (this) {
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> WinRTDirectCallInputKind.FLOAT32
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> WinRTDirectCallInputKind.FLOAT64
        else -> WinRTDirectCallInputKind.INTEGER_OR_ADDRESS
    }

private fun List<WinRTDirectCallInput>.floatingPointKinds(): Long =
    foldIndexed(0L) { index, encoded, input ->
        val bits = when (input.kind) {
            WinRTDirectCallInputKind.FLOAT32 -> 1L
            WinRTDirectCallInputKind.FLOAT64 -> 2L
            WinRTDirectCallInputKind.HSTRING -> 3L
            WinRTDirectCallInputKind.INTEGER_OR_ADDRESS -> 0L
        }
        encoded or (bits shl (index * 2))
    }

private fun IrSimpleFunction.stableThunkOwnerSuffix(): String =
    fqNameWhenAvailable?.asString().orEmpty().hashCode().toUInt().toString(16)

private fun primitiveMember(
    pluginContext: IrPluginContext,
    classId: ClassId,
    name: String,
): IrSimpleFunctionSymbol? = pluginContext.finderForBuiltins().findClass(classId)?.owner?.declarations
    ?.filterIsInstance<IrSimpleFunction>()
    ?.singleOrNull { function ->
        function.name.asString() == name && function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 0
    }?.symbol

private fun primitiveExtension(
    pluginContext: IrPluginContext,
    fromFile: IrFile?,
    receiverType: FqName,
    name: String,
): IrSimpleFunctionSymbol? = pluginContext.findDirectFunctions(
    CallableId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier(name)),
    fromFile,
).filter { symbol ->
    val parameters = symbol.owner.parameters
    parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 0 &&
        parameters.singleOrNull { parameter -> parameter.kind == IrParameterKind.ExtensionReceiver }
            ?.type?.classFqName == receiverType
}.toList().uniqueImplementation()

private fun WINRT_RAW_ADDRESS_IR_TYPE(pluginContext: IrPluginContext): IrType =
    pluginContext.finderForBuiltins().findClass(WINRT_RAW_ADDRESS_CLASS_ID)?.owner?.defaultType
        ?: error("kotlin-winrt requires RawAddress for the Native scalar-result transport.")

private const val MAX_NATIVE_RECIPE_THUNK_INPUT_COUNT = 20

private val WinRTProjectionCallSiteAbiCarrier.kotlinCarrierFqName: FqName
    get() = when (this) {
        WinRTProjectionCallSiteAbiCarrier.ADDRESS -> WINRT_RAW_ADDRESS_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.INT8 -> KOTLIN_BYTE_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.INT16 -> KOTLIN_SHORT_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.INT32 -> KOTLIN_INT_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.INT64 -> KOTLIN_LONG_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> KOTLIN_FLOAT_FQ_NAME
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> KOTLIN_DOUBLE_FQ_NAME
    }

private tailrec fun IrDeclarationParent.containingFile(): IrFile? = when (this) {
    is IrFile -> this
    is IrDeclaration -> parent.containingFile()
    else -> null
}

private fun IrPluginContext.findDirectClass(classId: ClassId, fromFile: IrFile?): IrClassSymbol? =
    fromFile?.let { finderForSource(it).findClass(classId) } ?: finderForBuiltins().findClass(classId)

private fun IrPluginContext.findDirectFunctions(
    callableId: CallableId,
    fromFile: IrFile?,
): Collection<IrSimpleFunctionSymbol> {
    val source = fromFile?.let { finderForSource(it).findFunctions(callableId) }.orEmpty()
    return source.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

private fun IrPluginContext.findDirectProperties(callableId: CallableId, fromFile: IrFile?) =
    fromFile?.let { finderForSource(it).findProperties(callableId) }.orEmpty()
        .ifEmpty { finderForBuiltins().findProperties(callableId) }

private fun IrClassSymbol.directFunction(
    name: String,
    regularParameterTypes: List<FqName>? = null,
): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { function ->
        function.name.asString() == name &&
            (regularParameterTypes == null || function.parameters.filter { it.kind == IrParameterKind.Regular }
                .map { it.type.classFqName } == regularParameterTypes)
    }?.symbol

private fun IrClassSymbol.directPropertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrProperty>().singleOrNull { it.name.asString() == name }?.getter?.symbol

private fun IrClassSymbol.directField(name: String): IrField? =
    owner.declarations.filterIsInstance<IrField>().singleOrNull { it.name.asString() == name }

private val WINRT_RUNTIME_PACKAGE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime")
private val WINRT_COM_VTABLE_INVOKER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ComVtableInvoker"))
private val WINRT_RAW_COM_PTR_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawComPtr")
private val WINRT_RAW_ADDRESS_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawAddress")
private val WINRT_RAW_COM_PTR_CLASS_ID = ClassId.topLevel(WINRT_RAW_COM_PTR_FQ_NAME)
private val WINRT_RAW_ADDRESS_CLASS_ID = ClassId.topLevel(WINRT_RAW_ADDRESS_FQ_NAME)
private val WINRT_PLATFORM_ABI_CLASS_ID = ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("PlatformAbi"))
private val WINRT_JVM_FFM_HANDLES_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("WinRTJvmFfmDowncallHandles"))
private val WINRT_NATIVE_SCALAR_SCRATCH_FRAME_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeScalarScratchFrame"))
private val JAVA_FOREIGN_PACKAGE_FQ_NAME = FqName("java.lang.foreign")
private val JAVA_MEMORY_SEGMENT_CLASS_ID = ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemorySegment"))
private val JAVA_MEMORY_LAYOUT_CLASS_ID = ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemoryLayout"))
private val JAVA_VALUE_LAYOUT_CLASS_ID = ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("ValueLayout"))
private val JAVA_ADDRESS_LAYOUT_CLASS_ID = ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("AddressLayout"))
private val JAVA_ADDRESS_LAYOUT_FQ_NAME = FqName("java.lang.foreign.AddressLayout")
private val JAVA_METHOD_HANDLE_CLASS_ID = ClassId(FqName("java.lang.invoke"), Name.identifier("MethodHandle"))
private val KOTLINX_CINTEROP_PACKAGE_FQ_NAME = FqName("kotlinx.cinterop")
private val KOTLINX_CINTEROP_CPOINTER_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("CPointer"))
private val KOTLINX_CINTEROP_CFUNCTION_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("CFunction"))
private val KOTLINX_CINTEROP_VECTOR128_FQ_NAME = FqName("kotlinx.cinterop.Vector128")
private val KOTLINX_CINTEROP_VECTOR128_CLASS_ID = ClassId.topLevel(KOTLINX_CINTEROP_VECTOR128_FQ_NAME)
private val KOTLINX_CINTEROP_COPAQUE_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("COpaque"))
private val KOTLIN_NATIVE_INTERNAL_PACKAGE_FQ_NAME = FqName("kotlin.native.internal")
private val KOTLIN_NATIVE_PTR_CLASS_ID =
    ClassId(KOTLIN_NATIVE_INTERNAL_PACKAGE_FQ_NAME, Name.identifier("NativePtr"))
private val KOTLIN_PACKAGE_FQ_NAME = FqName("kotlin")
private val KOTLIN_INT_FQ_NAME = FqName("kotlin.Int")
private val KOTLIN_LONG_FQ_NAME = FqName("kotlin.Long")
private val KOTLIN_BYTE_FQ_NAME = FqName("kotlin.Byte")
private val KOTLIN_SHORT_FQ_NAME = FqName("kotlin.Short")
private val KOTLIN_FLOAT_FQ_NAME = FqName("kotlin.Float")
private val KOTLIN_DOUBLE_FQ_NAME = FqName("kotlin.Double")
private val KOTLIN_UINT_FQ_NAME = FqName("kotlin.UInt")
private val KOTLIN_ULONG_FQ_NAME = FqName("kotlin.ULong")
private val KOTLIN_UINT_CLASS_ID = ClassId.topLevel(KOTLIN_UINT_FQ_NAME)
private val KOTLIN_ULONG_CLASS_ID = ClassId.topLevel(KOTLIN_ULONG_FQ_NAME)
private val KOTLIN_BYTE_CLASS_ID = ClassId.topLevel(KOTLIN_BYTE_FQ_NAME)
private val KOTLIN_SHORT_CLASS_ID = ClassId.topLevel(KOTLIN_SHORT_FQ_NAME)
private val KOTLIN_INT_CLASS_ID = ClassId.topLevel(KOTLIN_INT_FQ_NAME)
