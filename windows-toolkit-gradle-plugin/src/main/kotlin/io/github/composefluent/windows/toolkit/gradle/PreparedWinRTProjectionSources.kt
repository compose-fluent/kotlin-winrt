package io.github.composefluent.windows.toolkit.gradle

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceResolver
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.Guid
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.jar.JarFile

private const val PREPARED_STATIC_HEADER = "kotlin-winrt-prepared-static-sources-v4"

internal class StaticPreparationUnavailable(message: String) : RuntimeException(message)

/**
 * The dependency identities a configuration-time preparation may read: published ones only.
 *
 * Reading them resolves the identity configuration while the build is configured, so the caller
 * waits for the projects to be configured (see [isBeingConfigured]). A build can still
 * forbid that; the Android Gradle plugin does when
 * `android.dependencyResolutionAtConfigurationTime.disallow` is set. The generation task resolves
 * the same identities when it runs, so the preparation is left to it instead of failing the
 * configuration.
 */
internal fun configurationTimeDependencyIdentityFiles(
    identities: org.gradle.api.file.FileCollection,
): Set<java.io.File> =
    try {
        if (identities.buildDependencies.getDependencies(null).isNotEmpty()) {
            throw StaticPreparationUnavailable("dependency identity producers require IDE preparation tasks")
        }
        identities.files
    } catch (unavailable: StaticPreparationUnavailable) {
        throw unavailable
    } catch (error: Exception) {
        throw StaticPreparationUnavailable(
            "dependency identities cannot be resolved during configuration: ${error.message}",
        )
    }

/**
 * Prepares imported projection sources once the DSL and target model are complete.
 *
 * Fixed SDK, metadata, and NuGet identities use the same cache-first resolver as generation.
 * The caller routes task-produced metadata through IDE preparation tasks to preserve producer
 * dependencies instead of reading a previous build's output during configuration.
 */
internal fun prepareWinRTStaticProjectionSources(
    project: Project,
    extension: PackageReferencesConfiguration,
    dependencyIdentityFiles: Iterable<java.io.File>,
    generatedOutputDirectory: Provider<Directory>,
    supportOwnerIdentity: String,
    windowsSdkRegistryRoots: Iterable<String> = emptyList(),
): Path? {
    val registryRootPaths = windowsSdkRegistryRoots.toList()
        .orNullIfEmpty()
        ?.map(Path::of)
    val parsedSources = extension.metadataInputs.get()
        .map { input -> WinRTMetadataSource.parse(input) }
        .map { source -> source.withWindowsSdkRegistryRoots(registryRootPaths) }
    if (parsedSources.any { source ->
            source !is WinRTMetadataSource.PathSource &&
                source !is WinRTMetadataSource.NuGetPackage &&
                source !is WinRTMetadataSource.NuGetPackageReference
        }) throw StaticPreparationUnavailable("metadata includes a dynamic source")
    val identityFiles = dependencyIdentityFiles.toList()
    if (identityFiles.any { file -> !file.isFile }) {
        throw StaticPreparationUnavailable("dependency identity outputs are not available")
    }

    val explicitNuGetReferences = parsedSources.filterIsInstance<WinRTMetadataSource.NuGetPackageReference>()
    val projectionPackageSpecs = extension.nugetPackages
        .filter { packageReference -> packageReference.generateProjection }
        .map { packageReference -> "${packageReference.packageId}@${packageReference.version.get()}" }
    val explicitNuGetSpecs = explicitNuGetReferences.map { source ->
        "${source.packageId}@${source.version}"
    }
    val packageSpecs = (projectionPackageSpecs + explicitNuGetSpecs).distinct()
    // Like CsWinRTPrepareProjection's resolved package inputs, restored NuGet metadata is
    // task-produced. IDE preparation already depends on generateWinRTProjections, which
    // depends on WinApp restore. Never introduce a second downloader during configuration.
    if (packageSpecs.isNotEmpty()) {
        throw StaticPreparationUnavailable("NuGet metadata requires the restoreWinAppDependencies task")
    }
    val effectiveSources = parsedSources.filterNot { source ->
        source is WinRTMetadataSource.NuGetPackageReference
    } +
        if (extension.windowsSdkDeclared.get()) {
            listOf(
                WinRTMetadataSource.windowsSdk(
                    version = extension.windowsSdkVersion.orNull,
                    includeExtensions = extension.includeWindowsSdkExtensions.get(),
                    registryRoots = registryRootPaths,
                ),
            )
        } else {
            emptyList()
        }
    if (effectiveSources.isEmpty()) {
        return null
    }
    val cache = runCatching { WinRTMetadataSourceResolver.resolve(effectiveSources) }
        .getOrElse { error ->
            if (extension.windowsSdkDeclared.get()) {
                throw StaticPreparationUnavailable(
                    "Windows SDK metadata is not available during configuration: ${error.message}",
                )
            }
            throw error
        }
    if (cache.files.isEmpty() || cache.files.any { file -> !Files.isRegularFile(file) }) {
        throw StaticPreparationUnavailable("local metadata files are missing")
    }
    val emitJvmAuthoringHostExports = project.extensions.findByType(KotlinMultiplatformExtension::class.java) == null
    val key = preparedStaticProjectionKey(
        cache.files,
        extension,
        identityFiles,
        supportOwnerIdentity,
        project,
        emitJvmAuthoringHostExports,
        registryRootPaths.orEmpty(),
    )
    // Preserve first-import completion, while sharing immutable SDK sources between projects.
    val storeRoot = sharedWinRTCacheDirectory(project, "prepared-imports")
    val entry = storeRoot.resolve(key)
    val sourceRoot = entry.resolve("sources")
    // Keep the full metadata model and KotlinPoet graph out of the configuration daemon.
    // Only a cold/invalid entry starts a bounded process; warm imports just validate/copy bytes.
    val request = mapOf(
        "entry" to entry.toString(),
        "output" to generatedOutputDirectory.get().asFile.absolutePath,
        "sources" to effectiveSources.joinToString("\u0000") { source ->
            when (source) {
                is WinRTMetadataSource.PathSource -> source.path.toAbsolutePath().normalize().toString()
                is WinRTMetadataSource.NuGetPackage -> "nuget:${source.packagePath.toAbsolutePath().normalize()}"
                is WinRTMetadataSource.WindowsSdk -> (source.version ?: "sdk") +
                    if (source.includeExtensions) "+" else ""
                else -> error("Static projection source was not resolved: $source")
            }
        },
        "registryRoots" to registryRootPaths.orEmpty().joinToString("\u0000"),
        "modelCache" to sharedWinRTCacheDirectory(project, "metadata-models").toString(),
        "identities" to identityFiles.joinToString("\u0000") { it.absolutePath },
        "includeNamespaces" to extension.includeNamespaces.get().joinToString("\u0000"),
        "includeTypes" to extension.includeTypes.get().joinToString("\u0000"),
        "excludeNamespaces" to extension.excludeNamespaces.get().joinToString("\u0000"),
        "excludeTypes" to extension.excludeTypes.get().joinToString("\u0000"),
        "additionExclude" to extension.additionExcludeNamespaces.get().joinToString("\u0000"),
        "packagingOnly" to (extension is WindowsExtension && extension.applicationEnabled.get() &&
            extension.metadataInputs.get().isEmpty() && extension.includeNamespaces.get().isEmpty() &&
            extension.includeTypes.get().isEmpty() && !extension.generateWindowsSdkProjection.get()).toString(),
        "owner" to supportOwnerIdentity,
        "emitJvmAuthoringHostExports" to emitJvmAuthoringHostExports.toString(),
    )
    project.providers.of(PreparedProjectionValueSource::class.java) { spec ->
        spec.parameters.request.set(request)
        spec.parameters.classpath.from(kotlinWinRTPreparedGeneratorClasspath(project))
        // Worker API supplies these parent dependencies for task workers; a standalone JVM
        // needs their locations explicitly. Keep KotlinPoet on the pinned worker classpath.
        spec.parameters.classpath.from(listOf(
            "kotlin.Unit",
            "kotlin.reflect.full.KClasses",
            "kotlinx.serialization.KSerializer",
            "kotlinx.serialization.json.Json",
            "io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteCatalog",
            "io.github.composefluent.winrt.compiler.authoring.WinRTAuthoringMetadataContractsKt",
        ).map { name ->
            requireNotNull(preparedStaticCodeSourcePath(name)) { "Static generator dependency is unavailable: $name" }.toFile()
        })
        // The plugin's existing identity/file helpers use Gradle API types. Supplying the
        // distribution API here does not expose the consumer's buildscript classpath.
        val gradleHome = project.gradle.gradleHomeDir
        spec.parameters.classpath.from(if (gradleHome != null) {
            project.fileTree(gradleHome.resolve("lib")) { it.include("*.jar") }
        } else {
            // ProjectBuilder uses the test Gradle API jar instead of a distribution home.
            project.files(java.io.File(Project::class.java.protectionDomain.codeSource.location.toURI()))
        })
    }.get()
    return sourceRoot
}

private fun preparedStaticProjectionKey(
    files: List<Path>,
    extension: PackageReferencesConfiguration,
    identityFiles: List<java.io.File>,
    supportOwnerIdentity: String,
    project: Project,
    emitJvmAuthoringHostExports: Boolean,
    windowsSdkRegistryRoots: List<Path>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    fun updateField(name: String, value: String) {
        update("field")
        update(name)
        update(value.length.toString())
        update(value)
    }
    fun updateList(name: String, values: List<String>) {
        update("list")
        update(name)
        update(values.size.toString())
        values.forEachIndexed { index, value ->
            update(index.toString())
            update(value.length.toString())
            update(value)
        }
    }
    updateField("schema", "prepared-static-sources-v5")
    updateField("emitSupportFiles", "true")
    updateField("groupProjectionFilesByPackageOnWrite", "true")
    updateField("generationLayout", "SingleSourceSet")
    updateField("emitJvmAuthoringHostExports", emitJvmAuthoringHostExports.toString())
    preparedStaticImplementationRoots().forEach { root ->
        updateImplementationRoot(digest, root)
    }
    updateField("projectPath", project.path)
    updateField("projectName", project.name)
    updateField("supportOwnerIdentity", supportOwnerIdentity)
    updateList("metadataInputs", extension.metadataInputs.get())
    updateList("includeNamespaces", extension.includeNamespaces.get().sorted())
    updateList("includeTypes", extension.includeTypes.get().sorted())
    updateList("excludeNamespaces", extension.excludeNamespaces.get().sorted())
    updateList("excludeTypes", extension.excludeTypes.get().sorted())
    updateList("additionExcludeNamespaces", extension.additionExcludeNamespaces.get().sorted())
    updateField("windowsSdkDeclared", extension.windowsSdkDeclared.get().toString())
    updateField("windowsSdkVersion", extension.windowsSdkVersion.orNull.orEmpty())
    updateField("includeWindowsSdkExtensions", extension.includeWindowsSdkExtensions.get().toString())
    updateField("generateWindowsSdkProjection", extension.generateWindowsSdkProjection.get().toString())
    updateList("windowsSdkRegistryRoots", windowsSdkRegistryRoots.map(Path::toString).sorted())
    extension.nugetPackages
        .map { packageReference ->
            listOf(
                packageReference.packageId,
                packageReference.version.get(),
                packageReference.generateProjection.toString(),
            ).joinToString("\u0001")
        }
        .sorted()
        .let { packageRecords -> updateList("nugetPackages", packageRecords) }
    if (extension is WindowsExtension) {
        updateField("applicationEnabled", extension.applicationEnabled.get().toString())
    }
    updateList("metadataFiles", files.map { file -> file.toAbsolutePath().normalize().toString() }.sorted())
    files.sortedBy(Path::toString).forEach { file ->
        updateField("metadataFilePath", file.toAbsolutePath().normalize().toString())
        digest.update(PreparedProjectionFileFingerprints.content(file))
        digest.update(0)
    }
    updateList("identityFiles", identityFiles.map { file -> file.absolutePath }.sorted())
    identityFiles.sortedBy(java.io.File::getAbsolutePath).forEach { file ->
        updateField("identityFilePath", file.absolutePath)
        digest.update(PreparedProjectionFileFingerprints.content(file.toPath()))
        digest.update(0)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun preparedStaticImplementationRoots(): List<Path> = listOf(
    KotlinWindowsToolkitPlugin::class.java,
    KotlinProjectionGenerator::class.java,
    WinRTMetadataModel::class.java,
    Guid::class.java,
    ClassName::class.java,
).mapNotNull { type ->
    type.protectionDomain?.codeSource?.location?.toURI()?.let(Path::of)
}.plus(
    listOfNotNull(
        preparedStaticCodeSourcePath("io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteCatalog"),
        preparedStaticCodeSourcePath("io.github.composefluent.winrt.compiler.authoring.WinRTAuthoringMetadataContractsKt"),
    ),
).map { path ->
    path.toAbsolutePath().normalize()
}.distinct()
    .sortedBy(Path::toString)

private fun preparedStaticCodeSourcePath(typeName: String): Path? = runCatching {
    Class.forName(typeName, false, KotlinProjectionGenerator::class.java.classLoader)
        .protectionDomain
        ?.codeSource
        ?.location
        ?.toURI()
        ?.let(Path::of)
}.getOrNull()

internal fun preparedStaticImplementationFingerprint(roots: Iterable<Path>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    roots.map { path -> path.toAbsolutePath().normalize() }
        .distinct()
        .sortedBy(Path::toString)
        .forEach { root -> updateImplementationRoot(digest, root) }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun updateImplementationRoot(digest: MessageDigest, root: Path) {
    fun update(value: String) {
        digest.update(value.toByteArray(Charsets.UTF_8))
        digest.update(0)
    }
    val normalizedRoot = root.toAbsolutePath().normalize()
    update("implementation-root")
    update(normalizedRoot.toString())
    when {
        Files.isDirectory(normalizedRoot) -> {
            update("classes-directory")
            Files.walk(normalizedRoot).use { stream ->
                stream.filter(Files::isRegularFile)
                    .map(normalizedRoot::relativize)
                    .sorted()
                    .forEach { relative ->
                        update(relative.toString().replace('\\', '/'))
                        digest.update(PreparedProjectionFileFingerprints.content(normalizedRoot.resolve(relative)))
                        digest.update(0)
                    }
            }
        }

        Files.isRegularFile(normalizedRoot) -> {
            update("archive")
            digest.update(PreparedProjectionFileFingerprints.archive(normalizedRoot) {
              val archiveDigest = MessageDigest.getInstance("SHA-256")
              JarFile(normalizedRoot.toFile()).use { jar ->
                val entries = mutableListOf<String>()
                val enumeration = jar.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (!entry.isDirectory) entries += entry.name
                }
                entries.sorted().forEach { name ->
                    archiveDigest.update(name.toByteArray(Charsets.UTF_8))
                    archiveDigest.update(0)
                    jar.getInputStream(jar.getJarEntry(name)).use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            archiveDigest.update(buffer, 0, count)
                        }
                    }
                    archiveDigest.update(0)
                }
              }
              archiveDigest.digest()
            })
        }

        else -> update("missing")
    }
}

private fun updateFileContents(digest: MessageDigest, path: Path) {
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
}

internal fun writePreparedStaticManifest(path: Path, files: List<Path>, model: WinRTMetadataModel) {
    val names = model.namespaces.flatMap { namespace -> namespace.types }.map { type -> type.qualifiedName }.sorted()
    val encodedFiles = files.sortedBy(Path::toString).joinToString("\n") { file ->
        val digest = MessageDigest.getInstance("SHA-256")
        updateFileContents(digest, file)
        val digestHex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        "file\t${Base64.getUrlEncoder().withoutPadding().encodeToString(file.toString().toByteArray())}\t$digestHex"
    }
    val content = buildString {
        append(PREPARED_STATIC_HEADER).append('\n')
        append("types\t").append(names.joinToString(",")).append('\n')
        if (encodedFiles.isNotEmpty()) append(encodedFiles).append('\n')
        val root = path.parent.resolve("sources")
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(relative.toByteArray(Charsets.UTF_8))
                append("output\t").append(encoded).append('\t').append(preparedOutputHash(file)).append('\n')
            }
        }
    }
    GradleFileOperations.writeStringIfChanged(path, content)
}

private fun preparedOutputHash(path: Path): String {
    return java.util.HexFormat.of().formatHex(PreparedProjectionFileFingerprints.content(path))
}

/** Validate both the output inventory and bytes before reusing a prepared projection. */
internal fun isPreparedStaticSourceValid(sourceRoot: Path): Boolean = runCatching {
    val manifest = sourceRoot.parent.resolve("manifest.tsv")
    if (!Files.isDirectory(sourceRoot) || !Files.isRegularFile(manifest)) return false
    val lines = Files.readAllLines(manifest)
    if (lines.firstOrNull() != PREPARED_STATIC_HEADER) return false
    val root = sourceRoot.toAbsolutePath().normalize()
    val expected = mutableSetOf<Path>()
    for (line in lines.drop(1).filter { it.startsWith("output\t") }) {
        val fields = line.split('\t')
        if (fields.size != 3) return false
        val relative = Path.of(String(Base64.getUrlDecoder().decode(fields[1]), Charsets.UTF_8))
        val file = root.resolve(relative).normalize()
        if (relative.isAbsolute || !file.startsWith(root) || !expected.add(file)) return false
        if (!Files.isRegularFile(file) || preparedOutputHash(file) != fields[2]) return false
    }
    Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile).allMatch { it in expected }
    }
}.getOrDefault(false)

internal fun materializePreparedStaticSources(sourceRoot: Path, generatedRoot: Path) {
    if (!Files.isDirectory(sourceRoot)) {
        clearPreparedStaticSources(generatedRoot)
        return
    }
    val manifest = generatedRoot.resolve(".kotlin-winrt-prepared-static-files.tsv")
    val previousFiles = (if (Files.isRegularFile(manifest)) {
        Files.readAllLines(manifest).filter(String::isNotBlank).toSet()
    } else {
        emptySet()
    })
    val previousOwnedFiles = previousFiles + markedGeneratedProjectionFiles(generatedRoot, previousFiles)
    val currentFiles = mutableSetOf<String>()
    Files.walk(sourceRoot).use { stream ->
        stream.filter(Files::isRegularFile).forEach { source ->
            val relative = sourceRoot.relativize(source).toString().replace('\\', '/')
            currentFiles += relative
            val target = generatedRoot.resolve(relative)
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || preparedOutputHash(source) != preparedOutputHash(target)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    previousOwnedFiles.asSequence()
        .filterNot(currentFiles::contains)
        .map(generatedRoot::resolve)
        .forEach(Files::deleteIfExists)
    GradleFileOperations.writeStringIfChanged(
        manifest,
        currentFiles.sorted().joinToString(separator = "\n", postfix = if (currentFiles.isEmpty()) "" else "\n"),
    )
}

/** Adopts files from task generation predating the prepared-source ownership manifest. */
private fun markedGeneratedProjectionFiles(root: Path, knownFiles: Set<String> = emptySet()): Set<String> {
    if (!Files.isDirectory(root)) return emptySet()
    return Files.walk(root).use { paths ->
        paths.filter { root.relativize(it).toString().replace('\\', '/') !in knownFiles }
            .filter { isGeneratedWinRTProjectionSource(it.toFile()) }
            .map { root.relativize(it).toString().replace('\\', '/') }
            .toList().toSet()
    }
}

internal fun clearPreparedStaticSources(generatedRoot: Path) {
    val manifest = generatedRoot.resolve(".kotlin-winrt-prepared-static-files.tsv")
    val previousFiles = (if (Files.isRegularFile(manifest)) Files.readAllLines(manifest) else emptyList()) +
        markedGeneratedProjectionFiles(generatedRoot)
    if (previousFiles.isEmpty()) return
    val normalizedRoot = generatedRoot.toAbsolutePath().normalize()
    previousFiles
        .asSequence()
        .distinct()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { relative ->
            require(!Path.of(relative).isAbsolute) {
                "Prepared static manifest contains an absolute path: $relative"
            }
            normalizedRoot.resolve(relative).normalize().also { target ->
                require(target.startsWith(normalizedRoot)) {
                    "Prepared static manifest escapes its generated root: $relative"
                }
            }
        }
        .forEach(Files::deleteIfExists)
    GradleFileOperations.writeStringIfChanged(manifest, "")
}
