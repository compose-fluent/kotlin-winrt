package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.hotreload.*
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.ui.*
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService
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
fun WinRTPreviewPanel(project: Project, staticFile: String? = null, treeOnly: Boolean = false) {
    val live = project.service<WinRTHotReloadService>()
    val design = project.service<WinRTStaticPreviewService>()
    var static by remember { mutableStateOf(staticFile != null) }
    var showInspector by remember { mutableStateOf(staticFile == null) }
    val session: WinRTDevelopmentSession = if (static) design else live
    val state by session.state.collectAsState()
    val selection = project.service<WinRTVisualInspectionSelection>()
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
    val declarations by project.service<WinRTXamlSnapshotService>().state.collectAsState()
    val pages = declarations.values.flatMap { it.declarations.pages }
    val applicationNames = pages.filter { it.isApplication }.map { it.className }.toSet()
    val roots = state.roots.filter { it.className !in applicationNames }
    val root = if (static) roots.firstOrNull { it.className == WinRTXamlHotReloadProtocol.PREVIEW_CLASS } else
        roots.firstOrNull { WinRTVisualInspectionSelection.key(it) == rootKey } ?: roots.firstOrNull { candidate ->
            pages.any { it.className == candidate.className && it.baseTypeName == "Microsoft.UI.Xaml.Window" }
        } ?: roots.firstOrNull()
    var launchName by remember(module?.projectDirectory) { mutableStateOf<String?>(null) }
    val launches = module?.hotReloadLaunches.orEmpty().distinctBy { it.executable }
    val launch = launches.firstOrNull { it.taskName == launchName } ?: launches.firstOrNull()
    var projectCode by remember(module?.projectDirectory) { mutableStateOf(false) }
    val designLaunch = if (projectCode) launch else module?.staticPreview?.launch()
    val catalogService = project.service<WinRTXamlCatalogService>()
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
    var prepared by remember { mutableStateOf<WinRTXamlDesignDocument?>(null) }
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
                    val documents = WinRTXamlPreviewSources(project, module, files, source)
                    val application = documents.application()
                    if (!projectCode) documents.stageAssets()
                    WinRTXamlDesignDocument.prepare(documents.read(source), application?.let(documents::read),
                        documents.target(source), application?.let(documents::target).orEmpty(),
                        resource = if (projectCode) null else documents::resolve,
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
            else session.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, 0, emptyList(), true,
                prepared!!.markup, w, h, theme))
        }
    }
    LaunchedEffect(root, selectedPath, state.connected, static, manualRefresh, instance) {
        if (root != null && state.connected && (!static || prepared != null)) {
            session.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath,
                if (static) 0 else instance.coerceIn(0, (root.instances - 1).coerceAtLeast(0)), selectedPath, !treeOnly))
        }
    }
    LaunchedEffect(root, state.connected, static, automatic, selectedPath, instance, treeOnly) {
        if (!static && automatic && state.connected && root != null) while (true) {
            delay(1000)
            session.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, instance, selectedPath, !treeOnly))
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!treeOnly && staticFile == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButtonRow("Live Preview", !static, { static = false }); RadioButtonRow("Static Preview", static, { static = true })
        }
        if ((!state.connected && (!static || staticFile == null)) || static && projectCode) {
            @Composable fun buildOptions() {
                WinRTModulePicker(project)
                if (!static || projectCode) WinRTChoice("Application", launches.map { it.taskName to if (it.taskName.startsWith("runWinAppPackage")) "JVM · Packaged" else "JVM · Unpackaged" }, launch?.taskName) { launchName = it }
            }
            if (staticFile != null) WinRTDetails("project code options") { buildOptions() } else buildOptions()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!state.connected) DefaultButton(enabled = module != null && (if (static) designLaunch else launch) != null && !state.busy && state.pid == null,
                onClick = { session.start(module!!, (if (static) designLaunch else launch)!!, preview = static) }) { Text(if (static) "Retry preview" else "Start application") }
            if (static && projectCode && state.connected) DefaultButton(enabled = !state.busy,
                onClick = { session.start(module!!, launch!!, restart = true, preview = true) }) { Text("Build project code") }
            DefaultButton(enabled = state.connected && !state.inspecting, onClick = { selectPath(emptyList()); manualRefresh++ }) { Text("Refresh") }
            if (staticFile == null || state.pid != null) DefaultButton(enabled = !state.busy, onClick = session::reconnect) { Text("Reconnect") }
            if (state.pid != null) DefaultButton(onClick = session::stop) { Text(if (static) "Stop preview" else "Stop application") }
        }
        Text(state.message)
        if (staticFile != null && (module == null || designLaunch == null))
            Text("Synchronize a Kotlin WinRT module with a Windows App SDK reference to preview this document.")
        if (static) {
            CheckboxRow("Enable project code (requires compilation)", projectCode, { projectCode = it }, enabled = launches.isNotEmpty())
            if (staticFile == null) WinRTChoice("XAML document", files.map { it to (module?.projectDirectory?.let { dir -> it.removePrefix(dir.replace('\\', '/') + "/") } ?: it) }, source) { source = it }
            else Text(Path.of(staticFile).fileName.toString())
            WinRTDetails("preview options") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Text("Width (DIP)"); TextField(width, modifier = Modifier.fillMaxWidth()) }
                Column(Modifier.weight(1f)) { Text("Height (DIP)"); TextField(height, modifier = Modifier.fillMaxWidth()) }
              }
              WinRTChoice("Theme", listOf("Default", "Light", "Dark").map { it to it }, theme) { theme = it }
              notes.forEach { Text(it) }
            }
            if (notes.isNotEmpty()) Text("Design values and control defaults replace compiled bindings and event handlers. See preview options for details.")
        } else {
            WinRTChoice("Loaded component", roots.map { WinRTVisualInspectionSelection.key(it) to it.className.substringAfterLast('.') }, root?.let(WinRTVisualInspectionSelection::key)) { selection.selectRoot(it) }
            if ((root?.instances ?: 0) > 1) WinRTChoice("Instance", (0 until root!!.instances).map { it.toString() to "Instance ${it + 1}" }, instance.toString()) {
                selection.instance.value = it.toInt(); selection.path.value = emptyList()
            }
        }
        CheckboxRow(if (static) "Update after XAML edits" else "Refresh automatically", automatic, { automatic = it })
        if (staticFile != null) CheckboxRow("Visual Tree and properties", showInspector, { showInspector = it })
        failure?.let { Text(it) }
        val view = state.inspection.takeIf { failure == null && (!static || prepared != null) }
        if (view != null) {
            if (!treeOnly) PreviewImage(view, selectedPath, ::selectPath,
                if (staticFile != null && !showInspector) Modifier.weight(1f).fillMaxWidth()
                else Modifier.fillMaxWidth().heightIn(max = 280.dp))
            val node = view.nodes.firstOrNull { it.path == selectedPath }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                node?.let { Text(it.name.ifEmpty { it.typeName.substringAfterLast('.') }) }
                DefaultButton(enabled = node != null, onClick = {
                    val owner = node?.let { selected -> view.nodes.filter { it.path.size <= selected.path.size &&
                        selected.path.take(it.path.size) == it.path }.sortedByDescending { it.path.size }
                        .firstNotNullOfOrNull { item -> session.sourcePath(item.typeName)?.let { it to item.path } } }
                    val path = owner?.first ?: if (static) source else root?.let { session.sourcePath(it) }
                    if (path != null && node != null) scope.launch(Dispatchers.IO) {
                        navigate(project, path, node, view.nodes, owner?.second.orEmpty())
                    }
                }) { Text("Go to XAML") }
            }
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
        } else Text(when {
            static && (failure != null || state.inspectionError != null) -> "This document could not be rendered. Fix the reported XAML or resource error, then refresh."
            static && state.connected -> "Rendering this XAML document…"
            static -> "Start the design host to render this document with WinUI."
            else -> "Connect a development application to inspect its actual visual tree."
        })
    }
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun VisualTree(nodes: List<WinRTXamlVisualNode>, selected: List<Int>, select: (List<Int>) -> Unit, modifier: Modifier) {
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
internal fun visualInspectionTree(nodes: List<WinRTXamlVisualNode>): Tree<WinRTXamlVisualNode> {
    val children = nodes.groupBy { if (it.path.isEmpty()) null else it.path.dropLast(1) }
    fun TreeGeneratorScope<WinRTXamlVisualNode>.append(parent: List<Int>?) {
        children[parent].orEmpty().forEach { node ->
            if (children[node.path].isNullOrEmpty()) addLeaf(node, node.path)
            else addNode(node, node.path) { append(node.path) }
        }
    }
    return TreeBuilder<WinRTXamlVisualNode>().apply { append(null) }.build()
}

@Composable
private fun PreviewImage(view: WinRTXamlVisualSnapshot, selected: List<Int>, select: (List<Int>) -> Unit, modifier: Modifier) {
    val image = view.image ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value = withContext(Dispatchers.IO) { bgraImage(image).toComposeImageBitmap() }
    }
    val root = view.nodes.firstOrNull()?.bounds ?: return
    if (root.width <= 0 || root.height <= 0) return
    BoxWithConstraints(modifier) {
      val ratio = image.width.toFloat() / image.height
      val displayWidth = minOf(maxWidth, maxHeight * ratio)
      Box(Modifier.width(displayWidth).aspectRatio(ratio)) {
        bitmap?.let { Image(it, "WinUI XAML preview", Modifier.matchParentSize()) }
        Canvas(Modifier.matchParentSize().pointerInput(view.nodes) { detectTapGestures { point ->
            WinRTVisualInspectionSelection.hit(view.nodes, point.x / size.width * root.width, point.y / size.height * root.height)?.let { select(it.path) }
        } }) {
            view.nodes.firstOrNull { it.path == selected }?.bounds?.let { r ->
                drawRect(Color(0xFF4BA3FF), Offset((r.x / root.width * size.width).toFloat(), (r.y / root.height * size.height).toFloat()),
                    Size((r.width / root.width * size.width).toFloat(), (r.height / root.height * size.height).toFloat()), style = Stroke(2.dp.toPx()))
            }
        }
      }
    }
}

internal fun bgraImage(image: WinRTXamlVisualImage): BufferedImage {
    require(image.width in 1..768 && image.height in 1..768 && image.pixels.size == image.width * image.height * 4)
    val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
    val pixels = (bitmap.raster.dataBuffer as DataBufferInt).data
    pixels.indices.forEach { index -> val offset = index * 4; val bytes = image.pixels
        pixels[index] = ((bytes[offset + 3].toInt() and 255) shl 24) or ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or (bytes[offset].toInt() and 255)
    }
    return bitmap
}

private fun navigate(project: Project, path: String, node: WinRTXamlVisualNode, nodes: List<WinRTXamlVisualNode>, component: List<Int>) {
    val location = ReadAction.compute<Pair<com.intellij.openapi.vfs.VirtualFile, Int>?, RuntimeException> {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return@compute null
        val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
        val tags = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java)
        val ancestors = nodes.filter { it.path.size <= node.path.size && node.path.take(it.path.size) == it.path &&
            it.path.size >= component.size && it.path.take(component.size) == component }.sortedByDescending { it.path.size }
        val tag = ancestors.firstNotNullOfOrNull { item -> item.name.takeIf(String::isNotEmpty)?.let { name ->
            tags.firstOrNull { tag -> tag.attributes.any { it.localName == "Name" && it.value == name } }
        } } ?: ancestors.firstNotNullOfOrNull { item -> tags.filter { it.localName == item.typeName.substringAfterLast('.') }.singleOrNull() }
        file to (tag ?: xml.rootTag)?.textOffset.orEmptyOffset()
    }
    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed && location != null)
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, location.first, location.second), true) }
}
private fun Int?.orEmptyOffset(): Int = this ?: 0
