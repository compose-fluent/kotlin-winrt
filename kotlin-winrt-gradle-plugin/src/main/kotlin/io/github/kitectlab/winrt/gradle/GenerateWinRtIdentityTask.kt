package io.github.kitectlab.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class GenerateWinRtIdentityTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:Input
    abstract val includeNamespaces: ListProperty<String>

    @get:Input
    abstract val includeTypes: ListProperty<String>

    @get:Input
    abstract val excludeNamespaces: ListProperty<String>

    @get:Input
    abstract val excludeTypes: ListProperty<String>

    @get:Input
    abstract val additionExcludeNamespaces: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Input
    abstract val runtimeAssets: ListProperty<String>

    @TaskAction
    fun generate() {
        val target = outputFile.get().asFile.toPath()
        Files.createDirectories(target.parent)
        Files.writeString(
            target,
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"model\": \"library\",")
                appendLine("  \"metadataInputs\": ${metadataInputs.get().toJsonArray()},")
                appendLine("  \"includeNamespaces\": ${includeNamespaces.get().toJsonArray()},")
                appendLine("  \"includeTypes\": ${includeTypes.get().toJsonArray()},")
                appendLine("  \"excludeNamespaces\": ${excludeNamespaces.get().toJsonArray()},")
                appendLine("  \"excludeTypes\": ${excludeTypes.get().toJsonArray()},")
                appendLine("  \"additionExcludeNamespaces\": ${additionExcludeNamespaces.get().toJsonArray()},")
                appendLine("  \"windowsSdk\": {")
                appendLine("    \"version\": ${windowsSdkVersion.orNull.toJsonStringOrNull()},")
                appendLine("    \"includeExtensions\": ${includeWindowsSdkExtensions.get()}")
                appendLine("  },")
                appendLine("  \"nugetPackages\": ${nugetPackages.get().toJsonArray()},")
                appendLine("  \"runtimeAssets\": ${runtimeAssets.get().toJsonArray()}")
                appendLine("}")
            },
        )
    }
}

internal fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { it.toJsonString() }

internal fun String?.toJsonStringOrNull(): String =
    this?.toJsonString() ?: "null"

internal fun String.toJsonString(): String =
    buildString {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
