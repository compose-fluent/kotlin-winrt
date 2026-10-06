package io.github.composefluent.winrt.ide.hotreload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadStep
import org.jetbrains.jewel.ui.component.*

@Composable
fun WinRTHotReloadPanel(project: Project) {
    val projects = project.service<WinRTProjectService>()
    val service = project.service<WinRTHotReloadService>()
    val modules by projects.modules.collectAsState()
    val state by service.state.collectAsState()
    val automatic by service.automatic.collectAsState()
    var directory by remember { mutableStateOf<String?>(null) }
    var task by remember { mutableStateOf<String?>(null) }
    val available = modules.filter { it.hotReloadLaunches.isNotEmpty() && it.xamlCompilations.isNotEmpty() }
    val module = available.firstOrNull { it.projectDirectory == directory } ?: available.firstOrNull()
    val launch = module?.hotReloadLaunches?.firstOrNull { it.taskName == task } ?: module?.hotReloadLaunches?.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("XAML Hot Reload")
            Text("Update properties, mutable resources and local styles while keeping the current controls.")
            if (available.isEmpty()) Text("Synchronize a JVM WinUI application configured with packageType = None.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { available.forEach { current ->
                RadioButtonRow(current.projectPath, module == current, { directory = current.projectDirectory; task = null })
            } }
            module?.hotReloadLaunches?.forEach { current ->
                RadioButtonRow(current.taskName, launch == current, { task = current.taskName })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultButton(enabled = module != null && launch != null && !state.busy && state.pid == null,
                    onClick = { service.start(module!!, launch!!) }) { Text("Start with Hot Reload") }
                DefaultButton(enabled = module != null && launch != null && !state.busy,
                    onClick = { service.start(module!!, launch!!, restart = true) }) { Text("Rebuild and restart") }
            }
            CheckboxRow("Apply XAML document changes automatically", automatic, { service.automatic.value = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultButton(enabled = state.connected && !state.busy, onClick = service::apply) { Text("Apply changes") }
                DefaultButton(enabled = !state.busy, onClick = service::reconnect) { Text("Reconnect") }
                DefaultButton(onClick = service::disconnect) { Text("Disconnect") }
                DefaultButton(enabled = state.pid != null, onClick = service::stop) { Text("Stop application") }
            }
            Text(state.message)
            state.pid?.let { Text("Application process: $it") }
            Text("Style replacements refresh explicit StaticResource consumers in this page. Theme expressions, implicit styles, external dictionaries, element structure and compiled connections require rebuilding.")
        }
        items(state.roots, key = { "${it.className}:${it.resourcePath}" }) { root ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${root.className} · update ${root.version}")
                Text(root.resourcePath)
                Text("Named elements: ${root.elements.joinToString().ifEmpty { "none" }}")
            }
        }
        items(state.values) { value ->
            val path = value.path.joinToString("") { step -> when (step) {
                is WinRTXamlHotReloadStep.Property -> ".${step.name}"
                is WinRTXamlHotReloadStep.Key -> "[${step.name}]"
                is WinRTXamlHotReloadStep.Index -> "[${step.index}]"
            } }
            Text("${value.element.ifEmpty { "root" }}$path.${value.property} = ${value.value}")
        }
    }
}
