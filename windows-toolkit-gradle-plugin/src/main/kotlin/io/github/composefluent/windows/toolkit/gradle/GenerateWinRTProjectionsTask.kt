package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.projections.generator.referenceDeclarations

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringMetadataModel
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndexRows as parseAuthoringMetadataIndexRows
import io.github.composefluent.winrt.compiler.authoring.writeAuthoringMetadataIndex
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.xamlComponentResourceDictionaryTypes
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTMetadataCache
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceKind
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceResolver
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.projections.generator.redirectedWinUIXamlProjectionSurfaceTypeReferences
import io.github.composefluent.winrt.projections.generator.winRTAuthoringHostExportsClassName
import org.gradle.api.model.ObjectFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.CodeSource
import java.util.Base64
import javax.inject.Inject
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

private const val PREPARED_METADATA_MANIFEST_HEADER = "kotlin-winrt-prepared-metadata-v1"
private const val PREPARED_METADATA_MANIFEST_RELATIVE_PATH = "kotlin-winrt-metadata/resolved-sources.tsv"

@CacheableTask
abstract class GenerateWinRTProjectionsTask : DefaultTask() {
    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Input
    abstract val xamlFramework: Property<io.github.composefluent.winrt.runtime.XamlFramework>

    init {
        xamlFramework.convention(io.github.composefluent.winrt.runtime.XamlFramework.WinUI)
        windowsSdkDeclared.convention(false)
    }

    init {
        additionalAuthoringTargetArtifactNames.convention(emptyList())
        emitProjectionSources.convention(true)
        prepareMetadataOnly.convention(false)
        generateSdkPreviewSources.convention(false)
        referenceProjection.convention(false)
        workerEnvironment.convention(emptyMap())
    }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val generateSdkPreviewSources: Property<Boolean>

    @get:Input
    abstract val referenceProjection: Property<Boolean>

    @get:OutputDirectory
    abstract val authoringTypeDetailsOutputDirectory: DirectoryProperty

    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val xamlRegistrars: RegularFileProperty

    @get:Internal
    abstract val legacyOutputDirectories: ConfigurableFileCollection

    @get:Input
    abstract val emitJvmAuthoringHostExports: Property<Boolean>

    @get:Input
    abstract val emitProjectionSources: Property<Boolean>

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataInputFiles: ConfigurableFileCollection

    /** Tracks the SDK metadata selected by dynamic registry/environment discovery. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val windowsSdkMetadataFiles: FileCollection
        get() = objects.fileCollection().from(winAppRestoreLockFiles.elements.map {
            windowsSdkMetadataInputFiles(
                windowsSdkDeclared = windowsSdkDeclared.get(),
                metadataInputs = metadataInputs.get(),
                registryRoots = windowsSdkRegistryRoots.get(),
                customSdkRoot = windowsSdkRootEnvironment.orNull,
            )
        })

    @get:Input
    @get:Optional
    abstract val windowsSdkRootEnvironment: Property<String>

    /** Legacy DSL property; authoring source roots are owned by the scanner task. */
    @get:Internal
    abstract val sourceRoots: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoringCandidatesFile: RegularFileProperty

    /**
     * Resolved metadata produced by prepareWinRTProjectionMetadata. Keeping this as a
     * separate input prevents SDK/NuGet discovery from being repeated by every consumer
     * task and makes the preparation boundary visible to Gradle.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val preparedMetadataManifest: RegularFileProperty

    @get:Input
    abstract val prepareMetadataOnly: Property<Boolean>

    /** Shared acceleration state, not a semantic input or a task-owned output. */
    @get:Internal
    abstract val metadataModelCacheDirectory: DirectoryProperty

    /** Shared source cache, populated only by the generation task. */
    @get:Internal
    abstract val projectionSourceCacheDirectory: DirectoryProperty

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

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val winAppRestoreLockFiles: ConfigurableFileCollection

    @get:Input
    abstract val windowsSdkDeclared: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val windowsSdkRegistryRoots: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val generateWindowsSdkProjection: Property<Boolean>

    /** Explicit worker environment overrides; ambient environment is read only at execution. */
    @get:Internal
    abstract val workerEnvironment: MapProperty<String, String>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:org.gradle.api.tasks.IgnoreEmptyDirectories
    val restoredNuGetPackageContentFiles: FileCollection
        get() = objects.fileCollection().from(
            nugetPackageContentFiles,
            winAppRestoreLockFiles.elements.map { locks ->
                existingWinAppPackageContentRoots(locks.map { it.asFile }).map { root ->
                    objects.fileTree().setDir(root).apply { include("**/*.winmd", "**/*.WinMD", "**/*.nuspec") }
                }
            },
        )

    /** Additional explicit package inputs; restored roots are discovered when inputs are snapshotted. */
    @get:Internal
    abstract val nugetPackageContentFiles: ConfigurableFileCollection

    @get:Input
    abstract val projectModel: Property<String>

    @get:Input
    abstract val authoringAssemblyName: Property<String>

    @get:Input
    abstract val authoringTargetArtifactName: Property<String>

    @get:Input
    abstract val additionalAuthoringTargetArtifactNames: ListProperty<String>

    @get:Classpath
    abstract val authoringScannerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val authoringScannerJvmArgs: ListProperty<String>

    @get:Classpath
    abstract val generatorWorkerClasspath: ConfigurableFileCollection

    @get:Input
    abstract val generatorWorkerJvmArgs: ListProperty<String>

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun generate() {
        workerExecutor.processIsolation { spec ->
            spec.classpath.from(generatorWorkerClasspath)
            // Capturing the ambient environment during configuration invalidates the cache
            // for unrelated launcher/session variables. Semantic SDK inputs are modeled separately.
            spec.forkOptions.environment(System.getenv() + workerEnvironment.get())
            spec.forkOptions.jvmArgs(generatorWorkerJvmArgs.get())
        }.submit(GenerateWinRTProjectionsWorkAction::class.java) { parameters ->
            parameters.xamlFramework.set(xamlFramework)
            parameters.outputDirectory.set(outputDirectory)
            parameters.authoringTypeDetailsOutputDirectory.set(authoringTypeDetailsOutputDirectory)
            parameters.xamlRegistrars.set(xamlRegistrars)
            parameters.legacyOutputDirectories.from(legacyOutputDirectories)
            parameters.metadataInputs.set(metadataInputs)
            parameters.metadataInputFiles.from(metadataInputFiles)
            parameters.sourceRoots.from(sourceRoots)
            parameters.authoringCandidatesFile.set(authoringCandidatesFile)
            parameters.preparedMetadataManifest.set(preparedMetadataManifest)
            parameters.prepareMetadataOnly.set(prepareMetadataOnly)
            parameters.generateSdkPreviewSources.set(generateSdkPreviewSources)
            parameters.referenceProjection.set(referenceProjection)
            parameters.metadataModelCacheDirectory.set(metadataModelCacheDirectory)
            parameters.projectionSourceCacheDirectory.set(projectionSourceCacheDirectory)
            parameters.includeNamespaces.set(includeNamespaces)
            parameters.includeTypes.set(includeTypes)
            parameters.excludeNamespaces.set(excludeNamespaces)
            parameters.excludeTypes.set(excludeTypes)
            parameters.additionExcludeNamespaces.set(additionExcludeNamespaces)
            parameters.dependencyIdentityFiles.from(dependencyIdentityFiles)
            parameters.winAppRestoreLockFiles.from(winAppRestoreLockFiles)
            parameters.windowsSdkDeclared.set(windowsSdkDeclared)
            parameters.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
            parameters.windowsSdkVersion.set(windowsSdkVersion)
            parameters.includeWindowsSdkExtensions.set(includeWindowsSdkExtensions)
            parameters.generateWindowsSdkProjection.set(generateWindowsSdkProjection)
            parameters.nugetPackages.set(nugetPackages)
            parameters.projectModel.set(projectModel)
            parameters.authoringAssemblyName.set(authoringAssemblyName)
            parameters.authoringTargetArtifactName.set(authoringTargetArtifactName)
            parameters.additionalAuthoringTargetArtifactNames.set(additionalAuthoringTargetArtifactNames)
            parameters.emitJvmAuthoringHostExports.set(emitJvmAuthoringHostExports)
            parameters.emitProjectionSources.set(emitProjectionSources)
            parameters.authoringScannerClasspath.from(authoringScannerClasspath)
            parameters.authoringScannerJvmArgs.set(authoringScannerJvmArgs)
            parameters.workDirectory.set(temporaryDir)
        }
    }
}

internal interface GenerateWinRTProjectionsWorkParameters : WorkParameters {
    val xamlFramework: Property<io.github.composefluent.winrt.runtime.XamlFramework>
    val outputDirectory: DirectoryProperty
    val authoringTypeDetailsOutputDirectory: DirectoryProperty
    val xamlRegistrars: RegularFileProperty
    val legacyOutputDirectories: ConfigurableFileCollection
    val emitJvmAuthoringHostExports: Property<Boolean>
    val emitProjectionSources: Property<Boolean>
    val metadataInputs: ListProperty<String>
    val metadataInputFiles: ConfigurableFileCollection
    val sourceRoots: ConfigurableFileCollection
    val authoringCandidatesFile: RegularFileProperty
    val preparedMetadataManifest: RegularFileProperty
    val prepareMetadataOnly: Property<Boolean>
    val generateSdkPreviewSources: Property<Boolean>
    val referenceProjection: Property<Boolean>
    val metadataModelCacheDirectory: DirectoryProperty
    val projectionSourceCacheDirectory: DirectoryProperty
    val includeNamespaces: ListProperty<String>
    val includeTypes: ListProperty<String>
    val excludeNamespaces: ListProperty<String>
    val excludeTypes: ListProperty<String>
    val additionExcludeNamespaces: ListProperty<String>
    val dependencyIdentityFiles: ConfigurableFileCollection
    val winAppRestoreLockFiles: ConfigurableFileCollection
    val windowsSdkDeclared: Property<Boolean>
    val windowsSdkRegistryRoots: ListProperty<String>
    val windowsSdkVersion: Property<String>
    val includeWindowsSdkExtensions: Property<Boolean>
    val generateWindowsSdkProjection: Property<Boolean>
    val nugetPackages: ListProperty<String>
    val projectModel: Property<String>
    val authoringAssemblyName: Property<String>
    val authoringTargetArtifactName: Property<String>
    val additionalAuthoringTargetArtifactNames: ListProperty<String>
    val authoringScannerClasspath: ConfigurableFileCollection
    val authoringScannerJvmArgs: ListProperty<String>
    val workDirectory: DirectoryProperty
}

internal abstract class GenerateWinRTProjectionsWorkAction : WorkAction<GenerateWinRTProjectionsWorkParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    private val logger = Logging.getLogger(GenerateWinRTProjectionsWorkAction::class.java)

    override fun execute() {
        logGeneratorRuntimeClasspath()
        val generatedRoot = parameters.outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val authoringTypeDetailsRoot = parameters.authoringTypeDetailsOutputDirectory.get().asFile.toPath()
            .toAbsolutePath()
            .normalize()
        parameters.legacyOutputDirectories.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot { legacyRoot -> legacyRoot == generatedRoot || legacyRoot == authoringTypeDetailsRoot }
            .forEach(GradleFileOperations::deleteDirectory)
        // KotlinProjectionGenerator.writeFiles preserves unchanged generated files and
        // removes only stale files that it owns. Clearing this directory here would
        // defeat Gradle's incremental source snapshots and the generator's write-if-changed path.
        if (!parameters.emitProjectionSources.get()) {
            clearPreparedStaticSources(generatedRoot)
            Files.deleteIfExists(generatedRoot.resolve("kotlin-winrt-support/projection-input-contract.tsv"))
        }
        val metadataCache = metadataCache()
        // The resolved files carry the metadata; the declared SDK source carries the fact that
        // this projection has a Windows SDK and its UniversalApiContract, which select the
        // SDK source additions (WinRT.Interop and the COM interop helpers), as in CsWinRT input preparation. The resolver de-duplicates the files.
        val sources = metadataCache.files.map(WinRTMetadataSource::path) + declaredWindowsSdkSources()
        val effectiveExcludeTypes = parameters.excludeTypes.get()
        val unfilteredModel = metadataCache.load(parameters.metadataModelCacheDirectory.get().asFile.toPath())
        val effectiveIncludeTypes = parameters.includeTypes.get() +
            automaticXamlComponentResourceDictionaryTypes(unfilteredModel, parameters.includeTypes.get().toSet())
        validateDependencyProjectionIdentityOwnership(parameters.dependencyIdentityFiles.files)
        val dependencyProjectionSurfaceTypes = dependencyProjectionSurfaceTypeNames(parameters.dependencyIdentityFiles.files)
        val selection = io.github.composefluent.winrt.metadata.WinRTProjectionSurfaceFilter(
            namespaces = parameters.includeNamespaces.get().toSet(),
            types = parameters.includeTypes.get().toSet(),
            excludedNamespaces = parameters.excludeNamespaces.get().toSet(),
            excludedTypes = effectiveExcludeTypes.toSet(),
        ).withAdditionalTypeRoots(effectiveIncludeTypes + dependencyProjectionSurfaceTypes)
        val applicationPackagingOnly = parameters.projectModel.get() == "application" &&
            parameters.metadataInputs.get().isEmpty() &&
            parameters.includeNamespaces.get().isEmpty() &&
            parameters.includeTypes.get().isEmpty() &&
            !parameters.generateWindowsSdkProjection.get()
        val authoringMetadataBaseModel = unfilteredModel.filterProjectionSurface(
            filter = selection,
            additionalTypeReferences = ::redirectedWinUIXamlProjectionSurfaceTypeReferences,
        )
        val authoringMetadataIndex = generatedRoot.resolve("kotlin-winrt-authoring/metadata-index.tsv")
        Files.createDirectories(authoringMetadataIndex.parent)
        writeAuthoringMetadataIndex(
            mergedAuthoringMetadataIndexTypes(authoringMetadataBaseModel, parameters.dependencyIdentityFiles.files),
            authoringMetadataIndex,
        )
        if (parameters.prepareMetadataOnly.get()) {
            return
        }
        val authoringCandidates = parameters.authoringCandidatesFile.orNull
            ?.asFile
            ?.toPath()
            ?.takeIf(Files::isRegularFile)
            ?.let(KotlinWinRTAuthoringCandidateFile::read)
            ?: run {
                // Normal builds consume the scanner's semantic output. Only standalone
                // invocations without that output need to discover source files again.
                val authoringSourceRoots = parameters.sourceRoots.files
                    .map { it.toPath().toAbsolutePath().normalize() }
                    .filterNot { it.startsWith(generatedRoot) || isKotlinWindowsToolkitPluginOwnedAuthoringSourceRoot(it) }
                    .filter(::containsKotlinSource)
                if (authoringSourceRoots.isEmpty()) return@run emptyList()
                val scannerWorkDirectory = parameters.workDirectory.get().asFile.toPath().resolve("authoring-scanner")
                Files.createDirectories(scannerWorkDirectory)
                val candidatesFile = scannerWorkDirectory.resolve("candidates.tsv")
                runAuthoringScanner(
                    sourceRoots = authoringSourceRoots,
                    metadataIndex = authoringMetadataIndex,
                    candidatesFile = candidatesFile,
                )
                KotlinWinRTAuthoringCandidateFile.read(candidatesFile)
            }
        KotlinWinRTAuthoringCandidateFile.write(
            generatedRoot.resolve("kotlin-winrt-authoring/authored-candidates.tsv"),
            authoringCandidates,
        )
        val dependencySourceAdditionTypeNames = dependencySourceAdditionTypeNames(parameters.dependencyIdentityFiles.files)
        val exportedAuthoringCandidates = authoringCandidates.filter(KotlinWinRTAuthoredTypeCandidate::isPublic)
        val authoringCandidateMetadataRoots = authoringCandidateMetadataRootNames(authoringCandidates)
        val authoringSelection = selection.withAdditionalTypeRoots(authoringCandidateMetadataRoots)
        val authoringDetailsBaseModel = unfilteredModel.filterProjectionSurface(
            filter = authoringSelection,
            additionalTypeReferences = ::redirectedWinUIXamlProjectionSurfaceTypeReferences,
        )
        // CsWinRT includes exported types and their metadata dependencies before emitting
        // either projection. Authoring roots must retain bindings as well as TypeDetails.
        val baseModel = if (applicationPackagingOnly) WinRTMetadataModel(emptyList()) else authoringDetailsBaseModel
        val dependencyProjectionTypeNames = dependencyProjectedTypeNames(baseModel, parameters.dependencyIdentityFiles.files)
        val model = KotlinWinRTAuthoringMetadataModel.mergeAuthoredRuntimeClasses(baseModel, exportedAuthoringCandidates)
        val authoringModel = KotlinWinRTAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            authoringDetailsBaseModel,
            exportedAuthoringCandidates,
        )
        KotlinWinRTAuthoringMetadataModel.writeDescriptor(
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/authored-metadata.tsv"),
        )
        KotlinWinRTAuthoringMetadataModel.writeWinmd(
            assemblyName = parameters.authoringAssemblyName.get(),
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}.winmd"),
        )
        KotlinWinRTAuthoringMetadataModel.writeHostManifest(
            assemblyName = parameters.authoringAssemblyName.get(),
            targetArtifactName = parameters.authoringTargetArtifactName.get(),
            hostExportsClassName = winRTAuthoringHostExportsClassName(parameters.authoringTargetArtifactName.get()).canonicalName,
            candidates = exportedAuthoringCandidates,
            outputFile = generatedRoot.resolve("kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}.host.json"),
        )
        parameters.additionalAuthoringTargetArtifactNames.get()
            .filter(String::isNotBlank)
            .filterNot { targetArtifactName -> targetArtifactName == parameters.authoringTargetArtifactName.get() }
            .distinct()
            .forEach { targetArtifactName ->
                KotlinWinRTAuthoringMetadataModel.writeHostManifest(
                    assemblyName = parameters.authoringAssemblyName.get(),
                    targetArtifactName = targetArtifactName,
                    hostExportsClassName = winRTAuthoringHostExportsClassName(targetArtifactName).canonicalName,
                    candidates = exportedAuthoringCandidates,
                    outputFile = generatedRoot.resolve(
                        "kotlin-winrt-authoring/${parameters.authoringAssemblyName.get()}-${targetArtifactName.toKotlinSupportIdentifierSuffix()}.host.json",
                    ),
                )
            }
        val projectionModel = if (parameters.projectModel.get() == "application") baseModel else model
        val authoredRuntimeClassNames = authoringCandidates
            .mapTo(mutableSetOf()) { candidate -> candidate.sourceTypeName }
            .filterTo(mutableSetOf(), String::isNotBlank)
        val projectionContext = WinRTMetadataProjectionContext(
            xamlFramework = parameters.xamlFramework.get(),
            sources = sources,
            include = authoringSelection.namespaces + authoringSelection.types,
            exclude = parameters.excludeNamespaces.get().toSet() + effectiveExcludeTypes.toSet(),
            excludedTypes = effectiveExcludeTypes.toSet(),
            additionExclude = parameters.additionExcludeNamespaces.get().toSet(),
            // Application projections use baseModel (imported WinMD), while
            // authored application types are emitted separately below. CsWinRT's
            // component mode describes the component's own metadata, not its
            // imported controls: enabling it here suppresses their RCW registrar.
            component = parameters.projectModel.get() != "application" && exportedAuthoringCandidates.isNotEmpty(),
            inaccessibleDependencyTypes = dependencyInternalProjectedTypeNames(baseModel, parameters.dependencyIdentityFiles.files),
        )
        if (parameters.emitProjectionSources.get()) {
            if (parameters.referenceProjection.get()) {
                io.github.composefluent.winrt.projections.generator.NativeProjectionRecipe.write(
                    generatedRoot.resolve("kotlin-winrt-native-recipe"),
                    projectionModel.copy(windowsSdkSelections = unfilteredModel.windowsSdkSelections), projectionContext,
                    dependencyProjectionTypeNames + authoredRuntimeClassNames, dependencySourceAdditionTypeNames,
                    authoredRuntimeClassNames, parameters.authoringTargetArtifactName.get(),
                )
            }
            val inputContract = io.github.composefluent.winrt.projections.generator.KotlinProjectionInputContract.create(
                projectionModel, unfilteredModel, projectionContext,
                dependencyProjectionTypeNames + authoredRuntimeClassNames, dependencySourceAdditionTypeNames,
                parameters.authoringTargetArtifactName.get(), parameters.emitJvmAuthoringHostExports.get(),
            )
            val generator = KotlinProjectionGenerator(
                referenceProjection = parameters.referenceProjection.get(),
                emitSupportFiles = true,
                groupProjectionFilesByPackageOnWrite = true,
                projectionContext = projectionContext,
                suppressedProjectionTypeNames = dependencyProjectionTypeNames + authoredRuntimeClassNames,
                suppressedSourceAdditionTypeNames = dependencySourceAdditionTypeNames,
                authoredRuntimeClassNames = authoredRuntimeClassNames,
                supportOwnerIdentity = parameters.authoringTargetArtifactName.get(),
                emitJvmAuthoringHostExports = parameters.emitJvmAuthoringHostExports.get(),
            )
            val store = parameters.projectionSourceCacheDirectory.orNull?.asFile?.toPath()
            if (store != null && authoringCandidates.isEmpty()) {
                val implementationKey = preparedProjectionKey(inputContract)
                val key = if (parameters.referenceProjection.get()) {
                    preparedProjectionImplementationKey(implementationKey, "reference-declarations-v1")
                } else implementationKey
                prepareProjectionSources(store, key, generatedRoot, projectionModel) { destination ->
                    generator.generateTo(projectionModel, destination)
                }
            } else {
                generator.generateTo(projectionModel, generatedRoot)
            }
            GradleFileOperations.writeStringIfChanged(
                generatedRoot.resolve("kotlin-winrt-support/projection-input-contract.tsv"), inputContract.descriptor,
            )
        }
        if (!parameters.emitProjectionSources.get() && exportedAuthoringCandidates.isNotEmpty()) {
            // CsWinRT AuthoringExportTypesGenerator owns component exports independently of
            // imported projections. Shared SDK owners must not suppress this component's host.
            // Metadata is already resolved; this pass must not select SDK source additions.
            KotlinProjectionGenerator(
                referenceProjection = parameters.referenceProjection.get(),
                emitSupportFiles = true,
                projectionContext = projectionContext.copy(sources = emptyList()),
                suppressedProjectionTypeNames = dependencyProjectionTypeNames + authoredRuntimeClassNames,
                authoredRuntimeClassNames = authoredRuntimeClassNames,
                supportOwnerIdentity = parameters.authoringTargetArtifactName.get(),
                emitJvmAuthoringHostExports = parameters.emitJvmAuthoringHostExports.get(),
            ).generateAuthoringSupportTo(projectionModel, generatedRoot.resolve("authoring-support"))
        } else if (!parameters.emitProjectionSources.get()) {
            GradleFileOperations.deleteDirectory(generatedRoot.resolve("authoring-support"))
        }
        writeAuthoringMetadataIndex(
            mergedAuthoringMetadataIndexTypes(authoringModel, parameters.dependencyIdentityFiles.files),
            authoringMetadataIndex,
        )
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = authoringCandidates,
            metadataModel = authoringModel,
            outputDirectory = authoringTypeDetailsRoot,
            assemblyName = parameters.authoringAssemblyName.get(),
            declarationTransform = { declaration ->
                if (parameters.referenceProjection.get()) declaration.referenceDeclarations() else declaration
            },
        )
        writeAuthoringTypeDetailsRegistrarSupport(
            generatedRoot = generatedRoot,
            assemblyName = parameters.authoringAssemblyName.get(),
            candidates = authoringCandidates,
        )
        if (parameters.generateSdkPreviewSources.get())
            io.github.composefluent.winrt.compiler.xaml.writeXamlSdkPreviewSources(generatedRoot, projectionModel)
    }

    private fun writeAuthoringTypeDetailsRegistrarSupport(
        generatedRoot: Path,
        assemblyName: String,
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
    ) {
        val supportRoot = generatedRoot.resolve("kotlin-winrt-support")
        Files.createDirectories(supportRoot)
        val registrarClassNames = candidates.map { it.sourceSetName }.distinct().ifEmpty { listOf(null) }.map {
            "io.github.composefluent.winrt.projections.support." + authoringTypeDetailsRegistrarName(assemblyName, it)
        }.sorted()
        GradleFileOperations.writeStringIfChanged(
            supportRoot.resolve("authoring-type-details-registrars.tsv"),
            (listOf("className") + registrarClassNames).joinToString("\n", postfix = "\n"),
        )
        val manifest = supportRoot.resolve("compiler-support.tsv")
        val existing = if (Files.isRegularFile(manifest)) {
            Files.readAllLines(manifest).filter(String::isNotBlank)
        } else {
            listOf("kind\tclassName\tsourceFile\tentries\towner")
        }
        val newRows = registrarClassNames.map { registrarClassName -> listOf(
            "authoring-type-details-registrar",
            registrarClassName,
            "authoring-type-details-registrars.tsv",
            registrarClassNames.size.toString(),
            "",
        ).joinToString("\t") }
        val rows = (existing.drop(1).filterNot { it.startsWith("authoring-type-details-registrar\t") } + newRows)
            .distinct()
            .sorted()
        val xamlNames = parameters.xamlRegistrars.orNull?.asFile?.readLines()?.drop(1)?.filter(String::isNotBlank).orEmpty()
        val xamlRows = xamlNames.map { listOf("xaml-type-registrar", it, "xaml-type-registrars.tsv", xamlNames.size.toString(), "").joinToString("\t") }
        if (xamlNames.isNotEmpty()) GradleFileOperations.writeStringIfChanged(supportRoot.resolve("xaml-type-registrars.tsv"),
            (listOf("className") + xamlNames).joinToString("\n", postfix = "\n"))
        GradleFileOperations.writeStringIfChanged(
            manifest,
            (listOf(existing.firstOrNull() ?: "kind\tclassName\tsourceFile\tentries\towner") + rows.filterNot { it.startsWith("xaml-type-registrar\t") } + xamlRows)
                .joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun String.toKotlinSupportIdentifierSuffix(): String =
        buildString {
            this@toKotlinSupportIdentifierSuffix.trim().forEach { char ->
                append(if (char.isLetterOrDigit()) char else '_')
            }
        }.trim('_')
            .replace(Regex("_+"), "_")
            .let { suffix ->
                if (suffix.firstOrNull()?.isDigit() == true) "_$suffix" else suffix
            }

    private fun declaredWindowsSdkSources(): List<WinRTMetadataSource> {
        if (!parameters.windowsSdkDeclared.get()) {
            return emptyList()
        }
        return listOf(
            WinRTMetadataSource.windowsSdk(
                version = parameters.windowsSdkVersion.orNull,
                includeExtensions = parameters.includeWindowsSdkExtensions.get(),
                registryRoots = parameters.windowsSdkRegistryRoots.orNull
                    ?.orNullIfEmpty()
                    ?.map(Path::of),
            ),
        )
    }

    private fun metadataSources(): List<WinRTMetadataSource> {
        val registryRoots = parameters.windowsSdkRegistryRoots.orNull
            ?.orNullIfEmpty()
            ?.map(Path::of)
        val explicitSources = parameters.metadataInputs.get()
            .map(WinRTMetadataSource::parse)
            .map { source -> source.withWindowsSdkRegistryRoots(registryRoots) }
        val packageSpecs = (parameters.nugetPackages.get() + parameters.dependencyIdentityFiles.files.flatMap(::readNuGetPackages))
            .distinct()
            .sorted()
        val sdkSource = declaredWindowsSdkSources()
        val dependencyRecords = parameters.dependencyIdentityFiles.files.flatMap(::readDependencyAuthoredMetadataRecords)
        val dependencyAuthoredMetadataSources = writeDependencyAuthoredMetadataRecords(
            records = dependencyRecords,
            outputRoot = parameters.workDirectory.get().asFile.toPath().resolve("dependency-authored-metadata"),
        )
            .map(WinRTMetadataSource::path)
        return resolveWinAppProjectionSources(explicitSources, packageSpecs, parameters.winAppRestoreLockFiles.files) +
            sdkSource + dependencyAuthoredMetadataSources
    }

    /**
     * Resolve external metadata once and hand the stable file list to downstream work. The
     * manifest is deliberately text based so it can be inspected in build scans and restored
     * by the build cache without retaining compiler objects or a mutable metadata model.
     */
    private fun metadataCache(): WinRTMetadataCache {
        parameters.preparedMetadataManifest.orNull
            ?.asFile
            ?.toPath()
            ?.takeIf(Files::isRegularFile)
            ?.let(::readPreparedMetadataCache)
            ?.let { return it }

        val cache = WinRTMetadataSourceResolver.resolve(metadataSources())
        // A standalone SDK generation has no metadata-preparation producer.
        // Preserve its resolved inputs too, for design-time consumers to use
        // exactly this SDK selection without resolving a second metadata graph.
        val manifest = parameters.outputDirectory.get().asFile.toPath()
            .resolve(PREPARED_METADATA_MANIFEST_RELATIVE_PATH)
        writePreparedMetadataCache(manifest, cache)
        return cache
    }

    private fun writePreparedMetadataCache(path: Path, cache: WinRTMetadataCache) {
        Files.createDirectories(path.parent)
        val lines = buildList {
            add(PREPARED_METADATA_MANIFEST_HEADER)
            cache.resolvedFiles
                .sortedBy { resolved -> resolved.file.toString().lowercase() }
                .forEach { resolved ->
                    add(
                        listOf(
                            "file",
                            resolved.sourceKind.name,
                            encodePreparedMetadataValue(resolved.sourceDescription),
                            encodePreparedMetadataValue(resolved.file.toAbsolutePath().normalize().toString()),
                        ).joinToString("\t"),
                    )
                }
            cache.windowsSdkSelections
                .sortedBy { selection -> selection.version }
                .forEach { selection ->
                    val contracts = selection.contracts
                        .sortedWith(compareBy({ it.name }, { it.version }))
                        .joinToString(";") { contract ->
                            "${encodePreparedMetadataValue(contract.name)}=${encodePreparedMetadataValue(contract.version)}"
                        }
                    add(
                        listOf("sdk", selection.version, contracts).joinToString("\t"),
                    )
                }
        }
        GradleFileOperations.writeStringIfChanged(path, lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun encodePreparedMetadataValue(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))


    private fun containsKotlinSource(root: Path): Boolean {
        if (Files.isRegularFile(root)) {
            return root.extension == "kt"
        }
        if (!Files.isDirectory(root)) {
            return false
        }
        return Files.walk(root).use { stream ->
            stream.asSequence().any { path -> Files.isRegularFile(path) && path.extension == "kt" }
        }
    }

    private fun logGeneratorRuntimeClasspath() {
        val kotlinPoetClass = ClassName::class.java
        val kotlinStdlibClass = Sequence::class.java
        logger.lifecycle(
            "kotlin-winrt generator worker runtime: KotlinPoet={}, Kotlin stdlib={}, classloader={}",
            codeSourceLocation(kotlinPoetClass.protectionDomain?.codeSource),
            codeSourceLocation(kotlinStdlibClass.protectionDomain?.codeSource),
            GenerateWinRTProjectionsWorkAction::class.java.classLoader,
        )
        runCatching {
            ClassName("kotlin", "String")
        }.getOrElse { error ->
            throw IllegalStateException(
                "kotlin-winrt generator worker loaded an incompatible KotlinPoet from " +
                    "${codeSourceLocation(kotlinPoetClass.protectionDomain?.codeSource)}. " +
                    "The projection generator must run in the isolated worker classpath configured by " +
                    KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION +
                    ", not a downstream Gradle/buildSrc parent classloader.",
                error,
            )
        }
    }

    private fun codeSourceLocation(codeSource: CodeSource?): String =
        codeSource?.location?.toString() ?: "<unknown>"

    private fun runAuthoringScanner(
        sourceRoots: List<Path>,
        metadataIndex: Path,
        candidatesFile: Path,
    ) {
        val scannerWorkDirectory = candidatesFile.parent
        execOperations.javaexec { spec ->
            spec.classpath = parameters.authoringScannerClasspath
            spec.mainClass.set("io.github.composefluent.winrt.compiler.KotlinWinRTAuthoringScannerCli")
            spec.workingDir(scannerWorkDirectory.toFile())
            spec.jvmArgs(
                parameters.authoringScannerJvmArgs.get() +
                    "-Djava.io.tmpdir=${scannerWorkDirectory.toAbsolutePath().normalize()}",
            )
            spec.args(
                buildList {
                    add("--metadata-index")
                    add(metadataIndex.toString())
                    add("--output")
                    add(candidatesFile.toString())
                    sourceRoots.forEach { root ->
                        add("--source-root")
                        add(root.toString())
                    }
                },
            )
        }
    }

}

internal fun dependencyProjectedTypeNames(
    model: WinRTMetadataModel,
    identityFiles: Iterable<File>,
): Set<String> =
    identityFiles
        .flatMap { identityFile ->
            dependencyProjectedTypeNames(model, readProjectionSurfaceIdentity(identityFile)) +
                readDependencyAuthoredTypeNames(identityFile) +
                readAuthoredHostManifestRecords(identityFile)
                    .flatMap { record -> record.activatableClasses + record.activatableClassTargets.keys }
        }
        .toSortedSet()

internal fun dependencyProjectionSurfaceTypeNames(
    identityFiles: Iterable<File>,
): List<String> =
    identityFiles
        .flatMap { identityFile ->
            val identity = readProjectionSurfaceIdentity(identityFile)
            identity.includeTypes + identity.currentShapeProjectedTypes() + readDependencyAuthoredTypeNames(identityFile)
        }
        .distinct()
        .sorted()

internal fun dependencyInternalProjectedTypeNames(
    model: WinRTMetadataModel,
    identityFiles: Iterable<File>,
): Set<String> {
    return identityFiles.flatMap { file ->
        val identity = readProjectionSurfaceIdentity(file)
        val ownedTypes = dependencyProjectedTypeNames(model, identity)
        identity.internalProjectedTypes.orEmpty().filter { it in ownedTypes }
    }.toSortedSet()
}

internal fun dependencySourceAdditionTypeNames(
    identityFiles: Iterable<File>,
): Set<String> = identityFiles
    .also(::validateDependencyProjectionIdentityOwnership)
    .filter(File::isFile)
    .flatMap { identityFile -> readProjectionSurfaceIdentity(identityFile).sourceAdditions }
    .toSortedSet()

internal fun validateDependencyProjectionIdentityOwnership(identityFiles: Iterable<File>) {
    val files = identityFiles.toList()
    val typeOwners = linkedMapOf<String, Pair<ProjectionIdentityOwner, File>>()
    val identities = linkedMapOf<String, Pair<ProjectionIdentityOwner, File>>()
    files.sortedBy(File::getAbsolutePath).forEach { identityFile ->
        val identity = readProjectionSurfaceIdentity(identityFile)
        val owner = identity.owner ?: return@forEach
        val previousOwner = identities.putIfAbsent(owner.name, owner to identityFile)
        check(previousOwner == null || previousOwner.first == owner) {
            "WinRT projection owner '${owner.name}' has conflicting versions or identity contents: " +
                "'${previousOwner?.second}' and '$identityFile'. Resolve one producer version."
        }
        // A source addition may also collide with a generated projection, so both share
        // the same namespace of public Kotlin ownership rather than separate registries.
        (identity.currentShapeProjectedTypes() + identity.sourceAdditions).forEach { typeName ->
            val previous = typeOwners.putIfAbsent(typeName, owner to identityFile)
            check(previous == null || previous.first == owner) {
                "Projected type '$typeName' is claimed by multiple WinRT projection owners: " +
                    "'${previous?.first?.name}' (${previous?.second}) and '${owner.name}' ($identityFile)."
            }
        }
    }
    files.forEach { file ->
        readProjectionDependencyOwners(file).forEach dependency@{ expected ->
            val resolved = identities[expected.name]?.first
            // During graph construction project producers may not have run yet.
            if (resolved == null && files.any { !it.isFile }) return@dependency
            check(resolved == expected) {
                "WinRT projection '$file' requires owner '${expected.name}' version '${expected.version}' " +
                    "with identity ${expected.fingerprint}, but resolved ${resolved ?: "no owner"}. " +
                    "Resolve the producer's projection dependencies or rebuild it against the selected SDK."
            }
        }
    }
}

internal fun mergedAuthoringMetadataIndexTypes(
    model: WinRTMetadataModel,
    identityFiles: Iterable<java.io.File>,
) = (
    model.namespaces
        .flatMap { namespace -> namespace.types }
        .map { type ->
            io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType(
                qualifiedName = type.qualifiedName,
                kind = type.kind.name,
                overridableInterfaces = type.implementedInterfaces
                    .filter { implementation -> implementation.isOverridable }
                    .map { implementation -> implementation.interfaceName }
                    .distinct()
                    .sorted(),
                baseTypeName = type.baseTypeName.orEmpty(),
                iid = type.iid,
            )
        } +
        identityFiles
            .flatMap { identityFile ->
                readAuthoringMetadataIndexRows(identityFile)
                    .let { rows ->
                        parseAuthoringMetadataIndexRows(
                            rows,
                            "${identityFile.absolutePath} authoringMetadataIndexRows",
                        ).values
                    }
            }
    )
    .distinctBy { type -> type.qualifiedName }
    .sortedBy { type -> type.qualifiedName }

private fun dependencyProjectedTypeNames(
    model: WinRTMetadataModel,
    identity: ProjectionSurfaceIdentity,
): List<String> {
    val typesByName = model.namespaces
        .flatMap { namespace -> namespace.types }
        .associateBy(WinRTTypeDefinition::qualifiedName)
    return (identity.includeTypes + identity.currentShapeProjectedTypes())
        .distinct()
        .flatMap { typeName -> dependencyOwnedTypeAndBaseChain(typeName, typesByName) }
        .distinct()
}

private fun dependencyOwnedTypeAndBaseChain(
    typeName: String,
    typesByName: Map<String, WinRTTypeDefinition>,
): List<String> {
    val result = mutableListOf<String>()
    var currentName: String? = typeName
    while (!currentName.isNullOrBlank()) {
        val current = typesByName[currentName] ?: break
        if (!result.add(current.qualifiedName)) {
            break
        }
        currentName = current.baseTypeName
    }
    return result
}

internal fun automaticXamlComponentResourceDictionaryTypes(
    model: WinRTMetadataModel,
    includeTypes: Set<String>,
): List<String> {
    return model.xamlComponentResourceDictionaryTypes(includeTypes)
}

internal fun authoringCandidateMetadataRootNames(candidates: List<KotlinWinRTAuthoredTypeCandidate>): List<String> =
    candidates
        .flatMap { candidate ->
            candidate.winRTInterfaceNames + candidate.overridableInterfaceNames +
                candidate.staticFactoryInterfaceNames + listOfNotNull(
                    candidate.winRTBaseClassName,
                    candidate.activatableFactoryInterfaceName,
                    candidate.sourceTypeName,
                )
        }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .sorted()

internal fun WinRTMetadataModel.unresolvedWindowsSdkTypeReferences(): Set<String> {
    val definedTypeNames = namespaces
        .flatMap(WinRTNamespace::types)
        .mapTo(mutableSetOf(), WinRTTypeDefinition::qualifiedName)
    return namespaces
        .asSequence()
        .flatMap { namespace -> namespace.types.asSequence() }
        .flatMap { type -> type.referencedTypeNames().asSequence() }
        .filter { typeName -> typeName.startsWith("Windows.") }
        .filterNot(definedTypeNames::contains)
        .toSortedSet()
}

private fun WinRTTypeDefinition.referencedTypeNames(): Set<String> = buildSet {
    fun addTypeRef(type: WinRTTypeRef) {
        when (type.kind) {
            WinRTTypeRefKind.Named -> {
                type.qualifiedName?.let(::add)
                type.typeArguments.forEach(::addTypeRef)
            }
            WinRTTypeRefKind.Array -> type.elementType?.let(::addTypeRef)
            WinRTTypeRefKind.GenericTypeParameter,
            WinRTTypeRefKind.MethodTypeParameter,
            WinRTTypeRefKind.Unknown -> Unit
        }
    }

    fun addTypeName(typeName: String) {
        addTypeRef(WinRTTypeRef.fromDisplayName(typeName))
    }

    baseType?.let(::addTypeRef)
    defaultInterface?.let(::addTypeRef)
    implementedInterfaces.forEach { implementation -> addTypeRef(implementation.interfaceType) }
    genericParameters.forEach { parameter -> parameter.constraintTypes.forEach(::addTypeRef) }
    fields.forEach { field ->
        addTypeRef(field.type)
        addTypeName(field.typeName)
    }
    methods.forEach { method ->
        addTypeRef(method.returnType)
        addTypeName(method.returnTypeName)
        method.parameters.forEach { parameter ->
            addTypeRef(parameter.type)
            addTypeName(parameter.typeName)
        }
    }
    properties.forEach { property ->
        addTypeRef(property.type)
        addTypeName(property.typeName)
    }
    events.forEach { event ->
        addTypeRef(event.delegateType)
        addTypeName(event.delegateTypeName)
    }
    activation.activatableFactoryInterface?.let(::addTypeRef)
    activation.staticInterfaces.forEach(::addTypeRef)
    activation.composableFactoryInterface?.let(::addTypeRef)
    activation.factories.forEach { factory -> addTypeRef(factory.interfaceType) }
}

internal data class ProjectionSurfaceIdentity(
    val includeNamespaces: List<String>,
    val includeTypes: List<String>,
    val projectionShapeVersion: Int?,
    val projectedTypes: List<String>?,
    val sourceAdditions: List<String>,
    val excludeNamespaces: List<String>,
    val excludeTypes: List<String>,
    val internalProjectedTypes: List<String>? = null,
    val owner: ProjectionIdentityOwner? = null,
)

internal const val CURRENT_PROJECTION_SHAPE_VERSION: Int =
    io.github.composefluent.winrt.projections.generator.KOTLIN_PROJECTION_SHAPE_VERSION

internal fun ProjectionSurfaceIdentity.currentShapeProjectedTypes(): List<String> =
    projectedTypes.orEmpty().takeIf { projectionShapeVersion == CURRENT_PROJECTION_SHAPE_VERSION }.orEmpty()

internal fun readProjectionSurfaceIdentity(identityFile: java.io.File): ProjectionSurfaceIdentity {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val document = readProjectionIdentityDocument(content, identityFile)
    val shapeVersion = validateProjectionContractVersions(document, identityFile)
    val owner = readProjectionIdentityOwner(document, identityFile)
    fun names(name: String): List<String>? = projectionIdentityNames(document, name, identityFile)
    return ProjectionSurfaceIdentity(
        includeNamespaces = names("includeNamespaces").orEmpty(),
        includeTypes = names("includeTypes").orEmpty(),
        projectionShapeVersion = shapeVersion,
        projectedTypes = names("projectedTypes"),
        sourceAdditions = names("sourceAdditions").orEmpty(),
        excludeNamespaces = names("excludeNamespaces").orEmpty(),
        excludeTypes = names("excludeTypes").orEmpty(),
        internalProjectedTypes = names("internalProjectedTypes"),
        owner = owner,
    )
}
