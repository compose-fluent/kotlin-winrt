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
            )
        }
    }
}

data class WinRTSourceSetData(
    val name: String,
    val kotlinRoots: List<String>,
    val dependsOn: List<String>,
    val appxResourceRoots: List<String>,
) : Serializable

data class WinRTTargetData(val name: String, val platform: String, val sourceSets: List<String>) : Serializable
data class WinRTNuGetData(val id: String, val version: String, val generateProjection: Boolean) : Serializable
