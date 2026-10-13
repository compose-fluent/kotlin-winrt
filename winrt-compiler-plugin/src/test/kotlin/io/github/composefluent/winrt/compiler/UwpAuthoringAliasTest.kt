package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.runtime.Guid
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

class UwpAuthoringAliasTest {
    @Test
    fun authored_interfaces_preserve_uwp_identity_after_typealias_expansion() {
        // CsWinRT Projections.cs selects the WUX ABI for shared managed interfaces.
        // Compile against the real runtime aliases to exercise K2's expanded IR names.
        val root = Files.createTempDirectory("uwp-authoring-alias-").toFile()
        val source = File(root, "Model.kt").apply { writeText("""
            package sample
            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
            import windows.ui.xaml.data.INotifyPropertyChanged
            import windows.ui.xaml.data.PropertyChangedEventHandler
            @WinRTAuthoredRuntimeClass
            internal class Model : INotifyPropertyChanged {
                override fun addPropertyChanged(handler: PropertyChangedEventHandler) { }
                override fun removePropertyChanged(handler: PropertyChangedEventHandler) { }
            }
        """.trimIndent()) }
        // This test inspects emitted candidate identities, without constructing the
        // model. Supply the regular build's constructor-registration prerequisite.
        val registrar = File(root, "Registrar.kt").apply { writeText("""
            package io.github.composefluent.winrt.projections.support
            internal object WinRTAuthoringTypeDetailsRegistrar {
                fun register() { }
            }
        """.trimIndent()) }
        val classpath = listOf(Unit::class.java, Guid::class.java).joinToString(File.pathSeparator) {
            File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }
        for (includeWinUi in listOf(false, true)) {
            val metadata = File(root, "metadata-$includeWinUi.tsv").apply { writeText(buildString {
                appendLine("Windows.UI.Xaml.Data.INotifyPropertyChanged\tInterface")
                if (includeWinUi) appendLine("Microsoft.UI.Xaml.Data.INotifyPropertyChanged\tInterface")
            }) }
            val candidates = File(root, "candidates-$includeWinUi.tsv")
            val diagnostics = ByteArrayOutputStream()
            val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", Runtime.version().feature().toString(),
                "-classpath", classpath, "-d", File(root, "classes-$includeWinUi").absolutePath,
                "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}",
                "-P", "plugin:io.github.composefluent.winrt.compiler:metadataIndex=${metadata.absolutePath}",
                "-P", "plugin:io.github.composefluent.winrt.compiler:authoredCandidatesOutput=${candidates.absolutePath}",
                "-P", "plugin:io.github.composefluent.winrt.compiler:xamlFramework=uwp", source.absolutePath, registrar.absolutePath)
            val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
            assertEquals(diagnostics.toString(), ExitCode.OK, result)
            val candidate = KotlinWinRTAuthoringCandidateFile.read(candidates.toPath()).single()
            assertEquals("sample.Model", candidate.sourceTypeName)
            assertEquals(listOf("Windows.UI.Xaml.Data.INotifyPropertyChanged"), candidate.winRTInterfaceNames)
        }
    }
}
