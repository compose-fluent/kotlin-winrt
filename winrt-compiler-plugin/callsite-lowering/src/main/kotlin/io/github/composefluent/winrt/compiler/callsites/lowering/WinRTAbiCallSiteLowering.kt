@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isNullable

/** No projected-type lookup, codec selection or ownership policy belongs in this pass. */
internal fun lowerWinRTAbiCallSite(
    function: IrSimpleFunction,
    pluginContext: IrPluginContext,
    backend: WinRTDirectCallBackend,
) {
    val parameters = function.parameters.filter { it.kind == IrParameterKind.Regular }
    require(function.typeParameters.isEmpty()) { "fixed ABI stubs cannot have type parameters" }
    require(parameters.size >= 2) { "fixed ABI stubs require a receiver and vtable slot" }
    require(function.returnType == pluginContext.irBuiltIns.intType) { "fixed ABI stubs must return an Int HRESULT" }
    require(parameters[0].type.classFqName?.asString() == "io.github.composefluent.winrt.runtime.RawComPtr" && !parameters[0].type.isNullable()) {
        "receiver must be a non-null RawComPtr"
    }
    require(parameters[1].type == pluginContext.irBuiltIns.intType) { "vtable slot must be an Int" }
    val carriers = parameters.drop(2).map { parameter ->
        require(!parameter.type.isNullable()) { "ABI carriers cannot be nullable" }
        WinRTProjectionCallSiteAbiCarrier.entries.singleOrNull { it.kotlinCarrierFqName == parameter.type.classFqName }
            ?: error("Unsupported raw ABI carrier ${parameter.type}")
    }
    val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
    val invocation = requireNotNull(backend.emit(
        builder, pluginContext, function,
        builder.irGet(parameters[0]), builder.irGet(parameters[1]),
        carriers, parameters.drop(2).map { builder.irGet(it) },
    )) { "Cannot emit fixed ABI call" }
    function.body = builder.irBlockBody { +builder.irReturn(invocation) }
}
