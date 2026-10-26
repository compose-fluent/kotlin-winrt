package io.github.composefluent.winrt.ide.hotreload

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.ui.*
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadStep
import org.jetbrains.jewel.ui.component.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WinUIXamlHotReloadPanel(project: Project) {
    val service = project.service<WinUIXamlHotReloadService>()
    val state by service.state.collectAsState()
    val automatic by service.automatic.collectAsState()
    val module = selectedWinRTModule(project)
    var task by remember(module?.projectDirectory) { mutableStateOf<String?>(null) }
    val launches = module?.hotReloadLaunches.orEmpty().distinctBy { it.executable }
    val launch = launches.firstOrNull { it.taskName == task } ?: launches.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("XAML Hot Reload"); WinRTModulePicker(project) }
        item {
            when {
                module == null -> Text("Sync Gradle to load your WinUI application.")
                launches.isEmpty() -> Text("This module has no supported WinUI development launch. Select an application module and prepare XAML.")
                else -> WinRTChoice("Launch", launches.map { it.taskName to
                    it.taskName }, launch?.taskName) { task = it }
            }
            Text(state.message)
            if (state.projectCodeChanged && state.pid != null) Text("The application is running the previous compiled code. Source changes are not loaded yet.")
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.connected) DefaultButton(enabled = launch != null && !state.busy && state.pid == null,
                    onClick = { service.start(module!!, launch!!) }) { Text("Start application") }
                else DefaultButton(enabled = !state.busy, onClick = service::apply) { Text("Apply changes") }
                DefaultButton(enabled = launch != null && !state.busy && !state.rebuildingCode,
                    onClick = { service.rebuild(module!!, launch!!) }) { Text("Rebuild and restart") }
                if (state.pid != null) DefaultButton(onClick = service::stop) { Text("Stop") }
            }
            CheckboxRow("Apply changes automatically (rebuild when needed)", automatic, service::setAutomatic)
        }
        if (state.connected) item { Text("Connected · ${state.roots.size} loaded XAML roots") }
        state.restartOutcome?.let { outcome -> item {
            Text(outcome)
            Link("Dismiss restart result", onClick = service::dismissRestartOutcome)
        } }
        item {
            WinRTDetails("connection details") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultButton(enabled = !state.busy, onClick = service::reconnect) { Text("Reconnect") }
                    DefaultButton(onClick = service::disconnect) { Text("Disconnect") }
                }
                state.pid?.let { Text("Process: $it") }
                state.roots.forEach { root ->
                    Text("${root.className} · update ${root.version}")
                    Text(root.resourcePath)
                    Text("Named elements: ${root.elements.joinToString().ifEmpty { "none" }}")
                }
                state.values.forEach { value ->
                    val path = value.path.joinToString("") { step -> when (step) {
                        is WinUIXamlHotReloadStep.Property -> ".${step.name}"
                        is WinUIXamlHotReloadStep.Key -> "[${step.name}]"
                        is WinUIXamlHotReloadStep.Index -> "[${step.index}]"
                    } }
                    Text("${value.element.ifEmpty { "root" }}$path.${value.property} = ${value.value}")
                }
            }
        }
        item {
            WinRTDetails("supported edits") {
                Text("Properties, local resources, explicit styles and supported child collections update while the application runs.")
                Text("Existing named-element events can switch to already compiled compatible handlers, or be disabled and restored without restarting.")
                Text("New event handlers or slots, names, bindings, templates and parent changes require rebuilding. Moving controls preserves their objects; focus and Loaded/Unloaded state may change.")
                Text("Theme expressions, implicit styles and external dictionaries require rebuilding.")
                Text("Packaged applications and unpackaged toolkit hosts build code changes before restarting. A failed build keeps the current app; compatible named-control state is restored after a successful restart.")
            }
        }
    }
}
