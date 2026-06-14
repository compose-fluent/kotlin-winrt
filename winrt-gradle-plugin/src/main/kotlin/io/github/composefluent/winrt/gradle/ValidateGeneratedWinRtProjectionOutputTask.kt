package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ValidateGeneratedWinRtProjectionOutputTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSourcesDirectory: DirectoryProperty

    @get:Input
    abstract val forbiddenPatterns: ListProperty<String>

    init {
        forbiddenPatterns.convention(DEFAULT_FORBIDDEN_PATTERNS)
    }

    @TaskAction
    fun validate() {
        val root = generatedSourcesDirectory.get().asFile
        if (!root.exists()) {
            return
        }
        val patterns = forbiddenPatterns.get()
        val violations = mutableListOf<String>()
        root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val pattern = patterns.firstOrNull(line::contains) ?: return@forEachIndexed
                        val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        violations += "$relative:${index + 1}: $pattern: ${line.trim()}"
                    }
                }
            }
        if (violations.isNotEmpty()) {
            val sample = violations.take(MAX_REPORTED_VIOLATIONS).joinToString(System.lineSeparator())
            throw IllegalStateException(
                "Generated WinRT projection output contains forbidden fallback or JVM-only paths." +
                    System.lineSeparator() +
                    sample,
            )
        }
    }

    companion object {
        private const val MAX_REPORTED_VIOLATIONS = 50

        val DEFAULT_FORBIDDEN_PATTERNS = listOf(
            "ComVtableInvoker",
            "invokeGenericArgs",
            "Class.forName",
            "Proxy.newProxyInstance",
            "java.lang.reflect",
            "import java.",
        )
    }
}
