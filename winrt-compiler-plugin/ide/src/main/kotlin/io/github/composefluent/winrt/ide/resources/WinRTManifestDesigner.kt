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
                field.path.last().name == "TileUpdate" || field.path.last().name == "LockScreen" && field.attribute == "Notification" -> Application
                "VisualElements" in path -> if (field.path.last().name == "VisualElements" && field.attribute in listOf("DisplayName", "Description", "AppListEntry")) Application else VisualAssets
                field.path.last().name == "Logo" -> VisualAssets
                field.path.last().name == "Resource" && field.path.last().index == 0 && field.attribute == "Language" -> Application
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
    catalog: WinRTManifestCatalog = WinRTManifestCatalog.Empty,
    onCatalogCapability: (WinRTManifestCapability) -> Unit = { onCapability(it.name, it.namespace.contains("restrictedcapabilities"), it.device) },
    onDeclaration: (Int, WinRTManifestDeclaration) -> Unit = { _, _ -> },
    onAddNode: (WinRTXmlNode, WinRTManifestChild) -> Unit = { _, _ -> },
    onRemoveNode: (WinRTXmlNode) -> Unit = {},
    onSelection: (Int, Boolean, String, Boolean) -> Unit = { _, _, _, _ -> },
    onCertificate: (WinRTXmlField) -> Unit = {},
    onGenerateAssets: (WinRTManifestAssetRequest) -> Unit = {},
    onBrowseAssetVariant: (WinRTXmlField, Int) -> Unit = { _, _ -> },
    onRemoveAssetVariant: (WinRTXmlField, Int) -> Unit = { _, _ -> },
    onChooseAssetSource: ((String) -> Unit) -> Unit = {},
    generatingAssets: Boolean = false,
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
        if (page == WinRTManifestPage.VisualAssets) {
            WinRTManifestVisualAssets(snapshot, application, onEdit, onBrowse, assetPath, onSelection,
                onGenerateAssets, onBrowseAssetVariant, onRemoveAssetVariant, onChooseAssetSource, errors, generatingAssets) { application = it }
            return@Column
        }
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
                    WinRTManifestPage.Declarations -> "Add declarations and configure their properties."
                    WinRTManifestPage.ContentUris -> "Configure access rules for web content."
                    WinRTManifestPage.Packaging -> "Configure package identity, publisher and supported Windows versions."
                })
                if (page == WinRTManifestPage.Packaging && windowsVersions.isNotEmpty()) {
                    Text("Windows versions", fontWeight = FontWeight.SemiBold)
                    windowsVersions.forEach { Text(it) }
                    Link("Edit Windows versions in Gradle", onClick = onGradle)
                }
                if (page == WinRTManifestPage.Capabilities) WinRTManifestCapabilities(visibleFields, catalog, onRemove, onCatalogCapability)
                else if (page == WinRTManifestPage.Declarations) WinRTManifestDeclarations(snapshot, application, catalog, onEdit, onDeclaration, onAddNode, onRemoveNode)
                else visibleFields.filterNot { it.path.last().name in listOf("Rotation", "ShowOn") }.sortedBy { field ->
                    if (page == WinRTManifestPage.Application) listOf("DisplayName", "EntryPoint", "Language", "Description", "TrustLevel", "RuntimeBehavior", "AppListEntry", "Notification", "ResourceGroup", "Recurrence", "UriTemplate", "Id", "Executable")
                        .indexOf(field.attribute).takeIf { it >= 0 } ?: 100 else 0
                }.groupBy { groupTitle(it, page) }.forEach { (group, fields) ->
                    if (group.isNotEmpty()) Text(group, fontWeight = FontWeight.SemiBold)
                    fields.forEach { field ->
                        key(field.id) {
                            val asset = page == WinRTManifestPage.VisualAssets && (field.attribute.orEmpty().contains("Logo") || field.path.last().name == "Logo" || field.attribute == "Image")
                            val governed = windowsVersionsFromGradle && field.path.last().name == "TargetDeviceFamily" && field.attribute in listOf("MinVersion", "MaxVersionTested")
                            ManifestFormRow(fieldTitle(field)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (page == WinRTManifestPage.Packaging && field.attribute == "Version") ManifestVersionEditor(field, onEdit)
                                        else ManifestFieldEditor(field, !governed, Modifier.weight(1f), onEdit)
                                        if (page == WinRTManifestPage.Packaging && field.attribute == "Publisher") OutlinedButton(onClick = { onCertificate(field) }) { Text("Select Certificate…") }
                                        if (asset) OutlinedButton(onClick = { onBrowse(field) }) { Text("Browse…") }
                                        if (field.attribute in listOf("Name", "Language", "Match") && field.path.last().name in listOf("Protocol", "FileTypeAssociation", "Resource", "Rule") &&
                                            !(field.path.last().name == "Resource" && field.path.last().index == 0))
                                            Link("Remove", onClick = { onRemove(field) })
                                    }
                                    if (asset) ManifestAssetPreview(field.value, assetPath)
                                }
                            }
                        }
                    }
                }
                when (page) {
                    WinRTManifestPage.Application -> ManifestRotations(snapshot, application, onSelection)
                    WinRTManifestPage.Packaging -> ManifestFamilyName(snapshot)
                    WinRTManifestPage.ContentUris -> ManifestNewContentUri(application, snapshot.applicationCount > 0, onContentUri)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun ManifestFormRow(label: String, content: @Composable RowScope.() -> Unit) {
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
internal fun ManifestFieldEditor(field: WinRTXmlField, enabled: Boolean, modifier: Modifier, onEdit: (WinRTXmlField, String) -> Unit) {
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
    val choices = field.choices.ifEmpty { when (field.attribute) {
        "ProcessorArchitecture" -> listOf("x64", "x86", "arm64", "neutral")
        "AppListEntry" -> listOf("default", "none")
        "Type" -> if (field.path.last().name == "Rule") listOf("include", "exclude") else emptyList()
        else -> emptyList()
    } }
    Column(modifier) {
        if (choices.isNotEmpty()) WinRTComboBox(fieldTitle(field), (choices + field.value).distinct().map { it to it.ifEmpty { "Not set" } }, field.value, Modifier.fillMaxWidth()) { onEdit(field, it) }
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
internal fun ManifestAssetPreview(value: String, assetPath: (String) -> Path?, size: Int = 64) {
    var bitmap by remember(value) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(value) {
        bitmap = withContext(Dispatchers.IO) { runCatching {
            val path = assetPath(value) ?: return@runCatching null
            if (Files.size(path) !in 1..4_194_304) return@runCatching null
            org.jetbrains.skia.Image.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap()
        }.getOrNull() }
    }
    bitmap?.let { Image(it, contentDescription = "Asset preview", modifier = Modifier.size(size.dp)) }
}

private fun groupTitle(field: WinRTXmlField, page: WinRTManifestPage): String = when (page) {
    WinRTManifestPage.VisualAssets -> when (field.path.last().name) { "DefaultTile" -> "Tiles"; "SplashScreen" -> "Splash screen"; else -> "Icons and background" }
    WinRTManifestPage.Packaging -> when { field.path.any { it.name == "Identity" } -> "Package identity"; field.path.any { it.name == "Properties" } -> "Package properties"; field.path.any { it.name == "Resources" } -> "Languages"; else -> "Dependencies" }
    WinRTManifestPage.Declarations -> field.path.firstOrNull { it.name == "Protocol" || it.name == "FileTypeAssociation" }?.let { "${friendlyTitle(it.name)} ${it.index + 1}" } ?: "Extensions"
    else -> ""
}

internal fun fieldTitle(field: WinRTXmlField): String = when (field.attribute ?: field.path.last().name) {
    "Id" -> "Application ID"
    "MinVersion" -> "Minimum Windows version"
    "MaxVersionTested" -> "Maximum version tested"
    "ProcessorArchitecture" -> "Architecture"
    "Match" -> "URI"
    "Language" -> if (field.path.last().index == 0) "Default language" else "Language"
    "Notification" -> "Lock screen notification"
    "Recurrence" -> "Tile update recurrence"
    "UriTemplate" -> "Tile update URI template"
    "Square150x150Logo" -> "Square 150 × 150 logo"
    "Square44x44Logo" -> "Square 44 × 44 logo"
    "Wide310x150Logo" -> "Wide 310 × 150 logo"
    "Square310x310Logo" -> "Square 310 × 310 logo"
    "Square71x71Logo" -> "Square 71 × 71 logo"
    else -> friendlyTitle(field.attribute ?: field.path.last().name)
}

private fun friendlyTitle(name: String): String = name.substringAfter(':').replace(Regex("([a-z])([A-Z])"), "$1 $2")

@Composable
private fun ManifestRotations(snapshot: WinRTXmlSnapshot, application: Int, onSelection: (Int, Boolean, String, Boolean) -> Unit) {
    if (snapshot.applicationCount == 0) return
    Text("Supported rotations", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("landscape" to "Landscape", "portrait" to "Portrait", "landscapeFlipped" to "Landscape flipped", "portraitFlipped" to "Portrait flipped").forEach { (value, title) ->
            val selected = snapshot.fields.any { it.path.last().name == "Rotation" && it.path.any { step -> step.name == "Application" && step.index == application } && it.value == value }
            CheckboxRow(title, selected, { onSelection(application, true, value, it) })
        }
    }
}

@Composable
private fun ManifestVersionEditor(field: WinRTXmlField, onEdit: (WinRTXmlField, String) -> Unit) {
    val draft = remember(field.id) { ManifestFieldDraft(field) }
    val parts = remember(field.id) { List(4) { TextFieldState(field.value.split('.').getOrNull(it).orEmpty()) } }
    val values = parts.map { it.text.toString() }
    val valid = values.all { it.toIntOrNull() in 0..65535 }
    LaunchedEffect(field.value) {
        val local = parts.joinToString(".") { it.text.toString() }
        draft.buffer.edit { replace(0, length, local) }
        draft.accept(field)
        if (draft.buffer.text.toString() != local) parts.forEachIndexed { index, part ->
            val value = draft.buffer.text.toString().split('.').getOrNull(index).orEmpty()
            part.edit { replace(0, length, value) }
        }
    }
    val conflict = field.value != draft.expected.value && field.value != draft.submitted
    fun commit() {
        val value = parts.joinToString(".") { it.text.toString() }
        if (valid && !conflict && draft.submitted == null && value != draft.expected.value) {
            draft.submitted = value; onEdit(draft.expected, value)
        }
    }
    val latestCommit by rememberUpdatedState(::commit)
    LaunchedEffect(values, draft.expected.value, draft.submitted) { delay(350); commit() }
    DisposableEffect(field.id) { onDispose { latestCommit() } }
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Major", "Minor", "Build", "Revision").forEachIndexed { index, name -> Column(Modifier.weight(1f)) {
                Text(name)
                TextField(parts[index], modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Version $name" }.onFocusChanged { if (!it.isFocused) commit() })
            } }
        }
        if (!valid) Text("Each version number must be between 0 and 65535.")
        if (conflict) Text("The version changed in the source editor. Reopen Packaging to reload it.")
    }
}

@Composable
private fun ManifestFamilyName(snapshot: WinRTXmlSnapshot) {
    val name = snapshot.fields.firstOrNull { it.path.last().name == "Identity" && it.attribute == "Name" }?.value.orEmpty()
    val publisher = snapshot.fields.firstOrNull { it.path.last().name == "Identity" && it.attribute == "Publisher" }?.value.orEmpty()
    var family by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(name, publisher) { family = withContext(Dispatchers.IO) { runCatching { WinRTPackageIdentity.familyName(name, publisher) }.getOrNull() } }
    ManifestFormRow("Package family name") { Text(family ?: "Enter a valid package name and publisher.") }
    Text("Publisher is read from the selected certificate. Configure package signing in Gradle.")
}
