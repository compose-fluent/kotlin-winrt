package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.name

@CacheableTask
abstract class GenerateAppxResourcesTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val targetSourceSet: Property<String>

    @get:Input
    abstract val resourceRoots: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceFiles: ConfigurableFileCollection

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        val inputs = collectAppxResourceInputs(resourceRoots.get().map { root -> Path.of(root) })
            .filterNot { input ->
                input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
            }
        val source = outputRoot.resolve("${packageName.get().replace('.', '/')}/AppxRes.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, renderAppxResourcesSource(packageName.get(), inputs, targetSourceSet.get()))
    }
}

internal fun renderAppxResourcesSource(
    packageName: String,
    inputs: List<AppxResourceInput>,
    targetSourceSet: String,
): String {
    val root = AppxResourceTreeNode("")
    val namesByAccessor = linkedMapOf<String, String>()
    inputs.forEach { input ->
        val parts = input.relativePathString.split('/').filter(String::isNotEmpty)
        if (parts.isEmpty()) return@forEach
        val propertyName = appxResourcePropertyName(parts.last())
        val directoryParts = parts.dropLast(1)
        var node = root
        directoryParts.forEach { directory ->
            node = node.children.getOrPut(directory) { AppxResourceTreeNode(directory) }
        }
        val previous = node.files.put(propertyName, input)
        if (previous != null) {
            throw GradleException(
                "AppX resource accessor collision in $targetSourceSet: " +
                    "${previous.relativePathString} and ${input.relativePathString} both map to ${accessorPath(directoryParts, propertyName)}",
            )
        }
        val accessor = accessorPath(directoryParts, propertyName)
        val previousPath = namesByAccessor.put(accessor, input.relativePathString)
        if (previousPath != null && previousPath != input.relativePathString) {
            throw GradleException(
                "AppX resource accessor collision in $targetSourceSet: " +
                    "$previousPath and ${input.relativePathString} both map to $accessor",
            )
        }
    }
    validateAppxResourceTree(root, targetSourceSet, emptyList())

    val appxResourceType = ClassName(packageName, "AppxResource")
    val fileSpec = FileSpec.builder(packageName, "AppxRes")
        .addType(buildAppxResourceType())
        .addType(buildAppxResourceAccessType(root, appxResourceType, targetSourceSet))
        .build()
    return fileSpec.toString()
}

private class AppxResourceTreeNode(
    val name: String,
    val children: MutableMap<String, AppxResourceTreeNode> = sortedMapOf(),
    val files: MutableMap<String, AppxResourceInput> = sortedMapOf(),
)

private fun validateAppxResourceTree(
    node: AppxResourceTreeNode,
    targetSourceSet: String,
    parents: List<String>,
) {
    val memberNames = linkedMapOf<String, String>()
    node.children.forEach { (name, child) ->
        val identifier = kotlinIdentifier(name, "directory")
        val previous = memberNames.put(identifier, name)
        if (previous != null) {
            throw GradleException(
                "AppX resource accessor collision in $targetSourceSet: directories " +
                    "${(parents + previous).joinToString("/")} and ${(parents + name).joinToString("/")} both map to ${accessorPath(parents, identifier)}",
            )
        }
        validateAppxResourceTree(child, targetSourceSet, parents + name)
    }
    node.files.forEach { (name, input) ->
        val identifier = kotlinIdentifier(name, "file")
        val previous = memberNames.put(identifier, input.relativePathString)
        if (previous != null) {
            throw GradleException(
                "AppX resource accessor collision in $targetSourceSet: $previous and ${input.relativePathString} " +
                    "both map to ${accessorPath(parents, identifier)}",
            )
        }
    }
}

private fun buildAppxResourceType(): TypeSpec {
    val uriType = ClassName("windows.foundation", "Uri")
    return TypeSpec.classBuilder("AppxResource")
        .addModifiers(KModifier.PUBLIC)
        .primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.INTERNAL)
                .addParameter("path", STRING)
                .addParameter("encodedPath", STRING)
                .build(),
        )
        .addProperty(
            PropertySpec.builder("path", STRING)
                .addModifiers(KModifier.PUBLIC)
                .initializer("path")
                .build(),
        )
        .addProperty(
            PropertySpec.builder("encodedPath", STRING)
                .addModifiers(KModifier.PRIVATE)
                .initializer("encodedPath")
                .build(),
        )
        .addProperty(
            PropertySpec.builder("uri", uriType)
                .addModifiers(KModifier.PUBLIC)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(CodeBlock.of("return %T(%S + encodedPath)\n", uriType, "ms-appx:///"))
                        .build(),
                )
                .build(),
        )
        .addKdoc("A package-relative AppX resource and its lazily constructed ms-appx URI.")
        .build()
}

private fun buildAppxResourceAccessType(
    root: AppxResourceTreeNode,
    appxResourceType: ClassName,
    targetSourceSet: String,
): TypeSpec {
    val builder = TypeSpec.objectBuilder("AppxRes")
        .addModifiers(KModifier.PUBLIC)
        .addKdoc("Generated access to resources staged at the AppX package root.")
    addAppxResourceMembers(builder, root, appxResourceType, targetSourceSet)
    return builder.build()
}

private fun addAppxResourceMembers(
    output: TypeSpec.Builder,
    node: AppxResourceTreeNode,
    appxResourceType: ClassName,
    targetSourceSet: String,
) {
    node.children.forEach { (name, child) ->
        val childBuilder = TypeSpec.objectBuilder(kotlinIdentifier(name, "directory"))
            .addModifiers(KModifier.PUBLIC)
        addAppxResourceMembers(childBuilder, child, appxResourceType, targetSourceSet)
        output.addType(childBuilder.build())
    }
    node.files.forEach { (name, input) ->
        val path = input.relativePathString
        val encodedPath = encodeAppxUriPath(path)
        output.addProperty(
            PropertySpec.builder(kotlinIdentifier(name, "file"), appxResourceType)
                .addModifiers(KModifier.PUBLIC)
                .initializer(
                    CodeBlock.of("%T(%S, %S)", appxResourceType, path, encodedPath),
                )
                .build(),
        )
    }
}

private fun accessorPath(directoryParts: List<String>, propertyName: String): String =
    (directoryParts.map { kotlinIdentifier(it, "directory") } + propertyName).joinToString(".")

private fun appxResourcePropertyName(fileName: String): String {
    val words = fileName.split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
    if (words.isEmpty()) {
        throw GradleException("AppX resource file '$fileName' cannot produce a Kotlin accessor name.")
    }
    return words.first() + words.drop(1).joinToString("") { word ->
        word.replaceFirstChar { character -> character.uppercase(Locale.ROOT) }
    }
}

private fun kotlinIdentifier(value: String, kind: String): String {
    if (value.isBlank()) throw GradleException("AppX resource $kind name cannot be blank.")
    if (KOTLIN_IDENTIFIER.matches(value) && value !in KOTLIN_KEYWORDS) return value
    val quoted = value.replace('`', '_')
    if (quoted.isBlank()) throw GradleException("AppX resource $kind name '$value' cannot produce a Kotlin identifier.")
    return "`$quoted`"
}

private fun encodeAppxUriPath(path: String): String {
    val bytes = path.replace('\\', '/').toByteArray(StandardCharsets.UTF_8)
    val output = StringBuilder(bytes.size)
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xff
        val character = value.toChar()
        if (character == '/' || character.isAsciiLetterOrDigit() || character == '-' || character == '.' || character == '_' || character == '~') {
            output.append(character)
        } else {
            output.append('%')
            output.append(HEX[value ushr 4])
            output.append(HEX[value and 0x0f])
        }
    }
    return output.toString()
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private val KOTLIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while", "by", "catch", "constructor", "delegate", "dynamic", "field",
    "file", "finally", "get", "import", "init", "param", "property", "receiver", "set", "setparam", "where",
    "actual", "abstract", "annotation", "companion", "const", "crossinline", "data", "enum", "expect", "external",
    "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override",
    "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg",
)
private const val HEX = "0123456789ABCDEF"
