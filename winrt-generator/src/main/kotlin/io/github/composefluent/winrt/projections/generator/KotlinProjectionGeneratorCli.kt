package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

fun main(args: Array<String>) {
    val options = KotlinProjectionGeneratorOptions.parse(args.toList())
    val metadataSources = options.metadataSources.ifEmpty { listOf(WinRtMetadataSource.windowsSdk()) }
    val model = WinRtMetadataLoader.loadSources(metadataSources).filterProjectionSurface(
        namespaces = options.namespaces,
        types = options.types,
        excludedNamespaces = options.excludedNamespaces,
        excludedTypes = options.excludedTypes,
    )
    val fileCount = KotlinProjectionGenerator(
        emitSupportFiles = true,
        projectionContext = WinRtMetadataProjectionContext(
            sources = metadataSources,
            include = options.namespaces + options.types,
            exclude = options.excludedNamespaces + options.excludedTypes,
            additionExclude = options.additionExcludes,
        ),
    ).generateTo(model, options.outputDirectory)

    println("Generated $fileCount Kotlin projection file(s) into ${options.outputDirectory}.")
}

internal data class KotlinProjectionGeneratorOptions(
    val outputDirectory: Path,
    val metadataSources: List<WinRtMetadataSource>,
    val namespaces: Set<String>,
    val types: Set<String>,
    val excludedNamespaces: Set<String>,
    val excludedTypes: Set<String>,
    val additionExcludes: Set<String>,
) {
    companion object {
        fun parse(args: List<String>): KotlinProjectionGeneratorOptions {
            var outputDirectory: Path? = null
            val metadataSources = mutableListOf<WinRtMetadataSource>()
            val namespaces = mutableSetOf<String>()
            val types = mutableSetOf<String>()
            val excludedNamespaces = mutableSetOf<String>()
            val excludedTypes = mutableSetOf<String>()
            val additionExcludes = mutableSetOf<String>()
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
                    "--exclude-namespace" -> excludedNamespaces += args.valueAfter(index, arg).also { index++ }
                    "--exclude-type" -> excludedTypes += args.valueAfter(index, arg).also { index++ }
                    "--addition-exclude" -> additionExcludes += args.valueAfter(index, arg).also { index++ }
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
                excludedNamespaces = excludedNamespaces,
                excludedTypes = excludedTypes,
                additionExcludes = additionExcludes,
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
                Usage: winrt-generator --output <dir> [--namespace <name>] [--type <full.name>] [--exclude-namespace <name>] [--exclude-type <full.name>] [--addition-exclude <prefix>] [--winmd <path|sdk|sdk+|version[+]>...]

                If --winmd is omitted, the latest Windows SDK Platform.xml API-contract set is used.
                """.trimIndent(),
            )
            kotlin.system.exitProcess(0)
        }
    }
}

private fun List<String>.valueAfter(index: Int, name: String): String {
    require(index + 1 < size) { "$name requires a value." }
    return this[index + 1]
}
