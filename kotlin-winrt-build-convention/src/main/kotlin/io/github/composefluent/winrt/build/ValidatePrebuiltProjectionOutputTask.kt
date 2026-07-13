package io.github.composefluent.winrt.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ValidatePrebuiltProjectionOutputTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDirectory: DirectoryProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledClassesDirectories: ConfigurableFileCollection

    @get:Input
    abstract val crossArtifactClassOwners: ListProperty<String>

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val crossArtifactClassDirectories: ConfigurableFileCollection

    @get:Input
    abstract val allowedDuplicateClassPaths: ListProperty<String>

    @get:Input
    abstract val forbiddenPatterns: ListProperty<String>

    @get:Input
    abstract val repeatedTablePatterns: ListProperty<String>

    @get:Input
    abstract val maxTotalKotlinSourceBytes: Property<Long>

    @get:Input
    abstract val maxKotlinSourceFileLines: Property<Int>

    @get:Input
    abstract val maxTotalClassBytes: Property<Long>

    @get:Input
    abstract val maxClassFileBytes: Property<Long>

    init {
        forbiddenPatterns.convention(DEFAULT_FORBIDDEN_PATTERNS)
        repeatedTablePatterns.convention(DEFAULT_REPEATED_TABLE_PATTERNS)
        crossArtifactClassOwners.convention(emptyList())
        allowedDuplicateClassPaths.convention(emptyList())
        maxTotalKotlinSourceBytes.convention(75_000_000L)
        maxKotlinSourceFileLines.convention(20_000)
        maxTotalClassBytes.convention(75_000_000L)
        maxClassFileBytes.convention(900_000L)
    }

    @TaskAction
    fun validate() {
        val root = generatedSourcesDirectory.get().asFile
        if (!root.exists()) return
        val violations = mutableListOf<String>()
        val kotlinFiles = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        kotlinFiles.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    forbiddenPatterns.get().firstOrNull(line::contains)?.let { pattern ->
                        val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        violations += "$relative:${index + 1}: $pattern: ${line.trim()}"
                    }
                }
            }
        }
        validateDuplicateTopLevelFqns(kotlinFiles, violations)
        validateRepeatedTables(root, kotlinFiles, violations)
        validateSourceSize(root, kotlinFiles, violations)
        validateClassSize(violations)
        validateCrossArtifactClassOwnership(violations)
        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                "Generated WinRT projection output failed audit checks." + System.lineSeparator() +
                    violations.take(50).joinToString(System.lineSeparator()),
            )
        }
    }

    private fun validateDuplicateTopLevelFqns(kotlinFiles: List<File>, violations: MutableList<String>) {
        kotlinFiles.flatMap { file ->
            val text = file.readText()
            val packageName = PACKAGE_REGEX.find(text)?.groupValues?.get(1).orEmpty()
            TOP_LEVEL_DECLARATION_REGEX.findAll(text)
                .mapNotNull { it.groupValues.drop(1).firstOrNull(String::isNotBlank) }
                .map { name -> if (packageName.isBlank()) name else "$packageName.$name" }
                .toList()
        }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach { fqn ->
            violations += "duplicate top-level FQN: $fqn"
        }
    }

    private fun validateRepeatedTables(root: File, kotlinFiles: List<File>, violations: MutableList<String>) {
        repeatedTablePatterns.get().map(::Regex).forEach { regex ->
            kotlinFiles.flatMap { file ->
                regex.findAll(file.readText()).map { match ->
                    match.value.replace(Regex("\\s+"), " ") to
                        root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                }.toList()
            }.groupBy({ it.first }, { it.second })
                .filterValues { paths -> paths.distinct().size > 1 }
                .forEach { (table, paths) ->
                    violations += "repeated type/category branch table '$table' in ${paths.distinct().sorted().joinToString()}"
                }
        }
    }

    private fun validateSourceSize(root: File, kotlinFiles: List<File>, violations: MutableList<String>) {
        val totalBytes = kotlinFiles.sumOf(File::length)
        if (totalBytes > maxTotalKotlinSourceBytes.get()) {
            violations += "generated Kotlin source is $totalBytes bytes; maximum is ${maxTotalKotlinSourceBytes.get()}"
        }
        kotlinFiles.forEach { file ->
            val lineCount = file.useLines { it.count() }
            if (lineCount > maxKotlinSourceFileLines.get()) {
                val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                violations += "$relative has $lineCount lines; maximum is ${maxKotlinSourceFileLines.get()}"
            }
        }
    }

    private fun validateClassSize(violations: MutableList<String>) {
        val classFiles = compiledClassesDirectories.files.filter(File::exists).flatMap { file ->
            when {
                file.isDirectory -> file.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
                file.isFile && file.extension == "class" -> listOf(file)
                else -> emptyList()
            }
        }
        if (classFiles.isEmpty()) return
        val totalBytes = classFiles.sumOf(File::length)
        if (totalBytes > maxTotalClassBytes.get()) {
            violations += "generated class output is $totalBytes bytes; maximum is ${maxTotalClassBytes.get()}"
        }
        classFiles.filter { it.length() > maxClassFileBytes.get() }.forEach { file ->
            violations += "${file.name} is ${file.length()} bytes; maximum is ${maxClassFileBytes.get()}"
        }
    }

    private fun validateCrossArtifactClassOwnership(violations: MutableList<String>) {
        val owners = crossArtifactClassOwners.get()
        val roots = crossArtifactClassDirectories.files.toList()
        require(owners.size == roots.size) { "Cross-artifact class owners (${owners.size}) must match class roots (${roots.size})." }
        val allowedPaths = allowedDuplicateClassPaths.get().map(String::lowercase).toSet()
        owners.zip(roots).flatMap { (owner, root) ->
            if (!root.isDirectory) emptyList() else root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { file -> owner to root.toPath().relativize(file.toPath()).toString().replace('\\', '/') }
                .toList()
        }.groupBy { it.second.lowercase() }.toSortedMap().forEach { (path, entries) ->
            val duplicateOwners = entries.map { it.first }.distinct().sorted()
            if (duplicateOwners.size > 1 && path !in allowedPaths) {
                violations += "duplicate class path '${entries.first().second}' in owners: ${duplicateOwners.joinToString()}"
            }
        }
    }

    private companion object {
        val PACKAGE_REGEX = Regex("(?m)^package\\s+([A-Za-z0-9_.]+)")
        val TOP_LEVEL_DECLARATION_REGEX = Regex(
            """(?m)^(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|sealed\s+|data\s+|value\s+|enum\s+|open\s+|abstract\s+)*(?:(?:fun\s+interface|class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)|fun\s+(?:[A-Za-z_][A-Za-z0-9_<>?,. ]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(|(?:val|var)\s+(?:[A-Za-z_][A-Za-z0-9_<>?,. ]+\.)?([A-Za-z_][A-Za-z0-9_]*))""",
        )
        val DEFAULT_FORBIDDEN_PATTERNS = listOf(
            "ComVtableInvoker", "invokeGenericArgs", "Class.forName", "Proxy.newProxyInstance", "java.lang.reflect",
            "import java.", "WinRTProjectionRegistrar", "WinRTGenericAbiRegistry", "WinRTGenericTypeInstantiationRegistry",
            "WinRTEventProjectionRegistry", "GenericTypeInstantiationRuntimeBinding", "WinRTGenericTypeInstantiationRuntime",
            "EventSourceEntry", "eventSourceEntriesChunk", "GENERIC_ABI_DELEGATES", "installRuntimeBinding",
            "registerGenericInstantiation", "fun installEventSources()", "fun createEventSource(",
            "WinRTEventSourceRuntime.createEventSource(", "unsupportedAuthoringAbiArrayOperation", "Unsupported authored ABI",
            "Authored ReceiveArray parameter",
        )
        val DEFAULT_REPEATED_TABLE_PATTERNS = listOf(
            """when\s*\(\s*(sourceType|projectedTypeName|runtimeClassName|ownerType|typeName|kind)\s*\)""",
            """(sourceType|projectedTypeName|runtimeClassName|ownerType|typeName)\s+to\s+(?:\{|\w+Entry\()""",
        )
    }
}
