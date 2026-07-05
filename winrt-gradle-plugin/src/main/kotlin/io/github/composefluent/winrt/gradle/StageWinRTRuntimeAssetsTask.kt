package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRTNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.runtime.WinUiRuntimeAssetManifests
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

@CacheableTask
abstract class StageWinRTRuntimeAssetsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Input
    abstract val runtimeAssets: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyRuntimeAssetFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nugetPackageContentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resolvedNuGetPackageManifestFiles: ConfigurableFileCollection

    @get:Input
    abstract val nugetGlobalPackagesRoots: ListProperty<String>

    @get:Input
    abstract val useNuGetCliGlobalPackages: Property<Boolean>

    @get:Input
    abstract val nugetExecutable: Property<String>

    @get:Input
    abstract val nugetCliVersion: Property<String>

    @get:Internal
    abstract val nugetCliCacheDirectory: DirectoryProperty

    @get:Input
    abstract val restoreNuGetPackages: Property<Boolean>

    @get:Input
    abstract val runtimeIdentifier: org.gradle.api.provider.Property<String>

    @get:Input
    abstract val executableBaseName: Property<String>

    @get:Input
    abstract val generateProjectPri: Property<Boolean>

    @get:Input
    abstract val projectPriIndexName: Property<String>

    @get:Input
    abstract val projectPriFallbackIndexName: Property<String>

    @get:Input
    abstract val projectPriInitialPath: Property<String>

    @get:Input
    abstract val projectPriDefaultLanguage: Property<String>

    @get:Input
    abstract val projectPriDefaultQualifiers: ListProperty<String>

    @get:Input
    abstract val enableDefaultProjectPriResources: Property<Boolean>

    @get:Input
    abstract val makePriExecutable: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val projectPriTargetPaths: MapProperty<String, String>

    @get:Input
    abstract val projectPriExcludedFromBuildPaths: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appxManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriContentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriEmbedFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriContentFiles: ConfigurableFileCollection

    @get:Internal
    abstract val defaultProjectPriResourceRoot: DirectoryProperty

    @Input
    fun getDefaultProjectPriResourceRootPath(): String =
        defaultProjectPriResourceRoot.orNull
            ?.asFile
            ?.toPath()
            ?.toAbsolutePath()
            ?.normalize()
            ?.toString()
            .orEmpty()

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredMetadataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredTargetArtifactFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostDllFiles: ConfigurableFileCollection

    init {
        generateProjectPri.convention(true)
        projectPriIndexName.convention("")
        projectPriFallbackIndexName.convention("Application")
        projectPriInitialPath.convention("")
        projectPriDefaultLanguage.convention("")
        projectPriDefaultQualifiers.convention(listOf("scale-200", "contrast-standard"))
        enableDefaultProjectPriResources.convention(true)
        makePriExecutable.convention("")
        windowsSdkVersion.convention("")
        projectPriTargetPaths.convention(emptyMap())
        projectPriExcludedFromBuildPaths.convention(emptySet())
        executableBaseName.convention("app")
    }

    @TaskAction
    fun stage() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        GradleFileOperations.cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        copyRequiredFiles(
            paths = (runtimeAssetFiles.files + dependencyRuntimeAssetFiles.files).map { it.toPath() },
            outputRoot = outputRoot,
            description = "declared runtime asset",
        )
        writeDependencyRuntimeAssetRecords(
            records = dependencyIdentityFiles.files.flatMap(::readDependencyRuntimeAssetRecords),
            outputRoot = outputRoot,
        )
        copyOptionalFiles(authoredMetadataFiles.files.map { it.toPath() }, outputRoot)
        writeDependencyAuthoredMetadataRecords(
            records = dependencyIdentityFiles.files.flatMap(::readDependencyAuthoredMetadataRecords),
            outputRoot = outputRoot,
        )
        copyOptionalFiles(authoredHostManifestFiles.files.map { it.toPath() }, outputRoot)
        val dependencyHostManifests = writeDependencyAuthoredHostManifestRecords(
            records = dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifestRecords),
            outputRoot = outputRoot,
        )
        stageAuthoringHostRuntimeConfigs(
            sources = authoredHostManifestFiles.files.filter(java.io.File::isFile) + dependencyHostManifests.map(Path::toFile),
            outputRoot = outputRoot,
        )
        copyOptionalFiles(authoredTargetArtifactFiles.files.map { it.toPath() }, outputRoot)
        writeDependencyAuthoredTargetArtifactRecords(
            records = dependencyIdentityFiles.files.flatMap(::readDependencyAuthoredTargetArtifactRecords),
            outputRoot = outputRoot,
        )
        authoredHostDllFiles.files
            .asSequence()
            .map { it.toPath() }
            .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        val identities = (nugetPackages.get() + dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .map(::parseNuGetPackageIdentity)
            .distinctBy { "${it.normalizedPackageId.lowercase()}:${it.normalizedVersion.lowercase()}" }
        val resolvedPackageRoots = resolvedNuGetPackageManifestFiles.files
            .flatMap(::readResolvedRuntimeNuGetPackageRoots)
            .map(Path::of)
        val resolvedPackages = resolveNuGetPackages(
            identities = identities,
            resolvedPackageRoots = resolvedPackageRoots,
            modeledPackageRoots = nugetPackageContentFiles.files.map { it.toPath() },
        )
        val rid = runtimeIdentifier.get()
        resolvedPackages.forEach { resolved ->
            stageTopLevelDlls(resolved.packageRoot, outputRoot)
            stageRuntimeNativeDlls(resolved.packageRoot.resolve("runtimes").resolve(rid).resolve("native"), outputRoot)
            stageLibNativeAssets(resolved.packageRoot.resolve("lib").resolve("native"), rid, outputRoot)
            if (resolved.identity.isWindowsAppSdkRootPackage()) {
                stageWindowsAppSdkVersionInfo(resolved.packageRoot, outputRoot)
            }
            stageLiftedRegistrations(resolved.identity, resolved.packageRoot, outputRoot)
            stageFrameworkNativeAssets(
                resolved.packageRoot.resolve("runtimes-framework").resolve(rid).resolve("native"),
                outputRoot,
            )
            stageMsBuildCopyLocalPayloads(resolved.packageRoot, rid, outputRoot)
        }
        stageGeneratedComponentRegistrations(outputRoot)
        stageXamlMetadataProviderManifest(outputRoot)
        WinRTApplicationManifestGenerator.writeApplicationManifest(
            outputRoot,
            executableBaseName.get(),
            winRTManifestProcessorArchitecture(runtimeIdentifier.get()),
        )
        generateProjectPri(outputRoot)
    }

    private fun copyOptionalFiles(
        paths: Iterable<Path>,
        outputRoot: Path,
    ) {
        paths
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isRegularFile()) {
                    GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
                }
            }
    }

    private fun copyRequiredFiles(
        paths: Iterable<Path>,
        outputRoot: Path,
        description: String,
        origin: String? = null,
    ) {
        paths
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                require(source.isRegularFile()) {
                    buildString {
                        append("Kotlin/WinRT runtime asset staging requires $description file $source")
                        if (origin != null) {
                            append(" $origin")
                        }
                        append(" to exist.")
                    }
                }
                GradleFileOperations.copyFile(source, outputRoot.resolve(source.name))
            }
    }

    private fun resolveNuGetPackages(
        identities: List<WinRTNuGetPackageIdentity>,
        resolvedPackageRoots: List<Path>,
        modeledPackageRoots: List<Path>,
    ): List<io.github.composefluent.winrt.metadata.WinRTNuGetResolvedPackage> {
        if (resolvedPackageRoots.isNotEmpty()) {
            return resolvedPackageRoots
                .asSequence()
                .filter { it.isDirectory() }
                .mapNotNull { root ->
                    runCatching {
                        io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver.resolvePackageRoot(root)
                    }.getOrNull()
                }
                .distinctBy { it.packageRoot.toAbsolutePath().normalize().toString().lowercase() }
                .toList()
        }
        if (identities.isEmpty()) {
            return emptyList()
        }
        val resolvedFromModeledInputs = resolveNuGetPackagesFromModeledInputs(identities, modeledPackageRoots)
        if (resolvedFromModeledInputs != null) {
            return resolvedFromModeledInputs
        }
        error(
            "Resolved WinRT runtime NuGet package inputs are incomplete for: ${identities.joinToString()}. " +
                "Run resolveWinRTRuntimeNuGetPackages or provide nugetPackageContentFiles containing the full package closure.",
        )
    }

    private fun stageTopLevelDlls(packageRoot: Path, outputRoot: Path) {
        Files.list(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageRuntimeNativeDlls(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".dll", ignoreCase = true) }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.name)) }
        }
    }

    private fun stageLibNativeAssets(nativeRoot: Path, runtimeIdentifier: String, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        val arch = runtimeIdentifier.substringAfter("win-", missingDelimiterValue = runtimeIdentifier)
        val candidates = listOf(
            nativeRoot.resolve("Release").resolve(arch),
            nativeRoot.resolve("Debug").resolve(arch),
            nativeRoot.resolve(arch),
        )
        val selectedRoot = candidates.firstOrNull { it.isDirectory() } ?: return
        Files.walk(selectedRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.relativeTo(selectedRoot))) }
        }
    }

    private fun stageFrameworkNativeAssets(nativeRoot: Path, outputRoot: Path) {
        if (!nativeRoot.isDirectory()) {
            return
        }
        Files.walk(nativeRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .forEach { source -> GradleFileOperations.copyFile(source, outputRoot.resolve(source.relativeTo(nativeRoot))) }
        }
    }

    private fun stageMsBuildCopyLocalPayloads(packageRoot: Path, runtimeIdentifier: String, outputRoot: Path) {
        WinRTNuGetMsBuildPayloadResolver.resolveCopyLocalPayloads(packageRoot, runtimeIdentifier)
            .forEach { payload ->
                GradleFileOperations.copyFile(payload.source, outputRoot.resolve(payload.targetRelativePath))
            }
    }

    private fun stageWindowsAppSdkVersionInfo(packageRoot: Path, outputRoot: Path) {
        val versionInfo = packageRoot.resolve("include").resolve("WindowsAppSDK-VersionInfo.h")
        if (versionInfo.isRegularFile()) {
            GradleFileOperations.copyFile(versionInfo, outputRoot.resolve("include").resolve(versionInfo.name))
        }
    }

    private fun stageLiftedRegistrations(
        identity: WinRTNuGetPackageIdentity,
        packageRoot: Path,
        outputRoot: Path,
    ) {
        Files.walk(packageRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .forEach { source ->
                    GradleFileOperations.copyFile(
                        source,
                        outputRoot
                            .resolve("registrations")
                            .resolve(identity.normalizedPackageId)
                            .resolve(source.relativeTo(packageRoot)),
                    )
                }
        }
    }

    private fun stageXamlMetadataProviderManifest(outputRoot: Path) {
        val providers = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".winmd", ignoreCase = true) }
                .flatMap { winmd -> discoverXamlMetadataProviders(winmd).asSequence() }
                .filterNot { it == "Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider" }
                .distinct()
                .sorted()
                .toList()
        }
        if (providers.isEmpty()) {
            return
        }
        Files.writeString(
            outputRoot.resolve(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName),
            providers.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun discoverXamlMetadataProviders(winmd: Path): List<String> =
        runCatching {
            WinRTMetadataLoader.load(winmd)
                .namespaces
                .flatMap { namespace -> namespace.types }
                .filter(::isXamlMetadataProviderType)
                .map(WinRTTypeDefinition::qualifiedName)
        }.getOrElse { error ->
            logger.warn("Skipping XAML metadata provider discovery for ${winmd.fileName}: ${error.message}")
            emptyList()
        }

    private fun isXamlMetadataProviderType(type: WinRTTypeDefinition): Boolean {
        if (type.kind != WinRTTypeKind.RuntimeClass) {
            return false
        }
        return type.implementedInterfaces.any { implementation ->
            implementation.interfaceName == "Microsoft.UI.Xaml.Markup.IXamlMetadataProvider" ||
                implementation.interfaceName == "Windows.UI.Xaml.Markup.IXamlMetadataProvider"
        } || type.name == "XamlMetaDataProvider" ||
            type.name == "XamlMetadataProvider" ||
            type.qualifiedName.endsWith(".XamlTypeInfo.XamlMetaDataProvider") ||
            type.qualifiedName.endsWith("_XamlTypeInfo.XamlMetaDataProvider")
    }

    private fun stageGeneratedComponentRegistrations(outputRoot: Path) {
        Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".winmd", ignoreCase = true) }
                .mapNotNull { winmd -> generatedRegistrationForWinmd(outputRoot, winmd) }
                .forEach { registration ->
                    val output = outputRoot
                        .resolve("registrations")
                        .resolve("generated")
                        .resolve(registration.dllFileName.removeSuffix(".dll"))
                        .resolve("LiftedWinRTClassRegistrations.xml")
                    Files.createDirectories(output.parent)
                    Files.writeString(output, renderLiftedRegistration(registration))
                }
        }
    }

    private fun generatedRegistrationForWinmd(outputRoot: Path, winmd: Path): ComponentRegistration? {
        val dllFileName = "${winmd.name.substringBeforeLast('.')}.dll"
        if (!outputRoot.resolve(dllFileName).isRegularFile()) {
            return null
        }
        val classes = runCatching {
            WinRTMetadataLoader.load(winmd)
                .namespaces
                .flatMap { namespace -> namespace.types }
                .filter { type -> type.kind == WinRTTypeKind.RuntimeClass }
                .map(WinRTTypeDefinition::qualifiedName)
                .filterNot { className -> className.startsWith("Microsoft.") || className.startsWith("Windows.") }
                .distinct()
                .sorted()
        }.getOrElse { error ->
            logger.warn("Skipping WinRT activatable class registration discovery for ${winmd.fileName}: ${error.message}")
            emptyList()
        }
        return classes.takeIf(List<String>::isNotEmpty)?.let { ComponentRegistration(dllFileName, it) }
    }

    private fun renderLiftedRegistration(registration: ComponentRegistration): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<Registrations xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">""")
            appendLine("  <Extension Category=\"windows.activatableClass.inProcessServer\">")
            appendLine("    <InProcessServer>")
            appendLine("      <Path>${escapeXml(registration.dllFileName)}</Path>")
            registration.classNames.forEach { className ->
                appendLine("""      <ActivatableClass ActivatableClassId="${escapeXml(className)}" ThreadingModel="both" />""")
            }
            appendLine("    </InProcessServer>")
            appendLine("  </Extension>")
            appendLine("</Registrations>")
        }

    private data class ComponentRegistration(
        val dllFileName: String,
        val classNames: List<String>,
    )

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun generateProjectPri(outputRoot: Path) {
        if (!generateProjectPri.get() || !isWindowsHost()) {
            return
        }
        val inputPris = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".pri", ignoreCase = true) }
                .filterNot { it.name.equals("resources.pri", ignoreCase = true) }
                .filterNot { it.name.startsWith("resources.language-", ignoreCase = true) }
                .sorted()
                .toList()
        }
        val makePri = discoverMakePriExecutable() ?: run {
            logger.warn("Skipping application PRI generation because makepri.exe was not found.")
            return
        }
        val projectPriRoot = temporaryDir.toPath().resolve("project-pri")
        GradleFileOperations.cleanDirectory(projectPriRoot)
        Files.createDirectories(projectPriRoot)
        val copiedProjectPriItems = ProjectPriInputStager(
            projectPriRoot = projectPriRoot,
            projectPriInitialPath = projectPriInitialPath.get(),
            defaultProjectResourceRoot = defaultProjectPriResourceRoot.orNull?.asFile?.toPath(),
            targetPaths = projectPriTargetPaths.get(),
            excludedFromBuildPaths = projectPriExcludedFromBuildPaths.get(),
        ).stage(
            componentPriFiles = inputPris,
            componentPriBaseRoot = outputRoot,
            explicitResourceFiles = projectPriResourceFiles.files.map { it.toPath() },
            explicitLayoutFiles = projectPriLayoutFiles.files.map { it.toPath() },
            explicitContentFiles = projectPriContentFiles.files.map { it.toPath() },
            explicitEmbedFiles = projectPriEmbedFiles.files.map { it.toPath() },
            defaultResourceFiles = defaultProjectPriResourceFiles.files.map { it.toPath() },
            defaultLayoutFiles = defaultProjectPriLayoutFiles.files.map { it.toPath() },
            defaultContentFiles = defaultProjectPriContentFiles.files.map { it.toPath() },
            includeDefaultProjectResources = enableDefaultProjectPriResources.get(),
        )
        if (copiedProjectPriItems.isEmpty()) {
            return
        }
        ApplicationPackagePayloadWriter.copyPackagePayloads(projectPriRoot, outputRoot, copiedProjectPriItems)
        val configRoot = temporaryDir.toPath().resolve("project-pri-config")
        ProjectPriGenerator.generateApplicationPri(
            makePri,
            outputRoot,
            projectPriRoot,
            configRoot,
            projectPriIndexName(),
            ProjectPriManifestSupport.fullIndexDefaultQualifiers(
                ProjectPriManifestSupport.defaultLanguage(projectPriDefaultLanguage.get(), appxManifestFiles.files),
                projectPriDefaultQualifiers.get(),
            ),
            copiedProjectPriItems,
            logger,
        )
    }

    private fun projectPriIndexName(): String =
        ProjectPriManifestSupport.indexName(projectPriIndexName.get(), projectPriFallbackIndexName.get(), appxManifestFiles.files)

    private fun discoverMakePriExecutable(): Path? {
        return ProjectPriToolResolver.makePriExecutable(makePriExecutable.get(), windowsSdkVersion.get(), runtimeIdentifier.get())
    }

    private fun stageAuthoringHostRuntimeConfigs(
        sources: Collection<java.io.File>,
        outputRoot: Path,
    ) {
        sources
            .mapNotNull { source -> readAuthoringHostRuntimeConfig(source) }
            .groupBy { it.assemblyName }
            .forEach { (assemblyName, configs) ->
                val activatableClasses = configs
                    .flatMap { it.activatableClasses.entries }
                    .associate { it.key to it.value }
                if (activatableClasses.isNotEmpty()) {
                    writeAuthoringHostRuntimeConfig(
                        output = outputRoot.resolve("$assemblyName.runtimeconfig.json"),
                        activatableClasses = activatableClasses,
                    )
                }
            }
    }

    private fun writeDependencyAuthoredHostManifestRecords(
        records: List<AuthoredHostManifestRecord>,
        outputRoot: Path,
    ): List<Path> =
        records
            .distinctBy { record -> record.assemblyName.lowercase() to record.targetArtifact.lowercase() }
            .groupBy { record -> record.assemblyName.lowercase() }
            .flatMap { (_, assemblyRecords) ->
                val useTargetSuffix = assemblyRecords.size > 1
                assemblyRecords.map { record ->
                    val stem = if (useTargetSuffix) {
                        "${record.assemblyName}-${record.targetArtifact.toGeneratedFileStem()}"
                    } else {
                        record.assemblyName
                    }
                    val output = outputRoot.resolve("$stem.host.json")
                    Files.writeString(output, record.toHostManifestJson())
                    output
                }
            }

    private fun readAuthoringHostRuntimeConfig(source: java.io.File): AuthoringHostRuntimeConfig? {
        val content = source.takeIf { it.isFile }?.readText().orEmpty()
        val assemblyName = readJsonString(content, "assemblyName")
            ?: throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' is missing assemblyName.")
        if (assemblyName.isBlank()) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' has blank assemblyName.")
        }
        val targetArtifact = readJsonString(content, "targetArtifact").orEmpty()
        val explicitTargets = readJsonStringMap(content, "activatableClassTargets")
        val defaultTargets = readJsonStringArrayField(content, "activatableClasses")
            .filter { it.isNotBlank() && targetArtifact.isNotBlank() }
            .associateWith { targetArtifact }
        val declaredTargets = defaultTargets + explicitTargets
        if (declaredTargets.isEmpty()) {
            throw IllegalArgumentException("Kotlin/WinRT authoring host manifest '${source.absolutePath}' does not declare any activatable class targets.")
        }
        val activatableClasses = declaredTargets
            .filterValues { target -> target.endsWith(".jar", ignoreCase = true) }
        if (activatableClasses.isEmpty()) {
            return null
        }
        return AuthoringHostRuntimeConfig(
            assemblyName = assemblyName,
            activatableClasses = activatableClasses,
        )
    }

    private fun writeAuthoringHostRuntimeConfig(
        output: Path,
        activatableClasses: Map<String, String>,
    ) {
        Files.createDirectories(output.parent)
        Files.writeString(
            output,
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"model\": \"jvm-authoring-host-runtime-config\",")
                appendLine("  \"activatableClasses\": ${activatableClasses.toJsonObject()}")
                appendLine("}")
            },
        )
    }

}

private data class AuthoringHostRuntimeConfig(
    val assemblyName: String,
    val activatableClasses: Map<String, String>,
)

private fun WinRTNuGetPackageIdentity.isWindowsAppSdkRootPackage(): Boolean =
    normalizedPackageId.equals("Microsoft.WindowsAppSDK", ignoreCase = true)

internal fun isWindowsHost(): Boolean =
    System.getProperty("os.name").contains("Windows", ignoreCase = true)

internal fun currentWindowsRuntimeIdentifier(): String {
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        "aarch64" in arch || "arm64" in arch -> "win-arm64"
        "x86" in arch && "64" !in arch -> "win-x86"
        else -> "win-x64"
    }
}

internal fun parseNuGetPackageIdentity(spec: String): WinRTNuGetPackageIdentity {
    val separator = spec.lastIndexOf('@')
    require(separator > 0 && separator < spec.lastIndex) {
        "NuGet package must use '<id>@<version>' format: $spec"
    }
    return WinRTNuGetPackageIdentity(
        packageId = spec.substring(0, separator),
        version = spec.substring(separator + 1),
    )
}

internal fun readNuGetPackages(identityFile: java.io.File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""nugetPackages"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

internal fun readDependencyRuntimeAssetRecords(identityFile: java.io.File): List<RuntimeAssetRecord> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val arrayContent = readIdentityJsonArrayContent(content, "runtimeAssetRecords") ?: return emptyList()
    return readIdentityJsonObjectArray(arrayContent)
        .mapNotNull(::readDependencyRuntimeAssetRecord)
}

private fun readDependencyRuntimeAssetRecord(content: String): RuntimeAssetRecord? {
    val fileName = readPortableIdentityJsonStringField(content, "fileName")?.takeIf(String::isNotBlank) ?: return null
    val contentBase64 = readPortableIdentityJsonStringField(content, "contentBase64")?.takeIf(String::isNotBlank) ?: return null
    return RuntimeAssetRecord(fileName = fileName, contentBase64 = contentBase64)
}

internal fun writeDependencyRuntimeAssetRecords(
    records: List<RuntimeAssetRecord>,
    outputRoot: Path,
): List<Path> =
    records
        .distinctBy { record -> record.fileName.lowercase() }
        .map { record ->
            val fileName = requirePortableDependencyFileName(record.fileName, "dependency runtime asset")
            val output = outputRoot.resolve(fileName)
            Files.createDirectories(output.parent)
            Files.write(output, Base64.getDecoder().decode(record.contentBase64))
            output
        }

internal fun readDependencyAuthoredMetadataRecords(identityFile: java.io.File): List<AuthoredMetadataRecord> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val arrayContent = readIdentityJsonArrayContent(content, "authoredMetadataRecords") ?: return emptyList()
    return readIdentityJsonObjectArray(arrayContent)
        .mapNotNull(::readDependencyAuthoredMetadataRecord)
}

private fun readDependencyAuthoredMetadataRecord(content: String): AuthoredMetadataRecord? {
    val fileName = readPortableIdentityJsonStringField(content, "fileName")?.takeIf(String::isNotBlank) ?: return null
    val contentBase64 = readPortableIdentityJsonStringField(content, "contentBase64")?.takeIf(String::isNotBlank) ?: return null
    return AuthoredMetadataRecord(fileName = fileName, contentBase64 = contentBase64)
}

internal fun writeDependencyAuthoredMetadataRecords(
    records: List<AuthoredMetadataRecord>,
    outputRoot: Path,
): List<Path> =
    records
        .distinctBy { record -> record.fileName.lowercase() }
        .map { record ->
            val fileName = requirePortableDependencyFileName(record.fileName, "dependency-authored metadata")
            val output = outputRoot.resolve(fileName)
            Files.createDirectories(output.parent)
            Files.write(output, Base64.getDecoder().decode(record.contentBase64))
            output
        }

internal fun readAuthoredHostManifestRecords(identityFile: java.io.File): List<AuthoredHostManifestRecord> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val arrayContent = readIdentityJsonArrayContent(content, "authoredHostManifestRecords") ?: return emptyList()
    return readIdentityJsonObjectArray(arrayContent)
        .mapNotNull(::readAuthoredHostManifestRecord)
}

private fun AuthoredHostManifestRecord.toHostManifestJson(): String =
    buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"model\": \"dependency-authoring-host\",")
        appendLine("  \"assemblyName\": ${assemblyName.toJsonString()},")
        hostExportsClass?.let { value ->
            appendLine("  \"hostExportsClass\": ${value.toJsonString()},")
        }
        appendLine("  \"targetArtifact\": ${targetArtifact.toJsonString()},")
        appendLine("  \"activatableClasses\": ${activatableClasses.toJsonArray()},")
        appendLine("  \"activatableClassTargets\": ${activatableClassTargets.toJsonObject()}")
        appendLine("}")
    }

private fun String.toGeneratedFileStem(): String =
    map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")
        .trim('_')
        .ifBlank { "authoring_host" }

internal fun requirePortableDependencyFileName(fileName: String, description: String): String {
    require(fileName.isNotBlank()) {
        "Kotlin/WinRT identity $description record has a blank fileName."
    }
    require(Path.of(fileName).fileName.toString() == fileName && fileName != "." && fileName != "..") {
        "Kotlin/WinRT identity $description record must use a portable file name, but was '$fileName'."
    }
    return fileName
}

internal fun readAuthoredHostManifestActivatableClasses(manifest: java.io.File): List<String> {
    val content = manifest.takeIf { it.isFile }?.readText().orEmpty()
    return readJsonStringArrayField(content, "activatableClasses") +
        readJsonStringMap(content, "activatableClassTargets").keys
}

internal fun authoredHostManifestDeclaresActivatableClasses(manifest: java.io.File): Boolean {
    return readAuthoredHostManifestActivatableClasses(manifest).any { it.isNotBlank() }
}

internal fun readDependencyAuthoredTargetArtifactRecords(identityFile: java.io.File): List<AuthoredTargetArtifactRecord> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val arrayContent = readIdentityJsonArrayContent(content, "authoredTargetArtifactRecords") ?: return emptyList()
    return readIdentityJsonObjectArray(arrayContent)
        .mapNotNull(::readDependencyAuthoredTargetArtifactRecord)
}

private fun readDependencyAuthoredTargetArtifactRecord(content: String): AuthoredTargetArtifactRecord? {
    val fileName = readPortableIdentityJsonStringField(content, "fileName")?.takeIf(String::isNotBlank) ?: return null
    val contentBase64 = readPortableIdentityJsonStringField(content, "contentBase64")?.takeIf(String::isNotBlank) ?: return null
    return AuthoredTargetArtifactRecord(fileName = fileName, contentBase64 = contentBase64)
}

internal fun writeDependencyAuthoredTargetArtifactRecords(
    records: List<AuthoredTargetArtifactRecord>,
    outputRoot: Path,
): List<Path> =
    records
        .distinctBy { record -> record.fileName.lowercase() }
        .map { record ->
            val fileName = requirePortableDependencyFileName(record.fileName, "dependency-authored target artifact")
            val output = outputRoot.resolve(fileName)
            Files.createDirectories(output.parent)
            Files.write(output, Base64.getDecoder().decode(record.contentBase64))
            output
        }

private fun readJsonString(content: String, name: String): String? =
    findJsonFieldValueStart(content, name)
        ?.takeIf { index -> index < content.length && content[index] == '"' }
        ?.let { index -> readJsonQuotedString(content, index) }

private fun readJsonStringMap(content: String, name: String): Map<String, String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyMap()
    return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .associate { it.groupValues[1].decodeJsonString() to it.groupValues[2].decodeJsonString() }
}

internal fun readPortableIdentityJsonStringField(content: String, name: String): String? =
    readJsonString(content, name)

internal fun readIdentityJsonObjectArray(content: String): List<String> =
    readJsonObjectArray(content)

private fun readJsonObjectArray(content: String): List<String> {
    val objects = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaping = false
    content.forEachIndexed { index, char ->
        when {
            escaping -> escaping = false
            inString && char == '\\' -> escaping = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '{' -> {
                if (depth == 0) {
                    start = index
                }
                depth++
            }
            char == '}' -> {
                depth--
                if (depth == 0 && start >= 0) {
                    objects += content.substring(start, index + 1)
                    start = -1
                }
            }
        }
    }
    return objects
}

internal fun readIdentityJsonArrayContent(content: String, name: String): String? =
    readJsonArrayContent(content, name)

private fun readJsonArrayContent(content: String, name: String): String? {
    val field = Regex(""""${Regex.escape(name)}"\s*:""").find(content) ?: return null
    var index = field.range.last + 1
    while (index < content.length && content[index].isWhitespace()) {
        index++
    }
    if (index >= content.length || content[index] != '[') {
        return null
    }
    val start = index + 1
    var depth = 0
    var inString = false
    var escaping = false
    while (index < content.length) {
        val char = content[index]
        when {
            escaping -> escaping = false
            inString && char == '\\' -> escaping = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '[' -> depth++
            char == ']' -> {
                depth--
                if (depth == 0) {
                    return content.substring(start, index)
                }
            }
        }
        index++
    }
    return null
}

internal fun readJsonStringArrayField(content: String, name: String): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return readJsonStringArray(match.groupValues[1])
}

private fun readJsonStringArray(content: String): List<String> =
    buildList {
        var index = 0
        while (index < content.length) {
            if (content[index] == '"') {
                val end = findJsonStringEnd(content, index)
                add(content.substring(index + 1, end).decodeJsonString())
                index = end + 1
            } else {
                index++
            }
        }
    }

private fun findJsonFieldValueStart(content: String, name: String): Int? {
    var index = 0
    while (index < content.length) {
        if (content[index] != '"') {
            index++
            continue
        }
        val keyEnd = findJsonStringEnd(content, index)
        val key = content.substring(index + 1, keyEnd).decodeJsonString()
        index = keyEnd + 1
        while (index < content.length && content[index].isWhitespace()) {
            index++
        }
        if (index >= content.length || content[index] != ':') {
            continue
        }
        index++
        while (index < content.length && content[index].isWhitespace()) {
            index++
        }
        if (key == name) {
            return index
        }
    }
    return null
}

private fun readJsonQuotedString(content: String, startQuote: Int): String =
    content.substring(startQuote + 1, findJsonStringEnd(content, startQuote)).decodeJsonString()

private fun findJsonStringEnd(content: String, startQuote: Int): Int {
    var index = startQuote + 1
    var escaping = false
    while (index < content.length) {
        val char = content[index]
        when {
            escaping -> escaping = false
            char == '\\' -> escaping = true
            char == '"' -> return index
        }
        index++
    }
    throw IllegalArgumentException("Malformed Kotlin/WinRT identity JSON string.")
}

private fun String.decodeJsonString(): String =
    buildString {
        var index = 0
        while (index < this@decodeJsonString.length) {
            val char = this@decodeJsonString[index++]
            if (char != '\\' || index >= this@decodeJsonString.length) {
                append(char)
                continue
            }
            when (val escaped = this@decodeJsonString[index++]) {
                '\\' -> append('\\')
                '"' -> append('"')
                'b' -> append('\b')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> {
                    append('\\')
                    append(escaped)
                }
            }
        }
    }

private fun Map<String, String>.toJsonObject(): String =
    entries
        .toList()
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${key.toJsonString()}: ${value.toJsonString()}"
        }
