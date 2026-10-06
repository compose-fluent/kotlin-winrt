package io.github.composefluent.winrt.ide.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class WinRTProjectResolver : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses(): Set<Class<*>> = setOf(WinRTIdeModel::class.java)
    override fun getToolingExtensionsClasses(): Set<Class<*>> = setOf(WinRTIdeModel::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getExtraProject(gradleModule, WinRTIdeModel::class.java)
        if (model != null && model.isEnabled) {
            ideModule.createChild(WinRTModuleData.KEY, WinRTModuleData.from(model))
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}
