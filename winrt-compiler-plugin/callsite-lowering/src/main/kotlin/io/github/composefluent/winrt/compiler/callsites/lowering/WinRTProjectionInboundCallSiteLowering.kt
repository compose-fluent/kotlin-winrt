@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_INBOUND_CALL_SITE_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_INBOUND_ENTRY_POINT_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallWithSubstitutedType
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File
import java.util.Collections
import java.util.WeakHashMap

internal fun lowerWinRTProjectionInboundCallSites(
    moduleFragment: IrModuleFragment,
    pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
) {
    val semanticFunctions = mutableListOf<IrSimpleFunction>()
    moduleFragment.acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                if (declaration.annotations.any(::isInboundCallSiteAnnotation)) {
                    semanticFunctions += declaration
                }
                super.visitSimpleFunction(declaration)
            }
        },
    )
    if (semanticFunctions.isEmpty()) return

    val planner = WinRTProjectionCallSitePlanner(
        moduleFragment,
        pluginContext,
        WinRTProjectedTypeCanonicalizer(pluginContext),
    )
    val recipeLowering = runCatching {
        WinRTCallSiteRecipeLowering.create(pluginContext, moduleFragment)
    }.getOrElse { failure ->
        semanticFunctions.forEach { function ->
            pluginContext.reportInboundError(
                function,
                "cannot resolve typed inbound recipe symbols: ${failure.message}",
            )
        }
        return
    } ?: run {
        semanticFunctions.forEach { function ->
            pluginContext.reportInboundError(function, "cannot resolve typed inbound recipe symbols")
        }
        return
    }
    val symbols = InboundRuntimeSymbols.create(pluginContext, semanticFunctions.first().containingFile())
    if (symbols == null) {
        semanticFunctions.forEach { function ->
            pluginContext.reportInboundError(function, "cannot resolve inbound runtime symbols")
        }
        return
    }

    val entries = mutableMapOf<IrSimpleFunctionSymbol, IrSimpleFunction>()
    semanticFunctions.forEach { function ->
        if (function in loweredInboundSemanticFunctions) return@forEach
        val plan = planDirectInboundCallSite(function, planner)
        if (plan == null) {
            pluginContext.reportInboundError(function, "does not have a directly composable single-carrier ABI shape")
            return@forEach
        }
        validateDirectInboundCallSite(function)?.let { detail ->
            pluginContext.reportInboundError(function, detail)
            return@forEach
        }
        lowerSemanticTodoToUnit(function, pluginContext)
        val entry = synthesizeDirectInboundEntry(
            semantic = function,
            plan = plan,
            symbols = symbols,
            recipeLowering = recipeLowering,
            pluginContext = pluginContext,
        )
        entries[function.symbol] = entry
        loweredInboundSemanticFunctions += function
    }

    moduleFragment.transformChildrenVoid(
        object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                val transformed = super.visitCall(expression) as IrCall
                if (transformed.symbol.owner.fqNameWhenAvailable?.asString() != WINRT_PROJECTION_INBOUND_ENTRY_POINT_FQ_NAME) {
                    return transformed
                }
                val reference = transformed.arguments.firstOrNull()?.inboundFunctionReference()
                val entry = reference?.symbol?.let { symbol ->
                    (symbol as? IrSimpleFunctionSymbol)?.let(entries::get)
                }
                if (reference == null || entry == null) {
                    return transformed
                }
                return symbols.entryPointExpression(
                    builder = DeclarationIrBuilder(
                        pluginContext,
                        entry.symbol,
                        transformed.startOffset,
                        transformed.endOffset,
                    ),
                    entry = entry,
                    pluginContext = pluginContext,
                ) ?: transformed
            }
        },
    )
}

private fun synthesizeDirectInboundEntry(
    semantic: IrSimpleFunction,
    plan: DirectInboundCallSitePlan,
    symbols: InboundRuntimeSymbols,
    recipeLowering: WinRTCallSiteRecipeLowering,
    pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
): IrSimpleFunction {
    val file = semantic.containingFile()
        ?: error("kotlin-winrt cannot locate the inbound CallSite file owner.")
    val entry = pluginContext.irFactory.buildFun {
        startOffset = semantic.startOffset
        endOffset = semantic.endOffset
        origin = IrDeclarationOrigin.DEFINED
        name = Name.identifier("kotlinWinRTInbound_${semantic.name.asString()}_${semantic.fqNameWhenAvailable.toString().hashCode().toUInt().toString(16)}")
        visibility = DescriptorVisibilities.PRIVATE
        returnType = pluginContext.irBuiltIns.intType
    }.apply {
        parent = file
        addValueParameter("thisWord", pluginContext.irBuiltIns.longType)
        plan.parameters.forEachIndexed { index, parameter ->
            addValueParameter("arg$index", parameter.recipe.abiCarriers.single().irType(pluginContext))
        }
        if (plan.result != null) {
            addValueParameter("resultAddress", symbols.resultAddressType)
        }
    }
    val builder = DeclarationIrBuilder(pluginContext, entry.symbol, semantic.startOffset, semantic.endOffset)
    val entryParameters = entry.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    val thisWord = entryParameters.first()
    val semanticParameters = semantic.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    val semanticTarget = semanticParameters.first()
    entry.body = builder.irBlockBody {
        val success = builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
            val managedValue = builder.irCall(symbols.managedValue).apply {
                arguments[0] = builder.irGet(thisWord)
                // The CCW was constructed for this projected interface. Preserve that invariant in
                // IR so Native does not check the interface once for a cast and again for dispatch.
                type = semanticTarget.type
            }
            val projectedTarget = irTemporary(
                managedValue,
                nameHint = "projectedTarget",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            val projectedArguments = plan.parameters.mapIndexed { index, parameter ->
                irTemporary(
                    recipeLowering.decodeDirectInboundValue(
                        builder = builder,
                        recipe = parameter.recipe,
                        projectedType = parameter.parameter.type,
                        abiValue = builder.irGet(entryParameters[index + 1]),
                        pluginContext = pluginContext,
                    ) ?: error("kotlin-winrt cannot decode typed inbound parameter $index."),
                    nameHint = "projectedArg$index",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
            }
            val invocation = builder.irCall(semantic.symbol).apply {
                arguments[0] = builder.irGet(projectedTarget)
                projectedArguments.forEachIndexed { index, argument ->
                    arguments[index + 1] = builder.irGet(argument)
                }
            }
            if (plan.result == null) {
                +invocation
            } else {
                val result = irTemporary(
                    invocation,
                    nameHint = "projectedResult",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val resultAddress = entryParameters.last()
                +(
                    recipeLowering.emitDirectInboundResult(
                        builder = builder,
                        function = entry,
                        recipe = plan.result,
                        projectedValue = builder.irGet(result),
                        pluginContext = pluginContext,
                    ) { abiValue ->
                        symbols.writeResult(
                            builder = builder,
                            carrier = plan.result.abiCarriers.single(),
                            resultAddress = builder.irGet(resultAddress),
                            value = abiValue,
                        )
                    } ?: error("kotlin-winrt cannot encode and publish a typed inbound result.")
                )
            }
            if (plan.result == null) {
                +builder.irInt(0)
            }
        }
        +builder.irReturn(
            builder.irTry(
                type = pluginContext.irBuiltIns.intType,
                tryResult = success,
                catches = listOf(
                    catchThrowable(builder, entry, pluginContext) { failure ->
                        builder.irCall(symbols.failure).apply {
                            arguments[0] = builder.irGet(failure)
                        }
                    },
                ),
                finallyExpression = null,
            ),
        )
    }
    file.declarations += entry
    return entry
}

private data class DirectInboundCallSiteParameter(
    val parameter: IrValueParameter,
    val recipe: WinRTProjectionCallSiteRecipe,
)

private data class DirectInboundCallSitePlan(
    val parameters: List<DirectInboundCallSiteParameter>,
    val result: WinRTProjectionCallSiteRecipe?,
)

private fun planDirectInboundCallSite(
    function: IrSimpleFunction,
    planner: WinRTProjectionCallSitePlanner,
): DirectInboundCallSitePlan? {
    val parameters = function.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    if (parameters.isEmpty()) return null
    val inboundMetadata = function.annotations.singleOrNull(::isInboundCallSiteAnnotation) ?: return null
    val projectedParameters = parameters.drop(1).map { parameter ->
        val parameterMetadata = parameter.annotations.singleOrNull { annotation ->
            annotation.type.classFqName?.asString() == WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME
        } ?: return null
        DirectInboundCallSiteParameter(
            parameter = parameter,
            recipe = planner.directInboundRecipe(
                type = parameter.type,
                abiType = parameterMetadata.stringArgument("abiType"),
                usage = RecipeUsage.BORROWED_OUTPUT,
            ) ?: return null,
        )
    }
    val result = if (function.returnType.classFqName == KOTLIN_UNIT_FQ_NAME) {
        null
    } else {
        planner.directInboundRecipe(
            type = function.returnType,
            abiType = inboundMetadata.stringArgument("returnAbiType"),
            usage = RecipeUsage.INPUT,
        ) ?: return null
    }
    return DirectInboundCallSitePlan(projectedParameters, result)
}

private class InboundRuntimeSymbols private constructor(
    val managedValue: IrSimpleFunctionSymbol,
    val failure: IrSimpleFunctionSymbol,
    private val resultWriters: Map<WinRTProjectionCallSiteAbiCarrier, IrSimpleFunctionSymbol>,
    val resultAddressType: IrType,
    private val jvmEntryPoint: IrSimpleFunctionSymbol?,
    private val nativeEntryPoint: IrSimpleFunctionSymbol?,
    private val nativeStaticCFunctionOverloads: Map<Int, IrSimpleFunctionSymbol>,
) {
    fun writeResult(
        builder: DeclarationIrBuilder,
        carrier: WinRTProjectionCallSiteAbiCarrier,
        resultAddress: IrExpression,
        value: IrExpression,
    ): IrExpression? = resultWriters[carrier]?.let { writer ->
        builder.irCall(writer).apply {
            arguments[0] = resultAddress
            arguments[1] = value
        }
    }

    fun entryPointExpression(
        builder: DeclarationIrBuilder,
        entry: IrSimpleFunction,
        pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
    ): IrExpression? {
        jvmEntryPoint?.let { helper ->
            val file = entry.containingFile() ?: return null
            val sourceName = File(file.fileEntry.name).name
            val ownerClassName = PackagePartClassUtils
                .getPackagePartFqName(file.packageFqName, sourceName)
                .asString()
            return builder.irCall(helper).apply {
                arguments[0] = builder.irString(ownerClassName)
                arguments[1] = builder.irString(entry.name.asString())
            }
        }
        nativeEntryPoint?.let { helper ->
            val signatureTypes = entry.parameters
                .filter { parameter -> parameter.kind == IrParameterKind.Regular }
                .map { parameter -> parameter.type } + entry.returnType
            val staticCFunction = nativeStaticCFunctionOverloads[signatureTypes.size - 1]
                ?: return null
            val functionType = pluginContext.irBuiltIns
                .functionN(signatureTypes.size - 1)
                .symbol
                .typeWith(signatureTypes)
            val functionReference = IrFunctionReferenceImpl(
                constructorIndicator = null,
                startOffset = entry.startOffset,
                endOffset = entry.endOffset,
                type = functionType,
                origin = null,
                symbol = entry.symbol,
                reflectionTarget = entry.symbol,
            ).apply {
                initializeTargetShapeFromSymbol()
            }
            val staticFunctionPointer = builder.irCallWithSubstitutedType(
                callee = staticCFunction,
                typeArguments = signatureTypes,
            ).apply {
                arguments[0] = functionReference
            }
            return builder.irCall(helper).apply {
                // Keep this intrinsic intact until Native interop lowering. The KLib
                // serializer can represent the callable reference, while the target
                // backend still owns the final C bridge and calling convention.
                arguments[0] = staticFunctionPointer
            }
        }
        return null
    }

    companion object {
        fun create(
            pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
            fromFile: IrFile?,
        ): InboundRuntimeSymbols? {
            val managedValue = pluginContext.findInboundFunctions(WINRT_MANAGED_VALUE_CALLABLE_ID, fromFile)
                .singleOrNull() ?: return null
            val failure = pluginContext.findInboundFunctions(WINRT_INBOUND_FAILURE_CALLABLE_ID, fromFile)
                .singleOrNull() ?: return null
            val resultWriters = WINRT_INBOUND_RESULT_WRITER_NAMES.mapValues { (_, functionName) ->
                pluginContext.findInboundFunctions(
                    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier(functionName)),
                    fromFile,
                ).singleOrNull() ?: return null
            }
            val resultAddressTypes = resultWriters.values.map { writer ->
                writer.owner.parameters
                    .firstOrNull { parameter -> parameter.kind == IrParameterKind.Regular }
                    ?.type ?: return null
            }
            val resultAddressType = resultAddressTypes.firstOrNull() ?: return null
            if (resultAddressTypes.any { candidate -> candidate != resultAddressType }) return null
            val jvmEntryPoint = pluginContext.findInboundFunctions(WINRT_JVM_ENTRY_POINT_CALLABLE_ID, fromFile)
                .singleOrNull()
            val nativeEntryPoint = pluginContext.findInboundFunctions(WINRT_NATIVE_ENTRY_POINT_CALLABLE_ID, fromFile)
                .singleOrNull()
            if (jvmEntryPoint == null && nativeEntryPoint == null) return null
            val nativeStaticCFunctionOverloads = if (nativeEntryPoint == null) {
                emptyMap()
            } else {
                pluginContext.findInboundFunctions(
                    WINRT_NATIVE_STATIC_C_FUNCTION_CALLABLE_ID,
                    fromFile,
                ).asSequence()
                    .filter { function ->
                        function.owner.parameters.count { parameter ->
                            parameter.kind == IrParameterKind.Regular
                        } == 1
                    }
                    .groupBy { function -> function.owner.typeParameters.size - 1 }
                    .mapNotNull { (arity, overloads) ->
                        overloads.singleOrNull()?.let { arity to it }
                    }
                    .toMap()
            }
            return InboundRuntimeSymbols(
                managedValue = managedValue,
                failure = failure,
                resultWriters = resultWriters,
                resultAddressType = resultAddressType,
                jvmEntryPoint = jvmEntryPoint,
                nativeEntryPoint = nativeEntryPoint,
                nativeStaticCFunctionOverloads = nativeStaticCFunctionOverloads,
            )
        }
    }
}

private fun lowerSemanticTodoToUnit(
    function: IrSimpleFunction,
    pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
) {
    val builder = DeclarationIrBuilder(pluginContext, function.symbol)
    function.transformChildrenVoid(
        object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") {
                    return builder.irUnit()
                }
                return super.visitCall(expression)
            }
        },
    )
}

private fun validateDirectInboundCallSite(function: IrSimpleFunction): String? {
    if (function.typeParameters.isNotEmpty()) return "inbound stub must not declare type parameters"
    val parameters = function.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    if (parameters.isEmpty()) return "inbound stub must declare a projected target"
    if (parameters.first().type.isNullable()) return "inbound projected target must be non-null"
    var todoCount = 0
    function.body?.acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") todoCount += 1
                super.visitCall(expression)
            }
        },
    )
    if (todoCount != 1) return "inbound stub must contain exactly one TODO() placeholder, found $todoCount"
    return null
}

private fun WinRTProjectionCallSiteAbiCarrier.irType(
    pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
): IrType = when (this) {
    WinRTProjectionCallSiteAbiCarrier.ADDRESS -> pluginContext.irBuiltIns.longType
    WinRTProjectionCallSiteAbiCarrier.INT8 -> pluginContext.irBuiltIns.byteType
    WinRTProjectionCallSiteAbiCarrier.INT16 -> pluginContext.irBuiltIns.shortType
    WinRTProjectionCallSiteAbiCarrier.INT32 -> pluginContext.irBuiltIns.intType
    WinRTProjectionCallSiteAbiCarrier.INT64 -> pluginContext.irBuiltIns.longType
    WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> pluginContext.irBuiltIns.floatType
    WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> pluginContext.irBuiltIns.doubleType
}

private fun IrExpression.inboundFunctionReference(): IrFunctionReference? = when (this) {
    is IrFunctionReference -> this
    is IrTypeOperatorCall -> argument.inboundFunctionReference()
    else -> null
}

private fun isInboundCallSiteAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() == WINRT_PROJECTION_INBOUND_CALL_SITE_ANNOTATION_FQ_NAME

private fun IrFunctionAccessExpression.stringArgument(name: String): String {
    val index = symbol.owner.parameters.indexOfFirst { parameter -> parameter.name.asString() == name }
    require(index >= 0) { "annotation has no '$name' parameter" }
    val argument = arguments.getOrNull(index) ?: return ""
    return (argument as? org.jetbrains.kotlin.ir.expressions.IrConst)?.value as? String
        ?: error("annotation argument '$name' must be a constant string")
}

private fun catchThrowable(
    builder: DeclarationIrBuilder,
    function: IrSimpleFunction,
    pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
    result: (IrVariable) -> IrExpression,
): IrCatch {
    val parameter = IrVariableImpl(
        null,
        builder.startOffset,
        builder.endOffset,
        IrDeclarationOrigin.CATCH_PARAMETER,
        Name.identifier("failure"),
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

private tailrec fun IrDeclarationParent.containingFile(): IrFile? = when (this) {
    is IrFile -> this
    is IrDeclaration -> parent.containingFile()
    else -> null
}

private fun org.jetbrains.kotlin.backend.common.extensions.IrPluginContext.findInboundFunctions(
    callableId: CallableId,
    fromFile: IrFile?,
): Collection<IrSimpleFunctionSymbol> {
    val source = fromFile?.let { finderForSource(it).findFunctions(callableId) }.orEmpty()
    return source.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

@Suppress("DEPRECATION")
private fun org.jetbrains.kotlin.backend.common.extensions.IrPluginContext.reportInboundError(
    function: IrSimpleFunction,
    detail: String,
) {
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "kotlin-winrt inbound call site ${function.fqNameWhenAvailable ?: function.name} $detail.",
        null,
    )
}

private val WINRT_RUNTIME_PACKAGE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime")
private val WINRT_MANAGED_VALUE_CALLABLE_ID =
    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("winRTProjectionInboundManagedValue"))
private val WINRT_INBOUND_FAILURE_CALLABLE_ID =
    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("winRTProjectionInboundFailure"))
private val WINRT_JVM_ENTRY_POINT_CALLABLE_ID =
    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("winRTJvmProjectionInboundEntryPoint"))
private val WINRT_NATIVE_ENTRY_POINT_CALLABLE_ID =
    CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("winRTNativeProjectionInboundEntryPoint"))
private val WINRT_NATIVE_STATIC_C_FUNCTION_CALLABLE_ID =
    CallableId(FqName("kotlinx.cinterop"), Name.identifier("staticCFunction"))
private val KOTLIN_UNIT_FQ_NAME = FqName("kotlin.Unit")

private val WINRT_INBOUND_RESULT_WRITER_NAMES = mapOf(
    WinRTProjectionCallSiteAbiCarrier.ADDRESS to "winRTProjectionInboundWriteAddress",
    WinRTProjectionCallSiteAbiCarrier.INT8 to "winRTProjectionInboundWriteInt8",
    WinRTProjectionCallSiteAbiCarrier.INT16 to "winRTProjectionInboundWriteInt16",
    WinRTProjectionCallSiteAbiCarrier.INT32 to "winRTProjectionInboundWriteInt32",
    WinRTProjectionCallSiteAbiCarrier.INT64 to "winRTProjectionInboundWriteInt64",
    WinRTProjectionCallSiteAbiCarrier.FLOAT32 to "winRTProjectionInboundWriteFloat32",
    WinRTProjectionCallSiteAbiCarrier.FLOAT64 to "winRTProjectionInboundWriteFloat64",
)

private val loweredInboundSemanticFunctions = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<IrSimpleFunction, Boolean>()),
)
