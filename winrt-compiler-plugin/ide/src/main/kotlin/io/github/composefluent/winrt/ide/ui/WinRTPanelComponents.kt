package io.github.composefluent.winrt.ide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.*

@Composable
internal fun selectedWinRTModule(project: Project): WinRTModuleData? {
    val service = project.service<WinRTProjectService>()
    val modules by service.modules.collectAsState()
    val selected by service.selectedModuleDirectory.collectAsState()
    return modules.firstOrNull { it.projectDirectory == selected }
        ?: modules.firstOrNull { it.hotReloadLaunches.isNotEmpty() }
        ?: modules.firstOrNull { it.xamlCompilations.isNotEmpty() }
        ?: modules.firstOrNull()
}

@Composable
internal fun WinRTModulePicker(project: Project) {
    val service = project.service<WinRTProjectService>()
    val modules by service.modules.collectAsState()
    val selected = selectedWinRTModule(project)
    WinRTChoice("Module", modules.map { it.projectDirectory to it.projectPath }, selected?.projectDirectory) {
        service.selectedModuleDirectory.value = it
    }
}

@Composable
@OptIn(ExperimentalJewelApi::class)
internal fun WinRTChoice(label: String, choices: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        ListComboBox(
            items = choices.map { it.second },
            selectedIndex = choices.indexOfFirst { it.first == selected },
            onSelectedItemChange = { index -> choices.getOrNull(index)?.let { onSelect(it.first) } },
            itemKeys = { index, _ -> choices[index].first },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = choices.isNotEmpty(),
        )
    }
}

@Composable
internal fun WinRTDetails(label: String = "Details", content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Link(if (expanded) "Hide $label" else "Show $label", onClick = { expanded = !expanded })
        if (expanded) content()
    }
}
