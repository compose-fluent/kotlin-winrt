package io.github.composefluent.winrt.ide.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Load applications from Gradle's persisted model without requiring a WinRT editor or tool window. */
class WinRTProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<WinRTProjectService>()
    }
}
