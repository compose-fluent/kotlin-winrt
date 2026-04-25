package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataLoader
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtMetadataSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

fun main(args: Array<String>) {
    val options = KotlinProjectionGeneratorOptions.parse(args.toList())
    val metadataSources = options.metadataSources.ifEmpty { listOf(WinRtMetadataSource.windowsSdk()) }
    val model = WinRtMetadataLoader.loadSources(metadataSources).filterProjectionSurface(options.namespaces, options.types)
    val files = KotlinProjectionGenerator().generate(model)

    files.forEach { file ->
        val target = options.outputDirectory.resolve(file.relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, file.contents)
    }

    println("Generated ${files.size} Kotlin projection file(s) into ${options.outputDirectory}.")
}

internal data class KotlinProjectionGeneratorOptions(
    val outputDirectory: Path,
    val metadataSources: List<WinRtMetadataSource>,
    val namespaces: Set<String>,
    val types: Set<String>,
) {
    companion object {
        fun parse(args: List<String>): KotlinProjectionGeneratorOptions {
            var outputDirectory: Path? = null
            val metadataSources = mutableListOf<WinRtMetadataSource>()
            val namespaces = mutableSetOf<String>()
            val types = mutableSetOf<String>()
            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--output" -> outputDirectory = Path.of(args.valueAfter(index, arg)).also { index++ }
                    "--winmd", "--metadata" -> {
                        metadataSources.add(WinRtMetadataSource.parse(args.valueAfter(index, arg)))
                        index++
                    }
                    "--namespace" -> namespaces += args.valueAfter(index, arg).also { index++ }
                    "--type" -> types += args.valueAfter(index, arg).also { index++ }
                    "--help", "-h" -> printUsageAndExit()
                    else -> error("Unknown argument: $arg")
                }
                index++
            }

            return KotlinProjectionGeneratorOptions(
                outputDirectory = requireNotNull(outputDirectory) { "--output is required." },
                metadataSources = metadataSources,
                namespaces = namespaces,
                types = types,
            )
        }

        fun locateWindowsWinmd(): Path {
            System.getenv("KOTLIN_WINRT_WINDOWS_WINMD")
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.takeIf { it.isRegularFile() }
                ?.let { return it }

            val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val unionMetadata = Path.of(programFilesX86, "Windows Kits", "10", "UnionMetadata")
            require(unionMetadata.exists()) {
                "Windows SDK UnionMetadata directory was not found. Pass --winmd or set KOTLIN_WINRT_WINDOWS_WINMD."
            }

            return Files.walk(unionMetadata).use { stream ->
                stream.asSequence()
                    .filter(Files::isRegularFile)
                    .filter { it.name.equals("Windows.winmd", ignoreCase = true) }
                    .filterNot { it.parent.name.equals("Facade", ignoreCase = true) }
                    .sortedBy { it.toString() }
                    .toList()
                    .lastOrNull()
            } ?: error("Windows.winmd was not found under $unionMetadata.")
        }

        private fun printUsageAndExit(): Nothing {
            println(
                """
                Usage: winrt-generator --output <dir> [--namespace <name>] [--type <full.name>] [--winmd <path|sdk|sdk+|version[+]>...]

                If --winmd is omitted, the latest Windows SDK Platform.xml API-contract set is used.
                """.trimIndent(),
            )
            kotlin.system.exitProcess(0)
        }
    }
}

private fun WinRtMetadataModel.filterProjectionSurface(namespaces: Set<String>, types: Set<String>): WinRtMetadataModel =
    if (namespaces.isEmpty() && types.isEmpty()) {
        this
    } else {
        WinRtMetadataModel(
            this.namespaces.mapNotNull { namespace ->
                val namespaceTypes = namespace.types.filter { type ->
                    namespace.name in namespaces || type.qualifiedName in types
                }
                namespaceTypes.takeIf { it.isNotEmpty() }?.let { WinRtNamespace(namespace.name, it) }
            },
        ).normalized()
    }

private fun List<String>.valueAfter(index: Int, name: String): String {
    require(index + 1 < size) { "$name requires a value." }
    return this[index + 1]
}
