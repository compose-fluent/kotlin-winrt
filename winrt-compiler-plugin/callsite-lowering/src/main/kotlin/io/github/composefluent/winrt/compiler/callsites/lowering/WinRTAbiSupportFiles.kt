@file:OptIn(org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import java.security.MessageDigest
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.name.FqName

/**
 * CsWinRT keeps marshaling at the caller and calls a physical ABI signature. The JVM handle
 * and Native thunk are Kotlin platform adaptations of that signature, not projected-type state.
 * JVM signatures get isolated owners; Native uses a frontend-owned file without user top-level
 * state. Sharing must not initialize an unrelated user's state or eagerly initialize other JVM
 * signatures. This registry lives for one lowering invocation.
 */
internal class WinRTAbiSupportFiles(private val useExistingFile: Boolean = false) {
    private val files = mutableMapOf<Pair<IrModuleFragment, String>, IrFile>()

    fun identity(source: IrFile, signature: String): String = MessageDigest.getInstance("SHA-256")
        .digest((source.module.name.asString() + "|" + signature).toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun file(source: IrFile, signature: String): IrFile {
        if (useExistingFile) {
            // KLIB linking needs a frontend-owned file, not just an existing package. Choose
            // one without user top-level state so sharing cannot trigger unrelated initializers.
            // The zero-initialized generated thunk fields remain eligible on a second pass.
            return files.getOrPut(source.module to "native-owner") {
                source.module.files.asSequence()
                    .filterNot { it.fileEntry.name.startsWith("KotlinWinRTAbi_") }
                    .filter { it.metadata != null && it.declarations.isNotEmpty() }
                    .filter { file -> file.declarations.none {
                        it is IrProperty || it is IrField && !it.name.asString().startsWith("kotlinWinRTNative")
                    } }
                    .minByOrNull { it.packageFqName.asString() + "/" + it.fileEntry.name }
                    // No safe owner: retain file-local storage instead of changing initialization.
                    ?: return source
            }
        }
        return files.getOrPut(source.module to signature) {
            val suffix = identity(source, signature)
            val packageName = FqName("io.github.composefluent.winrt.generated.abi")
            val fileName = "KotlinWinRTAbi_$suffix.kt"
            // The bootstrap and full plugins may both lower this module. Reuse IR already
            // attached to it instead of relying on a cache local to one plugin invocation.
            source.module.files.singleOrNull {
                it.fileEntry.name == fileName && it.packageFqName == packageName
            }?.let { return@getOrPut it }
            IrFileImpl(
                NaiveSourceBasedFileEntryImpl(fileName, intArrayOf(0), 0),
                // Native KLIB serialization still requires a package fragment descriptor.
                IrFileSymbolImpl(EmptyPackageFragmentDescriptor(source.module.descriptor, packageName)),
                packageName,
            ).also {
                it.module = source.module
                source.module.files += it
            }
        }
    }
}
