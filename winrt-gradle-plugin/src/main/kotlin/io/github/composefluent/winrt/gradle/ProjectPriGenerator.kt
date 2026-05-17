package io.github.composefluent.winrt.gradle

import org.gradle.api.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
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
        GradleFileOperations.cleanDirectory(configRoot)
        Files.createDirectories(configRoot)
        ProjectPriConfigurationInputs.fromApplicationPackageItems(items).write(configRoot, projectPriRoot)
        val config = configRoot.resolve("priconfig.xml")
        val output = projectPriRoot.resolve("resources.pri")
        ProjectPriConfigXmlWriter.write(config, configRoot, projectPriRoot, defaultQualifiers)
        MakePriRunner.run(
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
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

}
