@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import org.jetbrains.jewel.ui.icon.PathIconKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.analysis.WinUIXamlSnapshotService
import io.github.composefluent.winrt.ide.hotreload.*
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.ui.*
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalogService
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.*
import org.jetbrains.jewel.ui.component.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WinUIXamlPreviewPanel(project: Project, staticFile: String? = null, treeOnly: Boolean = false) {
    val live = project.service<WinUIXamlHotReloadService>()
    val design = project.service<WinRTStaticPreviewService>()
    var static by remember { mutableStateOf(staticFile != null) }
    var showInspector by remember { mutableStateOf(staticFile == null) }
    val session: WinRTDevelopmentSession = if (static) design else live
    val state by session.state.collectAsState()
    val selection = project.service<WinUIXamlVisualInspectionSelection>()
    val rootKey by selection.root.collectAsState()
    val instance by selection.instance.collectAsState()
    val livePath by selection.path.collectAsState()
    var staticPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    val selectedPath = if (static) staticPath else livePath
    val modules by project.service<WinRTProjectService>().modules.collectAsState()
    val selectedModule = selectedWinRTModule(project)
    val module = staticFile?.let { path -> modules.filter { candidate -> runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(candidate.projectDirectory).toAbsolutePath().normalize())
    }.getOrDefault(false) }.maxByOrNull { it.projectDirectory.length } } ?: selectedModule
    val declarations by project.service<WinUIXamlSnapshotService>().state.collectAsState()
    val roots = state.inspectionRoots
    val root = if (static) roots.firstOrNull { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS } else
        remember(roots, rootKey) { selection.resolve(roots) }
    LaunchedEffect(root, static) {
        if (!static && root != null && rootKey != WinUIXamlVisualInspectionSelection.key(root))
            selection.selectRoot(WinUIXamlVisualInspectionSelection.key(root))
    }
    var launchName by remember(module?.projectDirectory) { mutableStateOf<String?>(null) }
    val launches = module?.hotReloadLaunches.orEmpty().filter { !static || it.previewExecutable.isNotBlank() }.distinctBy { it.executable }
    val launch = launches.firstOrNull { it.taskName == launchName } ?: launches.firstOrNull()
    val preferences = remember(project) { PropertiesComponent.getInstance(project) }
    val projectCodeKey = "winrt.preview.projectCode.${module?.projectDirectory.orEmpty()}"
    var preferProjectCode by remember(projectCodeKey) { mutableStateOf(preferences.getBoolean(projectCodeKey, true)) }
    val projectCode = preferProjectCode && launches.isNotEmpty()
    val designLaunch = if (projectCode) launch else module?.staticPreview?.launch()
    val catalogService = project.service<WinUIXamlCatalogService>()
    val catalogRevision by catalogService.changes.collectAsState()
    val resourceRevision by project.service<WinRTResourceIndex>().changes.collectAsState()
    LaunchedEffect(static, module, designLaunch, state.busy) {
        if (static && module != null && designLaunch != null) design.ensurePreview(module, designLaunch)
    }
    var automatic by remember { mutableStateOf(true) }
    var revision by remember { mutableLongStateOf(0) }
    var source by remember(module?.projectDirectory, staticFile) { mutableStateOf(staticFile ?: FileEditorManager.getInstance(project).selectedTextEditor
        ?.document?.let { FileDocumentManager.getInstance().getFile(it) }?.takeIf { it.extension.equals("xaml", true) }?.path.orEmpty()) }
    val files by produceState<List<String>>(emptyList(), module) {
        value = withContext(Dispatchers.IO) { (module?.xamlCompilations.orEmpty().flatMap { it.sourceRoots } +
            module?.sourceSets.orEmpty().flatMap { it.kotlinRoots }).distinct().flatMap { directory ->
            val path = Path.of(directory)
            if (!Files.isDirectory(path)) emptyList() else Files.walk(path).use { stream -> stream.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".xaml", true)
            }.limit(4096).map { it.toString().replace('\\', '/') }.toList() }
        }.distinct().sorted() }
        if (source.isEmpty()) source = value.firstOrNull { it.endsWith("MainWindow.xaml", true) } ?: value.firstOrNull().orEmpty()
    }
    val width = remember { TextFieldState("800") }; val height = remember { TextFieldState("600") }
    var theme by remember { mutableStateOf("Default") }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var manualRefresh by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf<WinUIXamlDesignDocument?>(null) }
    var submitted by remember(source, projectCode, static) {
        mutableStateOf<Triple<WinUIXamlInspectionRequest, Long?, Pair<Int, Long>>?>(null)
    }
    var showSettings by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    val customZoom = remember { TextFieldState("100") }
    var showSession by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var actualZoom by remember(source) { mutableFloatStateOf(1f) }
    var zoom by remember(source) { mutableStateOf<Float?>(null) }
    var fitSelection by remember(source) { mutableStateOf(false) }
    var resetViewport by remember(source) { mutableIntStateOf(0) }
    var lastView by remember(source, projectCode, static) { mutableStateOf<WinUIXamlVisualSnapshot?>(null) }
    val scope = rememberCoroutineScope()
    fun selectPath(path: List<Int>) { if (static) staticPath = path else selection.path.value = path }
    DisposableEffect(project, source) {
        val disposable = Disposer.newDisposable("WinRT preview document listener")
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document)
                if (file?.extension.equals("xaml", true)) revision++
            }
        }, disposable)
        onDispose { Disposer.dispose(disposable) }
    }
    LaunchedEffect(static, source, files, declarations, catalogRevision, resourceRevision, projectCode, if (automatic) revision else 0L, manualRefresh, width.text, height.text, theme) {
        prepared = null; failure = null
        if (static && source.isNotEmpty() && module != null) {
            // Debounce typing, not opening an already prepared document.
            if (revision > 0) delay(150)
            try {
                prepared = withContext(Dispatchers.IO) {
                    val catalog = (if (projectCode) catalogService.forFile(source) else catalogService.forDesigner(module))
                        ?: error("Waiting for the designer's SDK metadata…")
                    val documents = WinUIXamlPreviewSources(project, module, files, source)
                    val application = documents.application()
                    if (!projectCode) documents.stageAssets()
                    WinUIXamlDesignDocument.prepare(documents.read(source), application?.let(documents::read),
                        documents.target(source), application?.let(documents::target).orEmpty(),
                        resource = if (projectCode) null else documents::resolve,
                        designData = documents::resolve,
                        sdkType = if (projectCode) null else { uri, type -> catalog.resolve(uri, type) != null },
                        visualType = if (projectCode) null else { uri, type -> catalog.resolve(uri, type)?.let(catalog::isVisual) == true }) { uri, type, attribute ->
                        catalog.resolve(uri, type)?.let { catalog.members(it).any { member -> member.name == attribute && member.isEvent } } == true
                    }
                }
                notes = prepared!!.notes; staticPath = emptyList()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
        }
    }
    LaunchedEffect(root, state.connected, state.pid, static, prepared, width.text, height.text, theme, manualRefresh, resourceRevision) {
        if (static && root != null && state.connected && prepared != null) {
            val w = width.text.toString().toIntOrNull(); val h = height.text.toString().toIntOrNull()
            if (w == null || h == null || w !in 64..4096 || h !in 64..4096) failure = "Enter a width and height between 64 and 4096 DIP."
            else {
                val request = WinUIXamlInspectionRequest(root.className, root.resourcePath, 0, emptyList(), true,
                    prepared!!.markup, w, h, theme)
                val key = Triple(request, state.pid, manualRefresh to resourceRevision)
                // Metadata notifications can prepare the same document again. Keep
                // explicit refresh and asset changes, but avoid duplicate bitmaps.
                if (submitted != key) { session.inspect(request); submitted = key }
            }
        }
    }
    LaunchedEffect(root, selectedPath, state.connected, static, manualRefresh, instance) {
        if (root != null && state.connected && (!static || prepared != null && selectedPath.isNotEmpty())) {
            session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                if (static) 0 else instance.coerceIn(0, (root.instances - 1).coerceAtLeast(0)), selectedPath, !treeOnly && !static))
        }
    }
    LaunchedEffect(root, state.connected, static, automatic, selectedPath, instance, treeOnly) {
        if (!static && automatic && state.connected && root != null) while (true) {
            delay(1000)
            session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, instance, selectedPath, !treeOnly))
        }
    }
    val currentView = state.inspection.takeIf { failure == null && (!static || prepared != null) }
    LaunchedEffect(currentView) { if (currentView?.image != null) lastView = currentView }
    val view = currentView ?: lastView.takeIf { static }
    fun openSource(path: List<Int>) {
        val snapshot = view ?: return
        val node = snapshot.nodes.firstOrNull { it.path == path } ?: return
        val owner = snapshot.nodes.filter { it.path.size <= path.size && path.take(it.path.size) == it.path }
            .sortedByDescending { it.path.size }
            .firstNotNullOfOrNull { item -> session.sourcePath(item.typeName)?.let { it to item.path } }
        val file = owner?.first ?: if (static) source else root?.let { session.sourcePath(it) }
        if (file != null) scope.launch(Dispatchers.IO) { navigate(project, file, node, snapshot.nodes, owner?.second.orEmpty()) }
    }
    DisposableEffect(project, source, static, view?.nodes) {
        val disposable = Disposer.newDisposable("WinRT preview caret selection")
        if (static && view != null) {
            val nodes = view.nodes
            EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    val editor = event.editor
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    if (!file.path.equals(source.replace('\\', '/'), true)) return
                    val documents = PsiDocumentManager.getInstance(project)
                    documents.performForCommittedDocument(editor.document) {
                        if (Disposer.isDisposed(disposable) || editor.isDisposed) return@performForCommittedDocument
                        val xml = documents.getPsiFile(editor.document) as? XmlFile ?: return@performForCommittedDocument
                        val element = xml.findElementAt(editor.caretModel.offset.coerceAtMost((xml.textLength - 1).coerceAtLeast(0)))
                        val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return@performForCommittedDocument
                        WinUIXamlSourceSelection.node(tag, nodes)?.let { selectPath(it.path) }
                    }
                }
            }, disposable)
        }
        onDispose { Disposer.dispose(disposable) }
    }
    val status = if (view != null && static) when {
        !state.connected && !state.busy -> "Disconnected · last successful render"
        failure != null || state.inspectionError != null -> "Out of date · last successful render"
        !state.connected || prepared == null || state.inspecting -> "Updating preview…"
        else -> state.message
    } else state.message
    val error = failure ?: state.inspectionError
    val missingModule = staticFile != null && (module == null || designLaunch == null)
    fun startPreview() = session.start(module!!, (if (static) designLaunch else launch)!!, preview = static)
    val canStart = module != null && (if (static) designLaunch else launch) != null && !state.busy && state.pid == null

    @Composable fun inspector() {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Inspector", Modifier.weight(1f))
                if (!treeOnly) PreviewAction("Close inspector", "close") { showInspector = false }
            }
            val node = view?.nodes?.firstOrNull { it.path == selectedPath }
            Text(node?.let { it.name.ifEmpty { it.typeName.substringAfterLast('.') } } ?: "Select an element")
            Link("Go to XAML", enabled = node != null, onClick = { openSource(selectedPath) })
            VerticalSplitLayout(
                first = {
                    if (view != null) key(root?.className, root?.resourcePath, instance, static) {
                        VisualTree(view.nodes, selectedPath, ::selectPath, Modifier.fillMaxSize())
                    }
                },
                second = {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Effective properties")
                        view?.properties?.forEach { Text("${it.name}: ${it.value}") }
                    }
                },
                state = rememberSplitLayoutState(.55f),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
    @Composable fun canvas() {
        Box(Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Preview workspace" }, contentAlignment = Alignment.Center) {
            if (view != null && !treeOnly) key(source, resetViewport) {
                PreviewImage(view, selectedPath, ::selectPath, ::openSource, zoom, fitSelection, { actualZoom = it }, Modifier.fillMaxSize())
            } else Column(Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (staticFile != null) Text(Path.of(staticFile).fileName.toString())
                Text(when {
                    missingModule -> "Synchronize a Kotlin WinRT module with a Windows App SDK reference to preview this document."
                    state.busy -> "Preparing the preview. You can continue editing XAML while it builds."
                    error != null -> error
                    state.connected -> "Rendering this XAML document…"
                    static -> "Start the design host to render this document with WinUI."
                    else -> "Connect a development application to inspect its visual tree."
                })
                if (!state.connected && !state.busy) DefaultButton(enabled = canStart, onClick = ::startPreview) {
                    Text(if (static) "Retry preview" else "Start application")
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val popupWidth = (maxWidth - 24.dp).coerceIn(180.dp, 400.dp)
        val popupHeight = (maxHeight - 24.dp).coerceAtLeast(100.dp)
        Column(Modifier.fillMaxSize()) {
            if (staticFile == null && !treeOnly) Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButtonRow("Live Preview", !static, { static = false })
                RadioButtonRow("Static Preview", static, { static = true })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (treeOnly) inspector()
                else if (showInspector) HorizontalSplitLayout(
                    first = { canvas() }, second = { inspector() },
                    state = rememberSplitLayoutState(.65f),
                    firstPaneMinWidth = 80.dp, secondPaneMinWidth = 160.dp,
                    modifier = Modifier.fillMaxSize(),
                ) else canvas()
            }
            Divider(Orientation.Horizontal)
            // One fixed-height command strip; popups never participate in artboard measurement.
            Row(Modifier.fillMaxWidth().height(36.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!treeOnly) {
                    PreviewAction("Zoom out", "zoomOut", view != null && actualZoom > .125f) {
                        zoom = previewZoomStep(actualZoom, false); fitSelection = false
                    }
                    val percent = previewZoomLabel(actualZoom)
                    Dropdown(modifier = Modifier.width(112.dp).semantics { contentDescription = "Preview zoom" },
                        enabled = view != null, menuContent = {
                            selectableItem(selected = zoom == null && !fitSelection, onClick = {
                                fitSelection = false; zoom = null; resetViewport++
                            }) { Text("Fit all") }
                            selectableItem(selected = zoom == null && fitSelection,
                                enabled = view?.nodes?.any { it.path == selectedPath && it.bounds.width > 0 && it.bounds.height > 0 } == true,
                                onClick = { fitSelection = true; zoom = null; resetViewport++ }) { Text("Fit selection") }
                            selectableItem(selected = false, onClick = {
                                customZoom.setTextAndPlaceCursorAtEnd(previewZoomLabel(actualZoom).removeSuffix("%"))
                                showZoom = true
                            }) { Text("Custom zoom…") }
                            previewZoomLevels.forEach { level ->
                                selectableItem(selected = zoom == level, onClick = { zoom = level; fitSelection = false }) {
                                    Text(previewZoomLabel(level))
                                }
                            }
                        }) { Text(if (zoom == null) (if (fitSelection) "Sel " else "Fit ") + percent else percent) }
                    PreviewAction("Zoom in", "zoomIn", view != null && actualZoom < 8f) {
                        zoom = previewZoomStep(actualZoom, true); fitSelection = false
                    }
                    PreviewAction("Fit all", "fit", view != null, selected = zoom == null && !fitSelection) {
                        fitSelection = false; zoom = null; resetViewport++
                    }
                }
                Spacer(Modifier.width(8.dp))
                Divider(Orientation.Vertical, Modifier.height(20.dp))
                Spacer(Modifier.width(8.dp))
                PreviewAction("Preview settings", "settings", selected = showSettings) { showSettings = true }
                if (!treeOnly) PreviewAction("Show inspector", "inspector", selected = showInspector) { showInspector = !showInspector }
                PreviewAction("Refresh preview", "refresh", state.connected && !state.inspecting && !state.busy) { manualRefresh++ }
                PreviewAction("Preview session", "more", selected = showSession) { showSession = true }
            }
            Divider(Orientation.Horizontal)
            Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.busy || state.inspecting || (static && state.connected && prepared == null)) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Tooltip(tooltip = { Text(error ?: status) }, modifier = Modifier.weight(1f)) {
                    Text(error?.let { "Preview error · $it" } ?: status,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (state.busy) Link("Cancel", onClick = session::stop)
                else if (error != null) Link("Details", onClick = { showDiagnostics = true })
            }
        }
        if (showZoom) PreviewPopup("Custom zoom", popupWidth, popupHeight, { showZoom = false }) {
            Text("Zoom percentage (12.5–800%)")
            TextField(customZoom, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Zoom percentage" })
            val value = parsePreviewZoom(customZoom.text.toString())
            if (value == null) Text("Enter a percentage between 12.5 and 800.")
            DefaultButton(enabled = value != null, onClick = { zoom = value; fitSelection = false; showZoom = false }) { Text("Apply zoom") }
        }
        if (showSettings) PreviewPopup("Preview settings", popupWidth, popupHeight, { showSettings = false }) {
            if (static) {
                if (staticFile == null) WinRTChoice("XAML document", files.map { it to Path.of(it).fileName.toString() }, source) { source = it }
                val presets = listOf("360×640", "800×600", "1280×720", "1920×1080")
                WinRTChoice("Viewport preset", presets.map { it to it }, "${width.text}×${height.text}") { preset ->
                    val dimensions = preset.split('×')
                    width.setTextAndPlaceCursorAtEnd(dimensions[0]); height.setTextAndPlaceCursorAtEnd(dimensions[1])
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text("Width (DIP)"); TextField(width, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview width in DIP" }) }
                    Column(Modifier.weight(1f)) { Text("Height (DIP)"); TextField(height, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview height in DIP" }) }
                }
                Link("Swap width and height", onClick = {
                    val previousWidth = width.text.toString()
                    width.setTextAndPlaceCursorAtEnd(height.text.toString()); height.setTextAndPlaceCursorAtEnd(previousWidth)
                })
                WinRTChoice("Theme", listOf("Default", "Light", "Dark").map { it to it }, theme) { theme = it }
            } else {
                if (roots.size > 1) WinRTChoice("Window", roots.map { WinUIXamlVisualInspectionSelection.key(it) to it.className.substringAfterLast('.') }, root?.let(WinUIXamlVisualInspectionSelection::key)) { selection.selectRoot(it) }
                if ((root?.instances ?: 0) > 1) WinRTChoice("Instance", (0 until root!!.instances).map { it.toString() to "Instance ${it + 1}" }, instance.toString()) {
                    selection.instance.value = it.toInt(); selection.path.value = emptyList()
                }
            }
            CheckboxRow(if (static) "Update after XAML edits" else "Refresh automatically", automatic, { automatic = it })
        }
        if (showSession) PreviewPopup("Preview session", popupWidth, popupHeight, { showSession = false }) {
            WinRTModulePicker(project)
            if (!static || projectCode) WinRTChoice("Application", launches.map { it.taskName to it.taskName }, launch?.taskName) { launchName = it }
            if (static) CheckboxRow("Render project controls (requires compilation)", projectCode, {
                preferProjectCode = it; preferences.setValue(projectCodeKey, it, true)
            }, enabled = launches.isNotEmpty())
            if (!state.connected) Link(if (static) "Retry preview" else "Start application", enabled = canStart, onClick = { showSession = false; startPreview() })
            Link("Refresh", enabled = state.connected && !state.inspecting, onClick = { manualRefresh++; showSession = false })
            if (static && projectCode) Link("Rebuild project code", enabled = state.connected && !state.busy, onClick = {
                showSession = false; session.start(module!!, launch!!, restart = true, preview = true)
            })
            Link("Reconnect", enabled = !state.busy, onClick = { showSession = false; session.reconnect() })
            Link(if (state.busy) "Cancel build" else "Stop preview", enabled = state.pid != null || state.busy,
                onClick = { showSession = false; session.stop() })
            if (notes.isNotEmpty()) Link("Design-time notes", onClick = { showSession = false; showDiagnostics = true })
        }
        if (showDiagnostics) PreviewPopup("Preview details", popupWidth, popupHeight, { showDiagnostics = false }) {
            Text(error ?: status)
            notes.forEach { Text(it) }
        }
    }
}

@Composable
private fun PreviewAction(label: String, icon: String, enabled: Boolean = true, selected: Boolean? = null, action: () -> Unit) {
    Tooltip(tooltip = { Text(label) }) {
        val modifier = Modifier.size(28.dp).semantics { contentDescription = label }
        val key = PathIconKey("icons/preview/$icon.svg", WinUIXamlPreviewEditorProvider::class.java)
        if (selected == null) IconButton(onClick = action, enabled = enabled, modifier = modifier) {
            Icon(key, null, Modifier.size(16.dp).then(if (enabled) Modifier else Modifier.alpha(.4f)))
        } else SelectableIconButton(selected = selected, onClick = action, enabled = enabled, modifier = modifier) {
            Icon(key, null, Modifier.size(16.dp).then(if (enabled) Modifier else Modifier.alpha(.4f)))
        }
    }
}

@Composable
private fun PreviewPopup(title: String, width: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp,
    close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Popup(alignment = Alignment.Center, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(width).heightIn(max = maxHeight)
            .background(JewelTheme.globalColors.panelBackground)
            .border(1.dp, if (JewelTheme.isDark) Color(0xFF55575A) else Color(0xFFC9CDD3)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f))
                PreviewAction("Close $title", "close", action = close)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun VisualTree(nodes: List<WinUIXamlVisualNode>, selected: List<Int>, select: (List<Int>) -> Unit, modifier: Modifier) {
    val tree = remember(nodes) { visualInspectionTree(nodes) }
    val state = rememberTreeState()
    LaunchedEffect(selected) {
        state.selectedKeys = setOf(selected)
        state.openNodes(listOf(emptyList<Int>()) + (1 until selected.size).map { selected.take(it) })
    }
    LazyTree(tree, modifier = modifier, treeState = state, onSelectionChange = { elements ->
        elements.lastOrNull()?.data?.path?.let(select)
    }) { element ->
        val node = element.data
        Text(node.typeName.substringAfterLast('.') + node.name.takeIf(String::isNotEmpty)?.let { " · $it" }.orEmpty())
    }
}

@OptIn(ExperimentalJewelApi::class)
internal fun visualInspectionTree(nodes: List<WinUIXamlVisualNode>): Tree<WinUIXamlVisualNode> {
    val children = nodes.groupBy { if (it.path.isEmpty()) null else it.path.dropLast(1) }
    fun TreeGeneratorScope<WinUIXamlVisualNode>.append(parent: List<Int>?) {
        children[parent].orEmpty().forEach { node ->
            if (children[node.path].isNullOrEmpty()) addLeaf(node, node.path)
            else addNode(node, node.path) { append(node.path) }
        }
    }
    return TreeBuilder<WinUIXamlVisualNode>().apply { append(null) }.build()
}

@Composable
private fun PreviewImage(view: WinUIXamlVisualSnapshot, selected: List<Int>, select: (List<Int>) -> Unit, openSource: (List<Int>) -> Unit, zoom: Float?, fitSelection: Boolean, onZoom: (Float) -> Unit, modifier: Modifier) {
    val image = view.image ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value = withContext(Dispatchers.IO) { bgraImage(image).toComposeImageBitmap() }
    }
    val root = view.nodes.firstOrNull()?.bounds ?: return
    if (root.width <= 0 || root.height <= 0) return
    val target = if (fitSelection) view.nodes.firstOrNull { it.path == selected }?.bounds
        ?.takeIf { it.width > 0 && it.height > 0 } ?: root else root
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val effectiveZoom = previewScale(viewport.width, viewport.height, target.width, target.height, density, zoom).let { if (fitSelection) it.coerceAtMost(8f) else it }
    SideEffect { onZoom(effectiveZoom) }
    var pan by remember(zoom, fitSelection, if (fitSelection) selected else null) { mutableStateOf(Offset.Zero) }
    Canvas(modifier.onSizeChanged { viewport = it }.clipToBounds().semantics { contentDescription = "WinUI XAML preview. Double-click to open XAML. Drag to pan; use Fit to reset." }
        .pointerInput(zoom) { detectDragGestures { change, delta -> change.consume(); pan += delta } }
        .pointerInput(view.nodes, target, effectiveZoom, pan) {
          fun hit(point: Offset): WinUIXamlVisualNode? {
            val factor = effectiveZoom * density
            val origin = previewOrigin(size.width.toFloat(), size.height.toFloat(), target, factor) + pan
            return WinUIXamlVisualInspectionSelection.hit(view.nodes, (point.x - origin.x) / factor.toDouble(), (point.y - origin.y) / factor.toDouble())
          }
          detectTapGestures(onTap = { point -> hit(point)?.let { select(it.path) } },
              onDoubleTap = { point -> hit(point)?.let { select(it.path); openSource(it.path) } })
        }) {
        val factor = effectiveZoom * density
        val origin = previewOrigin(size.width.toFloat(), size.height.toFloat(), target, factor) + pan
        translate(origin.x, origin.y) { scale(factor, factor, Offset.Zero) {
            bitmap?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(root.width.toInt(), root.height.toInt())) }
            view.nodes.firstOrNull { it.path == selected }?.bounds?.let { r ->
                drawRect(Color(0xFF4BA3FF), Offset(r.x.toFloat(), r.y.toFloat()),
                    Size(r.width.toFloat(), r.height.toFloat()), style = Stroke(2.dp.toPx() / factor))
            }
        } }
    }
}

/** Zoom is measured in DIP; fit and explicit zoom share draw/hit-test coordinates. */
internal fun previewScale(width: Int, height: Int, rootWidth: Double, rootHeight: Double, density: Float, zoom: Float?): Float =
    zoom ?: if (width <= 0 || height <= 0 || rootWidth <= 0 || rootHeight <= 0) 1f
    else minOf(width / rootWidth, height / rootHeight).toFloat() / density

internal fun bgraImage(image: WinUIXamlVisualImage): BufferedImage {
    require(image.width in 1..768 && image.height in 1..768 && image.pixels.size == image.width * image.height * 4)
    val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
    val pixels = (bitmap.raster.dataBuffer as DataBufferInt).data
    pixels.indices.forEach { index -> val offset = index * 4; val bytes = image.pixels
        pixels[index] = ((bytes[offset + 3].toInt() and 255) shl 24) or ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or (bytes[offset].toInt() and 255)
    }
    return bitmap
}

private fun navigate(project: Project, path: String, node: WinUIXamlVisualNode, nodes: List<WinUIXamlVisualNode>, component: List<Int>) {
    val location = ReadAction.compute<Pair<com.intellij.openapi.vfs.VirtualFile, Int>?, RuntimeException> {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return@compute null
        val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
        val tag = WinUIXamlSourceSelection.tag(xml, node, nodes, component)
        file to (tag ?: xml.rootTag)?.textOffset.orEmptyOffset()
    }
    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed && location != null)
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, location.first, location.second), true) }
}
private fun Int?.orEmptyOffset(): Int = this ?: 0
