package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

abstract class ValidateWinRTNativeAuthoringExportsTask : DefaultTask() {
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSharedLibraryFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val objdumpExecutable: Property<String>

    @TaskAction
    fun validate() {
        val exportedManifests = authoredHostManifestFiles.files.filter(::authoredHostManifestDeclaresActivatableClasses)
        if (exportedManifests.isEmpty()) {
            return
        }
        val dlls = nativeSharedLibraryFiles.files
            .filter { file -> file.isFile && file.extension.equals("dll", ignoreCase = true) }
            .distinctBy { file -> file.absoluteFile.normalize().path.lowercase() }
        require(dlls.isNotEmpty()) {
            "Kotlin/WinRT native authoring export validation requires a linked mingwX64 shared library when " +
                "authored host manifests declare activatable classes: ${exportedManifests.joinToString { it.absolutePath }}"
        }
        val exportReader = NativeExportReader(objdumpExecutable.orNull.orEmpty())
        dlls.forEach { dll ->
            val exports = exportReader.readExports(dll)
            val missing = REQUIRED_EXPORTS.filterNot { export -> export in exports }
            require(missing.isEmpty()) {
                "Kotlin/WinRT native authoring shared library '${dll.absolutePath}' is missing exports: " +
                    missing.joinToString()
            }
        }
    }

    private class NativeExportReader(private val configuredExecutable: String) {
        fun readExports(dll: File): Set<String> {
            val command = exportCommand(dll)
            val output = run(command)
            return REQUIRED_EXPORTS.filterTo(mutableSetOf()) { export -> output.contains(export) }
        }

        private fun exportCommand(dll: File): List<String> {
            configuredExecutable.trim().takeIf(String::isNotEmpty)?.let { executable ->
                return objdumpCommand(executable, dll)
            }
            findExecutable("llvm-objdump.exe")?.let { executable -> return objdumpCommand(executable, dll) }
            findExecutable("llvm-objdump")?.let { executable -> return objdumpCommand(executable, dll) }
            findExecutable("objdump.exe")?.let { executable -> return objdumpCommand(executable, dll) }
            findExecutable("dumpbin.exe")?.let { executable -> return listOf(executable, "/exports", dll.absolutePath) }
            error("Kotlin/WinRT native authoring export validation requires llvm-objdump, objdump, or dumpbin on PATH.")
        }

        private fun objdumpCommand(executable: String, dll: File): List<String> =
            listOf(executable, "-p", dll.absolutePath)

        private fun findExecutable(name: String): String? =
            System.getenv("PATH")
                .orEmpty()
                .split(File.pathSeparatorChar)
                .asSequence()
                .filter(String::isNotBlank)
                .map { root -> File(root, name) }
                .firstOrNull(File::isFile)
                ?.absolutePath

        private fun run(command: List<String>): String {
            val output = ByteArrayOutputStream()
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.use { input -> input.copyTo(output) }
            check(process.waitFor(30, TimeUnit.SECONDS)) {
                "Timed out while reading native authoring exports with ${command.first()}."
            }
            val text = output.toString(Charsets.UTF_8)
            check(process.exitValue() == 0) {
                "Failed to read native authoring exports with '${command.joinToString(" ")}'.\n$text"
            }
            return text
        }
    }

    private companion object {
        val REQUIRED_EXPORTS = setOf("DllGetActivationFactory", "DllCanUnloadNow")
    }
}
