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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
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
    var panTool by remember { mutableStateOf(false) }
    val propertyFilter = remember { TextFieldState() }
    val sdk = project.service<WinRTSdkPreviewService>()
    val projectState by design.state.collectAsState()
    val selection = project.service<WinUIXamlVisualInspectionSelection>()
    val rootKey by selection.root.collectAsState()
    val instance by selection.instance.collectAsState()
    val livePath by selection.path.collectAsState()
    var staticPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var multiPaths by remember { mutableStateOf<Set<List<Int>>>(emptySet()) }
    val selectedPath = if (static) staticPath else livePath
    val selectedPaths = if (static) multiPaths + setOf(staticPath) else setOf(livePath)
    var showToolbox by remember { mutableStateOf(false) }
    var armedTool by remember { mutableStateOf<String?>(null) }
    var toolDrop by remember { mutableStateOf<DesignerToolDrop?>(null) }
    var snapEnabled by remember { mutableStateOf(true) }
    val modules by project.service<WinRTProjectService>().modules.collectAsState()
    val selectedModule = selectedWinRTModule(project)
    // A restored file must wait for its own imported module. Falling back to the
    // tool-window selection can briefly start another host/theme during restore.
    val module = if (staticFile != null) previewSourceModule(staticFile, modules) else selectedModule
    val declarations by project.service<WinUIXamlSnapshotService>().state.collectAsState()
    val inheritedLaunch = remember(staticFile, modules, projectState.pid, projectState.connected) {
        staticFile?.takeIf { module?.hotReloadLaunches.isNullOrEmpty() }?.let(design::sourcePreviewLaunch)
    }
    val launchModule = inheritedLaunch?.first ?: module
    var launchName by remember(launchModule?.projectDirectory) { mutableStateOf<String?>(null) }
    val launches = previewProjectLaunches(module, inheritedLaunch?.second, static)
    val launch = launches.firstOrNull { it.taskName == launchName } ?: launches.firstOrNull()
    val preferences = remember(project) { PropertiesComponent.getInstance(project) }
    val projectCodeKey = "winrt.preview.projectCode.${module?.projectDirectory.orEmpty()}"
    var preferProjectCode by remember(projectCodeKey) { mutableStateOf(preferences.getBoolean(projectCodeKey, true)) }
    val wantsProjectCode = preferProjectCode && launches.isNotEmpty()
    val matchingProjectHost = launchModule != null && launch != null && design.usesLaunch(launchModule, launch)
    // Keep document preparation on the requested backend while its process starts.
    // A disconnected Native session is not evidence that an SDK fallback is wanted.
    var basicFallback by remember(module, launch, wantsProjectCode) { mutableStateOf(false) }
    val projectCode = wantsProjectCode && !basicFallback
    val session: WinRTDevelopmentSession = if (!static) live else if (projectCode) design else sdk
    val state by session.state.collectAsState()
    val designLaunch = if (projectCode) launch else module?.staticPreview?.launch()
    val roots = if (static && projectCode && !matchingProjectHost) emptyList() else state.inspectionRoots
    val root = if (static) roots.firstOrNull { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS } else
        remember(roots, rootKey) { selection.resolve(roots) }
    LaunchedEffect(root, static) {
        if (!static && root != null && rootKey != WinUIXamlVisualInspectionSelection.key(root))
            selection.selectRoot(WinUIXamlVisualInspectionSelection.key(root))
    }

    val themeKey = "winrt.preview.theme.${module?.projectDirectory.orEmpty()}"
    var theme by remember(themeKey) { mutableStateOf(preferences.getValue(themeKey, "Default")
        .takeIf { it in setOf("Default", "Light", "Dark") } ?: "Default") }
    var startupCancelled by remember { mutableStateOf(false) }
    var startupRevision by remember { mutableIntStateOf(0) }
    var handledStartupRevision by remember { mutableIntStateOf(0) }
    val catalogService = project.service<WinUIXamlCatalogService>()
    val catalogRevision by catalogService.changes.collectAsState()
    val resourceRevision by project.service<WinRTResourceIndex>().changes.collectAsState()
    LaunchedEffect(static, module, launchModule, launch, wantsProjectCode, startupCancelled, startupRevision, theme) {
        if (static && module != null && !startupCancelled) {
            val retry = startupRevision != handledStartupRevision
            handledStartupRevision = startupRevision
            startPreferredXamlPreview(
                wantsProjectCode = wantsProjectCode && launch != null,
                projectConnected = matchingProjectHost && projectState.connected && design.usesPreviewTheme(theme),
                continueStartup = { !startupCancelled },
                startProject = { design.ensurePreviewReady(launchModule!!, launch!!, retry = retry, theme = theme) },
                awaitProject = { design.state.first { it.connected || !it.busy }.connected },
                startBasic = {
                    basicFallback = true
                    module.staticPreview?.launch()?.let { sdkLaunch ->
                        sdk.ensurePreviewReady(module, sdkLaunch, retry = retry, theme = theme)
                    }
                })
        }
    }
    var automatic by remember { mutableStateOf(true) }
    LaunchedEffect(session, static, automatic) {
        if (static) session.setPreviewAutomatic(automatic)
    }
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
    val previewApplication by produceState<String?>(null, static, module, launchModule, projectCode, files, source, revision, resourceRevision) {
        value = withContext(Dispatchers.IO) { module?.takeIf { static }?.let {
            WinUIXamlPreviewSources(project, it, files, source, if (projectCode) launchModule ?: it else it).application()
        } }
    }
    val width = remember { TextFieldState("800") }; val height = remember { TextFieldState("600") }
    val validWidth = width.text.toString().toIntOrNull()?.takeIf { it in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION }
    val validHeight = height.text.toString().toIntOrNull()?.takeIf { it in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION }
    var viewport by remember { mutableStateOf(800 to 600) }
    LaunchedEffect(validWidth, validHeight) {
        if (validWidth != null && validHeight != null) {
            delay(250)
            viewport = validWidth to validHeight
        }
    }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var manualRefresh by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf<WinUIXamlDesignDocument?>(null) }
    var recordState by remember(source) { mutableStateOf(false) }
    var selectedStates by remember(source) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stateDeletion by remember(source) { mutableStateOf<DesignerStateDeletion?>(null) }
    var showStateAuthoring by remember(source) { mutableStateOf(false) }
    var authoringGroup by remember(source) { mutableStateOf("") }
    val newGroupName = remember(source) { TextFieldState("") }
    val newStateName = remember(source) { TextFieldState("") }
    val stateGroups = remember(prepared?.markup) {
        prepared?.markup?.let { runCatching { WinUIXamlDesignStates.groups(it) }.getOrDefault(emptyList()) }.orEmpty()
    }
    LaunchedEffect(stateGroups) {
        if (prepared != null) selectedStates = selectedStates.filter { (key, value) -> stateGroups.any { it.key == key && value in it.states } }
    }
    val storyboardDocument = remember(prepared?.markup) {
        prepared?.markup?.let { runCatching { WinUIXamlDesignStoryboards.prepare(it) }.getOrNull() }
    }
    val storyboards = storyboardDocument?.realized(state.inspection?.nodes.orEmpty()).orEmpty()
    var showTimeline by remember { mutableStateOf(false) }
    var editTimeline by remember { mutableStateOf(false) }
    var createAnimationTrack by remember { mutableStateOf(false) }
    var timelineEnd by remember(source) { mutableIntStateOf(10_000) }
    var storyboardId by remember(source) { mutableStateOf("") }
    var createdStoryboardKey by remember(source) { mutableStateOf<String?>(null) }
    val editorStoryboardKey = remember(source, storyboardId) { storyboards.singleOrNull { it.id == storyboardId }?.key.orEmpty() }
    // Keep the source scope while a new render temporarily clears prepared markup.
    // Losing it resets the keyframe selection and hides successful edit feedback.
    val editorStoryboardOwner = remember(source, storyboardId) {
        storyboards.singleOrNull { it.id == storyboardId }?.owner
            ?.takeUnless { it in storyboardDocument?.syntheticOwners.orEmpty() }
    }
    var animationRevision by remember(source) { mutableIntStateOf(0) }
    var animationCommand by remember(source) { mutableStateOf<WinUIXamlPreviewAnimation?>(null) }
    var animationPlaying by remember(source) { mutableStateOf(false) }
    val animationPosition = remember(source) { TextFieldState("0") }
    fun animationTime(): Int = if (animationPlaying) state.inspection?.clock?.positionMillis ?: 0
        else animationPosition.text.toString().toIntOrNull()?.coerceIn(0, 86_400_000) ?: 0
    fun controlAnimation(action: String) {
        val storyboard = storyboards.singleOrNull { it.id == storyboardId } ?: return
        val completed = action == "Play" && !animationPlaying && animationCommand?.action == "Play" &&
            state.inspection?.clock?.let { !it.playing && it.positionMillis == animationTime() } == true
        val position = if (action == "Stop" || completed) 0 else animationTime()
        animationPosition.setTextAndPlaceCursorAtEnd(position.toString())
        val token = if (storyboard.instancePath != null) state.inspection?.treeToken?.takeIf { it > 0 } ?: return else 0L
        animationCommand = WinUIXamlPreviewAnimation(storyboard.owner, storyboard.key, position, action,
            storyboard.instancePath, storyboard.sourceId, token)
        animationPlaying = action == "Play"
        animationRevision++
    }
    LaunchedEffect(storyboards, prepared, createdStoryboardKey) {
        storyboards.singleOrNull { it.key == createdStoryboardKey }?.let {
            storyboardId = it.id
            createdStoryboardKey = null
            animationCommand = null
            animationPlaying = false
        }
        if (prepared != null && storyboards.none { it.id == storyboardId }) {
            storyboardId = ""; animationCommand = null; animationPlaying = false
            animationPosition.setTextAndPlaceCursorAtEnd("0")
        }
    }
    var submitted by remember(source, projectCode, static) {
        mutableStateOf<Triple<WinUIXamlInspectionRequest, Long?, Triple<Int, Long, Int>>?>(null)
    }
    var showSettings by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    val customZoom = remember { TextFieldState("100") }
    var showSession by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var actualZoom by remember(source) { mutableFloatStateOf(1f) }
    val displayDensity = androidx.compose.ui.platform.LocalDensity.current.density
    val captureScale = (kotlin.math.ceil(actualZoom * displayDensity * 4.0) / 4.0).coerceIn(0.125, 32.0)
    var zoom by remember(source) { mutableStateOf<Float?>(null) }
    var fitSelection by remember(source) { mutableStateOf(false) }
    var resetViewport by remember(source) { mutableIntStateOf(0) }
    // Tool panes move the canvas between composition branches. Keep its pan in
    // the editor so opening Properties does not lose the current work area.
    var captureViewport by remember(source) { mutableStateOf<WinUIXamlVisualBounds?>(null) }
    val canvasPan = remember(source) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(source, resetViewport) { canvasPan.value = Offset.Zero }
    var lastView by remember(source, static) { mutableStateOf<WinUIXamlVisualSnapshot?>(null) }
    val placeholderCache = remember(source, module?.buildDirectory, static) {
        module?.takeIf { static && source.isNotBlank() }?.let {
            WinUIXamlPreviewPlaceholder(Path.of(it.buildDirectory).resolve("kotlin-winrt/ide-preview-frames"), source)
        }
    }
    val placeholder by produceState<WinUIXamlVisualSnapshot?>(null, placeholderCache, theme, viewport) {
        value = null
        value = withContext(Dispatchers.IO) { placeholderCache?.read(theme, viewport.first, viewport.second) }
    }

    val scope = rememberCoroutineScope()
    var selectedSourceIdentity by remember(source) { mutableStateOf<Pair<String, String>?>(null) }
    fun selectPath(path: List<Int>) { if (static) {
        staticPath = path; multiPaths = emptySet()
        selectedSourceIdentity = state.inspection?.nodes?.firstOrNull { it.path == path && it.name.isNotEmpty() }?.let { it.typeName to it.name }
    } else selection.path.value = path }
    fun togglePath(path: List<Int>) {
        if (!static) { selectPath(path); return }
        val next = if (path in selectedPaths) selectedPaths - setOf(path) else
            selectedPaths.filterNot { it.take(path.size) == path || path.take(it.size) == it }.toSet() + setOf(path)
        if (next.isNotEmpty()) { staticPath = if (path in next) path else next.last(); multiPaths = next - setOf(staticPath) }
    }
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
    var preparationRevision by remember(source) { mutableLongStateOf(revision) }
    var preparationRefresh by remember(source) { mutableIntStateOf(manualRefresh) }
    LaunchedEffect(static, source, files, declarations, catalogRevision, resourceRevision, projectCode, launchModule, if (automatic) revision else 0L, manualRefresh) {
        val typing = automatic && revision != preparationRevision && manualRefresh == preparationRefresh
        preparationRevision = revision
        preparationRefresh = manualRefresh
        prepared = null; failure = null
        if (static && source.isNotEmpty() && module != null) {
            // Only new typing needs a quiet interval. Opening another document,
            // explicit refresh and metadata notifications can prepare immediately.
            if (typing) delay(150)
            try {
                prepared = withContext(Dispatchers.IO) {
                    val documents = WinUIXamlPreviewSources(project, module, files, source, if (projectCode) launchModule ?: module else module)
                    val cache = if (projectCode) WinUIXamlDesignDocumentCache(
                        Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-design-documents"), source) else null
                    val sourceText = documents.read(source)
                    val sourceClass = if (projectCode) runCatching { io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadMarkup.parse(sourceText).className }.getOrNull() else null
                    val sourceHash = io.github.composefluent.winrt.metadata.WinUIXamlDeclarations.sourceFingerprint(sourceText)
                    val bindingPage = if (projectCode) declarations.values.flatMap { it.declarations.pages }
                        .filter { it.className == sourceClass && it.sourceHash == sourceHash }.distinct().singleOrNull() else null
                    val cacheKey = if (projectCode) documents.projectDocumentKey() else null
                    cacheKey?.let { cache?.read(it) }?.let { return@withContext it }
                    val catalog = (if (projectCode) catalogService.forFile(source) else catalogService.forDesigner(module))
                    val events = if (projectCode && catalog == null) catalogService.eventsForFile(source) else null
                    if (catalog == null && events == null) return@withContext null

                    val application = documents.application()
                    if (projectCode && bindingPage == null && io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadMarkup.parse(sourceText).containsCompiledBindings())
                        error("Updating compiled binding metadata…")
                    if (!projectCode) documents.stageAssets()
                    WinUIXamlDesignDocument.prepare(documents.read(source), application?.let(documents::read),
                        documents.target(source), application?.let(documents::target).orEmpty(),
                        resource = documents::resolve,
                        compiledResourceFallback = projectCode,
                        projectBindings = projectCode, bindingPage = bindingPage,
                        designData = documents::resolve,
                        sdkType = if (projectCode) null else { uri, type -> catalog?.resolve(uri, type) != null },
                        visualType = { uri, type -> catalog?.let { schema -> schema.resolve(uri, type)?.let(schema::isVisual) == true } == true }) { uri, type, attribute ->
                        if (catalog != null) catalog.resolve(uri, type)?.let {
                            catalog.members(it).any { member -> member.name == attribute && member.isEvent }
                        } == true else events!!.isEvent(uri, type, attribute)
                    }.also { document ->
                        // The fast event-only preparation has no visual schema yet.
                        // Do not persist its unmarked tree over the later source-aware copy.
                        if (cacheKey != null && catalog != null) runCatching { cache?.write(cacheKey, document) }
                    }
                }
                prepared?.let { notes = it.notes }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = error.message }
        }
    }
    LaunchedEffect(viewport, theme, manualRefresh, resourceRevision, selectedStates) {
        if (animationCommand?.instancePath != null) {
            animationCommand = null
            animationPlaying = false
        }
    }
    LaunchedEffect(session, launchModule, launch, projectCode, static, prepared, viewport, theme, captureScale, captureViewport, manualRefresh, resourceRevision, selectedStates, animationCommand, animationRevision) {
        if (static && prepared != null) withContext(Dispatchers.IO) {
            session.state.previewRenderTargets().collectLatest { target ->
                val (pid, root) = target ?: return@collectLatest
                if (!session.usesPreviewTheme(theme) || projectCode &&
                    (launchModule == null || launch == null || !session.usesLaunch(launchModule, launch))) return@collectLatest
                val (w, h) = viewport
                val document = prepared ?: return@collectLatest
                val choices = selectedStates
                val markup = try { WinUIXamlDesignStates.select(storyboardDocument?.markup ?: document.markup, choices) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { failure = error.message; return@collectLatest }
                failure = null
                // Preparation and selection effects can run in the same composition.
                // Never replay a command whose resource disappeared in the new document.
                val validAnimation = animationCommand?.takeIf { command ->
                    storyboards.any { it.owner == command.owner && it.key == command.key && it.sourceId == command.sourceId && it.instancePath == command.instancePath } &&
                        (command.instancePath == null || submitted?.let {
                            it.second == pid && it.first.previewMarkup == markup && it.first.width == w && it.first.height == h &&
                                it.first.theme == theme && it.third.first == manualRefresh && it.third.second == resourceRevision
                        } == true)
                }
                // Deliver the new image before invoking effective-property getters.
                // The selection effect reads those once this snapshot is published.
                val request = WinUIXamlInspectionRequest(root.className, root.resourcePath, 0, emptyList(), true,
                    markup, w, h, theme, captureScale, validAnimation, readProperties = false, captureBounds = captureViewport)
                val key = Triple(request, pid, Triple(manualRefresh, resourceRevision, animationRevision))
                // Metadata notifications can prepare the same document again. Keep
                // explicit refresh and asset changes, but avoid duplicate bitmaps.
                if (submitted != key) {
                    val previous = submitted
                    // Source edits already waited before preparation. Only coalesce
                    // capture-density changes during continuous canvas zoom here.
                    if (previous != null && previous.second == pid && previous.third == key.third &&
                        previous.first.previewMarkup == request.previewMarkup && previous.first.animation == request.animation &&
                        (previous.first.renderScale != request.renderScale || previous.first.captureBounds != request.captureBounds)) delay(150)
                    // Capture density and the SDK animation clock do not change
                    // the source tree. Keep realized controls and template state
                    // when zooming; explicit refresh and asset edits still reload.
                    val captureOnly = previous != null && previous.second == pid && previous.third.first == manualRefresh && previous.third.second == resourceRevision &&
                        previous.first.copy(animation = request.animation, renderScale = request.renderScale, readProperties = request.readProperties, captureBounds = request.captureBounds) == request
                    currentCoroutineContext().ensureActive()
                    val imageFirst = !captureOnly && (previous == null || previous.second != pid)
                    // The session schedules the complete tree after publishing
                    // the image, even if catalog preparation recomposes this view.
                    session.inspect(when {
                        captureOnly -> request.copy(previewMarkup = "", selectedPath = selectedPath, readTree = false)
                        imageFirst -> request.copy(readTree = false)
                        else -> request
                    })
                    submitted = key
                }
            }
        }
    }
    LaunchedEffect(root, selectedPath, state.connected, static, manualRefresh, instance, state.inspectionPath, state.inspection?.nodes, captureScale, showInspector, state.inspectionPropertiesRead, state.inspectionTreeRead) {
        if (root != null && state.connected && (!static || prepared != null && state.inspection != null && state.inspectionTreeRead && (state.inspectionPath != selectedPath || (showInspector || treeOnly) && !state.inspectionPropertiesRead))) {
            session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                if (static) 0 else instance.coerceIn(0, (root.instances - 1).coerceAtLeast(0)), selectedPath, !treeOnly && !static, renderScale = captureScale, readProperties = !static || showInspector || treeOnly, readTree = !static))
        }
    }
    LaunchedEffect(root, state.connected, static, automatic, selectedPath, instance, treeOnly, captureScale) {
        if (!static && automatic && state.connected && root != null) while (true) {
            delay(1000)
            session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, instance, selectedPath, !treeOnly, renderScale = captureScale))
        }
    }
    LaunchedEffect(animationPlaying, root, state.connected, selectedPath, captureScale, showInspector) {
        if (static && animationPlaying && state.connected && root != null) while (true) {
            delay(100)
            if (!session.state.value.inspecting) session.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath,
                selectedPath = selectedPath, renderScale = captureScale, readProperties = showInspector || treeOnly))
        }
    }
    LaunchedEffect(state.inspection?.clock, state.inspecting) {
        if (!state.inspecting) state.inspection?.clock?.let { clock ->
            if (animationPlaying) {
                animationPosition.setTextAndPlaceCursorAtEnd(clock.positionMillis.toString())
                animationPlaying = clock.playing
            }
        }
    }
    LaunchedEffect(state.animationError) {
        if (state.animationError != null) animationPlaying = false
    }
    val currentView = state.inspection.takeIf { failure == null && (!static || prepared != null) }?.let { snapshot ->
        val synthetic = storyboardDocument?.syntheticOwners.orEmpty().takeIf { static }.orEmpty()
        if (synthetic.isEmpty()) snapshot else snapshot.copy(
            nodes = snapshot.nodes.map { if (it.name in synthetic) it.copy(name = "") else it },
            properties = if (snapshot.nodes.any { it.path == state.inspectionPath && it.name in synthetic })
                snapshot.properties.map { if (it.name == "Name") it.copy(value = "") else it } else snapshot.properties)
    }
    LaunchedEffect(currentView) { if (currentView?.image != null) lastView = currentView }
    val view = currentView ?: lastView.takeIf { static }
    val captureLimitations = remember(view?.nodes) { previewCaptureLimitations(view?.nodes.orEmpty()) }
    val showingPlaceholder = static && view == null && placeholder != null
    LaunchedEffect(state.inspection?.image, state.inspectionDocument, placeholderCache) {
        val document = state.inspectionDocument
        val image = state.inspection?.image
        // Correlate the response with this file's prepared document. A previous
        // tab/theme, selected state or animation must never overwrite its image.
        if (static && image != null && document != null && document.copy(readTree = true) == submitted?.first && document.previewMarkup == prepared?.markup &&
            document.theme == theme && document.width == viewport.first && document.height == viewport.second &&
            document.animation == null && selectedStates.isEmpty() && !animationPlaying &&
            state.connected && state.inspectionError == null) {
            withContext(Dispatchers.IO) {
                runCatching { placeholderCache?.write(theme, viewport.first, viewport.second, image) }
            }
        }
    }

    var pendingStructureSelection by remember(source) { mutableStateOf<com.intellij.psi.SmartPsiElementPointer<com.intellij.psi.xml.XmlTag>?>(null) }
    var structureNodesBefore by remember(source) { mutableStateOf(view?.nodes) }
    LaunchedEffect(view?.nodes, state.inspectionTreeRead) {
        if (static && !state.inspectionTreeRead) return@LaunchedEffect
        if (static && view != null && view.nodes != structureNodesBefore && pendingStructureSelection != null) {
            val pointer = pendingStructureSelection
            val path = withContext(Dispatchers.IO) { ReadAction.compute<List<Int>?, RuntimeException> {
                pointer?.element?.let { WinUIXamlSourceSelection.node(it, view.nodes)?.path }
            } }
            if (path != null) { selectPath(path); pendingStructureSelection = null }
        }

        if (static && view != null && pendingStructureSelection == null && multiPaths.isEmpty()) {
            selectedSourceIdentity?.let { identity -> view.nodes.singleOrNull { it.typeName == identity.first && it.name == identity.second }?.let { staticPath = it.path } }
        }
        if (static && view != null) multiPaths = multiPaths.filter { path -> view.nodes.any { it.path == path } }.toSet()
        if (static && view != null && view.nodes.none { it.path == staticPath }) {
            staticPath = generateSequence(staticPath) { it.dropLast(1).takeIf { parent -> it.isNotEmpty() } }
                .firstOrNull { path -> view.nodes.any { it.path == path } }.orEmpty()
        }
    }
    // Picking includes template parts only when they resolve to editable source for this instance.
    val authoredPaths by produceState<Set<List<Int>>?>(null, static, source, view?.nodes, revision, theme, previewApplication, catalogRevision, resourceRevision, state.inspectionTreeRead) {
        if (!static) { value = null; return@produceState }
        if (!state.inspectionTreeRead) { value = emptySet(); return@produceState }
        val nodes = view?.nodes ?: return@produceState
        value = withContext(Dispatchers.IO) { ReadAction.compute<Set<List<Int>>, RuntimeException> {
            val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(source.replace('\\', '/'))
            val xml = file?.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) } as? com.intellij.psi.xml.XmlFile
            xml?.let { document -> val context = WinUIXamlSourceSelection.context(document, theme, previewApplication); val tags = WinUIXamlSourceSelection.sourceTags(document, context); nodes.filter { node -> tags.count { tag -> WinUIXamlSourceSelection.node(tag, nodes, node.path, document, context) == node } == 1 }.map { it.path }.toSet() }.orEmpty()
        } }
    }
    var editFeedback by remember(source) { mutableStateOf<String?>(null) }
    // A successful write also changes revision; do not erase its feedback on
    // the next composition before the user has a chance to read it.
    LaunchedEffect(editFeedback) { if (editFeedback != null) { delay(6_000); editFeedback = null } }
    // A new selection/revision must lose edit capabilities in this composition,
    // before the asynchronous resolver starts. produceState keys alone retain
    // the previous value until its new coroutine runs, exposing stale commands.
    val editableResult by key(selectedPaths, revision, view?.nodes, static, source, state.connected, state.inspection, state.inspectionTreeRead, failure, state.inspectionError, state.projectCodeChanged, recordState, selectedStates, theme, previewApplication, catalogRevision, resourceRevision) {
      produceState<List<DesignerSelection>?>(null) {
        val snapshot = view
        if (snapshot == null || !static || !state.connected || state.inspection == null || !state.inspectionTreeRead || failure != null || state.inspectionError != null || state.projectCodeChanged) { value = emptyList(); return@produceState }
        value = withContext(Dispatchers.IO) { ReadAction.compute<List<DesignerSelection>, RuntimeException> {
            selectedPaths.mapNotNull { path ->
                val node = snapshot.nodes.firstOrNull { it.path == path } ?: return@mapNotNull null
                val owner = snapshot.nodes.filter { it.path.size <= path.size && path.take(it.path.size) == it.path }
                    .sortedByDescending { it.path.size }.firstNotNullOfOrNull { session.sourcePath(it.typeName) }
                val base = WinUIXamlDesignerEditing.selection(project, owner ?: source, node, snapshot.nodes, theme, previewApplication)
                if (!recordState) base else {
                    val chosen = selectedStates.entries.singleOrNull()
                    val group = stateGroups.singleOrNull { it.key == chosen?.key }
                    if (base == null || group == null || chosen == null) null
                    else DesignerStateEditing.selection(base, group, chosen.value)
                }
            }.takeIf { it.size == selectedPaths.size }.orEmpty()
        } }
      }
    }
    val editables = editableResult.orEmpty()
    var editStyle by remember(source, selectedPaths, recordState) { mutableStateOf(false) }
    var createStyle by remember(source, selectedPaths, recordState) { mutableStateOf(false) }
    val styleKey = remember(source, selectedPaths) { TextFieldState("") }
    var extractStyle by remember(source, selectedPaths) { mutableStateOf(true) }
    val styleResult by key(editables, revision, theme, launchModule, projectCode, resourceRevision) {
        produceState<Pair<Boolean, DesignerSelection?>>(false to null) {
            val selected = editables.singleOrNull()
            value = true to if (selected == null) null else withContext(Dispatchers.IO) {
                val application = module?.takeIf { static }?.let { WinUIXamlPreviewSources(project, it, files, source,
                    if (projectCode) launchModule ?: it else it).application() }
                ReadAction.compute<DesignerSelection?, RuntimeException> {
                    DesignerStyleEditing.selection(selected, previewTheme = theme, applicationSource = application)
                }
            }
        }
    }
    val (styleReady, styleSelection) = styleResult
    val editable = editables.lastOrNull()?.let { primary ->
        primary.copy(properties = primary.properties.filter { property -> editables.all { target ->
            target.properties.any { it.name == property.name && it.writable && (!it.elementValue || editables.size == 1) }
        } }.map { property -> property.copy(value = property.value.takeIf { value ->
            editables.all { target -> target.properties.first { it.name == property.name }.value == value }
        }) })
    }
    // Keep only presentation state during same-selection source resolution. Write
    // capabilities still come exclusively from the freshly resolved editables.
    // Catalog/resource notifications also follow our own property writes. They
    // invalidate resolution above, not the displayed editor or its keyboard focus.
    var displayedEditable by remember(source, selectedPaths, static, state.connected, failure,
        state.inspectionError, state.projectCodeChanged, recordState, selectedStates, theme, previewApplication) {
        mutableStateOf<DesignerSelection?>(null)
    }
    // Renaming can temporarily leave the old native snapshot unmatched to the
    // new source. Retain its editor as a disabled presentation until resolution
    // succeeds; a changed selection, connection or error still clears it above.
    SideEffect { if (editable != null) displayedEditable = editable }
    val propertySelection = editable ?: displayedEditable
    val currentEditables by rememberUpdatedState(editables)
    var rejectedEdits by remember { mutableStateOf<DesignerEditRejection?>(null) }
    fun applyBatch(edits: List<Pair<DesignerSelection, Map<String, String?>>>, title: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching { WinUIXamlDesignerEditing.applyBatch(project, edits, title) }
                .onSuccess { editFeedback = "$title · Ctrl+Z to undo" }
                .onFailure {
                    editFeedback = it.message ?: "Could not update XAML"
                    rejectedEdits = DesignerEditRejection((rejectedEdits?.revision ?: 0) + 1,
                        edits.map { (selection, changes) -> selection to changes.keys })
                }
        }
    }
    fun authorState(createGroup: Boolean) {
        val target = currentEditables.singleOrNull()
        val group = stateGroups.singleOrNull { it.locationKey == authoringGroup }
        val name = newStateName.text.toString().trim()
        val groupName = newGroupName.text.toString().trim()
        val path = source
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching {
                if (createGroup) DesignerStateAuthoring.addGroup(project, requireNotNull(target) { "Select a source element." }, groupName, name)
                else DesignerStateAuthoring.addState(project, path, requireNotNull(group) { "Select a state group." }, name)
            }.onSuccess { editFeedback = "Created visual state · Ctrl+Z to undo"; newStateName.setTextAndPlaceCursorAtEnd("") }
                .onFailure {
                    com.intellij.openapi.diagnostic.Logger.getInstance(DesignerStateAuthoring::class.java).warn("Could not create visual state", it)
                    editFeedback = it.message ?: "Could not create visual state"
                }
        }
    }
    fun renameState(group: DesignerStateGroup, state: String?) {
        showSettings = false
        val path = source
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching { DesignerStateAuthoring.rename(project, path, group, state) }
                .onFailure { editFeedback = it.message ?: "Could not rename visual state" }
        }
    }
    fun deleteState(group: DesignerStateGroup, state: String?) {
        val path = source
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            com.intellij.openapi.application.WriteIntentReadAction.run { com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments() }
            com.intellij.openapi.application.ReadAction.nonBlocking<Result<DesignerStateDeletion>> {
                runCatching {
                    val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: error("The source file is unavailable.")
                    DesignerStateDeletion.prepare(project, file, group, state)
                }
            }.inSmartMode(project).expireWith(project).finishOnUiThread(com.intellij.openapi.application.ModalityState.nonModal()) { result ->
                result.onSuccess { stateDeletion = it; showSettings = false }
                    .onFailure { editFeedback = it.message ?: "Could not delete visual state" }
            }.submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
        }
    }
    fun structureEdit(action: (DesignerSelection) -> Unit) {
        val target = currentEditables.singleOrNull() ?: return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching { action(target) }
                .onSuccess { editFeedback = "Updated layout structure · Ctrl+Z to undo" }
                .onFailure { editFeedback = it.message ?: "Could not update layout structure" }
        }
    }
    fun reorderSelection(forward: Boolean) = structureEdit { target ->
        structureNodesBefore = view?.nodes
        val moved = DesignerStructureEditing.reorder(project, target, forward)
        pendingStructureSelection = com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(moved)
    }
    fun clipboardAction(action: String) {
        val targets = currentEditables
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching {
                val clipboard = com.intellij.openapi.ide.CopyPasteManager.getInstance()
                when (action) {
                    "copy", "cut" -> {
                        val text = com.intellij.openapi.application.WriteAction.compute<String, RuntimeException> { DesignerClipboard.copy(targets) }
                        if (action == "cut") DesignerClipboard.remove(project, targets)
                        clipboard.setContents(java.awt.datatransfer.StringSelection(text))
                    }
                    "delete" -> DesignerClipboard.remove(project, targets)
                    "paste" -> {
                        val target = targets.lastOrNull() ?: error("Select a destination container.")
                        val text = clipboard.getContents(java.awt.datatransfer.DataFlavor.stringFlavor) as? String ?: error("The clipboard has no XAML text.")
                        structureNodesBefore = view?.nodes
                        val inserted = DesignerClipboard.paste(project, target, text)
                        pendingStructureSelection = com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(inserted.last())
                    }
                }
            }.onSuccess { editFeedback = if (action == "copy") "Copied XAML" else "Updated XAML · Ctrl+Z to undo" }
                .onFailure { editFeedback = it.message ?: "Could not edit XAML" }
        }
    }
    fun applyEdit(changes: Map<String, String?>, title: String) = applyBatch(currentEditables.map { it to changes }, title)
    fun changeLayout(change: DesignerTransform) {
        runCatching { currentEditables.map { it to WinUIXamlDesignerEditing.layout(it, change) } }
            .onSuccess { applyBatch(it, if (change.resize) "Resize XAML element" else "Move XAML elements") }
            .onFailure { editFeedback = it.message }
    }
    fun align(operation: DesignerAlignment) {
        runCatching { designerAlign(currentEditables, operation) }
            .onSuccess { applyBatch(it, operation.label) }.onFailure { editFeedback = it.message }
    }
    fun insertTool(type: String, path: List<Int>, x: Double, y: Double) {
        val snapshot = view ?: return
        armedTool = null
        scope.launch {
            val target = withContext(Dispatchers.IO) { ReadAction.compute<DesignerSelection?, RuntimeException> {
                // A native template descendant is only a hit target; walk to an
                // exact source ancestor before inserting any new content.
                snapshot.nodes.filter { path.take(it.path.size) == it.path }.sortedByDescending { it.path.size }
                    .firstNotNullOfOrNull { node -> WinUIXamlDesignerEditing.selection(project, source, node, snapshot.nodes, theme, previewApplication) }
            } }
            if (target == null) { editFeedback = "Select a source container for insertion."; return@launch }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) runCatching { DesignerInsertion.insert(project, target, type, x, y) }
                    .onSuccess { editFeedback = "Inserted $type · Ctrl+Z to undo" }
                    .onFailure { editFeedback = it.message }
            }
        }
    }
    val toolboxTypes by produceState<List<String>>(emptyList(), source, catalogRevision) {
        value = withContext(Dispatchers.IO) {
            val catalog = catalogService.forFile(source)
            designerToolbox.keys.filter { name -> catalog?.resolve(io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.PRESENTATION, name)?.let(catalog::isVisual) == true }
        }
    }
    fun openSource(path: List<Int>) {
        val snapshot = view ?: return
        val node = snapshot.nodes.firstOrNull { it.path == path } ?: return
        val owner = snapshot.nodes.filter { it.path.size <= path.size && path.take(it.path.size) == it.path }
            .sortedByDescending { it.path.size }
            .firstNotNullOfOrNull { item -> session.sourcePath(item.typeName)?.let { it to item.path } }
        val file = owner?.first ?: if (static) source else root?.let { session.sourcePath(it) }
        if (file != null) scope.launch(Dispatchers.IO) { navigate(project, file, node, snapshot.nodes, owner?.second.orEmpty(), theme, previewApplication) }
    }
    DisposableEffect(project, source, static, view?.nodes, state.inspectionTreeRead) {
        val disposable = Disposer.newDisposable("WinRT preview caret selection")
        if (static && view != null && state.inspectionTreeRead) {
            val nodes = view.nodes
            EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    val editor = event.editor
                    if (!editor.contentComponent.isFocusOwner) return
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
    val projectPreparing = static && wantsProjectCode && !projectCode
    val status = if (showingPlaceholder) {
        "Previous render · " + if (startupCancelled) "preview stopped" else if (state.busy) state.message
            else if (state.connected) "rendering current XAML…" else state.message
    } else if (static && startupCancelled) {
        if (view != null) "Preview stopped · last successful render" else "Preview stopped"
    } else if (projectPreparing && view != null) {
        val renderedHost = if (state.connected) "Basic preview" else "Last successful render"
        when {
            projectState.busy -> "$renderedHost · building project controls"
            projectState.message.contains("failed", true) -> "$renderedHost · project preview failed; see Gradle Run output"
            else -> "$renderedHost · project preview is not running"
        }
    } else if (view != null && static) when {
        state.busy || state.rebuildingCode -> state.message
        !state.connected -> "Last successful render · ${state.message}"
        failure != null || state.inspectionError != null -> "Out of date · last successful render"
        !state.inspectionTreeRead -> "Preview visible · preparing selection and properties…"
        prepared == null || state.inspecting -> "Updating preview…"
        else -> state.message
    } else state.message
    val codeOutdated = static && projectCode && state.projectCodeChanged
    // A rebuild keeps the stale-code guard until the new host is ready, but its
    // in-progress status must not be presented as a failed preview.
    val error = if (state.busy || state.rebuildingCode) null else failure ?: state.inspectionError ?: if (codeOutdated)
        "Kotlin source changed · rebuild project code to update this preview" else null
    val missingModule = staticFile != null && (module == null || designLaunch == null)
    fun startPreview() {
        if (static) { basicFallback = false; startupCancelled = false; startupRevision++ }
        else live.start(module!!, launch!!, preview = false)
    }
    fun rebuildProjectPreview() {
        showSession = false; basicFallback = false; startupCancelled = false
        if (projectState.connected && design.usesLaunch(launchModule!!, launch!!)) {
            design.rebuild(launchModule, launch)
        } else design.start(launchModule!!, launch!!, restart = true, preview = true, theme = theme)
    }
    fun stopPreview() {
        startupCancelled = true
        if (static) { design.stop(); sdk.stop() } else live.stop()
    }
    val canStart = module != null && (if (static) designLaunch else launch) != null && !state.busy && state.pid == null

    @Composable fun inspector() {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Inspector", Modifier.weight(1f))
                if (!treeOnly) PreviewAction("Close inspector", "close") { showInspector = false }
            }
            val node = view?.nodes?.firstOrNull { it.path == selectedPath }
            Text(if (selectedPaths.size > 1) "${selectedPaths.size} selected · last selected is the alignment anchor" else node?.let { it.name.ifEmpty { it.typeName.substringAfterLast('.') } } ?: "Select an element")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Link("Go to XAML", enabled = node != null, onClick = { openSource(selectedPath) })
                Link("Parent", enabled = selectedPath.isNotEmpty(), onClick = { selectPath(selectedPath.dropLast(1)) })
            }
            VerticalSplitLayout(
                first = {
                    if (view != null) key(root?.className, root?.resourcePath, instance, static) {
                        VisualTree(view.nodes, selectedPath, ::selectPath, Modifier.fillMaxSize())
                    }
                },
                second = {
                    Column(Modifier.fillMaxSize().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (recordState) "Properties · recording ${selectedStates.values.singleOrNull() ?: "select one state"}" else "Properties")
                        if (editStyle) {
                            Link("Back to element properties", onClick = { editStyle = false })
                            Text("Editing a shared style affects all its consumers. Local values still take precedence.")
                        } else if (editable == null && propertySelection != null) Text(
                            if (editableResult == null || state.inspecting || prepared == null) "Updating properties…"
                            else "Source selection unavailable. Select the element again.")
                        else if (!styleReady && editables.size == 1) Text("Reading applied style…")
                        else if (styleSelection != null) Link("Edit applied style…", onClick = { editStyle = true })
                        else if (!recordState && editables.size == 1) {
                            Link(if (createStyle) "Cancel style creation" else "Create style…", onClick = { createStyle = !createStyle })
                            if (createStyle) {
                                Text("Resource key")
                                TextField(styleKey, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New style resource key" })
                                CheckboxRow("Extract local literal properties", extractStyle, { extractStyle = it })
                                Text("Bindings, resource expressions and names stay on the element. The new style is stored in its nearest source container's Resources.")
                                DefaultButton(enabled = styleKey.text.isNotBlank(), onClick = {
                                    val target = currentEditables.singleOrNull()
                                    val key = styleKey.text.toString().trim()
                                    val extract = extractStyle
                                    ApplicationManager.getApplication().invokeLater {
                                        if (project.isDisposed) return@invokeLater
                                        runCatching { DesignerStyleAuthoring.create(project, requireNotNull(target) { "Select a source element." }, key, extract) }
                                            .onSuccess { createStyle = false; editStyle = true; editFeedback = "Created style $key · Ctrl+Z to undo" }
                                            .onFailure { editFeedback = it.message ?: "Could not create style" }
                                    }
                                }) { Text(if (extractStyle) "Extract style" else "Create style") }
                            }
                        }
                        Text("Filter properties")
                        TextField(propertyFilter, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Filter properties" })
                        val propertyScroll = rememberScrollState()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().padding(end = 12.dp).verticalScroll(propertyScroll),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!editStyle && propertyFilter.text.isEmpty()) DesignerStructurePanel(editables.singleOrNull(),
                            { axis, index, value -> structureEdit { DesignerStructureEditing.definition(project, it, axis, index, value) } },
                            ::reorderSelection, { container -> structureEdit { target ->
                                structureNodesBefore = view?.nodes
                                val moved = DesignerStructureEditing.reparent(project, target, container)
                                pendingStructureSelection = com.intellij.psi.SmartPointerManager.getInstance(project).createSmartPsiElementPointer(moved)
                            } })
                        if (editStyle) {
                            if (styleSelection == null) Text("The applied style is unavailable or changed. Return to element properties and select it again.")
                            else DesignerProperties(styleSelection, propertyFilter.text.toString(), rejection = rejectedEdits) { changes, title ->
                                styleSelection?.let { applyBatch(listOf(it to changes), "$title in style") }
                            }
                        } else {
                            if (propertySelection == null && editableResult == null) Text("Loading editable properties…")
                            else DesignerProperties(propertySelection, propertyFilter.text.toString(), active = editable != null,
                                effectiveValues = if (state.inspectionPath == selectedPath && state.inspectionPropertiesRead)
                                    view?.properties.orEmpty().associate { it.name to it.value } else emptyMap(), rejection = rejectedEdits, apply = ::applyEdit)
                        }
                        Text("Effective values · read only")
                        if (state.inspecting || state.inspectionPath != selectedPath || !state.inspectionPropertiesRead) Text("Reading selection…")
                        else androidx.compose.foundation.text.selection.SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                view?.properties?.filter { it.name.contains(propertyFilter.text, ignoreCase = true) }
                                    ?.forEach { property ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(property.name, Modifier.weight(1f))
                                            Text(property.value, Modifier.weight(1.4f))
                                        }
                                    }
                            }
                        }
                    }
                        androidx.compose.foundation.VerticalScrollbar(
                            adapter = androidx.compose.foundation.rememberScrollbarAdapter(propertyScroll),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                },
                state = rememberSplitLayoutState(.4f),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
    @Composable fun canvas() {
        Box(Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Preview workspace" }, contentAlignment = Alignment.Center) {
            if (view != null && !treeOnly) key(source) {
                PreviewImage(view, selectedPath, ::selectPath, ::openSource, zoom, fitSelection, panTool,
                    { actualZoom = it }, { zoom = it; fitSelection = false },
                    { fitSelection = false; zoom = null; resetViewport++ }, Modifier.fillMaxSize(), resetViewport, editable != null, ::changeLayout,
                    selectedPaths, ::togglePath, snapEnabled, armedTool, toolDrop, { toolDrop = null }, ::insertTool, { armedTool = null }, {
                        if (static) multiPaths = view.nodes.filter { it.path.size == selectedPath.size && it.path.dropLast(1) == selectedPath.dropLast(1) }
                            .map { it.path }.toSet() - setOf(staticPath)
                    }, authoredPaths, buildList {
                        if (editables.isNotEmpty()) {
                            add("Copy" to { clipboardAction("copy") })
                            add("Cut" to { clipboardAction("cut") })
                            add("Paste" to { clipboardAction("paste") })
                            add("Delete" to { clipboardAction("delete") })
                        }
                        add("Go to XAML" to { openSource(selectedPath) })
                        add("Properties" to { showInspector = true })
                        add("Fit selection" to { fitSelection = true; zoom = null; resetViewport++ })
                        add("Fit all" to { fitSelection = false; zoom = null; resetViewport++ })
                        if (editables.singleOrNull()?.structure?.previous == true) add("Move earlier" to { reorderSelection(false) })
                        if (editables.singleOrNull()?.structure?.next == true) add("Move later" to { reorderSelection(true) })
                    }, { paths ->
                        if (static) {
                            staticPath = paths.lastOrNull().orEmpty()
                            selectedSourceIdentity = null
                            multiPaths = paths - setOf(staticPath)
                        }
                    }, ::clipboardAction, panState = canvasPan, captureViewport = { if (static) captureViewport = it })
            } else if (showingPlaceholder) {
                PreviewImage(placeholder!!, emptyList(), {}, {}, zoom, false, false,
                    { actualZoom = it }, { zoom = it }, {}, Modifier.fillMaxSize(),
                    selectedPaths = emptySet(), sourcePaths = emptySet())
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
    // Inspector/toolbox toggles move the canvas between layout branches. Keep
    // its decoded bitmap, viewport and gesture state instead of flashing blank.
    val canvasContent by rememberUpdatedState<@Composable () -> Unit>({ canvas() })
    val retainedCanvas = remember { movableContentOf { canvasContent() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val popupWidth = (maxWidth - 24.dp).coerceIn(180.dp, 400.dp)
        val popupHeight = (maxHeight - 24.dp).coerceAtLeast(100.dp)
        Column(Modifier.fillMaxSize()) {
            if (staticFile == null && !treeOnly) Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButtonRow("Live Preview", !static, { static = false })
                RadioButtonRow("Static Preview", static, { static = true })
            }
            @Composable fun workspace() {
                if (treeOnly) inspector()
                else if (showInspector) HorizontalSplitLayout(
                    first = { retainedCanvas() }, second = { inspector() },
                    state = rememberSplitLayoutState(.65f),
                    firstPaneMinWidth = 80.dp, secondPaneMinWidth = 160.dp,
                    modifier = Modifier.fillMaxSize(),
                ) else retainedCanvas()
            }
            @Composable fun artboard() {
                if (showToolbox && static) HorizontalSplitLayout(
                    first = { DesignerToolbox(toolboxTypes, armedTool, { armedTool = it; panTool = false }, { toolDrop = it }, { showToolbox = false; armedTool = null }) },
                    second = { workspace() }, state = rememberSplitLayoutState(.25f),
                    firstPaneMinWidth = 160.dp, secondPaneMinWidth = 120.dp, modifier = Modifier.fillMaxSize())
                else workspace()
            }
            @Composable fun timeline() {
            val timelineScroll = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(end = 12.dp).verticalScroll(timelineScroll).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Storyboard timeline", Modifier.weight(1f))
                    PreviewAction("Close storyboard timeline", "close") { showTimeline = false }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        WinRTChoice("Storyboard", listOf("" to "Choose a storyboard") + storyboards.map { it.id to it.label }, storyboardId) {
                            if (animationCommand != null) controlAnimation("Stop")
                            storyboardId = it
                            animationPlaying = false
                            animationPosition.setTextAndPlaceCursorAtEnd("0")
                        }
                    }
                    Box(Modifier.width(160.dp)) {
                        WinRTChoice("Visible range", listOf(1000, 5000, 10000, 30000, 60000, 300000, 3600000, 86400000).map {
                            it.toString() to "0–${it / 1000} s"
                        }, timelineEnd.toString()) { timelineEnd = it.toInt() }
                    }
                }
                org.jetbrains.jewel.ui.component.Slider(
                    (animationPosition.text.toString().toIntOrNull()?.toFloat() ?: 0f).coerceIn(0f, timelineEnd.toFloat()),
                    { position ->
                        animationPlaying = false
                        animationPosition.setTextAndPlaceCursorAtEnd(position.roundToInt().toString())
                        controlAnimation("Seek")
                    }, Modifier.fillMaxWidth().semantics { contentDescription = "Storyboard playhead" },
                    storyboardId.isNotEmpty(), 0f..timelineEnd.toFloat(), 0, { controlAnimation("Seek") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0 s"); Text("${timelineEnd / 1000} s") }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Position (ms)")
                    TextField(animationPosition, enabled = !animationPlaying, modifier = Modifier.width(104.dp).semantics { contentDescription = "Storyboard position in milliseconds" })
                    val validTime = animationPosition.text.toString().toIntOrNull()?.let { it in 0..86_400_000 } == true
                    if (!validTime) Text("Enter 0–86400000 milliseconds.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = storyboardId.isNotEmpty() && validTime && !animationPlaying, onClick = { controlAnimation("Seek") }) { Text("Seek") }
                        DefaultButton(enabled = storyboardId.isNotEmpty() && validTime, onClick = { controlAnimation(if (animationPlaying) "Seek" else "Play") }) { Text(if (animationPlaying) "Pause" else "Play") }
                        OutlinedButton(enabled = storyboardId.isNotEmpty(), onClick = { controlAnimation("Stop") }) { Text("Stop") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playback leaves source XAML unchanged.")
                    Link(if (createAnimationTrack) "Hide new track" else "New animation track…", onClick = { createAnimationTrack = !createAnimationTrack })
                    Link(if (editTimeline) "Hide keyframe editor" else "Edit keyframes…", onClick = { editTimeline = !editTimeline })
                }
                if (createAnimationTrack) DesignerStoryboardCreatePanel(project, currentEditables.singleOrNull(), animationTime(), editorStoryboardKey) { createdStoryboardKey = it }
                if (editTimeline) DesignerStoryboardPanel(project, source,
                    editorStoryboardKey, revision, animationTime(), editorStoryboardOwner,
                    storyboards.singleOrNull { it.id == storyboardId }?.sourceId.orEmpty()) { millis ->
                    animationPosition.setTextAndPlaceCursorAtEnd(millis.toString())
                    animationPlaying = false
                    controlAnimation("Seek")
                }
                (state.animationError ?: failure)?.let { Text(it) }
            }
            androidx.compose.foundation.VerticalScrollbar(
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(timelineScroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showTimeline && static) VerticalSplitLayout(
                    first = { artboard() }, second = { timeline() },
                    state = rememberSplitLayoutState(.6f),
                    draggableWidth = 14.dp, firstPaneMinWidth = 100.dp, secondPaneMinWidth = 120.dp,
                    modifier = Modifier.fillMaxSize(),
                ) else artboard()
            }
            Divider(Orientation.Horizontal)
            // One fixed-height command strip; popups never participate in artboard measurement.
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Session and settings stay reachable when high DPI or a narrow
                // editor leaves less room for the canvas tools.
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically) {
                if (!treeOnly) {
                    PreviewAction("Select elements", "select", view != null, selected = !panTool) { panTool = false }
                    PreviewAction("Pan canvas", "hand", view != null, selected = panTool) { panTool = true; armedTool = null }
                    if (static) {
                        PreviewAction("Storyboard timeline", "timeline", enabled = static, selected = showTimeline) { showTimeline = !showTimeline }
                        PreviewAction("Toolbox", "toolbox", selected = showToolbox) { showToolbox = !showToolbox }
                        PreviewAction("Snap to edges and centers (Alt to bypass)", "snap", selected = snapEnabled) { snapEnabled = !snapEnabled }
                        Dropdown(enabled = editables.size > 1, menuContent = {
                            DesignerAlignment.entries.forEach { operation -> selectableItem(selected = false, onClick = { align(operation) }) { Text(operation.label) } }
                        }) { Text("Align") }
                    }
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
                if (state.busy || state.rebuildingCode || state.inspecting || (static && state.connected && prepared == null)) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Tooltip(tooltip = { Text(error ?: status) }, modifier = Modifier.weight(1f)) {
                    Text(error?.let { (if (view != null) "Out of date · " else "Preview error · ") + it } ?: (if (state.rebuildingCode) status else editFeedback ?: status),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (projectPreparing && projectState.busy) Link("Cancel project build", onClick = { startupCancelled = true; design.stop(); editFeedback = "Project build cancellation requested" })
                else if (state.busy) Link("Cancel", onClick = ::stopPreview)
                else {
                    if (codeOutdated && !state.rebuildingCode) Link("Rebuild project code", onClick = ::rebuildProjectPreview)
                    else if (!state.connected && canStart) Link("Retry preview", onClick = ::startPreview)
                    else if (error != null && state.connected) Link("Retry", enabled = !state.inspecting, onClick = { manualRefresh++ })
                    if (error != null || captureLimitations.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Link(if (error != null) "Details" else "Partial capture", onClick = { showDiagnostics = true })
                    }
                }
            }
        }
        if (showZoom) PreviewPopup("Custom zoom", popupWidth, popupHeight, { showZoom = false }) {
            Text("Zoom percentage (12.5–800%)")
            val zoomFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { zoomFocus.requestFocus() }
            TextField(customZoom, modifier = Modifier.fillMaxWidth().focusRequester(zoomFocus).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    parsePreviewZoom(customZoom.text.toString())?.let { zoom = it; fitSelection = false; showZoom = false }
                    true
                } else false
            }.semantics { contentDescription = "Zoom percentage" })
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
                    Column(Modifier.weight(1f)) { Text("Width (DIP)"); TextField(width, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview width in DIP" }); if (validWidth == null) Text("Enter 64–16384 DIP.") }
                    Column(Modifier.weight(1f)) { Text("Height (DIP)"); TextField(height, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview height in DIP" }); if (validHeight == null) Text("Enter 64–16384 DIP.") }
                }
                Link("Swap width and height", onClick = {
                    val previousWidth = width.text.toString()
                    width.setTextAndPlaceCursorAtEnd(height.text.toString()); height.setTextAndPlaceCursorAtEnd(previousWidth)
                })
                WinRTChoice("Theme", listOf("Default", "Light", "Dark").map { it to it }, theme) { theme = it; preferences.setValue(themeKey, it, "Default") }
                if (storyboards.isNotEmpty()) Link("Storyboard timeline…", onClick = { showSettings = false; showTimeline = true })
                Link(if (showStateAuthoring) "Hide state creation" else "Create visual states…", onClick = { showStateAuthoring = !showStateAuthoring })
                if (showStateAuthoring) {
                    Text("State name")
                    TextField(newStateName, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New visual state name" })
                    WinRTChoice("Add to group", listOf("" to "New group on selected element") + stateGroups.map { it.locationKey to it.label }, authoringGroup) { authoringGroup = it }
                    if (authoringGroup.isEmpty()) {
                        Text("Group name")
                        TextField(newGroupName, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New visual state group name" })
                        Text("Select the source element that will own the group. Turn off state recording while creating a group.")
                    }
                    DefaultButton(enabled = newStateName.text.isNotBlank() && (if (authoringGroup.isEmpty()) currentEditables.size == 1 && !recordState && newGroupName.text.isNotBlank() else stateGroups.any { it.locationKey == authoringGroup }),
                        onClick = { authorState(authoringGroup.isEmpty()) }) { Text(if (authoringGroup.isEmpty()) "Create group and state" else "Create state") }
                    editFeedback?.let { Text(it) }
                }
                stateGroups.forEach { group ->
                    WinRTChoice("Visual state · ${group.label}", listOf("" to "Automatic") + group.states.map { it to it },
                        selectedStates[group.key].orEmpty()) { value ->
                        selectedStates = if (value.isEmpty()) selectedStates - group.key else selectedStates + (group.key to value)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Link("Rename group…", onClick = { renameState(group, null) })
                        Link("Delete group…", onClick = { deleteState(group, null) })
                    }
                    selectedStates[group.key]?.let { selected ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Link("Delete state…", onClick = { deleteState(group, selected) })
                            Link("Rename state…", onClick = { renameState(group, selected) })
                        }
                    }
                }
                if (stateGroups.isNotEmpty()) {
                    Text("State selection affects this preview only. Automatic restores the document's triggers.")
                    CheckboxRow("Record properties in selected state", recordState, { recordState = it })
                    if (recordState) Text("Choose exactly one named state and a named element in its source namescope. Property edits and layout gestures create state setters; Reset removes an override.")
                }
            } else {
                if (roots.size > 1) WinRTChoice("Window", roots.map { WinUIXamlVisualInspectionSelection.key(it) to it.className.substringAfterLast('.') }, root?.let(WinUIXamlVisualInspectionSelection::key)) { selection.selectRoot(it) }
                if ((root?.instances ?: 0) > 1) WinRTChoice("Instance", (0 until root!!.instances).map { it.toString() to "Instance ${it + 1}" }, instance.toString()) {
                    selection.instance.value = it.toInt(); selection.path.value = emptyList()
                }
            }
            CheckboxRow(if (static) "Update after source edits" else "Refresh automatically", automatic, { automatic = it })
            Text("Drag blank canvas to marquee-select. Ctrl/Shift adds to selection; Ctrl/Shift+click toggles elements. Align uses the last selection. Alt temporarily disables snapping. Toolbox controls can be dragged into the canvas.")
            Text("Canvas: drag a selected element or its handles to edit layout. Arrows nudge (Shift: 10 DIP). Ctrl+wheel zooms; wheel, hand mode arrows or Space+drag pan.")
            Text("With canvas focus: +/− zoom, 0 shows 100%, F fits all, Escape selects the parent, Enter opens XAML.")
        }
        stateDeletion?.let { deletion ->
            PreviewPopup("Delete visual state", popupWidth, popupHeight, { stateDeletion = null }) {
                Text("Delete ${deletion.label} and its contents?")
                if (deletion.transitions > 0) Text("${deletion.transitions} transitions referring to this state will also be removed.")
                Text("Resolved external references have been checked. State names computed in code need manual review. Ctrl+Z restores this change.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultButton(onClick = {
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) runCatching { deletion.apply(project) }
                                .onSuccess { stateDeletion = null; editFeedback = "Deleted visual state · Ctrl+Z to undo" }
                                .onFailure { stateDeletion = null; editFeedback = it.message ?: "Could not delete visual state" }
                        }
                    }) { Text("Delete") }
                    DefaultButton(onClick = { stateDeletion = null }) { Text("Cancel") }
                }
            }
        }
        if (showSession) PreviewPopup("Preview session", popupWidth, popupHeight, { showSession = false }) {
            WinRTModulePicker(project)
            if (!static || projectCode) WinRTChoice("Application", launches.map { it.taskName to it.taskName }, launch?.taskName) { launchName = it }
            if (static) CheckboxRow("Render project controls (build in background)", wantsProjectCode, {
                preferProjectCode = it; preferences.setValue(projectCodeKey, it, true)
            }, enabled = launches.isNotEmpty())
            if (!state.connected) Link(if (static) "Retry preview" else "Start application", enabled = canStart, onClick = { showSession = false; startPreview() })
            Link("Refresh", enabled = state.connected && !state.inspecting, onClick = { manualRefresh++; showSession = false })
            if (static && wantsProjectCode) Link("Rebuild project code", enabled = !projectState.busy && !projectState.rebuildingCode, onClick = ::rebuildProjectPreview)
            Link("Reconnect", enabled = !state.busy, onClick = { showSession = false; session.reconnect() })
            Link(if (state.busy) "Cancel build" else "Stop preview", enabled = state.pid != null || state.busy,
                onClick = { showSession = false; stopPreview() })
            if (notes.isNotEmpty()) Link("Design-time notes", onClick = { showSession = false; showDiagnostics = true })
        }
        if (showDiagnostics) PreviewPopup("Preview details", popupWidth, popupHeight, { showDiagnostics = false }) {
            Text(error ?: status)
            captureLimitations.forEach { Text(it) }
            notes.forEach { Text(it) }
        }
    }
}

@Composable
internal fun PreviewAction(label: String, icon: String, enabled: Boolean = true, selected: Boolean? = null, action: () -> Unit) {
    Tooltip(tooltip = { Text(label) }) {
        // Some IDE themes use the panel background for selected icon buttons.
        // A theme-derived outline keeps persistent selection visible separately
        // from the transient hover background (including High Contrast).
        var focused by remember { mutableStateOf(false) }
        val modifier = Modifier.size(28.dp).onFocusChanged { focused = it.isFocused }
            .then(if (focused || selected == true) Modifier.border(
            if (focused) 2.dp else 1.dp, JewelTheme.globalColors.outlines.focused,
            androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) else Modifier)
            .semantics { contentDescription = label }
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
        // Opening the inspector synchronizes its tree selection. This is not
        // a user selection and must not clear a canvas multi-selection.
        elements.lastOrNull()?.data?.path?.let { path ->
            // The popup folder is a virtual group, not an SDK object.
            if (path == listOf(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES)) nodes.firstOrNull { it.path.size == 2 && it.path.first() == WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES }?.path else path
        }?.takeIf { it != selected }?.let(select)
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

/** Zoom is measured in DIP; fit and explicit zoom share draw/hit-test coordinates. */
internal fun previewScale(width: Int, height: Int, rootWidth: Double, rootHeight: Double, density: Float, zoom: Float?): Float =
    zoom ?: if (width <= 0 || height <= 0 || rootWidth <= 0 || rootHeight <= 0) 1f
    else minOf(width / rootWidth, height / rootHeight).toFloat() / density

internal fun bgraImage(image: WinUIXamlVisualImage): BufferedImage {
    require(image.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.pixels.size == image.width * image.height * 4)
    val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB_PRE)
    val pixels = (bitmap.raster.dataBuffer as DataBufferInt).data
    java.nio.ByteBuffer.wrap(image.pixels).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels)
    return bitmap
}

private fun navigate(project: Project, path: String, node: WinUIXamlVisualNode, nodes: List<WinUIXamlVisualNode>, component: List<Int>, theme: String? = null, applicationSource: String? = null) {
    val location = ReadAction.compute<Pair<com.intellij.openapi.vfs.VirtualFile, Int>?, RuntimeException> {
        val file = LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: return@compute null
        val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
        val tag = WinUIXamlSourceSelection.tag(xml, node, nodes, component, WinUIXamlSourceSelection.context(xml, theme, applicationSource))
        (tag?.containingFile?.virtualFile ?: file) to (tag ?: xml.rootTag)?.textOffset.orEmptyOffset()
    }
    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed && location != null)
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, location.first, location.second), true) }
}
private fun Int?.orEmptyOffset(): Int = this ?: 0
