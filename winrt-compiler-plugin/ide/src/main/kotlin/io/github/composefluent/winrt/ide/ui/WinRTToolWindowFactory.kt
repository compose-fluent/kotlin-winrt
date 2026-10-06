package io.github.composefluent.winrt.ide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

class WinRTToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<WinRTProjectService>()
        service.refreshFromGradleCache()
        toolWindow.addComposeTab("Projects", focusOnClickInside = true) {
            val modules by service.modules.collectAsState()
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Kotlin WinRT")
                    if (modules.isEmpty()) Text("Synchronize Gradle to import modules using the Windows toolkit plugin.")
                }
                items(modules, key = { it.projectDirectory }) { module ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(module.projectPath)
                        Text("Kotlin ${module.kotlinVersion} · Windows SDK ${module.windowsSdkVersion.ifEmpty { "not selected" }}")
                        Text(module.targets.joinToString { "${it.name} (${it.platform})" })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DefaultButton(onClick = { service.openFile("${module.projectDirectory}/build.gradle.kts") }) { Text("Build configuration") }
                            module.manifestFiles.firstOrNull()?.let { manifest ->
                                DefaultButton(onClick = { service.openFile(manifest) }) { Text("AppX manifest") }
                            }
                        }
                        module.sourceSets.forEach { sourceSet ->
                            Text("${sourceSet.name}: ${sourceSet.dependsOn.joinToString().ifEmpty { "no source set dependencies" }}")
                        }
                        module.packages.forEach { pkg -> Text("${pkg.id} ${pkg.version}") }
                    }
                }
            }
        }
    }
}
