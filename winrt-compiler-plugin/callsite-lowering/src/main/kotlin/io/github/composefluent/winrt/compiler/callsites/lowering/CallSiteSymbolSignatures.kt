@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

internal fun IrSimpleFunction.matchesCallSiteSignature(
    parameterTypes: List<String>,
    resultType: String,
    extensionReceiverType: String? = null,
): Boolean = typeParameters.isEmpty() &&
    parameters.none { it.kind == IrParameterKind.Context } &&
    parameters.singleOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.callSiteTypeName() == extensionReceiverType &&
    parameters.filter { it.kind == IrParameterKind.Regular }.map { it.type.callSiteTypeName() } == parameterTypes &&
    returnType.callSiteTypeName() == resultType

internal fun IrType.callSiteTypeName(): String {
    val simple = this as? IrSimpleType ?: return toString()
    val name = classFqName?.asString() ?: return toString()
    val arguments = if (simple.arguments.isEmpty()) "" else simple.arguments.joinToString(",", "<", ">") { argument ->
        val projection = argument as? org.jetbrains.kotlin.ir.types.IrTypeProjection
        if (projection == null) "*" else {
            val variance = when (projection.variance) {
                org.jetbrains.kotlin.types.Variance.INVARIANT -> ""
                org.jetbrains.kotlin.types.Variance.IN_VARIANCE -> "in "
                org.jetbrains.kotlin.types.Variance.OUT_VARIANCE -> "out "
            }
            variance + projection.type.callSiteTypeName()
        }
    }
    return name + arguments + if (isNullable()) "?" else ""
}

internal fun IrSimpleFunctionSymbol.callSiteSignature(): String =
    "${owner.fqNameWhenAvailable}(${owner.parameters.filter { it.kind != IrParameterKind.DispatchReceiver }
        .joinToString { "${it.kind}: ${it.type.callSiteTypeName()}" }}): ${owner.returnType.callSiteTypeName()}"
