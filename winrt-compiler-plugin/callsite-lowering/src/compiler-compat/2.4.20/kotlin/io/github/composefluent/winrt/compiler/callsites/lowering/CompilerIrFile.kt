@file:OptIn(org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.name.FqName

internal fun compilerIrFile(entry: IrFileEntry, symbol: IrFileSymbol, packageName: FqName, module: IrModuleFragment): IrFile =
    IrFileImpl(entry, symbol, packageName, module)
