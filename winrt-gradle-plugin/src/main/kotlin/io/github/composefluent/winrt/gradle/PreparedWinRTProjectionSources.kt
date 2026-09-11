package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceResolver
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.projections.generator.redirectedWinAppSdkProjectionSurfaceTypeReferences
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Base64

private const val PREPARED_STATIC_HEADER = "kotlin-winrt-prepared-static-sources-v1"

internal class StaticPreparationUnavailable(message: String) : RuntimeException(message)

/**
 * Prepares imported projection sources once fixed, local metadata is available.
 *
 * SDK discovery, NuGet restore, and task-produced metadata intentionally return null here.
 * Those inputs still use the execution-time generation path, which preserves producer
 * dependencies instead of reading a previous build's output during configuration.
 */
internal fun prepareWinRTStaticProjectionSources(
    project: Project,
    extension: BaseWinRTExtension,
    dependencyIdentityFiles: Iterable<java.io.File>,
    generatedOutputDirectory: Provider<Directory>,
    supportOwnerIdentity: String,
): Path? {
    val parsedSources = extension.metadataInputs.get().map { input -> WinRTMetadataSource.parse(input) }
    if (parsedSources.isEmpty() || parsedSources.any { source ->
            source !is WinRTMetadataSource.PathSource && source !is WinRTMetadataSource.NuGetPackage
        }) throw StaticPreparationUnavailable("fixed metadata is not a local path")
    if (project.configurations.findByName(KOTLIN_WINRT_LIBRARY_DEPENDENCY_IDENTITY_CONFIGURATION)
            ?.allDependencies
            ?.isNotEmpty() == true
    ) {
        throw StaticPreparationUnavailable("dependency identity producers are configured")
    }
    val identityFiles = dependencyIdentityFiles.toList()
    if (identityFiles.any { file -> !file.isFile }) {
        throw StaticPreparationUnavailable("dependency identity outputs are not available")
    }

    val cache = WinRTMetadataSourceResolver.resolve(parsedSources)
    if (cache.files.isEmpty() || cache.files.any { file -> !Files.isRegularFile(file) }) {
        throw StaticPreparationUnavailable("local metadata files are missing")
    }
    val model = cache.load(project.layout.projectDirectory.dir(".gradle/kotlin-winrt/metadata-models").asFile.toPath())
    val effectiveIncludeTypes = extension.includeTypes.get() +
        automaticXamlComponentResourceDictionaryTypes(model, extension.includeTypes.get().toSet())
    val dependencySurfaceTypes = dependencyProjectionSurfaceTypeNames(identityFiles)
    val applicationPackagingOnly = extension is WinRTExtension &&
        extension.applicationEnabled.get() &&
        extension.metadataInputs.get().isEmpty() &&
        extension.includeNamespaces.get().isEmpty() &&
        extension.includeTypes.get().isEmpty() &&
        !extension.generateWindowsSdkProjection.get()
    val staticModel = if (applicationPackagingOnly) {
        WinRTMetadataModel(emptyList())
    } else {
        model.filterProjectionSurface(
            namespaces = extension.includeNamespaces.get().toSet(),
            types = (effectiveIncludeTypes + dependencySurfaceTypes).toSet(),
            excludedNamespaces = extension.excludeNamespaces.get().toSet(),
            excludedTypes = extension.excludeTypes.get().toSet(),
            additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
        )
    }
    val context = WinRTMetadataProjectionContext(
        sources = parsedSources,
        include = extension.includeNamespaces.get().toSet() +
            effectiveIncludeTypes.toSet() + dependencySurfaceTypes.toSet(),
        exclude = extension.excludeNamespaces.get().toSet() + extension.excludeTypes.get().toSet(),
        excludedTypes = extension.excludeTypes.get().toSet(),
        additionExclude = extension.additionExcludeNamespaces.get().toSet(),
    )
    val key = preparedStaticProjectionKey(
        cache.files,
        extension,
        identityFiles,
        supportOwnerIdentity,
        project,
    )
    val storeRoot = project.layout.projectDirectory.dir(".gradle/kotlin-winrt/prepared-imports").asFile.toPath()
    val entry = storeRoot.resolve(key)
    val sourceRoot = entry.resolve("sources")
    Files.createDirectories(storeRoot)
    FileChannel.open(
        storeRoot.resolve("$key.lock"),
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
    ).use { channel ->
        channel.lock().use {
            if (!Files.isRegularFile(entry.resolve("manifest.tsv")) || !Files.isDirectory(sourceRoot)) {
                val temporary = Files.createTempDirectory(storeRoot, ".${key}-")
                try {
                    KotlinProjectionGenerator(
                        emitSupportFiles = true,
                        groupProjectionFilesByPackageOnWrite = true,
                        projectionContext = context,
                        suppressedProjectionTypeNames = dependencyProjectedTypeNames(staticModel, identityFiles),
                        suppressedSourceAdditionTypeNames = dependencySourceAdditionTypeNames(identityFiles),
                        supportOwnerIdentity = supportOwnerIdentity,
                        emitJvmAuthoringHostExports = project.extensions.findByType(KotlinMultiplatformExtension::class.java) == null,
                    ).generateTo(staticModel, temporary.resolve("sources"))
                    writePreparedStaticManifest(temporary.resolve("manifest.tsv"), cache.files, staticModel)
                    runCatching {
                        Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE)
                    }.getOrElse {
                        Files.move(temporary, entry)
                    }
                } catch (error: Throwable) {
                    GradleFileOperations.deleteDirectory(temporary)
                    throw error
                }
            }
        }
    }
    materializePreparedStaticSources(sourceRoot, generatedOutputDirectory.get().asFile.toPath())
    return sourceRoot
}

private fun preparedStaticProjectionKey(
    files: List<Path>,
    extension: BaseWinRTExtension,
    identityFiles: List<java.io.File>,
    supportOwnerIdentity: String,
    project: Project,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    update("prepared-static-sources-v1")
    update(KotlinProjectionGenerator::class.java.protectionDomain?.codeSource?.location?.toString().orEmpty())
    update(project.name)
    update(supportOwnerIdentity)
    extension.metadataInputs.get().forEach(::update)
    extension.includeNamespaces.get().sorted().forEach(::update)
    extension.includeTypes.get().sorted().forEach(::update)
    extension.excludeNamespaces.get().sorted().forEach(::update)
    extension.excludeTypes.get().sorted().forEach(::update)
    extension.additionExcludeNamespaces.get().sorted().forEach(::update)
    files.sortedBy(Path::toString).forEach { file ->
        update(file.toAbsolutePath().normalize().toString())
        digest.update(Files.readAllBytes(file))
        digest.update(0)
    }
    identityFiles.sortedBy(java.io.File::getAbsolutePath).forEach { file ->
        update(file.absolutePath)
        digest.update(Files.readAllBytes(file.toPath()))
        digest.update(0)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun writePreparedStaticManifest(path: Path, files: List<Path>, model: WinRTMetadataModel) {
    val names = model.namespaces.flatMap { namespace -> namespace.types }.map { type -> type.qualifiedName }.sorted()
    val encodedFiles = files.sortedBy(Path::toString).joinToString("\n") { file ->
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            .joinToString("") { byte -> "%02x".format(byte) }
        "file\t${Base64.getUrlEncoder().withoutPadding().encodeToString(file.toString().toByteArray())}\t$digest"
    }
    val content = buildString {
        append(PREPARED_STATIC_HEADER).append('\n')
        append("types\t").append(names.joinToString(",")).append('\n')
        if (encodedFiles.isNotEmpty()) append(encodedFiles).append('\n')
    }
    GradleFileOperations.writeStringIfChanged(path, content)
}

internal fun materializePreparedStaticSources(sourceRoot: Path, generatedRoot: Path) {
    if (!Files.isDirectory(sourceRoot)) return
    val manifest = generatedRoot.resolve(".kotlin-winrt-prepared-static-files.tsv")
    val previousFiles = if (Files.isRegularFile(manifest)) {
        Files.readAllLines(manifest).filter(String::isNotBlank).toSet()
    } else {
        emptySet()
    }
    val currentFiles = mutableSetOf<String>()
    Files.walk(sourceRoot).use { stream ->
        stream.filter(Files::isRegularFile).forEach { source ->
            val relative = sourceRoot.relativize(source).toString().replace('\\', '/')
            currentFiles += relative
            val target = generatedRoot.resolve(relative)
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || !Files.mismatch(source, target).let { it == -1L }) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    previousFiles.asSequence()
        .filterNot(currentFiles::contains)
        .map(generatedRoot::resolve)
        .forEach(Files::deleteIfExists)
    GradleFileOperations.writeStringIfChanged(
        manifest,
        currentFiles.sorted().joinToString(separator = "\n", postfix = if (currentFiles.isEmpty()) "" else "\n"),
    )
}
