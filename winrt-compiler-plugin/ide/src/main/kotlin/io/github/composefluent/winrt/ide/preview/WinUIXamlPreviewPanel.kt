package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
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
    var showSettings by remember { mutableStateOf(false) }
    var zoom by remember(source) { mutableStateOf<Float?>(null) }
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
    LaunchedEffect(static, source, files, declarations, catalogRevision, resourceRevision, projectCode, if (automatic) revision else 0L, manualRefresh, state.connected, width.text, height.text, theme) {
        prepared = null; failure = null
        if (static && state.connected && source.isNotEmpty() && module != null) {
            delay(350)
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
    LaunchedEffect(root, state.connected, static, prepared, width.text, height.text, theme) {
        if (static && root != null && state.connected && prepared != null) {
            val w = width.text.toString().toIntOrNull(); val h = height.text.toString().toIntOrNull()
            if (w == null || h == null || w !in 64..4096 || h !in 64..4096) failure = "Enter a width and height between 64 and 4096 DIP."
            else session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, 0, emptyList(), true,
                prepared!!.markup, w, h, theme))
        }
    }
    LaunchedEffect(root, selectedPath, state.connected, static, manualRefresh, instance) {
        if (root != null && state.connected && (!static || prepared != null)) {
            session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                if (static) 0 else instance.coerceIn(0, (root.instances - 1).coerceAtLeast(0)), selectedPath, !treeOnly))
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
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
      val controlsHeight = if (view == null) maxHeight else maxHeight * (if (showSettings) 0.5f else 0.4f)
      Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
       // Keep settings reachable at large IDE zoom levels without measuring the
       // weighted image/tree inside an unbounded scrolling column.
       Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!treeOnly && staticFile == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButtonRow("Live Preview", !static, { static = false }); RadioButtonRow("Static Preview", static, { static = true })
        }
        if ((!state.connected && (!static || staticFile == null)) || static && projectCode && showSettings) {
            @Composable fun buildOptions() {
                WinRTModulePicker(project)
                if (!static || projectCode) WinRTChoice("Application", launches.map { it.taskName to it.taskName }, launch?.taskName) { launchName = it }
            }
            if (staticFile != null) WinRTDetails("project code options") { buildOptions() } else buildOptions()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!state.connected) DefaultButton(enabled = module != null && (if (static) designLaunch else launch) != null && !state.busy && state.pid == null,
                onClick = { session.start(module!!, (if (static) designLaunch else launch)!!, preview = static) }) { Text(if (static) "Retry preview" else "Start application") }
            if (static && projectCode && state.connected && showSettings) OutlinedButton(enabled = !state.busy,
                onClick = { session.start(module!!, launch!!, restart = true, preview = true) }) { Text("Build project code") }
            OutlinedButton(enabled = state.connected && !state.inspecting, onClick = { manualRefresh++ }) { Text("Refresh") }
            if ((staticFile == null || state.pid != null) && showSettings) OutlinedButton(enabled = !state.busy, onClick = session::reconnect) { Text("Reconnect") }
            if (state.pid != null || state.busy) OutlinedButton(onClick = session::stop) { Text(if (state.busy) "Cancel" else if (static) "Stop preview" else "Stop application") }
        }
        Text(state.message)
        if (view != null && static && (!state.connected || failure != null || state.inspectionError != null || prepared == null || state.inspecting))
            Text(when {
                !state.connected && !state.busy -> "Preview disconnected — showing the last successful render."
                failure != null || state.inspectionError != null -> "Preview out of date — showing the last successful render."
                else -> "Updating preview — showing the last successful render."
            })
        state.inspectionError?.let { Text(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Hide settings" else "Settings") }
            if (!treeOnly && view != null) {
                OutlinedButton(onClick = { zoom = null; resetViewport++ }) { Text("Fit") }
                OutlinedButton(modifier = Modifier.semantics { contentDescription = "Zoom out" }, onClick = { zoom = ((zoom ?: 1f) / 1.25f).coerceAtLeast(0.1f) }) { Text("−") }
                OutlinedButton(onClick = { zoom = 1f }) { Text(zoom?.let { "${(it * 100).toInt()}%" } ?: "100%") }
                OutlinedButton(modifier = Modifier.semantics { contentDescription = "Zoom in" }, onClick = { zoom = ((zoom ?: 1f) * 1.25f).coerceAtMost(8f) }) { Text("+") }
            }
        }
        if (staticFile != null && (module == null || designLaunch == null))
            Text("Synchronize a Kotlin WinRT module with a Windows App SDK reference to preview this document.")
        if (static) {
            if (showSettings) CheckboxRow("Render project controls (requires compilation)", projectCode, {
                preferProjectCode = it; preferences.setValue(projectCodeKey, it, true)
            }, enabled = launches.isNotEmpty())
            if (staticFile == null) WinRTChoice("XAML document", files.map { it to (module?.projectDirectory?.let { dir -> it.removePrefix(dir.replace('\\', '/') + "/") } ?: it) }, source) { source = it }
            else if (view == null || showSettings) Text(Path.of(staticFile).fileName.toString())
            if (showSettings) {
              val presets = listOf("360×640", "800×600", "1280×720", "1920×1080")
              WinRTChoice("Viewport preset", presets.map { it to it }, "${width.text}×${height.text}") { preset ->
                  val dimensions = preset.split('×')
                  width.setTextAndPlaceCursorAtEnd(dimensions[0]); height.setTextAndPlaceCursorAtEnd(dimensions[1])
              }
              OutlinedButton(onClick = {
                  val previousWidth = width.text.toString()
                  width.setTextAndPlaceCursorAtEnd(height.text.toString()); height.setTextAndPlaceCursorAtEnd(previousWidth)
              }) { Text("Swap width and height") }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Text("Width (DIP)"); TextField(width, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview width in DIP" }) }
                Column(Modifier.weight(1f)) { Text("Height (DIP)"); TextField(height, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview height in DIP" }) }
              }
              WinRTChoice("Theme", listOf("Default", "Light", "Dark").map { it to it }, theme) { theme = it }
              notes.forEach { Text(it) }
            }
            if (notes.isNotEmpty() && showSettings) Text("Design values and control defaults replace compiled bindings and event handlers.")
        } else {
            if (roots.size > 1) WinRTChoice("Window", roots.map { WinUIXamlVisualInspectionSelection.key(it) to it.className.substringAfterLast('.') }, root?.let(WinUIXamlVisualInspectionSelection::key)) { selection.selectRoot(it) }
            if ((root?.instances ?: 0) > 1) WinRTChoice("Instance", (0 until root!!.instances).map { it.toString() to "Instance ${it + 1}" }, instance.toString()) {
                selection.instance.value = it.toInt(); selection.path.value = emptyList()
            }
        }
        if (showSettings) CheckboxRow(if (static) "Update after XAML edits" else "Refresh automatically", automatic, { automatic = it })
        if (staticFile != null) CheckboxRow("Visual Tree and properties", showInspector, { showInspector = it })
        failure?.let { Text(it) }
        if (view == null) Text(when {
            state.busy -> "Preparing the preview. You can continue editing XAML while it builds."
            static && (failure != null || state.inspectionError != null) -> "This document could not be rendered. Fix the reported XAML or resource error, then refresh."
            static && state.connected -> "Rendering this XAML document…"
            static -> "Start the design host to render this document with WinUI."
            else -> "Connect a development application to inspect its actual visual tree."
        })
        if (view != null && showInspector) {
            val node = view.nodes.firstOrNull { it.path == selectedPath }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                node?.let { Text(it.name.ifEmpty { it.typeName.substringAfterLast('.') }) }
                OutlinedButton(enabled = node != null, onClick = { openSource(selectedPath) }) { Text("Go to XAML") }
            }
        }
       }
        if (view != null) {
            if (!treeOnly) key(source, resetViewport) { PreviewImage(view, selectedPath, ::selectPath, ::openSource, zoom, Modifier.weight(1f).fillMaxWidth()) }
            if (showInspector) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                @Composable fun tree(modifier: Modifier) { key(root?.className, root?.resourcePath, instance, static) {
                    VisualTree(view.nodes, selectedPath, ::selectPath, modifier)
                } }
                @Composable fun properties(modifier: Modifier) { Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Effective properties"); view.properties.forEach { Text("${it.name}: ${it.value}") }
                } }
                if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tree(Modifier.weight(1f).fillMaxHeight()); properties(Modifier.weight(1f).fillMaxHeight())
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tree(Modifier.weight(1f).fillMaxWidth()); properties(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
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
private fun PreviewImage(view: WinUIXamlVisualSnapshot, selected: List<Int>, select: (List<Int>) -> Unit, openSource: (List<Int>) -> Unit, zoom: Float?, modifier: Modifier) {
    val image = view.image ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value = withContext(Dispatchers.IO) { bgraImage(image).toComposeImageBitmap() }
    }
    val root = view.nodes.firstOrNull()?.bounds ?: return
    if (root.width <= 0 || root.height <= 0) return
    var pan by remember(zoom) { mutableStateOf(Offset.Zero) }
    Canvas(modifier.clipToBounds().semantics { contentDescription = "WinUI XAML preview. Double-click to open XAML. Drag to pan; use Fit to reset." }
        .pointerInput(zoom) { detectDragGestures { change, delta -> change.consume(); pan += delta } }
        .pointerInput(view.nodes, zoom, pan) {
          fun hit(point: Offset): WinUIXamlVisualNode? {
            val factor = zoom?.times(density) ?: minOf(size.width / root.width, size.height / root.height).toFloat()
            val origin = Offset((size.width - root.width.toFloat() * factor) / 2, (size.height - root.height.toFloat() * factor) / 2) + pan
            return WinUIXamlVisualInspectionSelection.hit(view.nodes, (point.x - origin.x) / factor.toDouble(), (point.y - origin.y) / factor.toDouble())
          }
          detectTapGestures(onTap = { point -> hit(point)?.let { select(it.path) } },
              onDoubleTap = { point -> hit(point)?.let { select(it.path); openSource(it.path) } })
        }) {
        val factor = zoom?.times(density) ?: minOf(size.width / root.width, size.height / root.height).toFloat()
        val origin = Offset((size.width - root.width.toFloat() * factor) / 2, (size.height - root.height.toFloat() * factor) / 2) + pan
        translate(origin.x, origin.y) { scale(factor, factor, Offset.Zero) {
            bitmap?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(root.width.toInt(), root.height.toInt())) }
            view.nodes.firstOrNull { it.path == selected }?.bounds?.let { r ->
                drawRect(Color(0xFF4BA3FF), Offset(r.x.toFloat(), r.y.toFloat()),
                    Size(r.width.toFloat(), r.height.toFloat()), style = Stroke(2.dp.toPx() / factor))
            }
        } }
    }
}

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
