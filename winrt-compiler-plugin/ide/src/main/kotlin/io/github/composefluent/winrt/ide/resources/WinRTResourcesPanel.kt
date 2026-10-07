package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import io.github.composefluent.winrt.ide.project.WinRTGradleTasks
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import io.github.composefluent.winrt.ide.ui.*
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WinRTResourcesPanel(project: Project) {
    val service = project.service<WinRTProjectService>()
    val modules by service.modules.collectAsState()
    val fileRevision by project.service<WinRTResourceChanges>().revision.collectAsState()
    val module = selectedWinRTModule(project)
    val defaultSource = module?.sourceSets?.firstOrNull { it.appxResourceRoots.isNotEmpty() } ?: module?.sourceSets?.firstOrNull()
    var selection by remember(module?.projectDirectory) { mutableStateOf(defaultSource?.name.orEmpty()) }
    var revision by remember { mutableIntStateOf(0) }
    var inventory by remember { mutableStateOf<WinRTResourceInventory?>(null) }
    var selected by remember { mutableStateOf<WinRTResourceEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var translationErrors by remember { mutableStateOf(emptyList<String>()) }
    val filter = remember { TextFieldState() }
    val sourceSet = module?.sourceSets?.firstOrNull { it.name == selection }
    val layout = module?.packageLayouts?.firstOrNull { it.taskName == selection }
    LaunchedEffect(module, selection, revision, fileRevision) {
        kotlinx.coroutines.delay(200)
        inventory = null
        selected = null
        inventory = withContext(Dispatchers.IO) {
            when {
                module == null -> null
                layout != null -> WinRTResourceCatalog.staged(module, layout, modules)
                else -> sourceSet?.let { WinRTResourceCatalog.sourceSet(module, it) }
            }
        }
        translationErrors = withContext(Dispatchers.IO) { WinRTReswTranslations.inspect(project, inventory?.entries.orEmpty()) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Application resources")
        WinRTModulePicker(project)
        WinRTChoice("Resource scope", module?.let { current -> current.sourceSets.filter { it.appxResourceRoots.isNotEmpty() }.map {
            it.name to "Source files · ${it.name}"
        } + current.packageLayouts.map { it.taskName to "Staged package · ${it.variant}" } }.orEmpty(), selection) { selection = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = { revision++ }) { Text("Refresh") }
            if (layout != null && module != null) DefaultButton(onClick = {
                WinRTGradleTasks.run(project, module, listOf(layout.taskName), "Stage ${layout.variant}") { revision++ }
            }) { Text("Stage variant") }
            sourceSet?.appxResourceRoots?.lastOrNull()?.let { root ->
                DefaultButton(onClick = { service.openFile(root) }) { Text("Open folder") }
                DefaultButton(onClick = {
                    error = null
                    val files = FileChooser.chooseFiles(FileChooserDescriptor(true, false, false, false, false, true), project, null)
                    runCatching {
                        WriteCommandAction.runWriteCommandAction(project, "Import AppX resources", null, Runnable {
                            val directory = requireNotNull(VfsUtil.createDirectoryIfMissing(root))
                            require(files.none { directory.findChild(it.name) != null }) { "A resource already exists with that name." }
                            require(files.map { it.name.lowercase() }.distinct().size == files.size) { "Select files with distinct resource names." }
                            files.forEach { VfsUtil.copyFile(this, it, directory) }
                        })
                    }.onFailure { error = it.message }
                    revision++
                }) { Text("Import files…") }
            }
        }
        (error ?: inventory?.error)?.let { Text(it) }
        TextField(filter, placeholder = { Text("Filter package paths / resource families") }, modifier = Modifier.fillMaxWidth())
        selected?.let { entry ->
            Text(entry.target)
            ResourcePreview(entry.source)
            DefaultButton(onClick = { service.openFile(entry.source) }) { Text("Open source") }
            WinRTDetails("file details") {
                Text("${entry.owner}: ${entry.source}")
                entry.sourceArchive?.let { Text("Archive: $it") }
                if (entry.overrides.isNotEmpty()) Text("Override chain: ${(entry.overrides + entry.source).joinToString(" → ")}")
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(translationErrors) { Text(it) }
            val query = filter.text.toString()
            inventory?.entries.orEmpty().filter { it.target.contains(query, true) || WinRTResourceCatalog.family(it.target).contains(query, true) }
                .groupBy { WinRTResourceCatalog.family(it.target) }.toSortedMap().forEach { (family, entries) ->
                item(key = "family:$family") { Text(family) }
                items(entries, key = { "file:${it.target}" }) { entry ->
                    DefaultButton(onClick = { selected = entry }) { Text("${entry.target} · ${entry.owner}") }
                }
            }
            if (inventory?.candidates?.isNotEmpty() == true) {
                item { Text("Actual PRI candidates") }
                items(inventory?.candidates.orEmpty().filter { it.resourceUri.contains(query, true) || it.qualifiers.contains(query, true) }) {
                    Text("${it.resourceUri} · ${it.qualifiers} · ${it.value}")
                }
            }
        }
    }
}

@Composable
private fun ResourcePreview(path: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching {
            val file = Path.of(path)
            if (!Files.isRegularFile(file) || Files.size(file) > 16 * 1024 * 1024) null else
                ImageIO.createImageInputStream(file.toFile()).use { stream ->
                    val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
                    try {
                        reader.input = stream
                        if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) null else reader.read(0).toComposeImageBitmap()
                    } finally { reader.dispose() }
                }
        }.getOrNull() }
    }
    bitmap?.let { Image(it, "Resource image preview", Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 120.dp)) }
}
