package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

internal object ProjectPriGenerator {
    fun generateApplicationPri(
        makePri: Path,
        outputRoot: Path,
        projectPriRoot: Path,
        configRoot: Path,
        indexName: String,
        defaultQualifiers: List<Pair<String, String>>,
        items: Set<ApplicationPackageItem>,
        logger: Logger,
    ): Boolean {
        cleanDirectory(configRoot)
        Files.createDirectories(configRoot)
        ProjectPriConfigurationInputs.fromApplicationPackageItems(items).write(configRoot, projectPriRoot)
        val config = configRoot.resolve("priconfig.xml")
        val output = projectPriRoot.resolve("resources.pri")
        ProjectPriConfigXmlWriter.write(config, configRoot, projectPriRoot, defaultQualifiers)
        runMakePri(
            makePri,
            listOf("new", "/pr", projectPriRoot.toString(), "/cf", config.toString(), "/of", output.toString(), "/in", indexName, "/o"),
            outputRoot,
            "generate application PRI",
            logger,
        ) ?: return false
        copyGeneratedPriOutputs(projectPriRoot, outputRoot)
        return true
    }

    private fun copyGeneratedPriOutputs(projectPriRoot: Path, outputRoot: Path) {
        Files.walk(projectPriRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .filter {
                    it.name.equals("resources.pri", ignoreCase = true) ||
                        it.name.startsWith("resources.language-", ignoreCase = true)
                }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun runMakePri(
        makePri: Path,
        arguments: List<String>,
        workingDirectory: Path,
        description: String,
        logger: Logger,
    ): String? {
        val process = ProcessBuilder(listOf(makePri.toString()) + arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            output
        } else {
            logger.warn("Skipping application PRI generation after makepri failed to $description with exit code $exitCode:\n$output")
            null
        }
    }

    private fun decodeProcessOutput(bytes: ByteArray): String {
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) {
            return bytes.toString(Charsets.UTF_16LE)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun cleanDirectory(directory: Path) {
        if (!directory.isDirectory()) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder())
                .filter { it != directory }
                .forEach(Files::deleteIfExists)
        }
    }
}
