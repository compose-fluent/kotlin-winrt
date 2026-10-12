package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.functions

/** Check the projected delegate, including Kotlin nullability, before exporting CLR-only symbols. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlEventHandlerMatches(handler: IrSimpleFunction, delegateType: IrType): Boolean {
    val delegateClass = delegateType.classOrNull?.owner ?: return false
    val invoke = delegateClass.functions.singleOrNull { it.name.asString() == "invoke" } ?: return false
    val parameters = handler.parameters.filter { it.kind == IrParameterKind.Regular }
    val delegateParameters = invoke.parameters.filter { it.kind == IrParameterKind.Regular }
    // C# method-group conversion accepts a base argument type but cannot narrow nullability.
    return !handler.isSuspend && handler.typeParameters.isEmpty() && handler.dispatchReceiverParameter != null &&
        handler.parameters.none { it.kind == IrParameterKind.ExtensionReceiver || it.kind == IrParameterKind.Context } &&
        handler.returnType == invoke.returnType && parameters.size == delegateParameters.size &&
        parameters.zip(delegateParameters).all { (destination, source) ->
            // Substitute T before inspecting nullability: T's nullable upper bound
            // does not make EventHandler<Color>'s Color argument nullable.
            val sourceType = xamlIrMemberType(delegateType, delegateClass, source.type)
            destination.varargElementType == null &&
                (!sourceType.isNullable() || destination.type.isNullable()) &&
                destination.type.classOrNull?.let { sourceType.isSubtypeOfClass(it) } == true
        }
}
