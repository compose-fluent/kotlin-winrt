package io.github.composefluent.winrt.ide.nuget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTGradleTasks
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import kotlinx.coroutines.*
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

@Composable
fun WinRTNuGetPanel(project: Project) {
    val service = project.service<WinRTProjectService>()
    val modules by service.modules.collectAsState()
    val dependencyRevision by service.dependencyRevision.collectAsState()
    var directory by remember { mutableStateOf<String?>(null) }
    val module = modules.firstOrNull { it.projectDirectory == directory } ?: modules.firstOrNull()
    val query = remember(module?.projectDirectory) { TextFieldState() }
    val id = remember(module?.projectDirectory) { TextFieldState() }
    val version = remember(module?.projectDirectory) { TextFieldState() }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf<List<WinRTNuGetSource>>(emptyList()) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    val source = sources.firstOrNull { it.name == sourceName } ?: sources.firstOrNull()
    var results by remember(module?.projectDirectory) { mutableStateOf<List<WinRTNuGetSearchResult>>(emptyList()) }
    var inventory by remember { mutableStateOf(WinRTNuGetInventory(emptyList(), emptyList())) }
    var selected by remember(module?.projectDirectory) { mutableStateOf<WinRTNuGetPackageStatus?>(null) }
    var contributions by remember { mutableStateOf<WinRTNuGetContributions?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var prerelease by remember { mutableStateOf(false) }
    var projection by remember { mutableStateOf(false) }
    var transitive by remember { mutableStateOf(false) }
    var versions by remember(module?.projectDirectory) { mutableStateOf<List<String>>(emptyList()) }
    var rid by remember { mutableStateOf("win-x64") }
    var request by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(module, revision, dependencyRevision) {
        inventory = WinRTNuGetInventory(emptyList(), emptyList()); sources = emptyList(); selected = null
        module?.let {
            try {
                val data = withContext(Dispatchers.IO) {
                    WinRTNuGetInventoryReader.read(it) to WinRTNuGetSources.read(Path.of(it.nuGetConfigDirectory.ifEmpty { it.projectDirectory }))
                }
                inventory = data.first; sources = data.second; failure = null
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
        }
    }
    LaunchedEffect(selected, rid) {
        contributions = null
        selected?.let {
            try { contributions = withContext(Dispatchers.IO) { WinRTNuGetInventoryReader.contributions(it, rid) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
        }
    }
    LaunchedEffect(module?.projectDirectory, source?.address) { request?.cancel(); busy = false; results = emptyList(); versions = emptyList() }
    fun browse(action: suspend () -> Unit) {
        request?.cancel()
        request = scope.launch {
            busy = true; failure = null
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
            finally { busy = false }
        }
    }
    fun restore(current: WinRTModuleData) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                service.restoreDependenciesAfterImport(current.projectDirectory)
                ExternalSystemUtil.refreshProject(com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(
                    service.buildRootFor(current) ?: current.projectDirectory),
                    ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
            }
        }
    }
    fun edit(remove: Boolean) {
        val current = module ?: return
        val packageId = id.text.toString(); val packageVersion = version.text.toString()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                failure = null
                runCatching {
                    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("${current.projectDirectory.replace('\\', '/')}/build.gradle.kts")
                        ?: error("Open the module's Gradle build file to edit this dependency.")
                    WinRTNuGetDependencies.apply(project, file, packageId, if (remove) null else packageVersion, projection)
                    FileDocumentManager.getInstance().getDocument(file)?.let { FileDocumentManager.getInstance().saveDocument(it) }
                    restore(current)
                }.onFailure { failure = it.message; service.openFile("${current.projectDirectory}/build.gradle.kts") }
            }
        }
    }
    fun choose(packageId: String, packageVersion: String) {
        id.edit { replace(0, length, packageId) }; version.edit { replace(0, length, packageVersion) }; versions = emptyList()
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("NuGet packages")
            if (modules.isEmpty()) Text("Synchronize a Kotlin WinRT project to manage its packages.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { modules.forEach { current ->
                DefaultButton(onClick = { directory = current.projectDirectory }) { Text(current.projectPath) }
            } }
            module?.let { current ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DefaultButton(onClick = { restore(current) }) { Text("Synchronize and restore") }
                    DefaultButton(onClick = { revision++ }) { Text("Refresh status") }
                    DefaultButton(onClick = { service.openFile("${current.projectDirectory}/build.gradle.kts") }) { Text("Build configuration") }
                    if (current.nuGetConfigFile.isNotEmpty()) DefaultButton(onClick = { service.openFile(current.nuGetConfigFile) }) { Text("NuGet.Config") }
                }
            }
            (inventory.errors + listOfNotNull(failure)).forEach { Text(it) }
            Text("Package sources")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sources.forEach { feed ->
                RadioButtonRow(feed.name, source === feed, { sourceName = feed.name })
            } }
            source?.let { feed ->
                Text(feed.address)
                if (feed.requiresProvider) Text("This source uses encrypted credentials. Search needs a NuGet source credential environment variable; restore uses the configured NuGet provider.")
            }
            TextField(query, placeholder = { Text("Search packages") }, modifier = Modifier.fillMaxWidth())
            CheckboxRow("Include prerelease versions", prerelease, { prerelease = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultButton(enabled = source != null && !busy, onClick = {
                    val feed = source ?: return@DefaultButton; val text = query.text.toString(); val preview = prerelease
                    browse { results = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).search(text, preview) } }
                }) { Text("Search") }
                if (busy) DefaultButton(onClick = { request?.cancel() }) { Text("Cancel") }
            }
            TextField(id, placeholder = { Text("Package ID") }, modifier = Modifier.fillMaxWidth())
            TextField(version, placeholder = { Text("Exact version") }, modifier = Modifier.fillMaxWidth())
            val declared = module?.packages?.firstOrNull { it.id.equals(id.text.toString(), true) }
            CheckboxRow("Generate projection for a new package", projection, { projection = it }, enabled = declared == null)
            if (declared != null) Text("Existing projection options are preserved (${declared.generateProjection}).")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DefaultButton(enabled = !busy && source != null && id.text.isNotBlank(), onClick = {
                    val feed = source ?: return@DefaultButton; val packageId = id.text.toString(); val preview = prerelease
                    browse { versions = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).versions(packageId, preview) } }
                }) { Text("Load versions") }
                DefaultButton(enabled = module != null && id.text.isNotBlank() && version.text.isNotBlank(), onClick = { edit(false) }) { Text(if (declared == null) "Install" else "Update") }
                DefaultButton(enabled = declared != null, onClick = { edit(true) }) { Text("Remove") }
            }
            if (versions.isNotEmpty()) Text("Select a version")
            // Long version lists scroll with the panel instead of requiring a second scroll surface.
        }
        items(versions, key = { "version:$it" }) { value -> DefaultButton(onClick = { version.edit { replace(0, length, value) } }) { Text(value) } }
        item {
            if (id.text.isNotBlank() && version.text.isNotBlank()) {
                runCatching { WinRTNuGetDependencies.declaration(id.text.toString(), version.text.toString(), projection) }.getOrNull()?.let {
                    Text("Declaration for packageReferences { }")
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(it) }
                }
            }
        }
        items(results, key = { "search:${it.id.lowercase()}" }) { result ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DefaultButton(onClick = { choose(result.id, result.version) }) { Text("${result.id} ${result.version}") }
                Text(result.authors); Text(result.description)
            }
        }
        item { Text("Declared / restored dependencies"); CheckboxRow("Show transitive and tooling packages", transitive, { transitive = it }) }
        items(inventory.packages.filter { transitive || it.direct }, key = { "installed:${it.id.lowercase()}:${it.version}" }) { pkg ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DefaultButton(onClick = { selected = pkg; choose(pkg.id, pkg.version) }) { Text("${pkg.id} ${pkg.version}") }
                Text(if (pkg.direct) "Direct dependency · projection ${pkg.projection}" else "Transitive or WinApp tooling dependency")
                Text(if (pkg.root != null && pkg.problems.isEmpty()) "Restored · build/packaging validation still required" else "Restore incomplete")
                pkg.source?.let { Text("Source: $it") }; pkg.problems.forEach { Text(it) }
            }
        }
        selected?.let { pkg ->
            item {
                Text("${pkg.id}: package contributions")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("win-x64", "win-x86", "win-arm64").forEach { value ->
                    RadioButtonRow(value, rid == value, { rid = value })
                } }
                pkg.root?.let { DefaultButton(onClick = { service.openFile(it.toString()) }) { Text("Package directory") } }
                Text("WinMD inputs and CopyLocal items use the existing toolkit contracts. Arbitrary MSBuild targets are not executed.")
                contributions?.errors?.forEach { Text(it) }
            }
            contributions?.let { info ->
                items(info.winmds, key = { "winmd:$it" }) { file -> Text("WinMD: $file") }
                items(info.copyLocal, key = { "payload:${it.first}:${it.second}" }) { (file, target) -> Text("CopyLocal: $file → $target") }
                items(info.nativeFiles, key = { "native:$it" }) { file -> Text("Native candidate: $file") }
                items(info.buildFiles, key = { "msbuild:$it" }) { file -> DefaultButton(onClick = { service.openFile(file.toString()) }) { Text("MSBuild: ${file.fileName}") } }
            }
            item { module?.packageLayouts?.forEach { layout ->
                DefaultButton(onClick = { WinRTGradleTasks.run(project, module!!, listOf(layout.taskName), "Validate ${layout.variant} package") { revision++ } }) { Text("Stage ${layout.variant}") }
            } }
        }
    }
}
