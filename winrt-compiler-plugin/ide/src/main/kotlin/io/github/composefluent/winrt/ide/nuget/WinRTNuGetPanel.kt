package io.github.composefluent.winrt.ide.nuget

import androidx.compose.foundation.layout.*
import org.jetbrains.jewel.ui.Orientation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
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
    var readme by remember { mutableStateOf<String?>(null) }
    var readmeError by remember { mutableStateOf<String?>(null) }
    var loadingReadme by remember { mutableStateOf(false) }
    var prerelease by remember { mutableStateOf(false) }
    var projection by remember { mutableStateOf(false) }
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
                if (generation == searchGeneration) {
                    results = (if (append) results + page.packages else page.packages).distinctBy { it.id.lowercase() }; total = page.total
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation == searchGeneration) failure = error.message }
            finally { if (generation == searchGeneration) searching = false }
        }
    }
    LaunchedEffect(module?.projectDirectory, source?.address, prerelease, tab, searchRevision, query.text) {
        searchGeneration++; searchJob?.cancel(); searching = false; failure = null
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
    LaunchedEffect(source?.address, packageId, version, installed?.root) {
        readme = null; readmeError = null; loadingReadme = false
        if (source != null && packageId.isNotEmpty() && version.isNotEmpty()) {
            loadingReadme = true
            try {
                val restored = installed?.takeIf { WinRTNuGetVersion.compare(it.version, version) == 0 }?.root
                readme = runInterruptible(Dispatchers.IO) { WinRTNuGetBrowser(source).readme(packageId, version, restored) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { readmeError = "README unavailable: ${error.message}" }
            finally { loadingReadme = false }
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

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            WinRTTabs(listOf("Browse", "Installed", "Updates"), tab, Modifier.weight(1f)) { tab = it }
            WinRTModulePicker(project, Modifier.widthIn(max = 330.dp).weight(1f, fill = false), compact = true)
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextField(query, placeholder = { Text(if (tab == "Browse") "Search NuGet packages" else "Filter packages") },
            modifier = Modifier.widthIn(min = 180.dp, max = 340.dp).semantics { contentDescription = "Search NuGet packages" }.onPreviewKeyEvent {
                if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) { searchRevision++; true } else false
            })
            OutlinedButton(onClick = { revision++; searchRevision++ }) { Text("Refresh") }
            CheckboxRow("Include prerelease", prerelease, { prerelease = it }, Modifier.heightIn(min = 28.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Package source:")
                WinRTComboBox("Package source", sources.map { it.name to it.name }, source?.name, Modifier.width(170.dp)) {
                    sourceName = it; results = emptyList(); total = 0
                }
            }
            if (searching) Link("Cancel search", onClick = { searchJob?.cancel() })
        }
        Divider(Orientation.Horizontal)
        if (module == null) Text("Sync Gradle to manage this project's NuGet dependencies.", Modifier.padding(12.dp))
        (inventory.errors + listOfNotNull(failure)).forEach { Text(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (searching || checkingUpdates) Text(if (checkingUpdates) "Checking available updates…" else "Searching packages…", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        @Composable fun packageList(modifier: Modifier) {
            val packages = if (tab == "Browse") results.map { pkg ->
                WinRTNuGetListPackage(pkg.id, pkg.version, pkg.description, pkg.authors, inventory.packages.firstOrNull { it.id.equals(pkg.id, true) }?.version)
            } else inventory.packages.filter { pkg -> pkg.id.contains(query.text.toString(), true) &&
                (tab != "Updates" || pkg.direct && updates.containsKey(pkg.id.lowercase())) }.map { pkg ->
                val available = updates[pkg.id.lowercase()]
                WinRTNuGetListPackage(pkg.id, if (tab == "Updates") "${pkg.version} → $available" else pkg.version, pkg.problems.joinToString(),
                    group = if (pkg.direct) "Top-level packages" else "Transitive packages")
            }
            WinRTNuGetPackageList(packages, packageId, modifier, footer = {
                if (packages.isEmpty() && !searching && !checkingUpdates) Text(when (tab) {
                    "Browse" -> "No packages found. Change the query or package source."
                    "Updates" -> "No updates available from this source."
                    else -> "No installed packages match this filter."
                }, Modifier.padding(12.dp))
                if (tab == "Browse" && results.size < total) OutlinedButton(enabled = !searching, onClick = { search(true) }, modifier = Modifier.padding(12.dp)) { Text("Load more (${results.size} / $total)") }
            }) { pkg -> select(pkg.id, updates[pkg.id.lowercase()]?.takeIf { tab == "Updates" } ?: pkg.version) }
        }
        @Composable fun packageDetails(modifier: Modifier) {
            Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (packageId.isEmpty()) Text("Select a package to see its versions, dependencies and installation options.") else {
                    Text(packageId, fontWeight = FontWeight.SemiBold)
                    installed?.let { Text("Installed: ${it.version} · ${if (it.direct) "direct" else "transitive"}") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Version:")
                        WinRTComboBox("Version", (listOf(version) + versions).filter(String::isNotEmpty).distinct().map { it to it }, version, Modifier.weight(1f)) { version = it }
                        val same = declared?.version?.equals(version, true) == true
                        DefaultButton(enabled = module != null && version.isNotEmpty() && !same, onClick = { edit(false) }) {
                            Text(when { declared == null -> "Install"; same -> "Installed"; WinRTNuGetVersion.compare(version, declared.version) < 0 -> "Downgrade"; else -> "Update" })
                        }
                    }
                    if (declared != null) OutlinedButton(onClick = { edit(true) }) { Text("Uninstall") }
                    if (declared == null) CheckboxRow("Generate Kotlin projections", projection, { projection = it })
                    else Text(if (declared.generateProjection) "Kotlin projection enabled" else "Runtime package only")
                    Divider(Orientation.Horizontal)
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
                        Divider(Orientation.Horizontal)
                        Text("Dependencies", fontWeight = FontWeight.SemiBold)
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
                    Divider(Orientation.Horizontal)
                    WinRTNuGetReadme(project, readme, loadingReadme, readmeError)
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 640.dp) HorizontalSplitLayout(
                first = { packageList(Modifier.fillMaxSize()) },
                second = { packageDetails(Modifier.fillMaxSize()) },
                state = rememberSplitLayoutState(.62f),
                firstPaneMinWidth = 280.dp,
                secondPaneMinWidth = 280.dp,
                modifier = Modifier.fillMaxSize(),
            ) else if (packageId.isEmpty()) packageList(Modifier.fillMaxSize()) else Column(Modifier.fillMaxSize()) {
                Link("← Packages", onClick = { packageId = "" }, modifier = Modifier.padding(12.dp))
                packageDetails(Modifier.weight(1f).fillMaxWidth())
            }
        }
        Divider(Orientation.Horizontal)
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = module != null, onClick = { restore() }) { Text("Restore") }
            Link("Package ID…", onClick = { manual = !manual })
            module?.let { current -> Link("Gradle", onClick = { service.openFile("${current.projectDirectory}/build.gradle.kts") })
                if (current.nuGetConfigFile.isNotEmpty()) Link("Package sources…", onClick = { service.openFile(current.nuGetConfigFile) }) }
        }
        if (manual) Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(manualId, placeholder = { Text("Exact package ID") }, modifier = Modifier.weight(1f))
            DefaultButton(enabled = manualId.text.isNotBlank(), onClick = { select(manualId.text.toString().trim(), "") }) { Text("Find versions") }
        }
    }
}

@Composable
internal fun WinRTNuGetReadme(project: Project, content: String?, loading: Boolean, error: String?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("README", fontWeight = FontWeight.SemiBold)
        when {
            loading -> Text("Loading README…")
            error != null -> Text(error)
            content == null -> Text("This package version does not include a README.")
            else -> ProvideMarkdownStyling(project) {
                Markdown(content, onUrlClick = { url ->
                    if (url.startsWith("https://") || url.startsWith("http://")) BrowserUtil.browse(url)
                })
            }
        }
    }
}
