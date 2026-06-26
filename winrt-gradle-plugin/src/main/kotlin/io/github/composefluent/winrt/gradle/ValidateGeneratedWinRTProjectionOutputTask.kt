package io.github.composefluent.winrt.gradle

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

abstract class ValidateGeneratedWinRTProjectionOutputTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDirectory: DirectoryProperty

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledClassesDirectories: ConfigurableFileCollection

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
        maxTotalKotlinSourceBytes.convention(DEFAULT_MAX_TOTAL_KOTLIN_SOURCE_BYTES)
        maxKotlinSourceFileLines.convention(DEFAULT_MAX_KOTLIN_SOURCE_FILE_LINES)
        maxTotalClassBytes.convention(DEFAULT_MAX_TOTAL_CLASS_BYTES)
        maxClassFileBytes.convention(DEFAULT_MAX_CLASS_FILE_BYTES)
    }

    @TaskAction
    fun validate() {
        val root = generatedSourcesDirectory.get().asFile
        if (!root.exists()) {
            return
        }
        val patterns = forbiddenPatterns.get()
        val tableRegexes = repeatedTablePatterns.get().map(::Regex)
        val violations = mutableListOf<String>()
        val kotlinFiles = root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .toList()
        kotlinFiles
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val pattern = patterns.firstOrNull(line::contains) ?: return@forEachIndexed
                        val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        violations += "$relative:${index + 1}: $pattern: ${line.trim()}"
                    }
                }
            }
        validateDuplicateTopLevelFqns(kotlinFiles, violations)
        validateRepeatedTables(root, kotlinFiles, tableRegexes, violations)
        validateSourceSize(root, kotlinFiles, violations)
        validateClassSize(violations)
        if (violations.isNotEmpty()) {
            val sample = violations.take(MAX_REPORTED_VIOLATIONS).joinToString(System.lineSeparator())
            throw IllegalStateException(
                "Generated WinRT projection output failed audit checks." +
                    System.lineSeparator() +
                    sample,
            )
        }
    }

    private fun validateDuplicateTopLevelFqns(kotlinFiles: List<File>, violations: MutableList<String>) {
        val duplicateFqns = kotlinFiles.flatMap { file ->
            val text = file.readText()
            val packageName = PACKAGE_REGEX.find(text)?.groupValues?.get(1).orEmpty()
            TOP_LEVEL_DECLARATION_REGEX.findAll(text)
                .mapNotNull { match -> match.groupValues.drop(1).firstOrNull(String::isNotBlank) }
                .map { name -> if (packageName.isBlank()) name else "$packageName.$name" }
                .toList()
        }.groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        duplicateFqns.forEach { fqn ->
            violations += "duplicate top-level FQN: $fqn"
        }
    }

    private fun validateRepeatedTables(
        root: File,
        kotlinFiles: List<File>,
        tableRegexes: List<Regex>,
        violations: MutableList<String>,
    ) {
        tableRegexes.forEach { regex ->
            val matches = kotlinFiles.flatMap { file ->
                regex.findAll(file.readText()).map { match ->
                    val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                    val key = match.value.replace(Regex("""\s+"""), " ")
                    key to relative
                }.toList()
            }.groupBy({ it.first }, { it.second })
            matches.filterValues { paths -> paths.distinct().size > 1 }.forEach { (key, paths) ->
                violations += "repeated type/category branch table '$key' in ${paths.distinct().sorted().joinToString()}"
            }
        }
    }

    private fun validateSourceSize(
        root: File,
        kotlinFiles: List<File>,
        violations: MutableList<String>,
    ) {
        val maxTotalBytes = maxTotalKotlinSourceBytes.get()
        val totalBytes = kotlinFiles.sumOf { file -> file.length() }
        if (totalBytes > maxTotalBytes) {
            violations += "generated Kotlin source is $totalBytes bytes; maximum is $maxTotalBytes"
        }
        val maxLines = maxKotlinSourceFileLines.get()
        kotlinFiles.forEach { file ->
            val lineCount = file.useLines { lines -> lines.count() }
            if (lineCount > maxLines) {
                val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                violations += "$relative has $lineCount lines; maximum is $maxLines"
            }
        }
    }

    private fun validateClassSize(violations: MutableList<String>) {
        val classFiles = compiledClassesDirectories.files
            .filter(File::exists)
            .flatMap { file ->
                if (file.isDirectory) {
                    file.walkTopDown().filter { candidate -> candidate.isFile && candidate.extension == "class" }.toList()
                } else if (file.isFile && file.extension == "class") {
                    listOf(file)
                } else {
                    emptyList()
                }
            }
        if (classFiles.isEmpty()) {
            return
        }
        val maxTotalBytes = maxTotalClassBytes.get()
        val totalBytes = classFiles.sumOf { file -> file.length() }
        if (totalBytes > maxTotalBytes) {
            violations += "generated class output is $totalBytes bytes; maximum is $maxTotalBytes"
        }
        val maxClassBytes = maxClassFileBytes.get()
        classFiles.forEach { file ->
            val size = file.length()
            if (size > maxClassBytes) {
                violations += "${file.name} is $size bytes; maximum is $maxClassBytes"
            }
        }
    }

    companion object {
        private const val MAX_REPORTED_VIOLATIONS = 50
        private const val DEFAULT_MAX_TOTAL_KOTLIN_SOURCE_BYTES = 75_000_000L
        private const val DEFAULT_MAX_KOTLIN_SOURCE_FILE_LINES = 20_000
        private const val DEFAULT_MAX_TOTAL_CLASS_BYTES = 75_000_000L
        private const val DEFAULT_MAX_CLASS_FILE_BYTES = 900_000L

        private val PACKAGE_REGEX = Regex("""(?m)^package\s+([A-Za-z0-9_.]+)""")
        private val TOP_LEVEL_DECLARATION_REGEX =
            Regex(
                """(?m)^(?:public\s+|internal\s+|private\s+|expect\s+|actual\s+|sealed\s+|data\s+|value\s+|enum\s+|open\s+|abstract\s+)*(?:(?:fun\s+interface|class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)|fun\s+(?:[A-Za-z_][A-Za-z0-9_<>?,. ]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(|(?:val|var)\s+(?:[A-Za-z_][A-Za-z0-9_<>?,. ]+\.)?([A-Za-z_][A-Za-z0-9_]*))""",
            )

        val DEFAULT_FORBIDDEN_PATTERNS = listOf(
            "ComVtableInvoker",
            "invokeGenericArgs",
            "Class.forName",
            "Proxy.newProxyInstance",
            "java.lang.reflect",
            "import java.",
            "WinRTProjectionRegistrar",
            "WinRTGenericAbiRegistry",
            "WinRTGenericTypeInstantiationRegistry",
            "WinRTEventProjectionRegistry",
            "GenericTypeInstantiationRuntimeBinding",
            "WinRTGenericTypeInstantiationRuntime",
            "EventSourceEntry",
            "eventSourceEntriesChunk",
            "GENERIC_ABI_DELEGATES",
            "installRuntimeBinding",
            "registerGenericInstantiation",
            "fun installEventSources()",
            "fun createEventSource(",
            "WinRTEventSourceRuntime.createEventSource(",
            "unsupportedAuthoringAbiArrayOperation",
            "Unsupported authored ABI",
            "Authored ReceiveArray parameter",
        )

        val DEFAULT_REPEATED_TABLE_PATTERNS = listOf(
            """when\s*\(\s*(sourceType|projectedTypeName|runtimeClassName|ownerType|typeName|kind)\s*\)""",
            """(sourceType|projectedTypeName|runtimeClassName|ownerType|typeName)\s+to\s+(?:\{|\w+Entry\()""",
        )
    }
}
