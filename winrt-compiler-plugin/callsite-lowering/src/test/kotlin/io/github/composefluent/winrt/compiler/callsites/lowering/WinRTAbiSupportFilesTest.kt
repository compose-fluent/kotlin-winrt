@file:OptIn(org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.NaiveSourceBasedFileEntryImpl
import org.jetbrains.kotlin.name.FqName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class WinRTAbiSupportFilesTest {
    @Test
    fun bootstrap_and_full_plugin_passes_reuse_the_same_module_storage() {
        val module = IrModuleFragmentImpl(DefaultBuiltIns.Instance.builtInsModule)
        val packageName = FqName("test")
        val source = IrFileImpl(
            NaiveSourceBasedFileEntryImpl("Input.kt", intArrayOf(0), 0),
            IrFileSymbolImpl(EmptyPackageFragmentDescriptor(module.descriptor, packageName)),
            packageName,
        ).also { it.module = module; module.files += it }
        val bootstrap = WinRTAbiSupportFiles().file(source, "jvm-hresult|ADDRESS")
        val full = WinRTAbiSupportFiles()
        assertSame(bootstrap, full.file(source, "jvm-hresult|ADDRESS"))
        assertNotSame(bootstrap, full.file(source, "jvm-hresult|INT64"))
        assertEquals(3, module.files.size)
        // The descriptor is required by the Native KLIB serializer, even for synthesized files.
        assertEquals(bootstrap.packageFqName, bootstrap.symbol.descriptor.fqName)
        val native = WinRTAbiSupportFiles(useExistingFile = true).file(source, "native|HRESULT|ADDRESS")
        assertSame(source, native)
        assertSame(native, WinRTAbiSupportFiles(useExistingFile = true).file(source, "native|HRESULT|ADDRESS"))
        // Without a frontend-owned safe file, another caller must keep its own file-local
        // storage rather than borrowing the first caller's initialization context.
        val otherSource = IrFileImpl(
            NaiveSourceBasedFileEntryImpl("Other.kt", intArrayOf(0), 0),
            IrFileSymbolImpl(EmptyPackageFragmentDescriptor(module.descriptor, packageName)),
            packageName,
        ).also { it.module = module; module.files += it }
        val fallback = WinRTAbiSupportFiles(useExistingFile = true)
        assertSame(source, fallback.file(source, "native|HRESULT|ADDRESS"))
        assertSame(otherSource, fallback.file(otherSource, "native|HRESULT|ADDRESS"))
    }
}
