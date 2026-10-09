package io.github.composefluent.winrt.ide.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.Serializable

/** Materializes Tooling API proxies before the IDE persists its external project graph. */
data class WinRTModuleData(
    val projectPath: String,
    val projectDirectory: String,
    val buildDirectory: String,
    val kotlinVersion: String,
    val windowsSdkVersion: String,
    val sourceSets: List<WinRTSourceSetData>,
    val targets: List<WinRTTargetData>,
    val packages: List<WinRTNuGetData>,
    val manifestFiles: List<String>,
    val xamlCompilations: List<WinRTXamlCompilationData> = emptyList(),
    val packageLayouts: List<WinRTPackageLayoutData> = emptyList(),
    val nuGetConfigFile: String = "",
    val nuGetConfigDirectory: String = projectDirectory,
    val restoreLockFiles: List<String> = emptyList(),
    val hotReloadLaunches: List<WinRTHotReloadLaunchData> = emptyList(),
    val staticPreview: WinRTStaticPreviewData? = null,
    val runTasks: List<String> = emptyList(),
) : AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {
    companion object {
        val KEY: Key<WinRTModuleData> = Key.create(WinRTModuleData::class.java, ProjectKeys.MODULE.processingWeight + 1)

        fun from(model: WinRTIdeModel): WinRTModuleData {
            require(model.schemaVersion == WinRTIdeModel.SCHEMA_VERSION) {
                "Unsupported Kotlin WinRT IDE model version ${model.schemaVersion}"
            }
            return WinRTModuleData(
                model.projectPath, model.projectDirectory, model.buildDirectory,
                model.kotlinVersion, model.windowsSdkVersion,
                model.sourceSets.map { WinRTSourceSetData(it.name, it.kotlinRoots.toList(), it.dependsOn.toList(), it.appxResourceRoots.toList()) },
                model.targets.map { WinRTTargetData(it.name, it.platform, it.sourceSets.toList()) },
                model.nuGetPackages.map { WinRTNuGetData(it.id, it.version, it.isGenerateProjection) },
                model.manifestFiles.toList(),
                model.xamlCompilations.map { WinRTXamlCompilationData(it.taskName, it.sourceRoots.toList(),
                    it.declarationsFile, it.inputFile, it.compilerDirectory, it.metadataIndexFile) },
                model.packageLayouts.map { WinRTPackageLayoutData(it.taskName, it.variant, it.packageDirectory,
                    it.resourceReportFile, it.minWindowsVersion, it.maxVersionTested) },
                model.nuGetConfigFile, model.nuGetConfigDirectory, model.restoreLockFiles.toList(),
                model.hotReloadLaunches.map { WinRTHotReloadLaunchData(it.taskName, it.executable, it.workingDirectory, it.previewExecutable) },
                model.staticPreview?.let { WinRTStaticPreviewData(it.taskName, it.executable, it.workingDirectory, it.metadataReferencesFile) },
                model.runTasks.toList(),
            )
        }
    }
}

data class WinRTHotReloadLaunchData(val taskName: String, val executable: String, val workingDirectory: String,
    val previewExecutable: String = executable) : Serializable

data class WinRTStaticPreviewData(val taskName: String, val executable: String, val workingDirectory: String,
    val metadataReferencesFile: String) : Serializable {
    fun launch() = WinRTHotReloadLaunchData(taskName, executable, workingDirectory)
}

data class WinRTSourceSetData(
    val name: String,
    val kotlinRoots: List<String>,
    val dependsOn: List<String>,
    val appxResourceRoots: List<String>,
) : Serializable

data class WinRTTargetData(val name: String, val platform: String, val sourceSets: List<String>) : Serializable
data class WinRTNuGetData(val id: String, val version: String, val generateProjection: Boolean) : Serializable

data class WinRTPackageLayoutData(val taskName: String, val variant: String, val packageDirectory: String,
    val resourceReportFile: String, val minWindowsVersion: String, val maxVersionTested: String) : Serializable

data class WinRTXamlCompilationData(
    val taskName: String,
    val sourceRoots: List<String>,
    val declarationsFile: String,
    val inputFile: String,
    val compilerDirectory: String,
    val metadataIndexFile: String,
) : Serializable
