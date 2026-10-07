package io.github.composefluent.winrt.ide.nuget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.composefluent.winrt.ide.ui.*
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
    var tab by remember { mutableStateOf("browse") }
    var manualPackage by remember(module?.projectDirectory) { mutableStateOf(false) }
    var rid by remember { mutableStateOf("win-x64") }
    var request by remember { mutableStateOf<Job?>(null) }
    var requestGeneration by remember { mutableLongStateOf(0) }

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
    LaunchedEffect(module?.projectDirectory, source?.address) { ++requestGeneration; request?.cancel(); busy = false; results = emptyList(); versions = emptyList() }
    fun browse(action: suspend () -> Unit) {
        val generation = ++requestGeneration
        request?.cancel()
        request = scope.launch {
            busy = true; failure = null
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
            finally { if (generation == requestGeneration) busy = false }
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
        ++requestGeneration; request?.cancel(); busy = false
        id.edit { replace(0, length, packageId) }; version.edit { replace(0, length, packageVersion) }; versions = emptyList()
        selected = inventory.packages.firstOrNull { it.id.equals(packageId, true) }
        source?.let { feed ->
            val preview = prerelease
            browse { versions = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).versions(packageId, preview) } }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NuGet packages")
                if (modules.isEmpty()) Text("Synchronize a Kotlin WinRT project to manage its packages.")
                WinRTModulePicker(project)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButtonRow("Browse", tab == "browse", { tab = "browse" })
                    RadioButtonRow("Installed", tab == "installed", { tab = "installed" })
                }
                TextField(query, placeholder = { Text(if (tab == "browse") "Search packages" else "Filter installed packages") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search packages" })
                if (tab == "browse") {
                    WinRTChoice("Package source", sources.map { it.name to it.name }, source?.name) { sourceName = it }
                    CheckboxRow("Include prerelease versions", prerelease, { prerelease = it })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DefaultButton(enabled = source != null && !busy, onClick = {
                            val feed = source ?: return@DefaultButton; val text = query.text.toString(); val preview = prerelease
                            browse { results = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).search(text, preview) } }
                        }) { Text("Search") }
                        if (busy) DefaultButton(onClick = { ++requestGeneration; request?.cancel(); busy = false }) { Text("Cancel") }
                        Link("Enter a package ID…", onClick = { manualPackage = true })
                    }
                } else CheckboxRow("Show transitive packages", transitive, { transitive = it })
                (inventory.errors + listOfNotNull(failure)).forEach { Text(it) }
                if (busy) Text("Loading packages…")
            }
        }
        if (id.text.isNotBlank() || manualPackage) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manualPackage) TextField(id, placeholder = { Text("Package ID") },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Package ID" }) else Text(id.text.toString())
                val declared = module?.packages?.firstOrNull { it.id.equals(id.text.toString(), true) }
                val choices = (listOf(version.text.toString()) + versions).filter(String::isNotBlank).distinct()
                if (manualPackage) TextField(version, placeholder = { Text("Exact version") }, modifier = Modifier.fillMaxWidth())
                else WinRTChoice("Version", choices.map { it to it }, version.text.toString()) { value -> version.edit { replace(0, length, value) } }
                selected?.let { pkg ->
                    Text(if (pkg.root != null && pkg.problems.isEmpty()) "Installed: ${pkg.version}" else "Restore incomplete")
                    pkg.problems.forEach { Text(it) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultButton(enabled = module != null && id.text.isNotBlank() && version.text.isNotBlank(), onClick = { edit(false) }) {
                        Text(if (declared == null) "Install" else "Update")
                    }
                    if (declared != null) DefaultButton(onClick = { edit(true) }) { Text("Remove") }
                }
                WinRTDetails("advanced package options") {
                    TextField(id, placeholder = { Text("Package ID") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Package ID" })
                    TextField(version, placeholder = { Text("Exact version") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Exact version" })
                    CheckboxRow("Generate Kotlin projection", projection, { projection = it }, enabled = declared == null)
                    DefaultButton(enabled = !busy && source != null, onClick = {
                        val feed = source ?: return@DefaultButton; val packageId = id.text.toString(); val preview = prerelease
                        browse { versions = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).versions(packageId, preview) } }
                    }) { Text("Refresh versions") }
                    runCatching { WinRTNuGetDependencies.declaration(id.text.toString(), version.text.toString(), projection) }.getOrNull()?.let {
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(it) }
                    }
                }
                selected?.let { pkg ->
                    WinRTDetails("package contents") {
                        WinRTChoice("Architecture", listOf("win-x64", "win-x86", "win-arm64").map { it to it }, rid) { rid = it }
                        pkg.root?.let { DefaultButton(onClick = { service.openFile(it.toString()) }) { Text("Open package folder") } }
                        contributions?.let { info ->
                            info.errors.forEach { Text(it) }
                            info.winmds.forEach { Text("WinMD: $it") }
                            info.copyLocal.forEach { (file, target) -> Text("$target ← $file") }
                            info.nativeFiles.forEach { Text("Native: $it") }
                            info.buildFiles.forEach { file -> Link(file.fileName.toString(), onClick = { service.openFile(file.toString()) }) }
                        }
                    }
                }
            }
        }
        if (tab == "browse") {
            if (results.isEmpty() && !busy) item { Text("Search by package name, then select a result to choose its version and install it.") }
            items(results, key = { "search:${it.id.lowercase()}" }) { result ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RadioButtonRow("${result.id}  ${result.version}", id.text.toString().equals(result.id, true),
                        { choose(result.id, result.version) }, modifier = Modifier.fillMaxWidth())
                    Text(result.description, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        } else {
            val visible = inventory.packages.filter { (transitive || it.direct) && it.id.contains(query.text.toString(), true) }
            if (visible.isEmpty()) item { Text("No installed packages match this filter.") }
            items(visible, key = { "installed:${it.id.lowercase()}:${it.version}" }) { pkg ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RadioButtonRow("${pkg.id}  ${pkg.version}", id.text.toString().equals(pkg.id, true),
                        { choose(pkg.id, pkg.version) }, modifier = Modifier.fillMaxWidth())
                    Text(if (pkg.direct) "Direct dependency" else "Transitive dependency")
                }
            }
        }
        item {
            WinRTDetails("sources and build settings") {
                source?.let { feed ->
                    Text(feed.address)
                    if (feed.requiresProvider) Text("This source needs a NuGet credential environment variable for search. Restore uses its configured credential provider.")
                }
                module?.let { current ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DefaultButton(onClick = { restore(current) }) { Text("Restore packages") }
                        DefaultButton(onClick = { revision++ }) { Text("Refresh installed packages") }
                        Link("Gradle build file", onClick = { service.openFile("${current.projectDirectory}/build.gradle.kts") })
                        if (current.nuGetConfigFile.isNotEmpty()) Link("NuGet.Config", onClick = { service.openFile(current.nuGetConfigFile) })
                    }
                }
            }
        }
    }
}
