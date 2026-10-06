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
        }.sortedBy { it.getName() }
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
            }.sortedBy { it.getName() },
            nugetPackages = windows?.packageReferences?.nugetPackages.orEmpty().map { pkg ->
                IdeNuGetPackage(pkg.packageId, pkg.version.orNull.orEmpty(), pkg.generateProjection)
            }.sortedBy { it.getId().lowercase(java.util.Locale.ROOT) },
            manifestFiles = windows?.application?.appxManifestFiles?.files.orEmpty()
                .map { it.absoluteFile.normalize().path }.sorted(),
        )
    }
}

private data class IdeModel(
    private val enabled: Boolean,
    private val projectPath: String,
    private val projectDirectory: String,
    private val buildDirectory: String,
    private val kotlinVersion: String,
    private val windowsSdkVersion: String,
    private val sourceSets: List<WinRTIdeModel.SourceSet>,
    private val targets: List<WinRTIdeModel.Target>,
    private val nugetPackages: List<WinRTIdeModel.NuGetPackage>,
    private val manifestFiles: List<String>,
) : WinRTIdeModel {
    override fun getSchemaVersion() = WinRTIdeModel.SCHEMA_VERSION
    override fun isEnabled() = enabled
    override fun getProjectPath() = projectPath
    override fun getProjectDirectory() = projectDirectory
    override fun getBuildDirectory() = buildDirectory
    override fun getKotlinVersion() = kotlinVersion
    override fun getWindowsSdkVersion() = windowsSdkVersion
    override fun getSourceSets() = sourceSets
    override fun getTargets() = targets
    override fun getNuGetPackages() = nugetPackages
    override fun getManifestFiles() = manifestFiles
}

private data class IdeSourceSet(
    private val name: String,
    private val kotlinRoots: List<String>,
    private val dependsOn: List<String>,
    private val appxResourceRoots: List<String>,
) : WinRTIdeModel.SourceSet {
    override fun getName() = name
    override fun getKotlinRoots() = kotlinRoots
    override fun getDependsOn() = dependsOn
    override fun getAppxResourceRoots() = appxResourceRoots
}

private data class IdeTarget(
    private val name: String,
    private val platform: String,
    private val sourceSets: List<String>,
) : WinRTIdeModel.Target {
    override fun getName() = name
    override fun getPlatform() = platform
    override fun getSourceSets() = sourceSets
}

private data class IdeNuGetPackage(
    private val id: String,
    private val version: String,
    private val generateProjection: Boolean,
) : WinRTIdeModel.NuGetPackage {
    override fun getId() = id
    override fun getVersion() = version
    override fun isGenerateProjection() = generateProjection
}
