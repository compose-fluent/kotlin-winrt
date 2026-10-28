package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*

/** Typed calls stay in the page, including private handlers. A stable native
 * delegate forwards to one compiled handler, analogous to CsWinRT EventSource's
 * native invoker and mutable managed handler state. Release the original token
 * through the existing connection state; retargeting never adds a subscription.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlReloadableEvent(context: IrPluginContext, builder: IrBuilderWithScope,
    klass: IrClass, receiver: IrValueParameter, target: IrVariable, delegateType: IrType,
    handler: IrSimpleFunction, className: String, path: String, hash: String, element: String, event: String): IrExpression = with(builder) {
    fun runtime(name: String) = context.referenceFunctions(CallableId(FqName("io.github.composefluent.winrt.runtime"), Name.identifier(name))).single()
    fun original() = irSamConversion(xamlFunctionReference(context, handler, irGet(receiver)), delegateType)
    val candidates = klass.functions.filter { !it.name.isSpecial && xamlEventHandlerMatches(it, delegateType) }
        .groupBy { it.name.asString() }.values.mapNotNull { it.singleOrNull() }.toMutableList()
    if (candidates.none { it.symbol == handler.symbol }) candidates += handler
    val routeClass = requireNotNull(context.referenceClass(ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinUIXamlEventRoute")))).owner
    val nameGetter = routeClass.properties.single { it.name.asString() == "handlerName" }.getter!!
    val delegateClass = requireNotNull(delegateType.classOrNull).owner
    val invoke = delegateClass.functions.single { it.name.asString() == "invoke" }
    val parameterTypes = invoke.parameters.filter { it.kind == IrParameterKind.Regular }
        .map { xamlIrMemberType(delegateType, delegateClass, it.type) }
    require(invoke.returnType == context.irBuiltIns.unitType) { "Reloadable XAML events must return Unit" }
    irIfThenElse(delegateType, irCall(runtime("isWinUIXamlHotReloadEnabled")), irBlock(resultType = delegateType) {
        val route = irTemporary(irCallConstructor(routeClass.constructors.single().symbol, emptyList()).apply {
            arguments[0] = irString(handler.name.asString())
            arguments[1] = irCall(context.referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("arrayOf"))).single()).apply {
                typeArguments[0] = context.irBuiltIns.stringType
                arguments[0] = irVararg(context.irBuiltIns.stringType, candidates.map { irString(it.name.asString()) })
            }
        })
        +irCall(runtime("registerWinUIXamlHotReloadEvent")).apply {
            arguments[0] = irGet(receiver); arguments[1] = irString(className); arguments[2] = irString(path)
            arguments[3] = irString(hash); arguments[4] = irString(element); arguments[5] = irGet(target)
            arguments[6] = irString(event); arguments[7] = irGet(route)
        }
        val forward = context.irFactory.buildFun {
            name = SpecialNames.ANONYMOUS; visibility = DescriptorVisibilities.LOCAL
            returnType = context.irBuiltIns.unitType; origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply { parent = scope.scopeOwnerSymbol.owner as IrDeclarationParent }
        val parameters = parameterTypes.mapIndexed { index, type -> forward.addValueParameter("p$index", type) }
        forward.body = DeclarationIrBuilder(context, forward.symbol).irBlockBody {
            val current = irTemporary(irCall(nameGetter.symbol).apply { dispatchReceiver = irGet(route) })
            +irWhen(context.irBuiltIns.unitType, mutableListOf()).apply {
                candidates.forEach { candidate ->
                    branches += irBranch(irEquals(irGet(current), irString(candidate.name.asString())), irCall(candidate.symbol).apply {
                        dispatchReceiver = irGet(receiver)
                        candidate.parameters.filter { it.kind == IrParameterKind.Regular }.forEachIndexed { index, parameter ->
                            arguments[parameter.indexInParameters] = irImplicitCast(irGet(parameters[index]), parameter.type)
                        }
                    })
                }
                branches += irElseBranch(irUnit()) // Removed event attribute disables dispatch.
            }
        }
        +irSamConversion(IrFunctionExpressionImpl(startOffset, endOffset,
            context.irBuiltIns.functionN(parameterTypes.size).symbol.typeWith(parameterTypes + context.irBuiltIns.unitType),
            forward, IrStatementOrigin.LAMBDA), delegateType)
    }, original())
}
