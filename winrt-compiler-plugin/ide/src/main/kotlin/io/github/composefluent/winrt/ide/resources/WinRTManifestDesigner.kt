package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.winrt.ide.ui.WinRTComboBox
import io.github.composefluent.winrt.ide.ui.WinRTTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import java.nio.file.Files
import java.nio.file.Path

internal enum class WinRTManifestPage(val title: String) {
    Application("Application"), VisualAssets("Visual Assets"), Capabilities("Capabilities"),
    Declarations("Declarations"), ContentUris("Content URIs"), Packaging("Packaging");

    companion object {
        fun forField(field: WinRTXmlField): WinRTManifestPage {
            val path = field.path.map { it.name }
            return when {
                "Capabilities" in path -> Capabilities
                "ApplicationContentUriRules" in path -> ContentUris
                "Extensions" in path -> Declarations
                "VisualElements" in path -> if (field.path.last().name == "VisualElements" && field.attribute in listOf("DisplayName", "Description", "AppListEntry")) Application else VisualAssets
                field.path.last().name == "Logo" -> VisualAssets
                "Application" in path -> Application
                else -> Packaging
            }
        }
    }
}

/** A native IDE form over the existing PSI document, rather than an XML attribute dump. */
@Composable
internal fun WinRTManifestDesigner(
    snapshot: WinRTXmlSnapshot,
    errors: List<String>,
    windowsVersions: List<String>,
    windowsVersionsFromGradle: Boolean,
    onEdit: (WinRTXmlField, String) -> Unit,
    onRemove: (WinRTXmlField) -> Unit,
    onCapability: (String, Boolean, Boolean) -> Unit,
    onExtension: (Int, String, String?) -> Unit,
    onContentUri: (Int, String, String) -> Unit,
    onGradle: () -> Unit,
    onBrowse: (WinRTXmlField) -> Unit,
    assetPath: (String) -> Path?,
) {
    var page by remember { mutableStateOf(WinRTManifestPage.Application) }
    var application by remember { mutableIntStateOf(0) }
    val applicationIds = snapshot.fields.filter { it.path.last().name == "Application" && it.attribute == "Id" }
    val visibleFields = snapshot.fields.filter { field ->
        WinRTManifestPage.forField(field) == page && field.path.firstOrNull { it.name == "Application" }?.let { it.index == application } != false
    }
    Column(Modifier.fillMaxSize()) {
        WinRTTabs(WinRTManifestPage.entries.map { it.title }, page.title, Modifier.fillMaxWidth()) { title ->
            page = WinRTManifestPage.entries.single { it.title == title }
        }
        Divider(Orientation.Horizontal)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                errors.distinct().forEach { Text(it) }
                if (applicationIds.size > 1 && page !in listOf(WinRTManifestPage.Packaging, WinRTManifestPage.Capabilities)) {
                    ManifestFormRow("Application") {
                        WinRTComboBox("Application", applicationIds.map { it.path.last().index.toString() to it.value }, application.toString(), Modifier.fillMaxWidth()) { application = it.toInt() }
                    }
                }
                Text(when (page) {
                    WinRTManifestPage.Application -> "Identify and describe the application."
                    WinRTManifestPage.VisualAssets -> "Configure application icons, tiles and the splash screen."
                    WinRTManifestPage.Capabilities -> "Choose the system features the application can access."
                    WinRTManifestPage.Declarations -> "Register protocols and file associations."
                    WinRTManifestPage.ContentUris -> "Configure access rules for web content."
                    WinRTManifestPage.Packaging -> "Configure package identity, publisher and supported Windows versions."
                })
                if (page == WinRTManifestPage.Packaging && windowsVersions.isNotEmpty()) {
                    Text("Windows versions", fontWeight = FontWeight.SemiBold)
                    windowsVersions.forEach { Text(it) }
                    Link("Edit Windows versions in Gradle", onClick = onGradle)
                }
                if (page == WinRTManifestPage.Capabilities) ManifestCapabilities(visibleFields, onRemove, onCapability)
                else visibleFields.groupBy { groupTitle(it, page) }.forEach { (group, fields) ->
                    if (group.isNotEmpty()) Text(group, fontWeight = FontWeight.SemiBold)
                    fields.forEach { field ->
                        key(field.id) {
                            val asset = page == WinRTManifestPage.VisualAssets && (field.attribute.orEmpty().contains("Logo") || field.path.last().name == "Logo" || field.attribute == "Image")
                            val governed = windowsVersionsFromGradle && field.path.last().name == "TargetDeviceFamily" && field.attribute in listOf("MinVersion", "MaxVersionTested")
                            ManifestFormRow(fieldTitle(field)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ManifestFieldEditor(field, !governed, Modifier.weight(1f), onEdit)
                                        if (asset) OutlinedButton(onClick = { onBrowse(field) }) { Text("Browse…") }
                                        if (field.attribute in listOf("Name", "Language", "Match") && field.path.last().name in listOf("Protocol", "FileTypeAssociation", "Resource", "Rule"))
                                            Link("Remove", onClick = { onRemove(field) })
                                    }
                                    if (asset) ManifestAssetPreview(field.value, assetPath)
                                }
                            }
                        }
                    }
                }
                when (page) {
                    WinRTManifestPage.Declarations -> ManifestNewDeclaration(application, snapshot.applicationCount > 0, onExtension)
                    WinRTManifestPage.ContentUris -> ManifestNewContentUri(application, snapshot.applicationCount > 0, onContentUri)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ManifestFormRow(label: String, content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label:")
            Row(Modifier.fillMaxWidth(), content = content)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text("$label:", Modifier.width(180.dp).padding(top = 5.dp))
            Row(Modifier.weight(1f), content = content)
        }
    }
}

/** Keep an in-progress edit across document acknowledgements and reject external conflicts. */
private class ManifestFieldDraft(field: WinRTXmlField) {
    val buffer = TextFieldState(field.value)
    var expected by mutableStateOf(field)
    var submitted by mutableStateOf<String?>(null)

    fun accept(field: WinRTXmlField) {
        val local = buffer.text.toString()
        if (field.value == submitted) { expected = field; submitted = null }
        else if (local == expected.value || local == field.value) {
            if (local != field.value) buffer.edit { replace(0, length, field.value) }
            expected = field
        }
    }
}

@Composable
private fun ManifestFieldEditor(field: WinRTXmlField, enabled: Boolean, modifier: Modifier, onEdit: (WinRTXmlField, String) -> Unit) {
    val draft = remember(field.id) { ManifestFieldDraft(field) }
    LaunchedEffect(field.value) { draft.accept(field) }
    val conflict = field.value != draft.expected.value && field.value != draft.submitted
    fun commit() {
        val value = draft.buffer.text.toString()
        if (enabled && !conflict && draft.submitted == null && value != draft.expected.value) {
            draft.submitted = value
            onEdit(draft.expected, value)
        }
    }
    val latestCommit by rememberUpdatedState(::commit)
    LaunchedEffect(draft.buffer.text, draft.expected.value, draft.submitted) { delay(350); commit() }
    DisposableEffect(field.id) { onDispose { latestCommit() } }
    val choices = when (field.attribute) {
        "ProcessorArchitecture" -> listOf("x64", "x86", "arm64", "neutral")
        "AppListEntry" -> listOf("default", "none")
        "Type" -> if (field.path.last().name == "Rule") listOf("include", "exclude") else emptyList()
        else -> emptyList()
    }
    Column(modifier) {
        if (choices.isNotEmpty()) WinRTComboBox(fieldTitle(field), (choices + field.value).distinct().map { it to it }, field.value, Modifier.fillMaxWidth()) { onEdit(field, it) }
        else {
            val inputModifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = fieldTitle(field) }
            .onFocusChanged { if (!it.isFocused) commit() }
            if ((field.attribute ?: field.path.last().name) == "Description") TextArea(draft.buffer, enabled = enabled, modifier = inputModifier.heightIn(min = 64.dp, max = 120.dp))
            else TextField(draft.buffer, enabled = enabled, modifier = inputModifier
                .onPreviewKeyEvent { if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) { commit(); true } else false })
        }
        if (conflict) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Changed in the source editor.")
            Link("Reload", onClick = { draft.buffer.edit { replace(0, length, field.value) }; draft.expected = field; draft.submitted = null })
        }
    }
}

@Composable
private fun ManifestCapabilities(fields: List<WinRTXmlField>, onRemove: (WinRTXmlField) -> Unit, onAdd: (String, Boolean, Boolean) -> Unit) {
    val names = fields.filter { it.attribute == "Name" }
    val presets = listOf(
        Triple("Internet client", "internetClient", "Capability"),
        Triple("Internet client and server", "internetClientServer", "Capability"),
        Triple("Private networks", "privateNetworkClientServer", "Capability"),
        Triple("Webcam", "webcam", "DeviceCapability"),
        Triple("Microphone", "microphone", "DeviceCapability"),
        Triple("Run full trust", "runFullTrust", "RestrictedCapability"),
    )
    presets.forEach { (title, name, kind) ->
        val namespace = if (kind == "RestrictedCapability") WinRTXmlForms.RESTRICTED else WinRTXmlForms.FOUNDATION
        val existing = names.firstOrNull { it.value == name && it.path.last().namespace == namespace && it.path.last().name == if (kind == "DeviceCapability") kind else "Capability" }
        CheckboxRow(title, existing != null, { checked -> if (checked) onAdd(name, kind == "RestrictedCapability", kind == "DeviceCapability") else existing?.let(onRemove) })
    }
    names.filter { field -> presets.none { it.second == field.value } }.forEach { field ->
        CheckboxRow("${field.value} (${if (field.path.last().namespace == WinRTXmlForms.RESTRICTED) "restricted" else field.path.last().name.removeSuffix("Capability").lowercase().ifEmpty { "general" }})", true, { onRemove(field) })
    }
    Divider(Orientation.Horizontal)
    val name = remember { TextFieldState() }
    var kind by remember { mutableStateOf("General") }
    Text("Additional capability", fontWeight = FontWeight.SemiBold)
    ManifestFormRow("Name") { TextField(name, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Capability name") }) }
    ManifestFormRow("Type") { WinRTComboBox("Capability type", listOf("General", "Restricted", "Device").map { it to it }, kind, Modifier.fillMaxWidth()) { kind = it } }
    OutlinedButton(enabled = name.text.isNotBlank(), onClick = { onAdd(name.text.toString(), kind == "Restricted", kind == "Device") }) { Text("Add capability") }
}

@Composable
private fun ManifestNewDeclaration(application: Int, enabled: Boolean, onAdd: (Int, String, String?) -> Unit) {
    Divider(Orientation.Horizontal)
    Text("Add declaration", fontWeight = FontWeight.SemiBold)
    var type by remember { mutableStateOf("Protocol") }
    val name = remember { TextFieldState() }
    val extension = remember { TextFieldState(".txt") }
    ManifestFormRow("Type") { WinRTComboBox("Declaration type", listOf("Protocol", "File association").map { it to it }, type, Modifier.fillMaxWidth()) { type = it } }
    ManifestFormRow("Name") { TextField(name, modifier = Modifier.fillMaxWidth()) }
    if (type == "File association") ManifestFormRow("File extension") { TextField(extension, modifier = Modifier.fillMaxWidth()) }
    OutlinedButton(enabled = enabled && name.text.isNotBlank(), onClick = { onAdd(application, name.text.toString(), if (type == "Protocol") null else extension.text.toString()) }) { Text("Add declaration") }
}

@Composable
private fun ManifestNewContentUri(application: Int, enabled: Boolean, onAdd: (Int, String, String) -> Unit) {
    Divider(Orientation.Horizontal)
    Text("Add URI rule", fontWeight = FontWeight.SemiBold)
    val match = remember { TextFieldState() }
    var type by remember { mutableStateOf("include") }
    ManifestFormRow("URI") { TextField(match, placeholder = { Text("https://example.com") }, modifier = Modifier.fillMaxWidth()) }
    ManifestFormRow("Rule") { WinRTComboBox("URI rule", listOf("include" to "Include", "exclude" to "Exclude"), type, Modifier.fillMaxWidth()) { type = it } }
    OutlinedButton(enabled = enabled && match.text.isNotBlank(), onClick = { onAdd(application, match.text.toString(), type) }) { Text("Add URI rule") }
}

@Composable
private fun ManifestAssetPreview(value: String, assetPath: (String) -> Path?) {
    var bitmap by remember(value) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(value) {
        bitmap = withContext(Dispatchers.IO) { runCatching {
            val path = assetPath(value) ?: return@runCatching null
            if (Files.size(path) !in 1..4_194_304) return@runCatching null
            org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap()
        }.getOrNull() }
    }
    bitmap?.let { Image(it, contentDescription = "Asset preview", modifier = Modifier.size(64.dp)) }
}

private fun groupTitle(field: WinRTXmlField, page: WinRTManifestPage): String = when (page) {
    WinRTManifestPage.VisualAssets -> when (field.path.last().name) { "DefaultTile" -> "Tiles"; "SplashScreen" -> "Splash screen"; else -> "Icons and background" }
    WinRTManifestPage.Packaging -> when { field.path.any { it.name == "Identity" } -> "Package identity"; field.path.any { it.name == "Properties" } -> "Package properties"; field.path.any { it.name == "Resources" } -> "Languages"; else -> "Dependencies" }
    WinRTManifestPage.Declarations -> field.path.firstOrNull { it.name == "Protocol" || it.name == "FileTypeAssociation" }?.let { "${friendlyTitle(it.name)} ${it.index + 1}" } ?: "Extensions"
    else -> ""
}

private fun fieldTitle(field: WinRTXmlField): String = when (field.attribute ?: field.path.last().name) {
    "Id" -> "Application ID"
    "MinVersion" -> "Minimum Windows version"
    "MaxVersionTested" -> "Maximum version tested"
    "ProcessorArchitecture" -> "Architecture"
    "Match" -> "URI"
    "Square150x150Logo" -> "Square 150 × 150 logo"
    "Square44x44Logo" -> "Square 44 × 44 logo"
    "Wide310x150Logo" -> "Wide 310 × 150 logo"
    "Square310x310Logo" -> "Square 310 × 310 logo"
    "Square71x71Logo" -> "Square 71 × 71 logo"
    else -> friendlyTitle(field.attribute ?: field.path.last().name)
}

private fun friendlyTitle(name: String): String = name.substringAfter(':').replace(Regex("([a-z])([A-Z])"), "$1 $2")
