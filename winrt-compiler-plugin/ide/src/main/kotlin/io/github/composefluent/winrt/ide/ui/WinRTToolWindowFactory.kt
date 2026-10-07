package io.github.composefluent.winrt.ide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.project.WinRTGradleTasks
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.*

class WinRTToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.service<WinRTProjectService>().refreshFromGradleCache()
        toolWindow.addComposeTab("Overview", focusOnClickInside = true) { WinRTOverviewPanel(project) }
        toolWindow.addComposeTab("Hot Reload", focusOnClickInside = true) {
            io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadPanel(project)
        }
        toolWindow.addComposeTab("NuGet", focusOnClickInside = true) {
            io.github.composefluent.winrt.ide.nuget.WinRTNuGetPanel(project)
        }
        toolWindow.addComposeTab("Resources", focusOnClickInside = true) {
            io.github.composefluent.winrt.ide.resources.WinRTResourcesPanel(project)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WinRTOverviewPanel(project: Project) {
    val service = project.service<WinRTProjectService>()
    val snapshots by project.service<WinRTXamlSnapshotService>().state.collectAsState()
    val module = selectedWinRTModule(project)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Kotlin WinRT"); WinRTModulePicker(project) }
        if (module == null) item { Text("Sync Gradle to load your Kotlin WinRT modules.") }
        else {
            item {
                Text(module.targets.joinToString { "${it.name} (${it.platform})" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    module.manifestFiles.firstOrNull()?.let { manifest ->
                        DefaultButton(onClick = { io.github.composefluent.winrt.ide.resources.WinRTXmlFormEditorProvider.open(project, manifest) }) { Text("Manifest Designer") }
                    }
                    DefaultButton(onClick = { service.openFile("${module.projectDirectory}/build.gradle.kts") }) { Text("Open build file") }
                    if (module.xamlCompilations.isNotEmpty())
                        DefaultButton(onClick = { WinRTGradleTasks.prepareXaml(project, module) }) { Text("Prepare XAML") }
                }
            }
            if (module.xamlCompilations.isNotEmpty()) item {
                val states = module.xamlCompilations.mapNotNull { snapshots[WinRTXamlSnapshotService.key(it.declarationsFile)] }
                val errors = states.mapNotNull { it.error }.distinct()
                if (states.isEmpty()) Text("Prepare XAML to enable generated members in the editor.")
                else if (errors.isNotEmpty()) errors.forEach { Text(it) }
                else Text("${states.flatMap { it.declarations.pages }.distinctBy { it.className }.size} XAML classes ready")
            }
            item {
                Text("Manage dependencies in NuGet, application assets in Resources, and live XAML changes in Hot Reload.")
                WinRTDetails("project details") {
                    Text("Kotlin ${module.kotlinVersion} · Windows SDK ${module.windowsSdkVersion.ifEmpty { "not selected" }}")
                    Text(module.projectDirectory)
                    module.sourceSets.forEach { Text("${it.name}: ${it.dependsOn.joinToString().ifEmpty { "no source set dependencies" }}") }
                    module.packages.forEach { Text("${it.id} ${it.version}") }
                }
            }
        }
    }
}
