package io.github.composefluent.windows.toolkit.gradle

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations

/** Native contracts use KLIB identity and Kotlin's own declaration ABI renderer. */
@CacheableTask
abstract class VerifyWinRTNativeReferenceAbiTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val implementationKlib: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceKlib: RegularFileProperty
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty
    @get:Inject protected abstract val execOperations: ExecOperations

    @TaskAction fun verify() {
        val tool = NativeKlibContractReader(execOperations, javaLauncher.get(), compilerClasspath)
        val implementation = implementationKlib.get().asFile
        val reference = referenceKlib.get().asFile
        val implementationManifest = tool.manifest(implementation)
        val referenceManifest = tool.manifest(reference)
        for (key in listOf("unique_name", "compiler_version", "abi_version", "metadata_version", "native_targets")) {
            if (implementationManifest.getProperty(key) != referenceManifest.getProperty(key)) {
                throw GradleException("WinRT reference KLIB $key differs from its implementation: " +
                    "${referenceManifest.getProperty(key)} != ${implementationManifest.getProperty(key)}")
            }
        }
        val implementationAbi = tool.dump(implementation, "dump-abi")
        val referenceAbi = tool.dump(reference, "dump-abi")
        val reports = reportDirectory.get().asFile.apply { mkdirs() }
        reports.resolve("implementation.abi").writeText(implementationAbi)
        reports.resolve("reference.abi").writeText(referenceAbi)
        if (implementationAbi != referenceAbi) {
            throw GradleException("WinRT Native reference declarations differ from their implementation ABI. See $reports")
        }
        // Compare with the same compiler tool, not as a persistent cross-version IR format.
        // The first comment names the input artifact; retain every IR statement and signature.
        fun inlineIr(file: File) = tool.dump(file, "dump-ir-inlinable-functions")
            .replace(Regex("^// (\\d+) inlinable functions in [^\\n]+"), "// $1 inlinable functions")
        val implementationInline = inlineIr(implementation)
        val referenceInline = inlineIr(reference)
        reports.resolve("implementation.inline-ir").writeText(implementationInline)
        reports.resolve("reference.inline-ir").writeText(referenceInline)
        if (implementationInline != referenceInline) {
            throw GradleException("WinRT Native reference inline bodies differ from their implementation. See $reports")
        }
    }

}

internal class NativeKlibContractReader(
    private val execOperations: ExecOperations,
    private val launcher: JavaLauncher,
    private val compilerClasspath: org.gradle.api.file.FileCollection,
) {
    fun manifest(file: File): Properties = if (file.isDirectory) {
        file.resolve("default/manifest").inputStream().use { input -> Properties().apply { load(input) } }
    } else ZipFile(file).use { archive ->
        val entry = archive.getEntry("default/manifest")
            ?: throw GradleException("Missing KLIB manifest: $file")
        archive.getInputStream(entry).use { input -> Properties().apply { load(input) } }
    }

    fun dump(file: File, command: String): String {
        val output = ByteArrayOutputStream()
        execOperations.javaexec { spec ->
            spec.executable = launcher.executablePath.asFile.absolutePath
            spec.classpath = compilerClasspath
            spec.mainClass.set("org.jetbrains.kotlin.cli.utilities.MainKt")
            spec.jvmArgs("-Xmx1g", "-Dfile.encoding=UTF-8")
            spec.args("klib", command, file.absolutePath)
            spec.standardOutput = output
        }.assertNormalExitValue()
        return output.toString(Charsets.UTF_8.name()).replace("\r\n", "\n")
    }
}
