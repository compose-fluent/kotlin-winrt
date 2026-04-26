package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtMetadataLoader
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataSource
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageIdentity
import io.github.kitectlab.winrt.metadata.WinRtNuGetPackageResolver
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeRefKind
import io.github.kitectlab.winrt.projections.generator.KotlinProjectionGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.streams.asSequence

abstract class GenerateWinRtProjectionsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

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
    abstract val nugetExecutable: Property<String>

    @get:Input
    abstract val nugetCliVersion: Property<String>

    @get:Internal
    abstract val nugetCliCacheDirectory: DirectoryProperty

    @get:Input
    abstract val restoreNuGetPackages: Property<Boolean>

    @get:Input
    abstract val useNuGetCliGlobalPackages: Property<Boolean>

    @get:Input
    abstract val nugetGlobalPackagesRoots: ListProperty<String>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @TaskAction
    fun generate() {
        val sources = metadataSources()
        val model = WinRtMetadataLoader.loadSources(sources).filterProjectionSurface(
            namespaces = includeNamespaces.get().toSet(),
            types = includeTypes.get().toSet(),
            excludedNamespaces = excludeNamespaces.get().toSet(),
            excludedTypes = excludeTypes.get().toSet(),
        )
        KotlinProjectionGenerator(
            emitSupportFiles = true,
            projectionContext = WinRtMetadataProjectionContext(
                sources = sources,
                include = includeNamespaces.get().toSet() + includeTypes.get().toSet(),
                exclude = excludeNamespaces.get().toSet() + excludeTypes.get().toSet(),
                additionExclude = additionExcludeNamespaces.get().toSet(),
            ),
        ).generateTo(model, outputDirectory.get().asFile.toPath())
    }

    private fun metadataSources(): List<WinRtMetadataSource> {
        val explicitSources = metadataInputs.get().map(WinRtMetadataSource::parse)
        val sdkSource = if (windowsSdkVersion.isPresent || includeWindowsSdkExtensions.get()) {
            listOf(
                WinRtMetadataSource.windowsSdk(
                    version = windowsSdkVersion.orNull,
                    includeExtensions = includeWindowsSdkExtensions.get(),
                ),
            )
        } else {
            emptyList()
        }
        val explicitNuGetRoots = nugetGlobalPackagesRoots.get().map(Path::of)
        val cliNuGetRoots = nugetCliGlobalPackagesRoots()
        val packageIdentities = nugetPackages.get().map(::parseNuGetPackageIdentity)
        val nugetRoots = explicitNuGetRoots + cliNuGetRoots
        val availableNuGetIdentities = packageIdentities.filter { identity ->
            isNuGetPackageAvailable(identity, nugetRoots)
        }
        val missingNuGetIdentities = packageIdentities - availableNuGetIdentities.toSet()
        val restoredPackageDirectories = if (restoreNuGetPackages.get()) {
            restoreNuGetPackages(missingNuGetIdentities)
        } else {
            emptyList()
        }
        require(missingNuGetIdentities.isEmpty() || restoredPackageDirectories.isNotEmpty()) {
            "NuGet packages are missing from the configured NuGet cache and restoreNuGetPackages is false: ${missingNuGetIdentities.joinToString()}"
        }
        val resolvedNuGetSources = availableNuGetIdentities.map { identity ->
            WinRtMetadataSource.nugetPackage(
                packageId = identity.normalizedPackageId,
                version = identity.normalizedVersion,
                globalPackagesRoots = nugetRoots,
            )
        }
        val restoredNuGetSources = restoredPackageDirectories.map(WinRtMetadataSource::nugetPackage)
        return (explicitSources + sdkSource + resolvedNuGetSources + restoredNuGetSources).ifEmpty {
            listOf(WinRtMetadataSource.windowsSdk())
        }
    }

    private fun isNuGetPackageAvailable(
        identity: WinRtNuGetPackageIdentity,
        globalPackagesRoots: List<Path>,
    ): Boolean {
        val roots = WinRtNuGetPackageResolver.globalPackagesRoots(explicitRoots = globalPackagesRoots)
        return runCatching {
            WinRtNuGetPackageResolver.packageRoot(identity, roots)
        }.isSuccess
    }

    private fun restoreNuGetPackages(
        packageIdentities: List<WinRtNuGetPackageIdentity>,
    ): List<Path> {
        if (packageIdentities.isEmpty()) {
            return emptyList()
        }

        val installRoot = temporaryDir.toPath().resolve("nuget-install")
        Files.createDirectories(installRoot)
        packageIdentities.forEach { identity ->
            runNuGetInstall(identity, installRoot)
        }
        return discoverInstalledPackages(installRoot)
    }

    private fun runNuGetInstall(
        identity: WinRtNuGetPackageIdentity,
        installRoot: Path,
    ) {
        runNuGetCommand(
            arguments = listOf(
                "install",
                identity.normalizedPackageId,
                "-Version",
                identity.normalizedVersion,
                "-NonInteractive",
                "-OutputDirectory",
                installRoot.toString(),
            ),
            workingDirectory = installRoot,
            description = "install $identity",
        )
    }

    private fun discoverInstalledPackages(installRoot: Path): List<Path> =
        Files.list(installRoot).use { stream ->
            stream.asSequence()
                .filter { it.isDirectory() }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

    private fun parseNuGetPackageIdentity(spec: String): WinRtNuGetPackageIdentity {
        val separator = spec.lastIndexOf('@')
        require(separator > 0 && separator < spec.lastIndex) {
            "NuGet package must use '<id>@<version>' format: $spec"
        }
        return WinRtNuGetPackageIdentity(
            packageId = spec.substring(0, separator),
            version = spec.substring(separator + 1),
        )
    }

    private fun nugetCliGlobalPackagesRoots(): List<Path> {
        if (!useNuGetCliGlobalPackages.get()) {
            return emptyList()
        }
        return runCatching {
            val invocation = runNuGetCommand(
                arguments = listOf("locals", "global-packages", "-list"),
                description = "locate global-packages",
            )
            WinRtNuGetPackageResolver.parseNuGetGlobalPackagesOutput(invocation.output)
        }.getOrElse { error ->
            logger.info("NuGet CLI global-packages lookup failed: ${error.message}")
            emptyList()
        }
    }

    private fun runNuGetCommand(
        arguments: List<String>,
        workingDirectory: Path? = null,
        description: String,
    ): NuGetInvocation {
        val configuredInvocation = invokeNuGet(nugetExecutable.get(), arguments, workingDirectory)
        if (configuredInvocation.isSuccess) {
            return configuredInvocation
        }

        val cachedCommand = cachedNuGetCommand()
        if (cachedCommand == nugetExecutable.get()) {
            throw nugetFailure(description, configuredInvocation, null)
        }

        logger.info(
            "Configured Microsoft NuGet CLI '${nugetExecutable.get()}' failed for $description; " +
                "retrying with cached CLI $cachedCommand.",
        )
        val cachedInvocation = invokeNuGet(cachedCommand, arguments, workingDirectory)
        if (cachedInvocation.isSuccess) {
            return cachedInvocation
        }

        throw nugetFailure(description, configuredInvocation, cachedInvocation)
    }

    private fun invokeNuGet(
        executable: String,
        arguments: List<String>,
        workingDirectory: Path?,
    ): NuGetInvocation {
        val command = listOf(executable) + arguments
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile())
        }
        val process = runCatching { processBuilder.start() }.getOrElse { error ->
            return NuGetInvocation(
                executable = executable,
                exitCode = -1,
                output = error.message.orEmpty(),
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return NuGetInvocation(
            executable = executable,
            exitCode = exitCode,
            output = output,
        )
    }

    private fun nugetFailure(
        description: String,
        configuredInvocation: NuGetInvocation,
        cachedInvocation: NuGetInvocation?,
    ): GradleException {
        val configuredMessage =
            "configured '${configuredInvocation.executable}' exited ${configuredInvocation.exitCode}:$LINE_SEPARATOR${configuredInvocation.output}"
        val cachedMessage = cachedInvocation?.let {
            "${LINE_SEPARATOR}cached '${it.executable}' exited ${it.exitCode}:$LINE_SEPARATOR${it.output}"
        }.orEmpty()
        return GradleException("Microsoft NuGet CLI failed to $description.$LINE_SEPARATOR$configuredMessage$cachedMessage")
    }

    private fun cachedNuGetCommand(): String {
        val cachedExecutable = cachedNuGetExecutable()
        if (cachedExecutable.notExists()) {
            downloadNuGetCli(cachedExecutable)
        }
        return cachedExecutable.toString()
    }

    private fun cachedNuGetExecutable(): Path =
        nugetCliCacheDirectory.get().asFile.toPath()
            .resolve(nugetCliVersion.get())
            .resolve("nuget.exe")

    private fun downloadNuGetCli(targetExecutable: Path) {
        val version = nugetCliVersion.get()
        val packageUrl = URI(
            "https://api.nuget.org/v3-flatcontainer/nuget.commandline/$version/nuget.commandline.$version.nupkg",
        ).toURL()
        Files.createDirectories(targetExecutable.parent)
        logger.lifecycle("Downloading Microsoft NuGet CLI $version to $targetExecutable")
        packageUrl.openStream().use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { entry -> entry.name.equals("tools/nuget.exe", ignoreCase = true) }
                    ?: throw GradleException("NuGet.CommandLine $version package does not contain tools/nuget.exe.")
                Files.newOutputStream(targetExecutable).use { output ->
                    zip.copyTo(output)
                }
            }
        }
        if (targetExecutable.notExists()) {
            throw GradleException("Failed to cache Microsoft NuGet CLI at $targetExecutable")
        }
    }
}

private data class NuGetInvocation(
    val executable: String,
    val exitCode: Int,
    val output: String,
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}

private val LINE_SEPARATOR: String = System.lineSeparator()

private fun WinRtMetadataModel.filterProjectionSurface(
    namespaces: Set<String>,
    types: Set<String>,
    excludedNamespaces: Set<String>,
    excludedTypes: Set<String>,
): WinRtMetadataModel =
    if (namespaces.isEmpty() && types.isEmpty() && excludedNamespaces.isEmpty() && excludedTypes.isEmpty()) {
        this
    } else {
        val typesByQualifiedName = this.namespaces
            .flatMap(WinRtNamespace::types)
            .associateBy(WinRtTypeDefinition::qualifiedName)
        val includedNames = linkedSetOf<String>()
        this.namespaces.forEach { namespace ->
            namespace.types.forEach { type ->
                if (type.isProjectionFilterIncluded(namespaces, types, excludedNamespaces, excludedTypes)) {
                    includedNames += type.qualifiedName
                }
            }
        }
        val pending = ArrayDeque(includedNames)
        while (pending.isNotEmpty()) {
            val type = typesByQualifiedName[pending.removeFirst()] ?: continue
            type.referencedTypeNames()
                .filter { referenced -> referenced in typesByQualifiedName }
                .filterNot { referenced -> excludedTypes.any { referenced.startsWith("$it.") || referenced == it } }
                .forEach { referenced ->
                    if (includedNames.add(referenced)) {
                        pending += referenced
                    }
                }
        }
        WinRtMetadataModel(
            this.namespaces.mapNotNull { namespace ->
                val namespaceTypes = namespace.types.filter { type -> type.qualifiedName in includedNames }
                namespaceTypes.takeIf { it.isNotEmpty() }?.let { WinRtNamespace(namespace.name, it) }
            },
        ).normalized()
    }

private fun WinRtTypeDefinition.isProjectionFilterIncluded(
    namespaces: Set<String>,
    types: Set<String>,
    excludedNamespaces: Set<String>,
    excludedTypes: Set<String>,
): Boolean {
    val hasIncludeFilter = namespaces.isNotEmpty() || types.isNotEmpty()
    val explicitlyIncludedType = types.any { qualifiedName.startsWith("$it.") || qualifiedName == it }
    val included = !hasIncludeFilter ||
        namespaces.any { qualifiedName.startsWith("$it.") || qualifiedName == it } ||
        explicitlyIncludedType
    val explicitlyExcludedType = excludedTypes.any { qualifiedName.startsWith("$it.") || qualifiedName == it }
    val namespaceExcludeOverridden = excludedNamespaces.any { excludedNamespace ->
        namespaces.any { includedNamespace ->
            includedNamespace.startsWith("$excludedNamespace.") &&
                (qualifiedName.startsWith("$includedNamespace.") || qualifiedName == includedNamespace)
        }
    }
    val excluded = explicitlyExcludedType ||
        (
            !explicitlyIncludedType &&
                !namespaceExcludeOverridden &&
                excludedNamespaces.any { qualifiedName.startsWith("$it.") || qualifiedName == it }
            )
    return included && !excluded
}

private fun WinRtTypeDefinition.referencedTypeNames(): Set<String> = buildSet {
    baseType?.let { addTypeRef(it) }
    defaultInterface?.let { addTypeRef(it) }
    implementedInterfaces.forEach { addTypeRef(it.interfaceType) }
    fields.forEach { addTypeRef(it.type) }
    methods.forEach { method ->
        addTypeRef(method.returnType)
        method.parameters.forEach { addTypeRef(it.type) }
    }
    properties.forEach { addTypeRef(it.type) }
    events.forEach { addTypeRef(it.delegateType) }
    activation.activatableFactoryInterface?.let { addTypeRef(it) }
    activation.staticInterfaces.forEach { addTypeRef(it) }
    activation.composableFactoryInterface?.let { addTypeRef(it) }
    activation.factories.forEach { addTypeRef(it.interfaceType) }
}

private fun MutableSet<String>.addTypeRef(type: WinRtTypeRef) {
    when (type.kind) {
        WinRtTypeRefKind.Named -> {
            type.qualifiedName?.let(::add)
            type.typeArguments.forEach(::addTypeRef)
        }
        WinRtTypeRefKind.Array -> type.elementType?.let(::addTypeRef)
        WinRtTypeRefKind.GenericTypeParameter,
        WinRtTypeRefKind.MethodTypeParameter,
        WinRtTypeRefKind.Unknown -> Unit
    }
}
