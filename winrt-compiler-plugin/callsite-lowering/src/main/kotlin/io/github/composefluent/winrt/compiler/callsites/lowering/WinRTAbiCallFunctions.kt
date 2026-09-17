@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name

/**
 * Shares only the physical invocation, after CsWinRT-style caller marshaling has completed.
 * No projected type, IID, ownership or codec participates in this platform boundary.
 * Native keeps the entry inline so LLVM can retain the direct typed pointer call.
 */
internal class WinRTAbiCallFunctions(private val inline: Boolean = false) {
    private val functions = mutableMapOf<Pair<IrFile, String>, IrSimpleFunction>()

    fun call(
        builder: DeclarationIrBuilder,
        context: IrPluginContext,
        owner: IrSimpleFunction,
        supportFiles: WinRTAbiSupportFiles,
        signature: String,
        callArguments: List<IrExpression>,
        resultType: IrType,
        emit: (DeclarationIrBuilder, IrSimpleFunction, List<IrExpression>) -> IrExpression,
    ): IrExpression {
        val source = requireNotNull(owner.abiContainingFile())
        val file = supportFiles.file(source, signature)
        val function = functions.getOrPut(file to signature) {
            // Include the owner for Native's safe file-local fallback. On JVM each signature
            // already has an isolated owner. Look up declarations across plugin invocations.
            val identity = signature + "|" + file.packageFqName + "|" +
                file.fileEntry.name.substringAfterLast('/').substringAfterLast('\\')
            val name = Name.identifier("kotlinWinRTAbiInvoke_" + supportFiles.identity(source, identity))
            file.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { it.name == name }
                ?: context.irFactory.buildFun {
                    this.name = name
                    visibility = DescriptorVisibilities.PUBLIC
                    returnType = resultType
                    isInline = inline
                }.apply {
                    parent = file
                    callArguments.forEachIndexed { index, argument -> addValueParameter("p$index", argument.type) }
                    val bodyBuilder = DeclarationIrBuilder(context, symbol)
                    body = bodyBuilder.irBlockBody {
                        +irReturn(emit(bodyBuilder, this@apply, parameters.map(bodyBuilder::irGet)))
                    }
                    file.declarations += this
                }
        }
        check(function.returnType == resultType && function.parameters.map { it.type } == callArguments.map { it.type }) {
            "A physical ABI identity must have one canonical Kotlin signature: $signature"
        }
        return builder.irCall(function.symbol).apply {
            callArguments.forEachIndexed { index, argument -> this.arguments[index] = argument }
        }
    }
}
