@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WINRT_ENUM_CONSTANT_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteMetadata
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterDirection
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultKind
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionParameterMetadata
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.inline
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irShort
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
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
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.Collections
import java.util.WeakHashMap

@OptIn(ExperimentalCompilerApi::class)
class WinRTProjectionCallSiteCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.composefluent.winrt.compiler.callsites"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(WinRTProjectionCallSiteIrGenerationExtension())
    }
}

class WinRTProjectionCallSiteIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) = lowerWinRTProjectionCallSites(moduleFragment, pluginContext)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun lowerWinRTProjectionCallSites(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    guidSignaturesByKotlinClass: Map<String, String> = emptyMap(),
) {
    lowerWinRTManagedProjectionStateOwners(moduleFragment, pluginContext)
    lowerWinRTGuidGeneratorCalls(moduleFragment, pluginContext, guidSignaturesByKotlinClass)
    lowerWinRTGenericDelegateSamReferences(moduleFragment, pluginContext, guidSignaturesByKotlinClass)
    lowerWinRTEnumConstantReads(moduleFragment, pluginContext)
    val annotatedFunctions = mutableListOf<Pair<IrSimpleFunction, IrFunctionAccessExpression>>()
    val inlineCallSites = mutableListOf<InlineProjectionCallSite>()
    moduleFragment.acceptChildrenVoid(
        object : IrVisitorVoid() {
            private fun collectInlineCallSites(statements: List<IrStatement>) {
                statements.forEachIndexed { index, statement ->
                    val result = statement as? IrVariable ?: return@forEachIndexed
                    val annotation = result.annotations.singleOrNull(::isProjectionCallSiteAnnotation)
                        ?: return@forEachIndexed
                    val arguments = statements.subList(0, index)
                        .asReversed()
                        .takeWhile { candidate ->
                            candidate is IrVariable && candidate.name.asString().startsWith(INLINE_CALL_SITE_ARGUMENT_PREFIX)
                        }
                        .asReversed()
                        .map { candidate -> candidate as IrVariable }
                    inlineCallSites += InlineProjectionCallSite(result, annotation, arguments)
                }
            }

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                declaration.annotations.singleOrNull(::isProjectionCallSiteAnnotation)
                    ?.let { annotation -> annotatedFunctions += declaration to annotation }
                super.visitSimpleFunction(declaration)
            }

            override fun visitBlockBody(body: IrBlockBody) {
                collectInlineCallSites(body.statements)
                super.visitBlockBody(body)
            }

            override fun visitBlock(expression: IrBlock) {
                collectInlineCallSites(expression.statements)
                super.visitBlock(expression)
            }
        },
    )
    lowerWinRTProjectionInboundCallSites(moduleFragment, pluginContext)

    if (annotatedFunctions.isEmpty() && inlineCallSites.isEmpty()) return

    val projectedTypes = WinRTProjectedTypeCanonicalizer(pluginContext)
    val planner = WinRTProjectionCallSitePlanner(moduleFragment, projectedTypes)
    val symbolResolution = runCatching {
        WinRTCallSiteRecipeLowering.create(pluginContext, moduleFragment)
    }
    val symbols = symbolResolution.getOrNull()
    val symbolResolutionDetail = symbolResolution.exceptionOrNull()?.message
    for ((function, annotation) in annotatedFunctions) {
        if (function in loweredCallSiteFunctions) continue
        val metadata = parseMetadata(annotation, function.regularParameters().drop(2), function, pluginContext) ?: continue
        val plan = runCatching { planner.plan(function, metadata) }.getOrElse { failure ->
            pluginContext.reportError(function, "cannot plan typed ABI lowering: ${failure.message}")
            continue
        }
        validateCallSiteFunction(function, plan, projectedTypes)?.let { error ->
            pluginContext.reportError(function, error)
            continue
        }
        if (!function.hasTodoPlaceholder()) {
            pluginContext.reportError(function, "must contain a TODO() placeholder body before lowering")
            continue
        }
        if (symbols == null) {
            pluginContext.reportError(
                function,
                "cannot resolve the shared recipe-lowering runtime symbols" +
                    symbolResolutionDetail?.let { ": $it" }.orEmpty(),
            )
            continue
        }
        if (!symbols.lowerPlan(function, plan.descriptor, plan.callerOwnedConstructor, pluginContext)) {
            pluginContext.reportError(
                function,
                "cannot lower the typed call-site plan $metadata" +
                    symbols.lastFailureDetail?.let { detail -> ": $detail" }.orEmpty(),
            )
            continue
        }
        loweredCallSiteFunctions += function
    }

    for (callSite in inlineCallSites) {
        val result = callSite.result
        if (result in loweredInlineCallSiteResults) continue
        val metadata = parseMetadata(
            callSite.annotation,
            callSite.arguments.drop(2),
            result,
            pluginContext,
        ) ?: continue
        val plan = runCatching { planner.plan(createInlineCallSiteFunction(callSite, pluginContext), metadata) }
            .getOrElse { failure ->
                pluginContext.reportError(result, "cannot plan typed ABI lowering: ${failure.message}")
                continue
            }
        validateInlineCallSiteMarker(callSite, plan.descriptor)?.let { error ->
            pluginContext.reportError(result, error)
            continue
        }
        val function = createInlineCallSiteFunction(callSite, pluginContext)
        validateCallSiteFunction(function, plan, projectedTypes)?.let { error ->
            pluginContext.reportError(result, error)
            continue
        }
        if (symbols == null) {
            pluginContext.reportError(
                result,
                "cannot resolve the shared recipe-lowering runtime symbols" +
                    symbolResolutionDetail?.let { ": $it" }.orEmpty(),
            )
            continue
        }
        if (!symbols.lowerPlan(function, plan.descriptor, plan.callerOwnedConstructor, pluginContext)) {
            pluginContext.reportError(
                result,
                "cannot lower the typed call-site plan $metadata" +
                    symbols.lastFailureDetail?.let { detail -> ": $detail" }.orEmpty(),
            )
            continue
        }
        val parent = result.parent as IrFunction
        result.initializer = function.inline(target = parent, arguments = callSite.arguments, moveBody = false)
        loweredInlineCallSiteResults += result
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun lowerWinRTEnumConstantReads(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
) {
    moduleFragment.transformChildrenVoid(
        object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                val call = super.visitCall(expression) as IrCall
                val annotations = call.symbol.owner.annotations.filter(::isWinRTEnumConstantAnnotation)
                if (annotations.isEmpty()) return call
                if (annotations.size != 1) {
                    pluginContext.reportEnumConstantError(call.symbol.owner, "has multiple enum-constant annotations")
                    return call
                }
                val valueBits = runCatching { annotations.single().longArgument("valueBits") }
                    .getOrElse { failure ->
                        pluginContext.reportEnumConstantError(
                            call.symbol.owner,
                            "has invalid enum-constant metadata: ${failure.message}",
                        )
                        return call
                    }
                val enumConstructor = call.type.classOrNull?.singleValueConstructor()
                val enumParameter = enumConstructor?.owner?.regularParameters()?.singleOrNull()
                if (enumConstructor == null || enumParameter == null) {
                    pluginContext.reportEnumConstantError(
                        call.symbol.owner,
                        "must return a value type with exactly one constructor parameter",
                    )
                    return call
                }
                val builderScope = currentScope?.scope?.scopeOwnerSymbol
                if (builderScope == null) {
                    pluginContext.reportEnumConstantError(call.symbol.owner, "has no lowering scope")
                    return call
                }
                val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                val constructorArgument = builder.integralConstant(valueBits, enumParameter.type)
                if (constructorArgument == null) {
                    pluginContext.reportEnumConstantError(
                        call.symbol.owner,
                        "has unsupported enum carrier ${enumParameter.type.classFqName}",
                    )
                    return call
                }
                return builder.irCall(enumConstructor).apply {
                    arguments[0] = constructorArgument
                }
            }
        },
    )
}

private fun DeclarationIrBuilder.integralConstant(valueBits: Long, type: IrType): IrExpression? =
    when (type.classFqName) {
        KOTLIN_BYTE_FQ_NAME -> irByte(valueBits.toByte())
        KOTLIN_SHORT_FQ_NAME -> irShort(valueBits.toShort())
        KOTLIN_INT_FQ_NAME -> irInt(valueBits.toInt())
        KOTLIN_LONG_FQ_NAME -> irLong(valueBits)
        KOTLIN_UBYTE_FQ_NAME -> unsignedIntegralConstant(type, irByte(valueBits.toByte()))
        KOTLIN_USHORT_FQ_NAME -> unsignedIntegralConstant(type, irShort(valueBits.toShort()))
        KOTLIN_UINT_FQ_NAME -> unsignedIntegralConstant(type, irInt(valueBits.toInt()))
        KOTLIN_ULONG_FQ_NAME -> unsignedIntegralConstant(type, irLong(valueBits))
        else -> null
    }

private fun DeclarationIrBuilder.unsignedIntegralConstant(
    type: IrType,
    carrier: IrExpression,
): IrExpression? = type.classOrNull?.singleValueConstructor()?.let { constructor ->
    irCall(constructor).apply { arguments[0] = carrier }
}

private data class InlineProjectionCallSite(
    val result: IrVariable,
    val annotation: IrFunctionAccessExpression,
    val arguments: List<IrVariable>,
)

private fun parseMetadata(
    annotation: IrFunctionAccessExpression,
    parameters: List<IrValueParameter>,
    function: IrSimpleFunction,
    pluginContext: IrPluginContext,
): WinRTProjectionCallSiteMetadata? = runCatching {
    annotation.callSiteMetadataForParameters(parameters)
}.getOrElse { failure ->
        pluginContext.reportError(function, "has invalid structured metadata: ${failure.message}")
        null
}

private fun parseMetadata(
    annotation: IrFunctionAccessExpression,
    parameters: List<IrVariable>,
    variable: IrVariable,
    pluginContext: IrPluginContext,
): WinRTProjectionCallSiteMetadata? = runCatching {
    annotation.callSiteMetadataForVariables(parameters)
}.getOrElse { failure ->
        pluginContext.reportError(variable, "has invalid structured metadata: ${failure.message}")
        null
}

private fun IrFunctionAccessExpression.callSiteMetadataForParameters(
    parameters: List<IrValueParameter>,
): WinRTProjectionCallSiteMetadata = callSiteMetadataFromAnnotations(
    parameterAnnotations = parameters.map { parameter -> parameter.annotations },
)

private fun IrFunctionAccessExpression.callSiteMetadataForVariables(
    parameters: List<IrVariable>,
): WinRTProjectionCallSiteMetadata = callSiteMetadataFromAnnotations(
    parameterAnnotations = parameters.map { parameter -> parameter.annotations },
)

private fun IrFunctionAccessExpression.callSiteMetadataFromAnnotations(
    parameterAnnotations: List<List<IrFunctionAccessExpression>>,
): WinRTProjectionCallSiteMetadata = WinRTProjectionCallSiteMetadata(
    hResultPolicy = enumArgument(
        name = "hResult",
        default = WinRTProjectionCallSiteHResultPolicy.CHECK,
    ),
    resultKind = enumArgument(
        name = "result",
        default = WinRTProjectionCallSiteResultKind.INFER,
    ),
    returnAbiType = stringArgument("returnAbiType"),
    parameters = parameterAnnotations.mapIndexed { index, annotations ->
        val matches = annotations.filter(::isProjectionParameterAnnotation)
        require(matches.size <= 1) { "parameter $index has multiple projection-parameter annotations" }
        matches.singleOrNull()?.let { parameterAnnotation ->
            WinRTProjectionParameterMetadata(
                direction = parameterAnnotation.enumArgument(
                    name = "direction",
                    default = WinRTProjectionCallSiteParameterDirection.IN,
                ),
                abiType = parameterAnnotation.stringArgument("abiType"),
            )
        } ?: WinRTProjectionParameterMetadata()
    },
)

private inline fun <reified T : Enum<T>> IrFunctionAccessExpression.enumArgument(
    name: String,
    default: T,
): T {
    val argument = namedAnnotationArgument(name) ?: return default
    val enumName = (argument as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
        ?: error("annotation argument '$name' must be a constant enum value")
    return enumValues<T>().singleOrNull { value -> value.name == enumName }
        ?: error("annotation argument '$name' has unknown ${T::class.simpleName} value '$enumName'")
}

private fun IrFunctionAccessExpression.stringArgument(name: String): String {
    val argument = namedAnnotationArgument(name) ?: return ""
    return (argument as? IrConst)?.value as? String
        ?: error("annotation argument '$name' must be a constant string")
}

private fun IrFunctionAccessExpression.longArgument(name: String): Long {
    val argument = namedAnnotationArgument(name)
        ?: error("annotation argument '$name' is required")
    return (argument as? IrConst)?.value as? Long
        ?: error("annotation argument '$name' must be a constant Long")
}

private fun IrFunctionAccessExpression.namedAnnotationArgument(name: String): IrExpression? {
    val index = symbol.owner.parameters.indexOfFirst { parameter -> parameter.name.asString() == name }
    require(index >= 0) { "annotation has no '$name' parameter" }
    return arguments.getOrNull(index)
}

private fun validateInlineCallSiteMarker(
    callSite: InlineProjectionCallSite,
    descriptor: WinRTProjectionCallSiteDescriptor,
): String? {
    if (callSite.result.name.asString() != INLINE_CALL_SITE_RESULT_NAME) {
        return "must use the generated local result name $INLINE_CALL_SITE_RESULT_NAME"
    }
    if (callSite.result.initializer?.hasTodoPlaceholder() != true) {
        return "must contain a TODO() placeholder initializer before lowering"
    }
    val expected = descriptor.functionParameterCount + 2
    if (callSite.arguments.size != expected) return "must be preceded by exactly $expected typed call-site arguments"
    callSite.arguments.forEachIndexed { index, argument ->
        if (argument.name.asString() != "$INLINE_CALL_SITE_ARGUMENT_PREFIX$index") {
            return "argument $index must use the generated local name $INLINE_CALL_SITE_ARGUMENT_PREFIX$index"
        }
    }
    if (callSite.result.parent !is IrFunction || callSite.arguments.any { it.parent != callSite.result.parent }) {
        return "must keep its typed arguments and result in one function body"
    }
    return null
}

private fun createInlineCallSiteFunction(
    callSite: InlineProjectionCallSite,
    pluginContext: IrPluginContext,
): IrSimpleFunction {
    val parent = callSite.result.parent as IrFunction
    return pluginContext.irFactory.buildFun {
        startOffset = callSite.result.startOffset
        endOffset = callSite.result.endOffset
        name = Name.special("<winrt-call-site>")
        visibility = DescriptorVisibilities.LOCAL
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        returnType = callSite.result.type
    }.apply {
        this.parent = parent
        callSite.arguments.forEach { argument -> addValueParameter(argument.name, argument.type) }
    }
}

private fun isProjectionCallSiteAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() == WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME

private fun isWinRTEnumConstantAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() == WINRT_ENUM_CONSTANT_ANNOTATION_FQ_NAME

private fun isProjectionParameterAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() == WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME

private val loweredCallSiteFunctions = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<IrSimpleFunction, Boolean>()),
)
private val loweredInlineCallSiteResults = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<IrVariable, Boolean>()),
)

private fun validateCallSiteFunction(
    function: IrSimpleFunction,
    plan: PlannedWinRTProjectionCallSite,
    projectedTypes: WinRTProjectedTypeCanonicalizer,
): String? {
    val descriptor = plan.descriptor
    if (function.typeParameters.isNotEmpty()) return "must not declare type parameters"
    val parameters = function.regularParameters()
    if (parameters.size != descriptor.functionParameterCount + 2) {
        return "must declare receiver, slot, and exactly ${descriptor.functionParameterCount} marshaler parameters"
    }
    if (parameters[0].type.classFqName != WINRT_COM_OBJECT_REFERENCE_FQ_NAME) {
        return "must use ComObjectReference as its first parameter"
    }
    if (parameters[0].type.isNullable()) return "must use a non-null ComObjectReference receiver"
    if (parameters[1].type.classFqName != KOTLIN_INT_FQ_NAME) return "must use Int as its vtable-slot parameter"
    if (parameters[1].type.isNullable()) return "must use a non-null Int vtable-slot parameter"

    var parameterIndex = 2
    descriptor.slots.forEachIndexed { slotIndex, slot ->
        if (slot.functionParameterCount == 0) return@forEachIndexed
        val actual = parameters[parameterIndex++].type
        val expected = slot.recipe.projectedKotlinTypeName?.let(projectedTypes::canonicalize)
            ?: return "slot $slotIndex has a malformed or open projected type signature '${slot.recipe.typeSignature}'"
        if (slot.direction == WinRTProjectionCallSiteSlotDirection.OUT) {
            val holder = actual as? IrSimpleType
                ?: return "slot $slotIndex must use WinRTOut<$expected>"
            if (holder.classFqName != WINRT_OUT_FQ_NAME || holder.isNullable()) {
                return "slot $slotIndex must use non-null WinRTOut<$expected>"
            }
            val actualValue = holder.arguments.singleOrNull()?.typeOrNull?.let(projectedTypes::canonicalize)
                ?: return "slot $slotIndex must use closed WinRTOut<$expected>"
            if (actualValue != expected) {
                return "slot $slotIndex expects WinRTOut<$expected> but declares WinRTOut<$actualValue>"
            }
        } else {
            val actualType = projectedTypes.canonicalize(actual)
                ?: return "slot $slotIndex must use the closed projected type $expected"
            if (actualType != expected) {
                return "slot $slotIndex expects $expected but declares $actualType"
            }
        }
    }
    val projectedResultTypeName = descriptor.projectedResultTypeName
    val expectedResult = when {
        descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN -> KOTLIN_INT_FQ_NAME.asString()
        plan.callerOwnedConstructor != null -> projectedTypes.canonicalize(function.returnType)
            ?: return "must return a closed caller-owned result type"
        projectedResultTypeName == null -> KOTLIN_UNIT_FQ_NAME.asString()
        else -> projectedTypes.canonicalize(projectedResultTypeName)
            ?: return "has a malformed or open projected result type '$projectedResultTypeName'"
    }
    val actualResult = projectedTypes.canonicalize(function.returnType)
        ?: return "must return the closed projected type $expectedResult"
    if (actualResult != expectedResult) {
        return "expects result $expectedResult but declares $actualResult"
    }
    return null
}

private fun IrSimpleFunction.hasTodoPlaceholder(): Boolean = body?.hasTodoPlaceholder() == true

private fun IrSimpleFunction.countTodoPlaceholders(): Int {
    var count = 0
    body?.acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") count += 1
                super.visitCall(expression)
            }
        },
    )
    return count
}

private fun IrElement.hasTodoPlaceholder(): Boolean {
    if (this is IrCall && symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") return true
    var found = false
    acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (!found) element.acceptChildrenVoid(this)
            }
            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") found = true
                else super.visitCall(expression)
            }
        },
    )
    return found
}

private fun IrFunction.regularParameters() = parameters.filter { it.kind == IrParameterKind.Regular }

@Suppress("DEPRECATION")
private fun IrPluginContext.reportError(function: IrSimpleFunction, detail: String) {
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "kotlin-winrt call site ${function.fqNameWhenAvailable ?: function.name} $detail.",
        null,
    )
}

@Suppress("DEPRECATION")
private fun IrPluginContext.reportError(variable: IrVariable, detail: String) {
    messageCollector.report(CompilerMessageSeverity.ERROR, "kotlin-winrt call site ${variable.name} $detail.", null)
}

@Suppress("DEPRECATION")
private fun IrPluginContext.reportEnumConstantError(function: IrSimpleFunction, detail: String) {
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "kotlin-winrt enum constant ${function.fqNameWhenAvailable ?: function.name} $detail.",
        null,
    )
}

private fun IrPluginContext.findClassSymbol(classId: ClassId, fromFile: IrFile? = null): IrClassSymbol? =
    fromFile?.let { finderForSource(it).findClass(classId) } ?: finderForBuiltins().findClass(classId)

private fun IrPluginContext.findFunctionSymbols(
    callableId: CallableId,
    fromFile: IrFile? = null,
): Collection<IrSimpleFunctionSymbol> {
    val source = fromFile?.let { finderForSource(it).findFunctions(callableId) }.orEmpty()
    return source.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

private fun IrClassSymbol.functionNamed(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { it.name.asString() == name }?.symbol

private fun IrClassSymbol.functionNamedWithRegularParameterCount(name: String, count: Int): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>()
        .singleOrNull { it.name.asString() == name && it.regularParameters().size == count }
        ?.symbol

private fun IrClassSymbol.propertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrProperty>().singleOrNull { it.name.asString() == name }?.getter?.symbol

private fun IrClassSymbol.singleValueConstructor(): IrConstructorSymbol? =
    owner.declarations.filterIsInstance<IrConstructor>().singleOrNull { it.regularParameters().size == 1 }?.symbol

private val WINRT_RUNTIME_PACKAGE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime")
private val WINRT_COM_VTABLE_INVOKER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")
private val WINRT_COM_OBJECT_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComObjectReference")
private val WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeScalarScratchFrame")
private val WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeHStringReferenceFrame")
private val WINRT_NATIVE_STRUCT_ADAPTER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.NativeStructAdapter")
private val WINRT_MARSHALER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.Marshaler")
private val WINRT_RAW_ADDRESS_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawAddress")
private val WINRT_RAW_COM_PTR_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawComPtr")
private val WINRT_OUT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.WinRTOut")
private val WINRT_HRESULT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.HResult")
private val WINRT_INSPECTABLE_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.InspectableReference")
private val WINRT_IUNKNOWN_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IUnknownReference")
private val KOTLIN_UNIT_FQ_NAME = FqName("kotlin.Unit")
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
private val KOTLIN_STRING_FQ_NAME = FqName("kotlin.String")

private const val INLINE_CALL_SITE_ARGUMENT_PREFIX = "__winrtCallSiteArgument"
private const val INLINE_CALL_SITE_RESULT_NAME = "__winrtCallSiteResult"
