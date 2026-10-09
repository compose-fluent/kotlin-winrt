package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.background
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

@Composable
internal fun WinRTManifestCapabilities(fields: List<WinRTXmlField>, catalog: WinRTManifestCatalog,
    onRemove: (WinRTXmlField) -> Unit, onAdd: (WinRTManifestCapability) -> Unit) {
    val existing = fields.filter { it.attribute == "Name" && it.path.last().name in listOf("Capability", "DeviceCapability") }
    val fallback = listOf("internetClient", "internetClientServer", "privateNetworkClientServer").map { WinRTManifestCapability(it, WinRTXmlForms.FOUNDATION) } +
        listOf("webcam", "microphone").map { WinRTManifestCapability(it, WinRTXmlForms.FOUNDATION, true) } + WinRTManifestCapability("runFullTrust", WinRTXmlForms.RESTRICTED)
    val capabilities = (catalog.capabilities + fallback + existing.map { WinRTManifestCapability(it.value, it.path.last().namespace, it.path.last().name == "DeviceCapability") }).distinctBy { it.id }.sortedBy { it.title }
    val filter = remember { TextFieldState() }
    var selected by remember { mutableStateOf(capabilities.firstOrNull()?.id) }
    TextField(filter, placeholder = { Text("Filter capabilities") }, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth())
    Row(Modifier.fillMaxWidth().height(520.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.widthIn(max = 320.dp).weight(.4f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            capabilities.filter { it.name.contains(filter.text, true) || it.title.contains(filter.text, true) }.forEach { capability ->
                val field = existing.firstOrNull { it.value == capability.name && it.path.last().namespace == capability.namespace && (it.path.last().name == "DeviceCapability") == capability.device }
                Row(Modifier.fillMaxWidth().background(if (selected == capability.id) JewelTheme.globalColors.outlines.focused.copy(alpha = .15f) else Color.Transparent)
                    .clickable { selected = capability.id }.padding(6.dp)) {
                    CheckboxRow(capability.title, field != null, { checked -> selected = capability.id; if (checked) onAdd(capability) else field?.let(onRemove) })
                }
            }
        }
        Column(Modifier.weight(.6f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            capabilities.firstOrNull { it.id == selected }?.let { capability ->
                Text(capability.title, fontWeight = FontWeight.SemiBold)
                Text(capability.description)
                Text("Capability: ${capability.name}")
                Text("Namespace: ${capability.namespace}")
            }
        }
    }
    Divider(Orientation.Horizontal)
    val name = remember { TextFieldState() }
    var kind by remember { mutableStateOf("Restricted") }
    Text("Additional capability", fontWeight = FontWeight.SemiBold)
    ManifestFormRow("Name") { TextField(name, modifier = Modifier.fillMaxWidth()) }
    ManifestFormRow("Type") { WinRTComboBox("Capability type", listOf("Foundation", "UAP", "Restricted", "Device").map { it to it }, kind, Modifier.fillMaxWidth()) { kind = it } }
    OutlinedButton(enabled = name.text.toString().matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{2,127}")), onClick = {
        onAdd(WinRTManifestCapability(name.text.toString(), when (kind) { "UAP" -> WinRTXmlForms.UAP; "Restricted" -> WinRTXmlForms.RESTRICTED; else -> WinRTXmlForms.FOUNDATION }, kind == "Device"))
    }) { Text("Add Capability") }
}

@Composable
internal fun WinRTManifestDeclarations(snapshot: WinRTXmlSnapshot, application: Int, catalog: WinRTManifestCatalog,
    onEdit: (WinRTXmlField, String) -> Unit, onAdd: (Int, WinRTManifestDeclaration) -> Unit,
    onAddNode: (WinRTXmlNode, WinRTManifestChild) -> Unit, onRemove: (WinRTXmlNode) -> Unit) {
    val extensions = snapshot.nodes.filter { it.path.last().name == "Extension" && it.path.any { step -> step.name == "Extensions" } &&
        it.path.firstOrNull { step -> step.name == "Application" }?.let { it.index == application } != false }
    var selected by remember { mutableStateOf<String?>(null) }
    var type by remember(catalog) { mutableStateOf(catalog.declarations.firstOrNull()?.id.orEmpty()) }
    val selectedNode = extensions.firstOrNull { it.id == selected } ?: extensions.lastOrNull()
    val types = catalog.declarations.distinctBy { it.category to it.packageLevel }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WinRTComboBox("Available declarations", types.map { it.id to it.title }, type, Modifier.weight(1f)) { type = it }
        DefaultButton(enabled = type.isNotEmpty() && (snapshot.applicationCount > 0 || types.firstOrNull { it.id == type }?.packageLevel == true), onClick = {
            types.firstOrNull { it.id == type }?.let { onAdd(application, it) }; selected = null
        }) { Text("Add") }
    }
    if (types.isEmpty()) Text("Install a Windows SDK to load available declarations.")
    Row(Modifier.fillMaxWidth().heightIn(min = 360.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Declarations", fontWeight = FontWeight.SemiBold)
            extensions.forEachIndexed { index, node ->
                val category = snapshot.fields.firstOrNull { it.path == node.path && it.attribute == "Category" }?.value.orEmpty()
                Text("${manifestTitle(category.substringAfterLast('.'))} ${index + 1}", Modifier.fillMaxWidth()
                    .background(if (selectedNode?.id == node.id) JewelTheme.globalColors.outlines.focused.copy(alpha = .15f) else Color.Transparent)
                    .clickable { selected = node.id }.padding(8.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectedNode == null) Text("Add a declaration to configure its properties.")
            else {
                val category = snapshot.fields.firstOrNull { it.path == selectedNode.path && it.attribute == "Category" }?.value.orEmpty()
                val definition = catalog.declarations.firstOrNull { it.category == category && it.namespace == selectedNode.path.last().namespace }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(definition?.title ?: category, fontWeight = FontWeight.SemiBold)
                    Link("Remove Declaration", onClick = { onRemove(selectedNode); selected = null })
                }
                val fields = definition?.let { catalog.declarationFields(snapshot, it, selectedNode) }
                    ?: snapshot.fields.filter { it.path.take(selectedNode.path.size) == selectedNode.path }
                val nodes = snapshot.nodes.filter { it.path.take(selectedNode.path.size) == selectedNode.path }
                nodes.forEach { node -> key(node.id) {
                    val schema = definition?.let { catalog.nodeDefinition(snapshot, it, selectedNode, node) }
                    if (node != selectedNode) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(manifestTitle(node.path.last().name), fontWeight = FontWeight.SemiBold)
                        Link("Remove", onClick = { onRemove(node) })
                    }
                    fields.filter { it.path == node.path && it.attribute != "Category" }.forEach { field -> key(field.id) {
                        ManifestFormRow(fieldTitle(field)) { ManifestFieldEditor(field, true, Modifier.fillMaxWidth(), onEdit) }
                        if (schema?.attributes?.any { it.name == field.attribute && it.namespace == field.attributeNamespace && it.required } == true && field.value.isBlank())
                            Text("${fieldTitle(field)} is required.")
                    } }
                    val available = schema?.children.orEmpty().filter { child ->
                        (node != selectedNode || definition?.body?.let { it.name == child.name } == true) &&
                        snapshot.nodes.count { it.path.dropLast(1) == node.path && it.path.last().name == child.name && it.path.last().namespace == child.namespace } < child.maximum
                    }
                    schema?.children.orEmpty().filter { it.minimum > 0 && nodes.none { child -> child.path.dropLast(1) == node.path && child.path.last().name == it.name && child.path.last().namespace == it.namespace } }
                        .forEach { Text("Add ${manifestTitle(it.name)} to complete this declaration property.") }
                    if (available.isNotEmpty()) {
                        var childKey by remember(node.id, available.map { it.key }) { mutableStateOf(available.first().key) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WinRTComboBox("Declaration property", available.map { it.key to "${manifestTitle(it.name)} (${WinRTXmlForms.namespacePrefix(it.namespace)})" }, childKey, Modifier.weight(1f)) { childKey = it }
                            OutlinedButton(onClick = { available.firstOrNull { it.key == childKey }?.let { onAddNode(node, it) } }) { Text("Add Property") }
                        }
                    }
                    Divider(Orientation.Horizontal)
                } }
            }
        }
    }
}
