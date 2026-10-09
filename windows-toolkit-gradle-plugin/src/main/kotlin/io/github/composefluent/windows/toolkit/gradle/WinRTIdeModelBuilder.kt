package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Imports configuration only. SDK/WinMD ingestion and projection policy remain owned by
 * winrt-metadata and winrt-generator (the .cswinrt/src/cswinrt responsibility split).
 * A Gradle sync must not restore NuGet packages, resolve configurations or run the generator.
 */
internal class WinRTIdeModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = modelName == WinRTIdeModel::class.java.name

    override fun buildAll(modelName: String, project: Project): WinRTIdeModel {
        require(canBuild(modelName)) { "Unsupported IDE model: $modelName" }
        val windows = project.extensions.findByType(WindowsExtension::class.java)
        val kotlin = project.extensions.findByType(KotlinProjectExtension::class.java)
        val targets = when (kotlin) {
            is KotlinMultiplatformExtension -> kotlin.targets.toList()
            is KotlinSingleTargetExtension<*> -> listOf(kotlin.target)
            else -> emptyList()
        }
        val sourceSets = if (windows == null) emptyList() else kotlin?.sourceSets.orEmpty().map { sourceSet ->
            IdeSourceSet(
                sourceSet.name,
                sourceSet.kotlin.srcDirs.map { it.absoluteFile.normalize().path }.sorted(),
                sourceSet.dependsOn.map { it.name }.sorted(),
                appxResourceRoots(project, listOf(sourceSet.name)).map { it.toAbsolutePath().normalize().toString() },
            )
        }.sortedBy { it.name }
        return IdeModel(
            enabled = windows != null,
            projectPath = project.path,
            projectDirectory = project.projectDir.absolutePath,
            buildDirectory = project.layout.buildDirectory.get().asFile.absolutePath,
            kotlinVersion = if (kotlin == null) "" else project.getKotlinPluginVersion(),
            windowsSdkVersion = windows?.packageReferences?.windowsSdkVersion?.orNull.orEmpty(),
            sourceSets = sourceSets,
            targets = if (windows == null) emptyList() else targets.map { target ->
                IdeTarget(
                    target.name,
                    target.platformType.name,
                    target.compilations.flatMap { it.allKotlinSourceSets }.map { it.name }.distinct().sorted(),
                )
            }.sortedBy { it.name },
            nuGetPackages = windows?.packageReferences?.nugetPackages.orEmpty().map { pkg ->
                IdeNuGetPackage(pkg.packageId, pkg.version.orNull.orEmpty(), pkg.generateProjection)
            }.sortedBy { it.id.lowercase(java.util.Locale.ROOT) },
            manifestFiles = windows?.application?.appxManifestFiles?.files.orEmpty()
                .map { it.absoluteFile.normalize().path }.sorted(),
            xamlCompilations = if (windows == null) emptyList() else
                project.tasks.withType(CompileWinRTXamlTask::class.java).filter { !it.semanticSymbols.isPresent }.map { task ->
                    IdeXamlCompilation(
                        task.name,
                        task.sourceRoots.files.map { it.absoluteFile.normalize().path }.sorted(),
                        task.declarationsFile.get().asFile.absolutePath,
                        task.outputDirectory.file("input.json").get().asFile.absolutePath,
                        task.compilerDirectory.orNull?.asFile?.absolutePath.orEmpty(),
                        project.tasks.withType(GenerateWinRTAuthoringCandidatesTask::class.java)
                            .firstOrNull()?.metadataIndex?.orNull?.asFile?.absolutePath.orEmpty(),
                    )
                }.sortedBy { it.taskName },
            packageLayouts = if (windows == null) emptyList() else
                project.tasks.withType(StageWinAppPackageTask::class.java).map { task ->
                    IdePackageLayout(task.name, task.applicationVariant.get(), task.outputDirectory.get().asFile.absolutePath,
                        task.resourceResolutionReport.get().asFile.absolutePath, task.minWindowsVersion.get(), task.maxVersionTested.get())
                }.sortedBy { it.taskName },
            nuGetConfigFile = windows?.packageReferences?.nugetConfigFile?.orNull?.asFile?.absolutePath.orEmpty(),
            nuGetConfigDirectory = windows?.packageReferences?.nugetConfigDirectory?.orNull?.asFile?.absolutePath ?: project.projectDir.absolutePath,
            restoreLockFiles = if (windows == null) emptyList() else
                project.tasks.withType(RestoreWinAppDependenciesTask::class.java).map { it.winmdLockFile.get().asFile.absolutePath }.distinct().sorted(),
            hotReloadLaunches = if (windows == null) emptyList() else (
                project.tasks.withType(RunWinAppHostTask::class.java).filter { it.supportsXamlHotReload.get() && !it.sdkPreview.get() }.map {
                    IdeHotReloadLaunch(it.name, it.hostExecutable.get().asFile.absolutePath, it.workingDirectory.get().asFile.absolutePath)
                } + project.tasks.withType(RunWinAppPackageTask::class.java).filter { it.supportsXamlHotReload.get() }.map {
                    IdeHotReloadLaunch(it.name, it.hostExecutable.get().asFile.absolutePath, it.deploymentDirectory.get().asFile.absolutePath,
                        it.previewHostExecutable.get().asFile.absolutePath)
                }).sortedBy { it.taskName },
            staticPreview = if (windows?.packageReferences?.nugetPackages?.any {
                    it.packageId.startsWith("Microsoft.WindowsAppSDK", true) } != true) null else
                project.tasks.withType(RunWinAppHostTask::class.java).firstOrNull { it.sdkPreview.get() }?.let { task ->
                    IdeStaticPreview(task.name, task.hostExecutable.get().asFile.absolutePath,
                        task.workingDirectory.get().asFile.absolutePath,
                        project.tasks.named("buildWinRTXamlSdkPreview", BuildWinRTXamlSdkPreviewTask::class.java)
                            .get().metadataReferencesFile.get().asFile.absolutePath)
                },
            runTasks = if (windows == null) emptyList() else (
                project.tasks.withType(RunWinAppHostTask::class.java).filter { !it.sdkPreview.get() && it.supportsXamlHotReload.get() }.map { it.name } +
                project.tasks.withType(RunWinAppPackageTask::class.java).filter { it.packageType.get() == WindowsPackageType.Packaged.name }.map { it.name } +
                if (windows.application.packageType.get() == WindowsPackageType.None) project.tasks.names.filter {
                    it.startsWith("runDebugExecutable") || it.startsWith("runReleaseExecutable")
                } else emptyList()
            ).distinct().sorted(),
        )
    }
}

private data class IdeModel(
    private val enabled: Boolean,
    override val projectPath: String,
    override val projectDirectory: String,
    override val buildDirectory: String,
    override val kotlinVersion: String,
    override val windowsSdkVersion: String,
    override val sourceSets: List<WinRTIdeModel.SourceSet>,
    override val targets: List<WinRTIdeModel.Target>,
    override val nuGetPackages: List<WinRTIdeModel.NuGetPackage>,
    override val manifestFiles: List<String>,
    override val xamlCompilations: List<WinRTIdeModel.XamlCompilation>,
    override val packageLayouts: List<WinRTIdeModel.PackageLayout>,
    override val nuGetConfigFile: String,
    override val nuGetConfigDirectory: String,
    override val restoreLockFiles: List<String>,
    override val hotReloadLaunches: List<WinRTIdeModel.HotReloadLaunch>,
    override val staticPreview: WinRTIdeModel.StaticPreview?,
    override val runTasks: List<String>,
) : WinRTIdeModel {
    override val schemaVersion get() = WinRTIdeModel.SCHEMA_VERSION
    override val isEnabled get() = enabled
}

private data class IdeStaticPreview(override val taskName: String, override val executable: String,
    override val workingDirectory: String, override val metadataReferencesFile: String) : WinRTIdeModel.StaticPreview

private data class IdeHotReloadLaunch(
    override val taskName: String,
    override val executable: String,
    override val workingDirectory: String,
    override val previewExecutable: String = executable,
) : WinRTIdeModel.HotReloadLaunch

private data class IdePackageLayout(
    override val taskName: String,
    override val variant: String,
    override val packageDirectory: String,
    override val resourceReportFile: String,
    override val minWindowsVersion: String,
    override val maxVersionTested: String,
) : WinRTIdeModel.PackageLayout

private data class IdeXamlCompilation(
    override val taskName: String,
    override val sourceRoots: List<String>,
    override val declarationsFile: String,
    override val inputFile: String,
    override val compilerDirectory: String,
    override val metadataIndexFile: String,
) : WinRTIdeModel.XamlCompilation

private data class IdeSourceSet(
    override val name: String,
    override val kotlinRoots: List<String>,
    override val dependsOn: List<String>,
    override val appxResourceRoots: List<String>,
) : WinRTIdeModel.SourceSet

private data class IdeTarget(
    override val name: String,
    override val platform: String,
    override val sourceSets: List<String>,
) : WinRTIdeModel.Target

private data class IdeNuGetPackage(
    override val id: String,
    override val version: String,
    private val generateProjection: Boolean,
) : WinRTIdeModel.NuGetPackage {
    override val isGenerateProjection get() = generateProjection
}
