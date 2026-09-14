package io.github.composefluent.windows.toolkit.gradle

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceResolver
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.Guid
import org.gradle.api.GradleException
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
    val persistentNuGetRoot = project.layout.projectDirectory
        .dir(".gradle/kotlin-winrt/prepared-nuget")
        .asFile
        .toPath()
    val explicitNuGetRoots = extension.nugetGlobalPackagesRoots.get().map(Path::of) +
        explicitNuGetReferences.flatMap { source -> source.globalPackagesRoots }
    val configuredNuGetRoots = explicitNuGetRoots + listOf(persistentNuGetRoot)
    fun nuGetRoots(lookupOnly: Boolean, specs: List<String> = emptyList()): List<Path> =
        project.providers.of(PreparedNuGetRootsValueSource::class.java) { spec ->
            spec.parameters.executable.set(extension.nugetExecutable)
            spec.parameters.cliVersion.set(extension.nugetCliVersion)
            spec.parameters.cliCacheDirectory.set(project.gradle.gradleUserHomeDir.toPath().resolve("caches/kotlin-winrt/nuget-cli").toString())
            spec.parameters.scratchDirectory.set(project.layout.projectDirectory.dir(".gradle/kotlin-winrt/nuget-scratch").asFile.path)
            spec.parameters.installRoot.set(persistentNuGetRoot.toString())
            spec.parameters.packageSpecs.set(specs)
            spec.parameters.lookupOnly.set(lookupOnly)
        }.get().map(Path::of)
    val preparedNuGetSources = if (packageSpecs.isEmpty()) {
        emptyList()
    } else {
        val packageIdentities = packageSpecs.map(::parseNuGetPackageIdentity)
        val configuredRootsContainPackages = packageIdentities.all { identity ->
            isNuGetPackageClosureAvailable(identity, configuredNuGetRoots)
        }
        val cliNuGetRoots = if (
            extension.useNuGetCliGlobalPackages.get() &&
            !configuredRootsContainPackages &&
            !project.gradle.startParameter.isOffline
        ) {
            nuGetRoots(lookupOnly = true)
        } else {
            emptyList()
        }
        resolveNuGetProjectionMetadataSources(
            packageSpecs = packageSpecs,
            explicitGlobalPackagesRoots = configuredNuGetRoots,
            cliGlobalPackagesRoots = cliNuGetRoots,
            restoreNuGetPackages = extension.restoreNuGetPackages.get(),
            restoreMissing = { identities ->
                if (identities.isEmpty()) {
                    emptyList()
                } else {
                    if (project.gradle.startParameter.isOffline) {
                        throw GradleException("Projected NuGet packages are missing while Gradle is offline: ${identities.joinToString()}")
                    }
                    nuGetRoots(lookupOnly = false, specs = identities.map { "${it.normalizedPackageId}@${it.normalizedVersion}" })
                }
            },
        )
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
        } + preparedNuGetSources
    if (effectiveSources.isEmpty()) {
        throw StaticPreparationUnavailable("no fixed metadata or projected NuGet package is configured")
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
    val storeRoot = project.layout.projectDirectory.dir(".gradle/kotlin-winrt/prepared-imports").asFile.toPath()
    val entry = storeRoot.resolve(key)
    val sourceRoot = entry.resolve("sources")
    // Keep the full metadata model and KotlinPoet graph out of the configuration daemon.
    // Only a cold/invalid entry starts a bounded process; warm imports just validate/copy bytes.
    val request = mapOf(
        "entry" to entry.toString(),
        "sources" to effectiveSources.joinToString("\u0000") { source ->
            when (source) {
                is WinRTMetadataSource.PathSource -> source.path.toAbsolutePath().normalize().toString()
                is WinRTMetadataSource.NuGetPackage -> "nuget:${source.packagePath.toAbsolutePath().normalize()}"
                is WinRTMetadataSource.NuGetPackageReference -> "nuget:${source.packageId}@${source.version}"
                is WinRTMetadataSource.WindowsSdk -> (source.version ?: "sdk") +
                    if (source.includeExtensions) "+" else ""
                else -> error("Static projection source was not resolved: $source")
            }
        },
        "registryRoots" to registryRootPaths.orEmpty().joinToString("\u0000"),
        "modelCache" to project.layout.projectDirectory.dir(".gradle/kotlin-winrt/metadata-models").asFile.path,
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
    ) + effectiveSources.mapIndexedNotNull { index, source ->
        (source as? WinRTMetadataSource.NuGetPackageReference)?.let {
            "nugetRoots.$index" to it.globalPackagesRoots.joinToString("\u0000") { root -> root.toAbsolutePath().normalize().toString() }
        }
    }.toMap()
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
    materializePreparedStaticSources(sourceRoot, generatedOutputDirectory.get().asFile.toPath())
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
    updateField("schema", "prepared-static-sources-v4")
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
    updateField("restoreNuGetPackages", extension.restoreNuGetPackages.get().toString())
    updateField("useNuGetCliGlobalPackages", extension.useNuGetCliGlobalPackages.get().toString())
    updateField("nugetExecutable", extension.nugetExecutable.get())
    updateField("nugetCliVersion", extension.nugetCliVersion.get())
    updateList("nugetGlobalPackagesRoots", extension.nugetGlobalPackagesRoots.get().map(Path::of).map(Path::toString).sorted())
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
        updateFileContents(digest, file)
        digest.update(0)
    }
    updateList("identityFiles", identityFiles.map { file -> file.absolutePath }.sorted())
    identityFiles.sortedBy(java.io.File::getAbsolutePath).forEach { file ->
        updateField("identityFilePath", file.absolutePath)
        updateFileContents(digest, file.toPath())
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
                        updateFileContents(digest, normalizedRoot.resolve(relative))
                        digest.update(0)
                    }
            }
        }

        Files.isRegularFile(normalizedRoot) -> {
            update("archive")
            JarFile(normalizedRoot.toFile()).use { jar ->
                val entries = mutableListOf<String>()
                val enumeration = jar.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (!entry.isDirectory) entries += entry.name
                }
                entries.sorted().forEach { name ->
                    update(name)
                    jar.getInputStream(jar.getJarEntry(name)).use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    digest.update(0)
                }
            }
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
    val digest = MessageDigest.getInstance("SHA-256")
    updateFileContents(digest, path)
    return java.util.HexFormat.of().formatHex(digest.digest())
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
    }) + markedGeneratedProjectionFiles(generatedRoot)
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

/** Adopts files from task generation predating the prepared-source ownership manifest. */
private fun markedGeneratedProjectionFiles(root: Path): Set<String> {
    if (!Files.isDirectory(root)) return emptySet()
    return Files.walk(root).use { paths ->
        paths.filter { isGeneratedWinRTProjectionSource(it.toFile()) }
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
