package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.composefluent.winrt.ide.ui.WinRTComboBox
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import java.nio.file.Path
import kotlin.math.roundToInt

internal enum class WinRTManifestAssetKind(val title: String, val element: String, val attribute: String?, val width: Int, val height: Int, val basename: String) {
    Small("Small Tile", "DefaultTile", "Square71x71Logo", 71, 71, "Square71x71Logo"),
    Medium("Medium Tile", "VisualElements", "Square150x150Logo", 150, 150, "Square150x150Logo"),
    Wide("Wide Tile", "DefaultTile", "Wide310x150Logo", 310, 150, "Wide310x150Logo"),
    Large("Large Tile", "DefaultTile", "Square310x310Logo", 310, 310, "Square310x310Logo"),
    AppIcon("App Icon", "VisualElements", "Square44x44Logo", 44, 44, "Square44x44Logo"),
    Splash("Splash Screen", "SplashScreen", "Image", 620, 300, "SplashScreen"),
    Badge("Badge Logo", "LockScreen", "BadgeLogo", 24, 24, "BadgeLogo"),
    Package("Package Logo", "Logo", null, 50, 50, "StoreLogo");

    fun field(snapshot: WinRTXmlSnapshot, application: Int): WinRTXmlField? = snapshot.fields.firstOrNull {
        it.path.last().name == element && it.attribute == attribute && (this == Package || it.path.any { step -> step.name == "Application" && step.index == application })
    }
}
internal data class WinRTManifestAssetRequest(val application: Int, val source: String, val target: String, val kinds: Set<WinRTManifestAssetKind>,
    val scales: Set<Int>, val interpolation: String, val padding: Boolean, val lightTheme: Boolean, val overwrite: Boolean)

@Composable
internal fun WinRTManifestVisualAssets(snapshot: WinRTXmlSnapshot, application: Int, onEdit: (WinRTXmlField, String) -> Unit,
    onBrowse: (WinRTXmlField) -> Unit, assetPath: (String) -> Path?, onSelection: (Int, Boolean, String, Boolean) -> Unit,
    onGenerate: (WinRTManifestAssetRequest) -> Unit, onVariant: (WinRTXmlField, Int) -> Unit, onRemove: (WinRTXmlField, Int) -> Unit,
    onSource: ((String) -> Unit) -> Unit, errors: List<String>, generating: Boolean, onApplication: (Int) -> Unit) {
    var selected by remember { mutableStateOf<WinRTManifestAssetKind?>(null) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(170.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            (listOf(null) + WinRTManifestAssetKind.entries).forEach { kind ->
                Text(kind?.title ?: "All Visual Assets", Modifier.fillMaxWidth()
                    .background(if (selected == kind) JewelTheme.globalColors.outlines.focused.copy(alpha = .2f) else Color.Transparent)
                    .clickable { selected = kind }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        Divider(Orientation.Vertical)
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(selected?.title ?: "All Visual Assets", fontWeight = FontWeight.SemiBold)
                errors.distinct().forEach { Text(it) }
                if (snapshot.applicationCount > 1) {
                    val ids = snapshot.fields.filter { it.path.last().name == "Application" && it.attribute == "Id" }
                    ManifestFormRow("Application") { WinRTComboBox("Application", ids.map { it.path.last().index.toString() to it.value }, application.toString(), Modifier.fillMaxWidth()) { onApplication(it.toInt()) } }
                }
                if (selected == null) {
                    ManifestAssetGenerator(application, onGenerate, onSource, generating)
                    Divider(Orientation.Horizontal)
                    Text("Display Settings", fontWeight = FontWeight.SemiBold)
                    snapshot.fields.filter { field -> field.path.any { it.name == "Application" && it.index == application } &&
                        (field.attribute == "ShortName" || field.attribute == "BackgroundColor") }.forEach { field ->
                        ManifestFormRow(if (field.path.last().name == "SplashScreen") "Splash background" else fieldTitle(field)) {
                            ManifestFieldEditor(field, true, Modifier.fillMaxWidth(), onEdit)
                        }
                    }
                    Text("Show name on tiles")
                    listOf("square150x150Logo" to "Medium Tile", "wide310x150Logo" to "Wide Tile", "square310x310Logo" to "Large Tile").forEach { (value, title) ->
                        CheckboxRow(title, snapshot.fields.any { it.path.last().name == "ShowOn" && it.value == value && it.path.any { step -> step.name == "Application" && step.index == application } },
                            { onSelection(application, false, value, it) })
                    }
                    Divider(Orientation.Horizontal)
                }
                (selected?.let(::listOf) ?: WinRTManifestAssetKind.entries).forEach { kind ->
                    val field = kind.field(snapshot, application) ?: return@forEach
                    key(field.id) {
                        if (selected == null) Text(kind.title, fontWeight = FontWeight.SemiBold)
                        ManifestFormRow(fieldTitle(field)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ManifestFieldEditor(field, true, Modifier.weight(1f), onEdit)
                                OutlinedButton(onClick = { onBrowse(field) }) { Text("Browse…") }
                            }
                        }
                        Text("Scale Assets")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(100, 125, 150, 200, 400).forEach { scale ->
                                val variant = WinRTManifestAssets.qualified(field.value, scale)
                                Column(Modifier.width(130.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(112.dp).border(1.dp, JewelTheme.globalColors.outlines.focused.copy(alpha = .5f)).padding(12.dp)) {
                                        ManifestAssetPreview(variant, assetPath, 88)
                                        if (variant.isEmpty() || assetPath(variant) == null) Text("Scale $scale")
                                    }
                                    Text("${(kind.width * scale / 100f).roundToInt()} × ${(kind.height * scale / 100f).roundToInt()} px")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Link("Browse…", onClick = { onVariant(field, scale) })
                                        if (variant.isNotEmpty() && assetPath(variant) != null) Link("Remove", onClick = { onRemove(field, scale) })
                                    }
                                }
                            }
                        }
                        if (kind == WinRTManifestAssetKind.Badge) Text("Badge images must be monochrome PNG. Set Lock screen notification on the Application page to use them.")
                        Divider(Orientation.Horizontal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManifestAssetGenerator(application: Int, onGenerate: (WinRTManifestAssetRequest) -> Unit, onSource: ((String) -> Unit) -> Unit, generating: Boolean) {
    val source = remember { TextFieldState() }
    val target = remember { TextFieldState("Assets") }
    var kinds by remember { mutableStateOf(WinRTManifestAssetKind.entries.filter { it != WinRTManifestAssetKind.Badge }.toSet()) }
    var scales by remember { mutableStateOf(setOf(100, 125, 150, 200, 400)) }
    var interpolation by remember { mutableStateOf("Bicubic") }
    var padding by remember { mutableStateOf(true) }
    var light by remember { mutableStateOf(false) }
    var overwrite by remember { mutableStateOf(false) }
    Text("Asset Generator", fontWeight = FontWeight.SemiBold)
    Text("Select a source image of at least 400 × 400 pixels to create Windows scale assets.")
    ManifestFormRow("Source") {
        TextField(source, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onSource { path -> source.edit { replace(0, length, path) } } }) { Text("Browse…") }
    }
    ManifestFormRow("Target directory") { TextField(target, modifier = Modifier.fillMaxWidth()) }
    ManifestFormRow("Assets") { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WinRTManifestAssetKind.entries.filter { it != WinRTManifestAssetKind.Badge }.forEach { kind ->
            CheckboxRow(kind.title, kind in kinds, { kinds = if (it) kinds + kind else kinds - kind })
        }
    } }
    ManifestFormRow("Scales") { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(100, 125, 150, 200, 400).forEach { scale -> CheckboxRow(scale.toString(), scale in scales, { scales = if (it) scales + scale else scales - scale }) }
    } }
    ManifestFormRow("Resize mode") { WinRTComboBox("Resize mode", listOf("Bicubic", "Bilinear", "Nearest neighbor").map { it to it }, interpolation, Modifier.fillMaxWidth()) { interpolation = it } }
    CheckboxRow("Apply recommended padding", padding, { padding = it })
    CheckboxRow("Generate light theme app icons", light, { light = it })
    CheckboxRow("Replace existing generated assets", overwrite, { overwrite = it })
    DefaultButton(enabled = !generating && source.text.isNotBlank() && target.text.isNotBlank() && kinds.isNotEmpty() && scales.isNotEmpty(), onClick = {
        onGenerate(WinRTManifestAssetRequest(application, source.text.toString(), target.text.toString(), kinds, scales, interpolation, padding, light, overwrite))
    }) { Text(if (generating) "Generating…" else "Generate") }
}
