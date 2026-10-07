package io.github.composefluent.winrt.ide.nuget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.ui.*
import kotlinx.coroutines.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WinRTNuGetPanel(project: Project) {
    val service = project.service<WinRTProjectService>()
    val module = selectedWinRTModule(project)
    val dependencyRevision by service.dependencyRevision.collectAsState()
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var searchRevision by remember { mutableIntStateOf(0) }
    val query = remember(module?.projectDirectory) { TextFieldState() }
    var sources by remember { mutableStateOf<List<WinRTNuGetSource>>(emptyList()) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    val source = sources.firstOrNull { it.name == sourceName } ?: sources.firstOrNull()
    var inventory by remember { mutableStateOf(WinRTNuGetInventory(emptyList(), emptyList())) }
    var results by remember(module?.projectDirectory) { mutableStateOf<List<WinRTNuGetSearchResult>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var packageId by remember(module?.projectDirectory) { mutableStateOf("") }
    var version by remember(module?.projectDirectory) { mutableStateOf("") }
    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var details by remember { mutableStateOf<WinRTNuGetPackageDetails?>(null) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var contributions by remember { mutableStateOf<WinRTNuGetContributions?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loadingDetails by remember { mutableStateOf(false) }
    var prerelease by remember { mutableStateOf(false) }
    var projection by remember { mutableStateOf(false) }
    var transitive by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Browse") }
    var rid by remember { mutableStateOf("win-x64") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableLongStateOf(0) }
    var updates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var checkingUpdates by remember { mutableStateOf(false) }
    val installed = inventory.packages.firstOrNull { it.id.equals(packageId, true) }
    val declared = module?.packages?.firstOrNull { it.id.equals(packageId, true) }
    val searchResult = results.firstOrNull { it.id.equals(packageId, true) }
    var manual by remember { mutableStateOf(false) }
    val manualId = remember { TextFieldState() }

    LaunchedEffect(module, revision, dependencyRevision) {
        inventory = WinRTNuGetInventory(emptyList(), emptyList()); sources = emptyList(); updates = emptyMap()
        module?.let { current ->
            try {
                val result = withContext(Dispatchers.IO) {
                    WinRTNuGetInventoryReader.read(current) to WinRTNuGetSources.read(Path.of(current.nuGetConfigDirectory.ifEmpty { current.projectDirectory }))
                }
                inventory = result.first; sources = result.second; failure = null
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
        }
    }
    fun search(append: Boolean = false) {
        val feed = source ?: return
        val text = query.text.toString(); val preview = prerelease; val offset = if (append) results.size else 0
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true; failure = null
            try {
                val page = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(feed).searchPage(text, preview, offset) }
                results = (if (append) results + page.packages else page.packages).distinctBy { it.id.lowercase() }; total = page.total
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
            finally { if (generation == searchGeneration) searching = false }
        }
    }
    LaunchedEffect(module?.projectDirectory, source?.address, prerelease, tab, searchRevision, query.text) {
        searchJob?.cancel()
        if (tab == "Browse") { delay(350); search() }
    }
    LaunchedEffect(source?.address, packageId, prerelease) {
        versions = emptyList()
        if (source != null && packageId.isNotEmpty()) try {
            val choices = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(source).versions(packageId, prerelease) }
            versions = choices
            if (version.isEmpty()) version = choices.firstOrNull().orEmpty()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { detailsError = error.message }
    }
    LaunchedEffect(source?.address, packageId, version) {
        details = null; detailsError = null
        if (source != null && packageId.isNotEmpty() && version.isNotEmpty()) {
            loadingDetails = true
            try { details = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(source).details(packageId, version) } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { detailsError = "Version details unavailable: ${error.message}" }
            finally { loadingDetails = false }
        }
    }
    LaunchedEffect(installed, rid) {
        contributions = null
        try { contributions = installed?.let { pkg -> withContext(Dispatchers.IO) { WinRTNuGetInventoryReader.contributions(pkg, rid) } } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { detailsError = error.message }
    }
    LaunchedEffect(tab, inventory, source?.address, prerelease) {
        if (tab == "Updates" && source != null) {
            checkingUpdates = true; failure = null
            try {
                updates = runInterruptible(Dispatchers.IO) {
                    val browser = WinRTNuGetBrowser(source)
                    inventory.packages.filter { it.direct }.mapNotNull { pkg ->
                        browser.versions(pkg.id, prerelease).firstOrNull()?.takeIf { WinRTNuGetVersion.compare(it, pkg.version) > 0 }?.let { pkg.id.lowercase() to it }
                    }.toMap()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
            finally { checkingUpdates = false }
        }
    }
    fun restore() {
        val current = module ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                service.restoreDependenciesAfterImport(current.projectDirectory)
                ExternalSystemUtil.refreshProject(com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(service.buildRootFor(current) ?: current.projectDirectory),
                    ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
            }
        }
    }
    fun edit(remove: Boolean) {
        val current = module ?: return; val id = packageId; val selectedVersion = version; val generate = projection
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching {
                val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("${current.projectDirectory.replace('\\', '/')}/build.gradle.kts")
                    ?: error("The module's Gradle build file is missing.")
                WinRTNuGetDependencies.apply(project, file, id, if (remove) null else selectedVersion, generate)
                FileDocumentManager.getInstance().getDocument(file)?.let { FileDocumentManager.getInstance().saveDocument(it) }
                failure = null; restore()
            }.onFailure { failure = it.message }
        }
    }
    fun select(id: String, selectedVersion: String) { packageId = id; version = selectedVersion; projection = module?.packages?.firstOrNull { it.id.equals(id, true) }?.generateProjection ?: false; manual = false }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WinRTModulePicker(project)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Browse", "Installed", "Updates").forEach { RadioButtonRow(it, tab == it, { tab = it }) }
        }
        TextField(query, placeholder = { Text(if (tab == "Browse") "Search NuGet packages" else "Filter packages") },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search NuGet packages" }.onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) { searchRevision++; true } else false
            })
        WinRTChoice("Package source", sources.map { it.name to it.name }, source?.name) { sourceName = it; results = emptyList(); total = 0 }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckboxRow("Include prerelease", prerelease, { prerelease = it })
            if (tab == "Installed") CheckboxRow("Show transitive", transitive, { transitive = it })
            DefaultButton(onClick = { revision++; searchRevision++ }) { Text("Refresh") }
            if (searching) DefaultButton(onClick = { searchJob?.cancel() }) { Text("Cancel search") }
        }
        if (module == null) Text("Sync Gradle to manage this project's NuGet dependencies.")
        (inventory.errors + listOfNotNull(failure)).forEach { Text(it) }
        if (searching || checkingUpdates) Text(if (checkingUpdates) "Checking available updates…" else "Searching packages…")

        @Composable fun packageList(modifier: Modifier) {
            LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tab == "Browse") {
                    items(results, key = { it.id.lowercase() }) { pkg ->
                        PackageRow(pkg.id, pkg.version, pkg.description, pkg.authors,
                            inventory.packages.firstOrNull { it.id.equals(pkg.id, true) }?.let { "Installed ${it.version}" }, packageId.equals(pkg.id, true)) { select(pkg.id, pkg.version) }
                    }
                    if (results.isEmpty() && !searching) item { Text("No packages found. Change the query or package source.") }
                    if (results.size < total) item { DefaultButton(enabled = !searching, onClick = { search(true) }) { Text("Load more (${results.size} / $total)") } }
                } else {
                    val packages = inventory.packages.filter { pkg -> (transitive || pkg.direct) && pkg.id.contains(query.text.toString(), true) &&
                        (tab != "Updates" || updates.containsKey(pkg.id.lowercase())) }
                    items(packages, key = { "${it.id.lowercase()}:${it.version}" }) { pkg ->
                        val update = updates[pkg.id.lowercase()]
                        PackageRow(pkg.id, if (tab == "Updates") "${pkg.version} → $update" else pkg.version, pkg.problems.joinToString(),
                            if (pkg.direct) "Direct dependency" else "Transitive dependency", null, packageId.equals(pkg.id, true)) { select(pkg.id, update ?: pkg.version) }
                    }
                    if (packages.isEmpty() && !checkingUpdates) item { Text(if (tab == "Updates") "No updates available from this source." else "No installed packages match this filter.") }
                }
            }
        }
        @Composable fun packageDetails(modifier: Modifier) {
            Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (packageId.isEmpty()) Text("Select a package to see its versions, dependencies and installation options.") else {
                    Text(packageId)
                    installed?.let { Text("Installed: ${it.version} · ${if (it.direct) "direct" else "transitive"}") }
                    WinRTChoice("Version", (listOf(version) + versions).filter(String::isNotEmpty).distinct().map { it to it }, version) { version = it }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val same = declared?.version?.equals(version, true) == true
                        DefaultButton(enabled = module != null && version.isNotEmpty() && !same, onClick = { edit(false) }) {
                            Text(when { declared == null -> "Install"; same -> "Installed"; WinRTNuGetVersion.compare(version, declared.version) < 0 -> "Downgrade"; else -> "Update" })
                        }
                        if (declared != null) DefaultButton(onClick = { edit(true) }) { Text("Uninstall") }
                    }
                    if (declared == null) CheckboxRow("Generate Kotlin projections", projection, { projection = it })
                    else Text(if (declared.generateProjection) "Kotlin projection enabled" else "Runtime package only")
                    if (loadingDetails) Text("Loading version details…")
                    detailsError?.let { Text(it) }
                    Text(details?.description ?: searchResult?.description.orEmpty())
                    (details?.authors ?: searchResult?.authors)?.takeIf(String::isNotBlank)?.let { Text("Authors: $it") }
                    searchResult?.downloads?.takeIf { it > 0 }?.let { Text("Downloads: ${java.text.NumberFormat.getIntegerInstance().format(it)}") }
                    details?.published?.takeIf(String::isNotEmpty)?.let { Text("Published: ${it.substringBefore('T')}") }
                    (details?.projectUrl ?: searchResult?.projectUrl)?.takeIf { it.startsWith("https://") }?.let { url -> Link("Project website", onClick = { BrowserUtil.browse(url) }) }
                    details?.license?.takeIf(String::isNotEmpty)?.let { license ->
                        if (license.startsWith("https://")) Link("License", onClick = { BrowserUtil.browse(license) }) else Text("License: $license")
                    }
                    details?.deprecation?.takeIf(String::isNotEmpty)?.let { Text("Deprecated: $it") }
                    details?.dependencies?.let { groups ->
                        Text("Dependencies")
                        if (groups.isEmpty()) Text("No dependencies")
                        groups.forEach { group ->
                            Text(group.framework.ifEmpty { "All target frameworks" })
                            group.dependencies.forEach { (id, range) -> Link("$id  $range", onClick = { query.edit { replace(0, length, id) }; tab = "Browse"; select(id, "") }) }
                        }
                    }
                    installed?.let { pkg -> WinRTDetails("restored package contents") {
                        WinRTChoice("Architecture", listOf("win-x64", "win-x86", "win-arm64").map { it to it }, rid) { rid = it }
                        pkg.root?.let { Link("Open package folder", onClick = { service.openFile(it.toString()) }) }
                        contributions?.let { content ->
                            content.errors.forEach { Text(it) }; content.winmds.forEach { Link(it.fileName.toString(), onClick = { service.openFile(it.toString()) }) }
                            content.nativeFiles.forEach { Text("Native: ${it.fileName}") }
                            content.copyLocal.forEach { (file, target) -> Text("$target ← ${file.fileName}") }
                        }
                    } }
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 640.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                packageList(Modifier.weight(1f).fillMaxHeight()); packageDetails(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                packageList(Modifier.weight(1f).fillMaxWidth())
                if (packageId.isNotEmpty()) packageDetails(Modifier.weight(1f).fillMaxWidth())
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DefaultButton(enabled = module != null, onClick = { restore() }) { Text("Restore packages") }
            Link("Package ID…", onClick = { manual = !manual })
            module?.let { current -> Link("Gradle", onClick = { service.openFile("${current.projectDirectory}/build.gradle.kts") })
                if (current.nuGetConfigFile.isNotEmpty()) Link("Package sources…", onClick = { service.openFile(current.nuGetConfigFile) }) }
        }
        if (manual) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(manualId, placeholder = { Text("Exact package ID") }, modifier = Modifier.weight(1f))
            DefaultButton(enabled = manualId.text.isNotBlank(), onClick = { select(manualId.text.toString().trim(), "") }) { Text("Find versions") }
        }
    }
}

@Composable
private fun PackageRow(id: String, version: String, description: String, authors: String, installed: String?, selected: Boolean, onSelect: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(if (selected) JewelTheme.globalColors.panelBackground.copy(alpha = .6f) else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(onClick = onSelect).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioButtonRow(id, selected, onSelect, modifier = Modifier.fillMaxWidth())
        Text(version)
        if (authors.isNotEmpty()) Text(authors, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (description.isNotEmpty()) Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis)
        installed?.let { Text(it) }
    }
}
