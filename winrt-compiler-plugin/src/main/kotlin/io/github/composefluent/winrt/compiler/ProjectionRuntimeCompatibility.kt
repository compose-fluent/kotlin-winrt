package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.WINRT_PROJECTION_RUNTIME_ABI_VERSION
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * CsWinRT InteropGenerator.Emit.CreateInteropReferences validates the resolved runtime assembly.
 * Reading constants through IR checks the actual compilation libraries on JVM and Native,
 * rather than the toolkit's embedded runtime or a dependency's self-reported identity JSON.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun validateResolvedProjectionRuntimeAbi(context: IrPluginContext, reportError: (String) -> Unit): Boolean {
    var compatible = true
    for (name in listOf("WINRT_PROJECTION_RUNTIME_ABI_VERSION", "WINRT_RUNTIME_IMPLEMENTATION_ABI_VERSION")) {
        val declarations = context.referenceProperties(CallableId(
            FqName("io.github.composefluent.winrt.runtime"), Name.identifier(name),
        ))
        val actual = declarations.singleOrNull()?.owner?.backingField?.initializer?.expression
            .let { it as? IrConst }?.value as? Int
        if (actual != WINRT_PROJECTION_RUNTIME_ABI_VERSION) {
            compatible = false
            reportError("Incompatible resolved Kotlin/WinRT runtime: $name is ${actual ?: "missing or ambiguous"}; " +
                "this compiler requires ABI $WINRT_PROJECTION_RUNTIME_ABI_VERSION. " +
                "Align winrt-runtime and winrt-runtime-contracts with the projection toolkit.")
        }
    }
    return compatible
}
