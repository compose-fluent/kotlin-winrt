package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/** Only isolated schema/symbol compilations may discard their executable declarations. */
internal fun IrModuleFragment.consumeXamlSemanticDeclarations() {
    // Native's FIR-to-KLIB serializer indexes IR files alongside the frontend file metadata.
    // Keep that correspondence even though this temporary artifact has no executable IR.
    // These outputs must never be used as a library or as a final application artifact.
    files.forEach { it.declarations.clear() }
}
