package io.github.composefluent.winrt.ide.gradle

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.analysis.WinRTFirModuleConfiguration

class WinRTProjectDataService : AbstractProjectDataService<WinRTModuleData, Void>() {
    override fun getTargetDataKey() = WinRTModuleData.KEY

    override fun importData(
        toImport: Collection<DataNode<WinRTModuleData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        projectData?.let { data ->
            project.service<WinRTProjectService>().replaceBuildModels(data.linkedExternalProjectPath, toImport.map { it.data })
        }
    }

    override fun postProcess(
        toImport: Collection<DataNode<WinRTModuleData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        WinRTFirModuleConfiguration.configure(project, toImport.map { it.data }, modelsProvider)
    }
}
