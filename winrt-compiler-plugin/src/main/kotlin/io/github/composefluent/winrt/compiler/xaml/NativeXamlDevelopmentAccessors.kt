package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/** A compiler-owned source marker; no runtime/public projection contract is added. */
internal const val JVM_XAML_DEVELOPMENT_ACCESSORS = "KOTLIN_WINRT_JVM_XAML_DEVELOPMENT_ACCESSORS"

/**
 * CsWinRT Discover separates SDK implementation bookkeeping from executable business uses.
 * These generated SDK accessors serve the JVM development transport, not XAML activation:
 * platformStartWinUIXamlHotReload on mingwX64 explicitly rejects development sessions and
 * returns no transport. Keep the separate SDK KClass identity edges and ordinary component
 * metadata, but do not serialize unused callbacks or discover their SDK member dependencies.
 * Native transport parity is tracked in winrt-compiler-plugin/ide/README.md.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun lowerNativeXamlDevelopmentAccessors(module: IrModuleFragment, context: IrPluginContext) {
    module.files.forEach { file ->
        file.declarations.filterIsInstance<IrSimpleFunction>().forEach functionLoop@{ function ->
            val developmentOnly = function.annotations.any { annotation ->
                (annotation.symbol.owner.parent as? IrClass)
                    ?.fqNameWhenAvailable?.asString() == "kotlin.Suppress" &&
                    (annotation.arguments.firstOrNull() as? IrVararg)?.elements
                        ?.filterIsInstance<IrConst>()?.any { it.value == JVM_XAML_DEVELOPMENT_ACCESSORS } == true
            }
            if (!developmentOnly) return@functionLoop
            check(function.visibility == DescriptorVisibilities.PRIVATE && function.parameters.isEmpty() &&
                function.returnType.isUnit()) { "JVM XAML development accessor must be a private parameterless Unit helper" }
            function.body = DeclarationIrBuilder(context, function.symbol).irBlockBody {}
        }
    }
}
